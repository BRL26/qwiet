#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
BT="$SDK/build-tools/37.0.0"
JAR="$SDK/platforms/android-37/android.jar"
SRC="$ROOT/xposed-app/src/main"
STUBS="$ROOT/xposed-stubs"
OUT="$ROOT/xposed-build"
KEYSTORE="$ROOT/quietmode.keystore"
APK="$ROOT/QuietPanelHook.apk"

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/dex"

find "$SRC/java" "$STUBS" -name '*.java' > "$OUT/sources.txt"
javac -source 8 -target 8 -Xlint:-options \
  -classpath "$JAR" \
  -d "$OUT/classes" \
  @"$OUT/sources.txt"

find "$OUT/classes/com/brl" -name '*.class' > "$OUT/classes.txt"
"$BT/d8" --lib "$JAR" --min-api 29 --output "$OUT/dex" @"$OUT/classes.txt"

"$BT/aapt2" link \
  -o "$OUT/unsigned.apk" \
  -I "$JAR" \
  --manifest "$SRC/AndroidManifest.xml" \
  -A "$SRC/assets" \
  --min-sdk-version 29 \
  --target-sdk-version 37

python3 - "$OUT/unsigned.apk" "$OUT/dex/classes.dex" <<'PY'
import sys, zipfile
apk, dex = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(apk, "a", zipfile.ZIP_DEFLATED) as z:
    z.write(dex, "classes.dex")
PY
"$BT/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -keystore "$KEYSTORE" -alias quietmode \
    -storepass android -keypass android -keyalg RSA -keysize 2048 \
    -validity 10000 -dname "CN=Quiet Mode, O=Local, C=US" -noprompt
fi

"$BT/apksigner" sign --ks "$KEYSTORE" --ks-key-alias quietmode \
  --ks-pass pass:android --key-pass pass:android \
  --out "$APK" "$OUT/aligned.apk"
"$BT/apksigner" verify --print-certs "$APK"
echo "Built: $APK"
