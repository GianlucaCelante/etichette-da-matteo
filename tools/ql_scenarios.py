"""Scenari secondari sulla QL-1100c.

  python ql_scenarios.py auto                 # multipagina, taglio ogni N, 600 dpi, annulla (nessuna azione manuale)
  python ql_scenarios.py auto --only hires    # uno solo: multipagina | taglio | hires | annulla
  python ql_scenarios.py monitor 60           # registra i cambi di stato per 60 s (apri coperchio, togli rotolo...)
  python ql_scenarios.py errore               # stampa lunga: apri il coperchio durante la stampa; poi recupero e ristampa
  python ql_scenarios.py riconnessione 120    # scollega/ricollega USB, spegni/accendi: verifica ritrovamento
"""
import argparse
import os
import sys
import time

sys.path.insert(0, os.path.dirname(__file__))
from ql_probe import UsbPrinter, decode_status  # noqa: E402
from ql_usb import find_usbprint_paths  # noqa: E402
import ql_raster as R  # noqa: E402


def open_printer():
    paths = find_usbprint_paths(vid="04F9")
    if not paths:
        raise RuntimeError("nessuna stampante Brother USB trovata")
    return UsbPrinter(paths[0]), paths[0]


def require_continuous(p):
    s = R.get_status(p)
    print("Stato:", R.fmt(s))
    if s is None or s["error1"] or s["error2"] or not s["media_type"].startswith("0x0A"):
        raise RuntimeError("serve un nastro continuo senza errori")
    if s["media_width_mm"] not in R.CONTINUOUS:
        raise RuntimeError("larghezza %s non gestita" % s["media_width_mm"])
    return s["media_width_mm"]


def run_job(p, job, pages, label, timeout_s=40):
    print("[%s] job %d byte, %d pagine" % (label, len(job), pages))
    t_send = R.send_job(p, job)
    print("  inviato in %.0f ms" % t_send)
    t0 = time.time()
    esito, seen = R.follow_print(p, pages, timeout_s)
    print("  esito: %s in %.1f s (%d stati ricevuti)" % (esito, time.time() - t0, len(seen)))
    return esito, seen


# ----------------------------------------------------------------------------- scenari automatici

def sc_multipagina(p, media):
    w = R.CONTINUOUS[media]["print"]
    pages = [R.render_lines(w, 28, [("MULTIPAGINA %d/3" % i, 60, True), ("taglio dopo ogni etichetta", 26, False)])
             for i in (1, 2, 3)]
    job = R.build_job(pages, media, autocut=True, cut_each=1, cut_at_end=True)
    return run_job(p, job, 3, "multipagina: 3 pagine, FF fra le pagine, taglio ogni etichetta")


def sc_taglio(p, media):
    w = R.CONTINUOUS[media]["print"]
    pages = [R.render_lines(w, 28, [("TAGLIO OGNI 2 - pag. %d/4" % i, 54, True), ("attesi: 2 strisce da 2 etichette", 26, False)])
             for i in (1, 2, 3, 4)]
    job = R.build_job(pages, media, autocut=True, cut_each=2, cut_at_end=True)
    return run_job(p, job, 4, "taglio ogni 2 etichette su 4 pagine")


def sc_hires(p, media):
    w = R.CONTINUOUS[media]["print"]
    im300, im600 = R.render_detail(w, 32, "300 dpi (normale)")
    _, im600b = R.render_detail(w, 32, "600 dpi (alta risoluzione)")
    r1 = run_job(p, R.build_job([im300], media), 1, "dettagli a 300 dpi")
    time.sleep(1.0)
    r2 = run_job(p, R.build_job([im600b], media, hires=True), 1, "dettagli a 600 dpi (ESC i K bit 6, %d linee)" % im600b.size[1])
    return r1, r2


def sc_annulla(p, media):
    w = R.CONTINUOUS[media]["print"]
    pages = [R.render_lines(w, 90, [("ANNULLA - pagina %d/4" % i, 60, True), ("questa serie viene interrotta dopo 1 s", 26, False)])
             for i in (1, 2, 3, 4)]
    job = R.build_job(pages, media, autocut=True, cut_each=1)
    print("[annulla] job %d byte, 4 pagine da 90 mm; dopo 1 s invio invalidate + ESC @" % len(job))
    R.send_job(p, job)
    t0 = time.time()
    seen_before = []
    while time.time() - t0 < 1.0:
        d = R.poll_read(p, max_ms=200, quiet_ms=50)
        while len(d) >= 32:
            s = decode_status(d[:32]); d = d[32:]
            seen_before.append(s)
            print("  +%6.0f ms  %s" % ((time.time() - t0) * 1000, R.fmt(s)))
    p.write(b"\x00" * 400 + b"\x1b@")
    print("  +%6.0f ms  >> inviato invalidate + ESC @" % ((time.time() - t0) * 1000))
    deadline = time.time() + 12
    completed = 0
    while time.time() < deadline:
        d = R.poll_read(p, max_ms=400, quiet_ms=60)
        while len(d) >= 32:
            s = decode_status(d[:32]); d = d[32:]
            if s["status_type"].startswith("0x01"):
                completed += 1
            print("  +%6.0f ms  %s" % ((time.time() - t0) * 1000, R.fmt(s)))
        time.sleep(0.05)
    print("  pagine risultate 'completate' dopo l'annullamento: %d su 4" % completed)
    s = R.get_status(p)
    print("  stato finale: %s" % R.fmt(s))


