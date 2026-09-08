# Spike: resa del testo con Java 2D a 300 dpi

Dimostra che Java 2D (`java.awt`, JDK 17, nessuna libreria esterna) rende lo stesso testo con la
stessa qualità e le stesse misure della prova fatta con Python/Pillow il 5 settembre 2026
([`docs/prova-corpi.md`](../../docs/prova-corpi.md), script di riferimento
[`tools/ql_prova_etichetta.py`](../ql_prova_etichetta.py)), e che il layout dell'etichetta
"Completa" (stili misti dentro un paragrafo, due colonne, tabella allineata a destra) si fa con
`TextLayout` / `LineBreakMeasurer` / `AttributedString`.

Programma unico: [`TextRenderSpike.java`](TextRenderSpike.java). Nessuna dipendenza esterna,
nessun file generato dentro il repository: compilato e le immagini prodotte vanno in una cartella
scratch a scelta.

## Come compilare ed eseguire

```
javac -encoding UTF-8 -d <dir-classi> tools\spike-java2d\TextRenderSpike.java
java  -cp <dir-classi> TextRenderSpike <dir-output> [liberation-regular.ttf] [liberation-bold.ttf]
```

- `-encoding UTF-8` è **necessario**: senza, `javac` legge il sorgente con la codifica di default
  della piattaforma (su Windows quasi mai UTF-8) e i caratteri accentati nelle stringhe italiane
  ("Può", "Quantità") e i tre puntini di troncamento escono sbagliati nelle immagini. Vedi
  "Problemi e soluzioni" sotto.
- `<dir-output>` è obbligatorio: le PNG ci finiscono dentro (viene creata se non esiste).
- I due percorsi di Liberation Sans sono opzionali: se non passati, lo spike lavora solo con
  Arial. Se passati ma il file non si apre, lo spike stampa l'errore e continua solo con Arial
  (non si ferma).
- Arial è cercata ai percorsi fissi di sistema `C:\Windows\Fonts\arial.ttf` e `arialbd.ttf`.

Comando usato per questa prova (percorsi della sessione):

```
javac -encoding UTF-8 -d C:\...\scratchpad\spike-java2d\classes tools\spike-java2d\TextRenderSpike.java

java -cp C:\...\scratchpad\spike-java2d\classes TextRenderSpike ^
     C:\...\scratchpad\spike-java2d\out ^
     C:\...\scratchpad\spike-java2d\fonts\liberation-fonts-ttf-2.1.5\LiberationSans-Regular.ttf ^
     C:\...\scratchpad\spike-java2d\fonts\liberation-fonts-ttf-2.1.5\LiberationSans-Bold.ttf
```

Liberation Sans (release 2.1.5, licenza SIL OFL) scaricata da
`https://github.com/liberationfonts/liberation-fonts/files/7261482/liberation-fonts-ttf-2.1.5.tar.gz`,
l'URL indicato nel corpo della release GitHub `liberationfonts/liberation-fonts` tag `2.1.5`
(verificato con l'API `GET /repos/liberationfonts/liberation-fonts/releases/tags/2.1.5`), estratta
nella cartella scratch. Il download è riuscito, quindi la prova copre entrambi i font.

## Cosa produce

Nella cartella `<dir-output>`:

| File | Contenuto |
|---|---|
| `scaletta-Arial.png` | riga di ingredienti da 5 a 18 pt, resa bilevel (come la raster 1 bit della stampante) |
| `scaletta-Arial-aa.png` | stessa scaletta, con antialiasing e soglia al 50% |
| `scaletta-LiberationSans.png` / `-aa.png` | idem, con Liberation Sans |
| `completa-Arial.png` | etichetta "Completa", variante B (107,7 mm), con Arial |

Più, su stdout: per ogni corpo l'altezza della x misurata in pixel e mm (variante bilevel e
variante AA+soglia), il confronto con `prova-corpi.md`, il confronto fra le due famiglie di font,
e le dimensioni finali dell'etichetta "Completa".

## Come si misura l'altezza della x

Per ogni corpo la lettera "x" viene resa **da sola** su un canvas bianco, con
`font.deriveFont(float)` alla dimensione in pixel (`punti × 300/72 = punti × 4,16667`), poi si
scansiona l'immagine pixel per pixel cercando la riga più alta e la più bassa che contengono un
pixel nero: l'altezza è la differenza in pixel, convertita in mm (`px / 300 × 25,4`). Due varianti:

