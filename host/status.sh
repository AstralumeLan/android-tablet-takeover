#!/bin/bash
# usage: status.sh "正在做的事"   -> updates the takeover bar on the tablet (shows it if hidden)
D="$(cd "$(dirname "$0")" && pwd)"; if [ -f "$D/config.sh" ]; then . "$D/config.sh"; else . "$D/config.example.sh"; fi
A="adb -s $TABLET_ADB"
t="${*:-AI 正在接管此设备}"; q=$(printf "%s" "$t" | sed "s/'/'\\\\''/g")
$A shell "r=\$(am broadcast -a com.astra.takeover.STATUS --es text '$q' 2>&1); case \"\$r\" in *'result=0'*|*'Broadcast completed'*) ;; esac; dumpsys activity services com.astra.takeover | grep -q ServiceRecord || am start -n com.astra.takeover/.MainActivity --ez bg true --es text '$q' >/dev/null" >/dev/null
