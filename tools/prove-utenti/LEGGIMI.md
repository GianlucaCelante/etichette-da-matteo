# Prove con utenti simulati

Due strumenti, entrambi senza toccare `src/main`, il jar, il servizio installato (porta 8765) né `C:\ProgramData\Etichette`:

1. `istanza.ps1`: avvia un'istanza dell'app (il jar vero) con una **stampante Brother QL finta** che completa le stampe.
2. `utente.mjs`: un «utente» con il suo Chrome headless che si comanda a righe singole, come una persona.

Servono: JDK 17, Node 24, Chrome in `C:\Program Files\Google\Chrome\Application\chrome.exe`, il jar già costruito in `target\etichette-*.jar` (`mvn clean package`, non serve rifarlo). Una tantum: `npm install` in questa cartella (già fatto; `playwright-core`, nessun browser scaricato).

RAM: ogni istanza usa `-Xmx300m` (circa 350-450 MB reali), ogni sessione utente un solo Chrome (circa 250-400 MB). Meno istanze e sessioni possibile; a fine prova `ferma-tutto` e `tutti chiudi`.

## 1. Istanza di prova

Da PowerShell: `.\istanza.ps1 <comando> -N <n>`. Da Git Bash: `./istanza.cmd <comando> -N <n>` (stessi argomenti).

| Comando | Cosa fa |
|---|---|
| `avvia -N 1 [-Reset] [-Rotolo 62\|102] [-DurataPaginaMs 1500]` | porta **18770+N**, dati in `dati\utente-N`. `-Reset` cancella la cartella dati: app nuova come per un cliente nuovo (dati di partenza di serie). Attende `/api/versione`, scrive il PID in `pid\istanza-N.pid`. Se l'istanza gira già, non la riavvia (con `-Reset` sì). |
| `ferma -N 1` | ferma solo quella JVM (cercata per riga di comando e porta). |
| `ferma-tutto` | ferma tutte le istanze di prova. |
| `stato -N 1` | PID, versione, stato della stampante come lo vede l'app, comando in corso alla stampante finta, pagine stampate. |
| `errore -N 1 -Tipo coperchio\|rotolo-finito\|nessun-rotolo\|scollegata` | simula l'errore. |
| `ripristina -N 1` | torna alla normalità. |
| `cambia-rotolo -N 1 -Rotolo 62\|102` | cambia il rotolo «caricato» a runtime (per provare il rotolo sbagliato). |

### La stampante finta

- Parla il protocollo vero (stato a 32 byte, job raster, cancellazione del buffer): `MonitorStampante` e il resto dell'app girano identici a quelli di produzione. Cambia solo il trasporto (`PortaSimulata` al posto di `PortaUsb`).
- Ogni pagina «stampata» dura `-DurataPaginaMs` (default 1,5 s) e finisce come PNG in `dati\utente-N\stampate\NNNN.png` (immagine vera dell'etichetta, 300 dpi). Una pagina tutta bianca (l'espulsione del pezzo rovinato dopo un errore) si chiama `NNNN-vuota.png`.
- `dati\utente-N\stampante.log`: cronologia di pagine, comandi ed errori della stampante finta. `dati\utente-N\log\etichette.log` e `console.out.log`: log dell'app.
- **Errori**: si comandano scrivendo `dati\utente-N\stampante.txt` (righe `errore=...`, `rotolo=...`), riletto ogni 150 ms; `errore`/`ripristina` lo fanno per te. Se scatta un errore mentre una pagina è «in stampa», quella pagina **non esce** e l'app riceve la notifica di errore come dalla stampante vera.
  - `coperchio`: l'app va in pausa («Coperchio aperto»), poi, a stampante ripristinata, riprende da sola (espelle un pezzo bianco e rimanda la copia interrotta).
  - `rotolo-finito`: l'app chiede «L'etichetta è uscita intera?» (Sì, prosegui / No, ristampala / Annulla).
  - `nessun-rotolo`: errore «Nessun supporto caricato».
  - `scollegata`: la USB «sparisce»; l'app mostra «scollegata» finché non si ripristina.
