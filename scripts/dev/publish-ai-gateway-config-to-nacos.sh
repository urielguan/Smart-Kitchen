#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./common.sh
source "$SCRIPT_DIR/common.sh"

DATA_ID="${AI_NACOS_DATA_ID:-smartfood_ai_gateway_service.yaml}"
GROUP="${NACOS_GROUP:-DEFAULT_GROUP}"
NAMESPACE="${NACOS_NAMESPACE:-16d939fd-4033-4ef3-9241-6b15a51f8ad8}"
SERVER="http://${NACOS_SERVER_ADDR}"

TEXT_SERVICE_NAME="${SMARTFOOD_AI_TEXT_SERVICE_NAME:-统一AI网关-文本}"
TEXT_BASE_URL="${SMARTFOOD_AI_TEXT_BASE_URL:-}"
TEXT_API_KEY="${SMARTFOOD_AI_TEXT_API_KEY:-}"
TEXT_API_KEY_ENCRYPTED="${SMARTFOOD_AI_TEXT_API_KEY_ENCRYPTED:-}"
TEXT_MODEL="${SMARTFOOD_AI_TEXT_MODEL:-}"
TEXT_STATUS="${SMARTFOOD_AI_TEXT_STATUS:-inactive}"
AI_CONNECT_TIMEOUT_MS="${SMARTFOOD_AI_CONNECT_TIMEOUT_MS:-10000}"
AI_READ_TIMEOUT_MS="${SMARTFOOD_AI_READ_TIMEOUT_MS:-120000}"

VISION_SERVICE_NAME="${SMARTFOOD_AI_VISION_SERVICE_NAME:-统一AI网关-视觉}"
VISION_BASE_URL="${SMARTFOOD_AI_VISION_BASE_URL:-}"
VISION_API_KEY="${SMARTFOOD_AI_VISION_API_KEY:-}"
VISION_API_KEY_ENCRYPTED="${SMARTFOOD_AI_VISION_API_KEY_ENCRYPTED:-}"
VISION_MODEL="${SMARTFOOD_AI_VISION_MODEL:-}"
VISION_STATUS="${SMARTFOOD_AI_VISION_STATUS:-inactive}"

TOKEN="$(
  curl -sS -X POST "${SERVER}/nacos/v1/auth/login" \
    -d "username=${NACOS_USERNAME}&password=${NACOS_PASSWORD}" \
  | python3 -c 'import sys, json; print(json.load(sys.stdin)["accessToken"])'
)"

CURRENT_CONTENT="$(
  curl -sS "${SERVER}/nacos/v1/cs/configs?tenant=${NAMESPACE}&group=${GROUP}&dataId=${DATA_ID}&accessToken=${TOKEN}" || true
)"

if [[ "${CURRENT_CONTENT}" != "config data not exist"$'\r' && "${CURRENT_CONTENT}" != "config data not exist" ]]; then
  if [[ -z "${TEXT_BASE_URL}" && -z "${TEXT_MODEL}" && -z "${TEXT_API_KEY}" && -z "${TEXT_API_KEY_ENCRYPTED}" ]]; then
    echo "refuse to overwrite existing ${DATA_ID} with empty text model fields" >&2
    echo "set SMARTFOOD_AI_TEXT_BASE_URL / SMARTFOOD_AI_TEXT_MODEL / SMARTFOOD_AI_TEXT_API_KEY_ENCRYPTED explicitly when updating active config" >&2
    exit 1
  fi
fi

CONTENT="$(cat <<EOF
smartfood:
  ai:
    connect-timeout-ms: ${AI_CONNECT_TIMEOUT_MS}
    read-timeout-ms: ${AI_READ_TIMEOUT_MS}
    nacos-fallback-enabled: true
    default-services:
      - service-name: "${TEXT_SERVICE_NAME}"
        service-type: text
        base-url: "${TEXT_BASE_URL}"
        api-key: "${TEXT_API_KEY}"
        api-key-encrypted: "${TEXT_API_KEY_ENCRYPTED}"
        model-name: "${TEXT_MODEL}"
        applicable-modules:
          - nutrition_suggestion
        status: "${TEXT_STATUS}"
        remark: "Nacos统一AI文本接入配置"
      - service-name: "${VISION_SERVICE_NAME}"
        service-type: vision
        base-url: "${VISION_BASE_URL}"
        api-key: "${VISION_API_KEY}"
        api-key-encrypted: "${VISION_API_KEY_ENCRYPTED}"
        model-name: "${VISION_MODEL}"
        applicable-modules:
          - violation_recognition
        status: "${VISION_STATUS}"
        remark: "Nacos统一AI视觉接入配置"
EOF
)"

RESPONSE="$(
curl -sS -X POST "${SERVER}/nacos/v1/cs/configs" \
  --data-urlencode "tenant=${NAMESPACE}" \
  --data-urlencode "group=${GROUP}" \
  --data-urlencode "dataId=${DATA_ID}" \
  --data-urlencode "type=yaml" \
  --data-urlencode "content=${CONTENT}" \
  --data-urlencode "accessToken=${TOKEN}"
)"

echo "published ${DATA_ID} to nacos ${NACOS_SERVER_ADDR}"
echo "result: ${RESPONSE}"
echo
curl -sS "${SERVER}/nacos/v1/cs/configs?tenant=${NAMESPACE}&group=${GROUP}&dataId=${DATA_ID}&accessToken=${TOKEN}"
