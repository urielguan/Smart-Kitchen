<script setup lang="ts">
import { ref, computed, onMounted, h } from 'vue'
import { useRouter } from 'vue-router'
import { sysNotificationApi, type SysNotificationVO, type SysNotificationDetailVO, type ExecutableAction, type NotificationQuery } from '@/api/modules/sys-notification'
import { ElMessage, ElMessageBox } from 'element-plus'
import { WarningFilled } from '@element-plus/icons-vue'
import { formatDateTime } from '@/utils'
import { NOTIFICATION_PERMISSIONS } from '@/constants/permission'

const router = useRouter()

/** 构建确认弹窗 message */
const renderConfirmMessage = (title: string, description: string) => () =>
  h('div', { class: 'org-confirm' }, [
    h('div', { class: 'org-confirm__content' }, [
      h(WarningFilled, { class: 'org-confirm__icon' }),
      h('div', { class: 'org-confirm__text' }, [
        h('div', { class: 'org-confirm__title' }, title),
        h('div', { class: 'org-confirm__description' }, description),
      ]),
    ]),
  ])

// 查询表单
const queryForm = ref<Partial<NotificationQuery>>({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  category: '',
  readStatus: '',
  riskLevel: '',
})

// 列表数据
const loading = ref(false)
const tableData = ref<SysNotificationVO[]>([])
const total = ref(0)

// 分类选项
const categoryOptions = [
  { label: '食安告警', value: 'food_safety_alert' },
  { label: '审批待办', value: 'approval_todo' },
  { label: '系统通知', value: 'system_notice' },
  { label: '安全风控', value: 'security_risk' },
  { label: '平台公告', value: 'platform_announcement' },
]

const riskLevelOptions = [
  { label: '严重', value: 'severe' },
  { label: '高', value: 'high' },
  { label: '关注', value: 'attention' },
  { label: '普通', value: 'normal' },
]

const readStatusOptions = [
  { label: '未读', value: 'unread' },
  { label: '已读', value: 'read' },
]

// 多选
const selectedIds = ref<number[]>([])
const handleSelectionChange = (rows: SysNotificationVO[]) => {
  selectedIds.value = rows.map(r => r.id)
}

