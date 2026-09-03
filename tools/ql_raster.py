"""Protocollo raster Brother QL-1100/1100c: costruzione job, lettura stato, resa etichette di prova.

Implementazione di riferimento verificata sul campo (2026-09-03) su rotoli continui 62 e 102 mm.
Regole importanti:
- linea raster = 162 byte; il primo bit trasmesso e' il pin 1295 (linea costruita in ordine di pin e poi invertita)
- la stampante risponde ai poll USB con pacchetti vuoti finche' non ha dati: leggere in polling
- le risposte non lette restano in coda: svuotare prima di ogni richiesta di stato
- durante la stampa non inviare comandi (nemmeno ESC i S): gli stati arrivano da soli
"""
import os
import sys
import time

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(__file__))
from ql_probe import UsbPrinter, decode_status  # noqa: E402

TOTAL_PINS = 1296
BYTES_PER_LINE = TOTAL_PINS // 8
DPI = 300

# nastro continuo: pin del margine sinistro e pin dell'area di stampa (verificati per 62 e 102)
CONTINUOUS = {
    62: {"left": 544, "print": 696},
    102: {"left": 76, "print": 1164},
    103: {"left": 58, "print": 1200},
}

FONT_BOLD = r"C:\Windows\Fonts\arialbd.ttf"
FONT_REG = r"C:\Windows\Fonts\arial.ttf"


def mm(v, dpi=DPI):
    return int(round(v * dpi / 25.4))


# ----------------------------------------------------------------------------- compressione

def packbits(row):
    """TIFF PackBits per byte (manuale Brother): run -> -(n-1), byte ; literal -> (n-1), bytes."""
    out = bytearray()
    i, n = 0, len(row)
    while i < n:
        j = i
        while j + 1 < n and row[j + 1] == row[i] and j - i < 126:
            j += 1
        run = j - i + 1
        if run >= 2:
            out.append((-(run - 1)) & 0xFF)
            out.append(row[i])
            i = j + 1
            continue
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


# ----------------------------------------------------------------------------- job

def line_bytes(bw, y, left):
    line = Image.new("1", (TOTAL_PINS, 1), 1)           # indice x = numero pin, 1 = bianco
    line.paste(bw.crop((0, y, bw.size[0], y + 1)), (left, 0))
    line = line.transpose(Image.FLIP_LEFT_RIGHT)         # primo bit trasmesso = pin 1295
    packed = bytes(b ^ 0xFF for b in line.tobytes())     # bit 1 = nero
    if not any(packed):
        return b"Z"
    c = packbits(packed)
    return b"g\x00" + bytes([len(c)]) + c


def page_control(media_mm, rows, first_page, autocut, cut_each, cut_at_end, hires, margin_dots, quality):
    n1 = 0x80 | 0x04 | 0x02 | (0x40 if quality else 0)
    b = b"\x1bia\x01" + b"\x1bi!\x00"
    b += b"\x1biz" + bytes([n1, 0x0A, media_mm, 0]) + rows.to_bytes(4, "little") + bytes([0 if first_page else 1, 0])
    b += b"\x1biM" + bytes([0x40 if autocut else 0])
    b += b"\x1biA" + bytes([max(1, min(255, cut_each))])
    b += b"\x1biK" + bytes([(0x08 if cut_at_end else 0) | (0x40 if hires else 0)])
    b += b"\x1bid" + int(margin_dots).to_bytes(2, "little")
    b += b"M\x02"
    return b


def build_job(pages, media_mm, autocut=True, cut_each=1, cut_at_end=True, hires=False, margin_dots=35, quality=False):
    """pages: lista di immagini PIL (larghezza = area di stampa del rotolo; altezza = linee raster,
    a 300 dpi oppure 600 dpi se hires). Ritorna i byte del job completo."""
    spec = CONTINUOUS[media_mm]
    job = bytearray(b"\x00" * 400 + b"\x1b@")
    for i, im in enumerate(pages):
        bw = im.convert("1")
        if bw.size[0] != spec["print"]:
            raise ValueError("larghezza %d != area di stampa %d" % (bw.size[0], spec["print"]))
        rows = bw.size[1]
        job += page_control(media_mm, rows, i == 0, autocut, cut_each, cut_at_end, hires, margin_dots, quality)
        for y in range(rows):
            job += line_bytes(bw, y, spec["left"])
        job += b"\x0c" if i < len(pages) - 1 else b"\x1a"
    return bytes(job)


def build_page_job(im, media_mm, first=True, last=True, autocut=True, cut_each=1, cut_at_end=True, hires=False,
                   margin_dots=35, quality=False):
    """Una sola pagina, da inviare separatamente: la prima con invalidate+ESC @, le altre no;
    l'ultima chiude con 1A (stampa e avanza), le altre con FF."""
    spec = CONTINUOUS[media_mm]
    bw = im.convert("1")
    if bw.size[0] != spec["print"]:
        raise ValueError("larghezza %d != area di stampa %d" % (bw.size[0], spec["print"]))
    rows = bw.size[1]
    job = bytearray()
    if first:
        job += b"\x00" * 400 + b"\x1b@"
    job += page_control(media_mm, rows, first, autocut, cut_each, cut_at_end, hires, margin_dots, quality)
    for y in range(rows):
        job += line_bytes(bw, y, spec["left"])
    job += b"\x1a" if last else b"\x0c"
    return bytes(job)


