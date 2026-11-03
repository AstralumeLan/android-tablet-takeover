#!/bin/bash
# Remote unlock of the takeover overlay lock (root-only broadcast; no PIN needed, no screen taps).
# Unlock lasts until the screen next turns off. Exit 0 = unlocked and verified.
D="$(cd "$(dirname "$0")" && pwd)"; if [ -f "$D/config.sh" ]; then . "$D/config.sh"; else . "$D/config.example.sh"; fi
A="adb -s $TABLET_ADB"
$A shell "input keyevent KEYCODE_WAKEUP; su -c 'am broadcast -n com.astra.takeover/.Ctl -a com.astra.takeover.UNLOCK' >/dev/null"
for i in 1 2 3 4 5 6; do
  s=$($A shell "su -c 'am broadcast -n com.astra.takeover/.Ctl -a com.astra.takeover.STATE'" | grep -o 'data=.*')
  case "$s" in *"locked=false"*) echo "unlocked: $s"; exit 0;; esac; sleep 0.5
done
echo "UNLOCK FAILED: $s" >&2; exit 1
