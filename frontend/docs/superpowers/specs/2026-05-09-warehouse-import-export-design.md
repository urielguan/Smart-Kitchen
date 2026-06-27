# 仓库信息管理导入导出设计

## 背景

当前仓库管理页面 [frontend/src/views/wms/warehouse/index.vue](frontend/src/views/wms/warehouse/index.vue) 已有 `导入`、`导出` 按钮，但还只是静态 UI。根据 PRD，仓库信息管理需要补齐仓库与仓位两类对象的导入导出能力，并满足如下规则：

- 仓库导入支持 `xlsx`，模板字段：组织编码、仓库编码、仓库名称、仓库类型、仓库位置、负责人、联系方式、最大容量、状态。
- 仓位导入支持 `xlsx`，第一版模板字段定稿为：组织编码、所属仓库编码、仓位编码、仓位名称、仓位类型、区域编码、货架编码、货位编码、状态、仓位最大容量。
- 以下“仓位导入”相关章节都以这份字段清单为唯一准绳。
- 单文件不超过 10MB，单次导入不超过 5000 行。
- 导入支持部分成功，失败返回“行号 + 字段 + 原因”。
- 导出支持按当前筛选条件导出仓库列表与仓位列表。
- 导出格式支持 `xlsx/csv`。
- 导出范围受数据权限控制，越权数据不得导出。
- PRD 原始要求中包含“超大数据量导出转异步任务并通知下载”，但第一版范围按本文后续“超大导出处理”章节收敛执行。

另外，业务规则已补充明确：

- 仓库编码唯一：同组织内唯一，冲突即拦截。
- 仓位编码唯一：同仓库内唯一，冲突即拦截。
- 仓位层级唯一：同仓库内“区域-货架-货位”组合唯一，冲突即拦截。

## 目标

为仓库管理页面补齐可用的导入导出闭环，满足以下目标：

1. 保留当前页面顶部的 `导入`、`导出` 按钮，不拆分主界面布局。
2. 支持在一个入口中选择操作对象：`仓库` 或 `仓位`。
3. 仓库导出与仓位导出都基于页面当前筛选条件。
4. 仓位导出不是只导某个详情弹窗里的仓位，而是先按页面筛选条件筛出仓库，再导出这些仓库下的仓位。
5. 前后端实现方式对齐项目已有的供应商/组织导入导出模式，避免另起协议。
6. 第一版完整交付普通导入导出能力；超大导出只做阈值拦截与友好提示，不在本次实现真正异步导出任务。

## 不在本次范围内

本次设计不包含以下内容：

- 新增独立的“导出任务中心”完整页面。
- 真正可下载的异步导出任务链路；本次仅在超量时友好拦截并提示用户缩小筛选范围。
- 支持 `xls`、`csv` 导入；本次导入仅支持 `xlsx`。
- 将仓库页改造成“仓库表”和“仓位表”双 Tab 主界面。
- 用导入覆盖更新已有仓库/仓位；本次按照业务规则，重复即失败，不做更新。

## 交互设计

### 页面入口

- 当前页面顶部现有按钮：
  - `导入`
  - `导出`
- 当前仓位数据模型与表单仅包含 `locationCode`、`locationName`、容量、温湿度、状态、备注等字段，尚未包含 PRD 要求的 `仓位类型`、`区域编码`、`货架编码`、`货位编码`。因此本次若要满足仓位导入规则，必须一并补齐这些字段在前端类型、表单、后端 DTO / Entity / Mapper / 导出字段中的贯通支持。

### 导入流程

1. 点击 `导入`
2. 打开“导入类型选择弹窗”，可选：
   - `仓库导入`
   - `仓位导入`
3. 选择类型后进入统一导入弹窗 [WarehouseImportDialog.vue](frontend/src/components/business/warehouse/WarehouseImportDialog.vue)（本次新增文件）
4. 弹窗支持：
   - 下载对应模板
   - 上传单个 `xlsx` 文件
   - 显示对象对应的导入说明
   - 点击“确认导入”后提交
5. 导入完成后：
   - 全部成功：提示成功条数，刷新仓库列表与现有统计卡片（即页面 [WarehouseStatistics.vue](frontend/src/components/business/warehouse/WarehouseStatistics.vue) 依赖的 `store.fetchStatistics()` 数据）
   - 部分成功：提示成功/失败条数，并提供错误文件下载入口
   - 全部失败：提示失败原因，并提供错误文件下载入口

