#!/system/bin/sh
# Tablet takeover: re-enable wireless ADB (tcp 5555) after boot.
# Skipped if user pressed "退出接管" (flag /data/adb/takeover.disabled exists).
# Remove this file to undo: rm /data/adb/service.d/99-takeover-adb.sh
until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 5; done
sleep 20
LOG=/data/adb/takeover-boot.log
if [ -f /data/adb/takeover.disabled ]; then
  echo "$(date '+%F %T') skipped (takeover disabled by user)" >> $LOG; exit 0
fi
setprop service.adb.tcp.port 5555
stop adbd; start adbd
echo "$(date '+%F %T') wireless adb enabled on 5555" >> $LOG
