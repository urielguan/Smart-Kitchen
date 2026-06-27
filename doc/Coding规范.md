
## 技术栈详情

### 后端技术栈
- **框架**: Spring Boot 3.2 + Spring Cloud 2023 + Spring Cloud Alibaba 2023
- **服务注册**: Nacos
- **API 网关**: Spring Cloud Gateway
- **ORM**: MyBatis Plus
- **数据库**: MySQL 8.0
- **认证**: JWT
- **工具库**: Hutool, Lombok

### 前端技术栈
- **框架**: Vue 3 + Vite
- **状态管理**: Pinia
- **UI 组件**: Element Plus
- **HTTP 客户端**: Axios

## 构建与运行

### Docker Compose 部署（推荐方式）
```bash
# 1. 启动所有服务
docker compose up -d

# 2. 查看服务日志
docker compose logs -f

# 3. 停止服务
docker compose down
```


### 本地开发环境搭建

#### 环境要求
- JDK 17+
- Maven 3.8+
- Node.js 18+
- MySQL 8.0+
- Nacos 2.x (可选，用于服务发现)

## 开发规范

### 后端开发规范
- 使用 Spring Boot 3.2 和 Java 17
- 遵循 RESTful API 设计规范
- 使用 JWT 进行身份验证
- 使用 Lombok 减少样板代码
- 使用 Hutool 工具库简化开发

### 前端开发规范
- 使用 Vue 3 Composition API
- 使用 Pinia 进行状态管理
- 使用 Element Plus 组件库
- 使用 Three.js 进行 3D 渲染
- 使用 SCSS 进行样式编写

### 微服务架构规范
- 服务间通过 HTTP API 进行通信
- 使用 Nacos 进行服务注册与发现
- 使用 Spring Cloud Gateway 作为 API 网关
- 各服务独立部署，共享数据库

## 部署配置说明

### 环境变量配置
-项目使用 `.env` 文件进行环境配置，主要包含：

### Nacos 配置
-NACOS_SERVER_ADDR=192.168.31.4:8848
-NACOS_USERNAME=nacos
-NACOS_PASSWORD=nacos

### Java 配置
-JAVA_OPTS=-Xms256m -Xmx512m -XX:+UseG1GC


### Docker 部署说明
- 使用 docker-compose.yml 统一管理所有服务
- 每个服务都有独立的 Dockerfile
- 包含健康检查机制确保服务正常运行
- 使用 nginx 作为前端静态资源服务器

