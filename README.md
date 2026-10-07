# Etichette da Matteo

Piccola applicazione per stampare etichette alimentari (preparazioni e ingredienti di una pizzeria) su una **Brother QL-1100c** collegata via USB.

## Stato del progetto (2026-09-09)

- **Scheda tecnica e ricetta** (2026-10-07, versione 0.1.69, chiesto da Matteo alla demo): ogni ingrediente ha la sua scheda tecnica (valori per 100 g, allergeni contenuti, tracce); le ricette si scrivono in Ingredienti › «Ricette» (quantità in g, kg, ml o l, porzioni ottenute). Valori nutrizionali, «può contenere» ed elenco ingredienti in ordine di peso si calcolano da soli e restano correggibili a mano; le porzioni buttate dopo si segnano nello Storico. Dettagli in [`docs/api.md`](docs/api.md), «Scheda tecnica e ricetta».
- **Allineata al mockup finale e installata come servizio** (2026-09-09, versione 0.1.11): l'etichetta vive dentro il prodotto (niente tipi né galleria), l'etichetta in mano non è mai più alta che larga (corta attraverso il nastro o lunga con lunghezza automatica, regola della funzione `misuraEtichetta` del prototipo), anteprima che si adatta e scorre come nel mockup, testata condivisa con le azioni della vista, gruppi della scheda del prodotto come nel prototipo. Cinque prove reali del coperchio aperto con ripresa automatica (espulsione della copia interrotta e rinvio). Rete: porta 8765, regole del firewall su tutti i profili, responder mDNS proprio (`etichette.local`, non su Android: si usa il QR). MSI installato e aggiornato più volte sul PC di sviluppo (`installer/update.ps1`).

