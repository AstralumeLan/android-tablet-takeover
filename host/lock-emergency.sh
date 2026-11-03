#!/bin/bash
# Emergency: remove the overlay lock completely (no PIN needed). Kills the app, re-enables the notification shade.
# Add "--clear" to also wipe the stored PIN hash (lock stays disabled even after reboot until set-pin.sh is run again).
D="$(cd "$(dirname "$0")" && pwd)"; if [ -f "$D/config.sh" ]; then . "$D/config.sh"; else . "$D/config.example.sh"; fi
A="adb -s $TABLET_ADB"
[ "$1" = "--clear" ] && $A shell "su -c 'am broadcast -n com.astra.takeover/.Ctl -a com.astra.takeover.CLEARPIN'" | grep -o 'data=.*'
$A shell "su -c 'am force-stop com.astra.takeover; cmd statusbar send-disable-flag none'"; echo "takeover app stopped, overlay gone"
