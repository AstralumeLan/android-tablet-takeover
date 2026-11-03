#!/system/bin/sh
# Tablet takeover: keep Tailscale / takeover bar / Operit reachable when the screen locks.
# ZTE CpuFreezerManagerServiceV2 freezes app uids (cgroup v1 /dev/cpufreezer/uid_X) on screen-off.
# 1) re-add Doze whitelist (not persistent across reboots by itself)
# 2) lightweight watcher: if cgroup.freeze of these uids becomes 1, write 0 back.
# Undo: cmd wifi force-hi-perf-mode disabled; rm /data/adb/service.d/96-takeover-keepalive.sh; kill $(cat /data/adb/takeover-keepalive.pid)
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
# 3) Wi-Fi: force hi-perf mode = driver power save off (not persistent; reapplied every ~10 min)
#    undo: cmd wifi force-hi-perf-mode disabled
cmd wifi force-hi-perf-mode enabled >/dev/null 2>&1
BOX=""   # optional: tailnet IP of the controlling host (e.g. 100.x.y.z); 1 ping / ~24 s keeps the DERP/NAT path warm. Empty = off
if [ -f $PIDF ] && kill -0 "$(cat $PIDF)" 2>/dev/null; then exit 0; fi
echo $$ > $PIDF
echo "$(date '+%F %T') keepalive watcher started pid $$" >> $LOG
ts_up(){ if [ -n "$TSIP" ]; then ip -4 addr show 2>/dev/null | grep -q " $TSIP/"; else ip -4 addr show 2>/dev/null | grep -qE ' inet 100\.(6[4-9]|[7-9][0-9]|1[01][0-9]|12[0-7])\.'; fi; }
n=0
t=0
tsn=0
TSIP=""   # optional: this tablet's tailnet IPv4; empty = treat any 100.64.0.0/10 address as "Tailscale up"
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
  t=$((t+1))
  if [ -n "$BOX" ] && [ $((t % 12)) -eq 0 ]; then (ping -c1 -W3 $BOX >/dev/null 2>&1 &); fi
  if [ $((t % 300)) -eq 0 ]; then cmd wifi force-hi-perf-mode enabled >/dev/null 2>&1; fi
  # 4) Operit watchdog (~every 2 min): its HTTP API (port 8094 = 0x1F9E) lives in AIForegroundService;
  #    if the port is gone, restart that service (it had silently died once on 10-30).
  if [ $((t % 60)) -eq 30 ] && ! grep -q ":1F9E 00000000000000000000000000000000:0000 0A" /proc/net/tcp6 2>/dev/null; then
    am start-foreground-service -n com.ai.assistance.operit/.api.chat.AIForegroundService >/dev/null 2>&1
    echo "$(date '+%F %T') operit API down -> restarted AIForegroundService" >> $LOG
  fi
  # 5) overlay-lock safety net (~every 10 s): the lock disables shade/home/recents via `cmd statusbar send-disable-flag`;
  #    that record lives in system_server, so if the takeover app dies without cleanup, clear it here.
  if [ $((t % 5)) -eq 0 ] && [ -z "$(pidof com.astra.takeover)" ] && dumpsys statusbar 2>/dev/null | grep -q StatusBarShellCommandToken; then
    cmd statusbar send-disable-flag none >/dev/null 2>&1
    echo "$(date '+%F %T') takeover app not running -> cleared statusbar disable flags" >> $LOG
  fi
  # 6) Tailscale VPN watchdog (~every 10 s). Root cause of the 10-30/31 offline drops: ZTE MindSync
  #    LockBgCleanupPolicy force-stops com.tailscale.ipn ~3.5 min after screen-off ("bgc_lock"), which tears the VPN down.
  #    If the tailnet address is gone from every interface, reconnect via Tailscale's own IPNReceiver (VPN consent already granted).
  if [ $((t % 5)) -eq 2 ] && ! ts_up; then
    am broadcast --include-stopped-packages -n com.tailscale.ipn/.IPNReceiver -a com.tailscale.ipn.CONNECT_VPN >/dev/null 2>&1
    tsn=$((tsn+1)); echo "$(date '+%F %T') tailscale VPN down -> CONNECT_VPN (#$tsn)" >> $LOG
    # second try after ~20 s without VPN: bring the app up first (needed if its process was force-stopped)
    if [ $((tsn % 2)) -eq 0 ]; then am start-foreground-service -n com.tailscale.ipn/.IPNService -a com.tailscale.ipn.CONNECT_VPN >/dev/null 2>&1; fi
  fi
  sleep 2
done
