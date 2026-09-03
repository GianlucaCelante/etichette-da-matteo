"""
Sonda raw per Brother QL-1100 / QL-1100c via usbprint.sys (Windows, nessuna dipendenza).

- Apre l'interfaccia USB printer-class del dispositivo
- Legge l'IEEE 1284 Device ID (IOCTL_USBPRINT_GET_1284_ID)
- Legge lo stato porta (IOCTL_USBPRINT_GET_LPT_STATUS)
- Invia ESC @ + ESC i S e decodifica i 32 byte di stato
"""
import ctypes
import ctypes.wintypes as w
import sys
import time

DEVICE_PATH = r"\\?\USB#VID_04F9&PID_20A7#000A5G588428#{28d78fad-5a12-11d1-ae5b-0000f803a8c2}"

k32 = ctypes.WinDLL("kernel32", use_last_error=True)

GENERIC_READ = 0x80000000
GENERIC_WRITE = 0x40000000
FILE_SHARE_READ = 0x1
FILE_SHARE_WRITE = 0x2
OPEN_EXISTING = 3
FILE_FLAG_OVERLAPPED = 0x40000000
INVALID_HANDLE_VALUE = ctypes.c_void_p(-1).value
ERROR_IO_PENDING = 997
WAIT_OBJECT_0 = 0

IOCTL_USBPRINT_GET_LPT_STATUS = 0x220030
IOCTL_USBPRINT_GET_1284_ID = 0x220034


class OVERLAPPED(ctypes.Structure):
    _fields_ = [
        ("Internal", ctypes.c_void_p),
        ("InternalHigh", ctypes.c_void_p),
        ("Offset", w.DWORD),
        ("OffsetHigh", w.DWORD),
        ("hEvent", w.HANDLE),
    ]


k32.CreateFileW.restype = w.HANDLE
k32.CreateFileW.argtypes = [w.LPCWSTR, w.DWORD, w.DWORD, ctypes.c_void_p, w.DWORD, w.DWORD, w.HANDLE]
k32.CreateEventW.restype = w.HANDLE
k32.CreateEventW.argtypes = [ctypes.c_void_p, w.BOOL, w.BOOL, w.LPCWSTR]
k32.WriteFile.argtypes = [w.HANDLE, ctypes.c_void_p, w.DWORD, ctypes.POINTER(w.DWORD), ctypes.POINTER(OVERLAPPED)]
k32.ReadFile.argtypes = [w.HANDLE, ctypes.c_void_p, w.DWORD, ctypes.POINTER(w.DWORD), ctypes.POINTER(OVERLAPPED)]
k32.DeviceIoControl.argtypes = [w.HANDLE, w.DWORD, ctypes.c_void_p, w.DWORD, ctypes.c_void_p, w.DWORD,
                                ctypes.POINTER(w.DWORD), ctypes.POINTER(OVERLAPPED)]
k32.GetOverlappedResult.argtypes = [w.HANDLE, ctypes.POINTER(OVERLAPPED), ctypes.POINTER(w.DWORD), w.BOOL]
k32.WaitForSingleObject.argtypes = [w.HANDLE, w.DWORD]
k32.CancelIoEx.argtypes = [w.HANDLE, ctypes.POINTER(OVERLAPPED)]
k32.CloseHandle.argtypes = [w.HANDLE]


class UsbPrinter:
    def __init__(self, path=DEVICE_PATH):
        self.h = k32.CreateFileW(path, GENERIC_READ | GENERIC_WRITE, FILE_SHARE_READ | FILE_SHARE_WRITE,
                                 None, OPEN_EXISTING, FILE_FLAG_OVERLAPPED, None)
        if self.h == INVALID_HANDLE_VALUE or self.h is None:
            err = ctypes.get_last_error()
            raise OSError(err, "CreateFileW fallita: " + ctypes.FormatError(err))

    def close(self):
        k32.CloseHandle(self.h)

    def _overlapped(self, fn, timeout_ms):
        ov = OVERLAPPED()
        ov.hEvent = k32.CreateEventW(None, True, False, None)
        n = w.DWORD(0)
        try:
            ok = fn(ov, n)
            if not ok:
                err = ctypes.get_last_error()
                if err != ERROR_IO_PENDING:
                    raise OSError(err, ctypes.FormatError(err))
                if k32.WaitForSingleObject(ov.hEvent, timeout_ms) != WAIT_OBJECT_0:
                    k32.CancelIoEx(self.h, ctypes.byref(ov))
                    k32.GetOverlappedResult(self.h, ctypes.byref(ov), ctypes.byref(n), True)
                    raise TimeoutError("timeout dopo %d ms" % timeout_ms)
                if not k32.GetOverlappedResult(self.h, ctypes.byref(ov), ctypes.byref(n), False):
                    err = ctypes.get_last_error()
                    raise OSError(err, ctypes.FormatError(err))
            return n.value
        finally:
            k32.CloseHandle(ov.hEvent)

    def write(self, data, timeout_ms=5000):
        buf = ctypes.create_string_buffer(data, len(data))
        return self._overlapped(
            lambda ov, n: k32.WriteFile(self.h, buf, len(data), ctypes.byref(n), ctypes.byref(ov)), timeout_ms)

    def read(self, size=64, timeout_ms=3000):
        buf = ctypes.create_string_buffer(size)
        n = self._overlapped(
            lambda ov, cnt: k32.ReadFile(self.h, buf, size, ctypes.byref(cnt), ctypes.byref(ov)), timeout_ms)
        return buf.raw[:n]

    def ioctl(self, code, out_size=1024, timeout_ms=3000):
        buf = ctypes.create_string_buffer(out_size)
        n = self._overlapped(
            lambda ov, cnt: k32.DeviceIoControl(self.h, code, None, 0, buf, out_size, ctypes.byref(cnt),
                                                ctypes.byref(ov)), timeout_ms)
        return buf.raw[:n]


