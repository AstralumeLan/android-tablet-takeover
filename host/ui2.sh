#!/bin/bash
# Fast UI tree via the takeover app's AccessibilityService (root-only broadcast; no idle wait, all windows incl. split-screen/dialogs).
# ui2.sh dump [active|raw]    print compact tree (all windows by default; raw = every visible node)
# ui2.sh find "文字" [n]       print matching lines (text/desc/#id substring) with bounds + center
# ui2.sh tap  "文字" [n]       input tap at center of the n-th match (default 0)
# ui2.sh click "文字" [n]      ACTION_CLICK via the service (nearest clickable ancestor); refused while locked
# ui2.sh clickid viewId [n]   ACTION_CLICK by resource id
# ui2.sh scroll [down|up] [x] [y1] [y2]  swipe a list (default down = content moves up, x=1440 right pane; x=480 left pane), waits 0.8s
#   since v2.4 (2026-10-31) every DUMP drops the accessibility cache, so a dump after scrolling shows fresh content
# Tree line: path Class "text" d="desc" #id [l,t,r,b] flags(C=clickable L=long CHK+/- SEL E=editable SCR DIS)
D="$(cd "$(dirname "$0")" && pwd)"; if [ -f "$D/config.sh" ]; then . "$D/config.sh"; else . "$D/config.example.sh"; fi
A="adb -s $TABLET_ADB"
CACHE=/tmp/ui2.last
bc(){ $A exec-out "su -c 'am broadcast -n com.astra.takeover/.Ctl -a com.astra.takeover.$1 $2'" | sed -n '/data="/,$p' | sed '1s/^.*data="//; $s/"$//'; }
dump(){ case "$1" in active) bc DUMP "--es mode active";; raw) bc DUMP "--ez raw true";; *) bc DUMP;; esac > $CACHE; }
center(){ sed -n 's/.*\[\(-\{0,1\}[0-9]*\),\(-\{0,1\}[0-9]*\),\(-\{0,1\}[0-9]*\),\(-\{0,1\}[0-9]*\)\].*/\1 \2 \3 \4/p' | awk '{printf "%d %d\n", ($1+$3)/2, ($2+$4)/2}'; }
case "$1" in
  dump) dump "$2"; cat $CACHE;;
  find) dump; grep -aF -- "$2" $CACHE | grep -v '^W' | while read -r l; do echo "$l  => center $(echo "$l" | center)"; done;;
  tap)  dump; n=${3:-0}
        # prefer exact text/desc matches ("文字" or d="文字"), fall back to substring
        l=$(grep -aF -e "\"$2\"" $CACHE | grep -v '^W' | sed -n "$((n+1))p"); [ -n "$l" ] || l=$(grep -aF -- "$2" $CACHE | grep -v '^W' | sed -n "$((n+1))p")
        [ -n "$l" ] || { echo "not found: $2" >&2; exit 1; }
        xy=$(echo "$l" | center); $A shell input tap $xy; echo "tap $xy  <- $l";;
  scroll) d=${2:-down}; x=${3:-1440}; a=${4:-900}; b=${5:-350}; [ "$d" = up ] && { t=$a; a=$b; b=$t; }
        $A shell "input touchscreen swipe $x $a $x $b 400; sleep 0.8"; echo "scroll $d x=$x $a->$b";;
  click) bc CLICK "--es text \"$2\" --ei n ${3:-0}";;
  clickid) bc CLICK "--es id \"$2\" --ei n ${3:-0}";;
  *) sed -n '2,10p' "$0"; exit 1;;
esac
