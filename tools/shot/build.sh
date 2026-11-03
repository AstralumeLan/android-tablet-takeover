#!/bin/bash
# Build shot.jar: tiny app_process tool that scales a PNG screenshot to a small JPEG on the device.
#   adb push shot.jar /data/local/tmp/ ; adb shell 'screencap -p /data/local/tmp/s.png && \
#     app_process -Djava.class.path=/data/local/tmp/shot.jar /system/bin Shot /data/local/tmp/s.png /sdcard/out.jpg 0.5 60'
set -e; cd "$(dirname "$0")"
[ -n "$JAVA_HOME" ] && export PATH="$JAVA_HOME/bin:$PATH"
: "${BUILD_TOOLS:?}"; : "${ANDROID_JAR:?}"
rm -rf out && mkdir -p out/cls
javac -source 8 -target 8 -cp "$ANDROID_JAR" -d out/cls src/Shot.java
"$BUILD_TOOLS/d8" --min-api 26 --lib "$ANDROID_JAR" --output out out/cls/Shot.class
python3 -c "import zipfile;z=zipfile.ZipFile('shot.jar','w');z.write('out/classes.dex','classes.dex');z.close()"; ls -la shot.jar
