# =============================================================================
# Etichette - libreria condivisa per la gestione del servizio Windows
# =============================================================================
# Caricata con dot-source da install_service.ps1, uninstall_service.ps1 e
# update.ps1. Ridotta da agent-java/scripts/service_common.ps1: qui c'e' un
# solo servizio, un solo ambiente, nessuna selezione cloud, quindi restano
# solo gli helper per processi/SCM davvero riusati da piu' script.
#
# Contratto:
#   - Solo definizioni di funzione, nessun codice eseguito al caricamento:
#     chi fa il dot-source lo esegue una volta sola, senza effetti collaterali
#     a sorpresa.
#   - Ogni funzione gestisce da sola i propri errori (try/catch), perche' i
#     chiamanti hanno $ErrorActionPreference diversi (Stop in install/update,
#     Continue in uninstall).
# =============================================================================

function Get-ProcessPathOrNull {
    param(
        [Parameter(Mandatory = $true)]
        [System.Diagnostics.Process]$Process
    )
    try {
        return $Process.MainModule.FileName
    } catch {
        return $null
    }
}

function Test-IsUnderDirectory {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,
        [Parameter(Mandatory = $true)]
        [string]$Directory
    )
    try {
        $fullPath = [System.IO.Path]::GetFullPath($Path).TrimEnd('\')
        $fullDirectory = [System.IO.Path]::GetFullPath($Directory).TrimEnd('\')
        return $fullPath.Equals($fullDirectory, [System.StringComparison]::OrdinalIgnoreCase) `
            -or $fullPath.StartsWith($fullDirectory + '\', [System.StringComparison]::OrdinalIgnoreCase)
    } catch {
        return $false
    }
}

function Get-ServiceProcessId {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name
    )
    try {
        $service = Get-CimInstance -ClassName Win32_Service -Filter "Name='$Name'" -ErrorAction SilentlyContinue
        if ($null -ne $service -and $service.ProcessId -gt 0) {
            return [int]$service.ProcessId
        }
    } catch {
        Write-Warning "Impossibile leggere il PID del servizio ${Name}: $($_.Exception.Message)"
    }
    return $null
}

function Stop-ProcessTree {
    param(
        [Parameter(Mandatory = $true)]
        [int]$ProcessId
    )
    if ($ProcessId -le 0 -or $ProcessId -eq $PID) {
        return
    }
    $taskkill = Join-Path $env:SystemRoot "System32\taskkill.exe"
    if (Test-Path $taskkill) {
        & $taskkill /PID $ProcessId /T /F | Out-Host
        if ($LASTEXITCODE -eq 0) {
            return
        }
        Write-Warning "taskkill.exe ha fallito per il PID $ProcessId (codice $LASTEXITCODE); provo Stop-Process."
    }
    try {
        Stop-Process -Id $ProcessId -Force -ErrorAction Stop
    } catch {
        Write-Warning "Impossibile terminare il PID ${ProcessId}: $($_.Exception.Message)"
    }
}

function Stop-ServiceProcessTree {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name
    )
    $serviceProcessId = Get-ServiceProcessId -Name $Name
    if ($null -eq $serviceProcessId) {
        return
    }
    Write-Warning "Termino a forza il processo del servizio $Name (PID $serviceProcessId)."
    Stop-ProcessTree -ProcessId $serviceProcessId
}

function Stop-EtichetteProcessesInInstallDir {
    # Elimina processi Etichette.exe / java(w).exe rimasti orfani sotto la
    # cartella di installazione, dopo che il tentativo di stop tramite SCM
    # non ha funzionato in tempo.
    param(
        [Parameter(Mandatory = $true)]
        [string]$Root,
        [int]$GracefulCloseTimeoutMilliseconds = 5000
    )
    $processNames = @("Etichette", "java", "javaw")
    foreach ($name in $processNames) {
        Get-Process -Name $name -ErrorAction SilentlyContinue | ForEach-Object {
            $path = Get-ProcessPathOrNull -Process $_
            if (-not $path -or -not (Test-IsUnderDirectory -Path $path -Directory $Root)) {
                return
            }
            Write-Host "Fermo il processo residuo: $($_.ProcessName) (PID $($_.Id))"
            try {
                if ($_.MainWindowHandle -ne 0) {
                    [void]$_.CloseMainWindow()
                    if ($_.WaitForExit($GracefulCloseTimeoutMilliseconds)) {
                        return
                    }
                }
            } catch {
                # Si passa comunque alla terminazione forzata sotto.
            }
            Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue
        }
    }
}

function Wait-ForServiceStopped {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [int]$TimeoutSeconds = 10
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $service = Get-Service -Name $Name -ErrorAction SilentlyContinue
        if ($null -eq $service -or $service.Status -eq "Stopped") {
            return $true
        }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

function Test-ServiceMarkedForDeleteError {
    # Riconosce ERROR_SERVICE_MARKED_FOR_DELETE (Win32 1072 / 0x430): capita
    # quando "winsw install" (CreateService) o Start-Service girano mentre un
    # "winsw uninstall" (DeleteService) precedente non ha ancora rilasciato la
    # registrazione. Riprovare con un piccolo backoff basta di solito.
    param(
        [string]$Text,
        [int]$Code = 0
    )
    if ($Code -eq 1072) { return $true }
    if ([string]::IsNullOrEmpty($Text)) { return $false }
    return ($Text -match '\b1072\b') -or ($Text -match '0x0*430\b') -or ($Text -imatch 'marked for delet')
}
