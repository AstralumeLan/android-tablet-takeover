# Overlay PIN lock (takeover app ≥ v2.1)

The system lock screen is disabled (it would block remote control), so the app draws its own lock.

- **What it is**: a full-screen opaque `TYPE_APPLICATION_OVERLAY` window (`TakeoverLock`) that eats all touches and BACK, hides the system bars, and runs `cmd statusbar send-disable-flag statusbar-expansion notification-peek home recents` (via root) so shade/home/recents are unusable. Unlock runs `send-disable-flag none`.
- **When it locks**: immediately on `SCREEN_OFF`, and on boot / app update / service restart whenever a PIN is set. Stays unlocked until the next screen-off.
- **PIN storage**: only a random salt + PBKDF2-HMAC-SHA256 (20 000 iterations) hash in app-private SharedPreferences. `host/set-pin.sh` computes the hash on the host and sends only salt+hash; the PIN never reaches the tablet in clear text.
- **Rate limit**: 5 wrong tries → 30 s cooldown, doubling per further failure (max 32 min).
- **Guard**: if the overlay loses focus it collapses the shade every second and presses BACK on system dialogs that hide app overlays (resolver/chooser, package installer, permission controller), for up to 20 s.
- **Remote control** (`.Ctl` receiver, signature permission `com.astra.takeover.permission.CONTROL`): root/system bypass the permission check; `shell` (uid 2000) and third-party apps get `Permission Denial`. Actions: `LOCK`, `UNLOCK`, `SETPIN`, `CLEARPIN`, `STATE`, `DUMP`, `CLICK`, `TESTFAILS`.
- **Accessibility reader** (`.Ui`, v2.3+): read-only UI tree for `DUMP` / `CLICK`. Never dumps the lock overlay's content and refuses clicks while locked. v2.4 calls `clearCache()` before each request because the service subscribes to no content events, so the framework cache was otherwise stale after scrolling.

## Emergency recovery
1. `host/lock-emergency.sh` (force-stop app + `send-disable-flag none`); `--clear` also wipes the PIN hash.
2. Manually: `adb shell su -c 'am force-stop com.astra.takeover; cmd statusbar send-disable-flag none'`, or `pm disable-user` / uninstall.
3. If the app dies, the keepalive script notices leftover statusbar disable flags within ~10 s and clears them.
4. Safe mode (hold volume-down at boot) prevents third-party apps from running.
5. Without a computer: the correct PIN always works; wrong PINs only cause cooldowns.

## Limits
Not a system lock and no data encryption: it stops someone at the screen, not someone with USB/ADB access or safe mode.
Incoming calls can't be answered while locked. Heads-up notifications may still peek on some firmware.
