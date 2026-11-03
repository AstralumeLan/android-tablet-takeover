# Shared build settings. Override via environment.
#   JAVA_HOME      JDK 17 (javac + keytool on PATH also works)
#   BUILD_TOOLS    Android build-tools dir containing aapt2, d8, zipalign, apksigner (e.g. $ANDROID_HOME/build-tools/34.0.0)
#   ANDROID_JAR    android.jar of platform 33+ (e.g. $ANDROID_HOME/platforms/android-34/android.jar)
#   KEYSTORE       signing keystore (created on first build if missing; git-ignored)
#   KS_PASS        keystore password (required; never commit it)
set -e
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
[ -n "$JAVA_HOME" ] && export PATH="$JAVA_HOME/bin:$PATH"
: "${BUILD_TOOLS:?set BUILD_TOOLS to an Android build-tools directory}"
: "${ANDROID_JAR:?set ANDROID_JAR to platforms/android-3x/android.jar}"
KEYSTORE="${KEYSTORE:-$ROOT/signing/release.jks}"
: "${KS_PASS:?set KS_PASS (keystore password)}"
if [ ! -f "$KEYSTORE" ]; then
  mkdir -p "$(dirname "$KEYSTORE")"
  keytool -genkeypair -keystore "$KEYSTORE" -storepass "$KS_PASS" -keypass "$KS_PASS" -alias takeover \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=tablet-takeover" >/dev/null
  echo "created new keystore $KEYSTORE"
fi
# build one apk: $1=module dir, $2=output apk name, $3=extra javac classpath (optional), $4=extra aapt2 link args
build_apk(){
  local M="$1" OUT="$2" CP="$3" LINKX="$4"
  cd "$M"; rm -rf out && mkdir -p out/cls
  "$BUILD_TOOLS/aapt2" compile --dir res -o out/res.zip
  "$BUILD_TOOLS/aapt2" link -o out/u.apk --manifest AndroidManifest.xml -I "$ANDROID_JAR" out/res.zip $LINKX
  javac -source 8 -target 8 -nowarn -cp "$ANDROID_JAR${CP:+:$CP}" -d out/cls $(find src -name '*.java') 2>&1 | grep -v -i warning || true
  "$BUILD_TOOLS/d8" --min-api 26 --lib "$ANDROID_JAR" ${CP:+--classpath $CP} --output out $(find out/cls -name '*.class')
  python3 -c "import zipfile;z=zipfile.ZipFile('out/u.apk','a');z.write('out/classes.dex','classes.dex');z.close()"
  "$BUILD_TOOLS/zipalign" -f 4 out/u.apk out/a.apk
  "$BUILD_TOOLS/apksigner" sign --ks "$KEYSTORE" --ks-pass env:KS_PASS --out "$OUT" out/a.apk
  ls -la "$OUT"
}
export KS_PASS
