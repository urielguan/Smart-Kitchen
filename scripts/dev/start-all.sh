#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./common.sh
source "$SCRIPT_DIR/common.sh"

rebuild=false
takeover=false
launcher="$SCRIPT_DIR/launch-detached.py"

for arg in "$@"; do
  case "$arg" in
    --rebuild) rebuild=true ;;
    --takeover) takeover=true ;;
    *)
      echo "unknown argument: $arg" >&2
      exit 1
      ;;
  esac
done

if [[ "$rebuild" == "true" ]]; then
  rebuild_all_backend
fi

for service in "${SERVICE_IDS[@]}"; do
  if [[ "$service" == "frontend" ]]; then
    ensure_frontend_deps
  else
    ensure_backend_jar "$service"
  fi
done

for service in "${SERVICE_IDS[@]}"; do
  port="$(service_port "$service")"
  if managed_service_running "$service"; then
    echo "$service already managed on port $port"
    continue
  fi

  listener_pid="$(port_listener_pid "$port")"
  if [[ -n "$listener_pid" ]]; then
    if [[ "$takeover" == "true" ]]; then
      echo "$service taking over port $port from pid $listener_pid"
      kill_pid_gracefully "$listener_pid"
      sleep 1
      listener_pid="$(port_listener_pid "$port")"
    fi
  fi

  if [[ -n "$listener_pid" ]]; then
    echo "$service skipped: port $port is already occupied by pid $listener_pid"
    continue
  fi

  rm -f "$PID_DIR/$service.stop"
  : > "$(service_log_file "$service")"
  python3 "$launcher" "$(service_supervisor_log_file "$service")" "$SCRIPT_DIR/supervise.sh" "$service" >/dev/null
  sleep 1

  if wait_for_port "$port" 90; then
    echo "$service started on port $port"
  else
    echo "$service failed to open port $port, check $(service_log_file "$service")" >&2
  fi
done

echo
"$SCRIPT_DIR/status-all.sh"
