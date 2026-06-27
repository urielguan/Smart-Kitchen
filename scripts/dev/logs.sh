#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./common.sh
source "$SCRIPT_DIR/common.sh"

service="${1:-}"
if [[ -z "$service" ]]; then
  echo "usage: $0 <service-id>" >&2
  exit 1
fi

tail -n 200 -f "$(service_log_file "$service")"

