#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./common.sh
source "$SCRIPT_DIR/common.sh"

LOCAL_AI_RUNTIME_DIR="$RUNTIME_DIR/local-ai"
LOCAL_AI_ENV_FILE="$LOCAL_AI_RUNTIME_DIR/ollama-openai-proxy.env"
LOCAL_AI_PROXY_LOG="$LOG_DIR/ollama-openai-proxy.log"
LOCAL_AI_OLLAMA_LOG="$LOG_DIR/ollama.log"
LOCAL_AI_PROXY_PID_FILE="$PID_DIR/ollama-openai-proxy.pid"
LOCAL_AI_OLLAMA_PID_FILE="$PID_DIR/ollama.pid"

mkdir -p "$LOCAL_AI_RUNTIME_DIR"

export OLLAMA_HOST="${OLLAMA_HOST:-127.0.0.1:11434}"
export OLLAMA_BASE_URL="${OLLAMA_BASE_URL:-http://127.0.0.1:11434}"
export OLLAMA_MODEL="${OLLAMA_MODEL:-gemma4:e4b}"
export PROXY_HOST="${PROXY_HOST:-0.0.0.0}"
export PROXY_PORT="${PROXY_PORT:-18080}"
export PROXY_REQUEST_TIMEOUT_SECONDS="${PROXY_REQUEST_TIMEOUT_SECONDS:-120}"

local_ai_proxy_pid() {
  read_pid_file "$LOCAL_AI_PROXY_PID_FILE"
}

local_ai_ollama_pid() {
  read_pid_file "$LOCAL_AI_OLLAMA_PID_FILE"
}

local_ai_generate_key() {
  python3 - <<'PY'
import secrets
print("sk-local-" + secrets.token_urlsafe(32))
PY
}

ensure_local_ai_env() {
  if [[ ! -f "$LOCAL_AI_ENV_FILE" ]]; then
    local key
    key="$(local_ai_generate_key)"
    cat > "$LOCAL_AI_ENV_FILE" <<EOF
LOCAL_AI_KEY=$key
OLLAMA_MODEL=$OLLAMA_MODEL
OLLAMA_BASE_URL=$OLLAMA_BASE_URL
PROXY_HOST=$PROXY_HOST
PROXY_PORT=$PROXY_PORT
PROXY_REQUEST_TIMEOUT_SECONDS=$PROXY_REQUEST_TIMEOUT_SECONDS
EOF
    chmod 600 "$LOCAL_AI_ENV_FILE"
  fi
}

load_local_ai_env() {
  ensure_local_ai_env
  # shellcheck disable=SC1090
  source "$LOCAL_AI_ENV_FILE"
  export LOCAL_AI_KEY OLLAMA_MODEL OLLAMA_BASE_URL PROXY_HOST PROXY_PORT PROXY_REQUEST_TIMEOUT_SECONDS
}

start_ollama_service() {
  local listener
  listener="$(port_listener_pid 11434)"
  if [[ -n "$listener" ]]; then
    echo "ollama already listening on 11434 (pid=$listener)"
    return 0
  fi
  : > "$LOCAL_AI_OLLAMA_LOG"
  /bin/bash -lc "export OLLAMA_HOST='$OLLAMA_HOST'; nohup ollama serve >> '$LOCAL_AI_OLLAMA_LOG' 2>&1 & echo \$! > '$LOCAL_AI_OLLAMA_PID_FILE'"
  wait_for_port 11434 30
}

start_local_ai_proxy() {
  load_local_ai_env
  local listener
  listener="$(port_listener_pid "$PROXY_PORT")"
  if [[ -n "$listener" ]]; then
    echo "ollama-openai-proxy already listening on $PROXY_PORT (pid=$listener)"
    return 0
  fi
  : > "$LOCAL_AI_PROXY_LOG"
  /bin/bash -lc "export LOCAL_AI_KEY='$LOCAL_AI_KEY' OLLAMA_MODEL='$OLLAMA_MODEL' OLLAMA_BASE_URL='$OLLAMA_BASE_URL' PROXY_HOST='$PROXY_HOST' PROXY_PORT='$PROXY_PORT' PROXY_REQUEST_TIMEOUT_SECONDS='$PROXY_REQUEST_TIMEOUT_SECONDS'; nohup python3 '$SCRIPT_DIR/ollama-openai-proxy.py' >> '$LOCAL_AI_PROXY_LOG' 2>&1 & echo \$! > '$LOCAL_AI_PROXY_PID_FILE'"
  wait_for_port "$PROXY_PORT" 30
}

stop_local_ai_services() {
  local pid
  pid="$(local_ai_proxy_pid)"
  kill_pid_gracefully "$pid"
  rm -f "$LOCAL_AI_PROXY_PID_FILE"

  pid="$(local_ai_ollama_pid)"
  kill_pid_gracefully "$pid"
  rm -f "$LOCAL_AI_OLLAMA_PID_FILE"
}

local_ai_ip_candidates() {
  ipconfig getifaddr en0 2>/dev/null || true
  ipconfig getifaddr en1 2>/dev/null || true
}
