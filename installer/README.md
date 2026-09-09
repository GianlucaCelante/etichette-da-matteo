# Installer di Etichette

Come si costruisce, installa, aggiorna e disinstalla il servizio Windows
"Etichette". Riferimento normativo: [`docs/stack-tecnologico.md`](../docs/stack-tecnologico.md),
sezione "Installazione e consegna a Matteo" - questo README ne descrive
l'attuazione pratica.

Ridotto dagli script dell'agent Java della piattaforma ristoranti
(`restaurant-management-platform/agent-java/scripts`): stesso schema
(Maven -> jlink -> jpackage -> MSI, WinSW per il servizio, script PowerShell
per firewall e scorciatoie), ma per **un solo cliente, un solo ambiente,
un solo servizio**. Non c'e' scelta Local/Dev/Prod, non c'e' registrazione
tenant, non c'e' telemetria OTLP: tutto quello che serviva solo a quello e'
stato tolto (vedi "Cosa e' stato tolto rispetto ad agent-java" sotto).

## Regola prima di tutto

Sul PC di Matteo gira anche il gestionale delle casse. L'installer tocca
**solo**:

- la propria cartella in `C:\Program Files\Etichette\`;
- la propria cartella dati in `C:\ProgramData\Etichette\`;
- il proprio servizio Windows (`Etichette`);
- **due** regole del firewall (TCP 8765 per l'interfaccia web e UDP 5353
  per le risposte mDNS di `etichette.local`, entrambe profili Dominio,
  Privato e Pubblico - il confine di fiducia e' la rete locale, non la
  classificazione che Windows da' alla rete, mai la porta 80);
- le proprie scorciatoie (desktop pubblico, avvio automatico e menu Start di
  tutti gli utenti).

Nessuna rinomina del PC, nessun `powercfg`, nessun'altra impostazione di
Windows.

## File di questa cartella

| File | Cosa fa |
|---|---|
| `Build-Setup.ps1` | Script di build: Maven -> jlink -> jpackage -> MSI. |
| `Etichette.xml` | Configurazione WinSW del servizio (nome, eseguibile, ambiente, log, riavvio). |
| `install_service.ps1` | Crea la cartella dati, apre la porta nel firewall, registra/avvia il servizio, crea le scorciatoie. Lo esegue l'MSI stesso, elevato, in automatico. |
| `uninstall_service.ps1` | Ferma e deregistra il servizio; di default **non** cancella i dati (serve `-RemoveData`). Lo esegue l'MSI in disinstallazione. |
| `update.ps1` | Aggiornamento "installa sopra": backup del database, ferma il servizio, lancia il nuovo MSI, ripristina il servizio se fallisce. |
| `service_common.ps1` | Funzioni condivise (stop di processi/servizio) usate dagli script sopra. |
| `wix\main.install.wxs` | Sostituzione del `main.wxs` di jpackage: aggiunge le azioni che installano/avviano il servizio in automatico dentro l'MSI. |
| `etichette.ico` | Icona dell'app (etichetta stilizzata verde), generata con Python + Pillow. |

## Come si costruisce

Prerequisiti (gia' presenti sul PC di sviluppo di Gianluca):

- JDK 17 con `bin\jpackage.exe` e `bin\jlink.exe` (verifica `$env:JAVA_HOME`,
  oppure passa `-JdkHome`);
- WiX Toolset 3.x sul PATH (`candle.exe`, `light.exe`, `dark.exe`) - qui e'
  in `C:\Program Files (x86)\WiX Toolset v3.14\bin`, non sempre nel PATH di
  sistema:
  ```powershell
  $env:Path = "C:\Program Files (x86)\WiX Toolset v3.14\bin;" + $env:Path
  ```
- Maven sul PATH (evitabile con `-SkipMaven` se il jar e' gia' pronto);
- accesso a Internet la prima volta (WinSW viene scaricato da GitHub e messo
  in cache in `target\winsw-cache\`, con verifica SHA-256).

Build normale, dalla radice del repository:

```powershell
cd installer
.\Build-Setup.ps1
```

Senza `-Version`, la versione si legge da `..\pom.xml` (`<version>` del
progetto, tolto l'eventuale `-SNAPSHOT`): jar Maven, MSI e quello che
`/api/versione` restituisce dicono sempre la stessa cosa senza scriverla in
due posti. Produce `..\target\installer\Etichette-<versione>.msi`. Per
forzare una versione diversa da quella nel pom (per esempio in prova):

```powershell
.\Build-Setup.ps1 -Version "1.0.0"                # forza la versione invece di leggerla dal pom
.\Build-Setup.ps1 -SkipMaven                       # jar gia' costruito (versione dal pom), ripacchettizza solo
.\Build-Setup.ps1 -CodeSign                        # firma l'MSI (richiede SIGNING_CERT_PATH/SIGNING_CERT_PASSWORD)
```

L'MSI non e' firmato per la prima versione (deciso in
`docs/stack-tecnologico.md`): SmartScreen mostra "editore sconosciuto" e si
va avanti, va bene finche' installa Gianluca in assistenza remota o di
persona. `-CodeSign` c'e' gia', pronto per quando ci sara' un certificato.

Versione dell'app, dell'MSI e tag git devono coincidere: la versione risolta
(dal pom o da `-Version`) e' quella scritta nell'MSI (`--app-version` di
jpackage, `ProductVersion`) e nel nome del jar cercato in `target\`
(`etichette-<versione>.jar`, nome esatto: se `target\` contiene jar di
build precedenti con un'altra versione, vengono ignorati).

**Prima di taggare una release**, aggiorna `<version>` in `pom.xml` (root)
alla versione da rilasciare, poi costruisci senza passare `-Version`.

## Come si installa

Un doppio clic sull'MSI installa tutto: copia i file, crea la cartella dati,
apre la porta nel firewall, registra e avvia il servizio, crea le
scorciatoie. Non serve nessun passo manuale successivo.

Installazione silenziosa (per esempio via assistenza remota):

```powershell
msiexec /i "Etichette-1.0.0.msi" /qb
```

Dopo l'installazione: `C:\Program Files\Etichette\` contiene l'app e la
JRE ridotta, il servizio `Etichette` e' `Running`, e sul desktop pubblico, in
"Esecuzione automatica" e nel menu Start c'e' la scorciatoia "Etichette"
(Edge in modalita' app su `http://localhost:8765/`) - nessuna voce di menu
Start creata da jpackage: il suo lanciatore nativo non funzionerebbe per
questa app ("Failed to launch JVM", avvia la JVM direttamente invece che
tramite il servizio Windows).