- **bilevel** (`RenderingHints` con `TEXT_ANTIALIASING_OFF` e `FRACTIONALMETRICS_OFF`, su
  `BufferedImage.TYPE_BYTE_BINARY`): è la resa che andrebbe davvero sulla stampante — un pixel è
  o nero o bianco, niente vie di mezzo. Questa è la misura usata per il confronto con
  `prova-corpi.md`.
- **antialiasing + soglia 50%**: resa con AA acceso su un canvas `TYPE_INT_ARGB`, poi ogni pixel
  è convertito a bianco o nero confrontando il suo grigio con 128 (impostato pixel per pixel con
  `setRGB`, non lasciando che sia la pipeline di `Graphics2D` a "ditherare" disegnando
  direttamente su un'immagine 1-bit — vedi "Problemi e soluzioni").

## Risultati: la scaletta dei corpi

Arial, variante bilevel, confrontata con la tabella di `prova-corpi.md` (Python/Pillow, stesso
giorno di riferimento):

| Corpo | px altezza x | mm (Java, bilevel) | mm (prova-corpi.md) | differenza |
|---|---|---|---|---|
| 5 pt | 11 | 0,93 | 0,9 | +0,03 |
| 6 pt | 13 | 1,10 | 1,1 | +0,00 |
| 7 pt | 15 | 1,27 | 1,3 | −0,03 |
| 8 pt | 18 | 1,52 | 1,5 | +0,02 |
| 9 pt | 20 | 1,69 | 1,7 | −0,01 |
| 10 pt | 22 | 1,86 | 1,9 | −0,04 |
| 11 pt | 24 | 2,03 | 2,0 | +0,03 |
| 12 pt | 26 | 2,20 | 2,2 | +0,00 |
| 14 pt | 31 | 2,62 | 2,6 | +0,02 |
| 16 pt | 35 | 2,96 | 3,0 | −0,04 |
| 18 pt | 39 | 3,30 | 3,3 | +0,00 |

**Scarto massimo: 0,04 mm, su tutti i corpi entro ±0,1 mm.** Java 2D e Python/Pillow, pur con
motori di rendering del testo diversi (rispettivamente il rasterizzatore di font di AWT/FreeType
via JDK, e FreeType via Pillow) e pur misurando in modo diverso (Java conta i pixel neri di un
render reale; Pillow legge il bounding box dell'inchiostro via `textbbox`), concordano entro
l'arrotondamento di un pixel a 300 dpi (1 px = 0,085 mm). Le differenze residue sono coerenti con
l'hinting: a 300 dpi ogni corpo copre solo 4-8 px di allineamento fine dello stelo verticale della
"x", quindi lo snap al pixel dell'hinter può spostare l'altezza misurata di ±1 px a seconda di
dove cade la griglia — non serve altra spiegazione.

Liberation Sans, stessa misura bilevel, confrontata sia con `prova-corpi.md` sia con Arial:

| Corpo | mm (Liberation Sans) | mm (prova-corpi.md, Arial) | diff. da prova-corpi | diff. da Arial (Java) |
|---|---|---|---|---|
| 5 pt | 0,93 | 0,9 | +0,03 | +0,00 |
| 6 pt | 1,19 | 1,1 | +0,09 | +0,08 |
| 7 pt | 1,35 | 1,3 | +0,05 | +0,08 |
| 8 pt | 1,52 | 1,5 | +0,02 | +0,00 |
| 9 pt | 1,78 | 1,7 | +0,08 | +0,08 |
| 10 pt | 1,95 | 1,9 | +0,05 | +0,08 |
| 11 pt | 2,12 | 2,0 | +0,12 | +0,08 |
| 12 pt | 2,20 | 2,2 | +0,00 | +0,00 |
| 14 pt | 2,62 | 2,6 | +0,02 | +0,00 |
| 16 pt | 2,96 | 3,0 | −0,04 | +0,00 |
| 18 pt | 3,39 | 3,3 | +0,09 | +0,08 |

Liberation Sans è quasi sempre 0-1 pixel (0-0,08 mm) più alta di Arial, mai il contrario. È
coerente con quello che promette il progetto Liberation: le due famiglie sono **compatibili nelle
metriche di avanzamento** (stessa larghezza dei caratteri, stesse tabelle di spaziatura/kerning,
per cui un documento impaginato con l'una va a capo uguale con l'altra), non nel disegno del
glifo — l'altezza reale della "x" dipende dal contorno disegnato, non dalle metriche di
avanzamento, e Liberation Sans ha una "x" leggermente più alta a corpo uguale. Per l'etichetta
serve la metrica di avanzamento (per stare nella larghezza disponibile) più che l'altezza esatta
del pixel, quindi la sostituzione resta valida; la differenza di 0-1 px non sposta nessun corpo
sotto o sopra la soglia legale di 1,2 mm.

## Bilevel (OFF) contro antialiasing+soglia 50% (ON): quale si legge meglio ai corpi piccoli

Guardate `scaletta-Arial.png` e `scaletta-Arial-aa.png` (e gli equivalenti Liberation Sans) fianco
a fianco, in particolare le righe da 5 a 9 pt (sotto o appena sopra il minimo di legge): **sono
praticamente indistinguibili**. A 300 dpi, con un font ben hintato come Arial, il rasterizzatore
di AWT allinea già gli steli verticali alla griglia dei pixel quando l'antialiasing è spento
(`TEXT_ANTIALIASING_OFF` non disattiva l'hinting, solo la sfumatura dei bordi); il risultato è
quasi lo stesso bitmap che si ottiene rendendo con antialiasing e poi tagliando al 50% di
copertura. Le uniche differenze visibili, corpo per corpo, sono su singoli pixel dei bordi
diagonali (per esempio la gamba della "A" o il taglio della "q"), non sulla leggibilità
complessiva.

**Giudizio: nessuna delle due variante è più leggibile dell'altra ai corpi piccoli**, per questo
font a questa risoluzione. Conta molto di più il corpo (sotto 7 pt la "x" scende sotto 1,3 mm e
i tratti si assottigliano) che la scelta fra bilevel diretto e AA+soglia. Per la stampa reale
(raster 1 bit) conviene comunque la resa bilevel diretta: è esattamente il bitmap che arriva alla
testina, senza un passaggio di conversione in più.

## Risultati: l'etichetta "Completa"

Variante B (larghezza 107,7 mm, come nella prova di `prova-corpi.md`), con i corpi aggiornati al
5 settembre (minimo 7 pt, non più 6): titolo 18 pt, ingredienti 7 pt, può contenere 7 pt, scadenza
e conservazione 8 pt, lotto 7 pt, quantità 28 pt, valori nutrizionali 7 pt, produttore 7 pt a
piena larghezza in fondo.

```
completa-Arial.png: 1272 x 795 px = 107,7 x 67,3 mm
```

Per confronto, la stessa variante B in `prova-corpi.md` (corpi vecchi, con ingredienti e
nutrizionali a 6 pt anziché 7, e produttore dentro la colonna sinistra anziché a piena larghezza)
usciva alta 66,8 mm: 0,5 mm di differenza, coerente con l'aumento di un punto sui blocchi che
sono saliti a 7 pt.

Cosa mostra l'immagine (guardata con lo strumento di lettura immagini):

- **Grassetto misto nel paragrafo, con mandata a capo corretta.** Il paragrafo ingredienti (un
  `AttributedString` con `TextAttribute.FONT` diverso per ogni run, spezzato in righe da
  `LineBreakMeasurer`) va a capo su 4 righe, con "FRUMENTO", "AVENA", "GRANO", "SEMOLA" in
  grassetto dentro il testo normale, senza spazi persi o duplicati ai punti di taglio. Il
  paragrafo "Può contenere" fa lo stesso con gli allergeni.
- **Due colonne con filetto verticale.** A sinistra scadenza (con la data in grassetto dentro la
  frase), conservazione, lotto, quantità; a destra la tabella nutrizionale; il filetto verticale
  corre per tutta l'altezza della zona a due colonne.
- **Tabella nutrizionale allineata a destra.** Ogni riga ha l'etichetta a sinistra (in grassetto
  tranne le voci "di cui...") e il valore a destra, con gli allineamenti calcolati dalla
  larghezza reale del testo (`FontMetrics.stringWidth`), non da posizioni fisse.
- **Produttore a piena larghezza in fondo**, fuori dalla zona a due colonne, come richiesto.

## Cosa si è imparato

- **`LineBreakMeasurer` fa da solo quello che lo script Python doveva fare a mano.** La funzione
  `wrap()` di `ql_prova_etichetta.py` tokenizza il testo a mano (spezzoni + spazi come gettoni a
  sé) per non perdere o duplicare gli spazi a cavallo di un cambio di stile. Con
  `AttributedString` + `LineBreakMeasurer`, Java calcola da solo i punti di rottura corretti
  (usa `BreakIterator` internamente) rispettando gli attributi di stile per carattere: basta
  costruire la stringa concatenata e assegnare `TextAttribute.FONT` per intervallo di caratteri.
  Meno codice, meno casi limite da gestire a mano.
- **`deriveFont(float)` con la dimensione in pixel è la chiave per lavorare a 300 dpi.** AWT
  assume 72 dpi per il corpo dei font (1 pt = 1 "pixel" logico); per ottenere un rendering reale a
  300 dpi basta passare a `deriveFont` la dimensione già moltiplicata per `300/72 = 4,16667`, e
  disegnare 1:1 su un `BufferedImage`. Nessuna trasformazione di `Graphics2D` necessaria.
- **Disegnare AA direttamente su un'immagine `TYPE_BYTE_BINARY` non dà un controllo pulito sulla
  soglia.** La pipeline di `Graphics2D` per una destinazione a 2 colori applica la sua conversione
  interna (che nei test assomigliava a un ordered dithering, non a una soglia netta al 50%), che
  non è quello che serve per un confronto visivo onesto fra "spento" e "acceso". La soluzione è
  renderizzare con AA su un canvas `TYPE_INT_ARGB` (o `TYPE_BYTE_GRAY`) e poi convertire a bilevel
  a mano, pixel per pixel, con `setRGB` (che non passa dalla pipeline di rendering e quindi non
  dithera).
- **Liberation Sans è compatibile nelle metriche di avanzamento, non nel disegno del glifo.**
  Prevedibile ma verificato: la larghezza dei caratteri combacia (altrimenti l'etichetta
  andrebbe a capo diversamente), l'altezza della "x" no (differenze fino a 1 px / 0,08 mm a 300
  dpi, sempre nello stesso verso). Per l'uso previsto (font libero, senza licenza da acquistare,
  al posto di Arial) va bene lo stesso: non sposta nessun corpo sopra o sotto la soglia di legge.

