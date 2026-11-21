#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
BT="$SDK/build-tools/37.0.0"
JAR="$SDK/platforms/android-37/android.jar"
SRC="$ROOT/app/src/main"
OUT="$ROOT/build"
KEYSTORE="$ROOT/quietmode.keystore"
APK="$ROOT/QuietMode.apk"

for tool in "$BT/aapt2" "$BT/d8" "$BT/zipalign" "$BT/apksigner" "$JAR"; do
  if [ ! -e "$tool" ]; then
    echo "Missing: $tool" >&2
    exit 1
  fi
done

rm -rf "$OUT"
mkdir -p "$OUT/res" "$OUT/gen" "$OUT/classes" "$OUT/dex"

"$BT/aapt2" compile --dir "$SRC/res" -o "$OUT/res.zip"
"$BT/aapt2" link \
  -o "$OUT/unaligned.apk" \
  -I "$JAR" \
  --manifest "$SRC/AndroidManifest.xml" \
  --java "$OUT/gen" \
  --min-sdk-version 29 \
  --target-sdk-version 37 \
  "$OUT/res.zip"

find "$SRC/java" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -source 8 -target 8 -Xlint:-options \
  -classpath "$JAR" \
  -d "$OUT/classes" \
  @"$OUT/sources.txt"

find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
"$BT/d8" --lib "$JAR" --min-api 29 --output "$OUT/dex" @"$OUT/classes.txt"

cp "$OUT/unaligned.apk" "$OUT/unsigned.apk"
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
