"""Stampa di prova in modalita' raster sulla QL-1100/1100c (nastro continuo 62 mm o 102 mm).

Uso:
  python ql_testprint.py --render-only   # genera solo il PNG di anteprima
  python ql_testprint.py                 # stampa davvero (richiede nastro continuo caricato)
  python ql_testprint.py --no-cut        # senza taglio automatico

Il job segue il manuale "Raster Command Reference QL-1100" (invalidate, ESC @, ESC i a 01,
ESC i ! 00, ESC i z, ESC i M, ESC i A, ESC i K, ESC i d, M 02, g/Z, 1A) e registra tutti gli
stati inviati dalla stampante durante la stampa.
"""
import argparse
import datetime as dt
import os
import sys
import time

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(__file__))
from ql_probe import UsbPrinter, decode_status  # noqa: E402
from ql_settings_readout import poll_read, drain  # noqa: E402

TOTAL_PINS = 1296
BYTES_PER_LINE = TOTAL_PINS // 8  # 162
DPI = 300
DOTS_PER_MM = DPI / 25.4

# Nastro continuo: (pin margine sinistro, pin area di stampa) dal manuale (pag. 19), corretto
# per il 62 mm usando la colonna "Drive Head No." del manuale ESC/P (545-1240).
CONTINUOUS = {
    62: {"left": 544, "print": 696},
    102: {"left": 76, "print": 1164},
    103: {"left": 58, "print": 1200},
}

FONT_BOLD = r"C:\Windows\Fonts\arialbd.ttf"
FONT_REG = r"C:\Windows\Fonts\arial.ttf"


def mm(v):
    return int(round(v * DOTS_PER_MM))


def render_label(width_px, length_mm=45, media_mm=62):
    h = mm(length_mm)
    im = Image.new("L", (width_px, h), 255)
    d = ImageDraw.Draw(im)
    fb = lambda s: ImageFont.truetype(FONT_BOLD, s)  # noqa: E731
    fr = lambda s: ImageFont.truetype(FONT_REG, s)  # noqa: E731

    # cornice a tutta area stampabile
    d.rectangle([0, 0, width_px - 1, h - 1], outline=0, width=4)
    # triangolo pieno in alto a sinistra (marcatore asimmetrico)
    d.polygon([(8, 8), (70, 8), (8, 70)], fill=0)
    # righello mm lungo il bordo superiore (0 = inizio area stampabile)
    for i in range(0, int(width_px / DOTS_PER_MM) + 1):
        x = int(round(i * DOTS_PER_MM))
        if x >= width_px:
            break
        tick = 30 if i % 10 == 0 else (20 if i % 5 == 0 else 10)
        d.line([(x, 0), (x, tick)], fill=0, width=2)
        if i % 10 == 0 and i > 0:
            d.text((x + 3, 30), str(i), font=fr(16), fill=0)

    title = "QL-1100c TEST"
    f = fb(64)
    tw = d.textlength(title, font=f)
    d.text(((width_px - tw) / 2, 70), title, font=f, fill=0)

    sub = "%d mm continuo - 300 dpi - raster TIFF" % media_mm
    f2 = fr(26)
    tw2 = d.textlength(sub, font=f2)
    d.text(((width_px - tw2) / 2, 150), sub, font=f2, fill=0)

    f3 = fb(40)
    d.text((20, 230), "<- SX", font=f3, fill=0)
    tw3 = d.textlength("DX ->", font=f3)
    d.text((width_px - tw3 - 20, 230), "DX ->", font=f3, fill=0)

    ts = dt.datetime.now().strftime("%Y-%m-%d %H:%M")
    f4 = fr(24)
    tw4 = d.textlength(ts, font=f4)
    d.text(((width_px - tw4) / 2, 300), ts, font=f4, fill=0)

    # righello anche sul bordo inferiore, e testo "BASSO"
    for i in range(0, int(width_px / DOTS_PER_MM) + 1):
        x = int(round(i * DOTS_PER_MM))
        if x >= width_px:
            break
        tick = 30 if i % 10 == 0 else (20 if i % 5 == 0 else 10)
        d.line([(x, h - 1), (x, h - 1 - tick)], fill=0, width=2)
    f5 = fr(22)
    tw5 = d.textlength("BASSO (fine etichetta)", font=f5)
    d.text(((width_px - tw5) / 2, h - 70), "BASSO (fine etichetta)", font=f5, fill=0)
    return im


def packbits(row):
    """TIFF PackBits come da manuale Brother (run: -(n-1) + byte; literal: (n-1) + bytes)."""
    out = bytearray()
    i, n = 0, len(row)
    while i < n:
        # run
        j = i
        while j + 1 < n and row[j + 1] == row[i] and j - i < 126:
            j += 1
        run = j - i + 1
        if run >= 2:
            out.append((-(run - 1)) & 0xFF)
            out.append(row[i])
            i = j + 1
            continue
        # literal
        j = i
        while j + 1 < n and (j + 2 >= n or row[j + 1] != row[j + 2]) and j - i < 126:
            j += 1
        lit = row[i:j + 1]
        out.append(len(lit) - 1)
        out.extend(lit)
        i = j + 1
    if len(out) > BYTES_PER_LINE:
        return bytes([BYTES_PER_LINE - 1]) + bytes(row)
    return bytes(out)


