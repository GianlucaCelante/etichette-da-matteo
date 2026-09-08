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
#   3. Ferma il servizio (uninstall_service.ps1 -StopOnly): libera i file
#      dell'app prima che msiexec ci scriva sopra, evitando il prompt di
#      riavvio di Windows.
#   4. Esegue "msiexec /i" con /norestart (il banco etichette non deve mai
#      riavviarsi da solo). L'azione dell'MSI (vedi installer\wix) registra
#      e riavvia il servizio da sola sulla nuova versione.
#   5. Se msiexec fallisce, richiama install_service.ps1 -Silent per
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
$UninstallScript = Join-Path $ScriptDir "uninstall_service.ps1"
$InstallScript   = Join-Path $ScriptDir "install_service.ps1"
$DataDir   = "C:\ProgramData\Etichette"
$BackupDir = Join-Path $DataDir "backup"

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

function Invoke-PreMsiexecStop {
    if (-not (Test-Path $UninstallScript)) {
        Write-Warning "uninstall_service.ps1 non trovato in $UninstallScript; salto l'arresto preventivo."
        Write-Warning "L'MSI potrebbe chiedere il riavvio se il servizio e' in esecuzione."
        return
    }
    Write-Host "Fermo il servizio Etichette prima dell'installazione..."
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $UninstallScript -StopOnly
}

function Invoke-Msiexec {
    param([string]$Msi)
    $logPath = Join-Path $env:TEMP ("etichette-update-{0}.log" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
    Write-Host "Installo $Msi ..."
    Write-Host "Log dettagliato: $logPath"
    # /qb! = barra di avanzamento senza pulsante Annulla (un Annulla a meta'
    # aggiornamento e' il momento peggiore per un rollback). /norestart +
    # REBOOT=ReallySuppress: il banco etichette non si riavvia mai da solo.
    $process = Start-Process -FilePath "msiexec.exe" `
        -ArgumentList @("/i", "`"$Msi`"", "/qb!", "/norestart", "/L*v", "`"$logPath`"", "REBOOT=ReallySuppress") `
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
        $process = Start-Process -FilePath "powershell.exe" -Verb RunAs -Wait -PassThru -ArgumentList $argumentList
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
Invoke-PreMsiexecStop

$exitCode = Invoke-Msiexec -Msi $MsiPath
Write-Host "msiexec e' uscito con codice $exitCode."

if ($exitCode -eq 0 -or $exitCode -eq 3010) {
    Write-Host "Aggiornamento completato." -ForegroundColor Green
    if ($exitCode -eq 3010) {
        Write-Host "Windows Installer segnala un riavvio in sospeso (3010); e' stato soppresso (/norestart). Il servizio e' gia' stato registrato e riavviato dall'installer."
    }
    exit 0
}

Restore-ServiceAfterFailedInstall
Write-Error "msiexec ha fallito (codice $exitCode). Il servizio e' stato rimesso in funzione sulla versione presente sul disco."
exit 3
