#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./local-ai-common.sh
source "$SCRIPT_DIR/local-ai-common.sh"

load_local_ai_env

ollama_listener="$(port_listener_pid 11434)"
proxy_listener="$(port_listener_pid "$PROXY_PORT")"

printf "ollama               %-11s port=%-5s pid=%s\n" "$( [[ -n "$ollama_listener" ]] && echo up || echo down )" "11434" "${ollama_listener:-"-"}"
printf "ollama-openai-proxy  %-11s port=%-5s pid=%s\n" "$( [[ -n "$proxy_listener" ]] && echo up || echo down )" "$PROXY_PORT" "${proxy_listener:-"-"}"
printf "model=%s\n" "$OLLAMA_MODEL"
printf "base_url=%s\n" "$OLLAMA_BASE_URL"
