# =============================================================================
# Etichette - disinstallazione del servizio Windows
# =============================================================================
# Impacchettato nell'MSI da Build-Setup.ps1. Ferma e deregistra il servizio
# "Etichette" prima che l'MSI rimuova i file. E' anche sicuro da eseguire a
# mano prima di "Programmi e funzionalita'".
#
# Per decisione di progetto (docs/stack-tecnologico.md, sezione "Installazione
# e consegna a Matteo"): disinstallare "ferma e toglie il servizio ma lascia
# ProgramData con i dati". Il comportamento di default quindi NON cancella
# C:\ProgramData\Etichette; serve -RemoveData per cancellarlo davvero.
#
# Uso:
#   PS> .\uninstall_service.ps1                # ferma, deregistra, TIENE i dati
#   PS> .\uninstall_service.ps1 -RemoveData     # come sopra, ma cancella anche ProgramData\Etichette
#   PS> .\uninstall_service.ps1 -StopOnly       # solo per update.ps1: ferma senza deregistrare
#
# Richiede: privilegi di amministratore.
# =============================================================================

[CmdletBinding()]
param(
    [switch]$RemoveData,
    [switch]$StopOnly
)

$ErrorActionPreference = "Continue"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$WinswExe  = Join-Path $ScriptDir "Etichette.exe"
$DataDir   = "C:\ProgramData\Etichette"
$FirewallRuleName = "Etichette"
$FirewallRuleNameMdns = "Etichette mDNS"
$ShortcutName = "Etichette.lnk"
# 30s: coerente con lo <stoptimeout> di Etichette.xml, per dare tempo alla
# chiusura pulita di Spring Boot e all'handle della stampante di liberarsi.
$ServiceStopTimeoutSeconds = 30
$InstallRoot = (Resolve-Path (Join-Path $ScriptDir "..") -ErrorAction SilentlyContinue)
if ($null -ne $InstallRoot) {
    $InstallRoot = $InstallRoot.Path
} else {
    $InstallRoot = $ScriptDir
}

$ServiceCommonScript = Join-Path $ScriptDir "service_common.ps1"
if (-not (Test-Path $ServiceCommonScript)) {
    throw "service_common.ps1 non trovato in $ServiceCommonScript - l'MSI e' stato creato da Build-Setup.ps1?"
}
. $ServiceCommonScript

function Disable-EtichetteServiceAutoStart {
    # Disattiva l'avvio automatico PRIMA di fermare il servizio, cosi' le
    # azioni di ripristino di WinSW (<onfailure>) non lo fanno ripartire da
    # solo mentre l'MSI sta sostituendo i file. install_service.ps1 ripristina
    # l'avvio automatico ritardato a fine installazione.
    param([string]$Name)
    try {
        Set-Service -Name $Name -StartupType Disabled -ErrorAction Stop
        Write-Host "Avvio automatico di $Name disattivato (evita riavvii durante l'installazione)."
    } catch {
        Write-Warning "Impossibile disattivare l'avvio automatico di ${Name}: $($_.Exception.Message)"
    }
    $sc = Join-Path $env:SystemRoot "System32\sc.exe"
    if (Test-Path $sc) {
        & $sc failure $Name reset= 0 actions= "" 2>&1 | Out-Null
    }
}

function Stop-EtichetteServiceIfRegistered {
    param([string]$Name)
    $svc = Get-Service -Name $Name -ErrorAction SilentlyContinue
    if ($null -eq $svc) {
        Write-Host "$Name non e' registrato - niente da fermare."
        return $false
    }

    Disable-EtichetteServiceAutoStart -Name $Name

    if ($svc.Status -ne "Stopped") {
        Write-Host "Fermo $Name (timeout: ${ServiceStopTimeoutSeconds}s)..."
        Stop-Service -Name $Name -Force -ErrorAction SilentlyContinue
        if (-not (Wait-ForServiceStopped -Name $Name -TimeoutSeconds $ServiceStopTimeoutSeconds)) {
            Write-Warning "$Name non si e' fermato entro ${ServiceStopTimeoutSeconds}s; termino i processi rimasti sotto $InstallRoot."
            Stop-ServiceProcessTree -Name $Name
            Stop-EtichetteProcessesInInstallDir -Root $InstallRoot
            [void](Wait-ForServiceStopped -Name $Name -TimeoutSeconds 5)
        }
    }
    return $true
}

