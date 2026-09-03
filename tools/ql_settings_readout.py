"""Lettura completa (sola lettura) delle impostazioni della QL-1100/1100c nelle tre modalita' comando.

Regole apprese sul campo:
- la stampante risponde a un poll USB IN con un pacchetto vuoto se non ha dati pronti
- una risposta arriva ~20-40 ms dopo il comando: serve polling ripetuto
- le risposte non lette restano in coda nella stampante: svuotare prima di ogni richiesta
"""
import sys
import time
import os

sys.path.insert(0, os.path.dirname(__file__))
from ql_probe import UsbPrinter, decode_status  # noqa: E402


def poll_read(p, max_ms=1500, quiet_ms=150, size=64):
    """Fa polling finche' arrivano dati; termina dopo `quiet_ms` di silenzio o `max_ms` totali."""
    t0 = time.time()
    got = b""
    last = None
    while (time.time() - t0) * 1000 < max_ms:
        try:
            d = p.read(size, timeout_ms=500)
        except TimeoutError:
            d = b""
        if d:
            got += d
            last = time.time()
        elif last is not None and (time.time() - last) * 1000 > quiet_ms:
            break
        time.sleep(0.01)
    return got


def drain(p):
    n = 0
    while True:
        d = poll_read(p, max_ms=300, quiet_ms=120)
        if not d:
            break
        n += len(d)
    return n


def query(p, label, cmd, decode=None, max_ms=1500):
    drain(p)
    p.write(cmd)
    t0 = time.time()
    d = poll_read(p, max_ms=max_ms)
    dt = (time.time() - t0) * 1000
    if not d:
        print("  %-42s -> nessuna risposta (%.0f ms)" % (label, dt))
        return None
    txt = d.hex(" ")
    extra = ""
    if decode:
        try:
            extra = "  = " + decode(d)
        except Exception as e:  # noqa: BLE001
            extra = "  (decode err: %s)" % e
    print("  %-42s -> %s%s   [%d byte, %.0f ms]" % (label, txt, extra, len(d), dt))
    return d


def val3(table=None):
    def f(d):
        if len(d) < 3:
            return "risposta corta"
        v = d[2]
        return "%d (0x%02X)%s" % (v, v, (" " + table.get(v, "?")) if table else "")
    return f


MODE = {0: "ESC/P", 1: "Raster", 3: "P-touch Template"}
CUT = {0: "nessun taglio", 1: "taglio automatico", 8: "taglio a fine stampa", 9: "auto + fine stampa"}
PRINTOPT = {0: "priorita' velocita'", 1: "priorita' qualita'"}
CODESET = {0: "Windows1252", 1: "Windows1250", 2: "Brother standard"}
INTL = {0: "USA", 1: "Francia", 2: "Germania", 3: "UK", 4: "Danimarca I", 5: "Svezia", 6: "Italia", 7: "Spagna I",
        8: "Giappone", 9: "Norvegia", 10: "Danimarca II", 11: "Spagna II", 12: "America Latina", 13: "Corea", 64: "Legal"}
TRIGGER = {0: "stringa comando ricevuta", 1: "tutti gli oggetti riempiti", 2: "numero caratteri ricevuti"}
STYLE = {0: "normale", 1: "grassetto", 2: "outline", 3: "ombra", 4: "ombra+outline"}
FONT = {0: "Brougham (bitmap)", 1: "Letter Gothic Bold (bitmap)", 2: "Brussels (bitmap)", 3: "Helsinki (bitmap)",
        4: "San Diego (bitmap)", 9: "Letter Gothic (outline)", 10: "Brussels (outline)", 11: "Helsinki (outline)"}
ALIGN = {0: "sinistra", 1: "centro", 2: "destra"}
ESCP_CODESET = {0: "standard", 1: "Est Europa", 2: "Ovest Europa"}


def status_summary(d):
    s = decode_status(d[:32])
    return "modello=%s media=%s mm x %s mm tipo=%s err1=%s err2=%s fase=%s" % (
        s["model_code"], s["media_width_mm"], s["media_length_mm"], s["media_type"], s["error1"], s["error2"],
        s["phase_type"])


