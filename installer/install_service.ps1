# =============================================================================
# Etichette - installazione del servizio Windows
# =============================================================================
# Impacchettato nell'MSI da Build-Setup.ps1 e copiato in app\ accanto a
# Etichette.exe (WinSW), Etichette.xml e agli altri script. L'MSI lo esegue
# in automatico, elevato, come azione differita subito dopo aver copiato i
# file (vedi installer\wix\main.install.wxs): un doppio clic sull'MSI basta.
# E' anche eseguibile a mano, per esempio dopo un ripristino manuale del
# servizio o per ricreare le scorciatoie.
#
# Cosa fa:
#   1. Crea C:\ProgramData\Etichette (+ log\, backup\) con permessi per
#      SYSTEM/Administratos in scrittura e Users in sola lettura.
#   2. Apre la porta 8765 nel firewall di Windows su tutti i profili (Dominio,
#      Privato, Pubblico): il confine di fiducia e' la rete locale (nessun
#      login, il router NAT separa da internet), non la classificazione che
#      Windows da' alla rete, che su una Wi-Fi nuova e' quasi sempre Pubblica.
#   3. Registra il servizio "Etichette" con WinSW se non esiste ancora,
#      altrimenti lo ferma e basta (idempotente: "install" su un servizio
#      gia' esistente fa scrivere a WinSW un FATAL nel log anche se poi il
#      servizio parte comunque; WinSW rilegge Etichette.xml da solo a ogni
#      avvio, non serve nessun comando apposta per farlo), ripristina
#      l'avvio automatico ritardato e le azioni di ripristino, e lo fa
#      partire.
#   4. Crea il collegamento "Etichette" sul desktop pubblico, in
#      "Esecuzione automatica" e nel menu Start di tutti gli utenti: Edge in
#      modalita' app su http://localhost:8765/.
#   5. Se non e' -Silent, apre la pagina alla fine.
#
# Rieseguibile senza danni (idempotente): non duplica regole del firewall
# ne' collegamenti, e riconosce un servizio gia' registrato.
#
# Uso:
#   PS> .\install_service.ps1            # interattivo: apre la pagina alla fine
#   PS> .\install_service.ps1 -Silent    # usato dall'azione MSI: nessuna UI
#
# Richiede: privilegi di amministratore.
# =============================================================================

[CmdletBinding()]
param(
    [switch]$Silent
)

$ErrorActionPreference = "Stop"

$ScriptDir  = Split-Path -Parent $MyInvocation.MyCommand.Path
$WinswExe   = Join-Path $ScriptDir "Etichette.exe"
$WinswXml   = Join-Path $ScriptDir "Etichette.xml"
$IconPath   = Join-Path $ScriptDir "etichette.ico"
$DataDir    = "C:\ProgramData\Etichette"
$LogDir     = Join-Path $DataDir "log"
$BackupDir  = Join-Path $DataDir "backup"
$FirewallRuleName = "Etichette"
$FirewallPort = 8765
$FirewallRuleNameMdns = "Etichette mDNS"
$MdnsPort = 5353
$AppUrl     = "http://localhost:8765/"
$ShortcutName = "Etichette.lnk"
$ServiceStopTimeoutSeconds = 30

$ServiceCommonScript = Join-Path $ScriptDir "service_common.ps1"
if (-not (Test-Path $ServiceCommonScript)) {
    throw "service_common.ps1 non trovato in $ServiceCommonScript - l'MSI e' stato creato da Build-Setup.ps1?"
}
. $ServiceCommonScript

if (-not (Test-Path $WinswExe)) {
    throw "Etichette.exe (WinSW) non trovato in $WinswExe - l'MSI si e' installato correttamente?"
}
if (-not (Test-Path $WinswXml)) {
    throw "Etichette.xml non trovato in $WinswXml - l'MSI si e' installato correttamente?"
}

