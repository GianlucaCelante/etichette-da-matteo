# Etichette da Matteo

Piccola applicazione per stampare etichette alimentari (preparazioni e ingredienti di una pizzeria) su una **Brother QL-1100c** collegata via USB.

## Stato del progetto (2026-09-03)

- **Stampante mappata e verificata**: comunicazione raw via USB senza driver Brother, lettura stato e impostazioni, stampa di prova in modalità raster riuscita. Tutto in [`docs/mappatura-brother-ql-1100c.md`](docs/mappatura-brother-ql-1100c.md).
- **Interfaccia disegnata**: [canvas di design](https://claude.ai/code/artifact/cc8ac916-9882-434b-955a-bd521db09cac) con le schermate Stampa, Prodotti, Modelli di etichetta, Impostazioni, la vista da telefono e due direzioni alternative. Sorgenti degli artboard in [`design/`](design/).
- **Da decidere**: forma dell'app (desktop Windows, oppure web app locale con il PC come ponte USB) e stack tecnologico.

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
