# 智慧厨房管理平台 - 后端服务

## 项目结构

```
backend/
├── pom.xml                    # 父 POM
├── common/                    # 公共模块
│   └── src/main/java/com/yingzi/smartkitchen/common/
│       ├── constant/          # 常量定义
│       ├── dto/               # 数据传输对象
│       ├── exception/         # 异常处理
│       ├── util/              # 工具类
│       └── aspect/            # AOP 切面
├── auth-service/              # 认证服务 (:8081)
├── sys-service/               # 系统服务 (:8089)
├── scm-service/               # 供应链服务 (:8082)
├── wms-service/               # 仓储服务 (:8083)
├── recipe-service/            # 菜谱服务 (:8084)
├── cook-service/              # 烹饪服务 (:8085)
├── sample-service/            # 留样服务 (:8086)
├── health-service/            # 健康服务 (:8087)
└── device-service/            # 设备服务 (:8088)
```

## 技术栈

- Spring Boot 3.2.5
- Spring Cloud 2023.0.1
- Spring Cloud Alibaba 2022.0.0.0
- MyBatis-Plus 3.5.7
- JWT (jjwt 0.12.5)
- MySQL 8.0
- Nacos (服务注册/配置中心)
- Redis (缓存)

## 快速开始

### 前置要求
- JDK 17+
- Maven 3.8+
- 可访问的 Nacos 服务
- Docker & Docker Compose (可选)

### Nacos 本地联调约定
- 本地联调统一使用 namespace `test_gyr`
- namespace ID：`16d939fd-4033-4ef3-9241-6b15a51f8ad8`
- 启动前请准备以下环境变量：`NACOS_SERVER_ADDR`、`NACOS_NAMESPACE`、`NACOS_GROUP`、`NACOS_USERNAME`、`NACOS_PASSWORD`
- 每个服务会通过 `bootstrap.yml` 中的 `prefix` 去 Nacos 读取对应配置，例如 `auth-service` 对应 `smartfood_auth_service.yaml`

### 本地 bootstrap 配置协作方式
- 仓库提交 `bootstrap.yml.example` 作为模板文件
- 本地运行时把 `bootstrap.yml.example` 复制为 `bootstrap.yml`
- `bootstrap.yml` 仅供本地联调使用，已加入 `.gitignore`，不会再提交到 git

```bash
# 以 auth-service 为例
cp auth-service/src/main/resources/bootstrap.yml.example auth-service/src/main/resources/bootstrap.yml
```

### 编译项目
```bash
cd backend
mvn clean install -DskipTests
```

### 启动服务
```bash
# 先配置当前 shell 的 Nacos 环境变量
export NACOS_SERVER_ADDR=172.31.25.155:8848
export NACOS_NAMESPACE=16d939fd-4033-4ef3-9241-6b15a51f8ad8
export NACOS_GROUP=DEFAULT_GROUP
export NACOS_USERNAME=nacos
export NACOS_PASSWORD=nacos

# 推荐先启动认证和系统服务
cd auth-service
mvn spring-boot:run

cd ../sys-service
mvn spring-boot:run

# 再按需启动业务服务
cd ../scm-service
mvn spring-boot:run
```

## 服务端口

| 服务 | 端口 | 说明 |
|------|------|------|
| auth-service | 8081 | 认证授权服务 |
| sys-service | 8089 | 系统基础服务 |
| scm-service | 8082 | 供应链管理 |
| wms-service | 8083 | 仓储管理 |
| recipe-service | 8084 | 菜谱营养 |
| cook-service | 8085 | 烹饪记录 |
| sample-service | 8086 | 留样管理 |
| health-service | 8087 | 健康晨检 |
| device-service | 8088 | 设备管理 |

## 开发规范

参考 `../doc/Coding规范.md`