## Come si aggiorna

Un aggiornamento e' un MSI nuovo, installato "sopra" quello vecchio. Lo fa
Gianluca in assistenza remota:

```powershell
cd "cartella dove hai copiato il nuovo MSI"
& "C:\Program Files\Etichette\app\update.ps1" ".\Etichette-1.1.0.msi"
```

`update.ps1` puo' essere lanciato da qualunque cartella (la copia nel
repository, una copiata sul Desktop, una chiavetta): non dipende dal trovare
`Etichette.exe` (WinSW) accanto a se stesso, vedi il problema descritto sotto.

1. si autoeleva (UAC) se non e' gia' in una shell da amministratore;
2. copia il database (e i file `-wal`/`-shm`) in
   `ProgramData\Etichette\backup\pre-aggiornamento-<data-ora>\`;
3. chiude eventuali finestre di Edge aperte in modalita' app verso
   l'indirizzo locale del servizio (tengono aperta una connessione, per
   esempio un canale SSE per gli aggiornamenti in tempo reale, che puo'
   impedire allo spegnimento pulito di completarsi in tempo), poi ferma il
   servizio "Etichette" **per nome** tramite la SCM (`Get-Service` /
   `Stop-Service`, fino a 40s; se resta bloccato in "Stopping" termina
   l'albero di processi del servizio), cosi' `msiexec` non trova ne' i file
   ne' Edge a tenere occupata la sessione;
4. lancia `msiexec /i` con `/norestart` (il banco etichette non si riavvia
   mai da solo) e `MSIRESTARTMANAGERCONTROL=Disable` (Windows Installer non
   scansiona nemmeno i file in uso: una seconda rete di sicurezza, non la
   prima, dato il passo 3 sopra): l'azione dentro l'MSI registra e riavvia
   il servizio sulla nuova versione;
5. riapre la finestra dell'app, se ne era stata chiusa una al passo 3;
6. se `msiexec` fallisce, richiama `install_service.ps1 -Silent` per
   rimettere in piedi il servizio sulla versione ancora presente sul disco
   (Windows Installer ripristina i file precedenti quando un'installazione
   fallisce), invece di lasciare il banco etichette spento.

Il database si backuppa anche da solo ogni notte (funzione del servizio, non
di questo installer - vedi `docs/stack-tecnologico.md`, sezione "Backup e
dati"); il backup pre-aggiornamento qui e' una rete di sicurezza in piu',
indipendente, per il momento esatto dell'aggiornamento.

## Come si disinstalla

Da "Programmi e funzionalita'" (o `msiexec /x Etichette-1.0.0.msi`): ferma
e toglie il servizio, le regole del firewall e le scorciatoie, ma **lascia
`C:\ProgramData\Etichette` con i dati** (database SQLite, log, backup) - per
decisione di progetto, cosi' una disinstallazione per sbaglio non perde lo
storico.

Per cancellare anche i dati, a mano:

```powershell
& "C:\Program Files\Etichette\app\uninstall_service.ps1" -RemoveData
```

(uno stato disallineato nel messaggio del task originale menzionava anche
`-KeepData`: il comportamento attuato qui, coerente con
`docs/stack-tecnologico.md`, e' che i dati **restano di default**; solo
`-RemoveData` li cancella davvero. Segnalalo se l'intenzione era diversa.)

## Dove stanno dati e log

```
C:\ProgramData\Etichette\
    log\        <- log di bootstrap di WinSW (prima che Spring Boot inizializzi il proprio logging)
    backup\     <- backup del database (notturni dal servizio + pre-aggiornamento da update.ps1)
    *.db        <- database SQLite (nome esatto deciso dal servizio, non da questo installer)
