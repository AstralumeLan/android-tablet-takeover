# android-tablet-takeover

Remote, headless control of a **rooted Android tablet** over **Tailscale + wireless ADB**, built for an AI agent
(or any script) running on another machine. It gives the controller:

- a fast, text-based **UI tree reader** and clicker (Accessibility service, no screenshots, no `uiautomator` idle waits),
- a visible **"AI is in control" status bar** with a one-tap *exit takeover* button for the person holding the device,
- a custom **overlay PIN lock** (the system lock screen stays off so remote control keeps working),
- a **multi-layer keep-alive** that defeats aggressive vendor background killing (tested on a ZTE MyOS / Unisoc tablet),
- optional delegation of multi-step on-device tasks to the **Operit** agent app over its HTTP API.

> 中文简介：这是一套通过 Tailscale + 无线 ADB 远程接管已 root 安卓平板的工具。包含：基于无障碍服务的界面树读取与点击（不截图、很快）、
> 顶部"AI 接管中"状态条和一键退出接管、自绘覆盖层密码锁（只存 PBKDF2 哈希）、对付中兴系统冻结/锁屏清理的多层保活
> （开机脚本 + LSPosed 模块），以及可选的 Operit 本地代理委托脚本。所有地址、令牌、密码都通过本地配置文件提供，仓库里不含任何真实值。

## Architecture

```
 controller host (Linux)                        tablet (root, Magisk, LSPosed)
 ───────────────────────                        ─────────────────────────────────────────────
 host/*.sh ── adb over tailnet:5555 ──────────▶ adbd (re-enabled at boot by 99-takeover-adb.sh)
   ui2.sh dump/find/tap/click/scroll              └─ su -c am broadcast → com.astra.takeover/.Ctl
   lock.sh / unlock.sh / set-pin.sh                     ├─ DUMP / CLICK → .Ui (AccessibilityService)
   status.sh / overlay.sh                               ├─ LOCK / UNLOCK / SETPIN / STATE → Lock overlay
                                                        └─ .Cmd (STATUS/SHOW/HIDE) → status bar
 host/operit.sh ── HTTP :8094 (bearer) ───────▶ Operit AIForegroundService (optional)

 keep-alive layers on the tablet:
   service.d/96-takeover-keepalive.sh  thaw frozen uids · Doze whitelist · Wi-Fi hi-perf · Tailscale/Operit watchdogs
   lsp-keepalive (LSPosed, scope=system) block vendor force-stop + freezer for protected apps
   vendor strategy DB                    mark apps "locked / allow background" (read at boot)
```

| Path | What |
|---|---|
| `app/` | `com.astra.takeover` v2.4 — status bar service, overlay lock, root-only control receiver, accessibility UI reader |
| `lsp-keepalive/` | `com.astra.keepalive` LSPosed module hooking `system_server` (ZTE-specific method names) |
| `device/service.d/` | Magisk boot scripts: keep-alive watcher (v6), stop `ylog`, re-enable wireless adb |
| `examples/` | the first, minimal keep-alive (v1: Doze whitelist + thaw loop only) |
| `host/` | controller scripts (bash, need `adb`, `python3`) + `config.example.sh` |
| `tools/shot/` | `app_process` helper that turns a screencap PNG into a small JPEG on-device |
| `operit/` | example Operit skill for delegated tasks |
| `docs/` | overlay lock design, ZTE background-killing lessons, Operit notes |

## Requirements

- Android 8+ (tested on Android 13), **rooted with Magisk** (boot scripts in `/data/adb/service.d/`).
- **LSPosed** (Zygisk) for the keep-alive module — only needed on firmware that force-stops/freezes background apps.
- **Tailscale** on the tablet and the controller host (same tailnet). VPN consent granted once on the tablet.
- Host: `adb`, `bash`, `python3`. Build: JDK 17, Android build-tools (aapt2, d8, zipalign, apksigner) and a platform `android.jar` (API 33+; `clearCache()` needs 33).
- Optional: Operit app with its external API enabled.

## Setup

