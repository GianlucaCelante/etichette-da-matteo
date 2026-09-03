r"""Enumerazione delle stampanti USB (interfaccia usbprint) via SetupAPI, senza dipendenze.

find_usbprint_paths(vid="04F9") -> lista di percorsi \\?\USB#VID_...#{28d78fad-...} apribili con CreateFileW.
"""
import ctypes
import ctypes.wintypes as w

setupapi = ctypes.WinDLL("setupapi", use_last_error=True)

DIGCF_PRESENT = 0x02
DIGCF_DEVICEINTERFACE = 0x10
ERROR_NO_MORE_ITEMS = 259
ERROR_INSUFFICIENT_BUFFER = 122


class GUID(ctypes.Structure):
    _fields_ = [("Data1", w.DWORD), ("Data2", w.WORD), ("Data3", w.WORD), ("Data4", ctypes.c_ubyte * 8)]

    @classmethod
    def from_string(cls, s):
        s = s.strip("{}")
        p = s.split("-")
        g = cls()
        g.Data1 = int(p[0], 16)
        g.Data2 = int(p[1], 16)
        g.Data3 = int(p[2], 16)
        tail = bytes.fromhex(p[3] + p[4])
        for i in range(8):
            g.Data4[i] = tail[i]
        return g


class SP_DEVICE_INTERFACE_DATA(ctypes.Structure):
    _fields_ = [("cbSize", w.DWORD), ("InterfaceClassGuid", GUID), ("Flags", w.DWORD),
                ("Reserved", ctypes.POINTER(ctypes.c_ulong))]


GUID_DEVINTERFACE_USBPRINT = GUID.from_string("{28d78fad-5a12-11d1-ae5b-0000f803a8c2}")

setupapi.SetupDiGetClassDevsW.restype = w.HANDLE
setupapi.SetupDiGetClassDevsW.argtypes = [ctypes.POINTER(GUID), w.LPCWSTR, w.HWND, w.DWORD]
setupapi.SetupDiEnumDeviceInterfaces.argtypes = [w.HANDLE, ctypes.c_void_p, ctypes.POINTER(GUID), w.DWORD,
                                                 ctypes.POINTER(SP_DEVICE_INTERFACE_DATA)]
setupapi.SetupDiGetDeviceInterfaceDetailW.argtypes = [w.HANDLE, ctypes.POINTER(SP_DEVICE_INTERFACE_DATA),
                                                      ctypes.c_void_p, w.DWORD, ctypes.POINTER(w.DWORD), ctypes.c_void_p]
setupapi.SetupDiDestroyDeviceInfoList.argtypes = [w.HANDLE]


def find_usbprint_paths(vid="04F9", pid=None):
    guid = GUID_DEVINTERFACE_USBPRINT
    h = setupapi.SetupDiGetClassDevsW(ctypes.byref(guid), None, None, DIGCF_PRESENT | DIGCF_DEVICEINTERFACE)
    if h == ctypes.c_void_p(-1).value or h is None:
        raise OSError(ctypes.get_last_error(), "SetupDiGetClassDevs fallita")
    paths = []
    try:
        i = 0
        while True:
            ifd = SP_DEVICE_INTERFACE_DATA()
            ifd.cbSize = ctypes.sizeof(SP_DEVICE_INTERFACE_DATA)
            if not setupapi.SetupDiEnumDeviceInterfaces(h, None, ctypes.byref(guid), i, ctypes.byref(ifd)):
                if ctypes.get_last_error() == ERROR_NO_MORE_ITEMS:
                    break
                raise OSError(ctypes.get_last_error(), "SetupDiEnumDeviceInterfaces fallita")
            i += 1
            req = w.DWORD(0)
            setupapi.SetupDiGetDeviceInterfaceDetailW(h, ctypes.byref(ifd), None, 0, ctypes.byref(req), None)
            if ctypes.get_last_error() != ERROR_INSUFFICIENT_BUFFER:
                continue
            buf = ctypes.create_string_buffer(req.value)
            # cbSize = sizeof(DWORD) + sizeof(WCHAR) con allineamento: 8 su x64, 6 su x86
            ctypes.cast(buf, ctypes.POINTER(w.DWORD))[0] = 8 if ctypes.sizeof(ctypes.c_void_p) == 8 else 6
            if not setupapi.SetupDiGetDeviceInterfaceDetailW(h, ctypes.byref(ifd), buf, req.value, None, None):
                continue
            path = ctypes.wstring_at(ctypes.addressof(buf) + 4)
            up = path.upper()
            if vid and ("VID_%s" % vid.upper()) not in up:
                continue
            if pid and ("PID_%s" % pid.upper()) not in up:
                continue
            paths.append(path)
    finally:
        setupapi.SetupDiDestroyDeviceInfoList(h)
    return paths


if __name__ == "__main__":
    for p in find_usbprint_paths():
        print(p)
