#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./local-ai-common.sh
source "$SCRIPT_DIR/local-ai-common.sh"

load_local_ai_env

local_ip="$(local_ai_ip_candidates | head -n 1)"

echo "AI接口管理配置建议："
echo "  服务类型: text"
echo "  模型名称: $OLLAMA_MODEL"
echo "  接口地址(本机): http://127.0.0.1:${PROXY_PORT}/v1"
if [[ -n "$local_ip" ]]; then
  echo "  接口地址(局域网): http://${local_ip}:${PROXY_PORT}/v1"
fi
echo "  API Key: $LOCAL_AI_KEY"
echo "  适用模块: nutrition_suggestion"
