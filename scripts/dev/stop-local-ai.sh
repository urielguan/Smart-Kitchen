#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./local-ai-common.sh
source "$SCRIPT_DIR/local-ai-common.sh"

stop_local_ai_services
echo "local ai stopped"
