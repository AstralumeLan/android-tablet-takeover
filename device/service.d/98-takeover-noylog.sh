#!/system/bin/sh
# Tablet takeover: keep Unisoc ylog (system log collector) off after boot.
# Undo: rm /data/adb/service.d/98-takeover-noylog.sh ; start ylog
until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 5; done
sleep 30
stop ylog
echo "$(date '+%F %T') ylog stopped ($(getprop init.svc.ylog))" >> /data/adb/takeover-boot.log
