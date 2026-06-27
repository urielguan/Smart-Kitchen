# Smart Canteen Food Safety Management Platform

[![GitHub Release](https://img.shields.io/badge/Release-v1.0.0-blue.svg)]()
[![Platform](https://img.shields.io/badge/Platform-Web%20%7C%20Kitchen%20Terminal%20%7C%20Mobile-orange.svg)]()
[![Technology](https://img.shields.io/badge/Stack-Spring%20Cloud%20%7C%20Vue%203%20%7C%20YOLO-green.svg)]()

This system is an end-to-end **Smart Canteen & Kitchen Food Safety Management Platform** that integrates **IoT (Internet of Things) devices** and **AI Computer Vision algorithms**. Rather than just simple video recognition, the platform integrates deeply into every phase of daily canteen operations. It establishes a closed-loop food safety workflow covering food procurement, warehouse inventory, nutritional meal planning, face-based morning health checks, cooking process supervision, food sample retention, and automated alert response.

---

## Core Modules

### 1. Procurement Management
- **Supplier Admission & AI Evaluation**: Full lifecycle supplier credentials vetting and cancellation workflows. The AI automatically grades suppliers based on historical delivery rates, fulfillment quality, and price stability to assist in identifying and replacing high-risk suppliers.
- **Smart Purchasing Decisions**: Automatically compares historical purchase prices, real-time inventory, and live supplier quotes to recommend optimal purchase quantities and target suppliers.

### 2. Warehouse & Expiration Monitoring
- **Smart Temperature & Humidity Monitoring**: Integrates IoT sensors inside cold storage and dry warehouses to detect environmental anomalies.
- **Intelligent Stock Pick Suggestions (FEFO Rule)**: Generates automated warehousing and dispatch advice using "First Expired, First Out" and batch priority logic.
- **AI Demand Forecasting**: Combines historical consumption patterns, recipe plans, and dining headcounts to predict future ingredient requirements using time-series and machine learning models.
- **AI Ingredient Loss Analysis**: Automatically flags high-loss ingredients, timeframes, and root causes (e.g., spoilage, improper storage) to minimize food waste.

### 3. Recipe & Nutrition Management
- **AI Menu Recommendations**: Automatically designs daily/weekly/monthly meal schedules balanced for nutrition, cost budgets, customer preferences, and specific health profiles (e.g., diabetes, hypertension).
- **AI Dietary Profiles & Evaluation**: Conducts nutritional dietary analysis for target groups (e.g., children, elderly, patients), calculates macro distributions (protein, fats, carbs, calories), and offers optimization tips.
- **Cooking Parameters Preset**: Links recipes to strict safety parameters (e.g., core cooking temperature for meat must exceed 70°C).

### 4. Food Sample Retention
- **Automatic Retention Logs**: Instantly generates retention task logs linked to daily cooking records for complete traceability.
- **AI Quality Assessment**: Uses image recognition to grade cooked dishes based on color, texture, and doneness, giving automated feedback.
- **48-Hour Expiration Warnings**: Automatically flags and reminds staff to discard food samples after 48 hours of cold storage.

### 5. Smart Morning Health Checks
- **Face & Identity Verification**: Allows kitchen staff to perform check-ins via facial recognition terminals.
- **Compliance Vetting**: Validates body temperature, checks health certificate validity, and uses computer vision to detect hand wounds or skin infections, blocking non-compliant staff from starting their shifts.

### 6. Video Supervision & AI Violation Capture
- **Real-Time Video Feeds**: Connects to multiple IP camera streams with support for recording and playback.
- **AI Behavior Violations**: Automatically detects kitchen non-compliance, such as missing chef hats/masks/gloves, smoking, mobile phone usage, raw/cooked cross-contamination, unwashed hands, unattended stoves, or pest/rodent presence. Captures video clips and raises instant alerts.
- **Staff Behavioral & Efficiency Reports**: Scores staff hygiene and operational compliance to generate training and efficiency recommendations.

### 7. IoT & Device Management
- **Multi-Device Pairing**: Connects cameras, food testing kits, gas monitors, and temperature sensors.
- **Real-Time Cooking Curves**: Links temperature sensors to cooking burners. Clicking "Start Cooking" triggers real-time temperature collection (every 30 seconds) to plot cooking curves for safety audits.

---

## Technical Architecture

```
[Web Admin Portal] (Vue 3 + TS + Element Plus)  --> Administrative Staff (Procurement, Inventory, Alerts)
[Kitchen KDS Terminal] (Vue 3 + Vite)            --> Kitchen Staff (Cooking Tasks, Temperature Curves)
[Mobile App / Mini-Program] (Uni-app)           --> Handheld Operations (Inbound/Outbound, Mobile Health Checks)
                         |
                         v
             [API Gateway (Nginx / Nacos)]
                         |
                         v
       [Spring Cloud Microservices Cluster]
 (Auth-service, Device-service, Procurement-service, Warehouse-service, etc.)
                         |
           +-------------+-------------+
           |                           |
           v                           v
     [Database Layer (MySQL + Redis)] [Edge AI Inference Service] (YOLOv8 + RTSP Video Stream)
```

- **Backend**: Spring Boot / Spring Cloud (Nacos Registry & Configuration, Sentinel) + MyBatis-Plus + MySQL + Redis.
- **Web Frontend**: Vue 3 + Vite + TypeScript + Pinia + Element Plus.
- **Kitchen Terminal**: Specialized Kitchen Display System (KDS) layout with real-time SSE/WebSocket data updates.
- **AI Inference**: YOLOv8-based model execution on RTSP video feeds.

---

## Quick Start (Local Setup)

### 1. Restore Dataset Files
To keep the git repository lightweight, raw dataset JSON files under `doc/` have been compressed. **You must run the restore script first** after cloning the project:
```bash
./scripts/dev/restore-datasets.sh
```

### 2. Start Middleware
The platform requires MySQL, Redis, and Nacos. Spin them up using `docker-compose`:
```bash
docker-compose up -d
```

### 3. Launch Services and Frontend
Execute the unified startup script in the root directory:
```bash
./scripts/dev/start-all.sh --takeover
```
Default local access URLs:
- Web Admin Portal: `http://localhost:5175`
- Default Credentials: `admin` / `admin`

### 4. Run YOLO Inference Service (For Video Monitoring)
Navigate to your camera recognition algorithms directory:
```bash
cd path/to/camera-recognition
source .venv/bin/activate
python review_app.py --config config.yaml --host 127.0.0.1 --port 18081
```
Verify that the `http://127.0.0.1:18081/annotated.mjpg` stream is active to view AI bounding boxes in the Admin Portal under "Video Monitoring".

---

## Development Specifications
For database schemas, API specs, and technical details, refer to:
- [API Design Documentation](doc/API设计文档-智慧厨房管理平台.md)
- [Database Design Schema](doc/数据库设计-智慧厨房管理平台-v1.0.md)
- [Kitchen App Developer Guide](doc/后厨端研发手册.md)
- [Video Stream Setup & Troubleshooting Guide](doc/视频监控管理-视频画面启动手册.md)