function New-DataDirectories {
    Write-Host "[1/4] Preparo la cartella dati..." -ForegroundColor Green
    foreach ($dir in @($DataDir, $LogDir, $BackupDir)) {
        if (-not (Test-Path $dir)) {
            Write-Host "  Creo $dir"
            New-Item -ItemType Directory -Path $dir -Force | Out-Null
        }
    }

    # SYSTEM e Administrators in controllo pieno (il servizio gira come
    # LocalSystem), Users in sola lettura/esecuzione: cosi' un utente non
    # amministratore non puo' toccare il database ne' i backup dal PC
    # condiviso con il gestionale di cassa. Eredita' disattivata per non
    # farsi riscrivere i permessi dai default di ProgramData.
    $icaclsExe = Join-Path $env:SystemRoot "System32\icacls.exe"
    if (Test-Path $icaclsExe) {
        Write-Host "  Imposto i permessi su $DataDir"
        & $icaclsExe $DataDir /inheritance:r /grant:r `
            "SYSTEM:(OI)(CI)F" `
            "Administrators:(OI)(CI)F" `
            "Users:(OI)(CI)RX" 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "icacls su $DataDir ha restituito $LASTEXITCODE; i permessi potrebbero non essere completi."
        }
    } else {
        Write-Warning "icacls.exe non trovato; permessi di $DataDir lasciati ai default di Windows."
    }
}

function Set-OrCreateFirewallRule {
    # Aggiorna la regola se esiste gia' (idempotente: cosi' un "installa
    # sopra" corregge da solo, per esempio, chi l'aveva ancora sul profilo
    # Private,Domain), altrimenti la crea.
    param(
        [Parameter(Mandatory = $true)] [string]$DisplayName,
        [Parameter(Mandatory = $true)] [string]$Protocol,
        [Parameter(Mandatory = $true)] [int]$LocalPort,
        [Parameter(Mandatory = $true)] [string]$Description
    )

    try {
        $existing = Get-NetFirewallRule -DisplayName $DisplayName -ErrorAction SilentlyContinue
        if ($existing) {
            $existing | Set-NetFirewallRule `
                -Direction Inbound `
                -Action Allow `
                -Protocol $Protocol `
                -LocalPort $LocalPort `
                -Profile Domain,Private,Public `
                -Enabled True `
                -Description $Description `
                -ErrorAction Stop
        } else {
            New-NetFirewallRule `
                -DisplayName $DisplayName `
                -Direction Inbound `
                -Action Allow `
                -Protocol $Protocol `
                -LocalPort $LocalPort `
                -Profile Domain,Private,Public `
                -Enabled True `
                -Description $Description `
                | Out-Null
        }
    } catch {
        Write-Warning "Impossibile configurare la regola firewall '$DisplayName' per la porta ${LocalPort}: $($_.Exception.Message)"
        Write-Warning "L'installazione continua; la regola va creata a mano."
    }
}

function Set-EtichetteFirewallRule {
    Write-Host "[2/4] Configuro il firewall di Windows (TCP $FirewallPort, UDP $MdnsPort)..." -ForegroundColor Green

    $newRuleCmd = Get-Command New-NetFirewallRule -ErrorAction SilentlyContinue
    if ($null -eq $newRuleCmd) {
        Write-Warning "New-NetFirewallRule non disponibile; le regole del firewall vanno create a mano."
        return
    }

    # Tutti i profili (Dominio, Privato, Pubblico): il confine di fiducia e'
    # la rete locale, non la classificazione che Windows da' alla rete - una
    # Wi-Fi nuova nasce quasi sempre "Pubblica" di default, e su quel profilo
    # i telefoni non raggiungevano il servizio (verificato sul PC di
    # sviluppo). Nessun login sull'app e il router NAT separa gia' da
    # internet, quindi va bene. Mai la porta 80, che potrebbe servire al
    # gestionale di cassa.
    Set-OrCreateFirewallRule -DisplayName $FirewallRuleName -Protocol TCP -LocalPort $FirewallPort `
        -Description "Banco etichette: interfaccia web su TCP $FirewallPort per telefoni e tablet in rete locale."

    # Seconda regola, UDP 5353: le domande mDNS dei telefoni per
    # "etichette.local" (JmDNS) su rete Pubblica non arrivavano al processo
    # Java senza una regola dedicata, anche con la porta 8765 gia' aperta
    # (verificato sul PC di sviluppo: il QR funzionava, .local no).
    Set-OrCreateFirewallRule -DisplayName $FirewallRuleNameMdns -Protocol UDP -LocalPort $MdnsPort `
        -Description "Risposte mDNS (etichette.local) del servizio Etichette."
}

function Get-EdgePath {
    $candidates = @(
        (Join-Path ${env:ProgramFiles(x86)} "Microsoft\Edge\Application\msedge.exe"),
        (Join-Path ${env:ProgramFiles} "Microsoft\Edge\Application\msedge.exe")
    )
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path $candidate)) {
            return $candidate
        }
    }
    return $null
}

function New-EtichetteShortcut {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ShortcutPath,
        [Parameter(Mandatory = $true)]
        [string]$EdgePath
    )

    $parent = Split-Path -Parent $ShortcutPath
    if (-not [string]::IsNullOrWhiteSpace($parent) -and -not (Test-Path $parent)) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }

    try {
        $shell = New-Object -ComObject WScript.Shell
        $shortcut = $shell.CreateShortcut($ShortcutPath)
        $shortcut.TargetPath = $EdgePath
        $shortcut.Arguments = "--app=$AppUrl"
        $shortcut.Description = "Etichette - banco etichette"
        if (Test-Path $IconPath) {
            $shortcut.IconLocation = $IconPath
        }
        $shortcut.Save()
    } catch {
        Write-Warning "Impossibile creare il collegamento ${ShortcutPath}: $($_.Exception.Message)"
    }
}

