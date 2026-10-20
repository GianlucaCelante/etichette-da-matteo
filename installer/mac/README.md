# Installer di Etichette per macOS

Il corrispondente Mac dell'MSI di Windows ([`../README.md`](../README.md)): un `.pkg` che installa
l'app, la fa partire da sola all'accensione del Mac e apre la finestra a ogni accesso.

## Cosa installa

| Cosa | Dove | Equivalente su Windows |
|---|---|---|
| App (jar, JRE ridotta, libusb, lanciatore) | `/Applications/Etichette.app` | `C:\Program Files\Etichette` |
| Servizio (LaunchDaemon, parte all'avvio, come root, riavvio automatico) | `/Library/LaunchDaemons/it.etichette.service.plist` | servizio WinSW |
| Apertura della finestra a ogni accesso | `/Library/LaunchAgents/it.etichette.avvio.plist` | scorciatoia in Esecuzione automatica |
| Dati: database SQLite, log, backup | `/Library/Application Support/Etichette` | `C:\ProgramData\Etichette` |

Il lanciatore dell'app aspetta che il servizio risponda su `http://localhost:8765/` e apre la
finestra in modalità app (senza barra degli indirizzi) con Chrome o Edge, se installati, altrimenti
nel browser di serie. Il firewall di macOS, se acceso, viene aperto per la JRE di Etichette.

La stampante si usa **senza driver Brother**: il servizio parla direttamente con l'interfaccia USB
di classe stampante tramite libusb (`PortaLibusb` / `RicercaPortaLibusb`), al posto di `usbprint.sys`.
Se la QL-1100c ha il tasto **Editor Lite** acceso si presenta come disco e non è utilizzabile: va spento.

## Come si costruisce

Va fatto **su un Mac**, e il pacchetto è per l'architettura di quel Mac (Apple Silicon → `arm64`,
Intel → `x86_64`). Prerequisiti: JDK 17 con `jmods` (es. Temurin 17), Maven 3.9, `brew install libusb`.

```bash
installer/mac/Build-Setup.sh                    # versione da pom.xml
installer/mac/Build-Setup.sh --skip-maven       # jar già in target/
installer/mac/Build-Setup.sh --version 0.2.0
```

Produce `target/installer-mac/Etichette-<versione>-<arch>.pkg`. Non è firmato né notarizzato:
al primo apertura dal Finder serve clic destro → Apri; da terminale
`sudo installer -pkg Etichette-<versione>-arm64.pkg -target /`.

## Installare, aggiornare, disinstallare

- **Installa / aggiorna**: stesso `.pkg`, installato sopra. `preinstall` ferma il servizio e copia il
  database in `backup/pre-aggiornamento-<data-ora>`; `postinstall` rimette tutto in piedi.
- **Disinstalla**: `sudo /Applications/Etichette.app/Contents/Resources/uninstall.sh`. I dati restano
  (come su Windows); per cancellare anche quelli aggiungere `--remove-data`.
- **Log**: `/Library/Application Support/Etichette/log/` (`launchd.log` per l'avvio).
- **Stato del servizio**: `sudo launchctl print system/it.etichette.service`.

## Stato della verifica

Scritto e controllato su Windows (sintassi degli script e dei plist, test Java del parser dei
descrittori USB). **Mai eseguito su un Mac**: la build, l'installazione e soprattutto l'accesso alla
stampante vanno provati su un Mac vero. Punti da guardare alla prima prova:

- [ ] la build produce il `.pkg` e l'installazione parte il servizio (`launchctl print`);
- [ ] libusb vede la QL-1100c (nel log: «Stampante connessa»), apre l'interfaccia stampante e la
      lettura dello stato funziona (quirk dei pacchetti vuoti, `docs/mappatura-brother-ql-1100c.md` §5);
- [ ] scollegando e ricollegando il cavo la stampante torna da sola;
- [ ] su Apple Silicon JNA riesce a caricare `libjnidispatch` dalla cartella temporanea (Java di
      Temurin firmato; se macOS lo blocca, impostare `-Djna.tmpdir` su una cartella fissa);
- [ ] il font dell'etichetta è Arial (`/System/Library/Fonts/Supplemental`), non il ripiego Liberation;
- [ ] stampa vera su rotolo 62 e 102 mm.
