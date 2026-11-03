#!/system/bin/sh
# Tablet takeover: keep Tailscale / takeover bar / Operit reachable when the screen locks.
# ZTE CpuFreezerManagerServiceV2 freezes app uids (cgroup v1 /dev/cpufreezer/uid_X) on screen-off.
# 1) re-add Doze whitelist (not persistent across reboots by itself)
# 2) lightweight watcher: if cgroup.freeze of these uids becomes 1, write 0 back.
# Undo: rm /data/adb/service.d/96-takeover-keepalive.sh; kill $(cat /data/adb/takeover-keepalive.pid)
# Skipped if /data/adb/takeover.disabled exists (user pressed 退出接管).
LOG=/data/adb/takeover-boot.log
PIDF=/data/adb/takeover-keepalive.pid
until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 5; done
sleep 15
PKGS="com.tailscale.ipn com.astra.takeover com.ai.assistance.operit"   # apps that must never be frozen
# app uids differ per device: resolve them from the data dirs (fallback: dumpsys package <pkg> | grep userId=)
UIDS=""; for p in $PKGS; do u=$(stat -c %u /data/data/$p 2>/dev/null); [ -n "$u" ] && UIDS="$UIDS $u"; done
[ -f /data/adb/takeover.disabled ] && { echo "$(date '+%F %T') keepalive skipped (takeover disabled)" >> $LOG; exit 0; }
for p in $PKGS; do dumpsys deviceidle whitelist +$p >/dev/null 2>&1; cmd appops set $p RUN_ANY_IN_BACKGROUND allow >/dev/null 2>&1; done
am set-standby-bucket com.tailscale.ipn active >/dev/null 2>&1
if [ -f $PIDF ] && kill -0 "$(cat $PIDF)" 2>/dev/null; then exit 0; fi
echo $$ > $PIDF
echo "$(date '+%F %T') keepalive watcher started pid $$" >> $LOG
n=0
while true; do
  [ -f /data/adb/takeover.disabled ] && { echo "$(date '+%F %T') keepalive stopped (takeover disabled)" >> $LOG; rm -f $PIDF; exit 0; }
  for u in $UIDS; do
    # ZTE CpuFreezer uses the cgroup v1 freezer mounted at /dev/cpufreezer
    f=/dev/cpufreezer/uid_$u/freezer.state
    if [ -f "$f" ]; then
      read v < "$f"
      if [ "$v" != "THAWED" ]; then
        echo THAWED > "$f"
        n=$((n+1))
        [ $n -le 300 ] && echo "$(date '+%F %T') thawed uid_$u (was $v)" >> $LOG
      fi
    fi
    # cgroup v2 (AOSP freezer) just in case
    for f in /sys/fs/cgroup/uid_$u/pid_*/cgroup.freeze; do
      [ -f "$f" ] || continue
      read v < "$f"
      [ "$v" = "1" ] && echo 0 > "$f"
    done
  done
  sleep 2
done