function Set-EtichetteShortcuts {
    Write-Host "[3/4] Creo le scorciatoie (desktop, avvio automatico, menu Start)..." -ForegroundColor Green

    $edgePath = Get-EdgePath
    if (-not $edgePath) {
        Write-Warning "Microsoft Edge non trovato in Program Files; le scorciatoie non sono state create. Installa/verifica Edge e rilancia questo script."
        return
    }

    $commonDesktop  = [Environment]::GetFolderPath([Environment+SpecialFolder]::CommonDesktopDirectory)
    $commonStartup  = [Environment]::GetFolderPath([Environment+SpecialFolder]::CommonStartup)
    # CommonPrograms = "C:\ProgramData\Microsoft\Windows\Start Menu\Programs":
    # un file li' dentro, non una sottocartella, e' la voce del menu Start
    # per tutti gli utenti. jpackage NON crea piu' la propria (tolto
    # --win-menu da Build-Setup.ps1: il lanciatore nativo che generava non
    # funziona per questa app, "Failed to launch JVM", perche' avvia la JVM
    # direttamente invece che tramite il servizio Windows).
    $commonPrograms = [Environment]::GetFolderPath([Environment+SpecialFolder]::CommonPrograms)

    $targets = @()
    if (-not [string]::IsNullOrWhiteSpace($commonDesktop)) {
        $targets += (Join-Path $commonDesktop $ShortcutName)
    }
    if (-not [string]::IsNullOrWhiteSpace($commonStartup)) {
        $targets += (Join-Path $commonStartup $ShortcutName)
    }
    if (-not [string]::IsNullOrWhiteSpace($commonPrograms)) {
        $targets += (Join-Path $commonPrograms $ShortcutName)
    }

    if ($targets.Count -eq 0) {
        Write-Warning "Nessuna cartella desktop/avvio automatico/menu Start risolta; scorciatoie non create."
        return
    }

    foreach ($target in $targets) {
        New-EtichetteShortcut -ShortcutPath $target -EdgePath $edgePath
    }
}

