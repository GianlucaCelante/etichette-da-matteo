# Prova di stampa: corpi del testo e zona a due colonne

**5 settembre 2026 — QL-1100c, rotolo continuo da 102 mm, raster 300 dpi, taglio automatico.**
Strumento: [`tools/ql_prova_etichetta.py`](../tools/ql_prova_etichetta.py). La prova si è fatta
sul rotolo da 102 perché era quello caricato; per le misure fisiche non cambia niente — un punto
tipografico è lungo uguale su tutti i rotoli — e il 102 ha il vantaggio di contenere anche la
geometria del 62 con i margini bianchi ai lati.

## Cosa si voleva sapere

1. La zona a due colonne esce come disegnata sul canvas?
2. Quanto vengono grandi davvero i corpi che le tendine del canvas dichiarano?
3. Gli ingredienti stanno sopra il minimo di legge (1,2 mm di altezza della x, regolamento UE
   1169/2011 per le indicazioni obbligatorie)?

## 1. Le due colonne: nessun problema, e non era una domanda per la stampante

In modalità raster mandiamo alla stampante una bitmap 1-bit a 300 dpi: font e layout sono
completamente liberi (vedi [`mappatura-brother-ql-1100c.md`](mappatura-brother-ql-1100c.md), §3).
Due colonne, filetto verticale, allineamenti a destra dei valori nutrizionali: sono solo pixel.
Le tre stampe sono passate senza errori (`06/01 in stampa` → `01 stampa completata`), come le
prove di §7 della mappatura.

**Non serve nessuna capacità della stampante che non fosse già verificata.**

## 2. I corpi: la scaletta misurata

Stampata la stessa riga di ingredienti da 5 a 18 pt, con accanto la barra dell'altezza della x.
Font Arial, 300 dpi.

| Corpo | Altezza della x | |
|---|---|---|
| 5 pt | 0,9 mm | sotto il minimo |
| 6 pt | 1,1 mm | **sotto il minimo** |
| 7 pt | 1,3 mm | primo corpo a norma |
| 8 pt | 1,5 mm | |
| 9 pt | 1,7 mm | |
| 10 pt | 1,9 mm | |
| 11 pt | 2,0 mm | |
| 12 pt | 2,2 mm | |
| 14 pt | 2,6 mm | |
| 16 pt | 3,0 mm | |
| 18 pt | 3,3 mm | |

**Il canvas metteva gli ingredienti e i valori nutrizionali a 6 pt: erano fuori norma.**
**Deciso il 5 settembre: la scaletta della tendina parte da 7 pt**, i corpi più piccoli sono stati
tolti da tutte e due le schermate e i blocchi che stavano a 6 pt sono saliti a 7.

## 3. Le dimensioni dell'etichetta: i corpi dichiarati non ci stanno

Composta l'etichetta «Completa» con il contenuto e i corpi che dichiara il canvas (titolo 18 pt,
ingredienti 6, può contenere 6, scadenza e conservazione 8, lotto 6, quantità 28, valori
nutrizionali 6, produttore 6), lasciando che l'altezza esca dal contenuto:

| Variante | Larghezza | Altezza che ne esce | Note |
|---|---|---|---|
| A — lettura del canvas | 58,9 mm | **96,6 mm** | il canvas la disegna alta ~32 mm |
| B — 58,9 mm come altezza | 107,7 mm | **66,8 mm** | proporzione del canvas, ruotata sul nastro |

Nella variante A la colonna dei valori nutrizionali (un terzo di 58,9 = 18 mm) è troppo stretta:
ogni riga della tabella si tronca. Nella variante B la tabella respira, ma l'etichetta viene alta
66,8 mm contro i **58,9 mm** che il rotolo da 62 concede sul lato che attraversa la testina.

**Nessuna delle due letture sta in piedi ai corpi dichiarati.** Il blocco della quantità a 28 pt
(«2148 g», quasi 10 mm) e il titolo a 18 pt sono i due che mangiano più spazio.

## Cosa manca per chiudere

- **L'etichetta originale del cliente**, per misurarla invece di dedurla: dimensioni fisiche, corpo
  reale degli ingredienti, larghezza vera della colonna nutrizionale. Tutto il resto discende da lì.
- Con quelle misure: rifare la scaletta dei corpi del canvas (con il minimo a 7 pt) e correggere
  l'anteprima, che oggi è un'illustrazione verosimile ma non in scala.
