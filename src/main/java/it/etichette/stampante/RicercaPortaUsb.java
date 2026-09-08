package it.etichette.stampante;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.Guid.GUID;
import com.sun.jna.platform.win32.SetupApi;
import com.sun.jna.platform.win32.SetupApi.SP_DEVICE_INTERFACE_DATA;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Ricerca reale dei dispositivi usbprint con SetupApi (GUID_DEVINTERFACE_USBPRINT), filtrando
 * per Vendor ID Brother (04F9). Porting 1:1 di findUsbPrintPaths in
 * tools/spike-jna/QlStatusSpike.java, gia' verificato sulla stampante reale.
 *
 * Non attiva durante i test (profilo "test"): sostituita da un finto che non trova mai nulla,
 * cosi' il contesto Spring di test parte senza toccare hardware reale, indipendentemente da
 * cosa sia collegato al PC che esegue i test.
 */
@Component
@Profile("!test")
public class RicercaPortaUsb implements RicercaPorta {

    private static final Logger log = LoggerFactory.getLogger(RicercaPortaUsb.class);

    private static final String GUID_USBPRINT = "{28d78fad-5a12-11d1-ae5b-0000f803a8c2}";
    private static final int DIGCF_PRESENT = 0x02;
    private static final int DIGCF_DEVICEINTERFACE = 0x10;
    private static final String VID_BROTHER = "04F9";

    @Override
    public List<String> cerca() {
        List<String> risultato = new ArrayList<>();
        GUID guid = new GUID(GUID_USBPRINT);
        SetupApi api = SetupApi.INSTANCE;

        HANDLE hDevInfo = api.SetupDiGetClassDevs(guid, null, null, DIGCF_PRESENT | DIGCF_DEVICEINTERFACE);
        if (hDevInfo == null || WinBase.INVALID_HANDLE_VALUE.equals(hDevInfo)) {
            log.debug("SetupDiGetClassDevs fallita, errore Windows {}", Native.getLastError());
            return risultato;
        }
        try {
            int index = 0;
            while (true) {
                SP_DEVICE_INTERFACE_DATA ifd = new SP_DEVICE_INTERFACE_DATA();
                ifd.cbSize = ifd.size();

                boolean ok = api.SetupDiEnumDeviceInterfaces(hDevInfo, null, guid, index, ifd);
                if (!ok) {
                    break; // ERROR_NO_MORE_ITEMS o altro: si ferma comunque l'enumerazione
                }
                index++;

                IntByReference req = new IntByReference(0);
                api.SetupDiGetDeviceInterfaceDetail(hDevInfo, ifd, null, 0, req, null);
                if (Native.getLastError() != WinError.ERROR_INSUFFICIENT_BUFFER) {
                    continue;
                }

                Memory buf = new Memory(req.getValue());
                buf.clear();
                // cbSize della SP_DEVICE_INTERFACE_DETAIL_DATA: vincolo dell'API, sempre
                // sizeof(DWORD)+sizeof(WCHAR) allineato (8 su x64, 6 su x86).
                int cbSize = Native.POINTER_SIZE == 8 ? 8 : 6;
                buf.setInt(0, cbSize);

                boolean ok2 = api.SetupDiGetDeviceInterfaceDetail(hDevInfo, ifd, buf, req.getValue(), null, null);
                if (!ok2) {
                    continue;
                }
                String path = buf.getWideString(4);
                if (path.toUpperCase().contains("VID_" + VID_BROTHER)) {
                    risultato.add(path);
                }
            }
        } finally {
            api.SetupDiDestroyDeviceInfoList(hDevInfo);
        }
        return risultato;
    }
}
