#!/bin/bash
# Build the LSPosed module com.astra.keepalive -> lsp-keepalive/astra-keepalive.apk
# The Xposed API is provided at runtime by LSPosed; stubs/ only exists so javac can compile (not packaged).
. "$(dirname "$0")/../build-common.sh"
M="$ROOT/lsp-keepalive"; rm -rf "$M/out-stubs" && mkdir -p "$M/out-stubs"
javac -source 8 -target 8 -nowarn -cp "$ANDROID_JAR" -d "$M/out-stubs" $(find "$M/stubs" -name '*.java')
build_apk "$M" "$M/astra-keepalive.apk" "$M/out-stubs" "-A assets"
