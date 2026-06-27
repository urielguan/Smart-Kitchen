#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./common.sh
source "$SCRIPT_DIR/common.sh"

for (( idx=${#SERVICE_IDS[@]}-1; idx>=0; idx-- )); do
  service="${SERVICE_IDS[$idx]}"
  pid_file="$(service_pid_file "$service")"
  stop_flag="$PID_DIR/$service.stop"
  pid="$(read_pid_file "$pid_file")"

  if pid_is_running "$pid"; then
    touch "$stop_flag"
    kill "$pid" 2>/dev/null || true
    for _ in 1 2 3 4 5 6 7 8 9 10; do
      if ! pid_is_running "$pid"; then
        break
      fi
      sleep 1
    done
    if pid_is_running "$pid"; then
      kill -9 "$pid" 2>/dev/null || true
    fi
    echo "$service stopped"
  fi

  child_pid="$(read_pid_file "$(service_child_pid_file "$service")")"
  if pid_is_running "$child_pid"; then
    kill "$child_pid" 2>/dev/null || true
  fi

  rm -f "$pid_file" "$(service_child_pid_file "$service")" "$stop_flag"
done