function Remove-EtichetteFirewallRule {
    param([string]$DisplayName)
    $removeCmd = Get-Command Remove-NetFirewallRule -ErrorAction SilentlyContinue
    if ($null -eq $removeCmd) {
        Write-Warning "Remove-NetFirewallRule non disponibile; la regola del firewall resta."
        return
    }
    $existing = Get-NetFirewallRule -DisplayName $DisplayName -ErrorAction SilentlyContinue
    if ($existing) {
        Write-Host "Rimuovo la regola firewall: $DisplayName"
        $existing | Remove-NetFirewallRule -ErrorAction SilentlyContinue
    }
}

function Remove-EtichetteShortcuts {
    $paths = @()
    $commonDesktop  = [Environment]::GetFolderPath([Environment+SpecialFolder]::CommonDesktopDirectory)
    $commonStartup  = [Environment]::GetFolderPath([Environment+SpecialFolder]::CommonStartup)
    $commonPrograms = [Environment]::GetFolderPath([Environment+SpecialFolder]::CommonPrograms)
    if (-not [string]::IsNullOrWhiteSpace($commonDesktop))  { $paths += (Join-Path $commonDesktop $ShortcutName) }
    if (-not [string]::IsNullOrWhiteSpace($commonStartup))  { $paths += (Join-Path $commonStartup $ShortcutName) }
    if (-not [string]::IsNullOrWhiteSpace($commonPrograms)) { $paths += (Join-Path $commonPrograms $ShortcutName) }

    foreach ($path in $paths) {
        if (Test-Path $path) {
            Write-Host "Rimuovo la scorciatoia: $path"
            Remove-Item -LiteralPath $path -Force -ErrorAction SilentlyContinue
        }
    }
}

function Remove-ServiceRegistrationFallback {
    param([string]$Name)
    $service = Get-Service -Name $Name -ErrorAction SilentlyContinue
    if ($null -eq $service) { return }
    $sc = Join-Path $env:SystemRoot "System32\sc.exe"
    if (-not (Test-Path $sc)) {
        Write-Warning "sc.exe non trovato; impossibile rimuovere la registrazione di $Name."
        return
    }
    Write-Warning "Rimuovo la registrazione di $Name direttamente tramite la SCM."
    & $sc delete $Name | Out-Host
}

if ($StopOnly) {
    # Usato solo da update.ps1 prima di msiexec: ferma il servizio senza
    # deregistrarlo ne' toccare firewall/scorciatoie/dati.
    Write-Host "=== Etichette: arresto pre-aggiornamento ==="
    if (-not (Test-Path $WinswExe)) {
        Write-Warning "Etichette.exe non trovato in $WinswExe - fermo solo eventuali processi residui."
    } else {
        [void](Stop-EtichetteServiceIfRegistered -Name "Etichette")
    }
    Stop-EtichetteProcessesInInstallDir -Root $InstallRoot
    Write-Host "Fatto. Servizio fermato; registrazione, firewall e dati preservati."
    exit 0
}

Write-Host "=== Etichette: disinstallazione del servizio ==="

if (-not (Test-Path $WinswExe)) {
    Write-Warning "Etichette.exe non trovato in $WinswExe - il servizio potrebbe essere gia' stato rimosso."
    Stop-EtichetteProcessesInInstallDir -Root $InstallRoot
} else {
    $wasRegistered = Stop-EtichetteServiceIfRegistered -Name "Etichette"
    if ($wasRegistered) {
        Write-Host "Deregistro il servizio tramite WinSW..."
        & $WinswExe uninstall
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "winsw uninstall ha restituito $LASTEXITCODE"
            Remove-ServiceRegistrationFallback -Name "Etichette"
        }
    } else {
        Write-Host "Il servizio Etichette non era registrato - niente da fare."
    }
    Stop-EtichetteProcessesInInstallDir -Root $InstallRoot
}

Remove-EtichetteFirewallRule -DisplayName $FirewallRuleName
Remove-EtichetteFirewallRule -DisplayName $FirewallRuleNameMdns
Remove-EtichetteShortcuts

if ($RemoveData) {
    if (Test-Path $DataDir) {
        Write-Host "Cancello i dati in $DataDir (-RemoveData richiesto)..."
        Remove-Item -LiteralPath $DataDir -Recurse -Force -ErrorAction SilentlyContinue
    }
    Write-Host "Fatto. Servizio rimosso e dati cancellati."
} else {
    Write-Host "Fatto. Il servizio e' stato rimosso."
    Write-Host "$DataDir e' stato conservato (database SQLite, log, backup)."
    Write-Host "Per cancellare anche i dati: .\uninstall_service.ps1 -RemoveData"
}

exit 0