function Install-OrRestart-EtichetteService {
    Write-Host "[4/4] Registro/avvio il servizio Windows..." -ForegroundColor Green

    $existingService = Get-Service -Name "Etichette" -ErrorAction SilentlyContinue

    if ($null -eq $existingService) {
        Write-Host "  Registrazione nuova tramite WinSW..."
        # Riprova per ERROR_SERVICE_MARKED_FOR_DELETE (1072): puo' capitare se
        # un "uninstall" precedente non ha ancora rilasciato la registrazione
        # nella SCM (per esempio durante un aggiornamento "installa sopra").
        $maxAttempts = 3
        for ($attempt = 1; $attempt -le $maxAttempts; $attempt++) {
            $installOutput = & $WinswExe install 2>&1
            $installExit = $LASTEXITCODE
            $installOutput | ForEach-Object { Write-Host $_ }
            if ($installExit -eq 0) { break }
            $installText = ($installOutput | Out-String)
            $markedForDelete = Test-ServiceMarkedForDeleteError -Text $installText -Code $installExit
            if ($markedForDelete -and $attempt -lt $maxAttempts) {
                $backoff = 2 * $attempt
                Write-Warning "winsw install: registrazione precedente ancora in rimozione (1072). Riprovo tra ${backoff}s ($attempt/$maxAttempts)..."
                Start-Sleep -Seconds $backoff
                continue
            }
            throw "winsw install ha fallito (codice $installExit)."
        }
    } else {
        # Idempotente: NON richiamare "install" su un servizio gia'
        # registrato. Osservato l'8 settembre 2026 nel log del wrapper dopo
        # un aggiornamento "installa sopra" reale: WinSW risponde con
        # "FATAL - Failed to install the service. Servizio specificato gia'
        # esistente". Non blocca lo script (il servizio parte comunque), ma
        # sporca il log e sembra un fallimento a chi lo legge.
        # Niente "refresh" al posto di "install": WinSW 2.12 non ha quel
        # comando ("--help" elenca install/uninstall/start/stop/stopwait/
        # restart/restart!/status/test/testwait), la prova reale del 9
        # settembre 2026 ha scritto "FATAL - Unhandled exception: Unknown
        # command: refresh" nel log. Non serve comunque nulla al posto di
        # "install": WinSW rilegge Etichette.xml da solo a ogni avvio del
        # servizio, quindi il semplice fermo+riavvio sotto (Stop-Service qui,
        # Start-Service piu' in basso nella funzione) basta gia' a far
        # ripartire il servizio con la configurazione aggiornata.
        Write-Host "  Servizio gia' registrato: fermo e riavvio per applicare eventuali modifiche..."
        if ($existingService.Status -eq "Running") {
            Stop-Service -Name "Etichette" -Force -ErrorAction SilentlyContinue
            if (-not (Wait-ForServiceStopped -Name "Etichette" -TimeoutSeconds $ServiceStopTimeoutSeconds)) {
                Write-Warning "Il servizio non si e' fermato entro ${ServiceStopTimeoutSeconds}s; termino i processi rimasti."
                Stop-ServiceProcessTree -Name "Etichette"
                $installRoot = (Resolve-Path (Join-Path $ScriptDir "..") -ErrorAction SilentlyContinue)
                if ($installRoot) {
                    Stop-EtichetteProcessesInInstallDir -Root $installRoot.Path -GracefulCloseTimeoutMilliseconds 3000
                }
                [void](Wait-ForServiceStopped -Name "Etichette" -TimeoutSeconds 5)
            }
        }
    }

    # Ripristina l'avvio automatico ritardato e le azioni di ripristino: un
    # "uninstall -StopOnly" o un arresto per nome precedente (fatto da
    # update.ps1) potrebbero averle disattivate per evitare che la SCM
    # riavviasse il servizio a meta' aggiornamento. Esplicito con sc.exe
    # invece di delegarlo a "winsw install", che sopra non viene piu'
    # richiamato su un servizio gia' registrato (vedi commento sopra) - va
    # bene rieseguirlo anche dopo una registrazione nuova, e' idempotente.
    $sc = Join-Path $env:SystemRoot "System32\sc.exe"
    if (Test-Path $sc) {
        & $sc config "Etichette" start= delayed-auto 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "sc.exe config delayed-auto ha restituito $LASTEXITCODE; provo Set-Service."
            try {
                Set-Service -Name "Etichette" -StartupType AutomaticDelayedStart -ErrorAction Stop
            } catch {
                Write-Warning "Set-Service AutomaticDelayedStart ha fallito: $($_.Exception.Message)"
            }
        }
        # Azioni di ripristino: riavvio a 10s/30s/120s, contatore azzerato
        # dopo un'ora senza errori - stessi valori di <onfailure> in
        # Etichette.xml.
        & $sc failure "Etichette" reset= 3600 actions= restart/10000/restart/30000/restart/120000 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "sc.exe failure ha restituito $LASTEXITCODE; le azioni di ripristino potrebbero non essere state ripristinate."
        }
    }

    Write-Host "  Avvio il servizio..."
    $maxStartAttempts = 3
    for ($attempt = 1; $attempt -le $maxStartAttempts; $attempt++) {
        try {
            Start-Service -Name "Etichette" -ErrorAction Stop
            break
        } catch {
            $markedForDelete = Test-ServiceMarkedForDeleteError -Text $_.Exception.Message
            if ($markedForDelete -and $attempt -lt $maxStartAttempts) {
                $backoff = 2 * $attempt
                Write-Warning "Start-Service: registrazione ancora in rimozione (1072). Riprovo tra ${backoff}s ($attempt/$maxStartAttempts)..."
                Start-Sleep -Seconds $backoff
                continue
            }
            throw
        }
    }

    $deadline = (Get-Date).AddSeconds(30)
    while ((Get-Date) -lt $deadline) {
        $svc = Get-Service -Name "Etichette" -ErrorAction SilentlyContinue
        if ($null -ne $svc -and $svc.Status -eq "Running") {
            Write-Host "  Servizio avviato."
            break
        }
        Start-Sleep -Milliseconds 500
    }
    $svc = Get-Service -Name "Etichette" -ErrorAction SilentlyContinue
    if ($null -eq $svc -or $svc.Status -ne "Running") {
        $state = if ($null -eq $svc) { "<non registrato>" } else { $svc.Status }
        throw "Il servizio non e' arrivato allo stato Running entro 30s (stato: $state). Controlla $LogDir."
    }
}

New-DataDirectories
Set-EtichetteFirewallRule
Set-EtichetteShortcuts
Install-OrRestart-EtichetteService

Write-Host ""
Write-Host "Fatto. Il servizio Etichette e' installato e in esecuzione." -ForegroundColor Green
Write-Host "Cartella dati : $DataDir"
Write-Host "Log           : $LogDir"
Write-Host "Indirizzo     : $AppUrl"

if (-not $Silent) {
    $edgePath = Get-EdgePath
    if ($edgePath) {
        Write-Host "Apro la pagina..."
        Start-Process -FilePath $edgePath -ArgumentList "--app=$AppUrl"
    } else {
        Write-Warning "Microsoft Edge non trovato; apri manualmente $AppUrl."
    }
}
