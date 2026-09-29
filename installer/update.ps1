# =============================================================================
# Etichette - aggiornamento "installa sopra"
# =============================================================================
# Ridotto da agent-java/scripts/update_agent.ps1: qui non c'e' scoperta
# automatica dell'MSI ne' selezione ambiente, solo il percorso del nuovo
# installer passato a mano da Gianluca durante l'assistenza remota.
#
# Cosa fa:
#   1. Si autoeleva (UAC) se non e' gia' in una shell da amministratore.
#   2. Copia il database (e i file -wal/-shm collegati) in
#      ProgramData\Etichette\backup\pre-aggiornamento-<data>\, cosi' un
#      aggiornamento andato male non parte da un database gia' toccato.
#   3. Chiude eventuali finestre di Edge in modalita' app aperte verso
#      l'indirizzo locale del servizio (tengono aperta una connessione, per
#      esempio agli aggiornamenti in tempo reale via SSE, che puo' impedire
#      allo spegnimento pulito di completarsi in tempo), poi ferma il
#      servizio "Etichette" PER NOME tramite la SCM (Get-Service /
#      Stop-Service), non tramite un binario WinSW locale: funziona
#      indipendentemente dalla cartella da cui viene lanciato questo script
#      (repository, Desktop, chiavetta), a differenza della versione
#      precedente che delegava a "uninstall_service.ps1 -StopOnly" e quindi
#      dipendeva dal trovare Etichette.exe accanto a se stesso - vedi la nota
#      nella funzione Stop-EtichetteServiceByName sotto per il bug osservato.
#   4. Esegue "msiexec /i" con /norestart (il banco etichette non deve mai
#      riavviarsi da solo) e MSIRESTARTMANAGERCONTROL=Disable, cosi' Windows
#      Installer non mostra MAI la finestra "file in uso" (che comunque non
#      dovrebbe piu' presentarsi, avendo gia' fermato servizio e Edge sopra:
#      e' una seconda rete di sicurezza, non la prima). L'azione dell'MSI
#      (vedi installer\wix) registra e riavvia il servizio da sola sulla
#      nuova versione.
#   5. Riapre la finestra dell'app (se ne era stata chiusa una al passo 3).
#   6. Se msiexec fallisce, richiama install_service.ps1 -Silent per
#      rimettere in piedi il servizio: l'MSI di jpackage, fallendo, ripristina
#      i file della versione precedente (rollback di Windows Installer), quindi
#      questo passo riparte sulla versione precedente invece di lasciare il
#      banco etichette spento.
#
# Uso:
#   PS> .\update.ps1 "C:\percorso\Etichette-1.1.0.msi"
#
# Codici di uscita:
#   0  aggiornamento riuscito
#   2  elevazione UAC rifiutata
#   3  MSI non trovato, o msiexec fallito (servizio comunque ripristinato)
# =============================================================================

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$MsiPath,

    # Marcatore interno per evitare loop di autoelevazione. Non passarlo a mano.
    [switch]$Elevated
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$InstallScript = Join-Path $ScriptDir "install_service.ps1"
$DataDir   = "C:\ProgramData\Etichette"
$BackupDir = Join-Path $DataDir "backup"

# Helper di basso livello (Get-ServiceProcessId, Stop-ProcessTree,
# Stop-ServiceProcessTree, Wait-ForServiceStopped): tutti basati sul NOME del
# servizio o su Get-CimInstance, non su un percorso di file, quindi
# funzionano da qualunque cartella sia lanciato questo script.
$ServiceCommonScript = Join-Path $ScriptDir "service_common.ps1"
if (-not (Test-Path $ServiceCommonScript)) {
    throw "service_common.ps1 non trovato in $ServiceCommonScript."
}
. $ServiceCommonScript