def main():
    p = UsbPrinter()
    try:
        print("== Svuotamento coda risposte pendenti ==")
        print("  scartati %d byte" % drain(p))

        print("\n== Stato (ESC i S) x3, con polling corretto ==")
        for i in range(3):
            query(p, "ESC i S #%d" % (i + 1), b"\x1biS", status_summary)
        print("  verifica: dopo l'ultima risposta la coda deve essere vuota -> scartati %d byte" % drain(p))

        print("\n== Modalita' RASTER (ESC i a 01): impostazioni statiche leggibili ==")
        p.write(b"\x1bia\x01")
        time.sleep(0.1)
        drain(p)
        query(p, "ESC iXi1 modalita' comando all'accensione", b"\x1bXi1\x00\x00".replace(b"\x1bX", b"\x1biX"), val3(MODE))
        query(p, "ESC iXc1 opzioni taglio", b"\x1biXc1\x00\x00", val3(CUT))
        query(p, "ESC iXy1 taglio ogni N etichette", b"\x1biXy1\x00\x00", val3())
        query(p, "ESC iXq1 opzioni stampa (velocita'/qualita')", b"\x1biXq1\x00\x00", val3(PRINTOPT))
        query(p, "ESC iXn1 template selezionato", b"\x1biXn1\x00\x00", val3())
        query(p, "ESC iXm1 set codici caratteri", b"\x1biXm1\x00\x00", val3(CODESET))
        query(p, "ESC iXj1 set caratteri internazionale", b"\x1biXj1\x00\x00", val3(INTL))
        query(p, "ESC iXT1 trigger avvio stampa (template)", b"\x1biXT1\x00\x00", val3(TRIGGER))
        query(p, "ESC iXr1 conteggio caratteri avvio stampa", b"\x1biXr1\x00\x00")
        query(p, "ESC iXP1 stringa comando avvio stampa", b"\x1biXP1\x00\x00")
        query(p, "ESC iXD1 delimitatore", b"\x1biXD1\x00\x00")
        query(p, "ESC iXa1 stringhe non stampate", b"\x1biXa1\x00\x00")
        query(p, "ESC iXf1 carattere prefisso", b"\x1biXf1\x00\x00")
        query(p, "ESC iXR1 stringa line feed", b"\x1biXR1\x00\x00")
        query(p, "ESC iXC1 numero copie", b"\x1biXC1\x00\x00", val3())
        query(p, "ESC iXN1 copie numerazione", b"\x1biXN1\x00\x00", val3())
        query(p, "ESC iXF1 sostituzione FNC1", b"\x1biXF1\x00\x00", val3())

        print("\n== Modalita' ESC/P (ESC i a 00): impostazioni predefinite testo ==")
        p.write(b"\x1bia\x00")
        time.sleep(0.1)
        drain(p)
        query(p, "ESC i S (in ESC/P)", b"\x1biS", status_summary)
        query(p, "ESC iXQ1 stile carattere predefinito", b"\x1biXQ1\x00\x00", val3(STYLE))
        query(p, "ESC iXk1 font predefinito", b"\x1biXk1\x00\x00", val3(FONT))
        query(p, "ESC iXX1 dimensione carattere predefinita", b"\x1biXX1\x00\x00")
        query(p, "ESC iX31 interlinea predefinita", b"\x1biX31\x00\x00")
        query(p, "ESC iXA1 allineamento predefinito", b"\x1biXA1\x00\x00", val3(ALIGN))
        query(p, "ESC iX(1 lunghezza pagina predefinita", b"\x1biX(1\x00\x00")
        query(p, "ESC iXL1 orientamento landscape predefinito", b"\x1biXL1\x00\x00", val3({0: "no", 1: "si'"}))
        query(p, "ESC iXj1 set internazionale (ESC/P)", b"\x1biXj1\x00\x00", val3(INTL))
        query(p, "ESC iXm1 set codici (ESC/P)", b"\x1biXm1\x00\x00", val3(ESCP_CODESET))

        print("\n== Modalita' P-TOUCH TEMPLATE (ESC i a 03): versione e stato ==")
        p.write(b"\x1bia\x03")
        time.sleep(0.1)
        drain(p)
        query(p, "^VR versione firmware", b"^VR", lambda d: repr(d.decode("latin-1")))
        query(p, "^SR stato (32 byte)", b"^SR", status_summary)

        print("\n== Ripristino modalita' RASTER (dinamica, si azzera allo spegnimento) ==")
        p.write(b"\x1bia\x01")
        time.sleep(0.1)
        drain(p)
        query(p, "ESC i S finale", b"\x1biS", status_summary)
    finally:
        p.close()


if __name__ == "__main__":
    main()