MODEL_CODES = {
    0x31: "QL-560", 0x32: "QL-570", 0x33: "QL-580N", 0x34: "QL-1060N", 0x35: "QL-700",
    0x36: "QL-710W", 0x37: "QL-720NW", 0x38: "QL-800", 0x39: "QL-810W", 0x41: "QL-820NWB",
    0x43: "QL-1100", 0x44: "QL-1110NWB", 0x45: "QL-1115NWB", 0x4F: "QL-500/550", 0x50: "QL-1050",
    0x51: "QL-650TD",
}
MEDIA_TYPES = {0x00: "nessun supporto", 0x0A: "nastro continuo", 0x0B: "etichette pretagliate (die-cut)",
               0x4A: "nastro continuo", 0x4B: "etichette pretagliate (die-cut)", 0xFF: "supporto incompatibile"}
ERR1 = {0: "no media", 1: "end of media (solo die-cut)", 2: "cutter jam", 3: "weak batteries",
        4: "printer in use", 5: "printer turned off", 6: "high-voltage adapter", 7: "fan motor error"}
ERR2 = {0: "replace media", 1: "expansion buffer full", 2: "communication error",
        3: "communication buffer full", 4: "cover open", 5: "cancel key", 6: "media cannot be fed",
        7: "system error"}
STATUS_TYPES = {0x00: "risposta a richiesta stato", 0x01: "stampa completata", 0x02: "errore",
                0x04: "spegnimento", 0x05: "notifica", 0x06: "cambio fase"}
PHASE_TYPES = {0x00: "in attesa di ricezione", 0x01: "in stampa"}


def decode_status(s):
    if len(s) < 32:
        return {"raw": s.hex(" "), "errore": "ricevuti solo %d byte" % len(s)}

    def bits(b, table):
        return [name for bit, name in table.items() if b & (1 << bit)]

    return {
        "raw": s.hex(" "),
        "head_mark": hex(s[0]), "size": s[1], "brother_code": chr(s[2]), "series_code": chr(s[3]),
        "model_code": "0x%02X -> %s" % (s[4], MODEL_CODES.get(s[4], "sconosciuto")),
        "country_code": chr(s[5]),
        "error1": bits(s[8], ERR1), "error2": bits(s[9], ERR2),
        "media_width_mm": s[10],
        "media_type": "0x%02X -> %s" % (s[11], MEDIA_TYPES.get(s[11], "sconosciuto")),
        "mode_byte14": hex(s[14]),
        "media_length_mm": s[17],
        "status_type": "0x%02X -> %s" % (s[18], STATUS_TYPES.get(s[18], "?")),
        "phase_type": "0x%02X -> %s" % (s[19], PHASE_TYPES.get(s[19], "?")),
        "phase_number": (s[20] << 8) | s[21],
        "notification": hex(s[22]),
        "reserved_23_31": s[23:32].hex(" "),
    }


def main():
    print("Apro " + DEVICE_PATH)
    p = UsbPrinter()
    try:
        try:
            raw = p.ioctl(IOCTL_USBPRINT_GET_1284_ID)
            text = raw
            if len(raw) >= 2:
                ln = (raw[0] << 8) | raw[1]
                if abs(ln - len(raw)) <= 2:
                    text = raw[2:]
            print("\n[IEEE 1284 Device ID]")
            print("  " + text.decode("latin-1", "replace").strip("\x00"))
        except Exception as e:
            print("\n[IEEE 1284 Device ID] errore:", e)

        try:
            st = p.ioctl(IOCTL_USBPRINT_GET_LPT_STATUS, 4)
            print("\n[USB printer-class port status]")
            if st:
                b = st[0]
                print("  byte=0x%02X  not_error=%s  selected=%s  paper_empty=%s" % (
                    b, bool(b & 0x08), bool(b & 0x10), bool(b & 0x20)))
            else:
                print("  nessun dato")
        except Exception as e:
            print("\n[USB port status] errore:", e)

        print("\n[ESC i S - status information request]")
        p.write(b"\x00" * 400 + b"\x1b@")
        time.sleep(0.1)
        p.write(b"\x1biS")
        data = b""
        for _ in range(5):
            try:
                chunk = p.read(64, timeout_ms=2000)
            except TimeoutError:
                break
            data += chunk
            if len(data) >= 32:
                break
        if not data:
            print("  nessuna risposta (timeout)")
        else:
            for k, v in decode_status(data).items():
                print("  %-18s %s" % (k, v))
    finally:
        p.close()


if __name__ == "__main__":
    main()
