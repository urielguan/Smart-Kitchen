<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { aiConfigApi } from '@/api/modules/ai-config'
import type { AiRequestLogItem, AiServiceConfigForm, AiServiceConfigItem } from '@/types/ai-config'

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const detailVisible = ref(false)
const logDialogVisible = ref(false)
const currentId = ref<number | null>(null)
const currentDetail = ref<AiServiceConfigItem | null>(null)
const list = ref<AiServiceConfigItem[]>([])
const total = ref(0)
const logs = ref<AiRequestLogItem[]>([])

const searchForm = reactive({
  keyword: '',
  serviceType: '' as '' | 'text' | 'vision',
  status: '' as '' | 'active' | 'inactive',
  pageNum: 1,
  pageSize: 20
})

const form = reactive<AiServiceConfigForm>({
  serviceName: '',
  serviceType: 'text',
  baseUrl: '',
  apiKey: '',
  modelName: '',
  applicableModules: [],
  remark: ''
})

const moduleOptions = [
  { label: '计划营养评估', value: 'nutrition_suggestion' },
  { label: '违规识别', value: 'violation_recognition' }
]

const moduleLabelMap = moduleOptions.reduce<Record<string, string>>((acc, item) => {
  acc[item.value] = item.label
  return acc
}, {})

const getModuleLabel = (moduleCode: string) => moduleLabelMap[moduleCode] || moduleCode

const detailText = (value: unknown) => {
  if (value === null || value === undefined) {
    return '—'
  }
  if (Array.isArray(value)) {
    return value.length ? value.join('、') : '—'
  }
  if (typeof value === 'string') {
    return value.trim() ? value : '—'
  }
  return String(value)
}

const serviceTypeLabel = (value?: 'text' | 'vision') => value === 'vision' ? '视觉模型' : value === 'text' ? '文本模型' : '—'
const statusLabel = (value?: 'active' | 'inactive') => value === 'active' ? '启用' : value === 'inactive' ? '停用' : '—'

const openDetail = async (row: AiServiceConfigItem) => {
  const res = await aiConfigApi.detail(row.id)
  if (res.code === 'SUCCESS' && res.data) {
    currentDetail.value = res.data
    detailVisible.value = true
  }
}

const fetchList = async () => {
  loading.value = true
  try {
    const res = await aiConfigApi.page(searchForm)
    if (res.code === 'SUCCESS' && res.data) {
      list.value = res.data.list
      total.value = res.data.total
    }
  } finally {
    loading.value = false
  }
}

const resetForm = () => {
  currentId.value = null
  Object.assign(form, {
    serviceName: '',
    serviceType: 'text',
    baseUrl: '',
    apiKey: '',
    modelName: '',
    applicableModules: [],
    remark: ''
  })
}

const openCreate = () => {
  resetForm()
  dialogVisible.value = true
}

const openEdit = (row: AiServiceConfigItem) => {
  currentId.value = row.id
  Object.assign(form, {
    serviceName: row.serviceName,
    serviceType: row.serviceType,
    baseUrl: row.baseUrl,
    apiKey: '',
    modelName: row.modelName,
    applicableModules: row.applicableModules,
    remark: row.remark || ''
  })
  dialogVisible.value = true
}

const submit = async () => {
  if (!form.serviceName || !form.baseUrl || !form.modelName || form.applicableModules.length === 0) {
    ElMessage.warning('请完整填写配置')
    return
  }
  if (!currentId.value && !form.apiKey) {
    ElMessage.warning('请输入 API Key')
    return
  }
  saving.value = true
  try {
    const res = currentId.value
      ? await aiConfigApi.update(currentId.value, form)
      : await aiConfigApi.create(form)
    if (res.code === 'SUCCESS') {
      ElMessage.success('保存成功')
      dialogVisible.value = false
      await fetchList()
    }
  } finally {
    saving.value = false
  }
}

const runTest = async (row: AiServiceConfigItem) => {
  const res = await aiConfigApi.test(row.id)
  if (res.code === 'SUCCESS' && res.data) {
    ElMessage[res.data.success ? 'success' : 'error'](res.data.message)
    await fetchList()
  }
}

const toggleStatus = async (row: AiServiceConfigItem) => {
  const nextStatus = row.status === 'active' ? 'inactive' : 'active'
  const res = await aiConfigApi.changeStatus(row.id, nextStatus)
  if (res.code === 'SUCCESS') {
    ElMessage.success(nextStatus === 'active' ? '已启用' : '已停用')
    await fetchList()
  }
}

const remove = async (row: AiServiceConfigItem) => {
  await ElMessageBox.confirm(`确认删除 AI 服务「${row.serviceName}」吗？`, '删除确认', { type: 'warning' })
  const res = await aiConfigApi.remove(row.id)
  if (res.code === 'SUCCESS') {
    ElMessage.success('删除成功')
    await fetchList()
  }
}

const openLogs = async (row: AiServiceConfigItem) => {
  const res = await aiConfigApi.logs(row.id)
  if (res.code === 'SUCCESS' && res.data) {
    logs.value = res.data.list
    logDialogVisible.value = true
  }
}