def sc_hires2(p, media):
    """Varianti per attivare i 600 dpi: etichette da 26 mm; se escono da 52 mm con cerchio ovale, la modalita' non e' attiva."""
    from PIL import Image, ImageDraw, ImageFont
    w = R.CONTINUOUS[media]["print"]

    def label(title):
        H2 = R.mm(26, 600)
        im = Image.new("L", (w * 2, H2), 255)
        d = ImageDraw.Draw(im)
        d.rectangle([0, 0, w * 2 - 1, H2 - 1], outline=0, width=4)
        d.text((24, 16), title, font=ImageFont.truetype(R.FONT_BOLD, 60), fill=0)
        d.text((24, 100), "cerchio rotondo + 26 mm = 600 dpi attivi", font=ImageFont.truetype(R.FONT_REG, 40), fill=0)
        cx, cy, r = w * 2 - 300, H2 // 2, H2 // 2 - 40
        d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=0, width=6)
        return im.resize((w, H2), Image.LANCZOS).point(lambda v: 255 if v > 140 else 0).convert("1")

    spec = R.CONTINUOUS[media]

    def raw_lines(bw, compress=True):
        out = bytearray()
        for y in range(bw.size[1]):
            if compress:
                out += R.line_bytes(bw, y, spec["left"])
            else:
                line = Image.new("1", (R.TOTAL_PINS, 1), 1)
                line.paste(bw.crop((0, y, bw.size[0], y + 1)), (spec["left"], 0))
                line = line.transpose(Image.FLIP_LEFT_RIGHT)
                out += b"g\x00" + bytes([R.BYTES_PER_LINE]) + bytes(b ^ 0xFF for b in line.tobytes())
        return bytes(out)

    def job_variant(bw, order, quality, compress):
        rows = bw.size[1]
        n1 = 0x80 | 0x04 | 0x02 | (0x40 if quality else 0)
        z = b"\x1biz" + bytes([n1, 0x0A, media, 0]) + rows.to_bytes(4, "little") + b"\x00\x00"
        k = b"\x1biK\x48"
        j = bytearray(b"\x00" * 400 + b"\x1b@" + b"\x1bia\x01" + b"\x1bi!\x00")
        if order == "K-prima":
            j += k + z + b"\x1biM\x40" + b"\x1biA\x01"
        else:
            j += z + b"\x1biM\x40" + b"\x1biA\x01" + k
        j += b"\x1bid\x23\x00" + (b"M\x02" if compress else b"M\x00")
        j += raw_lines(bw, compress) + b"\x1a"
        return bytes(j)

    variants = [
        ("V1 qualita'", dict(order="std", quality=True, compress=True)),
        ("V2 K prima di z", dict(order="K-prima", quality=False, compress=True)),
        ("V3 non compresso", dict(order="std", quality=False, compress=False)),
        ("V4 qualita' + K prima + non compr.", dict(order="K-prima", quality=True, compress=False)),
    ]
    for name, kw in variants:
        bw = label(name)
        job = job_variant(bw, **kw)
        run_job(p, job, 1, "hires %s (%d linee)" % (name, bw.size[1]))
        time.sleep(1.0)
        R.drain(p)


def sc_annulla2(p, media):
    """Pattern per l'app: una pagina alla volta (FF), attesa di 'completata', annullamento = non inviare la successiva."""
    w = R.CONTINUOUS[media]["print"]
    pages = [R.render_lines(w, 60, [("UNA ALLA VOLTA - pag. %d/5" % i, 54, True), ("annullo dopo la 2a: attese 2 etichette intere", 26, False)])
             for i in range(1, 6)]
    print("[annulla2] 5 pagine previste, inviate una alla volta con FF; dopo la 2a invio invalidate + ESC @")
    t0 = time.time()
    for i in range(2):
        job = R.build_page_job(pages[i], media, first=(i == 0), last=False)
        R.send_job(p, job)
        esito, seen = R.follow_print(p, 1, timeout_s=20)
        print("  pagina %d: %s a +%.0f ms" % (i + 1, esito, (time.time() - t0) * 1000))
    p.write(b"\x00" * 400 + b"\x1b@")
    print("  >> annullato (invalidate + ESC @) al posto della pagina 3, a +%.0f ms" % ((time.time() - t0) * 1000))
    d = R.poll_read(p, max_ms=3000, quiet_ms=300)
    while len(d) >= 32:
        s = decode_status(d[:32]); d = d[32:]
        print("  +%6.0f ms  %s" % ((time.time() - t0) * 1000, R.fmt(s)))
    s = R.get_status(p)
    print("  stato dopo annullamento: %s" % R.fmt(s))


# ----------------------------------------------------------------------------- scenari manuali

