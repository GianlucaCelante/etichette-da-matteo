#!/bin/bash
# Disinstalla Etichette dal Mac: ferma e toglie il servizio, l'apertura automatica e l'app.
# Di serie NON cancella i dati (database, log, backup): una disinstallazione per sbaglio non perde
# lo storico. Per cancellare anche quelli:  sudo ./uninstall.sh --remove-data
# Corrisponde a uninstall_service.ps1 su Windows.

if [ "$(id -u)" -ne 0 ]; then
    echo "Serve l'amministratore: sudo \"$0\" $*" >&2
    exit 1
fi

DATI="/Library/Application Support/Etichette"

/bin/launchctl bootout system/it.etichette.service >/dev/null 2>&1
/usr/bin/pkill -f "/Applications/Etichette.app/Contents/Resources/app/etichette.jar" >/dev/null 2>&1

# Il LaunchAgent e' caricato nelle sessioni gia' aperte: si scarica per ogni utente collegato.
for uid in $(/usr/bin/who | /usr/bin/awk '{print $1}' | /usr/bin/sort -u | while read -r u; do /usr/bin/id -u "$u" 2>/dev/null; done); do
    /bin/launchctl bootout "gui/$uid/it.etichette.avvio" >/dev/null 2>&1
done

/bin/rm -f /Library/LaunchDaemons/it.etichette.service.plist /Library/LaunchAgents/it.etichette.avvio.plist
/bin/rm -rf /Applications/Etichette.app
/usr/sbin/pkgutil --forget it.etichette.pkg >/dev/null 2>&1

if [ "$1" = "--remove-data" ]; then
    /bin/rm -rf "$DATI"
    echo "Etichette disinstallata, dati cancellati."
else
    echo "Etichette disinstallata. I dati sono rimasti in: $DATI"
    echo "Per cancellarli:  sudo rm -rf \"$DATI\""
fi