```

Permessi: SYSTEM e Administrators in controllo pieno, Users in sola lettura -
cosi' un utente non amministratore del PC condiviso con il gestionale non
puo' toccare il database.

## Come si prova (senza installare su questo PC)

**Non installare il servizio ne' l'MSI sul PC di sviluppo di Gianluca.** La
build si puo' verificare fino in fondo senza installare nulla:

1. Costruisci l'MSI (eventualmente con un jar finto, vedi sotto).
2. Verifica la sintassi degli script senza eseguirli:
   ```powershell
   Get-ChildItem installer\*.ps1 | ForEach-Object {
       $errors = $null
       [System.Management.Automation.Language.Parser]::ParseFile($_.FullName, [ref]$null, [ref]$errors) | Out-Null
       if ($errors.Count -gt 0) { Write-Warning "$($_.Name): $($errors -join '; ')" }
   }
   ```
   Se e' installato, [PSScriptAnalyzer](https://github.com/PowerShell/PSScriptAnalyzer)
   da' un controllo piu' approfondito: `Invoke-ScriptAnalyzer -Path installer -Recurse`.
3. Estrai l'MSI senza installarlo (modalita' amministrativa) e ispeziona i
   file:
   ```powershell
   msiexec /a "Etichette-1.0.0.msi" /qn TARGETDIR="C:\qualche\cartella\estratta"
   ```
   Deve comparire `Etichette\Etichette.exe` (lanciatore di jpackage,
   *non* usato direttamente: e' un residuo inevitabile di jpackage, l'avvio
   vero passa dal servizio o dalla scorciatoia), `Etichette\app\` (jar,
   WinSW rinominato, XML, script, icona) e `Etichette\runtime\` (JRE
   ridotta con jlink).
4. Decompila l'MSI con `dark.exe` (incluso in WiX) per controllare le
   custom action e il `ServiceControl` senza installare:
   ```powershell
   dark.exe -x cartella_media Etichette-1.0.0.msi decompilato.wxs
   ```
   Cerca `EtichetteInstallService`, `EtichetteUninstallService` e
   `ServiceControl Name="Etichette"` nel file prodotto.

**La prova vera dell'installazione va fatta in Windows Sandbox** (il
servizio registrato, il firewall, le scorciatoie, la disinstallazione):
Windows Sandbox non vede periferiche USB, quindi non prova la stampante -
quella si prova sul PC di sviluppo con gli spike in `tools/spike-jna/` e
`tools/spike-java2d/`. Passi:

1. Attiva "Windows Sandbox" da "Funzionalita' di Windows" (richiede
   Windows 10/11 Pro o Enterprise), riavvia se richiesto.
2. Apri Windows Sandbox, copia dentro (trascina, o condividi una cartella)
   l'MSI prodotto.
3. Installa: doppio clic sull'MSI.
4. Verifica:
   - `Get-Service Etichette` -> `Running`;
   - `Get-NetFirewallRule -DisplayName Etichette` e `-DisplayName "Etichette mDNS"`
     -> presenti, profili Domain/Private/Public;
   - scorciatoia "Etichette" sul desktop pubblico e in
     `shell:common startup`;
   - `C:\ProgramData\Etichette\` creata con `log\` e `backup\`;
   - `http://localhost:8765/` risponde (con un servizio vero; con il jar
     finto di prova risponde "ok", vedi sotto).
