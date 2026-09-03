"""Esperimento 3: verifica dell'ipotesi "l'ultimo trasferimento resta nel buffer finche' non arriva il successivo".

Ogni passo: scrive, poi fa polling in lettura ogni 20 ms per max `poll_ms` e riporta quando (e se) arrivano dati.
"""
import sys
import time
import os

sys.path.insert(0, os.path.dirname(__file__))
from ql_probe import UsbPrinter, decode_status  # noqa: E402


def poll(p, label, poll_ms=2000, size=64):
    t0 = time.time()
    got = b""
    while (time.time() - t0) * 1000 < poll_ms:
        try:
            d = p.read(size, timeout_ms=500)
        except TimeoutError:
            d = b""
        if d:
            got += d
            dt = (time.time() - t0) * 1000
            info = decode_status(got[:32]) if len(got) >= 32 else {}
            print("  [%s] %d byte dopo %.0f ms  status_type=%s media=%s" % (
                label, len(got), dt, info.get("status_type"), info.get("media_width_mm")))
            return got
        time.sleep(0.02)
    print("  [%s] nulla in %d ms" % (label, poll_ms))
    return b""


def main():
    p = UsbPrinter()
    try:
        print("T0) drain")
        poll(p, "drain", 800)

        print("T1) ESC i S da solo, poll 2 s")
        p.write(b"\x1biS")
        poll(p, "T1")

        print("T2) un singolo NUL (invalidate), poll 2 s -> se arriva la risposta, l'ipotesi 'lag di un trasferimento' e' confermata")
        p.write(b"\x00")
        poll(p, "T2")

        print("T3) ESC i S + NUL nella stessa write, poll 2 s")
        p.write(b"\x1biS\x00")
        poll(p, "T3")

        print("T4) ESC i S + NUL di nuovo (ripetibilita')")
        p.write(b"\x1biS\x00")
        poll(p, "T4")

        print("T5) ESC i S + 61 NUL (pacchetto pieno da 64 byte)")
        p.write(b"\x1biS" + b"\x00" * 61)
        poll(p, "T5")

        print("T6) ESC i S + 62 NUL (65 byte: pacchetto pieno + 1)")
        p.write(b"\x1biS" + b"\x00" * 62)
        poll(p, "T6")

        print("T7) ESC i S da solo, poll 1 s; poi ESC i S da solo, poll 2 s (la seconda richiesta sblocca la prima?)")
        p.write(b"\x1biS")
        poll(p, "T7a", 1000)
        p.write(b"\x1biS")
        poll(p, "T7b")

        print("T8) drain finale con NUL")
        p.write(b"\x00")
        poll(p, "T8")
        poll(p, "T8-bis", 800)
    finally:
        p.close()


if __name__ == "__main__":
    main()