- Come parte senza toccare `src/main`: lo script spacchetta il jar in `app\<jar>\` (una tantum per jar, con una copia del jar così un `mvn clean package` non lo trova bloccato), compila `simulatore\src\prove\utenti\*.java` contro le sue classi e lancia `PropertiesLauncher` con `-Dloader.main=prove.utenti.AvviaConSimulata`, che avvia `EtichetteApplication` più una `@Configuration` con due bean `@Primary` (`Porta` e `RicercaPorta`). Il jar non cambia, la versione mostrata è quella vera. Nessun file sotto `src/test`: `mvn test` non ne è influenzato.

## 2. Utente simulato

`node utente.mjs <nome> <comando> [argomenti]` (dalla cartella `tools\prove-utenti`). Il primo comando avvia un piccolo demone con Chrome; gli altri gli parlano. Nomi diversi = sessioni indipendenti (profilo, cookie, cartella schermate proprie).

| Comando | Cosa fa |
|---|---|
| `avvia --porta 18771 [--telefono \| --viewport 1280x800] [--zoom 200] [--mantieni]` | apre la sessione. `--telefono` = 390x844, tocco, User-Agent Android. `--zoom 200` = zoom del browser al 200% (la pagina vede 640x400, come per un utente che ingrandisce). Di norma il profilo parte pulito (nuovo utente, nessun cookie); `--mantieni` lo riusa. |
| `vai <percorso>` | `vai /storico`, `vai stampa`, `vai http://...`. Una sola pagina, sempre la stessa (niente connessioni SSE accumulate). |
| `vedi [tutto]` | schermata a testo, numerata (vedi sotto). |
| `clicca "<testo>"` \| `clicca <n>` | preme un elemento. Per testo: corrispondenza esatta, poi «inizia con», poi «contiene», senza maiuscole/accenti; se ce n'è più d'uno ti dice quali (con i numeri) e non clicca. |
| `scrivi <n\|"etichetta"> "<testo>"` | scrive in un campo (sostituisce il contenuto; `""` lo svuota). Sui menu a tendina sceglie l'opzione per nome. `--tasti` digita tasto per tasto invece di impostare il valore. |
| `tasto Enter\|Tab\|Escape\|ArrowDown\|Control+A ...` | uno o più tasti (anche `invio`, `esc`, `su`, `giu`). |
| `scorri [su\|giu\|alto\|fondo] [px]` | rotellina del mouse al centro dello schermo; dice a che punto sei. |
| `attendi <secondi>` \| `attendi "<testo>" [--max 20]` | aspetta un tempo o che un testo compaia (es. `attendi "Stampate" --max 30`). |
| `carica <n\|"etichetta"> <file>` | allega un file a un campo file (foto di lotti e documenti). |
| `schermata [nome] [--intera] [--png] [--nitida]` | salva un JPEG in `schermate\<nome-utente>\NNN-nome.jpg` e stampa il percorso (aprilo con Read). Dimensione = pixel CSS (telefono 390 px di larghezza); con lo zoom è quella reale. |
| `registro [--nuovi]` | errori di console, richieste fallite, risposte HTTP >= 400 dall'avvio (con metodo, URL, stato). Gli abort di `/api/eventi` alla navigazione sono ignorati e contati. |
| `ridimensiona LxA`, `ricarica`, `indietro`, `stato` | come dicono. |
| `chiudi` | chiude il Chrome di questa sessione e il demone. `node utente.mjs tutti chiudi` chiude tutte le sessioni rimaste. |

Ogni azione aspetta che la UI si assesti (rete ferma e nessuna modifica alla pagina per 300 ms, al massimo 4 s) e risponde con un esito breve e una riga `→ percorso · «titolo»` più eventuali finestre aperte o avvisi visibili. Gli errori (elemento inesistente, disabilitato, coperto) rispondono entro pochi secondi (max 5 s) con un messaggio chiaro ed exit code 1. Dopo 45 minuti senza comandi la sessione si chiude da sola.

### Cosa mostra `vedi`

```
== /stampa · «Stampa etichetta» · 1280x800 · scorrimento 0/0px
[1] link «Stampa» (corrente)
# Stampa etichetta
[6] campo «Cerca etichetta» = "" (suggerimento: «Cerca etichetta…»)
[23] bottone «Una copia in meno» (DISABILITATO)
↓ [13] bottone «Fai una copia adesso» (DISABILITATO)
ORA | ETICHETTA | LOTTO | PESO | SCADENZA | DA
! AVVISO: ...
```

