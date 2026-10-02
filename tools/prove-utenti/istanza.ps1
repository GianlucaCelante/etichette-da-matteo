<#
  Istanze di PROVA dell'app Etichette con stampante finta (vedi LEGGIMI.md).

    istanza.ps1 avvia -N 1 [-Reset] [-Rotolo 62|102] [-DurataPaginaMs 1500] [-Jar <percorso>]
    istanza.ps1 ferma -N 1
    istanza.ps1 ferma-tutto
    istanza.ps1 stato -N 1
    istanza.ps1 errore -N 1 -Tipo coperchio|rotolo-finito|nessun-rotolo|scollegata
    istanza.ps1 ripristina -N 1
    istanza.ps1 cambia-rotolo -N 1 -Rotolo 62|102

  Porta = 18770 + N (mai la 8765 del servizio installato). Dati in dati\utente-N.
  Non tocca src/main, il jar, il servizio installato ne' C:\ProgramData\Etichette.
#>
param(
    [Parameter(Position = 0)][string]$Comando = '',
    [int]$N = 1,
    [switch]$Reset,
    [ValidateSet(62, 102)][int]$Rotolo = 62,
    [int]$DurataPaginaMs = 1500,
    [string]$Tipo = '',
    [string]$Jar = ''
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false) } catch { }
$Radice = $PSScriptRoot
$Repo = (Resolve-Path (Join-Path $Radice '..\..')).Path
$Porta = 18770 + $N
$Dati = Join-Path $Radice "dati\utente-$N"
$PidDir = Join-Path $Radice 'pid'
$PidFile = Join-Path $PidDir "istanza-$N.pid"
$File = Join-Path $Dati 'stampante.txt'
$ClasseMain = 'prove.utenti.AvviaConSimulata'

if ($N -lt 1 -or $N -gt 99) { Write-Error 'N deve stare fra 1 e 99'; exit 1 }

function Trova-Java([string]$nome) {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\$nome.exe"))) {
        return (Join-Path $env:JAVA_HOME "bin\$nome.exe")
    }
    $c = Get-Command $nome -ErrorAction SilentlyContinue
    if (-not $c) { throw "$nome non trovato (serve un JDK 17 nel PATH o JAVA_HOME)" }
    return $c.Source
}

# Processi java di QUESTA istanza (per riga di comando: mai per nome del programma da solo).
function Processi-Istanza {
    $filtro = "*$ClasseMain*--server.port=$Porta*"
    @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
        Where-Object { $_.CommandLine -like $filtro })
}

function Scrivi-Controllo([string]$errore, [int]$rotolo) {
    New-Item -ItemType Directory -Force $Dati | Out-Null
    Set-Content -Path $File -Value @("errore=$errore", "rotolo=$rotolo") -Encoding ASCII
}

function Leggi-Controllo {
    $r = @{ errore = 'ok'; rotolo = 62 }
    if (Test-Path $File) {
        foreach ($riga in Get-Content $File) {
            $kv = $riga -split '=', 2
            if ($kv.Count -eq 2) { $r[$kv[0].Trim()] = $kv[1].Trim() }
        }
    }
    return $r
}

function Versione-Risponde {
    try {
        $r = Invoke-WebRequest -UseBasicParsing -TimeoutSec 2 "http://127.0.0.1:$Porta/api/versione"
        return $r.Content
    } catch { return $null }
}