5. Disinstalla da "App installate" e verifica che il servizio sparisca ma
   `ProgramData\Etichette` resti.

### Provare la catena di build con un jar finto

Finche' il servizio vero non esiste, `Build-Setup.ps1` si puo' provare con
un jar minimo (una classe `it.etichette.EtichetteApplication` che apre un
server HTTP sulla porta 8765 e risponde "ok"):

```powershell
javac -d classes EtichetteApplication.java
jar --create --file etichette-0.1.0.jar --main-class it.etichette.EtichetteApplication -C classes .

.\Build-Setup.ps1 -Version "0.1.0" -SkipMaven -JarPath "C:\percorso\etichette-0.1.0.jar"
```

Questo verifica la catena jlink -> jpackage -> MSI, non il servizio vero:
non installa niente, quindi non prova ne' il servizio ne' `java.desktop`/AWT
per il rendering reale dell'etichetta.

## Elenco dei moduli jlink: da rivedere quando il jar esiste

L'elenco moduli in `Build-Setup.ps1` (`java.desktop`, `java.sql`,
`java.naming`, `java.xml`, `java.management`, `java.instrument`,
`java.security.jgss`, `java.net.http`, `jdk.unsupported`, `jdk.crypto.ec`,
`jdk.charsets`, `jdk.zipfs`, oltre a `java.base`/`java.logging`) e' un
elenco ragionato ma **generoso**, scritto prima che il servizio esistesse.
Quando il jar vero e' pronto:

```powershell
jdeps --multi-release 17 --print-module-deps --ignore-missing-deps target\etichette-*.jar
```

e confronta l'output con l'elenco in `Build-Setup.ps1`: aggiungi quello che
manca (per esempio `jdk.localedata` se serve la formattazione italiana di
data/valuta), togli quello che risulta inutile per tenere la JRE ridotta.

## Cosa e' stato tolto rispetto ad agent-java

