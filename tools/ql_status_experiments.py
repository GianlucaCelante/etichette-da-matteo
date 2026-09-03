"""Esperimenti per ottenere la risposta di stato (ESC i S) dalla QL-1100 via usbprint.sys."""
import sys
import time
import os

sys.path.insert(0, os.path.dirname(__file__))
from ql_probe import UsbPrinter, decode_status  # noqa: E402


def drain(p, label, timeout_ms=800, size=512):
    got = b""
    while True:
        try:
            chunk = p.read(size, timeout_ms=timeout_ms)
        except TimeoutError:
            break
        if not chunk:
            break
        got += chunk
        if len(got) >= 32:
            break
    print("  [%s] letti %d byte: %s" % (label, len(got), got.hex(" ") if got else "-"))
    return got


def show(data):
    if len(data) >= 32:
        for k, v in decode_status(data[:32]).items():
            print("     %-18s %s" % (k, v))


def main():
    p = UsbPrinter()
    try:
        print("1) drain iniziale (dati pendenti?)")
        drain(p, "drain", timeout_ms=500)

        print("2) ESC i S da solo, lettura 512 byte, 4 s")
        p.write(b"\x1biS")
        d = drain(p, "ESC i S", timeout_ms=4000)
        show(d)

        print("3) ESC i a 01 (raster) + ESC i S")
        p.write(b"\x1bia\x01")
        time.sleep(0.2)
        p.write(b"\x1biS")
        d = drain(p, "raster+status", timeout_ms=4000)
        show(d)

        print("4) tutto in una write: 200x00 + ESC @ + ESC i a 01 + ESC i S")
        p.write(b"\x00" * 200 + b"\x1b@" + b"\x1bia\x01" + b"\x1biS")
        d = drain(p, "one-shot", timeout_ms=4000)
        show(d)

        print("5) ESC i a 00 (ESC/P) + ESC i S")
        p.write(b"\x1bia\x00")
        time.sleep(0.2)
        p.write(b"\x1biS")
        d = drain(p, "escp+status", timeout_ms=4000)
        show(d)

        print("6) ESC i a 03 (P-touch Template) + ESC i S")
        p.write(b"\x1bia\x03")
        time.sleep(0.2)
        p.write(b"\x1biS")
        d = drain(p, "ptt+status", timeout_ms=4000)
        show(d)

        print("7) ripristino raster: ESC i a 01, poi lettura lunga 6 s")
        p.write(b"\x1bia\x01\x1biS")
        d = drain(p, "final", timeout_ms=6000)
        show(d)
    finally:
        p.close()


if __name__ == "__main__":
    main()