function Test-IsAdministrator {
    $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object System.Security.Principal.WindowsPrincipal($identity)
    return $principal.IsInRole([System.Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Backup-Database {
    if (-not (Test-Path $DataDir)) {
        Write-Warning "$DataDir non esiste ancora (primo aggiornamento?); nessun database da salvare."
        return
    }
    if (-not (Test-Path $BackupDir)) {
        New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
    }

    # File del database SQLite direttamente in ProgramData\Etichette (non
    # ricorsivo: non tocca ne' log\ ne' backup\ stesso). Il nome esatto del
    # file dipende dal servizio; qui si copia per estensione cosi' funziona
    # anche se cambia.
    $dbFiles = Get-ChildItem -Path $DataDir -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Extension -in ".db", ".sqlite", ".sqlite3" -or $_.Name -match "\.(db|sqlite|sqlite3)-(wal|shm)$" }

    if (-not $dbFiles -or $dbFiles.Count -eq 0) {
        Write-Warning "Nessun file di database trovato in $DataDir; salto il backup pre-aggiornamento."
        return
    }

    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $target = Join-Path $BackupDir "pre-aggiornamento-$stamp"
    New-Item -ItemType Directory -Path $target -Force | Out-Null

    foreach ($file in $dbFiles) {
        Copy-Item -LiteralPath $file.FullName -Destination $target -Force
    }
    Write-Host "Backup del database salvato in $target ($($dbFiles.Count) file)."
}

function Get-EdgePath {
    # Copia locale (come in install_service.ps1): update.ps1 deve restare
    # eseguibile da solo, senza dipendere da un altro script per poche righe.
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

function Get-EtichetteAppWindowProcesses {
    <#
      Trova i processi di Microsoft Edge aperti in modalita' app verso
      l'indirizzo locale del servizio, qualunque porta (l'app puo' finire su
      una porta diversa da quella di default se e' occupata - vedi
      Impostazioni): riga di comando che contiene "--app=http://localhost:<porta>".
      Una finestra cosi' aperta tiene una connessione attiva verso il
      servizio (per esempio un canale SSE per gli aggiornamenti in tempo
      reale), che puo' impedire allo spegnimento pulito di Spring Boot di
      completarsi entro il timeout.
    #>
    Get-CimInstance -ClassName Win32_Process -Filter "Name='msedge.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -and $_.CommandLine -match '--app=http://localhost:\d+' }
}

function Close-EtichetteAppWindows {
    param([array]$Processes)
    if (-not $Processes -or $Processes.Count -eq 0) {
        return
    }
    Write-Host "Chiudo $($Processes.Count) finestra/e di Edge in modalita' app (Etichette)..."
    foreach ($proc in $Processes) {
        try {
            Stop-Process -Id $proc.ProcessId -Force -ErrorAction Stop
        } catch {
            Write-Warning "Impossibile chiudere il processo Edge PID $($proc.ProcessId): $($_.Exception.Message)"
        }
    }
}

function Open-EtichetteAppWindows {
    # Riapre le finestre chiuse da Close-EtichetteAppWindows, allo stesso
    # indirizzo (porta inclusa): $Processes e' l'elenco catturato PRIMA
    # della chiusura, quindi la loro CommandLine e' ancora leggibile qui.
    #
    # Importante: Edge non va avviato come figlio diretto di questo script
    # (Start-Process -FilePath msedge.exe lo terrebbe agganciato al suo
    # albero di processi). Anche col fix sopra (WaitForExit invece di
    # -Wait), un Edge rimasto figlio dello script elevato e' fragile;
    # explorer.exe e "cmd /c start" avviano Edge e terminano subito,
    # staccandolo del tutto dall'albero di questo processo.
    param([array]$Processes)
    if (-not $Processes -or $Processes.Count -eq 0) {
        return
    }
    $urls = @()
    foreach ($proc in $Processes) {
        if ($proc.CommandLine -match '--app=(http://localhost:\d+/?)') {
            $urls += $Matches[1]
        }
    }
    $urls = $urls | Select-Object -Unique
    if (-not $urls -or $urls.Count -eq 0) {
        return
    }

    $publicDesktop = [Environment]::GetFolderPath('CommonDesktopDirectory')
    $shortcut = Join-Path $publicDesktop "Etichette.lnk"

    if (Test-Path -LiteralPath $shortcut) {
        # Il collegamento pubblico punta gia' all'indirizzo giusto (stessa
        # porta configurata per il servizio): lo apriamo con explorer.exe,
        # che avvia Edge come proprio figlio e termina subito, staccandolo
        # dall'albero di processi di questo script.
        Write-Host "Riapro l'app tramite il collegamento pubblico $shortcut ..."
        Start-Process -FilePath "explorer.exe" -ArgumentList ('"' + $shortcut + '"')
        return
    }

    $edgePath = Get-EdgePath
    if (-not $edgePath) {
        Write-Warning "Microsoft Edge non trovato e nessun collegamento pubblico; non riapro la finestra dell'app. Apri manualmente l'indirizzo del servizio."
        return
    }
    foreach ($url in $urls) {
        Write-Host "Riapro $url ..."
        # Ripiego se manca il collegamento pubblico: "cmd /c start" avvia
        # Edge tramite il proprio meccanismo di avvio e termina subito,
        # quindi anche qui Edge non resta figlio di questo script.
        Start-Process -FilePath "cmd.exe" -ArgumentList @("/c", "start", '""', ('"' + $edgePath + '"'), ("--app=$url")) -WindowStyle Hidden
    }
}

function Disable-EtichetteServiceAutoStart {
    # Come in uninstall_service.ps1: disattiva l'avvio automatico e le azioni
    # di ripristino PRIMA di fermare il servizio, cosi' WinSW non lo fa
    # ripartire da solo mentre msiexec sta sostituendo i file.
    # install_service.ps1 (eseguito dall'MSI) ripristina tutto a fine
    # installazione.
    try {
        Set-Service -Name "Etichette" -StartupType Disabled -ErrorAction Stop
    } catch {
        Write-Warning "Impossibile disattivare l'avvio automatico di Etichette: $($_.Exception.Message)"
    }
    $sc = Join-Path $env:SystemRoot "System32\sc.exe"
    if (Test-Path $sc) {
        & $sc failure "Etichette" reset= 0 actions= "" 2>&1 | Out-Null
    }
}

function Stop-EtichetteServiceByName {
    <#
      Ferma il servizio "Etichette" per NOME, tramite la SCM (Get-Service /
      Stop-Service / Get-CimInstance Win32_Service in service_common.ps1):
      funziona indipendentemente da dove sia lanciato questo script.

      Bug osservato l'8 settembre 2026, log in
      C:\ProgramData\Etichette\log\Etichette.wrapper.log: lanciato dalla
      cartella del repository (non da C:\Program Files\Etichette\app\), lo
      script prima di questa correzione delegava l'arresto a
      "uninstall_service.ps1 -StopOnly", che cerca Etichette.exe (WinSW)
      accanto a se stesso. Etichette.exe non e' nel repository (e' scaricato
      e staged solo dentro l'MSI costruito), quindi l'arresto veniva saltato
      in silenzio (solo un avviso) e msiexec partiva con il servizio ancora
      attivo: Windows Installer mostrava la finestra "Etichette sta usando
      file...". Fermare per nome tramite la SCM invece che tramite un
      binario locale toglie del tutto questa dipendenza dalla cartella.
    #>
    param(
        [int]$TimeoutSeconds = 40
    )

    $svc = Get-Service -Name "Etichette" -ErrorAction SilentlyContinue
    if ($null -eq $svc) {
        Write-Host "Il servizio Etichette non e' registrato; nessun arresto necessario."
        return
    }
    # Anche a servizio gia' fermo: "fermo" puo' voler dire che sta fallendo
    # e ripartendo ogni due minuti (azioni di ripristino), e un riavvio a
    # meta' msiexec riaprirebbe i file che si stanno sostituendo.
    Disable-EtichetteServiceAutoStart

    if ($svc.Status -eq "Stopped") {
        Write-Host "Il servizio Etichette e' gia' fermo."
    } else {
        Write-Host "Fermo il servizio Etichette (timeout ${TimeoutSeconds}s)..."
        Stop-Service -Name "Etichette" -Force -ErrorAction SilentlyContinue

        if (Wait-ForServiceStopped -Name "Etichette" -TimeoutSeconds $TimeoutSeconds) {
            Write-Host "Servizio fermato."
        } else {
            Write-Warning "Il servizio Etichette non si e' fermato entro ${TimeoutSeconds}s; termino il suo albero di processi."
            Stop-ServiceProcessTree -Name "Etichette"
            [void](Wait-ForServiceStopped -Name "Etichette" -TimeoutSeconds 5)
        }
    }

    Stop-EtichetteOrphanJvm
}

function Stop-EtichetteOrphanJvm {
    <#
      Termina le JVM del runtime installato rimaste vive senza servizio.
      Il 25 settembre 2026 il servizio risultava fermo (363 avvii falliti:
      "Port 8765 was already in use") mentre la JVM della 0.1.41, partita
      all'avvio del PC e sopravvissuta al suo WinSW, rispondeva ancora sulla
      8765: col servizio "gia' fermo" lo script passava subito a msiexec, che
      avrebbe trovato etichette.jar e il runtime in uso (come il 12/9,
      uscito 1602). Si riconoscono dal percorso dell'eseguibile, sotto la
      cartella di installazione: nessun'altra java.exe usa quel runtime.
    #>
    $installRoot = Get-EtichetteInstallRoot
    if (-not $installRoot) {
        return
    }
    # Win32_Process e non Get-Process: la JVM gira come LocalSystem e
    # MainModule puo' essere negato anche a un amministratore; il percorso
    # letto da CIM in una sessione elevata c'e' sempre.
    $orphans = @(Get-CimInstance -ClassName Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.ExecutablePath -and (Test-IsUnderDirectory -Path $_.ExecutablePath -Directory $installRoot) })
    if ($orphans.Count -eq 0) {
        return
    }
    foreach ($proc in $orphans) {
        Write-Warning "JVM del servizio ancora viva senza servizio (PID $($proc.ProcessId), avviata $($proc.CreationDate)): la termino."
        Stop-Process -Id $proc.ProcessId -Force -ErrorAction SilentlyContinue
    }
    Wait-Process -Id ($orphans | ForEach-Object { [int]$_.ProcessId }) -Timeout 10 -ErrorAction SilentlyContinue
}

function Get-EtichetteInstallRoot {
    # Dal percorso registrato del servizio:
    # "C:\Program Files\Etichette\app\Etichette.exe" -> C:\Program Files\Etichette
    try {
        $service = Get-CimInstance -ClassName Win32_Service -Filter "Name='Etichette'" -ErrorAction SilentlyContinue
        if ($null -eq $service -or -not $service.PathName) {
            return $null
        }
        if ($service.PathName -match '^\s*"([^"]+)"') {
            $exe = $Matches[1]
        } else {
            $exe = ($service.PathName.Trim() -split '\s+')[0]
        }
        $root = Split-Path -Parent (Split-Path -Parent $exe)
        if ($root -and (Test-Path -LiteralPath $root)) {
            return $root
        }
    } catch {
        Write-Warning "Impossibile ricavare la cartella di installazione dal servizio: $($_.Exception.Message)"
    }
    return $null
}

function Invoke-Msiexec {
    param([string]$Msi)
    $logPath = Join-Path $env:TEMP ("etichette-update-{0}.log" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
    Write-Host "Installo $Msi ..."
    Write-Host "Log dettagliato: $logPath"
    # /qb! = barra di avanzamento senza pulsante Annulla (un Annulla a meta'
    # aggiornamento e' il momento peggiore per un rollback). /norestart +
    # REBOOT=ReallySuppress: il banco etichette non si riavvia mai da solo.
    # MSIRESTARTMANAGERCONTROL=Disable: disattiva del tutto la scansione
    # della Restart Manager sui file in uso, cosi' la finestra "Etichette
    # sta usando file..." non puo' comparire nemmeno come effetto
    # collaterale di qualcos'altro che tiene un handle aperto - i passi sopra
    # (chiusura di Edge, arresto del servizio) sono gia' la prima difesa,
    # questa e' la seconda.
    $process = Start-Process -FilePath "msiexec.exe" `
        -ArgumentList @("/i", "`"$Msi`"", "/qb!", "/norestart", "/L*v", "`"$logPath`"", "REBOOT=ReallySuppress", "MSIRESTARTMANAGERCONTROL=Disable") `
        -Wait -PassThru
    return $process.ExitCode
}

function Restore-ServiceAfterFailedInstall {
    Write-Host "Installazione fallita: rimetto in piedi il servizio sulla versione presente sul disco..." -ForegroundColor Yellow
    if (Test-Path $InstallScript) {
        try {
            & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $InstallScript -Silent
            return
        } catch {
            Write-Warning "install_service.ps1 -Silent ha fallito: $($_.Exception.Message)"
        }
    }
    # Ripiego minimo se anche install_service.ps1 non fosse disponibile.
    $svc = Get-Service -Name "Etichette" -ErrorAction SilentlyContinue
    if ($null -ne $svc) {
        $sc = Join-Path $env:SystemRoot "System32\sc.exe"
        if (Test-Path $sc) { & $sc config "Etichette" start= delayed-auto 2>&1 | Out-Null }
        try { Start-Service -Name "Etichette" -ErrorAction Stop } catch {
            Write-Warning "Impossibile riavviare il servizio Etichette: $($_.Exception.Message). Serve intervento manuale."
        }
    }
}

# -----------------------------------------------------------------------------
# Autoelevazione
# -----------------------------------------------------------------------------
if (-not (Test-IsAdministrator)) {
    if ($Elevated) {
        Write-Error "La rielevazione non ha ottenuto i privilegi di amministratore. Interrompo."
        exit 3
    }
    if (-not (Test-Path -LiteralPath $MsiPath)) {
        Write-Error "MSI non trovato: $MsiPath"
        exit 3
    }
    $resolvedMsi = (Resolve-Path -LiteralPath $MsiPath).Path
    Write-Host "Servono i privilegi di amministratore. Richiedo l'elevazione..."
    $argumentList = @(
        "-NoProfile", "-ExecutionPolicy", "Bypass",
        "-File", ('"{0}"' -f $PSCommandPath),
        "-MsiPath", ('"{0}"' -f $resolvedMsi),
        "-Elevated"
    )
    try {
        # NON usare -Wait qui: PowerShell, quando -Verb RunAs richiede
        # ShellExecute, implementa -Wait con un Job object che aspetta
        # l'INTERO albero di processi del figlio, non solo il processo
        # elevato. Lo script elevato, a fine aggiornamento, riapre Edge
        # (Open-EtichetteAppWindows): con -Wait questo lanciatore restava
        # bloccato finche' l'utente non chiudeva anche quella finestra di
        # Edge - bug osservato l'8 settembre 2026 (oltre dieci minuti dopo
        # la fine reale dell'aggiornamento). Process.WaitForExit() aspetta
        # invece SOLO il PID del processo elevato, tramite il suo handle.
        $process = Start-Process -FilePath "powershell.exe" -Verb RunAs -PassThru -ArgumentList $argumentList
        $process.WaitForExit()
        exit $process.ExitCode
    } catch {
        $inner = $_.Exception
        $uacDeclined = $false
        while ($null -ne $inner) {
            if ($inner -is [System.ComponentModel.Win32Exception] -and $inner.NativeErrorCode -eq 1223) {
                $uacDeclined = $true
                break
            }
            $inner = $inner.InnerException
        }
        if ($uacDeclined) {
            Write-Warning "Elevazione rifiutata. Aggiornamento non eseguito."
            exit 2
        }
        Write-Error "Rilancio elevato fallito: $($_.Exception.Message)"
        exit 3
    }
}

# -----------------------------------------------------------------------------
# Corpo elevato
# -----------------------------------------------------------------------------
if (-not (Test-Path -LiteralPath $MsiPath)) {
    Write-Error "MSI non trovato: $MsiPath"
    exit 3
}
$MsiPath = (Resolve-Path -LiteralPath $MsiPath).Path

Write-Host "=== Etichette: aggiornamento a $MsiPath ==="

Backup-Database

$appWindows = Get-EtichetteAppWindowProcesses
Close-EtichetteAppWindows -Processes $appWindows

Stop-EtichetteServiceByName -TimeoutSeconds 40

$exitCode = Invoke-Msiexec -Msi $MsiPath
Write-Host "msiexec e' uscito con codice $exitCode."

if ($exitCode -eq 0 -or $exitCode -eq 3010) {
    Write-Host "Aggiornamento completato." -ForegroundColor Green
    if ($exitCode -eq 3010) {
        Write-Host "Windows Installer segnala un riavvio in sospeso (3010); e' stato soppresso (/norestart). Il servizio e' gia' stato registrato e riavviato dall'installer."
    }
    Open-EtichetteAppWindows -Processes $appWindows
    exit 0
}

Restore-ServiceAfterFailedInstall
Open-EtichetteAppWindows -Processes $appWindows
Write-Error "msiexec ha fallito (codice $exitCode). Il servizio e' stato rimesso in funzione sulla versione presente sul disco."
exit 3