onMounted(fetchList)
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <el-input v-model="searchForm.keyword" placeholder="服务名称 / 地址" clearable style="width: 240px" />
      <el-select v-model="searchForm.serviceType" placeholder="服务类型" clearable style="width: 140px">
        <el-option label="文本模型" value="text" />
        <el-option label="视觉模型" value="vision" />
      </el-select>
      <el-select v-model="searchForm.status" placeholder="状态" clearable style="width: 120px">
        <el-option label="启用" value="active" />
        <el-option label="停用" value="inactive" />
      </el-select>
      <el-button type="primary" @click="fetchList">查询</el-button>
      <el-button @click="openCreate">新增配置</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border>
      <el-table-column prop="serviceName" label="服务名称" min-width="180" />
      <el-table-column label="服务类型" width="110">
        <template #default="{ row }">{{ row.serviceType === 'text' ? '文本模型' : '视觉模型' }}</template>
      </el-table-column>
      <el-table-column prop="baseUrl" label="接口地址" min-width="220" />
      <el-table-column prop="modelName" label="模型名称" min-width="140" />
      <el-table-column label="适用模块" min-width="180">
        <template #default="{ row }">{{ row.applicableModules.map(getModuleLabel).join('、') }}</template>
      </el-table-column>
      <el-table-column label="测试结果" min-width="220">
        <template #default="{ row }">
          <el-tag :type="row.lastTestStatus === 'success' ? 'success' : row.lastTestStatus === 'failed' ? 'danger' : 'info'">
            {{ row.lastTestStatus || '未测试' }}
          </el-tag>
          <span class="muted">{{ row.lastTestMessage || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'active' ? 'success' : 'info'">{{ row.status === 'active' ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="400" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">查看详情</el-button>
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="primary" @click="runTest(row)">测试连接</el-button>
          <el-button link type="primary" @click="toggleStatus(row)">{{ row.status === 'active' ? '停用' : '启用' }}</el-button>
          <el-button link type="primary" @click="openLogs(row)">日志</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="searchForm.pageNum"
        v-model:page-size="searchForm.pageSize"
        layout="total, prev, pager, next"
        :total="total"
        @current-change="fetchList"
      />
    </div>

    <el-dialog v-model="detailVisible" title="AI 接口配置详情" width="900px">
      <div class="detail-dialog">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="服务名称">{{ detailText(currentDetail?.serviceName) }}</el-descriptions-item>
          <el-descriptions-item label="服务类型">{{ serviceTypeLabel(currentDetail?.serviceType) }}</el-descriptions-item>
          <el-descriptions-item label="接口地址" :span="2">{{ detailText(currentDetail?.baseUrl) }}</el-descriptions-item>
          <el-descriptions-item label="API Key">{{ detailText(currentDetail?.apiKeyMasked) }}</el-descriptions-item>
          <el-descriptions-item label="模型名称">{{ detailText(currentDetail?.modelName) }}</el-descriptions-item>
          <el-descriptions-item label="适用模块" :span="2">
            {{ currentDetail?.applicableModules?.length ? currentDetail.applicableModules.map(getModuleLabel).join('、') : '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusLabel(currentDetail?.status) }}</el-descriptions-item>
          <el-descriptions-item label="最近测试状态">{{ detailText(currentDetail?.lastTestStatus) }}</el-descriptions-item>
          <el-descriptions-item label="最近测试时间">{{ detailText(currentDetail?.lastTestAt) }}</el-descriptions-item>
          <el-descriptions-item label="最近测试结果" :span="2">{{ detailText(currentDetail?.lastTestMessage) }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detailText(currentDetail?.remark) }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detailText(currentDetail?.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ detailText(currentDetail?.updatedAt) }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="dialogVisible" :title="currentId ? '编辑 AI 配置' : '新增 AI 配置'" width="640px">
      <el-form label-width="100px">
        <el-form-item label="服务名称"><el-input v-model="form.serviceName" /></el-form-item>
        <el-form-item label="服务类型">
          <el-select v-model="form.serviceType" style="width: 100%">
            <el-option label="文本模型" value="text" />
            <el-option label="视觉模型" value="vision" />
          </el-select>
        </el-form-item>
        <el-form-item label="接口地址"><el-input v-model="form.baseUrl" /></el-form-item>
        <el-form-item label="API Key"><el-input v-model="form.apiKey" show-password placeholder="编辑时留空表示不修改" /></el-form-item>
        <el-form-item label="模型名称"><el-input v-model="form.modelName" /></el-form-item>
        <el-form-item label="适用模块">
          <el-select v-model="form.applicableModules" multiple style="width: 100%">
            <el-option v-for="item in moduleOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="logDialogVisible" title="AI 调用日志" width="900px">
      <el-table :data="logs" border>
        <el-table-column label="模块" width="160">
          <template #default="{ row }">{{ getModuleLabel(row.moduleCode) }}</template>
        </el-table-column>
        <el-table-column prop="requestType" label="类型" width="100" />
        <el-table-column prop="modelName" label="模型" min-width="120" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="durationMs" label="耗时(ms)" width="100" />
        <el-table-column prop="errorMessage" label="错误信息" min-width="200" />
        <el-table-column prop="createdAt" label="时间" min-width="180" />
      </el-table>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { padding: 16px; }
.toolbar { display: flex; gap: 12px; margin-bottom: 16px; align-items: center; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
.muted { margin-left: 8px; color: #909399; font-size: 12px; }
.detail-dialog { display: flex; flex-direction: column; gap: 16px; }
</style>