def build_job(label_im, media_mm, autocut=True, margin_dots=35, mirror=False):
    spec = CONTINUOUS[media_mm]
    if label_im.size[0] != spec["print"]:
        raise ValueError("larghezza immagine %d != area stampabile %d" % (label_im.size[0], spec["print"]))
    bw = label_im.convert("1")  # bianco=255, nero=0
    if mirror:
        bw = bw.transpose(Image.FLIP_LEFT_RIGHT)
    rows = bw.size[1]

    job = bytearray()
    job += b"\x00" * 400            # invalidate
    job += b"\x1b@"                 # initialize
    job += b"\x1bia\x01"            # raster mode
    job += b"\x1bi!\x00"            # auto status notification ON
    n1 = 0x80 | 0x04 | 0x02         # recovery on + width valid + type valid
    job += b"\x1biz" + bytes([n1, 0x0A, media_mm, 0x00]) + rows.to_bytes(4, "little") + b"\x00\x00"
    job += b"\x1biM" + (b"\x40" if autocut else b"\x00")   # various mode: auto cut
    job += b"\x1biA\x01"            # cut each 1 label
    job += b"\x1biK\x08"            # expanded: cut at end, 300 dpi
    job += b"\x1bid" + margin_dots.to_bytes(2, "little")   # margine (feed)
    job += b"M\x02"                 # TIFF compression

    left = spec["left"]
    zero_lines = 0
    for y in range(rows):
        line = Image.new("1", (TOTAL_PINS, 1), 1)          # 1 = bianco, indice x = numero pin
        line.paste(bw.crop((0, y, bw.size[0], y + 1)), (left, 0))
        # Verificato sul campo (2026-09-03): nella linea raster il PRIMO bit (MSB del primo byte)
        # corrisponde al pin 1295 e l'ultimo al pin 0. Senza questa inversione il testo esce
        # specchiato e l'immagine finisce per 3/4 fuori dal nastro.
        line = line.transpose(Image.FLIP_LEFT_RIGHT)
        packed = line.tobytes()                             # 162 byte
        # in Pillow mode "1": bit 1 = bianco; la stampante vuole bit 1 = nero -> inverti
        packed = bytes(b ^ 0xFF for b in packed)
        if not any(packed):
            job += b"Z"
            zero_lines += 1
        else:
            c = packbits(packed)
            job += b"g\x00" + bytes([len(c)]) + c
    job += b"\x1a"                  # print with feeding (ultima pagina)
    return bytes(job), rows, zero_lines


def fmt_status(d):
    s = decode_status(d[:32])
    return "type=%s phase=%s err1=%s err2=%s notif=%s media=%s/%s" % (
        s["status_type"], s["phase_type"], s["error1"], s["error2"], s["notification"],
        s["media_width_mm"], s["media_type"])


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--render-only", action="store_true")
    ap.add_argument("--no-cut", action="store_true")
    ap.add_argument("--mirror", action="store_true", help="specchia orizzontalmente il raster")
    ap.add_argument("--length-mm", type=float, default=45)
    ap.add_argument("--out", default=os.path.join(os.path.dirname(__file__), "testprint_preview.png"))
    args = ap.parse_args()

    media_mm = 62
    if not args.render_only:
        p = UsbPrinter()
        drain(p)
        p.write(b"\x1biS")
        d = poll_read(p)
        if len(d) < 32:
            print("Nessuna risposta di stato: interrompo.")
            return 1
        s = decode_status(d[:32])
        print("Stato iniziale:", fmt_status(d))
        if s["error1"] or s["error2"]:
            print("Errori presenti, interrompo.")
            return 1
        if not s["media_type"].startswith("0x0A"):
            print("Non e' caricato un nastro continuo: interrompo.")
            return 1
        media_mm = s["media_width_mm"]
        if media_mm not in CONTINUOUS:
            print("Larghezza nastro %d non gestita" % media_mm)
            return 1
        p.close()

    im = render_label(CONTINUOUS[media_mm]["print"], args.length_mm, media_mm)
    im.save(args.out)
    print("Anteprima salvata in", args.out, "dimensione", im.size)

    job, rows, zero_lines = build_job(im, media_mm, autocut=not args.no_cut, mirror=args.mirror)
    print("Job: %d byte, %d linee raster (%d vuote)" % (len(job), rows, zero_lines))
    if args.render_only:
        return 0

    p = UsbPrinter()
    try:
        t0 = time.time()
        CH = 4096
        for i in range(0, len(job), CH):
            p.write(job[i:i + CH], timeout_ms=10000)
        print("Inviato in %.0f ms; ora ascolto gli stati..." % ((time.time() - t0) * 1000))
        deadline = time.time() + 25
        got_done = False
        buf = b""
        while time.time() < deadline:
            d = poll_read(p, max_ms=400, quiet_ms=60)
            if d:
                buf += d
                while len(buf) >= 32:
                    st, buf = buf[:32], buf[32:]
                    s = decode_status(st)
                    print("  +%5.0f ms  %s" % ((time.time() - t0) * 1000, fmt_status(st)))
                    if s["status_type"].startswith("0x01"):
                        got_done = True
                    if s["status_type"].startswith("0x02"):
                        print("  ERRORE riportato dalla stampante")
                        return 2
                    if got_done and s["status_type"].startswith("0x06") and s["phase_type"].startswith("0x00"):
                        print("Stampa completata e stampante tornata in ricezione.")
                        return 0
            time.sleep(0.02)
        print("Timeout attesa stati (done=%s)" % got_done)
        return 3
    finally:
        p.close()


if __name__ == "__main__":
    sys.exit(main())
