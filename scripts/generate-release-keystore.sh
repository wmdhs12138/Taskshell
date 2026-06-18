#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KEYSTORE_DIR="$ROOT_DIR/keystore"
KEYSTORE_PATH="$KEYSTORE_DIR/taskshell-release.jks"
ALIAS="taskshell"

mkdir -p "$KEYSTORE_DIR"

if [[ -f "$KEYSTORE_PATH" ]]; then
  echo "Keystore already exists: $KEYSTORE_PATH"
  exit 1
fi

read -rsp "Store password: " STORE_PASSWORD
echo
read -rsp "Key password: " KEY_PASSWORD
echo

keytool -genkeypair \
  -v \
  -keystore "$KEYSTORE_PATH" \
  -storetype JKS \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -storepass "$STORE_PASSWORD" \
  -keypass "$KEY_PASSWORD" \
  -dname "CN=Taskshell, OU=Taskshell, O=wmdhs12138, L=Unknown, ST=Unknown, C=CN"

cat > "$ROOT_DIR/signing.env.example" <<ENVEOF
# Copy this file to signing.env and fill your real passwords.
# Never commit signing.env or keystore/*.jks.
export TASKSHELL_KEYSTORE="keystore/taskshell-release.jks"
export TASKSHELL_KEY_ALIAS="$ALIAS"
export TASKSHELL_STORE_PASSWORD="replace-with-store-password"
export TASKSHELL_KEY_PASSWORD="replace-with-key-password"
ENVEOF

echo
echo "Generated: $KEYSTORE_PATH"
echo "Generated template: $ROOT_DIR/signing.env.example"
echo "Next: cp signing.env.example signing.env && edit signing.env"