### 导出流程

1. 点击 `导出`
2. 打开“导出类型选择弹窗”，可选：
   - `仓库导出`
   - `仓位导出`
3. 同一弹窗中继续选择导出格式：
   - `xlsx`
   - `csv`
4. 点击“确认导出”后调用对应导出接口
5. 结果：
   - 常规数据量：直接下载文件
   - 超大数据量：后端按阈值拦截并返回明确业务错误，前端提示用户缩小筛选范围后重试

## 筛选与数据范围规则

### 仓库导出

仓库导出直接使用页面当前筛选条件：

- 仓库名称/编码
- 仓库类型
- 状态

导出的数据集是：**当前用户数据权限范围内，满足上述筛选条件的仓库列表**。

### 仓位导出

仓位导出也使用页面当前筛选条件，但含义不同：

1. 先用页面筛选条件筛出仓库集合。
2. 再导出这些仓库下的仓位列表。

也就是说，仓位导出的最终数据集是：**当前用户数据权限范围内，满足页面仓库筛选条件的仓库所关联的仓位**。

这条规则避免了“仓位导出只能从某个仓库详情弹窗进入”的限制，也与用户已经确认的预期一致。

## 前端设计

### 组件与页面改动

#### 1. 页面入口调整

修改 [frontend/src/views/wms/warehouse/index.vue](frontend/src/views/wms/warehouse/index.vue)：

- 将 `导入` 按钮接入导入类型选择逻辑
- 将 `导出` 按钮接入导出类型选择逻辑
- 保留现有搜索表单作为仓库/仓位导出的共同筛选源
- 在页面中挂载新增的导入/导出弹窗组件

#### 2. 仓库导入弹窗

新增 [frontend/src/components/business/warehouse/WarehouseImportDialog.vue](frontend/src/components/business/warehouse/WarehouseImportDialog.vue)（本次新增）

职责：

- 根据当前类型（仓库/仓位）展示对应标题、模板下载、说明文案
- 校验文件格式与大小
- 提交导入请求
- 导入完成后显示结果反馈
- 若有错误文件，提供下载入口

实现风格直接参考：

- [frontend/src/components/business/supplier/SupplierImportDialog.vue](frontend/src/components/business/supplier/SupplierImportDialog.vue)

#### 3. 仓库导出弹窗

新增 [frontend/src/components/business/warehouse/WarehouseExportDialog.vue](frontend/src/components/business/warehouse/WarehouseExportDialog.vue)（本次新增）：

职责：

- 选择导出对象：仓库/仓位
- 选择导出格式：xlsx/csv
- 确认导出时，将页面当前搜索条件一并传入 API
- 根据接口返回结果决定是直接下载文件，还是展示“导出数据量过大，请缩小筛选范围后重试”的提示

### Store 设计

修改 [frontend/src/stores/modules/warehouse.ts](frontend/src/stores/modules/warehouse.ts)：

新增状态：

- `importDialogVisible`
- `exportDialogVisible`
- `importTarget`: `'warehouse' | 'location' | null`
- `exportTarget`: `'warehouse' | 'location' | null`
- `exportFormat`: `'xlsx' | 'csv'`
- `importLoading`
- `exportLoading`

新增行为：

- 打开/关闭导入弹窗
- 打开/关闭导出弹窗
- 记录当前导入导出对象
- 导入成功后刷新 `fetchList()` 与 `fetchStatistics()`

### 前端 API 设计

修改 [frontend/src/api/modules/warehouse.ts](frontend/src/api/modules/warehouse.ts)：

新增仓库相关 API：

- `downloadWarehouseTemplate()`
- `importWarehouses(file: File)`
- `exportWarehouses(params, format)`
- `downloadWarehouseImportErrorFile(fileName)`

新增仓位相关 API：

- `downloadLocationTemplate()`
- `importLocations(file: File)`
- `exportLocations(params, format)`
- `downloadLocationImportErrorFile(fileName)`

实现模式对齐：

- [frontend/src/api/modules/supplier.ts](frontend/src/api/modules/supplier.ts)
- [frontend/src/api/modules/org.ts](frontend/src/api/modules/org.ts)

其中：

- 模板下载和错误文件下载使用 blob 下载或新窗口下载的既有模式
- `GET /export` 在本次实现中始终返回文件流，前端始终按 `blob` 处理，不引入“同一接口同时返回 JSON 和文件”的双响应协议
- 当导出数据量超过阈值时，后端返回明确业务错误，提示用户缩小筛选范围；前端不处理异步任务对象
- 文件名优先从 `content-disposition` 中解析

