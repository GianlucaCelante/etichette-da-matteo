# Etichette da Matteo

Piccola applicazione per stampare etichette alimentari (preparazioni e ingredienti di una pizzeria) su una **Brother QL-1100c** collegata via USB.

## Stato del progetto (2026-09-03)

- **Stampante mappata e verificata**: comunicazione raw via USB senza driver Brother, lettura stato e impostazioni, stampe di prova in modalità raster riuscite su entrambi i rotoli (62 e 102 mm). Tutto in [`docs/mappatura-brother-ql-1100c.md`](docs/mappatura-brother-ql-1100c.md).
- **Interfaccia disegnata**: [canvas di design](https://claude.ai/code/artifact/e8537537-bab9-4cce-a2f3-0dec57fc9bd2) con le schermate Stampa, Prodotti, Modelli di etichetta, Modello libero, Storico, Impostazioni, la vista da telefono e due direzioni alternative. Sorgenti degli artboard in [`design/`](design/).
- **Forma dell'app e funzioni decise**: un unico programma sul PC collegato via USB, che pubblica l'interfaccia sulla rete locale e la mostra anche in una finestra sul PC. Elenco delle funzioni della prima versione in [`docs/funzionalita-prima-versione.md`](docs/funzionalita-prima-versione.md).
- **Da decidere**: stack tecnologico (sul PC ci sono .NET 8, Node 24, Python 3.14).

## Struttura

```
docs/     mappatura della stampante (protocollo, stati, quirk, tabelle supporti)
tools/    script Python di riferimento per parlare con la stampante (nessuna dipendenza oltre Pillow)
design/   artboard del canvas di design (.dc.html) e layout (canvas.json)
```

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
| 62 mm continuo | 58,9 mm × libera (696 punti) | lungo il nastro |
| 102 mm continuo | 98,6 mm × libera (1164 punti) | attraverso il nastro |

Risoluzione 300 dpi, taglio automatico, rilevamento del rotolo dallo stato della stampante.
