#!/bin/bash
# =============================================================================
# Etichette - build dell'installer per macOS: Maven -> jlink -> pkgbuild -> .pkg
# =============================================================================
# Va lanciato SU UN MAC (jlink, libusb e pkgbuild producono binari del Mac su cui girano):
# un Mac Apple Silicon produce un pacchetto per Apple Silicon, un Mac Intel uno per Intel.
#
# Uso, dalla radice del repository:
#   installer/mac/Build-Setup.sh                    # versione letta da pom.xml (senza -SNAPSHOT)
#   installer/mac/Build-Setup.sh --version 0.2.0    # forza la versione
#   installer/mac/Build-Setup.sh --skip-maven       # jar gia' costruito in target/
#
# Richiede: JDK 17 (con jlink e la cartella jmods, es. Temurin 17), Maven 3.9, libusb
# (brew install libusb). Output: target/installer-mac/Etichette-<versione>-<arch>.pkg
# Il pacchetto non e' firmato (deciso con Gianluca): al primo avvio dal Finder serve clic destro
# > Apri, oppure si installa da terminale con: sudo installer -pkg <file>.pkg -target /
# =============================================================================
set -euo pipefail

VERSIONE=""
SKIP_MAVEN=0
while [ $# -gt 0 ]; do
    case "$1" in
        --version) VERSIONE="$2"; shift 2 ;;
        --skip-maven) SKIP_MAVEN=1; shift ;;
        *) echo "Opzione sconosciuta: $1" >&2; exit 2 ;;
    esac
done

QUI="$(cd "$(dirname "$0")" && pwd)"
RADICE="$(cd "$QUI/../.." && pwd)"
TARGET="$RADICE/target"
RUNTIME="$TARGET/runtime-mac"
STAGE="$TARGET/stage-mac"
USCITA="$TARGET/installer-mac"
ARCH="$(uname -m)"   # arm64 oppure x86_64

[ "$(uname -s)" = "Darwin" ] || { echo "Questo script va lanciato su macOS." >&2; exit 1; }

# --- Versione: dal pom (primo <version> dopo </parent>, senza -SNAPSHOT) ------
if [ -z "$VERSIONE" ]; then
    VERSIONE="$(sed -n '/<\/parent>/,$p' "$RADICE/pom.xml" | grep -m1 '<version>' | sed -E 's/.*<version>([^<]+)<\/version>.*/\1/; s/-SNAPSHOT$//')"
    [ -n "$VERSIONE" ] || { echo "Impossibile leggere la versione da pom.xml: usa --version." >&2; exit 1; }
    echo "  Versione (da pom.xml): $VERSIONE"
fi

# --- JDK 17 ------------------------------------------------------------------
if [ -z "${JAVA_HOME:-}" ]; then
    JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
