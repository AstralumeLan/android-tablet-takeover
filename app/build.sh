#!/bin/bash
# Build com.astra.takeover -> app/takeover.apk   (see build-common.sh for required env vars)
. "$(dirname "$0")/../build-common.sh"
build_apk "$ROOT/app" "$ROOT/app/takeover.apk"