def sc_monitor(p, seconds):
    print("Monitor stato per %d s: apri il coperchio, togli il rotolo, rimettilo, chiudi..." % seconds)
    last = None
    t0 = time.time()
    while time.time() - t0 < seconds:
        try:
            s = R.get_status(p)
        except OSError as e:
            print("  +%5.1f s  errore USB: %s" % (time.time() - t0, e))
            break
        f = R.fmt(s)
        if f != last:
            print("  +%5.1f s  %s" % (time.time() - t0, f))
            last = f
        time.sleep(0.5)


def sc_errore(p, media):
    w = R.CONTINUOUS[media]["print"]
    pages = [R.render_lines(w, 80, [("ERRORE IN STAMPA - pag. %d/3" % i, 54, True), ("apri il coperchio mentre stampa", 26, False)])
             for i in (1, 2, 3)]
    job = R.build_job(pages, media, autocut=True, cut_each=1)
    print("[errore] 3 pagine da 80 mm: APRI IL COPERCHIO durante la stampa", flush=True)
    R.send_job(p, job)
    esito, seen = R.follow_print(p, 3, timeout_s=40)
    print("  esito prima passata: %s" % esito)
    if esito != "errore":
        print("  nessun errore rilevato: la stampa e' finita prima dell'apertura del coperchio?")
        return
    err = [s for s in seen if s["status_type"].startswith("0x02")][-1]
    print("  errore riportato: err1=%s err2=%s" % (err["error1"], err["error2"]))
    print("  ora CHIUDI il coperchio: aspetto che l'errore sparisca (max 120 s); ogni 5 s provo anche invalidate + ESC @", flush=True)
    t0 = time.time()
    last_kick = time.time()
    last = None
    while time.time() - t0 < 120:
        s = R.get_status(p)
        f = R.fmt(s)
        if f != last:
            print("  +%5.1f s  %s" % (time.time() - t0, f), flush=True)
            last = f
        if s and not s["error1"] and not s["error2"]:
            print("  +%5.1f s  errore rientrato" % (time.time() - t0))
            break
        if time.time() - last_kick > 5:
            p.write(b"\x00" * 400 + b"\x1b@")
            last_kick = time.time()
            print("  +%5.1f s  >> inviato invalidate + ESC @" % (time.time() - t0), flush=True)
        time.sleep(0.5)
    else:
        print("  errore ancora presente, mi fermo")
        return
    print("  recupero: invalidate + ESC @ e ristampa dell'intero job")
    p.write(b"\x00" * 400 + b"\x1b@")
    time.sleep(0.2)
    R.drain(p)
    R.send_job(p, job)
    esito2, _ = R.follow_print(p, 3, timeout_s=40)
    print("  esito ristampa: %s" % esito2)


def sc_riconnessione(seconds):
    print("Riconnessione per %d s: scollega il cavo USB, aspetta 5 s, ricollega; poi spegni e riaccendi la stampante." % seconds)
    t0 = time.time()
    p = None
    path = None
    last = None
    while time.time() - t0 < seconds:
        if p is None:
            paths = find_usbprint_paths(vid="04F9")
            if paths:
                try:
                    p = UsbPrinter(paths[0])
                    path = paths[0]
                    print("  +%5.1f s  TROVATA e aperta: %s" % (time.time() - t0, path))
                    last = None
                except OSError as e:
                    print("  +%5.1f s  trovata ma apertura fallita: %s" % (time.time() - t0, e))
                    time.sleep(1.0)
                    continue
            else:
                if last != "assente":
                    print("  +%5.1f s  nessuna stampante Brother presente" % (time.time() - t0))
                    last = "assente"
                time.sleep(0.5)
                continue
        try:
            s = R.get_status(p)
            f = R.fmt(s)
            if f != last:
                print("  +%5.1f s  %s" % (time.time() - t0, f))
                last = f
        except OSError as e:
            print("  +%5.1f s  PERSA (errore %s: %s)" % (time.time() - t0, e.errno, e.strerror))
            try:
                p.close()
            except Exception:  # noqa: BLE001
                pass
            p = None
            last = None
        time.sleep(1.0)
    if p:
        p.close()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("scenario", choices=["auto", "monitor", "errore", "riconnessione"])
    ap.add_argument("arg", nargs="?", type=int, default=60)
    ap.add_argument("--only", choices=["multipagina", "taglio", "hires", "annulla", "annulla2", "hires2"])
    a = ap.parse_args()

    if a.scenario == "riconnessione":
        sc_riconnessione(a.arg)
        return

    p, path = open_printer()
    print("Stampante:", path)
    try:
        if a.scenario == "monitor":
            sc_monitor(p, a.arg)
            return
        media = require_continuous(p)
        if a.scenario == "errore":
            sc_errore(p, media)
            return
        steps = {"multipagina": sc_multipagina, "taglio": sc_taglio, "hires": sc_hires, "annulla": sc_annulla,
                 "annulla2": sc_annulla2, "hires2": sc_hires2}
        for name, fn in steps.items():
            if a.only and a.only != name:
                continue
            print("\n=== %s ===" % name)
            fn(p, media)
            time.sleep(1.5)
            R.drain(p)
    finally:
        p.close()


if __name__ == "__main__":
    main()
