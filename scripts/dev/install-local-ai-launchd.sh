#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
PLIST_TEMPLATE="$SCRIPT_DIR/launchd/com.yingzi.ollama-openai-proxy.plist.template"
TARGET_PLIST="$HOME/Library/LaunchAgents/com.yingzi.ollama-openai-proxy.plist"

mkdir -p "$HOME/Library/LaunchAgents"
sed "s#__ROOT_DIR__#${ROOT_DIR}#g" "$PLIST_TEMPLATE" > "$TARGET_PLIST"
launchctl unload "$TARGET_PLIST" >/dev/null 2>&1 || true
launchctl load "$TARGET_PLIST"
echo "installed: $TARGET_PLIST"
