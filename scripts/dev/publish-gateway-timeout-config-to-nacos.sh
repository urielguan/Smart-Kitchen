#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

DATA_ID="${GATEWAY_NACOS_DATA_ID:-smartfood_gateway_service.yaml}"
GROUP="${NACOS_GROUP:-DEFAULT_GROUP}"
NAMESPACE="${NACOS_NAMESPACE:-16d939fd-4033-4ef3-9241-6b15a51f8ad8}"
SERVER="http://${NACOS_SERVER_ADDR}"

GATEWAY_CONNECT_TIMEOUT_MS="${GATEWAY_CONNECT_TIMEOUT_MS:-10000}"
GATEWAY_RESPONSE_TIMEOUT_MS="${GATEWAY_RESPONSE_TIMEOUT_MS:-120000}"

TOKEN="$(
  curl -sS -X POST "${SERVER}/nacos/v1/auth/login" \
    -d "username=${NACOS_USERNAME}&password=${NACOS_PASSWORD}" \
  | python3 -c 'import sys, json; print(json.load(sys.stdin)["accessToken"])'
)"

CONTENT="$(cat <<EOF
spring:
  cloud:
    gateway:
      httpclient:
        connect-timeout: ${GATEWAY_CONNECT_TIMEOUT_MS}
        response-timeout: ${GATEWAY_RESPONSE_TIMEOUT_MS}ms
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
