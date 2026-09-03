"""Analizza un job raster Brother QL (es. catturato dal driver Windows su porta file) e ne riassume i comandi.

  python ql_parse_job.py capture.prn
"""
import sys


def parse(data):
    i, n = 0, len(data)
    out = []
    raster_lines = 0
    zero_lines = 0
    raster_bytes = 0

    def flush_raster():
        nonlocal raster_lines, zero_lines, raster_bytes
        if raster_lines or zero_lines:
            out.append("  ... %d linee raster 'g' (%d byte dati) + %d linee vuote 'Z'" % (raster_lines, raster_bytes, zero_lines))
            raster_lines = zero_lines = raster_bytes = 0

    while i < n:
        b = data[i]
        if b == 0x00:
            j = i
            while j < n and data[j] == 0x00:
                j += 1
            flush_raster()
            out.append("@%06d  invalidate: %d byte NUL" % (i, j - i))
            i = j
            continue
        if b == 0x1B and i + 1 < n and data[i + 1] == 0x40:
            flush_raster(); out.append("@%06d  ESC @  initialize" % i); i += 2; continue
        if b == 0x1B and i + 2 < n and data[i + 1] == 0x69:
            c = data[i + 2]
            if c == 0x61:
                flush_raster(); out.append("@%06d  ESC i a %02X  switch mode (%s)" % (i, data[i + 3], {0: "ESC/P", 1: "raster", 3: "P-touch Template"}.get(data[i + 3], "?"))); i += 4; continue
            if c == 0x21:
                flush_raster(); out.append("@%06d  ESC i ! %02X  auto status notify (%s)" % (i, data[i + 3], "on" if data[i + 3] == 0 else "off")); i += 4; continue
            if c == 0x7A:
                p = data[i + 3:i + 13]
                rn = int.from_bytes(p[4:8], "little")
                flags = []
                if p[0] & 0x02: flags.append("tipo")
                if p[0] & 0x04: flags.append("larghezza")
                if p[0] & 0x08: flags.append("lunghezza")
                if p[0] & 0x40: flags.append("PRIORITA' QUALITA'")
                if p[0] & 0x80: flags.append("recovery")
                flush_raster()
                out.append("@%06d  ESC i z  print info: n1=%02X [%s] tipo=%02X largh=%d lung=%d linee=%d pagina=%s n10=%02X" % (
                    i, p[0], ",".join(flags), p[1], p[2], p[3], rn, "prima" if p[8] == 0 else "altra", p[9]))
                i += 13; continue
            if c == 0x4D:
                flush_raster(); out.append("@%06d  ESC i M %02X  various mode (auto cut=%s)" % (i, data[i + 3], bool(data[i + 3] & 0x40))); i += 4; continue
            if c == 0x41:
                flush_raster(); out.append("@%06d  ESC i A %02X  cut each %d labels" % (i, data[i + 3], data[i + 3])); i += 4; continue
            if c == 0x4B:
                v = data[i + 3]
                flush_raster(); out.append("@%06d  ESC i K %02X  expanded (cut at end=%s, 600dpi bit=%s, altri bit=%02X)" % (i, v, bool(v & 0x08), bool(v & 0x40), v & ~0x48 & 0xFF)); i += 4; continue
            if c == 0x64:
                m = data[i + 3] | (data[i + 4] << 8)
                flush_raster(); out.append("@%06d  ESC i d  margine %d dot (%.1f mm)" % (i, m, m / 11.81)); i += 5; continue
            if c == 0x53:
                flush_raster(); out.append("@%06d  ESC i S  status request" % i); i += 3; continue
            if c == 0x55:
                # comandi "job ID" interni: ESC i U ... lunghezza sconosciuta: mostra i 16 byte successivi
                flush_raster(); out.append("@%06d  ESC i U  (interno) %s" % (i, data[i:i + 20].hex(" "))); i += 3; continue
            flush_raster(); out.append("@%06d  ESC i %c (%02X) sconosciuto: %s" % (i, c, c, data[i:i + 16].hex(" "))); i += 3; continue
        if b == 0x4D and i + 1 < n and data[i + 1] in (0, 1, 2):
            flush_raster(); out.append("@%06d  M %02X  compressione (%s)" % (i, data[i + 1], {0: "nessuna", 2: "TIFF"}.get(data[i + 1], "?"))); i += 2; continue
        if b == 0x67:
            cnt = data[i + 2]
            raster_lines += 1; raster_bytes += cnt; i += 3 + cnt; continue
        if b == 0x5A:
            zero_lines += 1; i += 1; continue
        if b == 0x0C:
            flush_raster(); out.append("@%06d  FF  stampa (pagina intermedia)" % i); i += 1; continue
        if b == 0x1A:
            flush_raster(); out.append("@%06d  ^Z  stampa con avanzamento (ultima pagina)" % i); i += 1; continue
        flush_raster(); out.append("@%06d  byte sconosciuto %02X: %s" % (i, b, data[i:i + 16].hex(" "))); i += 1
    flush_raster()
    return out


if __name__ == "__main__":
    d = open(sys.argv[1], "rb").read()
    print("file: %s, %d byte" % (sys.argv[1], len(d)))
    for line in parse(d):
        print(line)
