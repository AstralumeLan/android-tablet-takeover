# Lessons: keeping apps alive on a ZTE (MyOS / Android 13, Unisoc) tablet

Target: keep **Tailscale**, the **takeover app** and **Operit** running while the screen is off, so the
tablet stays reachable over the tailnet. Stock Android knobs (Doze whitelist, standby bucket,
`RUN_ANY_IN_BACKGROUND`) are necessary but **not sufficient** on ZTE firmware. Three vendor mechanisms bite:

## 1. CpuFreezer (cgroup v1 freezer) — apps frozen ~30–40 s after screen-off
- `CpuFreezerManagerServiceV2` writes `FROZEN` to `/dev/cpufreezer/uid_<uid>/freezer.state`.
- Its whitelist comes from the vendor "strategy" provider
  (`content://com.zte.heartyservice.strategy.provider/app_settings`, columns `locked`, `bg_run_mode`,
  `self_start_mode`, `related_start_mode`) and is only (re)loaded **at boot** or when a *system app* asks;
  calls from `shell` are rejected (`enforceCallIsSystemApp`).
- Fixes used, in layers:
  1. Set `locked=1 bg_run_mode=3 self_start_mode=3 related_start_mode=3` for the apps via `content update` (persistent, effective after reboot).
  2. `device/service.d/96-takeover-keepalive.sh`: a 2-second shell loop (builtin `read`, ~0 CPU) that writes `THAWED` back.
  3. LSPosed module `lsp-keepalive`: hooks `doSetUidFreezeLocked` / `doSetUidFreezer` in `system_server` and returns `false` for the protected app IDs.

## 2. MindSync "lock background cleanup" — force-stop ~3.5 min after screen-off
- Log signature: `ZTEPoint_FROCE_STOP_PACKAGE: com.tailscale.ipn reason: bgc_lock`.
- Every vendor cleanup path ends in `ActivityManagerService.zteEx_forceStopPackage`. Force-stopping Tailscale tears down the VPN → device offline.
- The in-memory "do not kill" list is read from the strategy DB **only at boot**, so changing the DB without a reboot does nothing.
- Fixes: LSPosed hook on `zteEx_forceStopPackage` (skip for protected packages) + a watchdog in the keepalive script
  that re-connects through Tailscale's own receiver:
  `am broadcast --include-stopped-packages -n com.tailscale.ipn/.IPNReceiver -a com.tailscale.ipn.CONNECT_VPN`
  (requires VPN consent granted once). Measured recovery after a manual force-stop: ~17 s.

## 3. Wi-Fi power save / DERP stalls
- With the screen off, the Wi-Fi driver enters suspend/power-save and the DERP relay TCP connection could stall (`derp.Recv i/o timeout`).
- Fix: `cmd wifi force-hi-perf-mode enabled` (not persistent → re-applied every ~10 min) plus an optional tiny ping to the
  controlling host every ~24 s to keep the NAT/DERP path warm.

## Other notes
- Operit's HTTP API lives in `AIForegroundService`; it occasionally died while the process survived. The keepalive
  checks the listening port in `/proc/net/tcp6` every ~2 min and restarts the service.
- Unisoc `ylog` (system log collector) burns noticeable CPU and has no persistent switch; `98-takeover-noylog.sh` stops it after boot.
- ZTE prompts on-screen for every `adb install` ("external source risk"). `settings put system monitor_app_install_by_adb_data 0` disables that.
- **Never `rmmod` / reload kernel modules on this device** — unloading the keyboard-cover driver caused a kernel panic and reboot.
- Don't put a VPN-type proxy app into always-on mode next to Tailscale; Android allows one VPN at a time.
- Verification method that worked: screen off for 7–13 min while the host probes `adb` every 15–20 s; 0 failures after all three layers were in place, including across a reboot.