### 前端类型

视情况扩展 [frontend/src/types/warehouse.ts](frontend/src/types/warehouse.ts)，新增或补齐：

- `WarehouseImportResult`
- `LocationImportResult`
- `WarehouseExportFormat = 'xlsx' | 'csv'`
- `LocationType` 相关字段
- `regionCode`、`shelfCode`、`slotCode` 相关字段

其中 `WarehouseImportResult` 与 `LocationImportResult` 的第一版返回结构固定为：

- `successCount: number`
- `failureCount: number`
- `totalCount: number`
- `partialSuccess: boolean`
- `errors: Array<{ rowNumber: number; field: string; reason: string }>`
- `errorFileName?: string`

第一版统一使用 `errorFileName` 作为错误文件下载标识；前端通过对应的“下载导入错误文件”接口换取实际文件，不使用 `errorFileUrl`。

## 后端设计

### 仓库导入导出接口

修改 [backend/wms-service/src/main/java/com/xykj/wms/controller/WarehouseController.java](backend/wms-service/src/main/java/com/xykj/wms/controller/WarehouseController.java)，新增：

- `GET /api/v1/wms/warehouses/import/template`
- `POST /api/v1/wms/warehouses/import`
- `GET /api/v1/wms/warehouses/export`
- `GET /api/v1/wms/warehouses/import/errors/{fileName}`

### 仓位导入导出接口

在仓位控制器中新增：

- `GET /api/v1/wms/locations/import/template`
- `POST /api/v1/wms/locations/import`
- `GET /api/v1/wms/locations/export`
- `GET /api/v1/wms/locations/import/errors/{fileName}`

若当前仓位只有 service 而无 controller，本次需新增独立控制器承接以上接口。

### 服务层改动

修改或扩展：

- [backend/wms-service/src/main/java/com/xykj/wms/service/WarehouseService.java](backend/wms-service/src/main/java/com/xykj/wms/service/WarehouseService.java)
- 相关 `LocationService`
- 对应 impl

新增职责：

- 下载导入模板
- 执行仓库导入
- 执行仓位导入
- 导出仓库
- 导出仓位
- 下载导入错误文件

### 导入实现策略

#### 仓库导入

模板字段：

- 组织编码
- 仓库编码
- 仓库名称
- 仓库类型
- 仓库位置
- 负责人
- 联系方式
- 最大容量
- 状态

校验规则：

- 文件必须为 `xlsx`
- 文件大小 ≤ 10MB
- 数据行数 ≤ 5000
- 表头必须匹配模板
- `组织编码` 必填且必须能定位到当前用户有权限的组织
- `仓库编码`、`仓库名称` 必填
- `仓库类型`、`状态` 必须是合法枚举值
- `最大容量` 必须为非负数
- 同组织内 `仓库编码` 唯一，冲突即该行失败

#### 仓位导入

模板字段：

- 组织编码
- 所属仓库编码
- 仓位编码
- 仓位名称
- 仓位类型
- 区域编码
- 货架编码
- 货位编码
- 状态
- 仓位最大容量

校验规则：

- `组织编码`、`所属仓库编码`、`仓位编码`、`仓位名称` 必填
- `仓位类型` 必填，且必须是系统允许的仓位类型枚举值
- `区域编码`、`货架编码`、`货位编码` 为仓位层级字段，三者必须成组提供：要么全部为空，要么全部非空，不允许只填其中一部分
- 当 `区域编码`、`货架编码`、`货位编码` 三者均有值时，按其组合做同仓库内唯一性校验
- 必须能定位到目标仓库，且组织与仓库关系正确
- `状态` 必须合法
- `仓位最大容量` 必须为非负数
- 同仓库内 `仓位编码` 唯一，冲突即该行失败
- 同仓库内 `区域编码-货架编码-货位编码` 组合唯一，冲突即该行失败

### 仓位字段映射与现有模型调整

当前 [frontend/src/types/warehouse.ts](frontend/src/types/warehouse.ts) 中的 `Location` / `LocationForm` 仍以“仓位名称 + 容量 + 温湿度 + 状态 + 备注”为主，而 PRD 的仓位导入模板要求新增以下主数据字段：

- `locationType`（仓位类型）
- `regionCode`（区域编码）
- `shelfCode`（货架编码）
- `slotCode`（货位编码）

