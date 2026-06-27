# 智慧厨房管理平台

## 本地启动

### 1. 启动主项目

在项目根目录执行：

```bash
cd /Users/guanyiru/Desktop/yingzicode/yingzi-zncf
./scripts/dev/start-all.sh --takeover
```

启动后默认访问：

- 前端：`http://localhost:5175`
- 登录页：`http://localhost:5175/login`
- 默认账号：`admin`
- 默认密码：`admin`

### 2. 启动视频监控管理的 YOLO 视频流

视频监控管理页面的识别画面不是主项目内部直接生成的，当前依赖外部联调项目：

- YOLO 项目目录：`/Users/guanyiru/Desktop/yingzicode/摄像头识别`
- 默认识别流地址：`http://127.0.0.1:18081/annotated.mjpg`

先启动 YOLO 联调服务：

```bash
cd /Users/guanyiru/Desktop/yingzicode/摄像头识别
source .venv/bin/activate
python review_app.py --config config.yaml --host 127.0.0.1 --port 18081
```

建议另开一个终端保活这个进程。

### 3. 验证视频流是否正常

在新终端执行：

```bash
curl -I 'http://127.0.0.1:18081/annotated.mjpg'
```

如果返回 `200 OK`，再登录主项目进入：

- `http://localhost:5175/video-monitor`

### 4. 当前项目的视频监控接入口径

`device-service` 当前默认读取：

- `VISION_STREAM_BASE_URL=http://127.0.0.1:18081`

对应配置位置：

- [backend/device-service/src/main/resources/application.yml](/Users/guanyiru/Desktop/yingzicode/yingzi-zncf/backend/device-service/src/main/resources/application.yml)
- [scripts/dev/common.sh](/Users/guanyiru/Desktop/yingzicode/yingzi-zncf/scripts/dev/common.sh)

## 常见问题

### 1. 视频监控页显示“视频加载失败”

先检查：

1. `18081` 端口上的服务是否真的是 YOLO 联调服务。
2. `review_app.py` 是否已经启动。
3. `http://127.0.0.1:18081/annotated.mjpg` 是否返回 `200`。
4. 视频源 `config.yaml -> camera.source` 是否可连通。

### 2. `18081` 端口被占用

先查占用：

```bash
lsof -iTCP:18081 -sTCP:LISTEN -n -P
```

如果不是 YOLO 联调服务占用，先释放该端口后再启动 `review_app.py`。

## 详细手册

完整的视频监控画面启动与排障手册见：

- [doc/视频监控管理-视频画面启动手册.md](/Users/guanyiru/Desktop/yingzicode/yingzi-zncf/doc/视频监控管理-视频画面启动手册.md)
