#!/usr/bin/env python3

import json
import os
import socket
import time
import urllib.error
import urllib.request
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


OLLAMA_BASE_URL = os.environ.get("OLLAMA_BASE_URL", "http://127.0.0.1:11434").rstrip("/")
OLLAMA_MODEL = os.environ.get("OLLAMA_MODEL", "gemma4:e4b")
LOCAL_AI_KEY = os.environ.get("LOCAL_AI_KEY", "").strip()
PROXY_HOST = os.environ.get("PROXY_HOST", "0.0.0.0")
PROXY_PORT = int(os.environ.get("PROXY_PORT", "18080"))
REQUEST_TIMEOUT_SECONDS = int(os.environ.get("PROXY_REQUEST_TIMEOUT_SECONDS", "120"))


def json_response(handler: BaseHTTPRequestHandler, status: int, payload: dict) -> None:
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    handler.send_response(status)
    handler.send_header("Content-Type", "application/json; charset=utf-8")
    handler.send_header("Content-Length", str(len(body)))
    handler.end_headers()
    handler.wfile.write(body)


def read_json_body(handler: BaseHTTPRequestHandler) -> dict:
    content_length = int(handler.headers.get("Content-Length", "0"))
    raw = handler.rfile.read(content_length) if content_length > 0 else b"{}"
    return json.loads(raw.decode("utf-8") or "{}")


def bearer_token(handler: BaseHTTPRequestHandler) -> str:
    auth = handler.headers.get("Authorization", "")
    prefix = "Bearer "
    if not auth.startswith(prefix):
        return ""
    return auth[len(prefix):].strip()


def require_auth(handler: BaseHTTPRequestHandler) -> bool:
    if not LOCAL_AI_KEY:
        json_response(handler, 500, {
            "error": {
                "message": "LOCAL_AI_KEY is not configured",
                "type": "server_error"
            }
        })
        return False
    if bearer_token(handler) != LOCAL_AI_KEY:
        json_response(handler, 401, {
            "error": {
                "message": "Invalid API key",
                "type": "invalid_request_error"
            }
        })
        return False
    return True


def fetch_ollama_tags() -> dict:
    req = urllib.request.Request(
        f"{OLLAMA_BASE_URL}/api/tags",
        method="GET",
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=REQUEST_TIMEOUT_SECONDS) as resp:
        return json.loads(resp.read().decode("utf-8"))


def ollama_model_exists() -> bool:
    try:
        tags = fetch_ollama_tags()
    except Exception:
        return False
    models = tags.get("models") or []
    return any(item.get("name") == OLLAMA_MODEL for item in models)


def build_openai_response(content: str) -> dict:
    created = int(time.time())
    prompt_tokens = 0
    completion_tokens = max(1, len(content) // 4) if content else 0
    return {
        "id": f"chatcmpl-{uuid.uuid4().hex}",
        "object": "chat.completion",
        "created": created,
        "model": OLLAMA_MODEL,
        "choices": [
            {
                "index": 0,
                "message": {
                    "role": "assistant",
                    "content": content,
                },
                "finish_reason": "stop",
            }
        ],
        "usage": {
            "prompt_tokens": prompt_tokens,
            "completion_tokens": completion_tokens,
            "total_tokens": prompt_tokens + completion_tokens,
        },
    }


def proxy_chat(payload: dict) -> dict:
    model = payload.get("model") or OLLAMA_MODEL
    if model != OLLAMA_MODEL:
        raise ValueError(f"Only model '{OLLAMA_MODEL}' is allowed, got '{model}'")

    messages = payload.get("messages")
    if not isinstance(messages, list) or not messages:
        raise ValueError("messages is required")

    ollama_payload = {
        "model": OLLAMA_MODEL,
        "messages": messages,
        "stream": False,
        "options": {},
    }

    if payload.get("temperature") is not None:
        ollama_payload["options"]["temperature"] = payload["temperature"]
    if payload.get("max_tokens") is not None:
        ollama_payload["options"]["num_predict"] = payload["max_tokens"]

    req = urllib.request.Request(
        f"{OLLAMA_BASE_URL}/api/chat",
        method="POST",
        data=json.dumps(ollama_payload, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=REQUEST_TIMEOUT_SECONDS) as resp:
        ollama_resp = json.loads(resp.read().decode("utf-8"))

    message = ollama_resp.get("message") or {}
    content = message.get("content") or ""
    return build_openai_response(content)


class OllamaOpenAiProxyHandler(BaseHTTPRequestHandler):
    server_version = "OllamaOpenAIProxy/1.0"

    def log_message(self, format: str, *args) -> None:
        print("%s - - [%s] %s" % (self.address_string(), self.log_date_time_string(), format % args))

    def do_GET(self) -> None:
        if self.path == "/health":
            try:
                tags = fetch_ollama_tags()
                json_response(self, 200, {
                    "status": "healthy",
                    "proxyHost": PROXY_HOST,
                    "proxyPort": PROXY_PORT,
                    "ollamaBaseUrl": OLLAMA_BASE_URL,
                    "model": OLLAMA_MODEL,
                    "modelExists": any(item.get("name") == OLLAMA_MODEL for item in tags.get("models") or []),
                })
            except Exception as exc:
                json_response(self, 503, {
                    "status": "unhealthy",
                    "ollamaBaseUrl": OLLAMA_BASE_URL,
                    "model": OLLAMA_MODEL,
                    "error": str(exc),
                })
            return

        if self.path == "/v1/models":
            if not require_auth(self):
                return
            model_exists = ollama_model_exists()
            json_response(self, 200, {
                "object": "list",
                "data": [
                    {
                        "id": OLLAMA_MODEL,
                        "object": "model",
                        "owned_by": "ollama",
                        "available": model_exists,
                    }
                ],
            })
            return

        json_response(self, 404, {"error": {"message": "Not found", "type": "invalid_request_error"}})

    def do_POST(self) -> None:
        if self.path != "/v1/chat/completions":
            json_response(self, 404, {"error": {"message": "Not found", "type": "invalid_request_error"}})
            return
        if not require_auth(self):
            return

        try:
            payload = read_json_body(self)
            response = proxy_chat(payload)
            json_response(self, 200, response)
        except ValueError as exc:
            json_response(self, 400, {"error": {"message": str(exc), "type": "invalid_request_error"}})
        except urllib.error.HTTPError as exc:
            message = exc.read().decode("utf-8", errors="ignore")
            json_response(self, exc.code, {"error": {"message": message or str(exc), "type": "upstream_error"}})
        except (urllib.error.URLError, TimeoutError, socket.timeout) as exc:
            json_response(self, 504, {"error": {"message": str(exc), "type": "upstream_timeout"}})
        except Exception as exc:
            json_response(self, 500, {"error": {"message": str(exc), "type": "server_error"}})


def main() -> None:
    server = ThreadingHTTPServer((PROXY_HOST, PROXY_PORT), OllamaOpenAiProxyHandler)
    print(f"Ollama OpenAI proxy listening on {PROXY_HOST}:{PROXY_PORT}, model={OLLAMA_MODEL}, upstream={OLLAMA_BASE_URL}")
    server.serve_forever()


if __name__ == "__main__":
    main()
