#!/bin/bash
D="$(cd "$(dirname "$0")" && pwd)"; if [ -f "$D/config.sh" ]; then . "$D/config.sh"; else . "$D/config.example.sh"; fi
A="adb -s $TABLET_ADB"
# Set the overlay-lock PIN from $PIN_FILE. Only a salted PBKDF2-SHA256 hash (20000 iters) is sent to the tablet; the PIN is never printed or sent.
read SALT HASH < <(PIN_FILE="$PIN_FILE" python3 - <<'PY'
import hashlib,os
pin=open(os.environ['PIN_FILE']).read().strip()
assert pin.isdigit() and 4<=len(pin)<=16, "pin must be 4-16 digits"
salt=os.urandom(16); print(salt.hex(), hashlib.pbkdf2_hmac('sha256',pin.encode(),salt,20000,32).hex())
PY
)
[ ${#HASH} = 64 ] || { echo "hash failed" >&2; exit 1; }
$A shell "su -c 'am broadcast -n com.astra.takeover/.Ctl -a com.astra.takeover.SETPIN --es salt $SALT --es hash $HASH --ei iter 20000'" | grep -o 'data=.*'
