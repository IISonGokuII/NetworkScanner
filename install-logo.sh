#!/bin/bash
# Djoudinis Sleeptimer - Logo installieren
#
# Verwendung:
#   ./install-logo.sh /pfad/zu/deinem/logo.png
#
# Das Script erstellt alle noetigen Icon-Groessen aus deinem Logo
# und kopiert sie in die richtigen Android-Ressourcen-Ordner.

if [ -z "$1" ]; then
    echo "Verwendung: $0 <logo.png>"
    echo "Beispiel:   $0 ~/Downloads/djoudinis-logo.png"
    exit 1
fi

SOURCE="$1"
if [ ! -f "$SOURCE" ]; then
    echo "Fehler: Datei '$SOURCE' nicht gefunden!"
    exit 1
fi

BASE_DIR="$(dirname "$0")/app/src/main/res"

# Erstelle mipmap-Ordner und skaliere das Logo
declare -A SIZES=(
    ["mipmap-mdpi"]=48
    ["mipmap-hdpi"]=72
    ["mipmap-xhdpi"]=96
    ["mipmap-xxhdpi"]=144
    ["mipmap-xxxhdpi"]=192
)

# Pruefe ob ImageMagick oder ffmpeg verfuegbar ist
if command -v convert &> /dev/null; then
    for dir in "${!SIZES[@]}"; do
        size=${SIZES[$dir]}
        mkdir -p "$BASE_DIR/$dir"
        convert "$SOURCE" -resize "${size}x${size}" "$BASE_DIR/$dir/ic_launcher.png"
        echo "Erstellt: $dir/ic_launcher.png (${size}x${size})"
    done
elif command -v ffmpeg &> /dev/null; then
    for dir in "${!SIZES[@]}"; do
        size=${SIZES[$dir]}
        mkdir -p "$BASE_DIR/$dir"
        ffmpeg -i "$SOURCE" -vf "scale=${size}:${size}" -y "$BASE_DIR/$dir/ic_launcher.png" 2>/dev/null
        echo "Erstellt: $dir/ic_launcher.png (${size}x${size})"
    done
else
    # Fallback: Einfach kopieren ohne Skalierung
    for dir in "${!SIZES[@]}"; do
        mkdir -p "$BASE_DIR/$dir"
        cp "$SOURCE" "$BASE_DIR/$dir/ic_launcher.png"
        echo "Kopiert: $dir/ic_launcher.png (nicht skaliert - installiere ImageMagick fuer korrekte Groessen)"
    done
fi

# Fire TV Banner (320x180)
mkdir -p "$BASE_DIR/drawable-xhdpi"
if command -v convert &> /dev/null; then
    convert "$SOURCE" -resize "320x180" -background '#1A1A3E' -gravity center -extent 320x180 "$BASE_DIR/drawable-xhdpi/app_banner.png"
    echo "Erstellt: drawable-xhdpi/app_banner.png (320x180)"
fi

echo ""
echo "Fertig! Aendere in AndroidManifest.xml:"
echo '  android:icon="@mipmap/ic_launcher"'
echo ""
echo "Dann neu bauen!"
