#!/bin/bash
# Re-lock remotely. lock.sh        -> show the overlay lock now (screen stays on)
#                   lock.sh --off  -> lock and turn the screen off
D="$(cd "$(dirname "$0")" && pwd)"; if [ -f "$D/config.sh" ]; then . "$D/config.sh"; else . "$D/config.example.sh"; fi
A="adb -s $TABLET_ADB"
s=$($A shell "su -c 'am broadcast -n com.astra.takeover/.Ctl -a com.astra.takeover.LOCK'" | grep -o 'data=.*'); echo "$s"
[ "$1" = "--off" ] && $A shell input keyevent KEYCODE_SLEEP
for i in 1 2 3 4 5 6; do
  s=$($A shell "su -c 'am broadcast -n com.astra.takeover/.Ctl -a com.astra.takeover.STATE'" | grep -o 'data=.*')
  case "$s" in *"locked=true"*) echo "locked: $s"; exit 0;; esac; sleep 0.5
done
echo "LOCK FAILED: $s" >&2; exit 1
