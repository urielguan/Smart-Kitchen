# 智慧厨房管理平台 - 边缘识别 + 中心 SaaS 配置与服务拆分设计文档

## 文档信息

| 项目 | 内容 |
|---|---|
| 文档名称 | 智慧厨房管理平台 - 边缘识别 + 中心 SaaS 配置与服务拆分设计文档 |
| 文档版本 | v1.0 |
| 创建日期 | 2026-06-17 |
| 核心聚焦 | Nacos 配置、套餐能力开关、`vision-ai-service` 服务拆分 |
| 适用范围 | 平台配置中心、后端实施、运维部署、产品套餐规划 |

---

## 1. 设计目标

本文件用于回答以下三个问题：

1. 中心平台和边缘节点各自需要哪些 Nacos 配置。
2. 如何把摄像头识别做成套餐能力和细粒度开关。
3. `vision-ai-service` 在当前项目里应该如何拆模块、拆接口、拆职责。

---

## 2. Nacos 配置设计

## 2.1 配置分层原则

建议分为 4 层：

1. 平台服务级配置
2. 租户视觉默认配置
3. 边缘节点启动模板配置
4. 灰度/覆盖配置

## 2.2 新增 Data ID 建议

| Data ID | 适用服务 | 作用 |
|---|---|---|
| `smartfood_vision_ai_service.yaml` | `vision-ai-service` | 中心视觉能力服务主配置 |
| `smartfood_device_service.yaml` | `device-service` | 设备服务的视觉流分发配置 |
| `smartfood_gateway_service.yaml` | `gateway-service` | 视觉相关接口超时与路由增强 |
| `smartfood_vision_edge_agent.yaml` | 边缘节点模板 | 边缘节点基础配置模板 |
| `smartfood_vision_tenant_{tenantId}.yaml` | 租户级覆盖 | 特定租户模型/阈值/规则覆盖 |

## 2.3 `smartfood_vision_ai_service.yaml` 示例

```yaml
spring:
  application:
    name: smartfood_vision_ai_service

server:
  port: 8090

vision:
  edge:
    enabled: true
    auth-mode: token
    heartbeat-timeout-seconds: 90
    callback-timeout-ms: 5000
  ingest:
    snapshot-required: true
    deduplicate-window-seconds: 30
    default-risk-level: medium
  storage:
    minio-bucket-snapshot: vision-snapshots
    minio-bucket-clip: vision-clips
  rule:
    default-profile-code: kitchen-standard-v1
  model:
    default-framework: ultralytics
    allow-tenant-custom-model: false
  stream:
    signed-url-expire-seconds: 300
    prefer-lan-url: true
  mq:
    event-topic: vision.detect.event
    alarm-topic: vision.detect.alarm
```

## 2.4 `smartfood_device_service.yaml` 示例

```yaml
vision-stream:
  mode: edge
  discovery: platform
  fallback-enabled: false
  play-url-strategy: tenant-edge
  signed-url-enabled: true
  default-stream-type: mjpeg
  prefer-analysis-stream: true
```

说明：

1. 这里不再保留固定 `base-url`。
2. `device-service` 通过 `device_vision_binding + vision_edge_node` 动态生成流地址。

## 2.5 `smartfood_gateway_service.yaml` 示例

```yaml
spring:
  cloud:
    gateway:
      httpclient:
        connect-timeout: 5000
        response-timeout: 30s

vision:
  api:
    timeout-ms: 30000
```

## 2.6 `smartfood_vision_edge_agent.yaml` 示例

```yaml
agent:
  node-code: EDGE-DEMO-001
  tenant-id: 1
  center-api-base-url: http://172.31.25.155:8080/api/v1/vision
  local-bind-host: 0.0.0.0
  local-bind-port: 18081
  auth-token: replace-me

model:
  path: /opt/vision/models/kitchen-yolo-base.pt
  framework: ultralytics
  device: cpu
  confidence: 0.6
  iou: 0.45
  image-size: 960

capture:
  frame-interval-ms: 500
  snapshot-on-event: true
  cache-dir: /opt/vision/cache

stream:
  annotated-path: /annotated.mjpg
  raw-path: /raw.mjpg

upload:
  retry-max: 10
  retry-interval-seconds: 30
```

## 2.7 租户覆盖配置示例

`smartfood_vision_tenant_1001.yaml`

```yaml
vision:
  tenant:
    model-code: kitchen-yolo-enterprise-v3
    rule-profile-code: kitchen-enterprise-v2
    confidence-threshold: 0.72
    annotated-stream-enabled: true
    video-clip-upload-enabled: true
```

---

## 3. 套餐与能力开关设计

## 3.1 套餐层

建议套餐编码：

| 套餐编码 | 说明 |
|---|---|
| `BASE` | 不开通视觉识别 |
| `VISION_BASIC` | 基础识别与实时流 |
| `VISION_PRO` | 带告警闭环、截图证据、统计分析 |
| `VISION_ENTERPRISE` | 带专属模型、规则定制、多节点 |

## 3.2 细粒度能力项

| 能力编码 | 说明 |
|---|---|
| `vision.realtime.detect` | 实时识别 |
| `vision.annotated.stream` | 标注流播放 |
| `vision.snapshot.evidence` | 事件截图留证 |
| `vision.video.clip` | 短视频片段留证 |
| `vision.custom.model` | 自定义模型 |
| `vision.rule.customize` | 自定义规则 |
| `vision.edge.multi-node` | 多边缘节点 |
| `vision.report.analytics` | 识别分析报表 |