# Prepara (una volta per jar) la cartella app\<jar>: contenuto del jar spacchettato + classi della
# stampante finta compilate contro quelle del jar. Protetta da un mutex: due avvii insieme non
# si pestano i piedi.
function Prepara-App {
    $jarItem = $null
    if ($Jar) {
        $jarItem = Get-Item $Jar
    } else {
        $jarItem = Get-ChildItem (Join-Path $Repo 'target') -Filter 'etichette-*.jar' |
            Where-Object { $_.Name -notlike '*.original' } |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if (-not $jarItem) { throw "nessun target\etichette-*.jar: costruirlo con mvn clean package" }
    }
    $nome = $jarItem.BaseName + '-' + $jarItem.LastWriteTimeUtc.ToString('yyyyMMddHHmmss')
    $app = Join-Path $Radice "app\$nome"
    $mutex = New-Object System.Threading.Mutex($false, 'Global\EtichettePreparaProve')
    [void]$mutex.WaitOne()
    try {
        if (-not (Test-Path (Join-Path $app 'etichette.jar'))) {
            Write-Host "Spacchetto $($jarItem.Name) in app\$nome ..."
            $tmp = "$app.tmp"
            if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }
            New-Item -ItemType Directory -Force $tmp | Out-Null
            Push-Location $tmp
            try { & (Trova-Java 'jar') xf $jarItem.FullName BOOT-INF; if ($LASTEXITCODE -ne 0) { throw 'jar xf fallito' } }
            finally { Pop-Location }
            # copia del jar: un 'mvn clean package' altrui non deve trovarlo bloccato da questa JVM
            Copy-Item $jarItem.FullName (Join-Path $tmp 'etichette.jar')
            Move-Item $tmp $app
        }
        $classi = Join-Path $app 'sim-classes'
        $sorgenti = @(Get-ChildItem (Join-Path $Radice 'simulatore\src') -Recurse -Filter '*.java')
        $ultimoSorgente = ($sorgenti | Sort-Object LastWriteTime -Descending | Select-Object -First 1).LastWriteTime
        $marker = Join-Path $classi 'compilato.txt'
        if (-not (Test-Path $marker) -or (Get-Item $marker).LastWriteTime -lt $ultimoSorgente) {
            Write-Host 'Compilo la stampante finta ...'
            if (Test-Path $classi) { Remove-Item -Recurse -Force $classi }
            New-Item -ItemType Directory -Force $classi | Out-Null
            $cp = "$app\BOOT-INF\classes;$app\BOOT-INF\lib\*"
            $args2 = @('-encoding', 'UTF-8', '--release', '17', '-d', $classi, '-cp', $cp) + ($sorgenti | ForEach-Object { $_.FullName })
            & (Trova-Java 'javac') @args2
            if ($LASTEXITCODE -ne 0) { throw 'javac fallito' }
            Set-Content $marker 'ok'
        }
    } finally { $mutex.ReleaseMutex() }
    return $app
}

function Ferma-Istanza {
    $ps = Processi-Istanza
    foreach ($p in $ps) {
        Write-Host "Fermo PID $($p.ProcessId) (porta $Porta)"
        Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
    }
    foreach ($p in $ps) {
        $fine = (Get-Date).AddSeconds(15)
        while ((Get-Process -Id $p.ProcessId -ErrorAction SilentlyContinue) -and (Get-Date) -lt $fine) { Start-Sleep -Milliseconds 200 }
    }
    if (Test-Path $PidFile) { Remove-Item $PidFile -Force }
    return $ps.Count
}

