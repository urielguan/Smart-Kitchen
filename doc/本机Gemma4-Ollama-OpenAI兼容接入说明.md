# 本机 Gemma4（Ollama）OpenAI 兼容接入说明

## 1. 目标

把本机 `Ollama` 中的 `gemma4:e4b` 暴露成一个局域网可访问的 `OpenAI-compatible` 服务，供系统 `AI接口管理` 直接接入。

---

## 2. 启动步骤

在项目根目录执行：

```bash
./scripts/dev/start-local-ai.sh
```

查看状态：

```bash
./scripts/dev/status-local-ai.sh
```

查看接入信息：

```bash
./scripts/dev/print-local-ai-credentials.sh
```

停止服务：

```bash
./scripts/dev/stop-local-ai.sh
```

---

## 3. 运行结构

- Ollama 原生服务：`127.0.0.1:11434`
- OpenAI 兼容代理：`0.0.0.0:18080`
- 当前固定模型：`gemma4:e4b`

代理提供接口：

- `GET /health`
- `GET /v1/models`
- `POST /v1/chat/completions`

---

## 4. AI接口管理配置

在系统管理 → `AI接口管理` 中新增文本模型配置：

- 服务类型：`text`
- 服务名称：`本机 Gemma4`
- 接口地址：`http://<本机局域网IP>:18080/v1`
- API Key：执行 `./scripts/dev/print-local-ai-credentials.sh` 获取
- 模型名称：`gemma4:e4b`
- 适用模块：`nutrition_suggestion`

注意：

- 现有后端会自动拼接 `/chat/completions`
- 所以接口地址必须填到 `/v1`
- 不要直接填 `/v1/chat/completions`

---

## 5. 本机验证

健康检查：

```bash
curl http://127.0.0.1:18080/health
```

模型列表：

```bash
curl http://127.0.0.1:18080/v1/models \
  -H "Authorization: Bearer <LOCAL_AI_KEY>"
```

文本生成：

```bash
curl http://127.0.0.1:18080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <LOCAL_AI_KEY>" \
  -d '{
    "model": "gemma4:e4b",
    "messages": [
      {"role": "system", "content": "你是一个中文助手"},
      {"role": "user", "content": "请回复：本地 Gemma4 已接通"}
    ],
    "temperature": 0.2,
    "max_tokens": 100
  }'
```

---

## 6. 开机自启

安装 `launchd`：

```bash
./scripts/dev/install-local-ai-launchd.sh
```

安装后会写入：

- `~/Library/LaunchAgents/com.yingzi.ollama-openai-proxy.plist`

---

## 7. 凭证保存

运行时实际 Key 保存在：

- `.runtime/local-ai/ollama-openai-proxy.env`

该文件不应提交到仓库。
