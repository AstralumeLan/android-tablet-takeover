# Copy to host/config.sh (git-ignored) and edit. Sourced by every host script.
# adb target of the tablet, e.g. its Tailscale (tailnet) address + wireless-adb port.
TABLET_ADB="${TABLET_ADB:-<TABLET_TAILNET_IP>:5555}"
# Operit external-chat API on the tablet (Operit app, port 8094 by default).
OPERIT_URL="${OPERIT_URL:-http://<TABLET_TAILNET_IP>:8094}"
# Files holding secrets (chmod 600). Never commit them, never print them.
OPERIT_TOKEN_FILE="${OPERIT_TOKEN_FILE:-$HOME/.config/tablet-takeover/operit.token}"
PIN_FILE="${PIN_FILE:-$HOME/.config/tablet-takeover/tablet.pin}"
