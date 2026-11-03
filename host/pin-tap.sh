#!/bin/bash
# Touch-test the PIN pad: pin-tap.sh            -> taps the real PIN from $PIN_FILE (digits never printed)
#                         pin-tap.sh --dummy D  -> taps a dummy wrong PIN D
# then taps 确定. Coordinates come from the app's own "keys" log line (current orientation).
D="$(cd "$(dirname "$0")" && pwd)"; if [ -f "$D/config.sh" ]; then . "$D/config.sh"; else . "$D/config.example.sh"; fi
A="adb -s $TABLET_ADB"
keys=$($A shell "logcat -d -s TOLock | grep ' keys ' | tail -1")
[ -n "$keys" ] || { echo "no key map in logcat" >&2; exit 1; }
if [ "$1" = "--dummy" ]; then P="$2"; else P=$(tr -dc 0-9 < "$PIN_FILE"); fi
cmd=""
for ((i=0;i<${#P};i++)); do d=${P:i:1}; xy=$(echo "$keys" | grep -o " $d=[0-9]*,[0-9]*" | cut -d= -f2 | tr , ' '); cmd="$cmd input tap $xy;"; done
ok=$(echo "$keys" | grep -o " OK=[0-9]*,[0-9]*" | cut -d= -f2 | tr , ' ')
$A shell "$cmd input tap $ok" ; unset P cmd
echo "tapped (${2:+dummy}) + OK"
