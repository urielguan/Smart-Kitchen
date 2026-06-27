# 智慧食堂食品安全管理平台 (Smart Canteen Food Safety Management Platform)

[![GitHub Release](https://img.shields.io/badge/Release-v1.0.0-blue.svg)]()
[![Platform](https://img.shields.io/badge/Platform-Web%20%7C%20Kitchen%20Terminal%20%7C%20Mobile-orange.svg)]()
[![Technology](https://img.shields.io/badge/Stack-Spring%20Cloud%20%7C%20Vue%203%20%7C%20YOLO-green.svg)]()

本系统是一套集成 **IoT 智能物联网设备** 与 **AI 计算机视觉算法** 的端到端**智慧食堂食品安全全链路监管平台**。平台不仅包含传统的视频识别，更深入食堂日常运营的每个环节，实现从“食材采购、仓储物料、智能配餐、人脸晨检、烹饪过程监管、食品留样”到“告警闭环处置”的食品安全闭环流转，全面赋能食堂食安监管。

---

## 核心功能模块

### 1. 采购管理 (Procurement Management)
- **供应商准入与 AI 评估**：全生命周期的供应商资质审查与注销机制，AI 根据历史到货率、履约质量、价格稳定性对供应商进行智能综合评分，辅助筛选高风险供应商。
- **智能采购决策**：自动比对历史采购价、实时库存与供应商报价，AI 智能推荐采购量及推荐供应商。

### 2. 仓储管理 (Warehouse & Expiration Monitoring)
- **温湿度智能监测**：结合冷库/干货仓 IoT 传感器，实时监测异常。
- **智能出入库建议（FEFO 规则）**：入库/出库时 AI 依据“保质期临期先出 + 批次优先”生成分拣建议。
- **AI 物料需求预测**：结合历史消耗、菜谱计划与就餐人数，利用时间序列与机器学习模型，智能预测补货量。
- **AI 损耗分析**：识别因操作不当、临期或存储不当产生的食材损耗，生成优化建议。

### 3. 菜谱营养管理 (Recipe & Nutrition Optimization)
- **AI 智能推荐菜谱**：根据食材库存、成本预算、就餐偏好与特殊健康限制（如糖尿病、高血压等），自动搭配推荐菜谱方案。
- **AI 营养评估与膳食画像**：对儿童、老人、病人等不同人群进行膳食画像分析，自动计算蛋白质、脂肪、碳水、热量等营养成分，保证营养均衡。
- **烹饪参数预设**：自动为菜品匹配烹饪温度和时长建议（如：肉类中心温度必须达 70℃ 以上）。

### 4. 留样管理 (Food Sampling / Retention)
- **留样自动建账**：根据每日烹饪任务自动关联并下发留样任务，确立留样台账。
- **AI 质量评估**：结合图像识别对留样菜品的色泽、形态、熟度进行 AI 质量评分与出品建议。
- **过期销样提醒**：系统自动监控冷藏保存满 48 小时触发销样，保障全程合规。

### 5. 智能人脸晨检 (Smart Morning Health Check)
- **多维度合规核验**：支持后厨员工通过人脸识别设备进行身份核验。
- **健康状况评估**：晨检流程中自动检测体温、核验健康证有效期，并通过 AI 识别检测手部创伤或异常，未过关人员禁止上岗。

### 6. 烹饪过程监管 & AI 违规识别 (AI Camera & Violation Detection)
- **实时监控接入**：后厨多路监控视频流接入，支持画面回放。
- **AI 行为违规抓拍**：自动识别后厨人员未戴厨师帽/口罩/手套、吸烟、玩手机、生熟混放、未洗手操作、以及厨房鼠患迹象和动火离人等违规行为，实时截取违规视频并推送报警。
- **AI 效能与规范分析**：分析人员规范性与卫生评分，评估后厨效率并输出培训建议。

### 7. 设备与 IoT 管理 (IoT Devices Setup)
- **多源 IoT 设备绑定**：无缝对接监控摄像头、食材检测仪、气体监测器、温度传感器等。
- **烹饪温度曲线**：温度传感器绑定灶台，在点击“开始烹饪”时触发实时温度采集（每 30 秒采集一次），生成全周期的烹饪温度曲线。

---

## 技术架构

```
[Web/PC 管理端] (Vue 3 + TS + Element Plus)  --> 管理人员 (采购、库存、告警复核)
[Kitchen 厨师小屏端] (Vue 3 + Vite)          --> 厨师 (烹饪任务、温度曲线采集)
[Mobile 移动端/微信小程序] (Uni-app)          --> 员工/供应商 (入库、移动晨检)
                      |
                      v
            [API 网关 (Nginx / Nacos)]
                      |
                      v
      [Spring Cloud 微服务业务群]
 (Auth-service, Device-service, Procurement-service, Warehouse-service, etc.)
                      |
        +-------------+-------------+
        |                           |
        v                           v
  [数据层 (MySQL + Redis)]   [AI/YOLO 边端推理服务] (YOLOv8 + RTSP 视频分析)
```

- **后端**：Spring Boot / Spring Cloud (Nacos 服务注册与配置, Sentinel 限流) + MyBatis-Plus + MySQL + Redis
- **Web 前端**：Vue 3 + Vite + TypeScript + Pinia + Element Plus
- **厨房操作端**：定制化 KDS (Kitchen Display System) 布局前端，支持实时数据流与设备指令下发。
- **算法层**：基于 YOLO 系列的目标检测算法，实时分析后厨摄像头画面。

---

## 快速本地启动

### 1. 还原大文件数据集
为减小仓库体积，大型数据集已进行压缩。克隆仓库后，**必须首先**运行还原脚本：
```bash
./scripts/dev/restore-datasets.sh
```

### 2. 启动基础中间件
系统依赖 MySQL、Redis、Nacos。使用 `docker-compose` 在本地一键启动：
```bash
docker-compose up -d
```

### 3. 启动主微服务群及前端
在项目根目录下执行集成启动脚本：
```bash
./scripts/dev/start-all.sh --takeover
```
启动后默认访问：
- 前端管理系统：`http://localhost:5175`
- 厨房操作端：`http://localhost:5173` (根据具体本地端口而定)
- 默认账号/密码：`admin` / `admin`

### 4. 启动 YOLO 视频流服务 (用于联调后厨视频监控)
在视频识别算法目录下启动推理服务：
```bash
# 进入 YOLO 推理算法目录
cd path/to/camera-recognition
source .venv/bin/activate
python review_app.py --config config.yaml --host 127.0.0.1 --port 18081
```
启动后，可在管理平台的 `视频监管` 页面中查看 AI 抓拍和实时合规性分析。

---

## 开发与规范
完整的开发规范与排障说明，请参阅：
- [API 设计文档](doc/API设计文档-智慧厨房管理平台.md)
- [数据库设计文档](doc/数据库设计-智慧厨房管理平台-v1.0.md)
- [后厨端研发手册](doc/后厨端研发手册.md)
- [视频画面启动与排障手册](doc/视频监控管理-视频画面启动手册.md)
