#!/bin/bash
D="$(cd "$(dirname "$0")" && pwd)"; if [ -f "$D/config.sh" ]; then . "$D/config.sh"; else . "$D/config.example.sh"; fi
A="adb -s $TABLET_ADB"
$A shell "su -c 'am broadcast -n com.astra.takeover/.Ctl -a com.astra.takeover.STATE'" | grep -o 'data=.*'
