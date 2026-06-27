<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { OutboundOrder } from '@/types/outbound'
import { OUTBOUND_STATUS_OPTIONS, OUTBOUND_TYPE_MAP } from '@/constants/outbound'
import { outboundApi } from '@/api/modules/outbound'
import { OUTBOUND_PERMISSIONS } from '@/constants/permission'

interface Props {
  modelValue: boolean
  orderId?: number | null
}
const props = withDefaults(defineProps<Props>(), { modelValue: false, orderId: null })
const emit  = defineEmits<{ 'update:modelValue': [val: boolean]; refresh: [] }>()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const order   = ref<OutboundOrder | null>(null)
const loading = ref(false)

watch(() => props.modelValue, async (val) => {
  if (val && props.orderId) await loadDetail(props.orderId)
})

const loadDetail = async (id: number) => {
  loading.value = true
  try {
    const res = await outboundApi.getDetail(id)
    if (res.code === 'SUCCESS') order.value = res.data
  } catch {}
  finally { loading.value = false }
}

const previewAttachment = (url: string) => {
  window.open(url, '_blank', 'noopener,noreferrer')
}
const downloadAttachment = async (url: string) => {
  if (order.value?.id) {
    await outboundApi.downloadAttachment(order.value.id, url)
  }
}
const getFileName = (url: string) => {
  try { return decodeURIComponent(url.split('/').pop() || '附件') } catch { return '附件' }
}