fi
[ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/jlink" ] && [ -d "$JAVA_HOME/jmods" ] ||
    { echo "JDK 17 con jlink e jmods non trovato: imposta JAVA_HOME." >&2; exit 1; }

# --- libusb ------------------------------------------------------------------
LIBUSB=""
for candidato in "$(brew --prefix libusb 2>/dev/null)/lib/libusb-1.0.dylib" /opt/homebrew/lib/libusb-1.0.dylib /usr/local/lib/libusb-1.0.dylib; do
    if [ -f "$candidato" ]; then LIBUSB="$candidato"; break; fi
done
[ -n "$LIBUSB" ] || { echo "libusb non trovata: brew install libusb" >&2; exit 1; }

echo "=============================================="
echo " Etichette per macOS - build $VERSIONE ($ARCH)"
echo "=============================================="
echo "  JDK    : $JAVA_HOME"
echo "  libusb : $LIBUSB"

# --- 1. Maven ------------------------------------------------------------------
if [ "$SKIP_MAVEN" -eq 1 ]; then
    echo "[1/4] Maven saltato (--skip-maven)"
else
    echo "[1/4] Maven clean package"
    (cd "$RADICE" && mvn clean package -DskipTests -B)
fi
JAR="$TARGET/etichette-$VERSIONE.jar"
[ -f "$JAR" ] || { echo "Jar non trovato: $JAR (esegui Maven senza --skip-maven)." >&2; exit 1; }

# --- 2. Immagine jlink (stessi moduli della versione Windows) -------------------
echo "[2/4] Immagine jlink"
rm -rf "$RUNTIME"
"$JAVA_HOME/bin/jlink" \
    --module-path "$JAVA_HOME/jmods" \
    --add-modules java.base,java.logging,java.sql,java.naming,java.xml,java.management,java.instrument,java.desktop,java.security.jgss,java.net.http,jdk.unsupported,jdk.crypto.ec,jdk.charsets,jdk.zipfs \
    --strip-debug --no-man-pages --no-header-files --compress=2 \
    --output "$RUNTIME"

# --- 3. Albero dei file da installare -----------------------------------------
echo "[3/4] Albero del pacchetto"
rm -rf "$STAGE"
APP="$STAGE/Applications/Etichette.app"
RISORSE="$APP/Contents/Resources"
mkdir -p "$APP/Contents/MacOS" "$RISORSE/app" "$RISORSE/lib" "$STAGE/Library/LaunchDaemons" "$STAGE/Library/LaunchAgents"

cp "$JAR" "$RISORSE/app/etichette.jar"
cp -R "$RUNTIME" "$RISORSE/runtime"
cp "$QUI/uninstall.sh" "$RISORSE/uninstall.sh"
cp "$QUI/app/Etichette" "$APP/Contents/MacOS/Etichette"
sed "s/@VERSIONE@/$VERSIONE/g" "$QUI/app/Info.plist" > "$APP/Contents/Info.plist"
cp "$QUI/launchd/it.etichette.service.plist" "$STAGE/Library/LaunchDaemons/"
cp "$QUI/launchd/it.etichette.avvio.plist" "$STAGE/Library/LaunchAgents/"
chmod 755 "$APP/Contents/MacOS/Etichette" "$RISORSE/uninstall.sh"

# libusb: copia (non il link simbolico di Homebrew), nome interno relativo, firma ad hoc (su
# Apple Silicon una libreria modificata e non firmata viene rifiutata dal caricatore).
cp -L "$LIBUSB" "$RISORSE/lib/libusb-1.0.dylib"
install_name_tool -id "@loader_path/libusb-1.0.dylib" "$RISORSE/lib/libusb-1.0.dylib"
codesign --force -s - "$RISORSE/lib/libusb-1.0.dylib"

# Icona: iconset dalle dimensioni del PNG sorgente.
ICONSET="$TARGET/etichette.iconset"
rm -rf "$ICONSET"; mkdir -p "$ICONSET"
for d in 16 32 64 128 256 512; do
    sips -z $d $d "$QUI/etichette-1024.png" --out "$ICONSET/icon_${d}x${d}.png" >/dev/null
    sips -z $((d*2)) $((d*2)) "$QUI/etichette-1024.png" --out "$ICONSET/icon_${d}x${d}@2x.png" >/dev/null
done
iconutil -c icns "$ICONSET" -o "$RISORSE/etichette.icns"

# --- 4. Pacchetto ---------------------------------------------------------------
echo "[4/4] pkgbuild + productbuild"
mkdir -p "$USCITA"
COMPONENTE="$USCITA/componente.pkg"
FINALE="$USCITA/Etichette-$VERSIONE-$ARCH.pkg"
chmod 755 "$QUI/scripts/preinstall" "$QUI/scripts/postinstall"
pkgbuild --root "$STAGE" \
    --identifier it.etichette.pkg \
    --version "$VERSIONE" \
    --install-location / \
    --ownership recommended \
    --scripts "$QUI/scripts" \
    "$COMPONENTE"
productbuild --package "$COMPONENTE" "$FINALE"
rm -f "$COMPONENTE"

echo ""
echo "=============================================="
echo " BUILD COMPLETATA"
echo "=============================================="
echo "  Installer : $FINALE"
echo "  Dimensione: $(du -h "$FINALE" | cut -f1)"
echo ""
echo "  Installazione: doppio clic (clic destro > Apri, il pacchetto non e' firmato)"
echo "  oppure:        sudo installer -pkg \"$FINALE\" -target /"
