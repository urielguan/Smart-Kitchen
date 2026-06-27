# API设计-智慧厨房管理平台-v1.0

## 1. 文档信息

| 项目 | 内容 |
|---|---|
| 文档名称 | 智慧厨房管理平台 API 设计文档 |
| 版本 | v1.0 |
| 技术栈 | Spring Boot 3.2 + Spring Cloud + MySQL 8.0 + Vue3 + JWT |
| 认证方式 | JWT Bearer Token |
| 适用范围 | 前后端联调、微服务间调用、测试验收 |

---

## 2. 总体规范

### 2.1 协议与编码
- 协议：`HTTPS`
- 数据格式：`application/json; charset=utf-8`
- 字符编码：`UTF-8`
- 时间格式：`yyyy-MM-dd HH:mm:ss`（业务展示）/ UTC时间戳（存储）
- 分页参数：`pageNum`（从1开始）、`pageSize`

### 2.2 API版本与路径规范
- 统一前缀：`/api/v1`
- 服务前缀：
  - 认证服务：`/api/v1/auth/**`
  - 组织与系统服务：`/api/v1/sys/**`
  - 采购服务：`/api/v1/scm/**`
  - 仓储服务：`/api/v1/wms/**`
  - 菜谱服务：`/api/v1/recipe/**`
  - 烹饪服务：`/api/v1/cook/**`
  - 留样服务：`/api/v1/sample/**`
  - 健康晨检服务：`/api/v1/health/**`
  - 设备与告警服务：`/api/v1/device/**`

