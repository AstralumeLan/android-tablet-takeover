#!/bin/bash
# usage: overlay.sh show|hide|dryrun   (dryrun = test the exit chain without disabling adb; result shown on bar + /sdcard/AI/takeover/exit.log)
D="$(cd "$(dirname "$0")" && pwd)"; if [ -f "$D/config.sh" ]; then . "$D/config.sh"; else . "$D/config.example.sh"; fi
A="adb -s $TABLET_ADB"
case "$1" in
  show) "$D/status.sh" "${2:-AI 正在接管此设备}";;
  hide) $A shell am broadcast -a com.astra.takeover.HIDE >/dev/null;;
  dryrun) $A shell am broadcast -a com.astra.takeover.DRYRUN >/dev/null;;
  stop) $A shell am stopservice -n com.astra.takeover/.TakeoverService >/dev/null;;
  *) echo "usage: $0 show [text]|hide|dryrun|stop"; exit 1;;
esac