# ----------------------------------------------------------------------------- stato

def poll_read(p, max_ms=1500, quiet_ms=150, size=64):
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


def get_status(p):
    """Svuota la coda, invia ESC i S e ritorna lo stato decodificato (o None)."""
    drain(p)
    p.write(b"\x1biS")
    d = poll_read(p)
    if len(d) < 32:
        return None
    return decode_status(d[:32])


def fmt(s):
    if s is None:
        return "nessuna risposta"
    return "type=%s phase=%s err1=%s err2=%s notif=%s media=%s mm %s" % (
        s["status_type"][:4], s["phase_type"][:4], s["error1"], s["error2"], s["notification"],
        s["media_width_mm"], s["media_type"][:4])


def send_job(p, job, chunk=4096):
    t0 = time.time()
    for i in range(0, len(job), chunk):
        p.write(job[i:i + chunk], timeout_ms=10000)
    return (time.time() - t0) * 1000


def follow_print(p, expected_pages, timeout_s=30, log=print):
    """Ascolta gli stati spontanei fino a fine job. Ritorna (esito, lista stati)."""
    t0 = time.time()
    deadline = t0 + timeout_s
    buf = b""
    completed = 0
    seen = []
    while time.time() < deadline:
        d = poll_read(p, max_ms=400, quiet_ms=60)
        if d:
            buf += d
            while len(buf) >= 32:
                st, buf = buf[:32], buf[32:]
                s = decode_status(st)
                s["_t_ms"] = (time.time() - t0) * 1000
                seen.append(s)
                log("  +%6.0f ms  %s" % (s["_t_ms"], fmt(s)))
                if s["status_type"].startswith("0x02"):
                    return "errore", seen
                if s["status_type"].startswith("0x01"):
                    completed += 1
                if completed >= expected_pages and s["status_type"].startswith("0x06") and s["phase_type"].startswith("0x00"):
                    return "ok", seen
        time.sleep(0.02)
    return "timeout", seen


# ----------------------------------------------------------------------------- resa etichette di prova

def render_lines(width_px, length_mm, lines, border=True, dpi_y=DPI):
    """lines: lista di (testo, dimensione px, grassetto). Ritorna immagine L (bianco 255)."""
    h = mm(length_mm, dpi_y)
    sy = dpi_y / DPI
    im = Image.new("L", (width_px, h), 255)
    d = ImageDraw.Draw(im)
    if border:
        d.rectangle([0, 0, width_px - 1, h - 1], outline=0, width=3)
    y = int(10 * sy)
    for text, size, bold in lines:
        f = ImageFont.truetype(FONT_BOLD if bold else FONT_REG, size)
        tw = d.textlength(text, font=f)
        if sy != 1:
            # testo "stirato" verticalmente: disegna su layer e ridimensiona
            layer = Image.new("L", (width_px, size + 8), 255)
            ImageDraw.Draw(layer).text(((width_px - tw) / 2, 0), text, font=f, fill=0)
            layer = layer.resize((width_px, int((size + 8) * sy)), Image.LANCZOS)
            im.paste(layer, (0, y))
            y += int((size + 14) * sy)
        else:
            d.text(((width_px - tw) / 2, y), text, font=f, fill=0)
            y += size + 14
    return im


def render_detail(width_px, length_mm, title):
    """Etichetta con dettagli fini resa a 600x600 dpi; ritorna (versione 300 dpi, versione 300x600 dpi)."""
    W2, H2 = width_px * 2, mm(length_mm, 600)
    im = Image.new("L", (W2, H2), 255)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, W2 - 1, H2 - 1], outline=0, width=4)
    f = ImageFont.truetype(FONT_BOLD, 64)
    d.text((24, 16), title, font=f, fill=0)
    y = 100
    for pt in (4, 5, 6, 8):
        px = int(pt * 600 / 72)
        fr = ImageFont.truetype(FONT_REG, px)
        d.text((24, y), "%d pt: Il veloce lupo bruno salta sopra il cane pigro 0123456789" % pt, font=fr, fill=0)
        y += px + 10
    # linee sottili a vari angoli (1 px a 600 dpi)
    x0 = 24
    for k in range(0, 9):
        d.line([(x0, H2 - 30), (x0 + 80 + k * 30, y + 10)], fill=0, width=1)
    # pattern orizzontale 1 px acceso / 1 px spento (600 dpi)
    px0 = W2 // 2
    for yy in range(y + 10, H2 - 30, 2):
        d.line([(px0, yy), (px0 + 200, yy)], fill=0, width=1)
    # pattern 2 px acceso / 2 px spento
    for yy in range(y + 10, H2 - 30, 4):
        d.line([(px0 + 240, yy), (px0 + 440, yy)], fill=0, width=2)
    # cerchi concentrici sottili
    cx, cy = W2 - 220, (y + H2 - 30) // 2
    for r in range(20, 160, 20):
        d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=0, width=1)

    def thr(img):
        return img.point(lambda v: 255 if v > 140 else 0).convert("1")

    im300 = thr(im.resize((width_px, H2 // 2), Image.LANCZOS))
    im600 = thr(im.resize((width_px, H2), Image.LANCZOS))
    return im300, im600