### 2.3 统一响应体格式

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": {},
  "traceId": "9f1f9d4e8d6d4c0d9f5d28f1a2b0c123",
  "timestamp": "2026-03-16 18:00:00"
}
```

| 字段名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| code | String | 业务状态码；成功固定为 `SUCCESS`，失败时返回约定错误码 | `SUCCESS` |
| message | String | 人类可读提示信息；用于前端展示或日志排查 | `操作成功` |
| data | Object \| Array \| String \| Number \| Boolean \| null | 业务数据载体 | `{}` |
| traceId | String | 链路追踪ID；用于跨网关、微服务、日志平台的全链路排查 | `9f1f9d4e8d6d4c0d9f5d28f1a2b0c123` |
| timestamp | String | 服务端响应时间，统一格式 `yyyy-MM-dd HH:mm:ss` | `2026-03-16 18:00:00` |

### 2.4 统一分页响应

```json
{
  "code": "SUCCESS",
  "message": "查询成功",
  "data": {
    "list": [],
    "pageNum": 1,
    "pageSize": 20,
    "total": 128,
    "totalPages": 7
  },
  "traceId": "xxx",
  "timestamp": "2026-03-16 18:00:00"
}
```

### 2.5 统一错误码（节选）

| code | HTTP状态码 | 说明 |
|---|---:|---|
| SUCCESS | 200 | 成功 |
| BAD_REQUEST | 400 | 参数错误 |
| UNAUTHORIZED | 401 | 未登录或Token无效 |
| FORBIDDEN | 403 | 无权限 |
| NOT_FOUND | 404 | 资源不存在 |
| CONFLICT | 409 | 状态冲突/唯一约束冲突 |
| VALIDATION_FAILED | 422 | 业务校验失败 |
| INTERNAL_ERROR | 500 | 系统异常 |
| TOKEN_EXPIRED | 401 | Token已过期 |
| DATA_SCOPE_DENIED | 403 | 数据权限不足 |

---

## 3. JWT认证与鉴权

### 3.1 登录获取Token
- Header：无
- 登录成功后返回：
  - `accessToken`
  - `refreshToken`
  - `expiresIn`

### 3.2 鉴权头
```http
Authorization: Bearer <accessToken>
```

### 3.3 刷新Token
- 使用 `refreshToken` 调用刷新接口换取新 `accessToken`
- 旧Token按策略加入黑名单（`auth_token.is_revoked=1`）

### 3.4 权限模型
- 功能权限：RBAC（角色-权限点）
- 数据权限：组织范围（`org_id`）+ 租户范围（`tenant_id`）
- 接口层强制校验：角色权限 + 数据权限

---

## 4. 微服务拆分与网关路由

### 4.1 网关统一入口
- 统一入口：`/api/v1/**`
- 网关职责：鉴权、限流、灰度、路由、审计日志注入

### 4.2 路由示例
- `/api/v1/scm/**` -> `scm-service`
- `/api/v1/wms/**` -> `wms-service`
- `/api/v1/recipe/**` -> `recipe-service`

### 4.3 跨服务调用原则
- 读场景：优先API聚合（避免跨服务DB强耦合）
- 写场景：主责服务写入，事件异步同步冗余字段
- 链路字段：必须携带 `traceId`、`tenantId`、`orgId`

---

## 5. 核心接口定义

---

### 5.1 认证授权模块

#### 用户登录
- **接口路径**：`/api/v1/auth/login`
- **请求方法**：POST
- **请求头**：Content-Type: application/json
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| username | String | 是 | 用户名/手机号/邮箱 | admin | 登录方式 |
| password | String | 是 | 密码（8-20位，含大小写字母+数字+特殊符号） | Admin@123 | 密码要求 |
| loginType | String | 否 | 登录类型：password=密码登录，sso=单点登录，qrcode=扫码登录 | password | 登录方式 |
| deviceId | String | 否 | 设备唯一标识（用于多设备管理） | web_chrome_123 | 多端登录 |
| deviceType | String | 否 | 设备类型：web/mobile/tablet/terminal | web | 多端登录 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| userId | Long | 用户ID | 1 |
| username | String | 用户名 | admin |
| realName | String | 真实姓名 | 系统管理员 |
| avatarUrl | String | 头像URL | https://xxx.com/avatar.jpg |
| orgId | Long | 所属组织ID | 1001 |
| orgName | String | 所属组织名称 | 一号食堂 |
| tenantId | Long | 租户ID | 1 |
| roles | Array | 角色列表 | ["admin"] |
| permissions | Array | 权限编码列表 | ["scm:supplier:create"] |
| accessToken | String | JWT访问令牌 | eyJhbGciOiJIUzI1NiJ9... |
| refreshToken | String | JWT刷新令牌 | eyJhbGciOiJIUzI1NiJ9... |
| expiresIn | Long | 访问令牌有效期（秒） | 7200 |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：用户名不能为空"
  - [401]：用户名或密码错误 → msg："用户名或密码错误"
  - [401]：账号已被锁定 → msg："账号已被锁定，请15分钟后重试"
  - [403]：账号已被禁用 → msg："账号已被禁用，请联系管理员"
  - [429]：登录频率过高 → msg："登录频率过高，请稍后再试"

---

#### 刷新Token
- **接口路径**：`/api/v1/auth/token/refresh`
- **请求方法**：POST
- **请求头**：Content-Type: application/json
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| refreshToken | String | 是 | 刷新令牌 | eyJhbGciOiJIUzI1NiJ9... | Token刷新 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| accessToken | String | 新的访问令牌 | eyJhbGciOiJIUzI1NiJ9... |
| refreshToken | String | 新的刷新令牌 | eyJhbGciOiJIUzI1NiJ9... |
| expiresIn | Long | 访问令牌有效期（秒） | 7200 |

- **异常场景**：
  - [401]：刷新令牌无效 → msg："刷新令牌无效或已过期"
  - [401]：刷新令牌已被撤销 → msg："刷新令牌已被撤销"

---

#### 用户登出
- **接口路径**：`/api/v1/auth/logout`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：无

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| - | - | 无返回数据 | null |

- **异常场景**：
  - [401]：Token无效 → msg："未登录或Token无效"

---

#### 查询当前用户信息
- **接口路径**：`/api/v1/auth/me`
- **请求方法**：GET
- **请求头**：Authorization: Bearer {token}
- **请求参数**：无

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| userId | Long | 用户ID | 1 |
| username | String | 用户名 | admin |
| realName | String | 真实姓名 | 系统管理员 |
| avatarUrl | String | 头像URL | https://xxx.com/avatar.jpg |
| email | String | 邮箱（脱敏） | a***@example.com |
| phone | String | 手机号（脱敏） | 138****8888 |
| orgId | Long | 所属组织ID | 1001 |
| orgName | String | 所属组织名称 | 一号食堂 |
| orgPath | String | 组织路径 | /集团/分公司/食堂 |
| tenantId | Long | 租户ID | 1 |
| roles | Array | 角色列表 | [{"roleCode":"admin","roleName":"系统管理员"}] |
| permissions | Array | 权限编码列表 | ["scm:supplier:create","scm:supplier:edit"] |
| lastLoginAt | String | 最后登录时间 | 2026-03-16 18:00:00 |

- **异常场景**：
  - [401]：Token无效 → msg："未登录或Token无效"

---

#### 修改密码
- **接口路径**：`/api/v1/auth/password`
- **请求方法**：PUT
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| oldPassword | String | 是 | 原密码 | OldPass@123 | 密码修改 |
| newPassword | String | 是 | 新密码（8-20位，含大小写字母+数字+特殊符号） | NewPass@456 | 密码复杂度 |
| confirmPassword | String | 是 | 确认新密码 | NewPass@456 | 密码确认 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| - | - | 无返回数据 | null |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：新密码格式不符合要求"
  - [400]：两次密码不一致 → msg："两次输入的密码不一致"
  - [401]：原密码错误 → msg："原密码错误"
  - [422]：新密码与原密码相同 → msg："新密码不能与原密码相同"

---

### 5.2 组织与员工模块

#### 组织列表
- **接口路径**：`/api/v1/sys/organizations`
- **请求方法**：GET
- **请求头**：Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| pageNum | Integer | 否 | 页码（默认1） | 1 | 分页查询 |
| pageSize | Integer | 否 | 每页条数（默认20，最大100） | 20 | 分页查询 |
| orgName | String | 否 | 组织名称（模糊搜索） | 食堂 | 组织列表 |
| orgCode | String | 否 | 组织编码 | CANTEEN_001 | 组织列表 |
| orgType | String | 否 | 组织类型：group/company/canteen/dept | canteen | 组织类型 |
| status | String | 否 | 状态：active/inactive | active | 组织状态 |
| parentId | Long | 否 | 父组织ID（获取子组织列表） | 10 | 组织树 |
| treeMode | Boolean | 否 | 是否返回树形结构（默认false） | true | 组织树 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| list | Array | 组织列表 | - |
| └ id | Long | 组织ID | 1001 |
| └ orgCode | String | 组织编码 | CANTEEN_001 |
| └ orgName | String | 组织名称 | 一号食堂 |
| └ orgType | String | 组织类型 | canteen |
| └ parentId | Long | 父组织ID | 10 |
| └ parentName | String | 父组织名称 | XX分公司 |
| └ level | Integer | 组织层级 | 3 |
| └ leaderId | Long | 负责人ID | 101 |
| └ leaderName | String | 负责人姓名 | 张三 |
| └ contactPhone | String | 联系电话 | 13800138000 |
| └ memberCount | Integer | 成员数量 | 25 |
| └ status | String | 状态 | active |
| └ children | Array | 子组织列表（treeMode=true时返回） | - |
| pageNum | Integer | 当前页码 | 1 |
| pageSize | Integer | 每页条数 | 20 |
| total | Long | 总记录数 | 50 |
| totalPages | Integer | 总页数 | 3 |

- **异常场景**：
  - [403]：无数据权限 → msg："无权限访问该组织数据"

---

#### 新增组织
- **接口路径**：`/api/v1/sys/organizations`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| orgCode | String | 是 | 组织编码（唯一） | CANTEEN_001 | 组织编码 |
| orgName | String | 是 | 组织名称（1-100字符） | 一号食堂 | 组织名称 |
| orgType | String | 是 | 组织类型：group/company/canteen/dept | canteen | 组织类型 |
| parentId | Long | 否 | 父组织ID（不传则为顶级组织） | 10 | 组织层级 |
| leaderId | Long | 否 | 负责人ID | 101 | 组织负责人 |
| contactPhone | String | 否 | 联系电话 | 13800138000 | 联系方式 |
| address | String | 否 | 地址 | 北京市朝阳区XX路XX号 | 地址 |
| status | String | 否 | 状态（默认active） | active | 组织状态 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 新增组织ID | 1001 |
| orgCode | String | 组织编码 | CANTEEN_001 |
| orgName | String | 组织名称 | 一号食堂 |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：组织名称不能为空"
  - [409]：组织编码已存在 → msg："组织编码已存在"
  - [403]：无创建权限 → msg："无权限创建组织"

---

#### 编辑组织
- **接口路径**：`/api/v1/sys/organizations/{id}`
- **请求方法**：PUT
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| orgName | String | 否 | 组织名称 | 一号食堂 | 组织名称 |
| parentId | Long | 否 | 父组织ID | 10 | 组织层级 |
| leaderId | Long | 否 | 负责人ID | 101 | 组织负责人 |
| contactPhone | String | 否 | 联系电话 | 13800138000 | 联系方式 |
| address | String | 否 | 地址 | 北京市朝阳区XX路XX号 | 地址 |
| status | String | 否 | 状态：active/inactive | active | 组织状态 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 组织ID | 1001 |
| orgName | String | 组织名称 | 一号食堂 |

- **异常场景**：
  - [404]：组织不存在 → msg："组织不存在"
  - [409]：组织名称重复 → msg："组织名称已存在"
  - [403]：无编辑权限 → msg："无权限编辑该组织"

---

#### 删除组织
- **接口路径**：`/api/v1/sys/organizations/{id}`
- **请求方法**：DELETE
- **请求头**：Authorization: Bearer {token}
- **请求参数**：无

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| - | - | 无返回数据 | null |

- **异常场景**：
  - [404]：组织不存在 → msg："组织不存在"
  - [422]：组织下存在成员 → msg："组织下存在成员，无法删除"
  - [422]：组织下存在子组织 → msg："组织下存在子组织，无法删除"
  - [403]：无删除权限 → msg："无权限删除该组织"

---

#### 组织树
- **接口路径**：`/api/v1/sys/organizations/tree`
- **请求方法**：GET
- **请求头**：Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| orgType | String | 否 | 组织类型过滤 | canteen | 组织树展示 |
| status | String | 否 | 状态过滤 | active | 组织状态 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| list | Array | 组织树列表 | - |
| └ id | Long | 组织ID | 1001 |
| └ orgCode | String | 组织编码 | CANTEEN_001 |
| └ orgName | String | 组织名称 | 一号食堂 |
| └ orgType | String | 组织类型 | canteen |
| └ children | Array | 子组织列表 | - |

- **异常场景**：
  - [403]：无数据权限 → msg："无权限访问组织数据"

---

#### 员工列表
- **接口路径**：`/api/v1/sys/employees`
- **请求方法**：GET
- **请求头**：Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| pageNum | Integer | 否 | 页码（默认1） | 1 | 分页查询 |
| pageSize | Integer | 否 | 每页条数（默认20，最大100） | 20 | 分页查询 |
| realName | String | 否 | 员工姓名（模糊搜索） | 张 | 员工列表 |
| employeeNo | String | 否 | 员工工号 | EMP001 | 员工列表 |
| orgId | Long | 否 | 所属组织ID | 1001 | 组织筛选 |
| status | String | 否 | 员工状态：active/left | active | 员工状态 |
| healthCertStatus | String | 否 | 健康证状态：pending/valid/expiring/expired | valid | 健康证状态 |
| faceEnrolled | Boolean | 否 | 人脸录入状态 | true | 人脸录入状态 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| list | Array | 员工列表 | - |
| └ id | Long | 员工ID | 10001 |
| └ employeeNo | String | 员工工号 | EMP001 |
| └ realName | String | 真实姓名 | 张三 |
| └ gender | Integer | 性别：0=未知，1=男，2=女 | 1 |
| └ age | Integer | 年龄 | 35 |
| └ phone | String | 手机号（脱敏） | 138****8888 |
| └ email | String | 邮箱（脱敏） | z***@example.com |
| └ orgId | Long | 所属组织ID | 1001 |
| └ orgName | String | 所属组织名称 | 一号食堂 |
| └ department | String | 部门 | 后厨部 |
| └ position | String | 岗位 | 厨师 |
| └ roleNames | String | 拥有角色（逗号分隔） | 厨师,食品安全员 |
| └ faceEnrolled | Boolean | 人脸是否已录入 | true |
| └ healthCertStatus | String | 健康证状态 | valid |
| └ healthCertExpiry | String | 健康证到期日期 | 2026-12-31 |
| └ status | String | 员工状态 | active |
| └ avatarUrl | String | 头像URL | https://xxx.com/avatar.jpg |
| pageNum | Integer | 当前页码 | 1 |
| pageSize | Integer | 每页条数 | 20 |
| total | Long | 总记录数 | 100 |
| totalPages | Integer | 总页数 | 5 |

- **异常场景**：
  - [403]：无数据权限 → msg："无权限访问该组织员工数据"

---

#### 新增员工
- **接口路径**：`/api/v1/sys/employees`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| employeeNo | String | 是 | 员工工号（唯一） | EMP001 | 员工工号 |
| realName | String | 是 | 真实姓名 | 张三 | 员工姓名 |
| gender | Integer | 否 | 性别：0=未知，1=男，2=女 | 1 | 性别 |
| birthDate | String | 否 | 出生日期 | 1990-01-15 | 出生日期 |
| phone | String | 否 | 手机号 | 13800138000 | 联系方式 |
| email | String | 否 | 邮箱 | zhangsan@example.com | 邮箱 |
| orgId | Long | 是 | 所属组织ID | 1001 | 所属组织 |
| department | String | 否 | 部门 | 后厨部 | 部门 |
| position | String | 否 | 岗位 | 厨师 | 岗位 |
| hireDate | String | 否 | 入职日期 | 2026-03-01 | 入职日期 |
| roleIds | Array | 否 | 角色ID列表 | [1, 2] | 角色权限 |
| status | String | 否 | 员工状态（默认active） | active | 员工状态 |
| createAccount | Boolean | 否 | 是否同时创建登录账号（默认true） | true | 创建账号 |
| username | String | 否 | 登录用户名（createAccount=true时必填） | zhangsan | 用户名 |
| initialPassword | String | 否 | 初始密码（createAccount=true时必填） | Pass@123 | 初始密码 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 员工ID | 10001 |
| employeeNo | String | 员工工号 | EMP001 |
| realName | String | 真实姓名 | 张三 |
| userId | Long | 关联用户ID（createAccount=true时返回） | 2001 |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：员工工号不能为空"
  - [409]：员工工号已存在 → msg："员工工号已存在"
  - [409]：手机号已被使用 → msg："手机号已被其他员工使用"
  - [403]：无创建权限 → msg："无权限创建员工"

---

#### 编辑员工
- **接口路径**：`/api/v1/sys/employees/{id}`
- **请求方法**：PUT
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| realName | String | 否 | 真实姓名 | 张三 | 员工姓名 |
| gender | Integer | 否 | 性别 | 1 | 性别 |
| birthDate | String | 否 | 出生日期 | 1990-01-15 | 出生日期 |
| phone | String | 否 | 手机号 | 13800138000 | 联系方式 |
| email | String | 否 | 邮箱 | zhangsan@example.com | 邮箱 |
| orgId | Long | 否 | 所属组织ID | 1001 | 所属组织 |
| department | String | 否 | 部门 | 后厨部 | 部门 |
| position | String | 否 | 岗位 | 厨师长 | 岗位 |
| roleIds | Array | 否 | 角色ID列表 | [1, 2, 3] | 角色权限 |
| status | String | 否 | 员工状态：active/left | active | 员工状态 |
| leaveDate | String | 否 | 离职日期（status=left时必填） | 2026-03-15 | 离职日期 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 员工ID | 10001 |
| realName | String | 真实姓名 | 张三 |

- **异常场景**：
  - [404]：员工不存在 → msg："员工不存在"
  - [403]：无编辑权限 → msg："无权限编辑该员工"

---

### 5.3 采购管理模块

#### 供应商列表
- **接口路径**：`/api/v1/scm/suppliers`
- **请求方法**：GET
- **请求头**：Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| pageNum | Integer | 否 | 页码（默认1） | 1 | 分页查询 |
| pageSize | Integer | 否 | 每页条数（默认20，最大100） | 20 | 分页查询 |
| supplierName | String | 否 | 供应商名称（模糊搜索） | 绿色农场 | 供应商列表 |
| supplierCode | String | 否 | 供应商编码 | SUP001 | 供应商列表 |
| status | String | 否 | 供应商状态：pending/active/rejected/disabled/cancelled | active | 供应商状态 |
| categoryTag | String | 否 | 商品品类标签 | 蔬菜 | 品类筛选 |
| licenseExpiring | Boolean | 否 | 是否资质即将过期（30天内） | true | 资质预警 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| list | Array | 供应商列表 | - |
| └ id | Long | 供应商ID | 1 |
| └ supplierCode | String | 供应商编码 | SUP001 |
| └ supplierName | String | 供应商名称 | 绿色农产品供应商 |
| └ contactName | String | 联系人 | 张经理 |
| └ contactPhone | String | 联系电话 | 13800138000 |
| └ address | String | 供应商地址 | 北京市朝阳区XX路XX号 |
| └ categoryTags | Array | 商品品类列表 | ["蔬菜","水果"] |
| └ status | String | 供应商状态 | active |
| └ licenseExpiresAt | String | 资质到期日期 | 2026-12-31 |
| └ categoryCount | Integer | 商品品类数 | 5 |
| └ creditScore | Decimal | 信用评分 | 95.50 |
| └ createdAt | String | 创建时间 | 2026-03-16 10:00:00 |
| pageNum | Integer | 当前页码 | 1 |
| pageSize | Integer | 每页条数 | 20 |
| total | Long | 总记录数 | 50 |
| totalPages | Integer | 总页数 | 3 |

- **异常场景**：
  - [403]：无数据权限 → msg："无权限访问供应商数据"

---

#### 供应商详情
- **接口路径**：`/api/v1/scm/suppliers/{id}`
- **请求方法**：GET
- **请求头**：Authorization: Bearer {token}
- **请求参数**：无

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 供应商ID | 1 |
| supplierCode | String | 供应商编码 | SUP001 |
| supplierName | String | 供应商名称 | 绿色农产品供应商 |
| supplierType | String | 供应商类型 | enterprise |
| contactName | String | 联系人姓名 | 张经理 |
| contactPhone | String | 联系电话 | 13800138000 |
| contactEmail | String | 联系邮箱 | zhang@example.com |
| province | String | 省份 | 北京市 |
| city | String | 城市 | 北京市 |
| district | String | 区县 | 朝阳区 |
| address | String | 详细地址 | XX路XX号 |
| categoryTags | Array | 商品品类列表 | ["蔬菜","水果"] |
| supplierLevel | String | 供应商等级 | A |
| licenseNo | String | 营业执照编号 | 91110100XXXX |
| licenseExpiresAt | String | 营业执照到期日 | 2026-12-31 |
| foodLicenseNo | String | 食品许可证编号 | JY123456789 |
| foodLicenseExpiresAt | String | 食品许可证到期日 | 2026-06-30 |
| qualificationFiles | Array | 资质文件列表 | ["https://xxx.com/file1.pdf"] |
| creditScore | Decimal | AI智能综合评分 | 95.50 |
| aiScoreDetail | Object | AI评分维度详情 | - |
| └ qualificationScore | Decimal | 资质完整性分数 | 100.00 |
| └ qualityScore | Decimal | 历史供货质量分数 | 90.00 |
| └ priceStabilityScore | Decimal | 价格稳定性分数 | 95.00 |
| └ deliveryScore | Decimal | 履约准时率分数 | 92.00 |
| manualScoreDetail | Object | 人工评分详情 | - |
| └ serviceScore | Decimal | 服务态度分数 | 90.00 |
| └ qualityManualScore | Decimal | 供货质量分数 | 88.00 |
| └ priceReasonableScore | Decimal | 价格合理性分数 | 85.00 |
| └ timelinessScore | Decimal | 交货及时性分数 | 92.00 |
| └ avgScore | Decimal | 人工评分平均分 | 88.75 |
| categories | Array | 供应商商品类 | - |
| └ categoryName | String | 商品类别名称 | 蔬菜类 |
| └ productCount | Integer | 该品类下商品数 | 15 |
| status | String | 供应商状态 | active |
| auditBy | Long | 审核人ID | 101 |
| auditByName | String | 审核人姓名 | 管理员 |
| auditAt | String | 审核时间 | 2026-03-16 10:00:00 |
| auditRemark | String | 审核备注 | 资质齐全 |

- **异常场景**：
  - [404]：供应商不存在 → msg："供应商不存在"
  - [403]：无数据权限 → msg："无权限访问该供应商数据"

---

#### 新增供应商
- **接口路径**：`/api/v1/scm/suppliers`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| supplierCode | String | 是 | 供应商编码（唯一） | SUP001 | 供应商编码 |
| supplierName | String | 是 | 供应商名称（1-100字符） | 绿色农产品供应商 | 供应商名称 |
| supplierType | String | 是 | 供应商类型：enterprise/individual | enterprise | 供应商类型 |
| province | String | 是 | 省份 | 北京市 | 省份 |
| city | String | 是 | 城市 | 北京市 | 城市 |
| district | String | 是 | 区县 | 朝阳区 | 区县 |
| address | String | 是 | 详细地址 | XX路XX号 | 详细地址 |
| contactName | String | 是 | 联系人姓名 | 张经理 | 联系人 |
| contactPhone | String | 是 | 联系人电话 | 13800138000 | 联系方式 |
| contactEmail | String | 否 | 联系人邮箱 | zhang@example.com | 联系方式 |
| categoryTags | Array | 是 | 商品类别标签 | ["蔬菜","水果"] | 商品类别 |
| supplierLevel | String | 否 | 供应商等级：A/B/C/D（默认C） | A | 供应商等级 |
| licenseNo | String | 是 | 营业执照编号 | 91110100XXXX | 资质信息 |
| licenseExpiresAt | String | 是 | 营业执照到期日期 | 2026-12-31 | 资质信息 |
| foodLicenseNo | String | 否 | 食品经营许可证编号 | JY123456789 | 资质信息 |
| foodLicenseExpiresAt | String | 否 | 食品许可证到期日期 | 2026-06-30 | 资质信息 |
| qualificationFiles | Array | 否 | 资质文件URL列表 | ["https://xxx.com/file1.pdf"] | 资质文件 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 新增供应商ID | 1 |
| supplierCode | String | 供应商编码 | SUP001 |
| supplierName | String | 供应商名称 | 绿色农产品供应商 |
| status | String | 供应商状态 | pending |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：供应商名称不能为空"
  - [409]：供应商编码已存在 → msg："供应商编码已存在"
  - [403]：无创建权限 → msg："无权限创建供应商"

---

#### 编辑供应商
- **接口路径**：`/api/v1/scm/suppliers/{id}`
- **请求方法**：PUT
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| supplierName | String | 否 | 供应商名称 | 绿色农产品供应商 | 供应商名称 |
| supplierType | String | 否 | 供应商类型 | enterprise | 供应商类型 |
| province | String | 否 | 省份 | 北京市 | 省份 |
| city | String | 否 | 城市 | 北京市 | 城市 |
| district | String | 否 | 区县 | 朝阳区 | 区县 |
| address | String | 否 | 详细地址 | XX路XX号 | 详细地址 |
| contactName | String | 否 | 联系人姓名 | 张经理 | 联系人 |
| contactPhone | String | 否 | 联系人电话 | 13800138000 | 联系方式 |
| contactEmail | String | 否 | 联系人邮箱 | zhang@example.com | 联系方式 |
| categoryTags | Array | 否 | 商品类别标签 | ["蔬菜","水果"] | 商品类别 |
| supplierLevel | String | 否 | 供应商等级 | A | 供应商等级 |
| licenseNo | String | 否 | 营业执照编号 | 91110100XXXX | 资质信息 |
| licenseExpiresAt | String | 否 | 营业执照到期日期 | 2026-12-31 | 资质信息 |
| foodLicenseNo | String | 否 | 食品经营许可证编号 | JY123456789 | 资质信息 |
| foodLicenseExpiresAt | String | 否 | 食品许可证到期日期 | 2026-06-30 | 资质信息 |
| qualificationFiles | Array | 否 | 资质文件URL列表 | ["https://xxx.com/file1.pdf"] | 资质文件 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 供应商ID | 1 |
| supplierName | String | 供应商名称 | 绿色农产品供应商 |
| needReaudit | Boolean | 是否需要重新审核 | true |

- **异常场景**：
  - [404]：供应商不存在 → msg："供应商不存在"
  - [403]：无编辑权限 → msg："无权限编辑该供应商"

---

#### 审核供应商
- **接口路径**：`/api/v1/scm/suppliers/{id}/audit`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| result | String | 是 | 审核结果：approved/rejected | approved | 审核操作 |
| remark | String | 否 | 审核意见（驳回时建议填写） | 资质齐全，审核通过 | 审核意见 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 供应商ID | 1 |
| status | String | 审核后状态 | active |

- **异常场景**：
  - [404]：供应商不存在 → msg："供应商不存在"
  - [422]：供应商状态不允许审核 → msg："该供应商不在待审核状态"
  - [403]：无审核权限 → msg："无权限审核供应商"

---

#### 注销供应商
- **接口路径**：`/api/v1/scm/suppliers/{id}/cancel`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| reason | String | 是 | 注销原因 | 供应商停止合作 | 注销原因 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 供应商ID | 1 |
| status | String | 注销后状态 | cancelled |

- **异常场景**：
  - [404]：供应商不存在 → msg："供应商不存在"
  - [422]：供应商存在采购订单数据 → msg："供应商存在采购订单数据，无法注销"
  - [403]：无注销权限 → msg："无权限注销供应商"

---

#### 采购计划列表
- **接口路径**：`/api/v1/scm/purchase-plans`
- **请求方法**：GET
- **请求头**：Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| pageNum | Integer | 否 | 页码（默认1） | 1 | 分页查询 |
| pageSize | Integer | 否 | 每页条数（默认20，最大100） | 20 | 分页查询 |
| planNo | String | 否 | 计划编号 | CGJH-20260316001 | 计划查询 |
| planDateStart | String | 否 | 计划日期起始 | 2026-03-01 | 日期范围 |
| planDateEnd | String | 否 | 计划日期结束 | 2026-03-31 | 日期范围 |
| status | String | 否 | 计划状态：draft/pending/approved/rejected | pending | 状态筛选 |
| createdBy | Long | 否 | 创建人ID | 101 | 创建人筛选 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| list | Array | 采购计划列表 | - |
| └ id | Long | 计划ID | 1 |
| └ planNo | String | 计划编号 | CGJH-20260316001 |
| └ planName | String | 计划名称 | 3月第一周采购计划 |
| └ planDate | String | 计划采购日期 | 2026-03-18 |
| └ sourceType | String | 来源类型：manual/ai_predict/stock_alert | manual |
| └ totalAmount | Decimal | 计划总金额 | 15000.00 |
| └ actualAmount | Decimal | 实际采购金额 | 14500.00 |
| └ status | String | 计划状态 | pending |
| └ createdByName | String | 创建人 | 张三 |
| └ createdAt | String | 创建时间 | 2026-03-16 10:00:00 |
| └ itemCount | Integer | 物料明细数量 | 15 |
| pageNum | Integer | 当前页码 | 1 |
| pageSize | Integer | 每页条数 | 20 |
| total | Long | 总记录数 | 50 |
| totalPages | Integer | 总页数 | 3 |

- **异常场景**：
  - [403]：无数据权限 → msg："无权限访问采购计划数据"

---

#### 新增采购计划
- **接口路径**：`/api/v1/scm/purchase-plans`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| planName | String | 否 | 计划名称 | 3月第一周采购计划 | 计划名称 |
| planDate | String | 是 | 计划采购日期 | 2026-03-18 | 计划日期 |
| sourceType | String | 否 | 来源类型（默认manual） | manual | 来源类型 |
| sourceRefId | Long | 否 | 来源单据ID | 1 | 来源单据 |
| remark | String | 否 | 备注 | 紧急采购 | 备注 |
| items | Array | 是 | 计划物料明细 | - | 物料明细 |
| └ materialId | Long | 是 | 物料ID | 1 | 物料 |
| └ materialName | String | 是 | 物料名称 | 鲜鸡蛋 | 物料名称 |
| └ materialUnit | String | 是 | 计量单位 | kg | 计量单位 |
| └ planQty | Decimal | 是 | 计划数量 | 100.000 | 计划数量 |
| └ estimatePrice | Decimal | 否 | 预估单价 | 12.00 | 预估单价 |
| └ supplierId | Long | 否 | 建议供应商ID | 1 | 供应商 |
| └ remark | String | 否 | 备注 | 需要新鲜 | 备注 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 计划ID | 1 |
| planNo | String | 计划编号 | CGJH-20260316001 |
| status | String | 计划状态 | draft |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：计划日期不能为空"
  - [400]：物料明细为空 → msg："请至少添加一条物料明细"
  - [403]：无创建权限 → msg："无权限创建采购计划"

---

#### 提交采购计划审核
- **接口路径**：`/api/v1/scm/purchase-plans/{id}/submit`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：无

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 计划ID | 1 |
| status | String | 提交后状态 | pending |

- **异常场景**：
  - [404]：采购计划不存在 → msg："采购计划不存在"
  - [422]：计划状态不允许提交 → msg："只有草稿状态的计划可以提交"
  - [403]：无提交权限 → msg："无权限提交采购计划"

---

#### 审核采购计划
- **接口路径**：`/api/v1/scm/purchase-plans/{id}/audit`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| result | String | 是 | 审核结果：approved/rejected | approved | 审核操作 |
| remark | String | 否 | 审核意见 | 计划合理，批准执行 | 审核意见 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 计划ID | 1 |
| status | String | 审核后状态 | approved |

- **异常场景**：
  - [404]：采购计划不存在 → msg："采购计划不存在"
  - [422]：计划状态不允许审核 → msg："该计划不在待审核状态"
  - [403]：无审核权限 → msg："无权限审核采购计划"

---

#### 删除采购计划
- **接口路径**：`/api/v1/scm/purchase-plans/{id}`
- **请求方法**：DELETE
- **请求头**：Authorization: Bearer {token}
- **请求参数**：无

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| - | - | 无返回数据 | null |

- **异常场景**：
  - [404]：采购计划不存在 → msg："采购计划不存在"
  - [422]：计划状态不允许删除 → msg："只能删除待审核状态的采购计划"
  - [403]：无删除权限 → msg："无权限删除采购计划"

---

#### AI采购决策建议
- **接口路径**：`/api/v1/scm/purchase-plans/ai-suggestion`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| materialIds | Array | 是 | 物料ID列表 | [1, 2, 3] | 物料列表 |
| planDate | String | 是 | 计划采购日期 | 2026-03-18 | 计划日期 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| suggestions | Array | 采购建议列表 | - |
| └ materialId | Long | 物料ID | 1 |
| └ materialName | String | 物料名称 | 鲜鸡蛋 |
| └ recommendPurchase | Boolean | 是否推荐采购 | true |
| └ recommendSupplierId | Long | 推荐供应商ID | 1 |
| └ recommendSupplierName | String | 推荐供应商名称 | 绿色农场 |
| └ suggestQty | Decimal | 建议采购数量 | 100.000 |
| └ estimateCost | Decimal | 预估成本 | 1200.00 |
| └ historyPrice | Decimal | 历史平均价格 | 11.50 |
| └ currentPrice | Decimal | 当前报价 | 12.00 |
| └ stockDays | Integer | 库存可支撑天数 | 5 |
| └ reason | String | 建议原因 | 库存不足，建议补货 |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：物料ID列表不能为空"
  - [429]：请求频率过高 → msg："AI分析请求频率过高，请稍后再试"

---

#### 采购订单列表
- **接口路径**：`/api/v1/scm/purchase-orders`
- **请求方法**：GET
- **请求头**：Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| pageNum | Integer | 否 | 页码（默认1） | 1 | 分页查询 |
| pageSize | Integer | 否 | 每页条数（默认20，最大100） | 20 | 分页查询 |
| orderNo | String | 否 | 订单编号 | CGD-20260316-00001 | 订单查询 |
| supplierId | Long | 否 | 供应商ID | 1 | 供应商筛选 |
| status | String | 否 | 订单状态 | pending_approve | 状态筛选 |
| dateStart | String | 否 | 创建日期起始 | 2026-03-01 | 日期范围 |
| dateEnd | String | 否 | 创建日期结束 | 2026-03-31 | 日期范围 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| list | Array | 采购订单列表 | - |
| └ id | Long | 订单ID | 1 |
| └ orderNo | String | 订单编号 | CGD-20260316-00001 |
| └ supplierId | Long | 供应商ID | 1 |
| └ supplierName | String | 供应商名称 | 绿色农产品供应商 |
| └ totalAmount | Decimal | 订单总金额 | 15000.00 |
| └ expectedDeliveryAt | String | 预计交货时间 | 2026-03-18 10:00:00 |
| └ deliveryAddress | String | 收货地址 | 一号食堂 |
| └ status | String | 订单状态 | delivering |
| └ createdByName | String | 创建人 | 张三 |
| └ createdAt | String | 创建时间 | 2026-03-16 10:00:00 |
| └ itemCount | Integer | 物料明细数量 | 10 |
| └ receivedCount | Integer | 已收货物料数 | 5 |
| pageNum | Integer | 当前页码 | 1 |
| pageSize | Integer | 每页条数 | 20 |
| total | Long | 总记录数 | 50 |
| totalPages | Integer | 总页数 | 3 |

- **异常场景**：
  - [403]：无数据权限 → msg："无权限访问采购订单数据"

---

#### 新增采购订单
- **接口路径**：`/api/v1/scm/purchase-orders`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| planIds | Array | 否 | 关联采购计划ID列表 | [1, 2] | 关联计划 |
| supplierId | Long | 是 | 供应商ID | 1 | 供应商 |
| orderDate | String | 是 | 订单日期 | 2026-03-16 | 订单日期 |
| expectedDeliveryAt | String | 是 | 预计交货时间 | 2026-03-18 10:00:00 | 预计交货 |
| deliveryAddress | String | 是 | 收货地址 | 一号食堂仓库 | 收货地址 |
| remark | String | 否 | 备注 | 请准时送达 | 备注 |
| items | Array | 是 | 订单物料明细 | - | 物料明细 |
| └ materialId | Long | 是 | 物料ID | 1 | 物料 |
| └ materialName | String | 是 | 物料名称 | 鲜鸡蛋 | 物料名称 |
| └ materialUnit | String | 是 | 计量单位 | kg | 计量单位 |
| └ orderQty | Decimal | 是 | 订购数量 | 100.000 | 订购数量 |
| └ unitPrice | Decimal | 是 | 单价 | 12.00 | 单价 |
| └ remark | String | 否 | 备注 | 新鲜鸡蛋 | 备注 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 订单ID | 1 |
| orderNo | String | 订单编号 | CGD-20260316-00001 |
| status | String | 订单状态 | pending_submit |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：供应商ID不能为空"
  - [400]：物料明细为空 → msg："请至少添加一条物料明细"
  - [422]：供应商未审核 → msg："供应商未通过审核，无法下单"
  - [403]：无创建权限 → msg："无权限创建采购订单"

---

#### 提交采购订单审核
- **接口路径**：`/api/v1/scm/purchase-orders/{id}/submit`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：无

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 订单ID | 1 |
| status | String | 提交后状态 | pending_approve |

- **异常场景**：
  - [404]：采购订单不存在 → msg："采购订单不存在"
  - [422]：订单状态不允许提交 → msg："只有待提交状态的订单可以提交"
  - [403]：无提交权限 → msg："无权限提交采购订单"

---

#### 审核采购订单
- **接口路径**：`/api/v1/scm/purchase-orders/{id}/audit`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| result | String | 是 | 审核结果：approved/rejected | approved | 审核操作 |
| remark | String | 否 | 审核意见 | 订单金额合理，批准采购 | 审核意见 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 订单ID | 1 |
| status | String | 审核后状态 | approved |

- **异常场景**：
  - [404]：采购订单不存在 → msg："采购订单不存在"
  - [422]：订单状态不允许审核 → msg："该订单不在待审核状态"
  - [403]：无审核权限 → msg："无权限审核采购订单"

---

#### 采购订单作废申请
- **接口路径**：`/api/v1/scm/purchase-orders/{id}/cancel`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| reason | String | 是 | 作废原因 | 供应商无法按时供货 | 作废原因 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 订单ID | 1 |
| status | String | 作废后状态 | cancelled |

- **异常场景**：
  - [404]：采购订单不存在 → msg："采购订单不存在"
  - [422]：订单状态不允许作废 → msg："只有待发货状态的订单可以申请作废"
  - [403]：无作废权限 → msg："无权限作废采购订单"

---

#### 收货验收
- **接口路径**：`/api/v1/scm/purchase-orders/{id}/receive`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| items | Array | 是 | 收货物料明细 | - | 收货明细 |
| └ itemId | Long | 是 | 订单明细ID | 1 | 明细ID |
| └ receivedQty | Decimal | 是 | 收货数量 | 100.000 | 收货数量 |
| └ warehouseId | Long | 是 | 入库仓库ID | 1 | 入库仓库 |
| └ positionId | Long | 是 | 入库仓位ID | 1 | 入库仓位 |
| └ batchNo | String | 否 | 批次号 | 20260316-001 | 批次号 |
| └ shelfLifeDays | Integer | 否 | 保质期（天） | 30 | 保质期 |
| └ qualityScore | Integer | 否 | 质量评分（1-5星） | 5 | 质量评分 |
| photos | Array | 否 | 验收照片URL列表 | ["https://xxx.com/photo1.jpg"] | 验收照片 |
| remark | String | 否 | 收货备注 | 货物完好 | 备注 |
| traceBatchId | String | 否 | 溯源批次码 | TRACE-20260316-001 | 溯源信息 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| receiptId | Long | 验收记录ID | 1 |
| receiptNo | String | 验收单编号 | YSD-20260316-00001 |
| orderId | Long | 订单ID | 1 |
| orderStatus | String | 订单状态 | inspected |

- **异常场景**：
  - [404]：采购订单不存在 → msg："采购订单不存在"
  - [422]：订单状态不允许收货 → msg："订单不在可收货状态"
  - [422]：收货数量超出订单数量 → msg："收货数量不能超过订单数量"
  - [403]：无收货权限 → msg："无权限进行收货验收"

---

#### 采购订单统计
- **接口路径**：`/api/v1/scm/purchase-orders/statistics`
- **请求方法**：GET
- **请求头**：Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| month | String | 否 | 统计月份（默认当月） | 2026-03 | 月份筛选 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| monthOrders | Integer | 本月订单数 | 25 |
| monthAmount | Decimal | 本月订单总金额 | 150000.00 |
| monthReceivedOrders | Integer | 本月已收货订单数 | 20 |
| monthPendingOrders | Integer | 本月待收货订单数 | 5 |
| monthCancelledOrders | Integer | 本月取消订单数 | 2 |
| avgOrderAmount | Decimal | 本月订单平均金额 | 6000.00 |

- **异常场景**：
  - [403]：无数据权限 → msg："无权限访问采购订单统计数据"

---

### 5.4 仓储管理模块

#### 仓库列表
- **接口路径**：`/api/v1/wms/warehouses`
- **请求方法**：GET
- **请求头**：Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| pageNum | Integer | 否 | 页码（默认1） | 1 | 分页查询 |
| pageSize | Integer | 否 | 每页条数（默认20，最大100） | 20 | 分页查询 |
| warehouseName | String | 否 | 仓库名称（模糊搜索） | 一号库 | 仓库列表 |
| warehouseCode | String | 否 | 仓库编码 | WH001 | 仓库列表 |
| warehouseType | String | 否 | 仓库类型：cold/dry/frozen/temporary | cold | 仓库类型 |
| status | String | 否 | 仓库状态：active/inactive/idle | active | 仓库状态 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| list | Array | 仓库列表 | - |
| └ id | Long | 仓库ID | 1 |
| └ warehouseCode | String | 仓库编码 | WH001 |
| └ warehouseName | String | 仓库名称 | 一号冷库 |
| └ warehouseType | String | 仓库类型 | cold |
| └ status | String | 仓库状态 | active |
| └ location | String | 仓库位置 | 一号食堂一楼 |
| └ managerId | Long | 负责人ID | 101 |
| └ managerName | String | 负责人姓名 | 张三 |
| └ managerPhone | String | 负责人联系方式 | 13800138000 |
| └ maxCapacity | Integer | 最大容量（立方米） | 1000 |
| └ currentCapacity | Integer | 当前容量（立方米） | 600 |
| └ capacityPercent | Decimal | 容量使用百分比 | 60.00 |
| └ positionCount | Integer | 总仓位数 | 50 |
| └ usedPositionCount | Integer | 已使用仓位数 | 30 |
| └ idlePositionCount | Integer | 空闲仓位数 | 20 |
| pageNum | Integer | 当前页码 | 1 |
| pageSize | Integer | 每页条数 | 20 |
| total | Long | 总记录数 | 10 |
| totalPages | Integer | 总页数 | 1 |

- **异常场景**：
  - [403]：无数据权限 → msg："无权限访问仓库数据"

---

#### 仓库详情
- **接口路径**：`/api/v1/wms/warehouses/{id}`
- **请求方法**：GET
- **请求头**：Authorization: Bearer {token}
- **请求参数**：无

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 仓库ID | 1 |
| warehouseCode | String | 仓库编码 | WH001 |
| warehouseName | String | 仓库名称 | 一号冷库 |
| warehouseType | String | 仓库类型 | cold |
| status | String | 仓库状态 | active |
| location | String | 仓库位置 | 一号食堂一楼 |
| maxCapacity | Integer | 最大容量 | 1000 |
| currentCapacity | Integer | 当前容量 | 600 |
| managerId | Long | 负责人ID | 101 |
| managerName | String | 负责人姓名 | 张三 |
| managerPhone | String | 负责人联系方式 | 13800138000 |
| coreIndicators | Object | 核心指标 | - |
| └ currentTemp | Decimal | 当前温度 | 4.5 |
| └ tempStatus | String | 温度状态：normal/warning/alert | normal |
| └ currentHumidity | Decimal | 当前湿度 | 65.00 |
| └ humidityStatus | String | 湿度状态 | normal |
| └ stockPercent | Decimal | 库存占用百分比 | 60.00 |
| └ stockStatus | String | 库存状态 | normal |
| └ tempAlertCount | Integer | 温度预警条数 | 0 |
| └ warmTip | String | 温馨提示 | 库存正常，请继续保持 |
| positionStats | Object | 仓位统计 | - |
| └ totalCount | Integer | 总仓位数 | 50 |
| └ usedCount | Integer | 已使用仓位数 | 30 |
| └ idleCount | Integer | 空闲仓位数 | 20 |
| positions | Array | 仓位列表 | - |
| └ id | Long | 仓位ID | 1 |
| └ positionCode | String | 仓位编码 | A-01-01 |
| └ positionType | String | 仓位类型 | normal |
| └ levelInfo | String | 层级位置 | A区-1货架-1货位 |
| └ stockQty | Integer | 库存数量 | 50 |

- **异常场景**：
  - [404]：仓库不存在 → msg："仓库不存在"
  - [403]：无数据权限 → msg："无权限访问该仓库数据"

---

#### 新增仓库
- **接口路径**：`/api/v1/wms/warehouses`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| warehouseCode | String | 是 | 仓库编码（唯一） | WH001 | 仓库编码 |
| warehouseName | String | 是 | 仓库名称（1-100字符） | 一号冷库 | 仓库名称 |
| warehouseType | String | 是 | 仓库类型：cold/dry/frozen/temporary | cold | 仓库类型 |
| maxCapacity | Integer | 是 | 最大容量（立方米） | 1000 | 最大容量 |
| location | String | 是 | 仓库位置 | 一号食堂一楼 | 仓库位置 |
| managerId | Long | 是 | 负责人ID | 101 | 负责人 |
| managerPhone | String | 是 | 负责人联系方式 | 13800138000 | 联系方式 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 新增仓库ID | 1 |
| warehouseCode | String | 仓库编码 | WH001 |
| warehouseName | String | 仓库名称 | 一号冷库 |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：仓库名称不能为空"
  - [409]：仓库编码已存在 → msg："仓库编码已存在"
  - [403]：无创建权限 → msg："无权限创建仓库"

---

#### 编辑仓库
- **接口路径**：`/api/v1/wms/warehouses/{id}`
- **请求方法**：PUT
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| warehouseCode | String | 否 | 仓库编码 | WH001 | 仓库编码 |
| warehouseName | String | 否 | 仓库名称 | 一号冷库 | 仓库名称 |
| maxCapacity | Integer | 否 | 最大容量 | 1200 | 最大容量 |
| location | String | 否 | 仓库位置 | 一号食堂一楼 | 仓库位置 |
| managerId | Long | 否 | 负责人ID | 101 | 负责人 |
| managerPhone | String | 否 | 负责人联系方式 | 13800138000 | 联系方式 |
| status | String | 否 | 仓库状态：active/inactive/idle | active | 仓库状态 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 仓库ID | 1 |
| warehouseName | String | 仓库名称 | 一号冷库 |

- **异常场景**：
  - [404]：仓库不存在 → msg："仓库不存在"
  - [409]：仓库编码已存在 → msg："仓库编码已存在"
  - [403]：无编辑权限 → msg："无权限编辑该仓库"

---

#### 删除仓库
- **接口路径**：`/api/v1/wms/warehouses/{id}`
- **请求方法**：DELETE
- **请求头**：Authorization: Bearer {token}
- **请求参数**：无

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| - | - | 无返回数据 | null |

- **异常场景**：
  - [404]：仓库不存在 → msg："仓库不存在"
  - [422]：仓库仓位中存在库存 → msg："仓库仓位中物料库存数不为0，无法删除"
  - [403]：无删除权限 → msg："无权限删除该仓库"

---

#### 新增仓位
- **接口路径**：`/api/v1/wms/positions`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| warehouseId | Long | 是 | 所属仓库ID | 1 | 所属仓库 |
| positionCode | String | 是 | 仓位编码 | A-01-01 | 仓位编码 |
| positionType | String | 是 | 仓位类型：normal/frozen/damaged | normal | 仓位类型 |
| areaCode | String | 是 | 区域编码 | A | 区域 |
| shelfCode | String | 是 | 货架编码 | 01 | 货架 |
| positionNo | String | 是 | 货位编号 | 01 | 货位 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 仓位ID | 1 |
| positionCode | String | 仓位编码 | A-01-01 |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：仓位编码不能为空"
  - [409]：仓位编码已存在 → msg："仓位编码已存在"
  - [403]：无创建权限 → msg："无权限创建仓位"

---

#### 编辑仓位
- **接口路径**：`/api/v1/wms/positions/{id}`
- **请求方法**：PUT
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| positionCode | String | 否 | 仓位编码 | A-01-01 | 仓位编码 |
| positionType | String | 否 | 仓位类型 | normal | 仓位类型 |
| areaCode | String | 否 | 区域编码 | A | 区域 |
| shelfCode | String | 否 | 货架编码 | 01 | 货架 |
| positionNo | String | 否 | 货位编号 | 01 | 货位 |
| status | String | 否 | 仓位状态：active/inactive/idle | active | 仓位状态 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 仓位ID | 1 |
| positionCode | String | 仓位编码 | A-01-01 |

- **异常场景**：
  - [404]：仓位不存在 → msg："仓位不存在"
  - [403]：无编辑权限 → msg："无权限编辑该仓位"

---

#### 删除仓位
- **接口路径**：`/api/v1/wms/positions/{id}`
- **请求方法**：DELETE
- **请求头**：Authorization: Bearer {token}
- **请求参数**：无

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| - | - | 无返回数据 | null |

- **异常场景**：
  - [404]：仓位不存在 → msg："仓位不存在"
  - [422]：仓位中存在库存 → msg："仓位中物料库存数不为0，无法删除"
  - [403]：无删除权限 → msg："无权限删除该仓位"

---

#### 物料列表
- **接口路径**：`/api/v1/wms/materials`
- **请求方法**：GET
- **请求头**：Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| pageNum | Integer | 否 | 页码（默认1） | 1 | 分页查询 |
| pageSize | Integer | 否 | 每页条数（默认20，最大100） | 20 | 分页查询 |
| materialName | String | 否 | 物料名称（模糊搜索） | 鸡蛋 | 物料列表 |
| materialCode | String | 否 | 物料编码 | MAT001 | 物料列表 |
| categoryId | Long | 否 | 物料类别ID | 1 | 类别筛选 |
| stockStatus | String | 否 | 库存状态：normal/low/high/expired | low | 库存状态 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| list | Array | 物料列表 | - |
| └ id | Long | 物料ID | 1 |
| └ materialCode | String | 物料编码 | MAT001 |
| └ materialName | String | 物料名称 | 鲜鸡蛋 |
| └ materialSpec | String | 物料规格 | 500g/盒 |
| └ unit | String | 物料单位 | kg |
| └ categoryName | String | 物料类别 | 蛋类 |
| └ storageRequire | String | 存储要求 | 冷藏 |
| └ shelfLifeDays | Integer | 保质期标准（天） | 30 |
| └ currentStock | Decimal | 当前库存 | 50.000 |
| └ minStock | Decimal | 最低库存 | 20.000 |
| └ maxStock | Decimal | 最高库存 | 300.000 |
| └ stockStatus | String | 物料库存状态 | normal |
| └ status | String | 物料状态 | active |
| └ imageUrl | String | 物料图片URL | https://xxx.com/egg.jpg |
| pageNum | Integer | 当前页码 | 1 |
| pageSize | Integer | 每页条数 | 20 |
| total | Long | 总记录数 | 100 |
| totalPages | Integer | 总页数 | 5 |

- **异常场景**：
  - [403]：无数据权限 → msg："无权限访问物料数据"

---

#### 物料详情
- **接口路径**：`/api/v1/wms/materials/{id}`
- **请求方法**：GET
- **请求头**：Authorization: Bearer {token}
- **请求参数**：无

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 物料ID | 1 |
| materialCode | String | 物料编码 | MAT001 |
| materialName | String | 物料名称 | 鲜鸡蛋 |
| materialSpec | String | 物料规格 | 500g/盒 |
| unit | String | 物料单位 | kg |
| categoryId | Long | 物料类别ID | 1 |
| categoryName | String | 物料类别 | 蛋类 |
| storageRequire | String | 存储要求 | 冷藏 |
| shelfLifeDays | Integer | 保质期标准（天） | 30 |
| nearExpiryDays | Integer | 临期提醒天数 | 7 |
| warningDays | Integer | 预警期天数 | 30 |
| currentStock | Decimal | 当前库存 | 50.000 |
| minStock | Decimal | 最低库存 | 20.000 |
| maxStock | Decimal | 最高库存 | 300.000 |
| stockStatus | String | 物料库存状态 | normal |
| status | String | 物料状态 | active |
| imageUrl | String | 物料图片URL | https://xxx.com/egg.jpg |
| stockDistribution | Array | 库存分布 | - |
| └ warehouseName | String | 仓库名称 | 一号冷库 |
| └ positionCode | String | 仓位编码 | A-01-01 |
| └ stockQty | Decimal | 库存数量 | 30.000 |
| └ batchNo | String | 批次号 | 20260316-001 |
| └ productionDate | String | 生产日期 | 2026-03-16 |
| └ shelfLifeDays | Integer | 保质期 | 30 |
| └ remainingDays | Integer | 剩余天数 | 25 |

- **异常场景**：
  - [404]：物料不存在 → msg："物料不存在"
  - [403]：无数据权限 → msg："无权限访问该物料数据"

---

#### 新增物料
- **接口路径**：`/api/v1/wms/materials`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| materialCode | String | 是 | 物料编码（唯一） | MAT001 | 物料编码 |
| materialName | String | 是 | 物料名称（1-100字符） | 鲜鸡蛋 | 物料名称 |
| materialSpec | String | 是 | 物料规格 | 500g/盒 | 物料规格 |
| unit | String | 是 | 物料单位 | kg | 物料单位 |
| categoryId | Long | 是 | 物料类别ID | 1 | 物料类别 |
| storageRequire | String | 是 | 存储要求 | 冷藏 | 存储要求 |
| shelfLifeDays | Integer | 是 | 保质期标准（天） | 30 | 保质期 |
| nearExpiryDays | Integer | 否 | 临期提醒天数（默认7） | 7 | 临期提醒 |
| warningDays | Integer | 否 | 预警期天数（默认30） | 30 | 预警期 |
| minStock | Decimal | 是 | 最低库存 | 20.000 | 最低库存 |
| maxStock | Decimal | 是 | 最高库存 | 300.000 | 最高库存 |
| imageUrl | String | 否 | 物料图片URL | https://xxx.com/egg.jpg | 物料图片 |
| useAiSuggestion | Boolean | 否 | 是否使用AI建议临期提醒天数 | true | AI建议 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 新增物料ID | 1 |
| materialCode | String | 物料编码 | MAT001 |
| materialName | String | 物料名称 | 鲜鸡蛋 |
| aiSuggestedDays | Integer | AI建议临期天数（useAiSuggestion=true时返回） | 7 |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：物料名称不能为空"
  - [409]：物料编码已存在 → msg："物料编码已存在"
  - [403]：无创建权限 → msg："无权限创建物料"

---

#### 编辑物料
- **接口路径**：`/api/v1/wms/materials/{id}`
- **请求方法**：PUT
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| materialName | String | 否 | 物料名称 | 鲜鸡蛋 | 物料名称 |
| materialSpec | String | 否 | 物料规格 | 500g/盒 | 物料规格 |
| unit | String | 否 | 物料单位 | kg | 物料单位 |
| categoryId | Long | 否 | 物料类别ID | 1 | 物料类别 |
| storageRequire | String | 否 | 存储要求 | 冷藏 | 存储要求 |
| shelfLifeDays | Integer | 否 | 保质期标准（天） | 30 | 保质期 |
| nearExpiryDays | Integer | 否 | 临期提醒天数 | 7 | 临期提醒 |
| warningDays | Integer | 否 | 预警期天数 | 30 | 预警期 |
| minStock | Decimal | 否 | 最低库存 | 20.000 | 最低库存 |
| maxStock | Decimal | 否 | 最高库存 | 300.000 | 最高库存 |
| imageUrl | String | 否 | 物料图片URL | https://xxx.com/egg.jpg | 物料图片 |
| status | String | 否 | 物料状态：active/inactive | active | 物料状态 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 物料ID | 1 |
| materialName | String | 物料名称 | 鲜鸡蛋 |

- **异常场景**：
  - [404]：物料不存在 → msg："物料不存在"
  - [422]：禁用物料时库存不为0 → msg："物料库存数不为0，无法禁用"
  - [403]：无编辑权限 → msg："无权限编辑该物料"

---

#### 删除物料
- **接口路径**：`/api/v1/wms/materials/{id}`
- **请求方法**：DELETE
- **请求头**：Authorization: Bearer {token}
- **请求参数**：无

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| - | - | 无返回数据 | null |

- **异常场景**：
  - [404]：物料不存在 → msg："物料不存在"
  - [422]：物料库存不为0 → msg："物料库存数不为0，无法删除"
  - [403]：无删除权限 → msg："无权限删除该物料"

---

#### 入库单列表
- **接口路径**：`/api/v1/wms/inbound-orders`
- **请求方法**：GET
- **请求头**：Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| pageNum | Integer | 否 | 页码（默认1） | 1 | 分页查询 |
| pageSize | Integer | 否 | 每页条数（默认20，最大100） | 20 | 分页查询 |
| inboundNo | String | 否 | 入库单号 | RKD-20260316-00001 | 入库单查询 |
| inboundType | String | 否 | 入库类型 | purchase | 入库类型筛选 |
| status | String | 否 | 入库状态：pending/approved/rejected | pending | 状态筛选 |
| dateStart | String | 否 | 入库日期起始 | 2026-03-01 | 日期范围 |
| dateEnd | String | 否 | 入库日期结束 | 2026-03-31 | 日期范围 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| list | Array | 入库单列表 | - |
| └ id | Long | 入库单ID | 1 |
| └ inboundNo | String | 入库单号 | RKD-20260316-00001 |
| └ inboundType | String | 入库类型 | purchase |
| └ inboundTypeName | String | 入库类型名称 | 采购入库 |
| └ sourceOrderNo | String | 来源单据号 | CGD-20260316-00001 |
| └ supplierName | String | 供应商 | 绿色农场 |
| └ totalQty | Decimal | 入库总数量 | 500.000 |
| └ totalAmount | Decimal | 入库金额 | 6000.00 |
| └ warehouseName | String | 入库仓库 | 一号冷库 |
| └ inboundDate | String | 入库日期 | 2026-03-16 |
| └ status | String | 入库状态 | pending |
| └ createdByName | String | 入库人 | 张三 |
| └ createdAt | String | 创建时间 | 2026-03-16 10:00:00 |
| pageNum | Integer | 当前页码 | 1 |
| pageSize | Integer | 每页条数 | 20 |
| total | Long | 总记录数 | 50 |
| totalPages | Integer | 总页数 | 3 |

- **异常场景**：
  - [403]：无数据权限 → msg："无权限访问入库单数据"

---

#### 新增入库单
- **接口路径**：`/api/v1/wms/inbound-orders`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| inboundType | String | 是 | 入库类型：purchase/transfer/return/refund/donation/other | purchase | 入库类型 |
| sourceOrderNo | String | 否 | 来源单据号 | CGD-20260316-00001 | 来源单据 |
| supplierId | Long | 否 | 供应商ID | 1 | 供应商 |
| inboundOrgId | Long | 是 | 入库组织ID | 1001 | 入库组织 |
| inboundDate | String | 是 | 入库日期 | 2026-03-16 | 入库日期 |
| remark | String | 否 | 备注 | 正常入库 | 备注 |
| items | Array | 是 | 入库物料明细 | - | 物料明细 |
| └ materialId | Long | 是 | 物料ID | 1 | 物料 |
| └ materialName | String | 是 | 物料名称 | 鲜鸡蛋 | 物料名称 |
| └ materialSpec | String | 是 | 物料规格 | 500g/盒 | 物料规格 |
| └ warehouseId | Long | 是 | 入库仓库ID | 1 | 入库仓库 |
| └ positionId | Long | 是 | 入库仓位ID | 1 | 入库仓位 |
| └ inboundQty | Decimal | 是 | 入库数量 | 100.000 | 入库数量 |
| └ unit | String | 是 | 物料单位 | kg | 物料单位 |
| └ batchNo | String | 是 | 批次号 | 20260316-001 | 批次号 |
| └ shelfLifeDays | Integer | 是 | 保质期（天） | 30 | 保质期 |
| └ unitPrice | Decimal | 否 | 单价 | 12.00 | 单价 |
| attachments | Array | 否 | 附件URL列表 | ["https://xxx.com/file1.pdf"] | 附件 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 入库单ID | 1 |
| inboundNo | String | 入库单号 | RKD-20260316-00001 |
| status | String | 入库状态 | pending |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：入库日期不能为空"
  - [400]：物料明细为空 → msg："请至少添加一条物料明细"
  - [403]：无创建权限 → msg："无权限创建入库单"

---

#### 审核入库单
- **接口路径**：`/api/v1/wms/inbound-orders/{id}/audit`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| result | String | 是 | 审核结果：approved/rejected | approved | 审核操作 |
| remark | String | 否 | 审核意见 | 入库单据齐全，审核通过 | 审核意见 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 入库单ID | 1 |
| status | String | 审核后状态 | approved |

- **异常场景**：
  - [404]：入库单不存在 → msg："入库单不存在"
  - [422]：入库单状态不允许审核 → msg："该入库单不在待审核状态"
  - [403]：无审核权限 → msg："无权限审核入库单"

---

#### 出库单列表
- **接口路径**：`/api/v1/wms/outbound-orders`
- **请求方法**：GET
- **请求头**：Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| pageNum | Integer | 否 | 页码（默认1） | 1 | 分页查询 |
| pageSize | Integer | 否 | 每页条数（默认20，最大100） | 20 | 分页查询 |
| outboundNo | String | 否 | 出库单号 | CKD-20260316-00001 | 出库单查询 |
| outboundType | String | 否 | 出库类型 | requisition | 出库类型筛选 |
| status | String | 否 | 出库状态：pending/approved/rejected | pending | 状态筛选 |
| dateStart | String | 否 | 出库日期起始 | 2026-03-01 | 日期范围 |
| dateEnd | String | 否 | 出库日期结束 | 2026-03-31 | 日期范围 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| list | Array | 出库单列表 | - |
| └ id | Long | 出库单ID | 1 |
| └ outboundNo | String | 出库单号 | CKD-20260316-00001 |
| └ outboundType | String | 出库类型 | requisition |
| └ outboundTypeName | String | 出库类型名称 | 领用出库 |
| └ outboundOrgId | Long | 出库组织ID | 1001 |
| └ outboundOrgName | String | 出库组织名称 | 一号食堂 |
| └ purpose | String | 用途 | 烹饪使用 |
| └ totalQty | Decimal | 出库总数量 | 50.000 |
| └ totalAmount | Decimal | 出库金额 | 600.00 |
| └ outboundDate | String | 出库日期 | 2026-03-16 |
| └ status | String | 出库状态 | pending |
| └ createdByName | String | 出库人 | 张三 |
| └ createdAt | String | 创建时间 | 2026-03-16 10:00:00 |
| pageNum | Integer | 当前页码 | 1 |
| pageSize | Integer | 每页条数 | 20 |
| total | Long | 总记录数 | 50 |
| totalPages | Integer | 总页数 | 3 |

- **异常场景**：
  - [403]：无数据权限 → msg："无权限访问出库单数据"

---

#### 新增出库单
- **接口路径**：`/api/v1/wms/outbound-orders`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| outboundType | String | 是 | 出库类型：requisition/sale/return/transfer/loss/donation/scrap/other | requisition | 出库类型 |
| outboundOrgId | Long | 是 | 出库组织ID | 1001 | 出库组织 |
| outboundDate | String | 是 | 出库日期 | 2026-03-16 | 出库日期 |
| purpose | String | 否 | 用途 | 烹饪使用 | 用途 |
| remark | String | 否 | 备注 | 正常出库 | 备注 |
| items | Array | 是 | 出库物料明细 | - | 物料明细 |
| └ materialId | Long | 是 | 物料ID | 1 | 物料 |
| └ materialName | String | 是 | 物料名称 | 鲜鸡蛋 | 物料名称 |
| └ materialSpec | String | 是 | 物料规格 | 500g/盒 | 物料规格 |
| └ warehouseId | Long | 是 | 出库仓库ID | 1 | 出库仓库 |
| └ positionId | Long | 是 | 出库仓位ID | 1 | 出库仓位 |
| └ outboundQty | Decimal | 是 | 出库数量 | 10.000 | 出库数量 |
| └ unit | String | 是 | 物料单位 | kg | 物料单位 |
| └ batchNo | String | 是 | 批次号 | 20260316-001 | 批次号 |
| └ purposeDetail | String | 否 | 出库用途 | 烹饪 | 用途 |
| attachments | Array | 否 | 附件URL列表 | ["https://xxx.com/file1.jpg"] | 附件 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 出库单ID | 1 |
| outboundNo | String | 出库单号 | CKD-20260316-00001 |
| status | String | 出库状态 | pending |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：出库日期不能为空"
  - [400]：物料明细为空 → msg："请至少添加一条物料明细"
  - [422]：出库数量超过库存 → msg："物料出库数量超过当前库存"
  - [403]：无创建权限 → msg："无权限创建出库单"

---

#### 审核出库单
- **接口路径**：`/api/v1/wms/outbound-orders/{id}/audit`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| result | String | 是 | 审核结果：approved/rejected | approved | 审核操作 |
| remark | String | 否 | 审核意见 | 出库单据齐全，审核通过 | 审核意见 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 出库单ID | 1 |
| status | String | 审核后状态 | approved |

- **异常场景**：
  - [404]：出库单不存在 → msg："出库单不存在"
  - [422]：出库单状态不允许审核 → msg："该出库单不在待审核状态"
  - [403]：无审核权限 → msg："无权限审核出库单"

---

#### AI入库建议
- **接口路径**：`/api/v1/wms/inbound-orders/ai-suggestion`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| items | Array | 是 | 入库物料列表 | - | 物料列表 |
| └ materialId | Long | 是 | 物料ID | 1 | 物料 |
| └ materialName | String | 是 | 物料名称 | 鲜鸡蛋 | 物料名称 |
| └ materialSpec | String | 是 | 物料规格 | 500g/盒 | 规格 |
| └ qty | Decimal | 是 | 入库数量 | 100.000 | 数量 |
| supplierId | Long | 否 | 供应商ID | 1 | 供应商 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| suggestions | Array | 入库建议列表 | - |
| └ materialId | Long | 物料ID | 1 |
| └ materialName | String | 物料名称 | 鲜鸡蛋 |
| └ recommendWarehouseId | Long | 建议入库仓库ID | 1 |
| └ recommendWarehouseName | String | 建议入库仓库名称 | 一号冷库 |
| └ recommendPositionId | Long | 建议入库仓位ID | 1 |
| └ recommendPositionCode | String | 建议入库仓位编码 | A-01-01 |
| └ batchSplits | Array | 分批次入库数量建议 | - |
| └ batchNo | String | 批次号 | 20260316-001 |
| └ splitQty | Decimal | 该批次入库数量 | 100.000 |
| └ reason | String | 建议原因 | 按保质期临期先出+批次优先规则 |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：物料列表不能为空"
  - [429]：请求频率过高 → msg："AI分析请求频率过高，请稍后再试"

---

#### AI出库建议
- **接口路径**：`/api/v1/wms/outbound-orders/ai-suggestion`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| items | Array | 是 | 出库物料列表 | - | 物料列表 |
| └ materialId | Long | 是 | 物料ID | 1 | 物料 |
| └ materialName | String | 是 | 物料名称 | 鲜鸡蛋 | 物料名称 |
| └ materialSpec | String | 是 | 物料规格 | 500g/盒 | 规格 |
| └ qty | Decimal | 是 | 出库数量 | 50.000 | 数量 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| suggestions | Array | 出库建议列表 | - |
| └ materialId | Long | 物料ID | 1 |
| └ materialName | String | 物料名称 | 鲜鸡蛋 |
| └ recommendWarehouseId | Long | 建议出库仓库ID | 1 |
| └ recommendWarehouseName | String | 建议出库仓库名称 | 一号冷库 |
| └ recommendPositionId | Long | 建议出库仓位ID | 1 |
| └ recommendPositionCode | String | 建议出库仓位编码 | A-01-01 |
| └ recommendBatchNo | String | 建议出库批次号 | 20260301-001 |
| └ batchSplits | Array | 分批次出库数量建议 | - |
| └ batchNo | String | 批次号 | 20260301-001 |
| └ splitQty | Decimal | 该批次出库数量 | 30.000 |
| └ reason | String | 建议原因 | 按保质期临期先出+批次优先规则 |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：物料列表不能为空"
  - [429]：请求频率过高 → msg："AI分析请求频率过高，请稍后再试"

---

#### AI需求预测
- **接口路径**：`/api/v1/wms/ai-demand-prediction`
- **请求方法**：GET
- **请求头**：Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| periodType | String | 是 | 预测周期：day/week/month | week | 预测周期 |
| categoryId | Long | 否 | 物料类别ID（不传则全部） | 1 | 类别筛选 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| statistics | Object | 预测统计 | - |
| └ urgentCount | Integer | 紧急补货数 | 5 |
| └ priorityCount | Integer | 优先补货数 | 10 |
| └ normalCount | Integer | 正常补货数 | 15 |
| └ accuracyRate | Decimal | 预测准确率 | 92.50 |
| predictions | Array | 预测结果列表 | - |
| └ materialId | Long | 物料ID | 1 |
| └ materialName | String | 物料名称 | 鲜鸡蛋 |
| └ currentStock | Decimal | 当前库存 | 50.000 |
| └ currentStockUnit | String | 当前库存单位 | kg |
| └ predictDemand | Decimal | 预测需求量 | 100.000 |
| └ predictDemandUnit | String | 预测需求量单位 | kg |
| └ suggestReplenish | Decimal | 建议补货量 | 50.000 |
| └ suggestReplenishUnit | String | 建议补货量单位 | kg |
| └ estimateAmount | Decimal | 预估金额 | 600.00 |
| └ confidence | Decimal | 置信度 | 0.95 |
| └ priority | String | 优先级：urgent/priority/normal | priority |
| modelInfo | Object | 预测模型说明 | - |
| └ dataSources | Array | 数据来源 | ["历史消耗记录","菜谱计划","就餐人数统计","实时库存"] |
| └ algorithm | String | 预测算法 | 时间序列分析 + 机器学习混合模型 |
| └ confidenceDesc | String | 置信度说明 | 表示预测结果的可靠性，数值越高表示越可信 |

- **异常场景**：
  - [403]：无数据权限 → msg："无权限访问AI需求预测数据"

---

#### AI智能损耗分析
- **接口路径**：`/api/v1/wms/ai-loss-analysis`
- **请求方法**：GET
- **请求头**：Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| periodType | String | 是 | 分析周期：week/month/quarter | month | 分析周期 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| statistics | Object | 预测统计 | - |
| └ totalLossAmount | Decimal | 总损耗金额 | 5000.00 |
| └ lossRate | Decimal | 损耗率 | 3.50 |
| └ lossItemCount | Integer | 损耗物品数 | 15 |
| └ improvePotentialAmount | Decimal | 改善潜力金额 | 2000.00 |
| lossItems | Array | 分析结果列表 | - |
| └ materialId | Long | 物料ID | 1 |
| └ materialName | String | 物料名称 | 鲜鸡蛋 |
| └ categoryName | String | 物料类别 | 蛋类 |
| └ lossRate | Decimal | 损耗率 | 5.00 |
| └ lossAmount | Decimal | 损耗金额 | 500.00 |
| └ lossCount | Integer | 损耗次数 | 10 |
| └ mainReason | String | 主要原因 | 临期报废 |
| └ aiSuggestion | String | AI建议 | 建议优化采购计划，减少库存积压 |
| lossReasonDistribution | Array | 损耗原因分布 | - |
| └ reason | String | 损耗原因 | 临期报废 |
| └ totalAmount | Decimal | 损耗总金额 | 2000.00 |
| └ percent | Decimal | 分布占比 | 40.00 |
| periodLossAnalysis | Array | 时段损耗分析 | - |
| └ periodName | String | 时段名称 | 上午 |
| └ lossRatio | Decimal | 损耗比率 | 35.00 |
| └ trend | String | 趋势 | rising |
| optimizationSuggestions | Array | AI损耗优化建议 | - |
| └ suggestion | String | 建议及说明 | 优化采购计划，减少库存积压 |
| └ estimateSavingAmount | Decimal | 预计节省金额 | 1000.00 |

- **异常场景**：
  - [403]：无数据权限 → msg："无权限访问AI损耗分析数据"

---

#### 库存预警列表
- **接口路径**：`/api/v1/wms/inventory-alerts`
- **请求方法**：GET
- **请求头**：Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| pageNum | Integer | 否 | 页码（默认1） | 1 | 分页查询 |
| pageSize | Integer | 否 | 每页条数（默认20，最大100） | 20 | 分页查询 |
| alertType | String | 否 | 预警类型：low/high/expired/near_expiry | low | 预警类型 |
| warehouseId | Long | 否 | 仓库ID | 1 | 仓库筛选 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| list | Array | 预警列表 | - |
| └ id | Long | 预警ID | 1 |
| └ materialId | Long | 物料ID | 1 |
| └ materialName | String | 物料名称 | 鲜鸡蛋 |
| └ materialSpec | String | 物料规格 | 500g/盒 |
| └ alertType | String | 预警类型 | low |
| └ alertTypeName | String | 预警类型名称 | 库存不足 |
| └ warehouseName | String | 所在仓库 | 一号冷库 |
| └ positionCode | String | 仓位编码 | A-01-01 |
| └ currentStock | Decimal | 当前库存 | 15.000 |
| └ minStock | Decimal | 最低库存 | 20.000 |
| └ maxStock | Decimal | 最高库存 | 300.000 |
| └ remainingDays | Integer | 剩余保质期天数 | 5 |
| └ alertTime | String | 预警时间 | 2026-03-16 10:00:00 |
| └ status | String | 预警状态：pending/handled | pending |
| pageNum | Integer | 当前页码 | 1 |
| pageSize | Integer | 每页条数 | 20 |
| total | Long | 总记录数 | 20 |
| totalPages | Integer | 总页数 | 1 |

- **异常场景**：
  - [403]：无数据权限 → msg："无权限访问库存预警数据"

---

#### 库存汇总报表
- **接口路径**：`/api/v1/wms/inventory-summary`
- **请求方法**：GET
- **请求头**：Authorization: Bearer {token}
- **请求参数**：无

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| dashboard | Object | 数据看板 | - |
| └ totalStock | Decimal | 库存总数 | 5000.000 |
| └ totalVariety | Integer | 库存品种 | 150 |
| └ alertMaterialCount | Integer | 预警物料数量 | 15 |
| └ stockValue | Decimal | 库存价值 | 150000.00 |
| summary | Array | 库存明细汇总 | - |
| └ materialId | Long | 物料ID | 1 |
| └ materialName | String | 物料名称 | 鲜鸡蛋 |
| └ materialSpec | String | 规格 | 500g/盒 |
| └ categoryName | String | 类别 | 蛋类 |
| └ currentStock | Decimal | 当前库存 | 50.000 |
| └ minStock | Decimal | 库存下限 | 20.000 |
| └ maxStock | Decimal | 库存上限 | 300.000 |
| └ warehouseName | String | 所在仓库 | 一号冷库 |
| └ stockStatus | String | 物料库存状态 | normal |

- **异常场景**：
  - [403]：无数据权限 → msg："无权限访问库存汇总数据"

---

#### 新增盘点表
- **接口路径**：`/api/v1/wms/stocktake-orders`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| warehouseId | Long | 是 | 盘点仓库ID | 1 | 盘点仓库 |
| positionId | Long | 否 | 盘点仓位ID（不传则盘点整个仓库） | 1 | 盘点仓位 |
| stocktakeType | String | 是 | 盘点类型：regular/emergency | regular | 盘点类型 |
| remark | String | 否 | 备注 | 年度盘点 | 备注 |
| items | Array | 是 | 盘点物料明细列表 | - | 盘点物料明细 |
| └ materialId | Long | 是 | 物料ID | 1 | 物料标识 |
| └ materialName | String | 是 | 物料名称 | 鲜鸡蛋 | 物料名称 |
| └ materialSpec | String | 否 | 物料规格 | 500g/盒 | 物料规格 |
| └ batchNo | String | 否 | 批次号 | LOT-20260317001 | 批次号 |
| └ positionId | Long | 否 | 所在仓位ID | 1 | 所在仓位 |
| └ positionCode | String | 否 | 所在仓位编码 | A-01-01 | 仓位编码 |
| └ systemQty | Decimal | 是 | 系统库存数 | 100.000 | 系统库存 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 盘点单ID | 1 |
| stocktakeNo | String | 盘点单编号 | ST-20260317001 |
| warehouseId | Long | 盘点仓库ID | 1 |
| warehouseName | String | 盘点仓库名称 | 一号冷库 |
| status | String | 盘点状态 | pending |
| itemCount | Integer | 盘点物料数 | 10 |
| createdAt | DateTime | 创建时间 | 2026-03-17 10:00:00 |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：盘点仓库不能为空"
  - [422]：仓库不存在 → msg："所选仓库不存在"
  - [422]：仓位不属于该仓库 → msg："所选仓位不属于该仓库"
  - [403]：无创建权限 → msg："无权限创建盘点表"

---

#### 编辑盘点表
- **接口路径**：`/api/v1/wms/stocktake-orders/{id}`
- **请求方法**：PUT
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| id | Long | 是 | 盘点单ID（路径参数） | 1 | 盘点单标识 |
| items | Array | 否 | 盘点物料明细列表 | - | 盘点物料明细 |
| └ materialId | Long | 是 | 物料ID | 1 | 物料标识 |
| └ materialName | String | 是 | 物料名称 | 鲜鸡蛋 | 物料名称 |
| └ materialSpec | String | 否 | 物料规格 | 500g/盒 | 物料规格 |
| └ batchNo | String | 否 | 批次号 | LOT-20260317001 | 批次号 |
| └ positionId | Long | 否 | 所在仓位ID | 1 | 所在仓位 |
| └ positionCode | String | 否 | 所在仓位编码 | A-01-01 | 仓位编码 |
| └ systemQty | Decimal | 是 | 系统库存数 | 100.000 | 系统库存 |
| └ actualQty | Decimal | 否 | 实际库存数 | 98.000 | 实际库存 |
| └ diffQty | Decimal | 否 | 差异数量 | -2.000 | 差异数量 |
| └ diffReason | String | 否 | 差异原因 | 计量误差 | 差异原因 |
| remark | String | 否 | 备注 | 调整盘点明细 | 备注 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 盘点单ID | 1 |
| stocktakeNo | String | 盘点单编号 | ST-20260317001 |
| status | String | 盘点状态 | pending |
| itemCount | Integer | 盘点物料数 | 10 |
| updatedAt | DateTime | 更新时间 | 2026-03-17 10:30:00 |

- **异常场景**：
  - [404]：盘点单不存在 → msg："盘点单不存在"
  - [422]：盘点单状态不允许编辑 → msg："只有待开始或盘点中的盘点单可以编辑"
  - [403]：无编辑权限 → msg："无权限编辑该盘点单"

---

#### 提交盘点表审核
- **接口路径**：`/api/v1/wms/stocktake-orders/{id}/submit`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| id | Long | 是 | 盘点单ID（路径参数） | 1 | 盘点单标识 |
| items | Array | 是 | 盘点数据列表 | - | 盘点数据 |
| └ materialId | Long | 是 | 物料ID | 1 | 物料标识 |
| └ actualQty | Decimal | 是 | 实际库存数 | 98.000 | 实际库存 |
| └ diffQty | Decimal | 是 | 差异数量（系统-实际） | -2.000 | 差异数量 |
| └ diffReason | String | 否 | 差异原因 | 计量误差 | 差异原因 |
| attachments | Array | 否 | 附件URL列表 | ["http://..."] | 附件 |
| remark | String | 否 | 备注 | 盘点完成 | 备注 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 盘点单ID | 1 |
| stocktakeNo | String | 盘点单编号 | ST-20260317001 |
| status | String | 盘点状态 | pending_approve |
| surplusQty | Decimal | 盘盈数量 | 5.000 |
| deficitQty | Decimal | 盘亏数量 | 3.000 |
| surplusAmount | Decimal | 盘盈金额 | 500.00 |
| deficitAmount | Decimal | 盘亏金额 | 300.00 |
| submittedAt | DateTime | 提交时间 | 2026-03-17 11:00:00 |

- **异常场景**：
  - [404]：盘点单不存在 → msg："盘点单不存在"
  - [422]：盘点单状态不允许提交 → msg："只有盘点中的盘点单可以提交审核"
  - [422]：存在未填写实际库存的物料 → msg："请填写所有物料的实际库存数"
  - [403]：无提交权限 → msg："无权限提交该盘点单"

---

#### 查看盘点表详情
- **接口路径**：`/api/v1/wms/stocktake-orders/{id}`
- **请求方法**：GET
- **请求头**：Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| id | Long | 是 | 盘点单ID（路径参数） | 1 | 盘点单标识 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 盘点单ID | 1 |
| stocktakeNo | String | 盘点单编号 | ST-20260317001 |
| stocktakeType | String | 盘点类型 | regular |
| stocktakeTypeName | String | 盘点类型名称 | 周期盘点 |
| warehouseId | Long | 盘点仓库ID | 1 |
| warehouseName | String | 盘点仓库名称 | 一号冷库 |
| positionId | Long | 盘点仓位ID | 1 |
| positionCode | String | 盘点仓位编码 | A-01-01 |
| status | String | 盘点状态 | pending_approve |
| statusName | String | 盘点状态名称 | 待审核 |
| startAt | DateTime | 盘点开始时间 | 2026-03-17 09:00:00 |
| endAt | DateTime | 盘点结束时间 | 2026-03-17 11:00:00 |
| surplusQty | Decimal | 盘盈数量 | 5.000 |
| deficitQty | Decimal | 盘亏数量 | 3.000 |
| surplusAmount | Decimal | 盘盈金额 | 500.00 |
| deficitAmount | Decimal | 盘亏金额 | 300.00 |
| remark | String | 备注 | - |
| attachments | Array | 附件列表 | ["http://..."] |
| createdBy | Long | 创建人ID | 10 |
| createdByName | String | 创建人姓名 | 张三 |
| createdAt | DateTime | 创建时间 | 2026-03-17 08:00:00 |
| approvedBy | Long | 审核人ID | 5 |
| approvedByName | String | 审核人姓名 | 管理员 |
| approvedAt | DateTime | 审核时间 | 2026-03-17 14:00:00 |
| approveRemark | String | 审核备注 | - |
| items | Array | 盘点物料明细列表 | - |
| └ id | Long | 明细ID | 1 |
| └ materialId | Long | 物料ID | 1 |
| └ materialName | String | 物料名称 | 鲜鸡蛋 |
| └ materialSpec | String | 物料规格 | 500g/盒 |
| └ batchNo | String | 批次号 | LOT-20260317001 |
| └ positionId | Long | 所在仓位ID | 1 |
| └ positionCode | String | 所在仓位编码 | A-01-01 |
| └ systemQty | Decimal | 系统库存数 | 100.000 |
| └ actualQty | Decimal | 实际库存数 | 98.000 |
| └ diffQty | Decimal | 差异数量 | -2.000 |
| └ diffReason | String | 差异原因 | 计量误差 |
| └ unitCost | Decimal | 单位成本 | 5.00 |
| └ diffAmount | Decimal | 差异金额 | -10.00 |
| └ itemRemark | String | 明细备注 | - |

- **异常场景**：
  - [404]：盘点单不存在 → msg："盘点单不存在"
  - [403]：无查看权限 → msg："无权限查看该盘点单"

---

#### 审核盘点表
- **接口路径**：`/api/v1/wms/stocktake-orders/{id}/audit`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| id | Long | 是 | 盘点单ID（路径参数） | 1 | 盘点单标识 |
| result | String | 是 | 审核结果：approved/rejected | approved | 审核操作 |
| approveRemark | String | 否 | 审核意见 | 盘点数据准确，同意调整 | 审核意见 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 盘点单ID | 1 |
| stocktakeNo | String | 盘点单编号 | ST-20260317001 |
| status | String | 盘点状态 | approved |
| approvedBy | Long | 审核人ID | 5 |
| approvedByName | String | 审核人姓名 | 管理员 |
| approvedAt | DateTime | 审核时间 | 2026-03-17 14:00:00 |
| inventoryAdjusted | Boolean | 是否已调整库存 | true |
| surplusQty | Decimal | 盘盈数量 | 5.000 |
| deficitQty | Decimal | 盘亏数量 | 3.000 |

- **异常场景**：
  - [404]：盘点单不存在 → msg："盘点单不存在"
  - [422]：盘点单状态不允许审核 → msg："只有待审核的盘点单可以审核"
  - [403]：无审核权限 → msg："无权限审核盘点单"

---

#### 盘点历史列表
- **接口路径**：`/api/v1/wms/stocktake-orders`
- **请求方法**：GET
- **请求头**：Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| pageNum | Integer | 否 | 页码（默认1） | 1 | 分页查询 |
| pageSize | Integer | 否 | 每页条数（默认20，最大100） | 20 | 分页查询 |
| stocktakeNo | String | 否 | 盘点单编号（模糊搜索） | ST-2026 | 盘点单编号 |
| warehouseId | Long | 否 | 盘点仓库ID | 1 | 仓库筛选 |
| status | String | 否 | 盘点状态：pending/in_progress/pending_approve/approved/rejected | approved | 状态筛选 |
| stocktakeType | String | 否 | 盘点类型：regular/emergency | regular | 类型筛选 |
| startDate | Date | 否 | 盘点开始日期（起） | 2026-03-01 | 日期筛选 |
| endDate | Date | 否 | 盘点开始日期（止） | 2026-03-17 | 日期筛选 |
| createdBy | Long | 否 | 创建人ID | 10 | 创建人筛选 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| list | Array | 盘点单列表 | - |
| └ id | Long | 盘点单ID | 1 |
| └ stocktakeNo | String | 盘点单编号 | ST-20260317001 |
| └ stocktakeType | String | 盘点类型 | regular |
| └ stocktakeTypeName | String | 盘点类型名称 | 周期盘点 |
| └ warehouseName | String | 盘点仓库名称 | 一号冷库 |
| └ positionCode | String | 盘点仓位编码 | A-01-01 |
| └ startAt | DateTime | 盘点日期 | 2026-03-17 09:00:00 |
| └ endAt | DateTime | 盘点结束时间 | 2026-03-17 11:00:00 |
| └ createdByName | String | 盘点人 | 张三 |
| └ itemCount | Integer | 盘点物料数 | 10 |
| └ surplusQty | Decimal | 盘盈数量 | 5.000 |
| └ deficitQty | Decimal | 盘亏数量 | 3.000 |
| └ status | String | 盘点状态 | approved |
| └ statusName | String | 盘点状态名称 | 已审核 |
| └ approvedByName | String | 审核人姓名 | 管理员 |
| pageNum | Integer | 当前页码 | 1 |
| pageSize | Integer | 每页条数 | 20 |
| total | Long | 总记录数 | 50 |
| totalPages | Integer | 总页数 | 3 |

- **异常场景**：
  - [403]：无数据权限 → msg："无权限访问盘点数据"

---

### 5.5 菜谱营养模块

#### 菜谱可视化看板
- **接口路径**：`/api/v1/recipe/dashboard`
- **请求方法**：GET
- **请求头**：Authorization: Bearer {token}
- **请求参数**：无

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| totalRecipes | Integer | 菜谱总数 | 150 |
| ingredientCoverage | Decimal | 食材覆盖率 | 85.50 |
| nutritionPassRate | Decimal | 营养达标率 | 92.00 |
| nutritionDistribution | Object | 营养素分布 | - |
| └ proteinPercent | Decimal | 蛋白质占比 | 25.00 |
| └ carbsPercent | Decimal | 碳水占比 | 50.00 |
| └ fatPercent | Decimal | 脂肪占比 | 25.00 |
| weeklyHotRecipes | Array | 本周热门菜谱TOP5 | - |
| └ recipeId | Long | 菜谱ID | 1 |
| └ recipeName | String | 菜谱名称 | 红烧肉 |
| └ recipeCategory | String | 菜谱类别 | 荤菜 |
| └ serveCount | Integer | 服务次数 | 50 |

- **异常场景**：
  - [403]：无数据权限 → msg："无权限访问菜谱看板数据"

---

#### 菜谱列表
- **接口路径**：`/api/v1/recipe/recipes`
- **请求方法**：GET
- **请求头**：Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| pageNum | Integer | 否 | 页码（默认1） | 1 | 分页查询 |
| pageSize | Integer | 否 | 每页条数（默认20，最大100） | 20 | 分页查询 |
| recipeName | String | 否 | 菜谱名称（模糊搜索） | 红烧肉 | 菜谱列表 |
| categoryId | Long | 否 | 菜谱类别ID | 1 | 类别筛选 |
| status | String | 否 | 菜谱状态：active/inactive | active | 状态筛选 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| list | Array | 菜谱列表 | - |
| └ id | Long | 菜谱ID | 1 |
| └ recipeCode | String | 菜谱编码 | CP001 |
| └ recipeName | String | 菜谱名称 | 红烧肉 |
| └ categoryName | String | 菜谱类别 | 荤菜 |
| └ targetCookTime | Integer | 目标烹饪时长（分钟） | 45 |
| └ targetTemp | Integer | 目标温度（℃） | 85 |
| └ nutritionInfo | Object | 营养成分 | - |
| └ protein | Decimal | 蛋白质（g） | 25.50 |
| └ carbs | Decimal | 碳水（g） | 15.00 |
| └ fat | Decimal | 脂肪（g） | 30.00 |
| └ calories | Integer | 热量 | 450 |
| └ status | String | 菜谱状态 | active |
| └ updatedAt | String | 更新时间 | 2026-03-16 10:00:00 |
| pageNum | Integer | 当前页码 | 1 |
| pageSize | Integer | 每页条数 | 20 |
| total | Long | 总记录数 | 150 |
| totalPages | Integer | 总页数 | 8 |

- **异常场景**：
  - [403]：无数据权限 → msg："无权限访问菜谱数据"

---

#### 菜谱详情
- **接口路径**：`/api/v1/recipe/recipes/{id}`
- **请求方法**：GET
- **请求头**：Authorization: Bearer {token}
- **请求参数**：无

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 菜谱ID | 1 |
| recipeCode | String | 菜谱编码 | CP001 |
| recipeName | String | 菜谱名称 | 红烧肉 |
| categoryId | Long | 菜谱类别ID | 1 |
| categoryName | String | 菜谱类别 | 荤菜 |
| targetCookTime | Integer | 目标烹饪时长（分钟） | 45 |
| targetTemp | Integer | 目标温度（℃） | 85 |
| cookingSteps | String | 制作步骤 | 1. 五花肉切块... |
| nutritionInfo | Object | 营养成分 | - |
| └ protein | Decimal | 蛋白质 | 25.50 |
| └ carbs | Decimal | 碳水 | 15.00 |
| └ fat | Decimal | 脂肪 | 30.00 |
| └ calories | Integer | 热量 | 450 |
| vitaminInfo | Object | 维生素含量 | - |
| └ vitaminA | Decimal | 维生素A（μg） | 50.00 |
| └ vitaminB1 | Decimal | 维生素B1（mg） | 0.50 |
| └ vitaminC | Decimal | 维生素C（mg） | 10.00 |
| ingredients | Array | 所需食材 | - |
| └ materialId | Long | 物料ID | 1 |
| └ materialName | String | 物料名称 | 五花肉 |
| └ materialSpec | String | 物料规格 | 500g/块 |
| └ quantity | Decimal | 用量 | 500.000 |
| └ unit | String | 单位 | g |
| └ protein | Decimal | 蛋白质 | 20.00 |
| └ carbs | Decimal | 碳水 | 5.00 |
| └ fat | Decimal | 脂肪 | 25.00 |
| status | String | 菜谱状态 | active |
| imageUrl | String | 菜谱图片URL | https://xxx.com/recipe.jpg |

- **异常场景**：
  - [404]：菜谱不存在 → msg："菜谱不存在"
  - [403]：无数据权限 → msg："无权限访问该菜谱数据"

---

#### 新增菜谱
- **接口路径**：`/api/v1/recipe/recipes`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| recipeCode | String | 是 | 菜谱编码（唯一） | CP001 | 菜谱编码 |
| recipeName | String | 是 | 菜谱名称（1-100字符） | 红烧肉 | 菜谱名称 |
| categoryId | Long | 是 | 菜谱类别ID | 1 | 菜谱类别 |
| cookingSteps | String | 是 | 制作步骤 | 1. 五花肉切块... | 制作步骤 |
| targetCookTime | Integer | 是 | 目标烹饪时长（分钟） | 45 | 目标时长 |
| targetTemp | Integer | 是 | 目标温度（℃） | 85 | 目标温度 |
| useAiSuggestion | Boolean | 否 | 是否使用AI建议时长/温度 | true | AI建议 |
| ingredients | Array | 是 | 所需食材列表 | - | 食材列表 |
| └ materialId | Long | 是 | 物料ID | 1 | 物料 |
| └ materialSpec | String | 是 | 物料规格 | 500g/块 | 规格 |
| └ quantity | Decimal | 是 | 用量 | 500.000 | 用量 |
| └ unit | String | 是 | 单位 | g | 单位 |
| imageUrl | String | 否 | 菜谱图片URL | https://xxx.com/recipe.jpg | 菜谱图片 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 新增菜谱ID | 1 |
| recipeCode | String | 菜谱编码 | CP001 |
| recipeName | String | 菜谱名称 | 红烧肉 |
| nutritionInfo | Object | AI分析的营养成分（自动生成） | - |
| └ protein | Decimal | 蛋白质 | 25.50 |
| └ carbs | Decimal | 碳水 | 15.00 |
| └ fat | Decimal | 脂肪 | 30.00 |
| └ calories | Integer | 热量 | 450 |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：菜谱名称不能为空"
  - [409]：菜谱编码已存在 → msg："菜谱编码已存在"
  - [400]：食材列表为空 → msg："请至少添加一条食材"
  - [403]：无创建权限 → msg："无权限创建菜谱"

---

#### 编辑菜谱
- **接口路径**：`/api/v1/recipe/recipes/{id}`
- **请求方法**：PUT
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| recipeName | String | 否 | 菜谱名称 | 红烧肉 | 菜谱名称 |
| categoryId | Long | 否 | 菜谱类别ID | 1 | 菜谱类别 |
| cookingSteps | String | 否 | 制作步骤 | 1. 五花肉切块... | 制作步骤 |
| targetCookTime | Integer | 否 | 目标烹饪时长（分钟） | 45 | 目标时长 |
| targetTemp | Integer | 否 | 目标温度（℃） | 85 | 目标温度 |
| ingredients | Array | 否 | 所需食材列表 | - | 食材列表 |
| └ materialId | Long | 是 | 物料ID | 1 | 物料 |
| └ materialSpec | String | 是 | 物料规格 | 500g/块 | 规格 |
| └ quantity | Decimal | 是 | 用量 | 500.000 | 用量 |
| └ unit | String | 是 | 单位 | g | 单位 |
| status | String | 否 | 菜谱状态：active/inactive | active | 菜谱状态 |
| imageUrl | String | 否 | 菜谱图片URL | https://xxx.com/recipe.jpg | 菜谱图片 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 菜谱ID | 1 |
| recipeName | String | 菜谱名称 | 红烧肉 |

- **异常场景**：
  - [404]：菜谱不存在 → msg："菜谱不存在"
  - [403]：无编辑权限 → msg："无权限编辑该菜谱"

---

#### 删除菜谱
- **接口路径**：`/api/v1/recipe/recipes/{id}`
- **请求方法**：DELETE
- **请求头**：Authorization: Bearer {token}
- **请求参数**：无

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| - | - | 无返回数据 | null |

- **异常场景**：
  - [404]：菜谱不存在 → msg："菜谱不存在"
  - [403]：无删除权限 → msg："无权限删除该菜谱"

---

#### AI营养成分分析
- **接口路径**：`/api/v1/recipe/recipes/{id}/ai-nutrition`
- **请求方法**：GET
- **请求头**：Authorization: Bearer {token}
- **请求参数**：无

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| recipeId | Long | 菜谱ID | 1 |
| recipeName | String | 菜谱名称 | 红烧肉 |
| nutritionInfo | Object | 营养成分 | - |
| └ protein | Decimal | 蛋白质 | 25.50 |
| └ carbs | Decimal | 碳水 | 15.00 |
| └ fat | Decimal | 脂肪 | 30.00 |
| └ calories | Integer | 热量 | 450 |
| vitaminInfo | Object | 维生素含量 | - |
| └ vitaminA | Decimal | 维生素A（μg） | 50.00 |
| └ vitaminB1 | Decimal | 维生素B1（mg） | 0.50 |
| └ vitaminC | Decimal | 维生素C（mg） | 10.00 |
| mineralInfo | Object | 矿物质含量 | - |
| └ calcium | Decimal | 钙（mg） | 20.00 |
| └ iron | Decimal | 铁（mg） | 2.50 |
| analysisTime | String | 分析时间 | 2026-03-16 10:00:00 |

- **异常场景**：
  - [404]：菜谱不存在 → msg："菜谱不存在"
  - [403]：无数据权限 → msg："无权限访问该菜谱数据"

---

#### AI智能菜谱优化
- **接口路径**：`/api/v1/recipe/recipes/{id}/ai-optimization`
- **请求方法**：GET
- **请求头**：Authorization: Bearer {token}
- **请求参数**：无

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| recipeId | Long | 菜谱ID | 1 |
| recipeName | String | 菜谱名称 | 红烧肉 |
| comprehensiveDashboard | Object | 综合看板 | - |
| └ costPercentVsAvg | Decimal | 预估食材成本较均价的百分比 | 105.00 |
| └ nutritionScore | Integer | 营养评分 | 85 |
| └ reviewScore | Decimal | 评价评分 | 4.50 |
| └ complaintCount | Integer | 投诉反馈条数（近30天） | 3 |
| costAnalysis | Object | 成本分析 | - |
| └ recentPurchases | Array | 最近采购记录 | - |
| └ materialName | String | 物料名称 | 五花肉 |
| └ unitPrice | Decimal | 单价 | 25.00 |
| └ purchaseDate | String | 采购时间 | 2026-03-10 |
| └ highCostAlerts | Array | 高成本食材预警 | - |
| └ materialName | String | 物料名称 | 五花肉 |
| └ reason | String | 原因 | 价格上涨15% |
| └ currentPrice | Decimal | 当前价格 | 28.00 |
| └ avgPrice | Decimal | 均价 | 24.35 |
| └ aiSuggestion | String | AI建议 | 建议寻找替代供应商 |
| complaintAnalysis | Object | 投诉反馈分析 | - |
| └ tasteIssues | Integer | 口味问题数 | 2 |
| └ qualityIssues | Integer | 质量问题数 | 1 |
| └ portionIssues | Integer | 份量问题数 | 0 |
| └ otherIssues | Integer | 其他问题数 | 0 |
| └ complaintSuggestions | String | 投诉反馈建议 | 建议调整咸度 |
| └ recentReviews | Array | 最近3条评价 | - |
| └ score | Integer | 评分 | 4 |
| └ content | String | 评价内容 | 味道不错 |
| └ reviewTime | String | 评价时间 | 2026-03-15 |
| optimizationSuggestions | Array | AI优化建议 | - |
| └ suggestionName | String | 建议方案名称 | 优化食材采购渠道 |
| └ source | String | 来源 | 成本分析 |
| └ priority | String | 优先级：high/medium/low | high |
| └ description | String | 建议描述 | 当前五花肉采购价格偏高 |
| └ improvementTrend | String | 改善后趋势描述 | 预计可降低成本10% |

- **异常场景**：
  - [404]：菜谱不存在 → msg："菜谱不存在"
  - [403]：无数据权限 → msg："无权限访问该菜谱数据"

---

#### 菜谱计划列表
- **接口路径**：`/api/v1/recipe/plans`
- **请求方法**：GET
- **请求头**：Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| pageNum | Integer | 否 | 页码（默认1） | 1 | 分页查询 |
| pageSize | Integer | 否 | 每页条数（默认20，最大100） | 20 | 分页查询 |
| planDateStart | String | 否 | 计划日期起始 | 2026-03-01 | 日期范围 |
| planDateEnd | String | 否 | 计划日期结束 | 2026-03-31 | 日期范围 |
| status | String | 否 | 计划状态：draft/pending/approved/rejected | pending | 状态筛选 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| list | Array | 菜谱计划列表 | - |
| └ id | Long | 计划ID | 1 |
| └ planNo | String | 计划编号 | CPJH-20260316-00001 |
| └ planDate | String | 菜谱日期 | 2026-03-18 |
| └ recipeCount | Integer | 菜品数量 | 8 |
| └ totalDiners | Integer | 用餐人数 | 500 |
| └ status | String | 菜谱计划状态 | pending |
| └ createdByName | String | 创建人 | 张三 |
| └ createdAt | String | 创建时间 | 2026-03-16 10:00:00 |
| pageNum | Integer | 当前页码 | 1 |
| pageSize | Integer | 每页条数 | 20 |
| total | Long | 总记录数 | 30 |
| totalPages | Integer | 总页数 | 2 |

- **异常场景**：
  - [403]：无数据权限 → msg："无权限访问菜谱计划数据"

---

#### 菜谱计划详情
- **接口路径**：`/api/v1/recipe/plans/{id}`
- **请求方法**：GET
- **请求头**：Authorization: Bearer {token}
- **请求参数**：无

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 计划ID | 1 |
| planNo | String | 计划编号 | CPJH-20260316-00001 |
| planDate | String | 菜谱日期 | 2026-03-18 |
| totalDiners | Integer | 总用餐人数 | 500 |
| recipeCount | Integer | 菜品数量 | 8 |
| status | String | 菜谱计划状态 | approved |
| createdByName | String | 创建人 | 张三 |
| approvedByName | String | 审核人 | 李四 |
| meals | Array | 餐次信息 | - |
| └ mealType | String | 餐次：breakfast/lunch/dinner | lunch |
| └ mealTypeName | String | 餐次名称 | 午餐 |
| └ diners | Integer | 用餐人数 | 500 |
| └ recipes | Array | 菜谱列表 | - |
| └ recipeId | Long | 菜谱ID | 1 |
| └ recipeName | String | 菜谱名称 | 红烧肉 |
| └ nutritionScore | Integer | 营养评分 | 85 |
| └ ingredients | Array | 食材清单 | - |
| └ materialName | String | 物料名称 | 五花肉 |
| └ materialSpec | String | 物料规格 | 500g/块 |
| └ quantity | Decimal | 用量 | 2500.000 |
| └ unit | String | 单位 | g |

- **异常场景**：
  - [404]：菜谱计划不存在 → msg："菜谱计划不存在"
  - [403]：无数据权限 → msg："无权限访问该菜谱计划数据"

---

#### 新增菜谱计划
- **接口路径**：`/api/v1/recipe/plans`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| planDate | String | 是 | 菜谱日期 | 2026-03-18 | 菜谱日期 |
| meals | Array | 是 | 餐次信息 | - | 餐次信息 |
| └ mealType | String | 是 | 餐次：breakfast/lunch/dinner | lunch | 餐次 |
| └ diners | Integer | 是 | 用餐人数 | 500 | 用餐人数 |
| └ recipeIds | Array | 是 | 菜谱ID列表 | [1, 2, 3] | 选择菜谱 |
| useAiRecommend | Boolean | 否 | 是否使用AI推荐菜谱 | true | AI推荐 |
| useAiNutrition | Boolean | 否 | 是否使用AI营养分析 | true | AI营养分析 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 计划ID | 1 |
| planNo | String | 计划编号 | CPJH-20260316-00001 |
| status | String | 计划状态 | draft |
| aiNutritionAnalysis | Object | AI营养分析（useAiNutrition=true时返回） | - |
| └ totalProtein | Decimal | 总蛋白质量 | 12500.00 |
| └ totalCarbs | Decimal | 总碳水量 | 7500.00 |
| └ totalFat | Decimal | 总脂肪量 | 15000.00 |
| └ totalCalories | Integer | 总热量 | 225000 |
| └ nutritionBalance | Decimal | 营养均衡度 | 0.85 |
| └ aiSuggestions | String | AI优化建议 | 建议增加蔬菜类菜品 |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：菜谱日期不能为空"
  - [400]：餐次信息为空 → msg："请至少添加一个餐次"
  - [403]：无创建权限 → msg："无权限创建菜谱计划"

---

#### 编辑菜谱计划
- **接口路径**：`/api/v1/recipe/plans/{id}`
- **请求方法**：PUT
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| planDate | String | 否 | 菜谱日期 | 2026-03-18 | 菜谱日期 |
| meals | Array | 否 | 餐次信息 | - | 餐次信息 |
| └ mealType | String | 是 | 餐次 | lunch | 餐次 |
| └ diners | Integer | 是 | 用餐人数 | 500 | 用餐人数 |
| └ recipeIds | Array | 是 | 菜谱ID列表 | [1, 2, 3] | 选择菜谱 |
| useAiRecommend | Boolean | 否 | 是否使用AI推荐菜谱 | true | AI推荐 |
| useAiNutrition | Boolean | 否 | 是否使用AI营养分析 | true | AI营养分析 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 计划ID | 1 |
| planNo | String | 计划编号 | CPJH-20260316-00001 |

- **异常场景**：
  - [404]：菜谱计划不存在 → msg："菜谱计划不存在"
  - [422]：计划状态不允许编辑 → msg："已审核的计划不能编辑"
  - [403]：无编辑权限 → msg："无权限编辑该菜谱计划"

---

#### 删除菜谱计划
- **接口路径**：`/api/v1/recipe/plans/{id}`
- **请求方法**：DELETE
- **请求头**：Authorization: Bearer {token}
- **请求参数**：无

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| - | - | 无返回数据 | null |

- **异常场景**：
  - [404]：菜谱计划不存在 → msg："菜谱计划不存在"
  - [422]：计划状态不允许删除 → msg："只能删除草稿或待审核状态的计划"
  - [403]：无删除权限 → msg："无权限删除该菜谱计划"

---

#### 提交菜谱计划审核
- **接口路径**：`/api/v1/recipe/plans/{id}/submit`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：无

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 计划ID | 1 |
| status | String | 提交后状态 | pending |

- **异常场景**：
  - [404]：菜谱计划不存在 → msg："菜谱计划不存在"
  - [422]：计划状态不允许提交 → msg："只有草稿状态的计划可以提交"
  - [403]：无提交权限 → msg："无权限提交菜谱计划"

---

#### 审核菜谱计划
- **接口路径**：`/api/v1/recipe/plans/{id}/audit`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| result | String | 是 | 审核结果：approved/rejected | approved | 审核操作 |
| remark | String | 否 | 审核意见 | 菜谱搭配合理，营养均衡 | 审核意见 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 计划ID | 1 |
| status | String | 审核后状态 | approved |
| cookTaskGenerated | Boolean | 是否已生成烹饪任务 | true |

- **异常场景**：
  - [404]：菜谱计划不存在 → msg："菜谱计划不存在"
  - [422]：计划状态不允许审核 → msg："该计划不在待审核状态"
  - [422]：存在库存不足物料 → msg："以下物料库存不足：五花肉、青菜"
  - [403]：无审核权限 → msg："无权限审核菜谱计划"

---

#### AI营养评估
- **接口路径**：`/api/v1/recipe/plans/{id}/ai-nutrition-assessment`
- **请求方法**：GET
- **请求头**：Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| portraitType | String | 否 | 人群膳食画像：elderly/child/patient/general | general | 人群画像 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| planId | Long | 计划ID | 1 |
| portraitInfo | Object | 画像营养分析 | - |
| └ portraitType | String | 人群膳食画像 | general |
| └ nutritionTargets | Object | 人群膳食画像的营养目标 | - |
| └ proteinTarget | Decimal | 蛋白质目标（g） | 75.00 |
| └ carbsTarget | Decimal | 碳水目标（g） | 300.00 |
| └ fatTarget | Decimal | 脂肪目标（g） | 65.00 |
| └ caloriesTarget | Integer | 热量目标 | 2000 |
| └ dietaryRestrictions | String | 饮食限制描述 | 无 |
| selectedRecipeNutrition | Object | 所选菜谱营养分析 | - |
| └ totalProtein | Decimal | 总蛋白质量（含人均量） | 12500.00 |
| └ proteinPerCapita | Decimal | 人均蛋白质量（g） | 25.00 |
| └ totalCarbs | Decimal | 总碳水量（含人均量） | 7500.00 |
| └ carbsPerCapita | Decimal | 人均碳水量（g） | 15.00 |
| └ totalFat | Decimal | 总脂肪量（含人均量） | 15000.00 |
| └ fatPerCapita | Decimal | 人均脂肪量（g） | 30.00 |
| └ totalCalories | Integer | 总热量（含人均量） | 225000 |
| └ caloriesPerCapita | Integer | 人均热量 | 450 |
| nutritionComparison | Array | 营养目标对比 | - |
| └ nutritionName | String | 营养名称 | 蛋白质 |
| └ perCapitaAmount | Decimal | 人均营养量（g） | 25.00 |
| └ targetAmount | Decimal | 人群画像目标量（g） | 75.00 |
| └ comparisonStatus | String | 对比状态：insufficient/adequate/excessive | insufficient |
| nutritionBalanceScore | Object | 营养均衡度评分 | - |
| └ score | Integer | 分数 | 85 |
| └ grade | String | 等级：needs_improvement/good/adequate/excessive | good |
| aiOptimizationSuggestions | String | AI优化建议 | 建议增加蔬菜类菜品以补充维生素 |

- **异常场景**：
  - [404]：菜谱计划不存在 → msg："菜谱计划不存在"
  - [403]：无数据权限 → msg："无权限访问该菜谱计划数据"

---

#### AI智能推荐菜谱
- **接口路径**：`/api/v1/recipe/plans/ai-recommend`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| planDate | String | 是 | 计划日期 | 2026-03-18 | 计划日期 |
| mealType | String | 是 | 餐次：breakfast/lunch/dinner | lunch | 餐次 |
| diners | Integer | 是 | 用餐人数 | 500 | 用餐人数 |
| budgetLimit | Decimal | 否 | 成本预算上限 | 3000.00 | 成本预算 |
| healthConditions | Array | 否 | 就餐人员健康状况 | ["diabetes","hypertension"] | 健康状况 |
| excludeRecipeIds | Array | 否 | 排除菜谱ID列表（近期已使用） | [1, 2, 3] | 排除已用 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| recommendStats | Object | 推荐统计 | - |
| └ recommendCount | Integer | 推荐菜品数 | 6 |
| └ estimateTotalCost | Decimal | 预估总成本 | 2500.00 |
| └ perCapitaCost | Decimal | 人均成本 | 5.00 |
| └ budgetRemaining | Decimal | 预算结余 | 500.00 |
| recommendRecipes | Array | 推荐菜谱明细 | - |
| └ mealType | String | 餐次 | lunch |
| └ recipes | Array | 菜谱推荐 | - |
| └ recipeId | Long | 菜谱ID | 1 |
| └ recipeName | String | 菜谱名称 | 清炒时蔬 |
| └ ingredients | String | 食材 | 青菜、蒜 |
| └ perCapitaNutrition | Object | 人均营养成分 | - |
| └ protein | Decimal | 蛋白质（g） | 5.00 |
| └ carbs | Decimal | 碳水（g） | 10.00 |
| └ fat | Decimal | 脂肪（g） | 3.00 |
| └ calories | Integer | 热量 | 85 |
| └ budgetAmount | Decimal | 预算金额 | 300.00 |
| nutritionOverview | Object | 推荐方案营养总览 | - |
| └ totalProtein | Decimal | 蛋白质总数（g） | 12500.00 |
| └ totalCarbs | Decimal | 碳水总数（g） | 25000.00 |
| └ totalFat | Decimal | 脂肪总数（g） | 7500.00 |
| └ totalCalories | Integer | 热量总数 | 225000 |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：计划日期不能为空"
  - [429]：请求频率过高 → msg："AI分析请求频率过高，请稍后再试"

---

#### 菜谱计划调整申请
- **接口路径**：`/api/v1/recipe/plans/{id}/adjust`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| adjustType | String | 是 | 调整类型：add/remove/replace | replace | 调整类型 |
| reason | String | 是 | 调整原因 | 原菜品食材缺货 | 调整原因 |
| meals | Array | 是 | 调整餐次信息 | - | 餐次信息 |
| └ mealType | String | 是 | 选择餐次 | lunch | 餐次 |
| └ diners | Integer | 是 | 用餐人数 | 500 | 用餐人数 |
| └ recipeIds | Array | 是 | 菜谱ID列表 | [1, 2, 3] | 菜谱列表 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 申请ID | 1 |
| planId | Long | 计划ID | 1 |
| status | String | 申请状态 | pending |

- **异常场景**：
  - [404]：菜谱计划不存在 → msg："菜谱计划不存在"
  - [422]：计划状态不允许调整 → msg："只有已审核的计划可以申请调整"
  - [403]：无调整权限 → msg："无权限调整菜谱计划"

---

#### 调整申请审核
- **接口路径**：`/api/v1/recipe/plan-adjustments/{id}/audit`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| result | String | 是 | 审核结果：approved/rejected | approved | 审核操作 |
| remark | String | 否 | 审核意见 | 调整合理，批准执行 | 审核意见 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 申请ID | 1 |
| planId | Long | 计划ID | 1 |
| status | String | 审核后状态 | approved |
| cookTaskUpdated | Boolean | 是否已更新烹饪任务 | true |

- **异常场景**：
  - [404]：调整申请不存在 → msg："调整申请不存在"
  - [422]：申请状态不允许审核 → msg："该申请不在待审核状态"
  - [403]：无审核权限 → msg："无权限审核调整申请"

---

### 5.6 烹饪与留样模块

---

#### 烹饪记录首页
- **接口路径**：`/api/v1/cook/dashboard`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| orgId | Long | 是 | 组织ID | 1001 | 数据隔离 |
| mealType | String | 否 | 餐次：breakfast/lunch/dinner/supper | lunch | 餐次筛选 |
| planDate | Date | 否 | 计划日期 | 2026-03-17 | 日期筛选 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| totalDishes | Integer | 菜品总数 | 15 |
| cookingCount | Integer | 烹饪中数量 | 3 |
| completedCount | Integer | 已完成数量 | 10 |
| tempAbnormalCount | Integer | 温度异常数量 | 1 |
| completionRate | Decimal | 今日完成进度百分比 | 66.67 |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：组织ID不能为空"
  - [403]：无数据访问权限 → msg："无权限访问该组织数据"

---

#### 烹饪任务列表
- **接口路径**：`/api/v1/cook/tasks`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| orgId | Long | 是 | 组织ID | 1001 | 数据隔离 |
| pageNum | Integer | 否 | 页码，默认1 | 1 | 分页 |
| pageSize | Integer | 否 | 每页条数，默认20 | 20 | 分页 |
| status | String | 否 | 状态：pending/in_progress/completed/cancelled | in_progress | 状态筛选 |
| mealType | String | 否 | 餐次 | lunch | 餐次筛选 |
| planDate | Date | 否 | 计划日期 | 2026-03-17 | 日期筛选 |
| chefId | Long | 否 | 厨师ID | 10 | 厨师筛选 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| total | Long | 总记录数 | 15 |
| list | Array | 任务列表 | - |
| └ id | Long | 任务ID | 1 |
| └ taskNo | String | 任务编号 | CT-20260317001 |
| └ menuName | String | 菜谱名称 | 红烧肉 |
| └ plannedQty | Integer | 计划份数 | 100 |
| └ actualQty | Integer | 实际完成份数 | 95 |
| └ assignedChefName | String | 厨师姓名 | 张三 |
| └ status | String | 状态 | in_progress |
| └ startTime | DateTime | 开始时间 | 2026-03-17 10:30:00 |
| └ cookingDuration | Integer | 烹饪时长（分钟） | 45 |
| └ qualityScore | Decimal | 质量评分 | 88.50 |
| └ location | String | 烹饪位置 | 1号灶台 |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：组织ID不能为空"

---

#### 烹饪任务详情
- **接口路径**：`/api/v1/cook/tasks/{id}`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| id | Long | 是 | 任务ID（路径参数） | 1 | 任务标识 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 任务ID | 1 |
| taskNo | String | 任务编号 | CT-20260317001 |
| planId | Long | 菜谱计划ID | 5 |
| menuId | Long | 菜谱ID | 10 |
| menuName | String | 菜谱名称 | 红烧肉 |
| plannedQty | Integer | 计划份数 | 100 |
| actualQty | Integer | 实际完成份数 | 95 |
| assignedChefId | Long | 指派厨师ID | 10 |
| assignedChefName | String | 厨师姓名 | 张三 |
| status | String | 状态 | in_progress |
| startTime | DateTime | 开始时间 | 2026-03-17 10:30:00 |
| endTime | DateTime | 完成时间 | 2026-03-17 11:15:00 |
| cookingDuration | Integer | 实际烹饪时长（分钟） | 45 |
| standardDuration | Integer | 标准时长（分钟） | 40 |
| targetTemp | Decimal | 要求温度（℃） | 75.0 |
| currentTemp | Decimal | 当前温度（℃） | 72.5 |
| tempDeviation | Decimal | 温度偏差（℃） | -2.5 |
| location | String | 烹饪位置 | 1号灶台 |
| ingredients | Array | 食材列表 | - |
| └ materialName | String | 食材名称 | 五花肉 |
| └ quantity | Decimal | 用量 | 5.0 |
| └ unit | String | 单位 | kg |
| aiViolationCount | Integer | AI违规次数 | 0 |
| violationDetails | Array | 违规详情 | - |
| qualityScore | Decimal | 质量评分 | 88.50 |
| remark | String | 备注 | - |

- **异常场景**：
  - [404]：任务不存在 → msg："烹饪任务不存在"

---

#### 开始烹饪
- **接口路径**：`/api/v1/cook/tasks/{id}/start`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| id | Long | 是 | 任务ID（路径参数） | 1 | 任务标识 |
| location | String | 否 | 烹饪位置 | 1号灶台 | 烹饪位置 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 任务ID | 1 |
| taskNo | String | 任务编号 | CT-20260317001 |
| status | String | 状态 | in_progress |
| startTime | DateTime | 开始时间 | 2026-03-17 10:30:00 |

- **异常场景**：
  - [404]：任务不存在 → msg："烹饪任务不存在"
  - [422]：任务状态不允许开始 → msg："只有待开始的烹饪任务可以开始"
  - [403]：非指派厨师 → msg："只有指派厨师可以开始烹饪"

---

#### 烹饪完成
- **接口路径**：`/api/v1/cook/tasks/{id}/complete`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| id | Long | 是 | 任务ID（路径参数） | 1 | 任务标识 |
| actualQty | Integer | 是 | 实际完成份数 | 100 | 实际份数 |
| remark | String | 否 | 备注 | 完成情况良好 | 备注 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 任务ID | 1 |
| taskNo | String | 任务编号 | CT-20260317001 |
| status | String | 状态 | completed |
| endTime | DateTime | 完成时间 | 2026-03-17 11:15:00 |
| cookingDuration | Integer | 烹饪时长（分钟） | 45 |
| qualityScore | Decimal | 质量评分 | 88.50 |
| sampleRequired | Boolean | 是否需要留样 | true |

- **异常场景**：
  - [404]：任务不存在 → msg："烹饪任务不存在"
  - [422]：任务状态不允许完成 → msg："只有进行中的烹饪任务可以完成"
  - [400]：实际份数不能为空 → msg："实际完成份数不能为空"

---

#### 温度曲线数据
- **接口路径**：`/api/v1/cook/tasks/{id}/temperature`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| id | Long | 是 | 任务ID（路径参数） | 1 | 任务标识 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| taskId | Long | 任务ID | 1 |
| taskNo | String | 任务编号 | CT-20260317001 |
| menuName | String | 菜谱名称 | 红烧肉 |
| targetTemp | Decimal | 目标温度（℃） | 75.0 |
| standardDuration | Integer | 标准时长（分钟） | 40 |
| tempRecords | Array | 温度记录列表 | - |
| └ recordTime | DateTime | 采集时间 | 2026-03-17 10:30:00 |
| └ temperature | Decimal | 温度值（℃） | 72.5 |
| └ isAbnormal | Boolean | 是否异常 | false |
| abnormalReason | String | 异常原因 | - |

- **异常场景**：
  - [404]：任务不存在 → msg："烹饪任务不存在"

---

#### AI烹饪过程智能监控
- **接口路径**：`/api/v1/cook/tasks/{id}/ai-monitor`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| id | Long | 是 | 任务ID（路径参数） | 1 | 任务标识 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| taskId | Long | 任务ID | 1 |
| menuName | String | 菜谱名称 | 红烧肉 |
| targetTemp | Decimal | 要求温度（℃） | 75.0 |
| currentTemp | Decimal | 当前温度（℃） | 72.5 |
| tempStatus | String | 温度状态：normal/warning/abnormal | normal |
| standardDuration | Integer | 标准时长（分钟） | 40 |
| currentDuration | Integer | 当前时长（分钟） | 35 |
| durationStatus | String | 时长状态：normal/warning/abnormal | normal |
| foodSafetyStatus | String | 食品安全状态：pass/warning/fail | pass |
| violationCount | Integer | 违规次数 | 0 |
| violations | Array | 违规记录列表 | - |
| └ violationType | String | 违规类型 | temperature_low |
| └ violationTime | DateTime | 违规时间 | 2026-03-17 10:45:00 |
| └ description | String | 违规描述 | 温度低于标准温度5℃ |
| suggestions | Array | 优化建议 | - |

- **异常场景**：
  - [404]：任务不存在 → msg："烹饪任务不存在"
  - [422]：任务未开始 → msg："烹饪任务尚未开始，无法获取监控数据"

---

#### 留样管理首页
- **接口路径**：`/api/v1/sample/dashboard`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| orgId | Long | 是 | 组织ID | 1001 | 数据隔离 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| totalSamples | Integer | 总留样数 | 120 |
| pendingDisposal | Integer | 待销样数 | 5 |
| disposed | Integer | 已销样数 | 110 |
| overdue | Integer | 已过期数 | 2 |
| todaySampled | Integer | 今日新增留样 | 8 |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：组织ID不能为空"
  - [403]：无数据访问权限 → msg："无权限访问该组织数据"

---

#### 留样列表
- **接口路径**：`/api/v1/sample/records`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| orgId | Long | 是 | 组织ID | 1001 | 数据隔离 |
| pageNum | Integer | 否 | 页码，默认1 | 1 | 分页 |
| pageSize | Integer | 否 | 每页条数，默认20 | 20 | 分页 |
| status | String | 否 | 状态：sampled/pending_disposal/disposed/overdue | pending_disposal | 状态筛选 |
| sampleDate | Date | 否 | 留样日期 | 2026-03-17 | 日期筛选 |
| mealType | String | 否 | 餐次 | lunch | 餐次筛选 |
| menuName | String | 否 | 菜谱名称（模糊） | 红烧肉 | 名称筛选 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| total | Long | 总记录数 | 120 |
| list | Array | 留样列表 | - |
| └ id | Long | 留样ID | 1 |
| └ sampleNo | String | 留样编号 | SP-20260317001 |
| └ menuName | String | 菜谱名称 | 红烧肉 |
| └ taskId | Long | 烹饪任务ID | 1 |
| └ sampleDate | Date | 留样日期 | 2026-03-17 |
| └ mealType | String | 餐次 | lunch |
| └ sampleWeight | Decimal | 留样重量（克） | 125.00 |
| └ storageLocation | String | 存储位置 | 1号冷藏柜A层 |
| └ sampledByName | String | 留样人 | 李四 |
| └ sampledAt | DateTime | 留样时间 | 2026-03-17 11:30:00 |
| └ disposalDueAt | DateTime | 应销样时间 | 2026-03-19 11:30:00 |
| └ aiQualityScore | Decimal | AI质量评分 | 88.50 |
| └ status | String | 状态 | pending_disposal |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：组织ID不能为空"

---

#### 新增留样
- **接口路径**：`/api/v1/sample/records`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| taskId | Long | 否 | 关联烹饪任务ID | 1 | 关联烹饪记录 |
| menuId | Long | 是 | 菜谱ID | 10 | 菜谱标识 |
| menuName | String | 是 | 菜谱名称 | 红烧肉 | 菜谱名称 |
| sampleDate | Date | 是 | 留样日期 | 2026-03-17 | 留样日期 |
| mealType | String | 是 | 餐次 | lunch | 餐次 |
| sampleWeight | Decimal | 是 | 留样重量（克） | 125.00 | 留样重量 |
| sampleImages | Array | 是 | 留样照片URL列表 | ["http://..."] | 留样附件 |
| storageLocation | String | 是 | 存储位置 | 1号冷藏柜A层 | 存储位置 |
| storageTemp | Decimal | 否 | 存储温度（℃） | 4.0 | 存储温度 |
| remark | String | 否 | 备注 | - | 备注 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 留样ID | 1 |
| sampleNo | String | 留样编号 | SP-20260317001 |
| disposalDueAt | DateTime | 应销样时间 | 2026-03-19 11:30:00 |
| aiQualityScore | Decimal | AI质量评分 | 88.50 |
| aiAnalysisResult | Object | AI分析结果 | - |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：菜谱ID不能为空"
  - [422]：烹饪任务不存在 → msg："关联的烹饪任务不存在"
  - [429]：请求频率过高 → msg："AI分析请求频率过高，请稍后再试"

---

#### 留样详情
- **接口路径**：`/api/v1/sample/records/{id}`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| id | Long | 是 | 留样ID（路径参数） | 1 | 留样标识 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 留样ID | 1 |
| sampleNo | String | 留样编号 | SP-20260317001 |
| taskId | Long | 关联烹饪任务ID | 1 |
| taskNo | String | 烹饪任务编号 | CT-20260317001 |
| menuId | Long | 菜谱ID | 10 |
| menuName | String | 菜谱名称 | 红烧肉 |
| sampleDate | Date | 留样日期 | 2026-03-17 |
| mealType | String | 餐次 | lunch |
| status | String | 状态 | pending_disposal |
| sampleWeight | Decimal | 留样重量（克） | 125.00 |
| sampleImages | Array | 留样照片 | ["http://..."] |
| storageLocation | String | 存储位置 | 1号冷藏柜A层 |
| storageTemp | Decimal | 存储温度（℃） | 4.0 |
| sampledBy | Long | 留样人ID | 10 |
| sampledByName | String | 留样人姓名 | 李四 |
| sampledAt | DateTime | 留样时间 | 2026-03-17 11:30:00 |
| disposalDueAt | DateTime | 应销样时间 | 2026-03-19 11:30:00 |
| aiQualityScore | Decimal | AI质量评分 | 88.50 |
| aiStarLevel | Integer | AI星级 | 4 |
| aiDimensionScores | Object | AI评分维度 | - |
| └ colorScore | Decimal | 色泽评分 | 90.0 |
| └ shapeScore | Decimal | 形态评分 | 85.0 |
| └ donenessScore | Decimal | 熟度评分 | 88.0 |
| aiDimensionAnalysis | Object | 维度分析 | - |
| └ colorAnalysis | String | 色泽分析 | 色泽金黄，状态良好 |
| └ shapeAnalysis | String | 形态分析 | 形态完整，切块均匀 |
| └ donenessAnalysis | String | 熟度分析 | 熟度适中，火候恰当 |
| aiSuggestions | Array | 优化建议 | ["可适当减少烹饪时间"] |
| disposalBy | Long | 销样人ID | - |
| disposalByName | String | 销样人姓名 | - |
| disposalAt | DateTime | 销样时间 | - |
| disposalImages | Array | 销样照片 | - |
| disposalRemark | String | 销样备注 | - |

- **异常场景**：
  - [404]：留样记录不存在 → msg："留样记录不存在"

---

#### AI智能评估
- **接口路径**：`/api/v1/sample/records/{id}/ai-evaluate`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| id | Long | 是 | 留样ID（路径参数） | 1 | 留样标识 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 留样ID | 1 |
| finalScore | Decimal | 最终得分 | 88.50 |
| starLevel | Integer | 最终星级（1-5） | 4 |
| dimensionScores | Object | 评分维度分数 | - |
| └ colorScore | Decimal | 色泽评分 | 90.0 |
| └ shapeScore | Decimal | 形态评分 | 85.0 |
| └ donenessScore | Decimal | 熟度评分 | 88.0 |
| dimensionAnalysis | Object | 评分维度分析 | - |
| └ colorAnalysis | String | 色泽分析 | 色泽金黄，状态良好 |
| └ shapeAnalysis | String | 形态分析 | 形态完整，切块均匀 |
| └ donenessAnalysis | String | 熟度分析 | 熟度适中，火候恰当 |
| suggestions | Array | 优化建议 | ["可适当减少烹饪时间"] |

- **异常场景**：
  - [404]：留样记录不存在 → msg："留样记录不存在"
  - [422]：无留样照片 → msg："留样照片不存在，无法进行AI评估"
  - [429]：请求频率过高 → msg："AI分析请求频率过高，请稍后再试"

---

#### 销样提醒列表
- **接口路径**：`/api/v1/sample/disposal-reminders`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| orgId | Long | 是 | 组织ID | 1001 | 数据隔离 |
| pageNum | Integer | 否 | 页码，默认1 | 1 | 分页 |
| pageSize | Integer | 否 | 每页条数，默认20 | 20 | 分页 |
| status | String | 否 | 状态：pending_disposal/overdue | pending_disposal | 状态筛选 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| total | Long | 总记录数 | 5 |
| list | Array | 销样提醒列表 | - |
| └ id | Long | 留样ID | 1 |
| └ sampleNo | String | 留样编号 | SP-20260315001 |
| └ taskId | Long | 关联烹饪任务ID | 1 |
| └ menuName | String | 菜谱名称 | 红烧肉 |
| └ sampleDate | Date | 留样日期 | 2026-03-15 |
| └ sampledAt | DateTime | 留样时间 | 2026-03-15 11:30:00 |
| └ storageLocation | String | 存储位置 | 1号冷藏柜A层 |
| └ disposalDueAt | DateTime | 应销样时间 | 2026-03-17 11:30:00 |
| └ status | String | 状态 | pending_disposal |
| └ isOverdue | Boolean | 是否超期 | false |
| └ remainHours | Integer | 剩余小时数 | 2 |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：组织ID不能为空"

---

#### 销样执行
- **接口路径**：`/api/v1/sample/records/{id}/disposal`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| id | Long | 是 | 留样ID（路径参数） | 1 | 留样标识 |
| disposalImages | Array | 是 | 销样照片URL列表 | ["http://..."] | 销样附件 |
| disposalRemark | String | 否 | 销样备注 | 已按规定销毁 | 销样备注 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 留样ID | 1 |
| sampleNo | String | 留样编号 | SP-20260315001 |
| status | String | 状态 | disposed |
| disposalBy | Long | 销样人ID | 10 |
| disposalByName | String | 销样人姓名 | 李四 |
| disposalAt | DateTime | 销样时间 | 2026-03-17 11:00:00 |

- **异常场景**：
  - [404]：留样记录不存在 → msg："留样记录不存在"
  - [422]：留样状态不允许销样 → msg："该留样已销样或状态异常"
  - [400]：销样照片不能为空 → msg："销样照片不能为空"

---

#### 销样详情
- **接口路径**：`/api/v1/sample/records/{id}/disposal`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| id | Long | 是 | 留样ID（路径参数） | 1 | 留样标识 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 留样ID | 1 |
| sampleNo | String | 留样编号 | SP-20260315001 |
| taskId | Long | 关联烹饪任务ID | 1 |
| taskNo | String | 烹饪任务编号 | CT-20260315001 |
| menuName | String | 菜谱名称 | 红烧肉 |
| sampleDate | Date | 留样日期 | 2026-03-15 |
| sampledAt | DateTime | 留样时间 | 2026-03-15 11:30:00 |
| disposalBy | Long | 销样人ID | 10 |
| disposalByName | String | 销样人姓名 | 李四 |
| disposalAt | DateTime | 销样时间 | 2026-03-17 11:00:00 |
| disposalImages | Array | 销样照片 | ["http://..."] |
| disposalRemark | String | 销样备注 | 已按规定销毁 |

- **异常场景**：
  - [404]：留样记录不存在 → msg："留样记录不存在"
  - [422]：留样未销样 → msg："该留样尚未执行销样"

---

### 5.7 健康晨检模块

---

#### 智能人脸晨检首页
- **接口路径**：`/api/v1/health/dashboard`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| orgId | Long | 是 | 组织ID | 1001 | 数据隔离 |
| checkDate | Date | 否 | 晨检日期，默认今天 | 2026-03-17 | 日期筛选 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| totalChecked | Integer | 今日检查人数 | 25 |
| passCount | Integer | 通过人数 | 23 |
| failCount | Integer | 未通过人数 | 2 |
| certAbnormal | Integer | 健康证异常人数 | 3 |
| pendingCount | Integer | 待检查人数 | 5 |
| passRate | Decimal | 通过率（%） | 92.00 |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：组织ID不能为空"
  - [403]：无数据访问权限 → msg："无权限访问该组织数据"

---

#### 待检查员工列表
- **接口路径**：`/api/v1/health/pending-employees`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| orgId | Long | 是 | 组织ID | 1001 | 数据隔离 |
| checkDate | Date | 否 | 晨检日期，默认今天 | 2026-03-17 | 日期筛选 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| total | Integer | 总人数 | 5 |
| list | Array | 员工列表 | - |
| └ employeeId | Long | 员工ID | 10 |
| └ employeeName | String | 员工姓名 | 张三 |
| └ avatarUrl | String | 头像URL | http://... |
| └ position | String | 职位 | 厨师 |
| └ employeeNo | String | 工号 | EMP001 |
| └ certStatus | String | 健康证状态：valid/expired | valid |
| └ certExpiryDate | Date | 健康证到期日期 | 2026-06-15 |
| └ hasFaceData | Boolean | 是否已录入人脸 | true |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：组织ID不能为空"

---

#### 晨检记录列表
- **接口路径**：`/api/v1/health/check-records`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| orgId | Long | 是 | 组织ID | 1001 | 数据隔离 |
| pageNum | Integer | 否 | 页码，默认1 | 1 | 分页 |
| pageSize | Integer | 否 | 每页条数，默认20 | 20 | 分页 |
| checkDate | Date | 否 | 晨检日期 | 2026-03-17 | 日期筛选 |
| checkResult | String | 否 | 晨检结果：pass/fail | pass | 结果筛选 |
| employeeName | String | 否 | 员工姓名（模糊） | 张三 | 姓名筛选 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| total | Long | 总记录数 | 25 |
| list | Array | 晨检记录列表 | - |
| └ id | Long | 晨检ID | 1 |
| └ checkNo | String | 晨检编号 | HC-20260317001 |
| └ employeeId | Long | 员工ID | 10 |
| └ employeeName | String | 员工姓名 | 张三 |
| └ avatarUrl | String | 头像URL | http://... |
| └ position | String | 职位 | 厨师 |
| └ employeeNo | String | 工号 | EMP001 |
| └ checkTime | DateTime | 检查时间 | 2026-03-17 08:30:00 |
| └ temperature | Decimal | 体温（℃） | 36.5 |
| └ tempStatus | String | 体温状态：normal/abnormal | normal |
| └ certCheckResult | String | 健康证检查状态：pass/fail | pass |
| └ handHygieneResult | String | 手部卫生状态：pass/fail | pass |
| └ uniformCheckResult | String | 着装检查状态：pass/fail | pass |
| └ checkResult | String | 晨检结果：pass/fail | pass |
| └ failReason | String | 不通过原因 | - |
| └ remark | String | 备注 | - |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：组织ID不能为空"

---

#### AI人脸晨检与身份核验
- **接口路径**：`/api/v1/health/check-records/ai-check`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| faceImage | String | 是 | 人脸照片（Base64或URL） | data:image/... | 人脸识别 |
| temperature | Decimal | 是 | 体温（℃） | 36.5 | 体温检测 |
| deviceId | Long | 否 | 晨检设备ID | 1 | 设备标识 |
| orgId | Long | 是 | 组织ID | 1001 | 数据隔离 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| checkId | Long | 晨检记录ID | 1 |
| checkNo | String | 晨检编号 | HC-20260317001 |
| employeeId | Long | 员工ID | 10 |
| employeeName | String | 员工姓名 | 张三 |
| position | String | 职位 | 厨师 |
| employeeNo | String | 工号 | EMP001 |
| avatarUrl | String | 头像URL | http://... |
| faceMatchScore | Decimal | 人脸匹配度（0-100） | 98.50 |
| faceMatchResult | String | 人脸匹配结果：pass/fail | pass |
| checkTime | DateTime | 检测时间 | 2026-03-17 08:30:00 |
| temperature | Decimal | 体温（℃） | 36.5 |
| tempStatus | String | 体温状态：normal/abnormal | normal |
| tempCheckResult | String | 体温检查结果：pass/fail | pass |
| certNo | String | 健康证编号 | HZ202601001 |
| certExpiryDate | Date | 健康证到期日期 | 2026-06-15 |
| certStatus | String | 健康证状态：valid/expiring/expired | valid |
| certCheckResult | String | 健康证检查结果：pass/fail | pass |
| certCheckMessage | String | 健康证检查信息 | - |
| handHygiene | String | 手部卫生：pass/fail | pass |
| handHygieneMessage | String | 手部卫生异常说明 | - |
| uniformCheck | String | 着装检查：pass/fail | pass |
| uniformCheckMessage | String | 着装检查异常说明 | - |
| checkResult | String | 晨检结果：pass/fail | pass |
| failReasons | Array | 不通过原因列表 | - |
| hasWarning | Boolean | 是否有预警 | false |
| warningMessages | Array | 预警信息列表 | - |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：人脸照片不能为空"
  - [422]：人脸识别失败 → msg："人脸识别失败，请重新拍摄"
  - [422]：未找到匹配员工 → msg："未识别到员工信息，请先录入人脸"
  - [429]：请求频率过高 → msg："AI识别请求频率过高，请稍后再试"

---

#### 晨检详情
- **接口路径**：`/api/v1/health/check-records/{id}`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| id | Long | 是 | 晨检ID（路径参数） | 1 | 晨检标识 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 晨检ID | 1 |
| checkNo | String | 晨检编号 | HC-20260317001 |
| employeeId | Long | 员工ID | 10 |
| employeeName | String | 员工姓名 | 张三 |
| employeeNo | String | 工号 | EMP001 |
| position | String | 职位 | 厨师 |
| avatarUrl | String | 头像URL | http://... |
| checkDate | Date | 晨检日期 | 2026-03-17 |
| checkTime | DateTime | 晨检时间 | 2026-03-17 08:30:00 |
| faceImageUrl | String | 人脸照片URL | http://... |
| faceMatchScore | Decimal | 人脸匹配度 | 98.50 |
| temperature | Decimal | 体温（℃） | 36.5 |
| certStatus | String | 健康证状态（冗余） | valid |
| handHygiene | String | 手部卫生：pass/fail | pass |
| uniformCheck | String | 着装检查：pass/fail | pass |
| healthStatus | String | 健康状况：normal/abnormal | normal |
| checkResult | String | 晨检结果：pass/fail | pass |
| failReason | String | 不通过原因 | - |
| checkerId | Long | 晨检员ID | 5 |
| checkerName | String | 晨检员姓名 | 管理员 |
| remark | String | 备注 | - |

- **异常场景**：
  - [404]：晨检记录不存在 → msg："晨检记录不存在"

---

#### 人脸录入与更新
- **接口路径**：`/api/v1/health/face-features`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| employeeId | Long | 是 | 员工ID | 10 | 员工标识 |
| faceImage | String | 是 | 人脸照片（Base64或URL） | data:image/... | 人脸照片 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 人脸特征ID | 1 |
| employeeId | Long | 员工ID | 10 |
| employeeName | String | 员工姓名 | 张三 |
| faceImageUrl | String | 人脸照片URL | http://... |
| qualityScore | Decimal | 照片质量评分 | 95.00 |
| enrolledAt | DateTime | 录入时间 | 2026-03-17 10:00:00 |
| message | String | 提示信息 | 人脸录入成功 |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：员工ID不能为空"
  - [422]：人脸照片质量不合格 → msg："人脸照片质量不合格，请重新拍摄"
  - [422]：未检测到人脸 → msg："未检测到人脸，请确保正脸拍摄"
  - [422]：检测到多张人脸 → msg："检测到多张人脸，请确保只有一人"
  - [403]：无权限操作 → msg："无权限为该员工录入人脸"
  - [429]：请求频率过高 → msg："AI识别请求频率过高，请稍后再试"

---

#### 人脸信息查询
- **接口路径**：`/api/v1/health/face-features/{employeeId}`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| employeeId | Long | 是 | 员工ID（路径参数） | 10 | 员工标识 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 人脸特征ID | 1 |
| employeeId | Long | 员工ID | 10 |
| employeeName | String | 员工姓名 | 张三 |
| faceImageUrl | String | 人脸照片URL | http://... |
| qualityScore | Decimal | 照片质量评分 | 95.00 |
| isActive | Boolean | 是否启用 | true |
| enrolledAt | DateTime | 录入时间 | 2026-03-17 10:00:00 |
| lastUsedAt | DateTime | 最后使用时间 | 2026-03-17 08:30:00 |

- **异常场景**：
  - [404]：人脸信息不存在 → msg："该员工尚未录入人脸信息"

---

#### 健康证管理首页
- **接口路径**：`/api/v1/health/certificates/dashboard`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| orgId | Long | 是 | 组织ID | 1001 | 数据隔离 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| totalCount | Integer | 健康证总数 | 30 |
| validCount | Integer | 有效数量 | 25 |
| expiringCount | Integer | 即将过期（30天内）数量 | 3 |
| expiredCount | Integer | 已过期数量 | 2 |
| unregisteredCount | Integer | 未办理数量 | 1 |
| urgentWarnings | Array | 紧急预警信息 | - |
| └ employeeId | Long | 员工ID | 10 |
| └ employeeName | String | 员工姓名 | 张三 |
| └ expiryDate | Date | 到期日期 | 2026-03-20 |
| └ remainDays | Integer | 剩余天数 | 3 |
| └ warningType | String | 预警类型：expired/expiring | expiring |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：组织ID不能为空"
  - [403]：无数据访问权限 → msg："无权限访问该组织数据"

---

#### 健康证列表
- **接口路径**：`/api/v1/health/certificates`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| orgId | Long | 是 | 组织ID | 1001 | 数据隔离 |
| pageNum | Integer | 否 | 页码，默认1 | 1 | 分页 |
| pageSize | Integer | 否 | 每页条数，默认20 | 20 | 分页 |
| status | String | 否 | 状态：pending/valid/expiring/expired | valid | 状态筛选 |
| employeeName | String | 否 | 员工姓名（模糊） | 张三 | 姓名筛选 |
| uploadStatus | String | 否 | 电子版上传状态：uploaded/not_uploaded | uploaded | 上传状态筛选 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| total | Long | 总记录数 | 30 |
| list | Array | 健康证列表 | - |
| └ id | Long | 健康证ID | 1 |
| └ employeeId | Long | 员工ID | 10 |
| └ employeeName | String | 员工姓名 | 张三 |
| └ employeeNo | String | 员工编号 | EMP001 |
| └ orgName | String | 所属组织 | 后厨部 |
| └ certificateNo | String | 健康证编号 | HZ202601001 |
| └ issueDate | Date | 发证日期 | 2026-01-15 |
| └ expiryDate | Date | 有效期至 | 2026-07-15 |
| └ status | String | 状态：pending/valid/expiring/expired | valid |
| └ hasUploadedImage | Boolean | 是否已上传电子版 | true |
| └ remainDays | Integer | 剩余天数 | 90 |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：组织ID不能为空"

---

#### 新增/编辑健康证
- **接口路径**：`/api/v1/health/certificates`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| id | Long | 否 | 健康证ID（编辑时必传） | 1 | 健康证标识 |
| employeeId | Long | 是 | 员工ID | 10 | 选择员工 |
| certificateNo | String | 是 | 健康证编号 | HZ202601001 | 证件编号 |
| issueDate | Date | 是 | 发证日期 | 2026-01-15 | 发证日期 |
| expiryDate | Date | 是 | 有效期至 | 2026-07-15 | 有效期 |
| issueOrg | String | 否 | 发证机构 | XX市疾控中心 | 发证机构 |
| certificateImages | Array | 否 | 电子版照片URL列表 | ["http://..."] | 电子版上传 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 健康证ID | 1 |
| employeeId | Long | 员工ID | 10 |
| employeeName | String | 员工姓名 | 张三 |
| certificateNo | String | 健康证编号 | HZ202601001 |
| status | String | 状态 | valid |
| remainDays | Integer | 剩余天数 | 90 |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：员工ID不能为空"
  - [422]：员工已存在健康证 → msg："该员工已存在健康证信息，请编辑或删除后重新添加"
  - [422]：有效期无效 → msg："有效期必须大于发证日期"
  - [404]：员工不存在 → msg："员工不存在"

---

#### 健康证详情
- **接口路径**：`/api/v1/health/certificates/{id}`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| id | Long | 是 | 健康证ID（路径参数） | 1 | 健康证标识 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 健康证ID | 1 |
| employeeId | Long | 员工ID | 10 |
| employeeName | String | 员工姓名 | 张三 |
| employeeNo | String | 员工编码 | EMP001 |
| certificateNo | String | 证件编号 | HZ202601001 |
| status | String | 状态 | valid |
| issueDate | Date | 发证日期 | 2026-01-15 |
| expiryDate | Date | 有效期至 | 2026-07-15 |
| issueOrg | String | 发证机构 | XX市疾控中心 |
| certificateImages | Array | 电子版附件 | ["http://..."] |
| remainDays | Integer | 剩余天数 | 90 |
| remark | String | 备注 | - |

- **异常场景**：
  - [404]：健康证不存在 → msg："健康证不存在"

---

#### 健康证过期预警列表
- **接口路径**：`/api/v1/health/certificates/expiring`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| orgId | Long | 是 | 组织ID | 1001 | 数据隔离 |
| pageNum | Integer | 否 | 页码，默认1 | 1 | 分页 |
| pageSize | Integer | 否 | 每页条数，默认20 | 20 | 分页 |
| warningType | String | 否 | 预警类型：expiring/expired | expiring | 类型筛选 |
| days | Integer | 否 | 即将过期天数，默认30 | 30 | 天数筛选 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| total | Long | 总记录数 | 5 |
| list | Array | 预警列表 | - |
| └ id | Long | 健康证ID | 1 |
| └ employeeId | Long | 员工ID | 10 |
| └ employeeName | String | 员工姓名 | 张三 |
| └ employeeNo | String | 员工编号 | EMP001 |
| └ orgName | String | 所属组织 | 后厨部 |
| └ certificateNo | String | 健康证编号 | HZ202601001 |
| └ expiryDate | Date | 到期日期 | 2026-03-20 |
| └ status | String | 状态：expiring/expired | expiring |
| └ remainDays | Integer | 剩余天数 | 3 |
| └ warningLevel | String | 预警级别：warning/urgent | urgent |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：组织ID不能为空"

---

#### 删除健康证
- **接口路径**：`/api/v1/health/certificates/{id}`
- **请求方法**：DELETE
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| id | Long | 是 | 健康证ID（路径参数） | 1 | 健康证标识 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 健康证ID | 1 |
| message | String | 提示信息 | 删除成功 |

- **异常场景**：
  - [404]：健康证不存在 → msg："健康证不存在"
  - [403]：无删除权限 → msg："无权限删除健康证"

---

### 5.8 设备与告警模块

---

#### 设备管理首页
- **接口路径**：`/api/v1/device/dashboard`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| orgId | Long | 是 | 组织ID | 1001 | 数据隔离 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| totalDevices | Integer | 设备总数 | 25 |
| onlineCount | Integer | 在线数量 | 20 |
| offlineCount | Integer | 离线数量 | 3 |
| alertCount | Integer | 报警数量 | 2 |
| maintenanceCount | Integer | 维护中数量 | 1 |
| deviceTypeStats | Array | 设备类型统计 | - |
| └ deviceType | String | 设备类型 | camera |
| └ deviceTypeName | String | 设备类型名称 | 监控摄像头 |
| └ count | Integer | 数量 | 10 |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：组织ID不能为空"
  - [403]：无数据访问权限 → msg："无权限访问该组织数据"

---

#### 设备列表
- **接口路径**：`/api/v1/device/devices`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| orgId | Long | 是 | 组织ID | 1001 | 数据隔离 |
| pageNum | Integer | 否 | 页码，默认1 | 1 | 分页 |
| pageSize | Integer | 否 | 每页条数，默认20 | 20 | 分页 |
| deviceType | String | 否 | 设备类型：camera/sensor/scale/terminal/pos | camera | 类型筛选 |
| onlineStatus | String | 否 | 在线状态：online/offline/fault | online | 状态筛选 |
| status | String | 否 | 状态：active/inactive/maintenance | active | 状态筛选 |
| deviceName | String | 否 | 设备名称（模糊） | 摄像头 | 名称筛选 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| total | Long | 总记录数 | 25 |
| list | Array | 设备列表 | - |
| └ id | Long | 设备ID | 1 |
| └ deviceCode | String | 设备编码 | DEV-001 |
| └ deviceName | String | 设备名称 | 1号摄像头 |
| └ deviceType | String | 设备类型 | camera |
| └ deviceTypeName | String | 设备类型名称 | 监控摄像头 |
| └ locationDesc | String | 位置描述 | 后厨1号操作台 |
| └ managerName | String | 负责人姓名 | 张三 |
| └ managerPhone | String | 负责人电话 | 138****1234 |
| └ onlineStatus | String | 在线状态 | online |
| └ lastHeartbeatAt | DateTime | 最后心跳时间 | 2026-03-17 10:00:00 |
| └ status | String | 状态 | active |
| └ extraInfo | Object | 设备特有信息 | - |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：组织ID不能为空"

---

#### 设备详情
- **接口路径**：`/api/v1/device/devices/{id}`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| id | Long | 是 | 设备ID（路径参数） | 1 | 设备标识 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 设备ID | 1 |
| deviceCode | String | 设备编码 | DEV-001 |
| deviceName | String | 设备名称 | 1号摄像头 |
| deviceType | String | 设备类型 | camera |
| deviceTypeName | String | 设备类型名称 | 监控摄像头 |
| deviceModel | String | 设备型号 | DS-2CD2143 |
| manufacturer | String | 生产厂商 | 海康威视 |
| sn | String | 设备序列号 | SN123456 |
| macAddress | String | MAC地址 | AA:BB:CC:DD:EE:FF |
| ipAddress | String | IP地址 | 192.168.1.100 |
| locationDesc | String | 位置描述 | 后厨1号操作台 |
| positionX | Decimal | X坐标 | 10.500 |
| positionY | Decimal | Y坐标 | 5.200 |
| positionZ | Decimal | Z坐标 | 0.000 |
| installDate | Date | 安装日期 | 2025-01-15 |
| warrantyExpiresAt | Date | 保修到期日 | 2027-01-15 |
| maintenanceCycleDays | Integer | 维保周期（天） | 90 |
| lastMaintenanceAt | Date | 上次维保日期 | 2026-01-15 |
| nextMaintenanceAt | Date | 下次维保日期 | 2026-04-15 |
| onlineStatus | String | 在线状态：online/offline/fault | online |
| lastHeartbeatAt | DateTime | 最后心跳时间 | 2026-03-17 10:00:00 |
| status | String | 状态：active/inactive/maintenance | active |
| managerId | Long | 负责人ID | 10 |
| managerName | String | 负责人姓名 | 张三 |
| managerPhone | String | 负责人电话 | 13812345678 |
| orgId | Long | 所属组织ID | 1001 |
| orgName | String | 所属组织名称 | 后厨部 |
| configParams | Object | 设备配置参数（按类型不同） | - |
| remark | String | 备注 | - |

- **异常场景**：
  - [404]：设备不存在 → msg："设备不存在"

---

#### 新增设备
- **接口路径**：`/api/v1/device/devices`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| deviceCode | String | 是 | 设备编码 | DEV-001 | 设备编号 |
| deviceName | String | 是 | 设备名称 | 1号摄像头 | 设备名称 |
| deviceType | String | 是 | 设备类型 | camera | 设备类型 |
| deviceModel | String | 否 | 设备型号 | DS-2CD2143 | 型号 |
| manufacturer | String | 否 | 生产厂商 | 海康威视 | 厂商 |
| sn | String | 否 | 设备序列号 | SN123456 | 序列号 |
| macAddress | String | 否 | MAC地址 | AA:BB:CC:DD:EE:FF | MAC地址 |
| ipAddress | String | 否 | IP地址 | 192.168.1.100 | IP地址 |
| locationDesc | String | 否 | 位置描述 | 后厨1号操作台 | 安装区域 |
| positionX | Decimal | 否 | X坐标 | 10.500 | 3D坐标 |
| positionY | Decimal | 否 | Y坐标 | 5.200 | 3D坐标 |
| positionZ | Decimal | 否 | Z坐标 | 0.000 | 3D坐标 |
| installDate | Date | 否 | 安装日期 | 2025-01-15 | 安装时间 |
| warrantyExpiresAt | Date | 否 | 保修到期日 | 2027-01-15 | 保质期 |
| maintenanceCycleDays | Integer | 否 | 维保周期（天） | 90 | 维保周期 |
| managerId | Long | 否 | 负责人ID | 10 | 负责人 |
| orgId | Long | 是 | 所属组织ID | 1001 | 所属组织 |
| configParams | Object | 否 | 设备配置参数 | - | 特有信息 |
| remark | String | 否 | 备注 | - | 备注 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 设备ID | 1 |
| deviceCode | String | 设备编码 | DEV-001 |
| deviceName | String | 设备名称 | 1号摄像头 |
| status | String | 状态 | active |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：设备编码不能为空"
  - [422]：设备编码已存在 → msg："设备编码已存在，请修改"
  - [403]：无新增权限 → msg："无权限新增设备"

---

#### 修改设备
- **接口路径**：`/api/v1/device/devices/{id}`
- **请求方法**：PUT
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| id | Long | 是 | 设备ID（路径参数） | 1 | 设备标识 |
| deviceName | String | 是 | 设备名称 | 1号摄像头 | 设备名称 |
| deviceType | String | 是 | 设备类型 | camera | 设备类型 |
| ... | ... | ... | （其他参数同新增） | - | - |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 设备ID | 1 |
| deviceCode | String | 设备编码 | DEV-001 |
| deviceName | String | 设备名称 | 1号摄像头 |
| status | String | 状态 | active |

- **异常场景**：
  - [404]：设备不存在 → msg："设备不存在"
  - [400]：参数校验失败 → msg："参数校验失败：设备名称不能为空"
  - [403]：无修改权限 → msg："无权限修改设备"

---

#### 删除设备
- **接口路径**：`/api/v1/device/devices/{id}`
- **请求方法**：DELETE
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| id | Long | 是 | 设备ID（路径参数） | 1 | 设备标识 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 设备ID | 1 |
| message | String | 提示信息 | 删除成功 |

- **异常场景**：
  - [404]：设备不存在 → msg："设备不存在"
  - [422]：设备存在关联告警 → msg："设备存在未处理的告警，无法删除"
  - [403]：无删除权限 → msg："无权限删除设备"

---

#### 设备数据采集记录
- **接口路径**：`/api/v1/device/devices/{deviceId}/data-logs`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| deviceId | Long | 是 | 设备ID（路径参数） | 1 | 设备标识 |
| pageNum | Integer | 否 | 页码，默认1 | 1 | 分页 |
| pageSize | Integer | 否 | 每页条数，默认20 | 20 | 分页 |
| dataType | String | 否 | 数据类型 | temperature | 类型筛选 |
| startTime | DateTime | 否 | 开始时间 | 2026-03-17 00:00:00 | 时间筛选 |
| endTime | DateTime | 否 | 结束时间 | 2026-03-17 23:59:59 | 时间筛选 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| total | Long | 总记录数 | 100 |
| list | Array | 采集记录列表 | - |
| └ id | Long | 日志ID | 1 |
| └ deviceId | Long | 设备ID | 1 |
| └ deviceCode | String | 设备编码 | DEV-001 |
| └ dataType | String | 数据类型 | temperature |
| └ dataValue | Decimal | 数据值 | 36.5 |
| └ dataUnit | String | 数据单位 | ℃ |
| └ collectedAt | DateTime | 采集时间 | 2026-03-17 10:00:00 |

- **异常场景**：
  - [404]：设备不存在 → msg："设备不存在"

---

#### 告警管理首页
- **接口路径**：`/api/v1/device/alerts/dashboard`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| orgId | Long | 是 | 组织ID | 1001 | 数据隔离 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| totalAlerts | Integer | 告警总数 | 15 |
| pendingCount | Integer | 待处理数量 | 5 |
| handlingCount | Integer | 处理中数量 | 3 |
| handledCount | Integer | 已处置数量 | 5 |
| closedCount | Integer | 已关闭数量 | 2 |
| todayAlerts | Integer | 今日告警数 | 3 |
| urgentCount | Integer | 紧急告警数 | 1 |
| criticalCount | Integer | 严重告警数 | 2 |
| alertTypeStats | Array | 告警类型统计 | - |
| └ alertType | String | 告警类型 | device_offline |
| └ alertTypeName | String | 告警类型名称 | 设备离线 |
| └ count | Integer | 数量 | 5 |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：组织ID不能为空"
  - [403]：无数据访问权限 → msg："无权限访问该组织数据"

---

#### 告警列表
- **接口路径**：`/api/v1/device/alerts`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| orgId | Long | 是 | 组织ID | 1001 | 数据隔离 |
| pageNum | Integer | 否 | 页码，默认1 | 1 | 分页 |
| pageSize | Integer | 否 | 每页条数，默认20 | 20 | 分页 |
| alertType | String | 否 | 告警类型 | temp_abnormal | 类型筛选 |
| alertLevel | String | 否 | 告警级别：info/warning/error/critical | warning | 级别筛选 |
| status | String | 否 | 状态：pending/assigned/handling/handled/reviewed/closed | pending | 状态筛选 |
| deviceId | Long | 否 | 设备ID | 1 | 设备筛选 |
| startTime | DateTime | 否 | 开始时间 | 2026-03-17 00:00:00 | 时间筛选 |
| endTime | DateTime | 否 | 结束时间 | 2026-03-17 23:59:59 | 时间筛选 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| total | Long | 总记录数 | 15 |
| list | Array | 告警列表 | - |
| └ id | Long | 告警ID | 1 |
| └ alertNo | String | 告警编号 | AL-20260317001 |
| └ alertType | String | 告警类型 | temp_abnormal |
| └ alertTypeName | String | 告警类型名称 | 温度异常 |
| └ alertLevel | String | 告警级别 | warning |
| └ alertLevelName | String | 告警级别名称 | 警告 |
| └ deviceId | Long | 设备ID | 1 |
| └ deviceName | String | 设备名称 | 温度传感器1号 |
| └ deviceType | String | 设备类型 | sensor |
| └ alertContent | String | 告警内容 | 温度超过设定阈值 |
| └ status | String | 状态 | pending |
| └ statusName | String | 状态名称 | 待处理 |
| └ assignedTo | Long | 指派处理人ID | 10 |
| └ assignedToName | String | 指派处理人姓名 | 张三 |
| └ triggeredAt | DateTime | 触发时间 | 2026-03-17 10:00:00 |
| └ handledAt | DateTime | 处理时间 | - |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：组织ID不能为空"

---

#### 告警详情
- **接口路径**：`/api/v1/device/alerts/{id}`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| id | Long | 是 | 告警ID（路径参数） | 1 | 告警标识 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 告警ID | 1 |
| alertNo | String | 告警编号 | AL-20260317001 |
| alertType | String | 告警类型 | temp_abnormal |
| alertTypeName | String | 告警类型名称 | 温度异常 |
| alertLevel | String | 告警级别 | warning |
| alertLevelName | String | 告警级别名称 | 警告 |
| deviceId | Long | 设备ID | 1 |
| deviceName | String | 设备名称 | 温度传感器1号 |
| deviceType | String | 设备类型 | sensor |
| alertContent | String | 告警内容 | 温度超过设定阈值 |
| alertDetail | Object | 告警详情 | - |
| alertImages | Array | 告警截图 | ["http://..."] |
| alertVideoUrl | String | 告警视频URL | http://... |
| triggeredAt | DateTime | 触发时间 | 2026-03-17 10:00:00 |
| status | String | 状态 | handled |
| assignedTo | Long | 指派处理人ID | 10 |
| assignedToName | String | 指派处理人姓名 | 张三 |
| assignedAt | DateTime | 指派时间 | 2026-03-17 10:05:00 |
| handledBy | Long | 实际处理人ID | 10 |
| handledByName | String | 实际处理人姓名 | 张三 |
| handledAt | DateTime | 处理时间 | 2026-03-17 10:30:00 |
| handleResult | String | 处理结果 | 已调整温度 |
| handleImages | Array | 处理照片 | ["http://..."] |
| reviewedBy | Long | 复核人ID | 5 |
| reviewedByName | String | 复核人姓名 | 管理员 |
| reviewedAt | DateTime | 复核时间 | 2026-03-17 11:00:00 |
| reviewResult | String | 复核结果：approved/rejected | approved |
| reviewRemark | String | 复核备注 | 处理得当 |

- **异常场景**：
  - [404]：告警不存在 → msg："告警不存在"

---

#### 告警指派
- **接口路径**：`/api/v1/device/alerts/{id}/assign`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| id | Long | 是 | 告警ID（路径参数） | 1 | 告警标识 |
| assignedTo | Long | 是 | 指派处理人ID | 10 | 选择负责人 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 告警ID | 1 |
| alertNo | String | 告警编号 | AL-20260317001 |
| status | String | 状态 | assigned |
| assignedTo | Long | 指派处理人ID | 10 |
| assignedToName | String | 指派处理人姓名 | 张三 |
| assignedAt | DateTime | 指派时间 | 2026-03-17 10:05:00 |

- **异常场景**：
  - [404]：告警不存在 → msg："告警不存在"
  - [422]：告警状态不允许指派 → msg："只有待处理的告警可以指派"
  - [400]：处理人不能为空 → msg："请选择处理人"

---

#### 告警处置
- **接口路径**：`/api/v1/device/alerts/{id}/handle`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| id | Long | 是 | 告警ID（路径参数） | 1 | 告警标识 |
| handleResult | String | 是 | 处置说明 | 已调整温度至正常范围 | 处置描述 |
| handleImages | Array | 否 | 处置照片URL列表 | ["http://..."] | 处置照片 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 告警ID | 1 |
| alertNo | String | 告警编号 | AL-20260317001 |
| status | String | 状态 | handled |
| handledBy | Long | 处理人ID | 10 |
| handledByName | String | 处理人姓名 | 张三 |
| handledAt | DateTime | 处理时间 | 2026-03-17 10:30:00 |

- **异常场景**：
  - [404]：告警不存在 → msg："告警不存在"
  - [422]：告警状态不允许处置 → msg："只有已指派或处理中的告警可以处置"
  - [400]：处置说明不能为空 → msg："请填写处置说明"

---

#### 告警复核
- **接口路径**：`/api/v1/device/alerts/{id}/review`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| id | Long | 是 | 告警ID（路径参数） | 1 | 告警标识 |
| reviewResult | String | 是 | 复核结果：approved/rejected | approved | 复核操作 |
| reviewRemark | String | 否 | 复核备注 | 处理得当 | 复核备注 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 告警ID | 1 |
| alertNo | String | 告警编号 | AL-20260317001 |
| status | String | 状态 | reviewed |
| reviewedBy | Long | 复核人ID | 5 |
| reviewedByName | String | 复核人姓名 | 管理员 |
| reviewedAt | DateTime | 复核时间 | 2026-03-17 11:00:00 |

- **异常场景**：
  - [404]：告警不存在 → msg："告警不存在"
  - [422]：告警状态不允许复核 → msg："只有已处置的告警可以复核"
  - [403]：无复核权限 → msg："无权限复核告警"

---

#### 告警策略配置列表
- **接口路径**：`/api/v1/device/alert-rules`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| orgId | Long | 是 | 组织ID | 1001 | 数据隔离 |
| pageNum | Integer | 否 | 页码，默认1 | 1 | 分页 |
| pageSize | Integer | 否 | 每页条数，默认20 | 20 | 分页 |
| ruleType | String | 否 | 规则类型：threshold/offline/ai_event | threshold | 类型筛选 |
| status | String | 否 | 状态：active/inactive | active | 状态筛选 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| total | Long | 总记录数 | 10 |
| list | Array | 规则列表 | - |
| └ id | Long | 规则ID | 1 |
| └ ruleName | String | 规则名称 | 温度超限告警 |
| └ ruleType | String | 规则类型 | threshold |
| └ ruleTypeName | String | 规则类型名称 | 阈值告警 |
| └ deviceType | String | 适用设备类型 | sensor |
| └ alertLevel | String | 告警级别 | warning |
| └ notifyChannels | String | 通知渠道 | sms,email |
| └ status | String | 状态 | active |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：组织ID不能为空"

---

#### 新增/修改告警策略
- **接口路径**：`/api/v1/device/alert-rules`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| id | Long | 否 | 规则ID（编辑时必传） | 1 | 规则标识 |
| ruleName | String | 是 | 规则名称 | 温度超限告警 | 规则名称 |
| ruleType | String | 是 | 规则类型：threshold/offline/ai_event | threshold | 规则类型 |
| deviceType | String | 否 | 适用设备类型 | sensor | 设备类型 |
| conditionJson | Object | 是 | 触发条件 | {"min": 0, "max": 100} | 触发条件 |
| alertLevel | String | 是 | 告警级别 | warning | 告警级别 |
| notifyChannels | String | 否 | 通知渠道（逗号分隔） | sms,email | 通知渠道 |
| status | String | 否 | 状态：active/inactive，默认active | active | 状态 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 规则ID | 1 |
| ruleName | String | 规则名称 | 温度超限告警 |
| status | String | 状态 | active |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：规则名称不能为空"
  - [403]：无配置权限 → msg："无权限配置告警策略"

---

#### 删除告警策略
- **接口路径**：`/api/v1/device/alert-rules/{id}`
- **请求方法**：DELETE
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| id | Long | 是 | 规则ID（路径参数） | 1 | 规则标识 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 规则ID | 1 |
| message | String | 提示信息 | 删除成功 |

- **异常场景**：
  - [404]：规则不存在 → msg："告警策略不存在"
  - [403]：无删除权限 → msg："无权限删除告警策略"

---

#### 实时监控列表
- **接口路径**：`/api/v1/device/monitors/realtime`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| orgId | Long | 是 | 组织ID | 1001 | 数据隔离 |
| onlineStatus | String | 否 | 在线状态：online/offline | online | 状态筛选 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| total | Integer | 总数量 | 10 |
| list | Array | 监控列表 | - |
| └ deviceId | Long | 设备ID | 1 |
| └ deviceName | String | 摄像头名称 | 1号摄像头 |
| └ locationDesc | String | 摄像头位置 | 后厨1号操作台 |
| └ streamUrl | String | 实时流地址 | rtsp://... |
| └ onlineStatus | String | 摄像头状态 | online |
| └ resolution | String | 分辨率 | 1920x1080 |
| └ frameRate | Integer | 帧率 | 25 |
| └ alertCount | Integer | 警告条数 | 2 |
| └ lastUpdatedAt | DateTime | 最后更新时间 | 2026-03-17 10:00:00 |
| └ alertMessages | Array | 预警信息列表 | - |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：组织ID不能为空"

---

#### 视频回放列表
- **接口路径**：`/api/v1/device/monitors/{deviceId}/recordings`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| deviceId | Long | 是 | 设备ID（路径参数） | 1 | 设备标识 |
| pageNum | Integer | 否 | 页码，默认1 | 1 | 分页 |
| pageSize | Integer | 否 | 每页条数，默认20 | 20 | 分页 |
| recordDate | Date | 否 | 录像日期 | 2026-03-17 | 日期筛选 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| total | Long | 总记录数 | 24 |
| list | Array | 录像列表 | - |
| └ id | Long | 录像ID | 1 |
| └ deviceId | Long | 设备ID | 1 |
| └ deviceName | String | 摄像头名称 | 1号摄像头 |
| └ locationDesc | String | 摄像头位置 | 后厨1号操作台 |
| └ startTime | DateTime | 录像开始时间 | 2026-03-17 08:00:00 |
| └ endTime | DateTime | 录像结束时间 | 2026-03-17 09:00:00 |
| └ duration | Integer | 录像时长（秒） | 3600 |
| └ fileSize | Long | 视频大小（字节） | 104857600 |
| └ resolution | String | 分辨率 | 1920x1080 |
| └ playUrl | String | 回放地址 | http://... |

- **异常场景**：
  - [404]：设备不存在 → msg："设备不存在"

---

### 5.9 角色权限模块

---

#### 角色分组列表
- **接口路径**：`/api/v1/sys/role-groups`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| tenantId | Long | 否 | 租户ID，默认当前租户 | 1 | 租户隔离 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| total | Integer | 总数量 | 5 |
| list | Array | 角色分组列表 | - |
| └ id | Long | 分组ID | 1 |
| └ groupName | String | 角色分组名称 | 系统管理 |
| └ groupDesc | String | 角色分组描述 | 系统管理相关角色 |
| └ roleCount | Integer | 角色数量 | 3 |
| └ roles | Array | 分组下的角色列表 | - |
| └─ id | Long | 角色ID | 1 |
| └─ roleName | String | 角色名称 | 超级管理员 |
| └─ roleCode | String | 角色编码 | SUPER_ADMIN |
| └─ roleDesc | String | 角色描述 | 系统最高权限角色 |
| └─ memberCount | Integer | 成员数量 | 2 |
| └─ createdAt | DateTime | 创建时间 | 2026-01-01 00:00:00 |

- **异常场景**：
  - [403]：无访问权限 → msg："无权限访问角色分组"

---

#### 新增角色分组
- **接口路径**：`/api/v1/sys/role-groups`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| groupName | String | 是 | 角色分组名称 | 系统管理 | 分组名称 |
| groupDesc | String | 否 | 角色分组描述 | 系统管理相关角色 | 分组描述 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 分组ID | 1 |
| groupName | String | 角色分组名称 | 系统管理 |
| groupDesc | String | 角色分组描述 | 系统管理相关角色 |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：分组名称不能为空"
  - [422]：分组名称已存在 → msg："分组名称已存在"
  - [403]：无新增权限 → msg："无权限新增角色分组"

---

#### 修改角色分组
- **接口路径**：`/api/v1/sys/role-groups/{id}`
- **请求方法**：PUT
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| id | Long | 是 | 分组ID（路径参数） | 1 | 分组标识 |
| groupName | String | 是 | 角色分组名称 | 系统管理 | 分组名称 |
| groupDesc | String | 否 | 角色分组描述 | 系统管理相关角色 | 分组描述 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 分组ID | 1 |
| groupName | String | 角色分组名称 | 系统管理 |
| groupDesc | String | 角色分组描述 | 系统管理相关角色 |

- **异常场景**：
  - [404]：分组不存在 → msg："角色分组不存在"
  - [400]：参数校验失败 → msg："参数校验失败：分组名称不能为空"
  - [422]：分组名称已存在 → msg："分组名称已存在"
  - [403]：无修改权限 → msg："无权限修改角色分组"

---

#### 删除角色分组
- **接口路径**：`/api/v1/sys/role-groups/{id}`
- **请求方法**：DELETE
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| id | Long | 是 | 分组ID（路径参数） | 1 | 分组标识 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 分组ID | 1 |
| message | String | 提示信息 | 删除成功 |

- **异常场景**：
  - [404]：分组不存在 → msg："角色分组不存在"
  - [422]：分组下存在角色 → msg："该分组下存在角色，请先移除或迁移角色"
  - [422]：最后一个分组不能删除 → msg："系统至少保留一个角色分组"
  - [403]：无删除权限 → msg："无权限删除角色分组"

---

#### 角色列表
- **接口路径**：`/api/v1/sys/roles`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| tenantId | Long | 否 | 租户ID | 1 | 租户隔离 |
| pageNum | Integer | 否 | 页码，默认1 | 1 | 分页 |
| pageSize | Integer | 否 | 每页条数，默认20 | 20 | 分页 |
| groupId | Long | 否 | 角色分组ID | 1 | 分组筛选 |
| roleName | String | 否 | 角色名称（模糊） | 管理员 | 名称筛选 |
| status | String | 否 | 状态：active/inactive | active | 状态筛选 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| total | Long | 总记录数 | 10 |
| list | Array | 角色列表 | - |
| └ id | Long | 角色ID | 1 |
| └ roleName | String | 角色名称 | 超级管理员 |
| └ roleCode | String | 角色编码 | SUPER_ADMIN |
| └ roleDesc | String | 角色描述 | 系统最高权限角色 |
| └ groupId | Long | 所属分组ID | 1 |
| └ groupName | String | 所属分组名称 | 系统管理 |
| └ memberCount | Integer | 成员数量 | 2 |
| └ status | String | 状态 | active |
| └ isSystem | Boolean | 是否系统角色 | true |
| └ createdAt | DateTime | 创建时间 | 2026-01-01 00:00:00 |

- **异常场景**：
  - [403]：无访问权限 → msg："无权限访问角色列表"

---

#### 新增角色
- **接口路径**：`/api/v1/sys/roles`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| roleName | String | 是 | 角色名称 | 采购专员 | 角色名称 |
| roleCode | String | 是 | 角色编码 | PURCHASE_STAFF | 角色编码 |
| groupId | Long | 是 | 所属分组ID | 2 | 角色分组 |
| roleDesc | String | 否 | 角色描述 | 负责采购相关工作 | 角色描述 |
| funcPermissions | Array | 否 | 功能权限ID列表 | [1, 2, 3] | 功能权限 |
| dataPermissions | Array | 否 | 数据权限（组织ID列表） | [1001, 1002] | 数据权限 |
| status | String | 否 | 状态，默认active | active | 状态 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 角色ID | 1 |
| roleName | String | 角色名称 | 采购专员 |
| roleCode | String | 角色编码 | PURCHASE_STAFF |
| groupId | Long | 所属分组ID | 2 |
| groupName | String | 所属分组名称 | 供应链管理 |
| status | String | 状态 | active |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：角色名称不能为空"
  - [422]：角色编码已存在 → msg："角色编码已存在"
  - [422]：分组不存在 → msg："所选角色分组不存在"
  - [403]：无新增权限 → msg："无权限新增角色"

---

#### 修改角色
- **接口路径**：`/api/v1/sys/roles/{id}`
- **请求方法**：PUT
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| id | Long | 是 | 角色ID（路径参数） | 1 | 角色标识 |
| roleName | String | 是 | 角色名称 | 采购专员 | 角色名称 |
| roleCode | String | 是 | 角色编码 | PURCHASE_STAFF | 角色编码 |
| groupId | Long | 是 | 所属分组ID | 2 | 角色分组 |
| roleDesc | String | 否 | 角色描述 | 负责采购相关工作 | 角色描述 |
| funcPermissions | Array | 否 | 功能权限ID列表 | [1, 2, 3] | 功能权限 |
| dataPermissions | Array | 否 | 数据权限（组织ID列表） | [1001, 1002] | 数据权限 |
| status | String | 否 | 状态 | active | 状态 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 角色ID | 1 |
| roleName | String | 角色名称 | 采购专员 |
| roleCode | String | 角色编码 | PURCHASE_STAFF |
| groupId | Long | 所属分组ID | 2 |
| groupName | String | 所属分组名称 | 供应链管理 |
| status | String | 状态 | active |

- **异常场景**：
  - [404]：角色不存在 → msg："角色不存在"
  - [400]：参数校验失败 → msg："参数校验失败：角色名称不能为空"
  - [422]：系统角色不能修改 → msg："系统内置角色不能修改"
  - [422]：角色编码已存在 → msg："角色编码已存在"
  - [403]：无修改权限 → msg："无权限修改角色"

---

#### 角色详情
- **接口路径**：`/api/v1/sys/roles/{id}`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| id | Long | 是 | 角色ID（路径参数） | 1 | 角色标识 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 角色ID | 1 |
| roleName | String | 角色名称 | 采购专员 |
| roleCode | String | 角色编码 | PURCHASE_STAFF |
| groupId | Long | 所属分组ID | 2 |
| groupName | String | 所属分组名称 | 供应链管理 |
| roleDesc | String | 角色描述 | 负责采购相关工作 |
| funcPermissions | Array | 功能权限列表 | - |
| └ permissionId | Long | 权限ID | 1 |
| └ permissionName | String | 权限名称 | 采购计划新增 |
| └ permissionCode | String | 权限编码 | scm:plan:create |
| └ moduleId | Long | 模块ID | 1 |
| └ moduleName | String | 模块名称 | 采购管理 |
| dataPermissions | Array | 数据权限列表 | - |
| └ orgId | Long | 组织ID | 1001 |
| └ orgName | String | 组织名称 | 采购部 |
| └ orgPath | String | 组织路径 | /总部/采购部 |
| memberCount | Integer | 成员数量 | 5 |
| status | String | 状态 | active |
| isSystem | Boolean | 是否系统角色 | false |
| createdAt | DateTime | 创建时间 | 2026-01-01 00:00:00 |
| updatedAt | DateTime | 更新时间 | 2026-03-17 10:00:00 |

- **异常场景**：
  - [404]：角色不存在 → msg："角色不存在"

---

#### 删除角色
- **接口路径**：`/api/v1/sys/roles/{id}`
- **请求方法**：DELETE
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| id | Long | 是 | 角色ID（路径参数） | 1 | 角色标识 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 角色ID | 1 |
| message | String | 提示信息 | 删除成功 |

- **异常场景**：
  - [404]：角色不存在 → msg："角色不存在"
  - [422]：角色下存在成员 → msg："该角色下存在关联成员，请先移除成员"
  - [422]：系统角色不能删除 → msg："系统内置角色不能删除"
  - [403]：无删除权限 → msg："无权限删除角色"

---

#### 角色成员列表
- **接口路径**：`/api/v1/sys/roles/{roleId}/members`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| roleId | Long | 是 | 角色ID（路径参数） | 1 | 角色标识 |
| pageNum | Integer | 否 | 页码，默认1 | 1 | 分页 |
| pageSize | Integer | 否 | 每页条数，默认20 | 20 | 分页 |
| employeeName | String | 否 | 员工姓名（模糊） | 张三 | 姓名筛选 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| total | Long | 总记录数 | 5 |
| list | Array | 成员列表 | - |
| └ employeeId | Long | 员工ID | 10 |
| └ employeeNo | String | 员工编码 | EMP001 |
| └ employeeName | String | 员工姓名 | 张三 |
| └ avatarUrl | String | 头像URL | http://... |
| └ orgId | Long | 所属组织ID | 1001 |
| └ orgName | String | 所属组织名称 | 采购部 |
| └ position | String | 职位 | 采购员 |
| └ phone | String | 手机号 | 138****1234 |
| └ status | String | 员工状态 | active |
| └ joinedAt | DateTime | 加入角色时间 | 2026-01-15 10:00:00 |

- **异常场景**：
  - [404]：角色不存在 → msg："角色不存在"

---

#### 添加角色成员
- **接口路径**：`/api/v1/sys/roles/{roleId}/members`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| roleId | Long | 是 | 角色ID（路径参数） | 1 | 角色标识 |
| employeeIds | Array | 是 | 员工ID列表 | [10, 11, 12] | 员工列表 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| roleId | Long | 角色ID | 1 |
| roleName | String | 角色名称 | 采购专员 |
| addedCount | Integer | 成功添加数量 | 3 |
| memberCount | Integer | 当前成员总数 | 8 |

- **异常场景**：
  - [404]：角色不存在 → msg："角色不存在"
  - [400]：员工ID不能为空 → msg："请选择要添加的员工"
  - [422]：部分员工已存在 → msg："部分员工已拥有该角色，已自动跳过"

---

#### 移除角色成员
- **接口路径**：`/api/v1/sys/roles/{roleId}/members/{employeeId}`
- **请求方法**：DELETE
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| roleId | Long | 是 | 角色ID（路径参数） | 1 | 角色标识 |
| employeeId | Long | 是 | 员工ID（路径参数） | 10 | 员工标识 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| roleId | Long | 角色ID | 1 |
| employeeId | Long | 员工ID | 10 |
| memberCount | Integer | 当前成员总数 | 7 |

- **异常场景**：
  - [404]：角色不存在 → msg："角色不存在"
  - [404]：员工不在该角色中 → msg："该员工不在此角色中"
  - [403]：无移除权限 → msg："无权限移除角色成员"

---

#### 功能权限树
- **接口路径**：`/api/v1/sys/permissions/tree`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：无

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| total | Integer | 模块总数 | 10 |
| list | Array | 权限模块树 | - |
| └ moduleId | Long | 模块ID | 1 |
| └ moduleCode | String | 模块编码 | scm |
| └ moduleName | String | 模块名称 | 采购管理 |
| └ permissions | Array | 权限列表 | - |
| └─ permissionId | Long | 权限ID | 1 |
| └─ permissionCode | String | 权限编码 | scm:plan:view |
| └─ permissionName | String | 权限名称 | 采购计划查看 |
| └─ permissionType | String | 权限类型 | menu/button/api |

- **异常场景**：
  - [403]：无访问权限 → msg："无权限访问权限配置"

---

#### 数据权限组织树
- **接口路径**：`/api/v1/sys/permissions/org-tree`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| tenantId | Long | 否 | 租户ID | 1 | 租户隔离 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 组织ID | 1 |
| orgCode | String | 组织编码 | ORG001 |
| orgName | String | 组织名称 | 总部 |
| parentId | Long | 父级组织ID | - |
| children | Array | 子组织列表 | - |

- **异常场景**：
  - [403]：无访问权限 → msg："无权限访问组织数据"

---

### 5.10 数据监管与评价模块

---

#### 数据监管看板首页
- **接口路径**：`/api/v1/sys/supervision/dashboard`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| orgId | Long | 是 | 组织ID | 1001 | 数据隔离 |
| dateRange | String | 否 | 日期范围：today/week/month/quarter/year | month | 时间范围 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| violationRate | Decimal | 违规率（%） | 2.5 |
| violationRateYoy | Decimal | 违规率同比（%） | -0.5 |
| violationRateMom | Decimal | 违规率环比（%） | -0.3 |
| violationRateTarget | Decimal | 违规率目标值（%） | 3.0 |
| traceResponseTime | Decimal | 溯源响应时长（小时） | 2.5 |
| traceResponseYoy | Decimal | 溯源响应同比（%） | -10.0 |
| traceResponseMom | Decimal | 溯源响应环比（%） | -5.0 |
| traceResponseTarget | Decimal | 溯源响应目标值（小时） | 4.0 |
| wasteRate | Decimal | 食材浪费率（%） | 5.2 |
| wasteRateYoy | Decimal | 浪费率同比（%） | -1.0 |
| wasteRateMom | Decimal | 浪费率环比（%） | -0.5 |
| wasteRateTarget | Decimal | 浪费率目标值（%） | 8.0 |
| satisfactionRate | Decimal | 就餐满意度（%） | 92.5 |
| satisfactionYoy | Decimal | 满意度同比（%） | 2.0 |
| satisfactionMom | Decimal | 满意度环比（%） | 1.5 |
| satisfactionTarget | Decimal | 满意度目标值（%） | 90.0 |
| recentViolations | Array | 最近违规记录（前5条） | - |
| └ id | Long | 违规ID | 1 |
| └ title | String | 违规标题 | 温度传感器离线 |
| └ description | String | 违规说明 | 后厨1号温度传感器离线超过2小时 |
| └ location | String | 地点 | 后厨 |
| └ triggeredAt | DateTime | 时间 | 2026-03-17 08:30:00 |
| └ status | String | 状态 | pending/handled |
| recentTraces | Array | 最近溯源记录（前5条） | - |
| └ id | Long | 溯源ID | 1 |
| └ materialName | String | 物料名称 | 鲜鸡蛋 |
| └ batchNo | String | 批次号 | LOT-20260317001 |
| └ responseTime | Decimal | 响应时长（小时） | 1.5 |
| └ status | String | 状态 | completed/timeout |
| trendData | Array | 趋势分析（按周） | - |
| └ weekLabel | String | 周标签 | W1 |
| └ violationRate | Decimal | 违规率 | 2.5 |
| └ wasteRate | Decimal | 浪费率 | 5.2 |
| └ satisfactionRate | Decimal | 满意度 | 92.0 |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：组织ID不能为空"
  - [403]：无数据访问权限 → msg："无权限访问该组织数据"

---

#### 违规记录列表
- **接口路径**：`/api/v1/sys/supervision/violations`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| orgId | Long | 是 | 组织ID | 1001 | 数据隔离 |
| pageNum | Integer | 否 | 页码，默认1 | 1 | 分页 |
| pageSize | Integer | 否 | 每页条数，默认20 | 20 | 分页 |
| status | String | 否 | 状态：pending/handled | pending | 状态筛选 |
| violationType | String | 否 | 违规类型 | device_offline | 类型筛选 |
| startTime | DateTime | 否 | 开始时间 | 2026-03-01 00:00:00 | 时间筛选 |
| endTime | DateTime | 否 | 结束时间 | 2026-03-17 23:59:59 | 时间筛选 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| total | Long | 总记录数 | 15 |
| list | Array | 违规记录列表 | - |
| └ id | Long | 违规ID | 1 |
| └ violationNo | String | 违规编号 | VIO-20260317001 |
| └ title | String | 违规标题 | 温度传感器离线 |
| └ description | String | 违规说明 | 后厨1号温度传感器离线超过2小时 |
| └ violationType | String | 违规类型 | device_offline |
| └ violationTypeName | String | 违规类型名称 | 设备离线 |
| └ location | String | 地点 | 后厨 |
| └ triggeredAt | DateTime | 触发时间 | 2026-03-17 08:30:00 |
| └ status | String | 状态 | pending |
| └ handledAt | DateTime | 处理时间 | - |
| └ handlerName | String | 处理人 | - |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：组织ID不能为空"

---

#### 违规记录详情
- **接口路径**：`/api/v1/sys/supervision/violations/{id}`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| id | Long | 是 | 违规ID（路径参数） | 1 | 违规标识 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 违规ID | 1 |
| violationNo | String | 违规编号 | VIO-20260317001 |
| title | String | 违规标题 | 温度传感器离线 |
| description | String | 违规说明 | 后厨1号温度传感器离线超过2小时 |
| violationType | String | 违规类型 | device_offline |
| violationTypeName | String | 违规类型名称 | 设备离线 |
| location | String | 地点 | 后厨 |
| triggeredAt | DateTime | 触发时间 | 2026-03-17 08:30:00 |
| status | String | 状态 | handled |
| violationImages | Array | 违规截图 | ["http://..."] |
| relatedData | Object | 关联数据 | - |
| handledBy | Long | 处理人ID | 10 |
| handledByName | String | 处理人姓名 | 张三 |
| handledAt | DateTime | 处理时间 | 2026-03-17 10:30:00 |
| handleResult | String | 处理结果 | 已更换设备 |
| handleImages | Array | 处理照片 | ["http://..."] |

- **异常场景**：
  - [404]：违规记录不存在 → msg："违规记录不存在"

---

#### 溯源响应记录列表
- **接口路径**：`/api/v1/sys/supervision/traces`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| orgId | Long | 是 | 组织ID | 1001 | 数据隔离 |
| pageNum | Integer | 否 | 页码，默认1 | 1 | 分页 |
| pageSize | Integer | 否 | 每页条数，默认20 | 20 | 分页 |
| status | String | 否 | 状态：completed/timeout | completed | 状态筛选 |
| materialName | String | 否 | 物料名称（模糊） | 鸡蛋 | 名称筛选 |
| startTime | DateTime | 否 | 开始时间 | 2026-03-01 00:00:00 | 时间筛选 |
| endTime | DateTime | 否 | 结束时间 | 2026-03-17 23:59:59 | 时间筛选 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| total | Long | 总记录数 | 50 |
| list | Array | 溯源记录列表 | - |
| └ id | Long | 溯源ID | 1 |
| └ traceNo | String | 溯源编号 | TRC-20260317001 |
| └ materialName | String | 物料名称 | 鲜鸡蛋 |
| └ batchNo | String | 批次号 | LOT-20260317001 |
| └ supplierName | String | 供应商名称 | XX养殖场 |
| └ requestTime | DateTime | 溯源请求时间 | 2026-03-17 08:00:00 |
| └ responseTime | Decimal | 响应时长（小时） | 1.5 |
| └ responseTimeRange | String | 响应时间范围 | 0.5-2.5小时 |
| └ completedAt | DateTime | 完成时间 | 2026-03-17 09:30:00 |
| └ status | String | 状态 | completed |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：组织ID不能为空"

---

#### 用餐评价统计
- **接口路径**：`/api/v1/sys/reviews/statistics`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| orgId | Long | 是 | 组织ID | 1001 | 数据隔离 |
| dateRange | String | 否 | 日期范围 | month | 时间范围 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| totalReviews | Integer | 总评价数 | 500 |
| avgScore | Decimal | 平均评分 | 4.5 |
| fiveStarCount | Integer | 五星好评数 | 300 |
| fourStarCount | Integer | 四星好评数 | 120 |
| threeStarCount | Integer | 三星评价数 | 50 |
| twoStarCount | Integer | 二星评价数 | 20 |
| oneStarCount | Integer | 一星评价数 | 10 |
| satisfactionRate | Decimal | 满意度（4星及以上） | 84.0 |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：组织ID不能为空"

---

#### 用餐评价列表
- **接口路径**：`/api/v1/sys/reviews`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| orgId | Long | 是 | 组织ID | 1001 | 数据隔离 |
| pageNum | Integer | 否 | 页码，默认1 | 1 | 分页 |
| pageSize | Integer | 否 | 每页条数，默认20 | 20 | 分页 |
| scoreLevel | Integer | 否 | 评分等级：1-5 | 5 | 评分筛选 |
| reviewerName | String | 否 | 评价人姓名（模糊） | 张 | 姓名筛选 |
| startTime | DateTime | 否 | 开始时间 | 2026-03-01 00:00:00 | 时间筛选 |
| endTime | DateTime | 否 | 结束时间 | 2026-03-17 23:59:59 | 时间筛选 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| total | Long | 总记录数 | 500 |
| list | Array | 评价列表 | - |
| └ id | Long | 评价ID | 1 |
| └ reviewNo | String | 评价编号 | REV-20260317001 |
| └ score | Integer | 评价等级（1-5星） | 5 |
| └ reviewerId | Long | 被评价人ID | 10 |
| └ reviewerName | String | 被评价人姓名 | 张三 |
| └ reviewerOrgName | String | 被评价人所属组织 | 后厨部 |
| └ reviewContent | String | 评价说明 | 菜品味道很好 |
| └ points | Integer | 积分数 | 10 |
| └ tags | Array | 标签 | ["味道好", "分量足"] |
| └ images | Array | 评价图片 | ["http://..."] |
| └ createdAt | DateTime | 评价时间 | 2026-03-17 12:30:00 |
| └ reviewerUserId | Long | 评价用户ID | 100 |
| └ reviewerUserName | String | 评价用户姓名 | 李四 |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：组织ID不能为空"

---

#### 申诉与反馈统计
- **接口路径**：`/api/v1/sys/complaints/statistics`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| orgId | Long | 是 | 组织ID | 1001 | 数据隔离 |
| dateRange | String | 否 | 日期范围 | month | 时间范围 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| totalComplaints | Integer | 总申诉数 | 30 |
| pendingCount | Integer | 待处理数 | 5 |
| handlingCount | Integer | 处理中数 | 3 |
| resolvedCount | Integer | 已解决数 | 22 |
| satisfactionRate | Decimal | 满意度（%） | 85.0 |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：组织ID不能为空"

---

#### 申诉与反馈列表
- **接口路径**：`/api/v1/sys/complaints`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| orgId | Long | 是 | 组织ID | 1001 | 数据隔离 |
| pageNum | Integer | 否 | 页码，默认1 | 1 | 分页 |
| pageSize | Integer | 否 | 每页条数，默认20 | 20 | 分页 |
| complaintType | String | 否 | 申诉类型：complaint/suggestion | complaint | 类型筛选 |
| status | String | 否 | 状态：pending/handling/resolved | pending | 状态筛选 |
| submitterName | String | 否 | 申诉人姓名（模糊） | 张 | 姓名筛选 |
| startTime | DateTime | 否 | 开始时间 | 2026-03-01 00:00:00 | 时间筛选 |
| endTime | DateTime | 否 | 结束时间 | 2026-03-17 23:59:59 | 时间筛选 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| total | Long | 总记录数 | 30 |
| list | Array | 申诉列表 | - |
| └ id | Long | 申诉ID | 1 |
| └ complaintNo | String | 申诉编号 | CP-20260317001 |
| └ title | String | 申诉标题 | 菜品份量不足 |
| └ description | String | 申诉说明 | 今日午餐份量偏少 |
| └ complaintType | String | 申诉类型 | complaint |
| └ complaintTypeName | String | 申诉类型名称 | 投诉 |
| └ submitterId | Long | 申诉人ID | 100 |
| └ submitterName | String | 申诉人姓名 | 张三 |
| └ submitterPhone | String | 申诉人电话 | 138****1234 |
| └ submittedAt | DateTime | 申诉时间 | 2026-03-17 13:00:00 |
| └ relatedInfo | Object | 关联信息 | - |
| └ status | String | 状态 | pending |
| └ satisfaction | String | 满意度：unrated/satisfied/neutral/dissatisfied | unrated |
| └ handlerName | String | 处理人 | - |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：组织ID不能为空"

---

#### 申诉与反馈详情
- **接口路径**：`/api/v1/sys/complaints/{id}`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| id | Long | 是 | 申诉ID（路径参数） | 1 | 申诉标识 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 申诉ID | 1 |
| complaintNo | String | 申诉编号 | CP-20260317001 |
| title | String | 申诉标题 | 菜品份量不足 |
| description | String | 申诉说明 | 今日午餐份量偏少 |
| complaintType | String | 申诉类型 | complaint |
| complaintTypeName | String | 申诉类型名称 | 投诉 |
| status | String | 状态 | resolved |
| submitterId | Long | 申诉人ID | 100 |
| submitterName | String | 申诉人姓名 | 张三 |
| submitterPhone | String | 申诉人电话 | 13812345678 |
| submittedAt | DateTime | 申诉时间 | 2026-03-17 13:00:00 |
| relatedInfo | Object | 关联信息 | - |
| └ videoUrl | String | 关联视频URL | http://... |
| └ traceId | Long | 关联溯源ID | 1 |
| └ traceNo | String | 关联溯源编号 | TRC-20260317001 |
| images | Array | 申诉图片 | ["http://..."] |
| handleRecords | Array | 处理记录 | - |
| └ handlerId | Long | 处理人ID | 10 |
| └ handlerName | String | 处理人姓名 | 管理员 |
| └ handledAt | DateTime | 处理时间 | 2026-03-17 14:00:00 |
| └ handleResult | String | 处理说明 | 已核实并调整份量 |
| └ handleImages | Array | 处理照片 | ["http://..."] |
| satisfaction | String | 满意度 | satisfied |
| satisfactionName | String | 满意度名称 | 满意 |
| satisfactionRemark | String | 满意度评价备注 | 处理及时 |

- **异常场景**：
  - [404]：申诉不存在 → msg："申诉记录不存在"

---

#### 处理申诉与反馈
- **接口路径**：`/api/v1/sys/complaints/{id}/handle`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| id | Long | 是 | 申诉ID（路径参数） | 1 | 申诉标识 |
| handleResult | String | 是 | 处理说明 | 已核实并调整份量 | 处理描述 |
| handleImages | Array | 否 | 处理照片URL列表 | ["http://..."] | 处理照片 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 申诉ID | 1 |
| complaintNo | String | 申诉编号 | CP-20260317001 |
| status | String | 状态 | resolved |
| handledBy | Long | 处理人ID | 10 |
| handledByName | String | 处理人姓名 | 管理员 |
| handledAt | DateTime | 处理时间 | 2026-03-17 14:00:00 |

- **异常场景**：
  - [404]：申诉不存在 → msg："申诉记录不存在"
  - [422]：申诉状态不允许处理 → msg："只有待处理或处理中的申诉可以处理"
  - [400]：处理说明不能为空 → msg："请填写处理说明"
  - [403]：无处理权限 → msg："无权限处理申诉"

---

#### 评价申诉满意度
- **接口路径**：`/api/v1/sys/complaints/{id}/satisfaction`
- **请求方法**：POST
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| id | Long | 是 | 申诉ID（路径参数） | 1 | 申诉标识 |
| satisfaction | String | 是 | 满意度：satisfied/neutral/dissatisfied | satisfied | 满意度 |
| satisfactionRemark | String | 否 | 满意度评价备注 | 处理及时 | 备注 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| id | Long | 申诉ID | 1 |
| complaintNo | String | 申诉编号 | CP-20260317001 |
| satisfaction | String | 满意度 | satisfied |
| satisfactionName | String | 满意度名称 | 满意 |

- **异常场景**：
  - [404]：申诉不存在 → msg："申诉记录不存在"
  - [422]：申诉未处理 → msg："申诉尚未处理，无法评价"
  - [422]：已评价 → msg："该申诉已评价，不能重复评价"
  - [403]：无评价权限 → msg："只有申诉人可以评价"

---

#### 统计分析-评分分布
- **接口路径**：`/api/v1/sys/reviews/score-distribution`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| orgId | Long | 是 | 组织ID | 1001 | 数据隔离 |
| dateRange | String | 否 | 日期范围 | month | 时间范围 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| distribution | Array | 评分分布 | - |
| └ scoreLevel | Integer | 评分等级 | 5 |
| └ scoreName | String | 评分名称 | 五星 |
| └ count | Integer | 条数 | 300 |
| └ percentage | Decimal | 占比（%） | 60.0 |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：组织ID不能为空"

---

#### 统计分析-热门标签
- **接口路径**：`/api/v1/sys/reviews/hot-tags`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| orgId | Long | 是 | 组织ID | 1001 | 数据隔离 |
| dateRange | String | 否 | 日期范围 | month | 时间范围 |
| limit | Integer | 否 | 返回条数，默认10 | 10 | 数量限制 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| total | Integer | 总标签数 | 20 |
| list | Array | 热门标签列表 | - |
| └ tagName | String | 标签名称 | 味道好 |
| └ count | Integer | 使用次数 | 150 |
| └ percentage | Decimal | 占比（%） | 30.0 |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：组织ID不能为空"

---

#### 统计分析-积分排行
- **接口路径**：`/api/v1/sys/reviews/points-ranking`
- **请求方法**：GET
- **请求头**：Content-Type: application/json、Authorization: Bearer {token}
- **请求参数**：

| 参数名 | 类型 | 是否必传 | 说明 | 示例 | 关联PRD功能点 |
|--------|------|----------|------|------|----------------|
| orgId | Long | 是 | 组织ID | 1001 | 数据隔离 |
| dateRange | String | 否 | 日期范围 | month | 时间范围 |
| limit | Integer | 否 | 返回条数，默认10 | 10 | 数量限制 |

- **响应参数**：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| total | Integer | 总人数 | 50 |
| list | Array | 积分排行列表 | - |
| └ rankNo | Integer | 排名 | 1 |
| └ employeeId | Long | 员工ID | 10 |
| └ employeeName | String | 员工姓名 | 张三 |
| └ avatarUrl | String | 头像URL | http://... |
| └ orgName | String | 所属组织 | 后厨部 |
| └ position | String | 职位 | 厨师 |
| └ totalPoints | Integer | 积分数 | 580 |

- **异常场景**：
  - [400]：参数校验失败 → msg："参数校验失败：组织ID不能为空"

---

## 6. 请求与响应示例（通用）

### 6.1 列表查询（分页）
`GET /api/v1/scm/suppliers?pageNum=1&pageSize=20&status=active`

### 6.2 新增
`POST /api/v1/wms/materials`

```json
{
  "materialCode": "MAT-0001",
  "materialName": "鲜鸡蛋",
  "unit": "kg",
  "shelfLifeDays": 30,
  "minStock": 20,
  "maxStock": 300,
  "orgId": 1001
}
```

### 6.3 统一错误响应示例
```json
{
  "code": "VALIDATION_FAILED",
  "message": "参数校验失败：materialName不能为空",
  "data": null,
  "traceId": "xxx",
  "timestamp": "2026-03-16 18:00:00"
}
```

---

## 7. 前后端联调约定

- 前端（Vue3）统一封装：
  - `Authorization` 自动注入
  - `code !== SUCCESS` 统一错误提示
  - 401自动跳转登录并尝试刷新Token
- 后端（Spring Boot）统一返回体拦截器 + 全局异常处理器
- 微服务间调用走内部网关/Feign，保留 `traceId`

---

## 8. 安全与合规要求（接口层）

- 敏感字段脱敏返回（手机号、身份证号等）
- 关键操作写入审计日志：新增/修改/删除/审核/导出
- 高风险接口限流：登录、告警处理、批量操作
- 上传接口病毒扫描（证件、图片、视频）

---

## 9. 状态码与业务状态对齐（关键）

- 采购计划：`draft -> pending -> approved/rejected`
- 采购订单：`pending_submit -> pending_approve -> approved -> delivering -> pending_receipt -> received/inspected -> closed/cancelled`
- 入库/出库单：`draft -> pending -> approved/rejected`
- 烹饪任务：`pending -> in_progress -> completed/cancelled`
- 留样：`sampled -> pending_disposal -> disposed/overdue`
- 告警：`pending -> assigned -> handling -> handled -> reviewed -> closed`