1. **Build** (no Gradle needed):
   ```bash
   export JAVA_HOME=/path/to/jdk17 BUILD_TOOLS=$ANDROID_HOME/build-tools/34.0.0 \
          ANDROID_JAR=$ANDROID_HOME/platforms/android-34/android.jar KS_PASS='<choose-a-password>'
   ./app/build.sh            # -> app/takeover.apk  (creates signing/release.jks on first run; git-ignored)
   ./lsp-keepalive/build.sh  # -> lsp-keepalive/astra-keepalive.apk
   ./tools/shot/build.sh     # optional -> tools/shot/shot.jar
   ```
2. **Install & grant** (over USB adb first):
   ```bash
   adb install -r app/takeover.apk
   adb shell appops set com.astra.takeover SYSTEM_ALERT_WINDOW allow
   adb shell su -c 'settings put secure enabled_accessibility_services com.astra.takeover/com.astra.takeover.Ui; settings put secure accessibility_enabled 1'
   ```
   Grant root to `com.astra.takeover` in Magisk (it needs `su` for statusbar flags and for *exit takeover*).
3. **Boot scripts**: review the variables at the top, then
   `adb push device/service.d/*.sh /data/local/tmp/` and as root copy them to `/data/adb/service.d/` with mode 755.
   In `96-takeover-keepalive.sh` optionally set `TSIP` (tablet tailnet IP) and `BOX` (controller tailnet IP).
4. **LSPosed module** (if needed): set your app IDs in `Hook.java` (`dumpsys package <pkg> | grep userId=`), rebuild,
   install, enable it in LSPosed with scope *System Framework*, reboot. Check the LSPosed log for `AstraKeepAlive: hooked ...`.
5. **Host config**: `cp host/config.example.sh host/config.sh` and fill in the tablet's tailnet address. Put the Operit
   token and the lock PIN in the files it points to (`chmod 600`).
6. **Lock**: `host/set-pin.sh` (sends salt + hash only), then `host/lockstate.sh`. Keep `host/lock-emergency.sh` handy.
7. Try it: `host/unlock.sh && host/ui2.sh dump active && host/ui2.sh click "Settings"`.

### `ui2.sh` cheat sheet
```
ui2.sh dump [active|raw]       compact tree of all windows (W lines = windows with bounds)
ui2.sh find "text" [n]         matching lines + center coordinates
ui2.sh tap  "text" [n]         input tap at the match center
ui2.sh click "text" [n]        accessibility ACTION_CLICK on nearest clickable ancestor (refused while locked)
ui2.sh clickid viewId [n]      click by resource id
ui2.sh scroll [down|up] [x]    swipe a list, then dump again (v2.4 dumps are always fresh)
line format: path Class "text" d="desc" #id [l,t,r,b] flags  (C clickable, L long, CHK+/-, SEL, E editable, SCR, DIS)
```

## ZTE background-killing lessons (short version)

Doze whitelisting is not enough. On this firmware **CpuFreezer** freezes app uids ~30 s after screen-off, **MindSync
lock cleanup** force-stops Tailscale ~3.5 min after screen-off (`reason: bgc_lock`), and Wi-Fi power save stalls the DERP
connection. The vendor whitelist is only read at boot. What finally made the device reachable for 13+ min screen-off
tests (and across reboots) was layering: vendor strategy DB + thaw loop + Tailscale `CONNECT_VPN` watchdog + LSPosed hooks
on `zteEx_forceStopPackage` and the freezer + Wi-Fi hi-perf mode. Also: **never `rmmod` drivers** (kernel panic).
Details: [`docs/zte-background-killing.md`](docs/zte-background-killing.md).

## Safety notes

- This is a **remote-control backdoor by design**. Only use it on devices you own, with the knowledge of the person using the device. The status bar and *exit takeover* button exist so that person can always see and stop it.
- Wireless adb on a tailnet is only as safe as your tailnet: use Tailscale ACLs so only the controller host can reach port 5555/8094. Never expose them to the internet or LAN.
- The overlay lock is **not** device encryption — anyone with adb/USB or safe mode can bypass it.
- The control receiver is protected by a signature permission; only root/system can lock, unlock or read the UI tree. Keep `su` grants minimal.
- Agents driving the device must not pay, order, send messages or change settings without explicit human confirmation (the example Operit skill encodes this).
- Secrets (Operit token, PIN, keystore password) live only in local files referenced by `host/config.sh`; `.gitignore` excludes them. Every boot script lists its undo command in its header.

## License

MIT — see [LICENSE](LICENSE).

---

> 🤖 Made with AI · 本项目由 AI 辅助构建。