## 3.3 前端呈现规则

前端统一通过能力接口控制显示：

1. 未开通 `vision.realtime.detect`：隐藏视频监控识别入口。
2. 未开通 `vision.annotated.stream`：只显示原始流或直接隐藏识别流区域。
3. 未开通 `vision.report.analytics`：隐藏行为分析与统计看板。
4. 未开通 `vision.snapshot.evidence`：事件详情不展示截图证据。

## 3.4 后端执行规则

1. 未开通能力时，中心拒绝边缘节点绑定或事件入库。
2. 超出摄像头或节点额度时，不允许新增绑定。
3. 专属模型能力未开通时，只允许选择平台公共模型。

---

## 4. `vision-ai-service` 服务拆分方案

## 4.1 服务定位

`vision-ai-service` 是中心视觉能力编排服务，不直接承担所有视频推理，而是：

1. 管配置
2. 管边缘节点
3. 管模型规则
4. 收识别事件
5. 桥接告警闭环

## 4.2 包结构建议

```text
backend/vision-ai-service
├── controller
├── service
│   ├── capability
│   ├── edge
│   ├── model
│   ├── rule
│   ├── stream
│   ├── event
│   └── alarm
├── mapper
├── entity
├── dto
├── vo
└── config
```

## 4.3 子模块职责

### 4.3.1 `capability`

职责：

1. 租户视觉能力开关
2. 套餐配额校验
3. 前端能力查询接口

### 4.3.2 `edge`

职责：

1. 边缘节点注册
2. 心跳与在线状态维护
3. 边缘节点令牌与版本管理

### 4.3.3 `model`

职责：

1. 模型元数据管理
2. 公共模型与租户模型隔离
3. 模型版本发布与回滚

### 4.3.4 `rule`

职责：

1. 平台规则模板
2. 租户规则覆盖
3. 规则版本与灰度

### 4.3.5 `stream`

职责：

1. 生成标注流、原始流、回放流访问地址
2. 返回直连边缘还是中心代理策略
3. 输出签名 URL 或播放票据

### 4.3.6 `event`

职责：

1. 接收边缘节点上报的视觉事件
2. 去重、聚合、风险分级
3. 事件与帧明细入库

### 4.3.7 `alarm`

职责：

1. 把视觉事件桥接到现有告警和整改工单体系
2. 回写 `alarm_id / dispatch_id`
3. 提供事件到工单回跳能力

---

## 5. API 设计建议

## 5.1 对前端和中心服务开放

### 查询租户视觉能力
- `GET /api/v1/vision/capabilities/current`

### 查询设备可访问流地址
- `GET /api/v1/vision/devices/{deviceId}/stream-access`

### 查询模型列表
- `GET /api/v1/vision/models`

### 查询规则模板列表
- `GET /api/v1/vision/rule-profiles`

## 5.2 对边缘节点开放

### 边缘节点注册
- `POST /api/v1/vision/edge/register`

### 边缘节点心跳
- `POST /api/v1/vision/edge/heartbeat`

### 拉取节点配置
- `GET /api/v1/vision/edge/{nodeCode}/runtime-config`

### 上报识别事件
- `POST /api/v1/vision/events/report`

### 上传截图
- `POST /api/v1/vision/snapshots/upload`

### 上传视频片段
- `POST /api/v1/vision/clips/upload`

## 5.3 对内部服务开放

### 设备服务查询流分发信息
- `GET /api/v1/vision/internal/devices/{deviceId}/dispatch`

### 告警桥接
- `POST /api/v1/vision/internal/events/{eventId}/bridge-alarm`

---

## 6. 与现有服务的交互关系

## 6.1 `device-service`

需要改造点：

1. 列表接口返回的 `analysisStreamUrl` 改为动态查询。
2. 不再写死 `vision-stream.base-url` 作为生产口径。
3. 设备管理页新增边缘节点绑定与视觉能力状态展示。

## 6.2 `sys-service`

建议新增：

1. 租户能力查询接口
2. 平台套餐与租户开通管理接口

## 6.3 `gateway-service`

建议补充：

1. `vision` 路由转发
2. 更长的视觉接口超时配置
3. 可选短时签名 URL 鉴权透传

---

## 7. 分阶段落地建议

### 阶段一：服务骨架

1. 新建 `vision-ai-service`
2. 新增 Nacos Data ID
3. 新增租户能力、边缘节点、设备绑定三类表

### 阶段二：边缘接入

1. 当前 `/Users/guanyiru/Desktop/yingzicode/摄像头识别` 升级为 `vision-edge-agent`
2. 实现注册、心跳、事件上报
3. `device-service` 改为动态流地址

### 阶段三：产品化治理

1. 套餐与能力开关
2. 模型中心与规则模板
3. 与告警工单、看板统计打通

---

## 8. 推荐结论

对当前项目，最稳的拆分方式是：

1. `device-service` 继续负责设备和视频播放入口。
2. 新增 `vision-ai-service` 负责 SaaS 化治理与中心编排。
3. 现场部署 `vision-edge-agent` 负责拉流和推理。
4. Nacos 统一管理中心服务配置，边缘节点使用模板配置加中心回传配置结合运行。
5. 套餐与能力项同时存在，套餐决定默认范围，能力项决定实际控制粒度。

这套设计能兼容你现在的代码结构，也适合后续从联调版平滑演进到正式 SaaS 版。