| In agent-java | Qui |
|---|---|
| Scelta ambiente Local/Dev/Prod/Custom, `Configure-Environment.ps1`, `agent-env.json`, `agent-env.default.txt` | Un solo ambiente, nessuna scelta: `Etichette.xml` ha gia' i valori giusti |
| `-AutoInstallService` come opzione, richiede `-Environment` | L'installazione automatica del servizio e' il comportamento **predefinito**, sempre attivo: un solo cliente non deve scegliere una modalita' di build |
| Rollback/downgrade via MSI (`JpAllowDowngrades`, `JP_DOWNGRADABLE_FOUND` che rimuove il prodotto piu' nuovo) | Il downgrade via MSI resta **bloccato** (comportamento di default di jpackage): se un aggiornamento fallisce ci pensa `update.ps1` a rimettere in piedi il servizio sulla versione ancora sul disco, non serve un vero rollback via Windows Installer |
| `RmpLaunchRegistrationPage` (apre una pagina di registrazione tenant dopo l'installazione, da `InstallUISequence`) | Non esiste una registrazione tenant; le scorciatoie create da `install_service.ps1` bastano per aprire l'app |
| Verifica dei bundle SPA "cold-boot" nel jar (POS, KDS, code...) | Non pertinente: un'unica interfaccia React incorporata nel jar, senza bundle multipli da verificare |
| Porta LAN 8081 per i monitor cucina, profilo firewall `Any` | Porta 8765, profili **Domain+Private+Public** (il confine di fiducia e' la rete locale, non la classificazione della rete; mai la 80, che potrebbe servire al gestionale di cassa) |
| Launcher elevato con autoelevazione + file-picker (`update_agent.ps1`), scoperta automatica dell'MSI, marcatura `RMP_AGENT_SERVICE_LAUNCH` per bloccare avvii diretti pericolosi | `update.ps1` prende il percorso dell'MSI come parametro esplicito (lo passa Gianluca); nessuna scoperta automatica ne' marcatura anti-avvio-diretto (fuori scopo per un solo cliente) |
| Notifiche di aggiornamento in-app, endpoint `/admin/v1/updates/*`, Task Scheduler per l'hand-off asincrono | Nessun aggiornamento automatico nella prima versione (deciso in `docs/stack-tecnologico.md`): un cliente solo non vale l'infrastruttura |
| `jdk.jfr` (Java Flight Recorder per diagnosi hub-freeze), `jdk.localedata` | Non nell'elenco moduli jlink iniziale: da aggiungere se servono davvero (vedi sezione sopra) |
| Firma del certificato producer "Restaurant Management Platform" | `--vendor "Gianluca Celante"`, `--copyright "Copyright (C) 2026 Gianluca Celante"` |

## Problemi incontrati durante lo sviluppo

- **Commenti XML con `--`**: sia `Etichette.xml` sia `wix\main.install.wxs`
  avevano, nei commenti descrittivi, riferimenti a opzioni con due trattini
  (`--runtime-image`, `--resource-dir`). Gli standard XML vietano `--`
  dentro un commento (`<!-- ... -->`): `candle.exe` fallisce con
  `CNDL0104` e WinSW non caricherebbe affatto `Etichette.xml`. Risolto
  riformulando i commenti senza il doppio trattino letterale. Verificato con
  `grep -- '--'` su entrambi i file dopo la correzione, e con una build
  completa che ha superato `candle.exe`/`light.exe`.
- **PSScriptAnalyzer non installato** su questo PC: la verifica sintattica
  qui si e' fermata al parser di `System.Management.Automation.Language`
  (nessun errore su nessuno dei cinque script). Se lo si installa in futuro
  (`Install-Module PSScriptAnalyzer -Scope CurrentUser`), vale la pena
  rilanciare `Invoke-ScriptAnalyzer -Path installer -Recurse` per un
  controllo piu' approfondito (variabili inutilizzate, verbi non
  approvati, eccetera).
- **`-dJpAllowDowngrades=yes` passato comunque da jpackage**: la riga di
  comando che jpackage genera per `candle.exe` include sempre
  `-dJpAllowDowngrades=yes`, indipendentemente da quello che fa
  `main.install.wxs`. Non e' un problema: il nostro template non referenzia
  mai `$(var.JpAllowDowngrades)` (a differenza del riferimento di
  agent-java, che lo usa per abilitare condizionalmente il rollback) - il
  blocco dei downgrade e' scritto qui in modo incondizionato
  (`JpUpgradeVersionOnlyDetectDowngrade="yes"` fisso). Verificato
  decompilando l'MSI con `dark.exe`: `JpDisallowDowngrade` e' presente e
  agganciato a `JP_DOWNGRADABLE_FOUND`.
- **Aggiornamento reale 0.1.0 -> 0.1.1 (8 settembre 2026), due bug trovati
  sul PC di sviluppo**: `update.ps1` era stato lanciato dalla cartella del
  repository (non da `C:\Program Files\Etichette\app\`); il suo tentativo di
  fermare il servizio delegava a `uninstall_service.ps1 -StopOnly`, che
  cerca `Etichette.exe` (WinSW) accanto a se stesso - non presente nel
  repository (solo dentro l'MSI costruito) - quindi l'arresto veniva
  saltato in silenzio e `msiexec` partiva col servizio ancora attivo,
  facendo comparire la finestra "Etichette sta usando file..." di Windows
  Installer. **Risolto** fermando il servizio per nome tramite la SCM
  (`Stop-Service`), che non dipende da alcun percorso di file, piu' la
  chiusura delle finestre Edge in modalita' app e
  `MSIRESTARTMANAGERCONTROL=Disable` come reti di sicurezza aggiuntive (vedi
  "Come si aggiorna" sopra). Secondo bug, nello stesso log: il passo di
  registrazione del servizio richiamava sempre `Etichette.exe install`
  anche quando il servizio esisteva gia', e WinSW scriveva un
  `FATAL - Servizio specificato gia' esistente` nel log (non bloccante, ma
  fuorviante). **Risolto** rendendo la registrazione idempotente:
  `install_service.ps1` chiama `install` solo per un servizio nuovo; per uno
  gia' esistente si limita a fermarlo e a farlo ripartire piu' sotto, senza
  richiamare `install` ne' alcun altro comando WinSW al suo posto - un primo
  tentativo di sostituirlo con `Etichette.exe refresh` e' stato tolto lo
  stesso giorno: quel comando non esiste in WinSW 2.12 (`--help` elenca
  install/uninstall/start/stop/stopwait/restart/restart!/status/test/testwait)
  e scriveva un secondo FATAL, `Unknown command: refresh`, nel log. Non
  serve comunque nulla al posto di "install": WinSW rilegge `Etichette.xml`
  da solo a ogni avvio del servizio.
- **Lanciatore non elevato bloccato oltre dieci minuti dopo un aggiornamento
  reale (9 settembre 2026)**: `Start-Process -Wait` nel ramo di
  autoelevazione aspetta l'INTERO albero di processi del figlio quando usa
  `-Verb RunAs` (ShellExecute), non solo il processo elevato; siccome lo
  script elevato riapre poi Edge (`Open-EtichetteAppWindows`), il lanciatore
  restava bloccato finche' l'utente non chiudeva anche quella finestra.
  **Risolto** sostituendo `-Wait` con `Process.WaitForExit()` (aspetta solo
  il PID elevato) e avviando Edge staccato dall'albero di processi
  (`explorer.exe` sul collegamento pubblico, o in ripiego `cmd /c start`)
  invece che come figlio diretto dello script.

## Cosa resta da provare

- [ ] Installazione reale in Windows Sandbox: servizio, firewall,
      scorciatoie, apertura di `http://localhost:8765/`, disinstallazione
      con dati preservati.
- [ ] Lo stesso con il jar vero (quando esiste), per controllare che
      `java.awt.headless=true` basti a far partire il rendering Java 2D in
      un servizio senza sessione grafica (Sessione 0) e che l'elenco moduli
      jlink sia davvero sufficiente (vedi jdeps sopra).
- [ ] Ripetere l'aggiornamento reale "installa sopra" con la versione
      corretta di `update.ps1` (fermata per nome, chiusura di Edge,
      `MSIRESTARTMANAGERCONTROL`): il primo tentativo (0.1.0 -> 0.1.1, 8
      settembre 2026) ha trovato i due bug descritti sopra, corretti ma non
      ancora riverificati con un aggiornamento reale sul servizio installato.
      Include il caso di fallimento (per esempio disconnettendo la rete a
      meta' installazione) per controllare che il servizio si rimetta
      davvero in piedi sulla versione precedente.
- [ ] Espansione di `%BASE%` dentro `<arguments>` in `Etichette.xml`: usata
      per il percorso del jar (`%BASE%\etichette.jar`), e' documentata nel
      file XML come "da verificare"; se non dovesse funzionare come negli
      esempi WinSW conosciuti, sostituire con il percorso assoluto fisso
      `C:\Program Files\Etichette\app\etichette.jar` (la cartella
      d'installazione e' fissa per decisione di progetto, quindi il
      percorso assoluto e' comunque stabile).
- [ ] Firma del certificato (`-CodeSign`) quando ci sara' un certificato
      vero: il flag esiste ed e' stato solo verificato per la parte
      "certificato assente -> avviso e si va avanti", non per una firma
      reale.

## Build con poca memoria

Se il PC ha poca RAM libera (la build completa lancia anche `npm run build` dentro Maven), si va in due passi: prima l'interfaccia a mano (`cd ui && npm run build`), poi il jar senza Node e senza npm ma con la copia di `ui/dist` (`mvn clean package -DskipTests -Dskip.installnodenpm=true -Dskip.npm=true`; in PowerShell scrivere `mvn --% ...` o mettere le opzioni fra virgolette), e infine `.\Build-Setup.ps1 -SkipMaven`, che fa solo jlink e jpackage sul jar già pronto.