switch ($Comando) {
    'avvia' {
        if ($Porta -eq 8765) { throw 'mai la 8765' }
        $inizio = Get-Date
        $gia = Processi-Istanza
        if ($gia.Count -gt 0 -and -not $Reset) {
            Write-Host "Istanza $N gia' in esecuzione (PID $($gia[0].ProcessId)): non la riavvio. Usa -Reset o 'ferma' prima."
            exit 0
        }
        if ($gia.Count -gt 0) { [void](Ferma-Istanza) }
        $occupata = Get-NetTCPConnection -LocalPort $Porta -State Listen -ErrorAction SilentlyContinue
        if ($occupata) { throw "la porta $Porta e' occupata dal PID $($occupata[0].OwningProcess), non e' una mia istanza" }

        $app = Prepara-App
        if ($Reset -and (Test-Path $Dati)) {
            for ($i = 0; $i -lt 10; $i++) {
                try { Remove-Item -Recurse -Force $Dati -ErrorAction Stop; break } catch { Start-Sleep -Milliseconds 500 }
            }
            if (Test-Path $Dati) { throw "impossibile cancellare $Dati" }
            Write-Host "Reset: cartella dati cancellata (app nuova)."
        }
        New-Item -ItemType Directory -Force $Dati, $PidDir | Out-Null
        Scrivi-Controllo 'ok' $Rotolo

        $env:ETICHETTE_DATA_DIR = $Dati
        # Il JAR vero (copia) con la classe di avvio e la stampante finta aggiunte via loader.path:
        # cosi' l'app e' quella prodotta (versione compresa), solo il trasporto USB cambia.
        $classi = "$app\sim-classes/"   # barra finale: loader.path la tratta come cartella di classi (e niente "\" prima delle virgolette)
        $argomenti = "-Xmx300m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Dfile.encoding=UTF-8 -Dsim.rotolo=$Rotolo -Dsim.durata-ms=$DurataPaginaMs " +
            "-Dloader.path=`"$classi`" -Dloader.main=$ClasseMain -cp `"$app\etichette.jar`" org.springframework.boot.loader.launch.PropertiesLauncher --server.port=$Porta"
        $out = Join-Path $Dati 'console.out.log'
        $err = Join-Path $Dati 'console.err.log'
        $proc = Start-Process -FilePath (Trova-Java 'java') -ArgumentList $argomenti -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput $out -RedirectStandardError $err -WorkingDirectory $Dati
        Set-Content -Path $PidFile -Value $proc.Id -Encoding ASCII

        $versione = $null
        $limite = (Get-Date).AddSeconds(120)
        while ((Get-Date) -lt $limite) {
            if ($proc.HasExited) {
                Write-Host "L'app e' terminata subito (codice $($proc.ExitCode)). Ultime righe:"
                if (Test-Path $err) { Get-Content $err -Tail 15 }
                if (Test-Path $out) { Get-Content $out -Tail 15 }
                Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
                exit 1
            }
            $versione = Versione-Risponde
            if ($versione) { break }
            Start-Sleep -Milliseconds 500
        }
        if (-not $versione) { Write-Host "Nessuna risposta su $Porta entro 120 s (PID $($proc.Id) lasciato in vita: 'ferma -N $N')"; exit 1 }
        $sec = [math]::Round(((Get-Date) - $inizio).TotalSeconds, 1)
        Write-Host "Istanza $N pronta in $sec s: http://127.0.0.1:$Porta  PID $($proc.Id)  rotolo $Rotolo mm  $DurataPaginaMs ms/pagina"
        Write-Host "versione: $versione"
        Write-Host "dati: $Dati  (pagine stampate in $Dati\stampate)"
    }
    'ferma' {
        $fermati = Ferma-Istanza
        if ($fermati -eq 0) { Write-Host "Istanza $N non in esecuzione." } else { Write-Host "Istanza $N fermata." }
    }
    'ferma-tutto' {
        $tutti = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | Where-Object { $_.CommandLine -like "*$ClasseMain*" })
        foreach ($p in $tutti) { Write-Host "Fermo PID $($p.ProcessId)"; Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue }
        if (Test-Path $PidDir) { Remove-Item (Join-Path $PidDir 'istanza-*.pid') -Force -ErrorAction SilentlyContinue }
        Write-Host "Fermate $($tutti.Count) istanze di prova."
    }
    'stato' {
        $ps = Processi-Istanza
        if ($ps.Count -eq 0) { Write-Host "Istanza $N : NON in esecuzione (porta $Porta)"; exit 0 }
        Write-Host "Istanza $N : in esecuzione, PID $($ps[0].ProcessId), porta $Porta"
        Write-Host "versione: $(Versione-Risponde)"
        try { $rs = Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 "http://127.0.0.1:$Porta/api/stampante"; Write-Host "stampante: $([Text.Encoding]::UTF8.GetString($rs.RawContentStream.ToArray()))" } catch { Write-Host "stampante: ? ($($_.Exception.Message))" }
        $c = Leggi-Controllo
        Write-Host "comando stampante finta: errore=$($c.errore) rotolo=$($c.rotolo)"
        $pag = @(Get-ChildItem (Join-Path $Dati 'stampate') -Filter '*.png' -ErrorAction SilentlyContinue)
        Write-Host "pagine stampate: $($pag.Count)  in $Dati\stampate"
    }
    'errore' {
        if ($Tipo -notin @('coperchio', 'rotolo-finito', 'nessun-rotolo', 'scollegata')) { throw "-Tipo: coperchio | rotolo-finito | nessun-rotolo | scollegata" }
        $c = Leggi-Controllo
        Scrivi-Controllo $Tipo ([int]$c.rotolo)
        Write-Host "Stampante $N : errore = $Tipo"
    }
    'ripristina' {
        $c = Leggi-Controllo
        Scrivi-Controllo 'ok' ([int]$c.rotolo)
        Write-Host "Stampante $N : tutto a posto"
    }
    'cambia-rotolo' {
        $c = Leggi-Controllo
        Scrivi-Controllo $c.errore $Rotolo
        Write-Host "Stampante $N : rotolo caricato = $Rotolo mm"
    }
    default {
        Write-Host 'Uso: istanza.ps1 avvia|ferma|ferma-tutto|stato|errore|ripristina|cambia-rotolo -N <1..99> [-Reset] [-Rotolo 62|102] [-DurataPaginaMs 1500] [-Tipo ...]'
        exit 2
    }
}
