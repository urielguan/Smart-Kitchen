#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./local-ai-common.sh
source "$SCRIPT_DIR/local-ai-common.sh"

load_local_ai_env
start_ollama_service
start_local_ai_proxy

echo "local ai started"
"$SCRIPT_DIR/status-local-ai.sh"
