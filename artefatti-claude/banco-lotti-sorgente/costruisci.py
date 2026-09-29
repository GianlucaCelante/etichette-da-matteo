# Uso: python artefatti-claude/banco-lotti-sorgente/costruisci.py (da qualsiasi cartella)
#
# Costruisce il prototipo "Banco lotti" (artefatti-claude/banco-lotti-2026-09-14.html)
# innestando il CSS vero dell'app (ui/src/index.css, il contenuto di @layer components)
# nel modello banco-lotti.template.html al posto del segnaposto /*__CSS_APP__*/.
# Cosi' il prototipo resta allineato all'interfaccia quando il CSS dell'app cambia:
# si rilancia lo script e si ripubblica l'artefatto.
import io
import pathlib

qui = pathlib.Path(__file__).resolve().parent
radice = qui.parents[1]
css = io.open(radice / "ui/src/index.css", encoding="utf-8").read()
inizio = css.index("@layer components {") + len("@layer components {")
fine = css.rindex("} /* fine @layer components */")
corpo = css[inizio:fine]
# i @font-face dell'app (che puntano a /font/) stanno prima del layer e restano fuori:
# nel prototipo i font arrivano da Google, come nei prototipi precedenti
modello = io.open(qui / "banco-lotti.template.html", encoding="utf-8").read()
assert "/*__CSS_APP__*/" in modello, "segnaposto del CSS mancante nel modello"
out = modello.replace("/*__CSS_APP__*/", corpo.strip())
dest = radice / "artefatti-claude/banco-lotti-2026-09-14.html"
io.open(dest, "w", encoding="utf-8", newline="\n").write(out)
print("scritto", dest, len(out), "byte")