- `[n]` = elemento interattivo (bottone, link, campo, scelta con le opzioni, casella `[x]`/`[ ]`, scheda...), con etichetta e valore. Stati fra parentesi: DISABILITATO, sola lettura, obbligatorio, NON VALIDO, SENZA NOME (un controllo senza testo accessibile, utile da segnalare).
- `#`, `##`: titoli. `! AVVISO:` = messaggi (`role=alert/status`). `↓`/`↑` = fuori dallo schermo (serve scorrere). Con una finestra (dialog) aperta si vede solo il suo contenuto, più gli avvisi fuori.
- I numeri sono quelli dell'ultimo `vedi` (o dell'ultimo «ambiguo»): se la pagina cambia molto, rifai `vedi`; un numero che non c'è più dà errore invece di cliccare altro. `clicca "testo"` non rinumera.
- Massimo 160 righe, `vedi tutto` per le altre.

Da Git Bash i percorsi che iniziano per `/` vengono riscritti in `C:/Program Files/Git/...`: `vai` lo corregge da solo (oppure scrivi `vai storico`).

## Esempio di sessione

```powershell
cd tools\prove-utenti
.\istanza.ps1 avvia -N 1 -Reset
node utente.mjs mario avvia --porta 18771
node utente.mjs mario vai /stampa
node utente.mjs mario vedi
node utente.mjs mario clicca "Base pizza low carb"
node utente.mjs mario clicca "Una copia in più"          # x2 per 3 copie
node utente.mjs mario clicca "Stampa 3 copie"
node utente.mjs mario attendi "Stampate" --max 30
node utente.mjs mario vai /storico ; node utente.mjs mario vedi
node utente.mjs mario registro
node utente.mjs mario chiudi
.\istanza.ps1 ferma -N 1
```

Errore durante una stampa da 5 copie:

```powershell
node utente.mjs mario clicca "Stampa 5 copie"
Start-Sleep 3
.\istanza.ps1 errore -N 1 -Tipo coperchio       # la UI dice «Coperchio aperto»
node utente.mjs mario vedi
.\istanza.ps1 ripristina -N 1                   # dopo ~10 s l'app espelle un pezzo bianco e riprende
```

Più utenti insieme: istanze diverse (`-N 1`, `-N 2`...) e sessioni con nomi diversi (`--porta 18771`, `--porta 18772`). Se due utenti usano la stessa istanza condividono i dati e la stampante (come due telefoni sullo stesso PC).

## Dove finiscono le cose, come ripulire

| Cosa | Dove (tutto ignorato da git) |
|---|---|
| Dati, log, pagine stampate di un'istanza | `dati\utente-N\` (`stampate\`, `stampante.log`, `stampante.txt`, `log\`) |
| PID delle istanze | `pid\` |
| Screenshot e file scaricati dell'utente | `schermate\<nome>\` (`scaricati\`) |
| Profilo Chrome dell'utente | `profili\<nome>\` |
| Stato e log dei demoni | `run\` |
| Jar spacchettato e simulatore compilato | `app\` |

Pulizia: `node utente.mjs tutti chiudi`, `.\istanza.ps1 ferma-tutto`, poi si possono cancellare a mano `dati`, `schermate`, `profili`, `run`, `pid` (e `app` se serve spazio: viene rifatta all'avvio successivo). Non si usa mai `taskkill /IM`: i comandi fermano solo i processi riconosciuti dalla propria riga di comando (la JVM con `prove.utenti.AvviaConSimulata --server.port=<porta>`, i `chrome.exe` con `--user-data-dir` dentro `profili\<nome>`).

## Limiti noti

- Il Chrome è headless e senza GPU; font, anti-aliasing e tocco sono simulati, non sono quelli di un telefono vero. `--telefono` emula un viewport, non un dispositivo.
- La stampante finta non simula: nastro che si inceppa a metà dopo l'invio, risposte lente/mute, annullamento hardware. Gli errori sono solo quelli elencati.
- `vedi` è una lettura del DOM: non vede cosa è coperto da altri elementi (il click lo scopre e lo dice) né i colori; per l'aspetto serve `schermata`.
- Gli elementi cliccabili senza ruolo né testo accessibile vengono riconosciuti solo con l'euristica `cursor: pointer`.
- Le finestre `confirm/alert` del browser vengono accettate da sole (e annotate in `registro`).
- Il campo `date` accetta solo `AAAA-MM-GG`.
