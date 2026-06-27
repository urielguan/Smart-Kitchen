#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RUNTIME_DIR="$ROOT_DIR/.runtime"
LOG_DIR="$RUNTIME_DIR/logs"
PID_DIR="$RUNTIME_DIR/pids"

mkdir -p "$LOG_DIR" "$PID_DIR"

export NACOS_SERVER_ADDR="${NACOS_SERVER_ADDR:-172.31.25.155:8848}"
export NACOS_NAMESPACE="${NACOS_NAMESPACE:-16d939fd-4033-4ef3-9241-6b15a51f8ad8}"
export NACOS_GROUP="${NACOS_GROUP:-DEFAULT_GROUP}"
export NACOS_USERNAME="${NACOS_USERNAME:-nacos}"
export NACOS_PASSWORD="${NACOS_PASSWORD:-nacos}"

export MYSQL_HOST="${MYSQL_HOST:-172.31.25.155}"
export MYSQL_PORT="${MYSQL_PORT:-3306}"
export MYSQL_DATABASE="${MYSQL_DATABASE:-smart_food_safety}"
export MYSQL_USERNAME="${MYSQL_USERNAME:-root}"
export MYSQL_PASSWORD="${MYSQL_PASSWORD:-YingziOS#2026}"

export GATEWAY_TARGET="${GATEWAY_TARGET:-http://127.0.0.1:8080}"
export RECIPE_SERVICE_TARGET="${RECIPE_SERVICE_TARGET:-http://127.0.0.1:8084}"
export VITE_API_BASE_URL="${VITE_API_BASE_URL:-/api}"
export VITE_BYPASS_LOGIN="${VITE_BYPASS_LOGIN:-false}"
export VITE_DASHBOARD_DATA_SOURCE="${VITE_DASHBOARD_DATA_SOURCE:-auto}"
export VISION_STREAM_BASE_URL="${VISION_STREAM_BASE_URL:-http://127.0.0.1:18081}"
export FRONTEND_PORT="${FRONTEND_PORT:-5175}"
export FRONTEND_HOST="${FRONTEND_HOST:-0.0.0.0}"

export JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home}"
export PATH="$JAVA_HOME/bin:$PATH"

SERVICE_IDS=(
  gateway-service
  auth-service
  scm-service
  wms-service
  recipe-service
  cook-service
  sample-service
  health-service
  device-service
  sys-service
  frontend
)

service_port() {
  case "$1" in
    gateway-service) echo 8080 ;;
    auth-service) echo 8081 ;;
    scm-service) echo 8082 ;;
    wms-service) echo 8083 ;;
    recipe-service) echo 8084 ;;
    cook-service) echo 8085 ;;
    sample-service) echo 8086 ;;
    health-service) echo 8087 ;;
    device-service) echo 8088 ;;
    sys-service) echo 8089 ;;
    frontend) echo "$FRONTEND_PORT" ;;
    *)
      echo "unknown service: $1" >&2
      return 1
      ;;
  esac
}

service_dir() {
  case "$1" in
    frontend) echo "$ROOT_DIR/frontend" ;;
    *) echo "$ROOT_DIR/backend/$1" ;;
  esac
}

service_jar() {
  case "$1" in
    auth-service) echo "$ROOT_DIR/backend/auth-service/target/auth-service-1.0.0.jar" ;;
    gateway-service) echo "$ROOT_DIR/backend/gateway-service/target/gateway-service-1.0.0.jar" ;;
    scm-service) echo "$ROOT_DIR/backend/scm-service/target/scm-service-1.0.0.jar" ;;
    wms-service) echo "$ROOT_DIR/backend/wms-service/target/wms-service-1.0.0.jar" ;;
    recipe-service) echo "$ROOT_DIR/backend/recipe-service/target/recipe-service-1.0.0.jar" ;;
    cook-service) echo "$ROOT_DIR/backend/cook-service/target/cook-service-1.0.0.jar" ;;
    sample-service) echo "$ROOT_DIR/backend/sample-service/target/sample-service-1.0.0.jar" ;;
    health-service) echo "$ROOT_DIR/backend/health-service/target/health-service-1.0.0.jar" ;;
    device-service) echo "$ROOT_DIR/backend/device-service/target/device-service-1.0.0.jar" ;;
    sys-service) echo "$ROOT_DIR/backend/sys-service/target/sys-service-1.0.0.jar" ;;
    *)
      echo ""
      return 1
      ;;
  esac
}

service_pid_file() {
  echo "$PID_DIR/$1.pid"
}

service_child_pid_file() {
  echo "$PID_DIR/$1.child.pid"
}

service_log_file() {
  echo "$LOG_DIR/$1.log"
}

service_supervisor_log_file() {
  echo "$LOG_DIR/$1.supervisor.log"
}

pid_is_running() {
  local pid="$1"
  [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null
}

read_pid_file() {
  local pid_file="$1"
  if [[ -f "$pid_file" ]]; then
    tr -d '[:space:]' < "$pid_file"
  fi
}

managed_service_running() {
  local service="$1"
  local pid
  pid="$(read_pid_file "$(service_pid_file "$service")")"
  pid_is_running "$pid"
}

child_service_running() {
  local service="$1"
  local pid
  pid="$(read_pid_file "$(service_child_pid_file "$service")")"
  pid_is_running "$pid"
}

port_listener_pid() {
  local port="$1"
  (lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true) | head -n 1
}

is_port_listening() {
  local port="$1"
  lsof -iTCP:"$port" -sTCP:LISTEN -n -P >/dev/null 2>&1
}

kill_pid_gracefully() {
  local pid="${1:-}"
  if ! pid_is_running "$pid"; then
    return 0
  fi

  kill "$pid" 2>/dev/null || true
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    if ! pid_is_running "$pid"; then
      return 0
    fi
    sleep 1
  done

  kill -9 "$pid" 2>/dev/null || true
}

wait_for_port() {
  local port="$1"
  local timeout="${2:-60}"
  local elapsed=0
  while (( elapsed < timeout )); do
    if is_port_listening "$port"; then
      return 0
    fi
    sleep 1
    elapsed=$((elapsed + 1))
  done
  return 1
}

ensure_backend_jar() {
  local service="$1"
  local jar
  jar="$(service_jar "$service")"
  if [[ -f "$jar" ]]; then
    return 0
  fi
  (
    cd "$ROOT_DIR/backend"
    mvn -pl "$service" -am -DskipTests package
  )
}

rebuild_all_backend() {
  (
    cd "$ROOT_DIR/backend"
    mvn -DskipTests package
  )
}

ensure_frontend_deps() {
  if [[ ! -d "$ROOT_DIR/frontend/node_modules" ]]; then
    (
      cd "$ROOT_DIR/frontend"
      pnpm install
    )
  fi
}

print_service_line() {
  local service="$1"
  local port listener pid status
  port="$(service_port "$service")"
  pid="$(read_pid_file "$(service_pid_file "$service")")"
  listener="$(port_listener_pid "$port")"
  if managed_service_running "$service"; then
    status="managed-up"
  elif [[ -n "$listener" ]]; then
    status="port-busy"
  else
    status="down"
  fi
  printf "%-16s %-11s port=%-5s supervisor=%-8s listener=%s\n" "$service" "$status" "$port" "${pid:-"-"}" "${listener:-"-"}"
}