- **Stampante mappata e verificata**: comunicazione raw via USB senza driver Brother, lettura stato e impostazioni, stampe di prova in modalità raster riuscite su entrambi i rotoli (62 e 102 mm). Tutto in [`docs/mappatura-brother-ql-1100c.md`](docs/mappatura-brother-ql-1100c.md).
- **Interfaccia disegnata**: [canvas di design](https://claude.ai/code/artifact/e8537537-bab9-4cce-a2f3-0dec57fc9bd2) con le nove schermate del PC — Stampa, stampa in corso, errore, Etichette (dati del prodotto ed etichetta insieme), scelta dell’etichetta, etichetta nuova a blocchi, Storico, Impostazioni, aggancio dei telefoni — e le nove del telefono, che ripetono le stesse quattro voci: stampa, in stampa, errore, stampata, scheda del prodotto, scelta dell’etichetta, etichetta nuova, storico, impostazioni. Sorgenti degli artboard in [`design/`](design/).
- **Forma dell'app e funzioni decise**: un unico programma sul PC collegato via USB, che pubblica l'interfaccia sulla rete locale e la mostra anche in una finestra sul PC. Elenco delle funzioni della prima versione in [`docs/funzionalita-prima-versione.md`](docs/funzionalita-prima-versione.md).
- **Prova di stampa dei corpi fatta** (2026-09-05, rotolo 102 mm): la zona a due colonne esce come disegnata — in raster il layout e’ libero, non serviva nessuna capacita’ nuova. Ma i corpi dichiarati dal canvas non stanno nell’etichetta: **a 6 pt l’altezza della x e’ 1,1 mm, sotto il minimo di legge di 1,2 mm** (il primo corpo che lo supera e’ 7 pt, 1,3 mm), e ai corpi dichiarati l’etichetta viene alta **96,6 mm** se larga 58,9 (il canvas la disegna alta 32) oppure **66,8 mm** se larga 107,7 — in tutti e due i casi piu’ dei 58,9 mm che il rotolo da 62 concede sul lato corto. Serve l’etichetta originale del cliente per misurarla, e poi si rifa’ la scaletta. Dettagli in [`docs/prova-corpi.md`](docs/prova-corpi.md).
- **Prima versione completa dell'app** (2026-09-08, pomeriggio): prodotti ed etichette con l'editor a blocchi, renderer Java 2D con anteprima identica alla stampa, stampa con lotto e scadenza proposti, storico con ristampa, telefoni collegati per nome, schemi del lotto. Contratto delle API in [`docs/api.md`](docs/api.md); verificata con stampe reali dal browser. Restano gli orientamenti aperti nel documento dello stack (rischi) e la prova dell'MSI in Windows Sandbox.
- **Scheletro dell'applicazione costruito** (2026-09-08): progetto Maven con servizio, interfaccia e installatore; la fetta verticale Impostazioni funziona davvero (stato della stampante dal vivo via SSE, stampa di prova dal browser, MSI con JRE inclusa provato con il jar vero). Prodotti, etichette, storico e renderer a blocchi sono la fase successiva.
- **Stack tecnologico deciso** (2026-09-08): Java 17 + Spring Boot come servizio Windows, JNA per la stampante, resa dell'etichetta in Java 2D, SQLite, interfaccia React come PWA. Motivazioni, alternative scartate e verifiche in [`docs/stack-tecnologico.md`](docs/stack-tecnologico.md); Tre spike già fatti sulla stampante reale: lettura dello stato via JNA e porting della stampa raster con job identico a quello Python ([`tools/spike-jna/`](tools/spike-jna/LEGGIMI.md)), resa del testo in Java 2D con la scaletta dei corpi confermata entro 0,04 mm ([`tools/spike-java2d/`](tools/spike-java2d/LEGGIMI.md)).

## Struttura

```
docs/     mappatura della stampante (protocollo, stati, quirk, tabelle supporti),
          funzioni concordate e catture del driver Brother
tools/    script Python di riferimento per parlare con la stampante (nessuna dipendenza oltre Pillow)
          e gli spike Java (spike-jna: stato e stampa via JNA; spike-java2d: resa del testo)
design/   artboard del canvas di design (.dc.html), layout (canvas.json)
          e canvas-pubblicato.html, la versione assemblata da aprire nel browser
artefatti-claude/  copie scaricate degli artefatti online (prototipo «Banco etichette» e canvas)
pom.xml, src/      il servizio (Java 17, Spring Boot): stampante via JNA, resa, dati, API, mDNS
ui/       l'interfaccia (React 19, Vite, TypeScript), incorporata nel jar dalla build Maven
installer/  MSI con JRE inclusa e servizio Windows (jpackage, WinSW, script PowerShell)
```

## L'applicazione: come si costruisce e si prova

Prerequisiti sul PC di sviluppo: JDK 17 (con `jlink` e `jpackage`), Maven 3.9, WiX 3.14 per l'MSI. Node non serve: la build Maven scarica il suo.

```
mvn verify                         # servizio + interfaccia + test; jar in target/etichette-<v>.jar
mvn -Dskip.ui=true verify          # solo il servizio (piu' rapido, senza interfaccia)
java -jar target/etichette-0.1.0-SNAPSHOT.jar            # porta 8765, dati in ./data
java -jar target/etichette-0.1.0-SNAPSHOT.jar --server.port=18080   # se la 8765 e' occupata

cd ui && npm ci && npm run dev     # interfaccia in sviluppo su :5173, /api inoltrato al servizio su :8765
cd ui && npm run mock -- 8099      # servizio finto per lavorare senza stampante

powershell installer/Build-Setup.ps1 -Version 0.1.0      # MSI in target/installer/
```

Sul PC di sviluppo la porta 8080 e' occupata dall'agent RMP: per questo la porta di serie dell'app e' la 8765 (vedi `docs/stack-tecnologico.md`, rischio 8). Dati, log e database SQLite stanno in `ETICHETTE_DATA_DIR` (in produzione `C:\ProgramData\Etichette`). API in `/api` (stato stampante, eventi SSE, stampa di prova, impostazioni, rete, versione); il resto delle rotte serve l'interfaccia.

## Strumenti per la stampante

Richiedono Python 3 su Windows con la stampante collegata e accesa. Il percorso USB è quello dell'esemplare in uso (seriale nel percorso, vedi `tools/ql_probe.py`).

```
python tools/ql_probe.py                    # ID USB, stato porta, stato completo (32 byte)
python tools/ql_settings_readout.py         # impostazioni statiche nelle tre modalità comando (sola lettura)
python tools/ql_testprint.py --render-only  # genera l'anteprima PNG dell'etichetta di prova
python tools/ql_testprint.py                # stampa l'etichetta di prova (nastro continuo 62 o 102 mm)
```

## Rotoli supportati dal cliente

| Rotolo | Area stampabile | Orientamento testo |
|---|---|---|
| 62 mm continuo | 58,9 mm × libera (696 punti) | attraverso il nastro (righe larghe 58,9 mm) |
| 102 mm continuo | 98,6 mm × libera (1164 punti) | attraverso il nastro |

Risoluzione 300 dpi, taglio automatico, rilevamento del rotolo dallo stato della stampante. L'orientamento con il testo lungo il nastro sul rotolo da 62 resta da decidere con l'etichetta originale del cliente (vedi `docs/api.md`, sezione Resa).
