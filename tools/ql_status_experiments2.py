"""Esperimento 2: pattern di risposta a ESC i S ripetuto, letture ritardate, effetto di ESC i a."""
import sys
import time
import os

sys.path.insert(0, os.path.dirname(__file__))
from ql_probe import UsbPrinter, decode_status  # noqa: E402


def rd(p, label, timeout_ms=3000, size=64):
    t0 = time.time()
    try:
        d = p.read(size, timeout_ms=timeout_ms)
    except TimeoutError:
        d = b""
    dt = (time.time() - t0) * 1000
    if d:
        info = decode_status(d[:32]) if len(d) >= 32 else {}
        print("  [%s] %d byte in %.0f ms  media=%s/%s type=%s" % (
            label, len(d), dt, info.get("media_width_mm"), info.get("media_length_mm"), info.get("status_type")))
    else:
        print("  [%s] nulla (%.0f ms)" % (label, dt))
    return d


def main():
    p = UsbPrinter()
    try:
        print("A) drain di eventuali risposte pendenti (fino a 3 letture da 1 s)")
        for i in range(3):
            if not rd(p, "drain%d" % i, timeout_ms=1000):
                break

        print("B) 6 richieste ESC i S consecutive, lettura subito dopo ciascuna (3 s)")
        for i in range(6):
            p.write(b"\x1biS")
            rd(p, "req%d" % i)

        print("C) 1 richiesta, poi 3 letture consecutive senza nuove richieste")
        p.write(b"\x1biS")
        for i in range(3):
            rd(p, "read%d" % i, timeout_ms=2000)

        print("D) 1 richiesta, attesa 1.5 s, poi lettura")
        p.write(b"\x1biS")
        time.sleep(1.5)
        rd(p, "delayed")

        print("E) ESC i a 01 da solo: la stampante emette qualcosa?")
        p.write(b"\x1bia\x01")
        rd(p, "after-mode", timeout_ms=1500)
        print("   poi ESC i S")
        p.write(b"\x1biS")
        rd(p, "req-after-mode")
        print("   e una seconda ESC i S")
        p.write(b"\x1biS")
        rd(p, "req-after-mode-2")

        print("F) ESC i S con lettura da 32 byte esatti")
        p.write(b"\x1biS")
        rd(p, "size32", size=32)
        p.write(b"\x1biS")
        rd(p, "size32-b", size=32)
    finally:
        p.close()


if __name__ == "__main__":
    main()
