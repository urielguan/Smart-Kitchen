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

pid_file="$(service_pid_file "$service")"
child_pid_file="$(service_child_pid_file "$service")"
stop_flag="$PID_DIR/$service.stop"
log_file="$(service_log_file "$service")"

echo "$$" > "$pid_file"
rm -f "$stop_flag"

child_pid=""
cleanup() {
  touch "$stop_flag"
  if [[ -n "$child_pid" ]] && pid_is_running "$child_pid"; then
    kill "$child_pid" 2>/dev/null || true
    wait "$child_pid" 2>/dev/null || true
  fi
  rm -f "$pid_file" "$child_pid_file"
}
trap '' HUP
trap cleanup EXIT INT TERM

while true; do
  if [[ -f "$stop_flag" ]]; then
    break
  fi

  nohup "$SCRIPT_DIR/run-service.sh" "$service" >>"$log_file" 2>&1 &
  child_pid=$!
  echo "$child_pid" > "$child_pid_file"
  if wait "$child_pid"; then
    exit_code=0
  else
    exit_code=$?
  fi
  rm -f "$child_pid_file"
  child_pid=""

  if [[ -f "$stop_flag" ]]; then
    break
  fi

  printf '[%s] %s exited with code %s, restarting in 3s\n' "$(date '+%F %T')" "$service" "$exit_code" >>"$log_file"
  sleep 3
done
