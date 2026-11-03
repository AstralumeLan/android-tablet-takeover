#!/bin/bash
D="$(cd "$(dirname "$0")" && pwd)"; if [ -f "$D/config.sh" ]; then . "$D/config.sh"; else . "$D/config.example.sh"; fi
A="adb -s $TABLET_ADB"
# wake the tablet screen (no lock screen set, so this goes straight to the desktop)
$A shell 'input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard; dumpsys power | grep -m1 mWakefulness='
