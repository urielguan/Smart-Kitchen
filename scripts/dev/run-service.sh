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

case "$service" in
  frontend)
    cd "$(service_dir "$service")"
    exec pnpm exec vite --host "$FRONTEND_HOST" --port "$FRONTEND_PORT"
    ;;
  *)
    jar="$(service_jar "$service")"
    if [[ ! -f "$jar" ]]; then
      echo "jar not found: $jar" >&2
      exit 1
    fi
    cd "$(service_dir "$service")"
    exec java -Xms256m -Xmx768m -jar "$jar"
    ;;
esac

