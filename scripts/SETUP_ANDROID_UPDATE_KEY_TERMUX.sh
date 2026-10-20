#!/usr/bin/env bash
# One-time Termux setup for personal WinKit Android in-place update signing.
# Keeps the private keystore OUT of GitHub source and guest disks.
set -euo pipefail
umask 077
REPO="crunchradio81/WinKit"
for cmd in gh keytool openssl base64 tr; do
  command -v "$cmd" >/dev/null || { echo "Missing $cmd. In Termux: pkg install -y gh openjdk-17 openssl coreutils" >&2; exit 2; }
done
gh auth status >/dev/null
DIR="$HOME/.config/winkit-android-signing"
STORE="$DIR/winkit-update.jks"
PASSFILE="$DIR/winkit-update-password"
ALIAS="winkit-updates"
mkdir -p "$DIR"
chmod 700 "$DIR"
if [[ -e "$STORE" && -f "$PASSFILE" ]]; then
  echo "Reusing existing personal WinKit signing key; no key regeneration."
elif [[ ! -e "$STORE" && ! -e "$PASSFILE" ]]; then
  PASSWORD="$(openssl rand -hex 24)"
  printf '%s' "$PASSWORD" > "$PASSFILE"
  keytool -genkeypair -noprompt -storetype PKCS12 \
    -keystore "$STORE" -alias "$ALIAS" -keyalg RSA -keysize 3072 \
    -validity 12500 -storepass "$PASSWORD" -keypass "$PASSWORD" \
    -dname "CN=WinKit Personal Android, O=WinKit"
  unset PASSWORD
else
  echo "Only one of the key/password files exists. Stop to prevent key loss." >&2
  exit 2
fi
chmod 600 "$STORE" "$PASSFILE"
PASSWORD="$(cat "$PASSFILE")"
keytool -list -keystore "$STORE" -alias "$ALIAS" -storepass "$PASSWORD" >/dev/null
base64 "$STORE" | tr -d '\n' | gh secret set WINKIT_SIGNING_KEYSTORE_B64 -R "$REPO"
printf '%s' "$PASSWORD" | gh secret set WINKIT_SIGNING_STORE_PASSWORD -R "$REPO"
printf '%s' "$ALIAS" | gh secret set WINKIT_SIGNING_KEY_ALIAS -R "$REPO"
printf '%s' "$PASSWORD" | gh secret set WINKIT_SIGNING_KEY_PASSWORD -R "$REPO"
unset PASSWORD
echo "WinKit stable signing secrets configured for $REPO."
echo "PRIVATE BACKUP REQUIRED: $STORE and $PASSFILE"
echo "Never post either file, its base64, or its password in chat or GitHub."
echo "The FIRST new-key APK needs one uninstall; subsequent builds can update in place."