因此本次实现需要明确以下映射规则：

- `locationType`、`regionCode`、`shelfCode`、`slotCode` 作为仓位持久化字段新增到前后端模型中，不是仅供导入临时使用。
- 现有 `locationName` 仍保留为仓位必填展示字段，本次不会移除，也不由层级编码自动生成。
- 仓位导入模板虽然未单列 `仓位名称`，但为了与现有仓位模型、表单和列表保持一致，第一版实现时应在模板中补充 `仓位名称` 字段；否则导入后无法满足现有创建/编辑/展示链路的数据要求。
- `regionCode`、`shelfCode`、`slotCode` 用于承载仓位层级信息，并参与“同仓库内区域-货架-货位组合唯一”校验。
- 现有仓位表单 [frontend/src/components/business/warehouse/LocationForm.vue](frontend/src/components/business/warehouse/LocationForm.vue) 也需要同步补齐这些字段，否则导入后的记录无法在编辑态完整回显与维护。

这意味着本次不仅是“加导入导出接口”，还包含仓位主数据模型的增量扩展与前后端贯通。

导入接口支持部分成功，第一版返回结构固定包含：

- `successCount`
- `failureCount`
- `totalCount`
- `errors`: 行级失败明细数组
- `errorFileName`
- `partialSuccess`: 是否部分成功

当失败明细较多时，服务端将错误信息写入临时错误文件，供前端下载。模式参考：

- [backend/scm-service/src/main/java/com/xykj/scm/controller/SupplierController.java](backend/scm-service/src/main/java/com/xykj/scm/controller/SupplierController.java)
- [backend/sys-service/src/main/java/com/xykj/sys/controller/OrganizationController.java](backend/sys-service/src/main/java/com/xykj/sys/controller/OrganizationController.java)

### 导出实现策略

#### 仓库导出

- 入参使用现有仓库查询 DTO，并增加 `format=xlsx|csv`
- 服务端按当前用户数据权限 + 页面筛选条件查询仓库
- 返回仓库导出文件

#### 仓位导出

- 入参复用页面当前仓库筛选条件，而不是单独定义仓位筛选表单
- 服务端先按该条件筛出仓库 ID 集合
- 再查询这些仓库下的仓位列表
- 返回仓位导出文件

这种设计保证：

- 数据权限由服务端统一裁决
- 前端不需要先查出全量仓库 ID 再传回后端
- 超量导出转异步时，后端可以直接基于筛选条件重放查询

### 导出格式

支持：

- `xlsx`
- `csv`

建议默认：

- 弹窗默认选中 `xlsx`

### 超大导出处理

PRD 原始要求是“超大数据量导出转异步任务并通知下载”。考虑到当前仓库模块尚无现成导出任务中心，而本次核心目标是先完成真实可用的普通导入导出闭环，因此第一版对这条规则做如下收敛：

- 第一版导出阈值固定为 `5000 条`。

1. 服务端在导出前先估算数据量。
2. 未超过阈值（`<= 5000 条`）：继续走同步导出，直接返回 `xlsx/csv` 文件流。
3. 超过阈值（`> 5000 条`）：本次不实现真正异步导出任务，不返回任务对象，也不引入新的异步下载协议；后端直接返回明确业务错误，提示“当前导出数据量过大，请缩小筛选范围后重试”。
4. 前端收到该错误后，展示友好提示，不触发下载。

这样可以确保：

- 第一版普通导出体验不受影响。
- `GET /export` 始终是单一文件流语义，不会出现同一接口既返回 blob 又返回 JSON 的协议冲突。
- 本次范围不扩张为“导出任务系统”建设。

后续如果业务明确要求补齐真正异步导出任务，再单独设计任务实体、任务状态轮询、下载入口和任务中心页面。

## 数据权限

所有导入导出都以服务端权限判断为准。

### 导入

- 用户对目标组织或目标仓库无权限时，该行失败
- 不允许通过 Excel 导入越权创建仓库/仓位

### 导出

- 仅导出当前用户有权访问的数据
- 即使前端传入更宽泛的筛选条件，也不得突破服务端数据权限范围

这部分继续复用现有仓库模块的 `DataScope` 与 query scope 处理逻辑，参考：

- [WarehouseServiceImpl.java](backend/wms-service/src/main/java/com/xykj/wms/service/impl/WarehouseServiceImpl.java)

## 异常与提示策略

### 导入前端提示

