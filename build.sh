#!/usr/bin/env bash
# Construit budgeat.apk sans Gradle, directement avec les outils du SDK Android.
# Prérequis : JDK 17 (dans le PATH) et un SDK Android avec build-tools + une plateforme.
set -euo pipefail

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/c/Android}}"
# Versions auto-détectées : les runners CI n'ont pas les mêmes que ce poste.
# Uniquement du stable : les runners embarquent des préversions (android-37.2-beta2,
# build-tools rc) qu'un simple « sort -V | tail -1 » sélectionnerait, produisant un
# APK compilé contre un SDK marqué DEV.
BT_VER="${BT_VER:-$(ls "$SDK/build-tools" 2>/dev/null | grep -E '^[0-9]+(\.[0-9]+)*$' | sort -V | tail -1)}"
PLATFORM="${PLATFORM:-$(ls "$SDK/platforms" 2>/dev/null | grep -E '^android-[0-9]+$' | sort -V | tail -1)}"

[ -n "$BT_VER" ]   || { echo "aucune build-tools stable dans $SDK/build-tools"; exit 1; }
[ -n "$PLATFORM" ] || { echo "aucune plateforme stable dans $SDK/platforms"; exit 1; }

BT="$SDK/build-tools/$BT_VER"
JAR="$SDK/platforms/$PLATFORM/android.jar"
OUT="build"

MIN_SDK=26
TARGET_SDK=34
VERSION_CODE="${VERSION_CODE:-1}"
VERSION_NAME="${VERSION_NAME:-1.0}"

# Signature : surchargeable pour que le CI injecte la vraie clé.
KS="${KEYSTORE_FILE:-luma.keystore}"
KS_PASS="${KEYSTORE_PASSWORD:-budgeat}"
KS_ALIAS="${KEY_ALIAS:-budgeat}"
KEY_PASS="${KEY_PASSWORD:-$KS_PASS}"

echo "SDK=$SDK  build-tools=$BT_VER  platform=$PLATFORM"

[ -f "$JAR" ] || { echo "android.jar introuvable : $JAR"; exit 1; }
[ -x "$BT/aapt2.exe" ] || [ -x "$BT/aapt2" ] || { echo "build-tools introuvables : $BT"; exit 1; }

AAPT2="$BT/aapt2.exe"; [ -x "$AAPT2" ] || AAPT2="$BT/aapt2"
AAPT="$BT/aapt.exe";   [ -x "$AAPT" ]  || AAPT="$BT/aapt"
D8="$BT/d8.bat";       [ -f "$D8" ]    || D8="$BT/d8"
ZIPALIGN="$BT/zipalign.exe"; [ -x "$ZIPALIGN" ] || ZIPALIGN="$BT/zipalign"
APKSIGNER="$BT/apksigner.bat"; [ -f "$APKSIGNER" ] || APKSIGNER="$BT/apksigner"

echo "==> Régénération de l'asset HTML"
node pack-asset.mjs

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/obj"

echo "==> 1/6 aapt2 compile (ressources)"
"$AAPT2" compile --dir res -o "$OUT/res.zip"

echo "==> 2/6 aapt2 link (manifeste + assets)"
"$AAPT2" link \
  -o "$OUT/app.unsigned.apk" \
  -I "$JAR" \
  --manifest AndroidManifest.xml \
  -A assets \
  --java "$OUT/gen" \
  --min-sdk-version "$MIN_SDK" \
  --target-sdk-version "$TARGET_SDK" \
  --version-code "$VERSION_CODE" \
  --version-name "$VERSION_NAME" \
  "$OUT/res.zip"

echo "==> 3/6 javac"
find java "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac --release 8 -nowarn -classpath "$JAR" -d "$OUT/obj" "@$OUT/sources.txt"

echo "==> 4/6 d8 (dex)"
find "$OUT/obj" -name '*.class' > "$OUT/classes.txt"
"$D8" --min-api "$MIN_SDK" --lib "$JAR" --output "$OUT" "@$OUT/classes.txt"

echo "==> 5/6 intégration du dex + alignement"
( cd "$OUT" && "$AAPT" add -f app.unsigned.apk classes.dex >/dev/null )
"$ZIPALIGN" -f 4 "$OUT/app.unsigned.apk" "$OUT/app.aligned.apk"

echo "==> 6/6 signature"
if [ ! -f "$KS" ]; then
  echo "    (aucun keystore : création d'une clé locale — APK non redistribuable)"
  keytool -genkeypair -keystore "$KS" -alias "$KS_ALIAS" \
    -storepass "$KS_PASS" -keypass "$KEY_PASS" \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Luma, O=Perso, C=FR" >/dev/null 2>&1
fi
"$APKSIGNER" sign \
  --ks "$KS" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KEY_PASS" \
  --ks-key-alias "$KS_ALIAS" \
  --out luma.apk "$OUT/app.aligned.apk"

"$APKSIGNER" verify --print-certs luma.apk | head -4
ls -la luma.apk
echo "==> luma.apk prêt"