const formatTime = (val: string | null | undefined) => {
  if (!val) return '—'
  const d = new Date(val)
  if (isNaN(d.getTime())) return val
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}/${pad(d.getMonth() + 1)}/${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const statusType  = (status: string) =>
  OUTBOUND_STATUS_OPTIONS.find(o => o.value === status)?.type ?? 'info'
const statusLabel = (status: string) =>
  OUTBOUND_STATUS_OPTIONS.find(o => o.value === status)?.label ?? status

// 审批弹窗
const rejectDialogVisible = ref(false)
const rejectReason        = ref('')

const handleApprove = async () => {
  if (!props.orderId) return
  try {
    await ElMessageBox.confirm('确认审核通过该出库单？', '审核确认', { type: 'success' })
    await outboundApi.approve(props.orderId)
    ElMessage.success('审核通过')
    emit('refresh')
    await loadDetail(props.orderId)
  } catch {
  }
}

const handleReject = async () => {
  if (!rejectReason.value.trim()) { ElMessage.warning('请填写驳回原因'); return }
  try {
    await outboundApi.reject(props.orderId!, rejectReason.value)
    ElMessage.success('已驳回')
    rejectDialogVisible.value = false
    rejectReason.value = ''
    emit('refresh')
    await loadDetail(props.orderId!)
  } catch {}
}

const handleExecute = async () => {
  if (!props.orderId) return
  try {
    await ElMessageBox.confirm('确认执行出库？执行后将扣减库存。', '出库确认', { type: 'warning' })
    await outboundApi.execute(props.orderId)
    ElMessage.success('出库成功')
    emit('refresh')
    await loadDetail(props.orderId)
  } catch {
  }
}
</script>

<template>
  <el-drawer v-model="visible" title="出库单详情" size="720px" :close-on-click-modal="false">
    <div v-loading="loading" class="detail-content">
      <template v-if="order">
        <!-- 基础信息 -->
        <el-descriptions :column="3" border class="mb-16">
          <el-descriptions-item label="出库单号">{{ order.outboundNo }}</el-descriptions-item>
          <el-descriptions-item label="出库类型">
            {{ OUTBOUND_TYPE_MAP[order.outboundType] || order.outboundType }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(order.status)" size="small">{{ statusLabel(order.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="领用组织">{{ order.targetOrgName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="出库日期">{{ formatTime(order.completedAt) }}</el-descriptions-item>
          <el-descriptions-item v-if="order.supplierName" label="供应商">{{ order.supplierName }}</el-descriptions-item>
          <el-descriptions-item v-if="order.recipientName" label="领用人">{{ order.recipientName }}</el-descriptions-item>
          <el-descriptions-item label="总数量">{{ order.totalQuantity ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="总金额(元)">
            {{ order.totalAmount ? Number(order.totalAmount).toFixed(2) : '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatTime(order.createdAt) }}</el-descriptions-item>
          <el-descriptions-item v-if="order.submitterName" label="提交人">{{ order.submitterName }}</el-descriptions-item>
          <el-descriptions-item v-if="order.submittedAt" label="提交时间">{{ formatTime(order.submittedAt) }}</el-descriptions-item>
          <el-descriptions-item v-if="order.approverName" label="审核人">{{ order.approverName }}</el-descriptions-item>
          <el-descriptions-item v-if="order.approvedAt" label="审核时间">{{ formatTime(order.approvedAt) }}</el-descriptions-item>
          <el-descriptions-item v-if="order.executorName" label="执行人">{{ order.executorName }}</el-descriptions-item>
          <el-descriptions-item v-if="order.executedAt" label="执行时间">{{ formatTime(order.executedAt) }}</el-descriptions-item>
          <el-descriptions-item v-if="order.status === 'rejected' && order.approveRemark" label="驳回原因" :span="3">
            <span style="color: #f56c6c">{{ order.approveRemark }}</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="order.sourceOrderNo" label="关联单据" :span="3">
            <el-tag type="info">{{ order.sourceOrderNo }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item v-if="order.remark" label="备注" :span="3">{{ order.remark }}</el-descriptions-item>
          <el-descriptions-item v-if="order.attachments?.length" label="附件" :span="3">
            <div v-for="(url, idx) in order.attachments" :key="idx" style="display: flex; align-items: center; gap: 8px; margin-bottom: 4px;">
              <span style="font-size: 13px;">{{ getFileName(url) }}</span>
              <template v-if="url.startsWith('/upload/') || url.startsWith('http://') || url.startsWith('https://')">
                <el-button link type="primary" size="small" @click="previewAttachment(url)">查看</el-button>
                <el-button link type="primary" size="small" @click="downloadAttachment(url)">下载</el-button>
              </template>
              <span v-else style="color: #909399; font-size: 12px;">（历史附件仅保存了文件名）</span>
            </div>
          </el-descriptions-item>
        </el-descriptions>

        <!-- 明细列表 -->
        <div class="section-title">出库明细</div>
        <el-table :data="order.items ?? []" border size="small" stripe>
          <el-table-column type="expand" width="48">
            <template #default="{ row }">
              <div v-if="row.allocations?.length" class="detail-allocation">
                <div class="detail-allocation__title">分配明细</div>
                <el-table :data="row.allocations" size="small" border>
                  <el-table-column prop="warehouseName" label="仓库" min-width="120" />
                  <el-table-column prop="locationName" label="仓位" min-width="110" />
                  <el-table-column prop="batchNo" label="批次号" min-width="120" />
                  <el-table-column prop="productionDate" label="生产日期" width="110" />
                  <el-table-column prop="expiryDate" label="到期日" width="110" />
                  <el-table-column prop="quantity" label="分配数量" width="100" align="right" />
                </el-table>
              </div>
              <div v-else class="detail-allocation__empty">当前明细未使用分配子表</div>
            </template>
          </el-table-column>
          <el-table-column type="index" label="序号" width="55" align="center" />
          <el-table-column prop="materialName" label="物料名称" min-width="120" />
          <el-table-column prop="spec"         label="规格"     width="100" />
          <el-table-column prop="unit"         label="单位"     width="70" align="center" />
          <el-table-column prop="warehouseName" label="出库仓库" min-width="100" />
          <el-table-column prop="locationName"  label="仓位"    width="100" />
          <el-table-column prop="batchNo"       label="批次号"  width="100" />
          <el-table-column prop="quantity"      label="数量"    width="80" align="right" />
          <el-table-column prop="purpose"      label="用途"    min-width="100" />
          <el-table-column label="单价(元)" width="90" align="right">
            <template #default="{ row }">{{ row.unitCost ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="小计(元)" width="100" align="right">
            <template #default="{ row }">
              {{ row.totalCost ? Number(row.totalCost).toFixed(2) : '—' }}
            </template>
          </el-table-column>
        </el-table>

        <!-- 操作按钮 -->
        <div v-if="order.status === 'pending' || order.status === 'approved'" class="action-bar">
          <template v-if="order.status === 'pending'">
            <el-button type="success" v-permission="OUTBOUND_PERMISSIONS.AUDIT" @click="handleApprove">审核通过</el-button>
            <el-button type="danger"  v-permission="OUTBOUND_PERMISSIONS.AUDIT" @click="rejectDialogVisible = true">驳回</el-button>
          </template>
          <template v-if="order.status === 'approved'">
            <el-button type="primary" v-permission="OUTBOUND_PERMISSIONS.EXECUTE" @click="handleExecute">执行出库</el-button>
          </template>
        </div>
      </template>
    </div>
  </el-drawer>

  <!-- 驳回理由弹窗 -->
  <el-dialog v-model="rejectDialogVisible" title="驳回原因" width="400px" append-to-body>
    <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="请填写驳回原因" />
    <template #footer>
      <el-button @click="rejectDialogVisible = false">取消</el-button>
      <el-button type="danger" @click="handleReject">确认驳回</el-button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.detail-content { padding: 0 16px 16px; }
.mb-16 { margin-bottom: 16px; }
.section-title {
  font-size: 14px;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 8px;
}
.action-bar {
  margin-top: 16px;
  display: flex;
  gap: 12px;
}

.detail-allocation {
  padding: 8px 16px;
}

.detail-allocation__title {
  margin-bottom: 8px;
  font-weight: 600;
  color: $text-primary;
}

.detail-allocation__empty {
  padding: 8px 16px;
  color: $text-secondary;
}
</style>