- 未选文件：提示“请先选择要导入的文件”
- 文件格式错误：提示“请选择 xlsx 文件”
- 文件超 10MB：提示“文件大小不能超过 10MB”
- 行数超限：由后端返回错误提示“单次导入不能超过 5000 行”

### 导入结果提示

- 全部成功：`成功导入 X 条`
- 部分成功：`成功 X 条，失败 Y 条，可下载错误文件查看详情`
- 全部失败：`导入失败，请下载错误文件查看详情`

### 导出前端提示

- 同步导出成功：浏览器直接下载
- 超量导出：提示“当前导出数据量过大，请缩小筛选范围后重试”
- 导出失败：提示接口返回的错误信息

## 测试与验收

### 前端验证

1. `导入` 按钮能打开对象选择并进入对应导入弹窗。
2. `导出` 按钮能打开对象与格式选择弹窗。
3. 仓库导出会带上当前 `searchForm` 条件。
4. 仓位导出也会带上当前 `searchForm` 条件。
5. 导入文件格式错误、超大小、空文件时提示正确。
6. 导入成功后页面列表和统计自动刷新。

### 后端验证

1. 仓库/仓位模板下载接口存在且可用。
2. 仓库/仓位导入接口支持部分成功。
3. 仓库编码重复时返回明确失败行与原因。
4. 仓位编码重复时返回明确失败行与原因。
5. 仓位层级组合重复时返回明确失败行与原因。
6. 仓库导出结果与页面筛选条件一致。
7. 仓位导出结果仅包含符合筛选条件的仓库下的仓位。
8. 越权数据不会导出。

### 手工验收场景

1. 使用仓库名称/类型/状态筛选后导出仓库，结果与页面一致。
2. 使用同样筛选后导出仓位，结果仅包含命中仓库下的仓位。
3. 导入混合数据，验证部分成功与错误文件下载。
4. 验证文件大小、行数、唯一性冲突都能被正确拦截。

## 实施顺序建议

1. 前端补导入/导出弹窗骨架与类型选择交互。
2. 前端先补 API 定义与最小回归测试。
3. 后端补仓库导入导出接口。
4. 后端补仓位导入导出接口，并同步补齐仓位新增字段在 DTO / Entity / Mapper / 表单中的贯通。
5. 接通前端错误文件下载与结果提示。
6. 最后补导出阈值判断与超量友好提示链路。

## 关键文件

### 前端

- [frontend/src/views/wms/warehouse/index.vue](frontend/src/views/wms/warehouse/index.vue)
- [frontend/src/stores/modules/warehouse.ts](frontend/src/stores/modules/warehouse.ts)
- [frontend/src/api/modules/warehouse.ts](frontend/src/api/modules/warehouse.ts)
- [frontend/src/types/warehouse.ts](frontend/src/types/warehouse.ts)
- [frontend/src/components/business/warehouse/WarehouseImportDialog.vue](frontend/src/components/business/warehouse/WarehouseImportDialog.vue)
- [frontend/src/components/business/warehouse/WarehouseExportDialog.vue](frontend/src/components/business/warehouse/WarehouseExportDialog.vue)

### 后端

- [backend/wms-service/src/main/java/com/xykj/wms/controller/WarehouseController.java](backend/wms-service/src/main/java/com/xykj/wms/controller/WarehouseController.java)
- `backend/wms-service/src/main/java/com/xykj/wms/controller/LocationController.java`（若不存在则新增）
- [backend/wms-service/src/main/java/com/xykj/wms/service/WarehouseService.java](backend/wms-service/src/main/java/com/xykj/wms/service/WarehouseService.java)
- `backend/wms-service/src/main/java/com/xykj/wms/service/LocationService.java`
- [backend/wms-service/src/main/java/com/xykj/wms/service/impl/WarehouseServiceImpl.java](backend/wms-service/src/main/java/com/xykj/wms/service/impl/WarehouseServiceImpl.java)
- 对应 `LocationServiceImpl`

## 决策摘要

- 顶部按钮保持两颗，不拆为四颗。
- `导入` 与 `导出` 都先选择对象类型。
- 仓位导出使用页面当前仓库筛选条件，导出命中仓库下的仓位。
- 导入遇到唯一性冲突时直接拦截，不做覆盖更新。
- 导出支持 `xlsx/csv`，普通数据量同步导出，超量时按阈值拦截并提示缩小筛选范围。
- 不在本次补完整导出任务中心，也不实现真正异步导出任务。