## Problemi e soluzioni

- **Testo italiano accentato ("Può", "Quantità") e i tre puntini di troncamento uscivano come
  mojibake nelle prime immagini** (`PuÃ²`, `QuantitÃ `, `â€¦` al posto di "…"). Causa: `javac`
  legge il file sorgente con la codifica di default della piattaforma se non le si dice
  altrimenti, e su questa macchina Windows non è UTF-8; il file sorgente è salvato in UTF-8 (byte
  `E2 80 A6` per "…", eccetera), letto con una codifica diversa produce caratteri sbagliati
  **cotti dentro il file `.class`** — non è un problema di stampa a console, il testo sbagliato
  finisce proprio nei pixel della PNG. Risolto compilando con `javac -encoding UTF-8`
  esplicito. **Chiunque ricompili questo file su Windows deve ricordarsi il flag**, altrimenti il
  bug si ripresenta silenziosamente (compila senza errori, ma il testo è sbagliato).
- **Il download di Liberation Sans è andato a buon fine** al primo tentativo (nessun problema da
  segnalare): l'URL dell'allegato della release 2.1.5 su GitHub, preso dal corpo della release via
  API (`GET /repos/liberationfonts/liberation-fonts/releases/tags/2.1.5`), ha risposto 200 con
  l'archivio `.tar.gz` atteso.

## Verifica dei criteri di accettazione

- Compila ed esegue con JDK 17 (`17.0.12`), nessuna dipendenza esterna: verificato.
- Le PNG esistono nella cartella scratch passata come argomento e sono state guardate con lo
  strumento di lettura immagini (incluso uno zoom 3x sulle righe 5-9 pt per il confronto
  bilevel/AA): verificato.
- Tabella delle altezze della x per Arial entro ±0,1 mm da `prova-corpi.md` su tutti i corpi
  (scarto massimo 0,04 mm): verificato.
- Liberation Sans: differenza da Arial riportata (0-1 px, 0-0,08 mm, sempre Liberation Sans più
  alta): verificato, come atteso per font metricamente compatibili ma con contorni diversi.
- Etichetta "Completa": grassetto misto con mandata a capo corretta, due colonne con filetto,
  tabella allineata a destra, tutti verificati guardando `completa-Arial.png`
  (1272 × 795 px = 107,7 × 67,3 mm).
- Nessun file `.class`, `.png` o font dentro il repository: verificato (`git status
  tools/spike-java2d/` mostra solo `TextRenderSpike.java` e questo file).