// 获取列表
const fetchList = async () => {
  loading.value = true
  try {
    const res = await sysNotificationApi.getList(queryForm.value)
    if (res.code === 'SUCCESS' && res.data) {
      tableData.value = res.data.list || []
      total.value = res.data.total || 0
    }
  } catch (error: any) {
    ElMessage.error(error.message || '获取消息列表失败')
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  queryForm.value.pageNum = 1
  fetchList()
}

// 重置
const handleReset = () => {
  queryForm.value = { pageNum: 1, pageSize: 10, keyword: '', category: '', readStatus: '', riskLevel: '' }
  fetchList()
}

// 分页
const handlePageChange = (page: number) => {
  queryForm.value.pageNum = page
  fetchList()
}

const handleSizeChange = (size: number) => {
  queryForm.value.pageSize = size
  queryForm.value.pageNum = 1
  fetchList()
}

// 标记已读
const handleMarkRead = async (row: SysNotificationVO) => {
  try {
    const res = await sysNotificationApi.markAsRead(row.id)
    if (res.code === 'SUCCESS') {
      ElMessage.success('已标记为已读')
      fetchList()
    }
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  }
}

// 标记未读
const handleMarkUnread = async (row: SysNotificationVO) => {
  try {
    const res = await sysNotificationApi.markAsUnread(row.id)
    if (res.code === 'SUCCESS') {
      ElMessage.success('已标记为未读')
      fetchList()
    }
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  }
}

// 批量标记已读
const handleBatchRead = async () => {
  if (selectedIds.value.length === 0) {
    ElMessage.warning('请先选择消息')
    return
  }
  try {
    const res = await sysNotificationApi.batchMarkAsRead(selectedIds.value)
    if (res.code === 'SUCCESS') {
      ElMessage.success('批量标记已读成功')
      fetchList()
    }
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  }
}

// 批量删除
const handleBatchDelete = async () => {
  if (selectedIds.value.length === 0) {
    ElMessage.warning('请先选择消息')
    return
  }
  try {
    await ElMessageBox({
      title: '删除消息',
      message: renderConfirmMessage('删除消息', `确定要删除选中的 ${selectedIds.value.length} 条消息吗？`),
      customClass: 'material-message-box',
      showClose: false,
      closeOnClickModal: false,
      showCancelButton: true,
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    })
    const res = await sysNotificationApi.batchDelete(selectedIds.value)
    if (res.code === 'SUCCESS') {
      ElMessage.success('删除成功')
      fetchList()
    }
  } catch { /* cancel */ }
}

// 全部标记已读
const handleMarkAllRead = async () => {
  const unreadIds = tableData.value.filter(r => r.readStatus === 'unread').map(r => r.id)
  if (unreadIds.length === 0) {
    ElMessage.info('当前页没有未读消息')
    return
  }
  try {
    const res = await sysNotificationApi.batchMarkAsRead(unreadIds)
    if (res.code === 'SUCCESS') {
      ElMessage.success('全部标记已读成功')
      fetchList()
    }
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  }
}

// 风险等级 tag 类型
const getRiskTagType = (level: string) => {
  const map: Record<string, string> = { severe: 'danger', high: 'danger', attention: 'warning', normal: 'info' }
  return map[level] || 'info'
}

// 分类 tag 类型
const getCategoryTagType = (category: string) => {
  const map: Record<string, string> = { food_safety_alert: 'danger', approval_todo: 'warning', system_notice: 'info', security_risk: 'danger', platform_announcement: '' }
  return map[category] || 'info'
}

// 已读状态样式
const getRowClassName = ({ row }: { row: SysNotificationVO }) => {
  return row.readStatus === 'unread' ? 'unread-row' : ''
}

// ========== 详情弹窗 ==========
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailData = ref<SysNotificationDetailVO | null>(null)

// 解析可执行操作
const parsedActions = computed<ExecutableAction[]>(() => {
  if (!detailData.value?.executableActions) return []
  try {
    const raw = detailData.value.executableActions
    return typeof raw === 'string' ? JSON.parse(raw) : Array.isArray(raw) ? raw : []
  } catch { return [] }
})

const handleAction = (action: ExecutableAction) => {
  detailVisible.value = false
  router.push(action.route)
}

const handleDetail = async (row: SysNotificationVO) => {
  detailVisible.value = true
  detailLoading.value = true
  try {
    const res = await sysNotificationApi.getDetail(row.id)
    if (res.code === 'SUCCESS' && res.data) {
      detailData.value = res.data
    }
  } catch (error: any) {
    ElMessage.error(error.message || '获取详情失败')
  } finally {
    detailLoading.value = false
  }
  fetchList()
}

const handleCloseDetail = () => {
  detailVisible.value = false
  detailData.value = null
}

onMounted(() => {
  fetchList()
})
</script>

<template>
  <div class="notification-page">
    <!-- 搜索栏 -->
    <div class="toolbar">
      <el-input
        v-model="queryForm.keyword"
        placeholder="搜索标题"
        clearable
        style="width: 220px"
        @keyup.enter="handleSearch"
      />
      <el-select v-model="queryForm.category" placeholder="全部分类" clearable style="width: 200px">
        <el-option v-for="opt in categoryOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <el-select v-model="queryForm.readStatus" placeholder="全部状态" clearable style="width: 200px">
        <el-option v-for="opt in readStatusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <el-select v-model="queryForm.riskLevel" placeholder="全部风险" clearable style="width: 200px">
        <el-option v-for="opt in riskLevelOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <el-button class="btn-search" @click="handleSearch">查询</el-button>
      <el-button class="btn-reset" @click="handleReset">重置</el-button>
    </div>

    <!-- 表格区 -->
    <div class="table-wrapper">
      <!-- 操作栏 -->
      <div class="table-header">
        <el-button class="btn-all-read" v-permission="NOTIFICATION_PERMISSIONS.MARK_READ" @click="handleMarkAllRead">全部已读</el-button>
        <el-button class="btn-batch-read" v-permission="NOTIFICATION_PERMISSIONS.MARK_READ" :disabled="selectedIds.length === 0" @click="handleBatchRead">
          批量已读{{ selectedIds.length > 0 ? `(${selectedIds.length})` : '' }}
        </el-button>
        <el-button class="btn-batch-delete" v-permission="NOTIFICATION_PERMISSIONS.DELETE" :disabled="selectedIds.length === 0" @click="handleBatchDelete">
          批量删除{{ selectedIds.length > 0 ? `(${selectedIds.length})` : '' }}
        </el-button>
      </div>

      <!-- 表格 -->
      <el-table
        :data="tableData"
        v-loading="loading"
        :row-class-name="getRowClassName"
        :cell-style="{ verticalAlign: 'middle' }"
        @selection-change="handleSelectionChange"
        style="width: 100%"
        height="100%"
      >
        <el-table-column type="selection" width="40" />
        <el-table-column label="分类" width="120">
          <template #default="{ row }">
            <el-tag :type="getCategoryTagType(row.category)" size="small">{{ row.categoryName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="标题" min-width="250">
          <template #default="{ row }">
            <div>
              <span :class="{ 'unread-title': row.readStatus === 'unread' }" class="title-text" @click="handleDetail(row)">{{ row.title }}</span>
              <div v-if="row.summary" class="summary-text">{{ row.summary }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="风险等级" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="getRiskTagType(row.riskLevel)" size="small">{{ row.riskLevelName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.readStatus === 'unread' ? 'danger' : 'info'" size="small" effect="plain">
              {{ row.readStatusName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="110" prop="sourceModule" />
        <el-table-column label="时间" width="160">
          <template #default="{ row }">
            <span class="time-display">{{ row.timeDisplay }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleDetail(row)">详情</el-button>
            <el-button v-permission="NOTIFICATION_PERMISSIONS.MARK_READ" v-if="row.readStatus === 'unread'" link type="primary" @click="handleMarkRead(row)">已读</el-button>
            <el-button v-permission="NOTIFICATION_PERMISSIONS.MARK_READ" v-else link type="info" @click="handleMarkUnread(row)">未读</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination" v-if="total > 0">
        <span class="total">共 {{ total }} 项数据</span>
        <el-pagination
          v-model:current-page="queryForm.pageNum"
          v-model:page-size="queryForm.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          :pager-count="7"
          layout="sizes, prev, pager, next"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </div>

    <!-- 详情弹窗 -->
    <el-dialog
      :model-value="detailVisible"
      width="600px"
      :close-on-click-modal="false"
      :show-close="false"
      align-center
      class="notification-detail-dialog"
      @close="handleCloseDetail"
    >
      <template #header>
        <div class="dialog-header">
          <span class="dialog-title">消息详情</span>
          <div class="close-btn" @click="handleCloseDetail">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
              <line x1="6" y1="6" x2="18" y2="18" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
              <line x1="6" y1="18" x2="18" y2="6" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
            </svg>
          </div>
        </div>
      </template>

      <div v-loading="detailLoading" class="detail-body">
        <template v-if="detailData">
          <!-- 基础信息 -->
          <div class="detail-section">
            <div class="section-title">
              <span class="title-bar" />
              基础信息
            </div>
            <div class="info-table">
              <div class="info-label">分类</div>
              <div class="info-value">
                <el-tag :type="getCategoryTagType(detailData.category)" size="small">{{ detailData.categoryName }}</el-tag>
              </div>
              <div class="info-label">风险等级</div>
              <div class="info-value">
                <el-tag :type="getRiskTagType(detailData.riskLevel)" size="small">{{ detailData.riskLevelName }}</el-tag>
              </div>

              <div class="info-label">状态</div>
              <div class="info-value">
                <el-tag :type="detailData.readStatus === 'unread' ? 'danger' : 'info'" size="small">
                  {{ detailData.readStatusName }}
                </el-tag>
              </div>
              <div class="info-label">来源</div>
              <div class="info-value">{{ detailData.sourceModule || '-' }}</div>

              <div class="info-label">发送时间</div>
              <div class="info-value info-value--span3">{{ detailData.sendTime ? formatDateTime(detailData.sendTime) : '-' }}</div>
            </div>
          </div>

          <!-- 内容详情 -->
          <div class="detail-section">
            <div class="section-title">
              <span class="title-bar" />
              内容详情
            </div>
            <div class="content-box">
              <div class="content-title" :class="{ 'unread-title': detailData.readStatus === 'unread' }">{{ detailData.title }}</div>
              <div v-if="detailData.summary" class="content-summary">{{ detailData.summary }}</div>
              <div v-if="detailData.body" class="content-body">{{ detailData.body }}</div>
              <div v-if="parsedActions.length > 0" class="action-buttons">
                <el-button v-for="(action, idx) in parsedActions" :key="idx" type="primary" link @click="handleAction(action)">{{ action.label }}</el-button>
              </div>
            </div>
          </div>
        </template>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button class="btn-cancel" @click="handleCloseDetail">关闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss">
/* ---- Dialog 容器（unscoped） ---- */
.notification-detail-dialog.el-dialog {
  background: #FFFFFF;
  --el-dialog-border-radius: 12px;
  --el-dialog-padding-primary: 0;
  border-radius: 12px !important;
  overflow: hidden;
  padding: 0 !important;
  display: flex !important;
  flex-direction: column !important;
}

.notification-detail-dialog.el-dialog .el-dialog__header {
  padding: 24px 24px 13px !important;
  margin-right: 0;
  border-bottom: 1px solid #E1E2E9;
}

.notification-detail-dialog.el-dialog .el-dialog__body {
  padding: 16px 24px 24px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.notification-detail-dialog.el-dialog .el-dialog__footer {
  padding: 0;
  border-top: 1px solid #E1E2E9;
}

/* ---- 表格基础样式（unscoped） ---- */
.notification-page .el-table {
  --el-table-border-color: #E7E7E7;
  --el-table-row-height: 46px;

  .el-table__cell {
    padding-left: 12px;
    padding-right: 12px;
    font-family: 'PingFang SC', sans-serif;
    font-weight: 400;
    font-size: 14px;
    line-height: 22px;
    color: #000000E5;
  }
}

.notification-page .el-table__body tr {
  height: 46px;
  border-bottom: 1px solid #E7E7E7;

  &:nth-child(odd) td {
    background-color: #FFFFFF;
  }

  &:nth-child(even) td {
    background-color: #F5F9FF;
  }
}

.notification-page .el-table__inner-wrapper::before {
  display: none;
}

.notification-page .el-table thead th {
  font-family: 'PingFang SC', sans-serif;
  font-weight: 400;
  font-size: 14px;
  line-height: 22px;
  color: #00000066;
  background-color: #F5F9FF !important;
  border-bottom: 1px solid #E7E7E7;
}

/* ---- Checkbox 颜色 ---- */
.notification-page .el-checkbox__input.is-checked .el-checkbox__inner {
  background-color: #7288FA;
  border-color: #7288FA;
}

.notification-page .el-checkbox__input.is-indeterminate .el-checkbox__inner {
  background-color: #7288FA;
  border-color: #7288FA;
}

/* ---- 详情弹窗标签自定义样式（与列表 Tag 不同） ---- */
.notification-detail-dialog .detail-body .el-tag {
  border-radius: 5px;
  height: 24px;
  padding: 0 8px;
  line-height: 22px;
  display: inline-flex;
  align-items: center;
}
</style>

<style lang="scss" scoped>
.notification-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  gap: 16px;
}

.notification-page > .toolbar,
.notification-page > .table-wrapper {
  margin: 0;
}

/* ---- 搜索栏 ---- */
.toolbar {
  background: #FFFFFF;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  padding: 20px;
  flex-shrink: 0;
  display: flex;
  gap: 8px;
  align-items: center;

  :deep(.el-button) {
    margin-left: 4px;
  }

  .btn-search {
    margin-left: auto;
  }
}

.btn-search {
  width: 60px;
  height: 32px;
  padding: 5px 16px;
  background: #7288FA;
  border-color: #7288FA;
  border-radius: 6px;
  color: #fff;

  &:hover {
    background: #5C75E8;
    border-color: #5C75E8;
    color: #fff;
  }
}

.btn-reset {
  width: 60px;
  height: 32px;
  padding: 5px 16px;
  background: #F2F4F8;
  border-color: #F2F4F8;
  border-radius: 6px;
  font-family: 'PingFang SC', sans-serif;
  font-weight: 400;
  font-size: 14px;
  line-height: 22px;
  color: rgba(0, 0, 0, 0.9);

  &:hover {
    background: #E3E7EF;
    border-color: #E3E7EF;
    color: rgba(0, 0, 0, 0.9);
  }
}

/* ---- 表格区 ---- */
.table-wrapper {
  background: #FFFFFF;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow: hidden;

  :deep(.el-table) {
    flex: 1;
    min-height: 0;
    padding: 0 16px;
  }

  :deep(.el-tag--success) {
    background: #E3F9E9;
    border: 1px solid #2BA471;
    border-radius: 3px;
    color: #2BA471;
    height: 24px;
    padding: 2px 8px;
    line-height: 20px;
  }

  :deep(.el-tag--primary) {
    background: #E8F3FF;
    border: 1px solid #3370FF;
    border-radius: 3px;
    color: #3370FF;
    height: 24px;
    padding: 2px 8px;
    line-height: 20px;
  }

  :deep(.el-tag--warning) {
    background: #FFF1E9;
    border: 1px solid #E37318;
    border-radius: 3px;
    color: #E37318;
    height: 24px;
    padding: 2px 8px;
    line-height: 20px;
  }

  :deep(.el-tag--danger) {
    background: #FFF0ED;
    border: 1px solid #D54941;
    border-radius: 3px;
    color: #D54941;
    height: 24px;
    padding: 2px 8px;
    line-height: 20px;
  }

  :deep(.el-tag--info) {
    background: #F4F4F5;
    border: 1px solid #909399;
    border-radius: 3px;
    color: #909399;
    height: 24px;
    padding: 2px 8px;
    line-height: 20px;
  }
}

.table-header {
  padding: 16px 20px;
  display: flex;
  gap: 8px;
  align-items: center;
  flex-shrink: 0;

  :deep(.el-button) {
    margin-left: 0;
  }
}

.btn-all-read {
  height: 32px;
  padding: 5px 16px;
  background: #7288FA;
  border-color: #7288FA;
  border-radius: 6px;
  color: #fff;

  &:hover {
    background: #5C75E8;
    border-color: #5C75E8;
    color: #fff;
  }
}

.btn-batch-read {
  height: 32px;
  padding: 5px 16px;
  background: #FFFFFF;
  border: 1px solid #BEC0CA;
  border-radius: 6px;
  color: #606266;

  &:hover {
    background: #F5F7FA;
    border-color: #7288FA;
    color: #7288FA;
  }

  &.is-disabled {
    opacity: 0.45;
  }
}

.btn-batch-delete {
  height: 32px;
  padding: 5px 16px;
  background: #FF7474;
  border-color: #FF7474;
  border-radius: 6px;
  color: #fff;

  &:hover {
    background: #FF3D3D;
    border-color: #FF3D3D;
    color: #fff;
  }

  &.is-disabled {
    opacity: 0.45;
  }
}

/* ---- 表格行样式 ---- */
:deep(.el-table__body tr.unread-row td) {
  background-color: #FFF7E6 !important;
}

.unread-title {
  font-weight: 600;
  color: #303133;
}

.time-display {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
}

.title-text {
  cursor: pointer;
  font-size: 14px;
  color: #303133;

  &:hover {
    color: #5570F1;
  }
}

.summary-text {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ---- 操作列按钮 ---- */
:deep(.el-button--primary.is-link) {
  color: #5570F1;

  &:hover {
    color: #2E45D6;
  }

  &:focus {
    color: #5570F1;
  }
}

/* ---- 分页 ---- */
.pagination {
  padding: 16px 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-shrink: 0;

  .total {
    font-family: 'PingFang SC', sans-serif;
    font-weight: 400;
    font-size: 14px;
    color: rgba(0, 0, 0, 0.6);
  }

  :deep(.el-pagination .el-pager) {
    gap: 4px;
  }

  :deep(.el-pagination .is-active) {
    width: 32px;
    height: 32px;
    background: #7288FA;
    border-radius: 3px;
    color: #fff;
  }

  :deep(.el-pagination .el-pager li:not(.is-active)) {
    width: 32px;
    height: 32px;
    border: 1px solid #DCDCDC;
    border-radius: 3px;
    color: rgba(0, 0, 0, 0.6);
  }
}

/* ---- 详情弹窗 ---- */
.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 32px;
}

.dialog-title {
  font-family: 'Poppins', 'PingFang SC', sans-serif;
  font-weight: 500;
  font-size: 20px;
  line-height: 30px;
  color: #000000;
}

.close-btn {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 32px;
  height: 32px;
  background: #FFF2E2;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;

  &:hover {
    background: #FFE8CC;
  }
}

.dialog-footer {
  display: flex;
  align-items: center;
  gap: 9px;
  justify-content: flex-end;
  padding: 12px 24px 16px;
}

.btn-cancel {
  width: 58px;
  height: 32px;
  background: #FFFFFF;
  border: 1px solid #BEC0CA;
  border-radius: 6px;
  box-shadow: 0 2px 0 rgba(0, 0, 0, 0.016);
  color: #53545C;
  font-size: 13px;

  &:hover,
  &:focus {
    background: #F5F7FA;
    border-color: #7288FA;
    color: #7288FA;
  }
}

/* ---- 详情内容 ---- */
.detail-body {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.detail-section {
  &:last-child {
    margin-bottom: 0;
  }
}

.section-title {
  display: flex;
  align-items: center;
  font-weight: 500;
  font-size: 16px;
  color: #303133;
  margin-bottom: 12px;
}

.title-bar {
  width: 4px;
  height: 20px;
  background: #7288FA;
  border-radius: 2px;
  margin-right: 8px;
  flex-shrink: 0;
}

/* ---- Info Table ---- */
.info-table {
  display: grid;
  grid-template-columns: 112px 1fr 112px 1fr;
  border-top: 1px solid #E1E2E9;
  border-left: 1px solid #E1E2E9;
}

.info-label {
  background: #F5F7FA;
  padding: 8px 12px;
  font-size: 14px;
  color: #333333;
  border-right: 1px solid #ECEEF5;
  border-bottom: 1px solid #E1E2E9;
  display: flex;
  align-items: center;
  min-height: 40px;
}

.info-value {
  padding: 8px 12px;
  font-size: 14px;
  color: #333333;
  border-right: 1px solid #E1E2E9;
  border-bottom: 1px solid #E1E2E9;
  display: flex;
  align-items: center;
  min-height: 40px;
}

.info-value--span3 {
  grid-column: span 3;
}

/* ---- 内容详情 ---- */
.content-box {
  background: #F5F7FA;
  border-radius: 6px;
  padding: 16px;
}

.content-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}

.content-summary {
  font-size: 13px;
  color: #606266;
  margin-bottom: 8px;
}

.content-body {
  font-size: 14px;
  color: #303133;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-all;
}

.action-buttons {
  margin-top: 12px;
  display: flex;
  gap: 8px;
}
</style>
