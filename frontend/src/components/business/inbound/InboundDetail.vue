<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { InboundOrder } from '@/types/inbound'
import { INBOUND_STATUS_OPTIONS, INBOUND_SOURCE_TYPE_MAP } from '@/constants/inbound'
import { inboundApi } from '@/api/modules/inbound'
import { INBOUND_PERMISSIONS } from '@/constants/permission'

interface AttachmentView {
  name: string
  url: string
  accessible: boolean
}

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

const order   = ref<InboundOrder | null>(null)
const loading = ref(false)

watch(() => props.modelValue, async (val) => {
  if (val && props.orderId) await loadDetail(props.orderId)
})

const loadDetail = async (id: number) => {
  loading.value = true
  try {
    const res = await inboundApi.getDetail(id)
    if (res.code === 'SUCCESS') order.value = res.data
  } catch {}
  finally { loading.value = false }
}

const effectiveStatus = (currentOrder?: InboundOrder | null, fallbackStatus?: string) => {
  if (!currentOrder) return fallbackStatus ?? ''
  if (currentOrder.status === 'completed') return 'completed'
  if (currentOrder.status === 'approved' && normalizedPostStatus(currentOrder.postStatus) === 'posted') {
    return 'completed'
  }
  return currentOrder.status
}
const statusType  = (status: string) =>
  INBOUND_STATUS_OPTIONS.find(o => o.value === status)?.type ?? 'info'
const statusLabel = (status: string) =>
  INBOUND_STATUS_OPTIONS.find(o => o.value === status)?.label ?? status
const normalizedPostStatus = (postStatus?: string) => {
  switch ((postStatus ?? '').trim()) {
    case 'posted':
      return 'posted'
    case 'post_failed':
      return 'post_failed'
    case 'unposted':
    case 'none':
    case '':
      return 'unposted'
    default:
      return postStatus
  }
}
const postStatusType = (postStatus?: string) => {
  switch (normalizedPostStatus(postStatus)) {
    case 'posted':
      return 'success'
    case 'post_failed':
      return 'danger'
    default:
      return 'info'
  }
}
const postStatusLabel = (postStatus?: string) => {
  switch (normalizedPostStatus(postStatus)) {
    case 'posted':
      return '过账成功'
    case 'post_failed':
      return '过账失败'
    case 'unposted':
      return '未过账'
    default:
      return postStatus || '未知状态'
  }
}
const isAttachmentUrl = (value: string) => /^(\/upload\/|https?:\/\/)/.test(value)
const getAttachmentName = (value: string) => decodeURIComponent(value.split('/').pop() || value)
const attachmentList = computed<AttachmentView[]>(() =>
  (order.value?.attachments ?? []).map((attachment) => ({
    name: getAttachmentName(attachment),
    url: attachment,
    accessible: isAttachmentUrl(attachment),
  }))
)

const handlePreviewAttachment = async (attachment: AttachmentView) => {
  if (!props.orderId) return
  if (!attachment.accessible) {
    ElMessage.warning('历史附件仅保存了文件名，请重新上传后再查看')
    return
  }
  try {
    await inboundApi.previewAttachment(props.orderId, attachment.url)
  } catch (error) {
    if (error instanceof Error && error.message === 'PREVIEW_WINDOW_BLOCKED') {
      ElMessage.warning('浏览器拦截了预览窗口，请允许弹窗后重试')
    }
  }
}

const handleDownloadAttachment = async (attachment: AttachmentView) => {
  if (!props.orderId) return
  if (!attachment.accessible) {
    ElMessage.warning('历史附件仅保存了文件名，请重新上传后再下载')
    return
  }
  try {
    await inboundApi.downloadAttachment(props.orderId, attachment.url)
  } catch {}
}

const FORBIDDEN_APPROVE_MESSAGE = '无权审核入库单'
const FORBIDDEN_REJECT_MESSAGE = '无权驳回入库单'
const FORBIDDEN_UNAPPROVE_MESSAGE = '无权反审核入库单'
const FORBIDDEN_RETRY_POST_MESSAGE = '无权重试过账'
const FORBIDDEN_POST_APPROVED_MESSAGE = '无权执行过账'
const REJECT_STATUS_MESSAGE = '只有待审批状态的入库单可以驳回'
const UNAPPROVE_STATUS_MESSAGE = '只有已审核或已入库的入库单可以反审核'
const RETRY_POST_STATUS_MESSAGE = '只有过账失败的已审核入库单可以重试过账'
const POST_APPROVED_STATUS_MESSAGE = '只有已审核且未过账的入库单可以执行过账'

// 审批弹窗
const rejectDialogVisible = ref(false)
const rejectRemark        = ref('')

const handleApprove = async () => {
  if (!props.orderId || order.value?.version == null) return
  try {
    await ElMessageBox.confirm('确认审批通过该入库单？', '审批确认', { type: 'success' })
    await inboundApi.approve(props.orderId, order.value.version)
    ElMessage.success('审批通过')
    emit('refresh')
    await loadDetail(props.orderId)
  } catch (error) {
    if (error instanceof Error && error.message === FORBIDDEN_APPROVE_MESSAGE) {
      ElMessage.error(FORBIDDEN_APPROVE_MESSAGE)
      return
    }
    if (error instanceof Error && error.message) {
      ElMessage.warning(error.message)
    }
  }
}

const handleUnapprove = async () => {
  if (!props.orderId || order.value?.version == null) return
  try {
    await ElMessageBox.confirm('确认反审核该入库单？反审核后会回退已审核状态。', '反审核确认', { type: 'warning' })
    await inboundApi.unapprove(props.orderId, order.value.version)
    ElMessage.success('反审核成功')
    emit('refresh')
    await loadDetail(props.orderId)
  } catch (error) {
    if (error instanceof Error && error.message === FORBIDDEN_UNAPPROVE_MESSAGE) {
      ElMessage.error(FORBIDDEN_UNAPPROVE_MESSAGE)
      return
    }
    if (error instanceof Error && error.message === UNAPPROVE_STATUS_MESSAGE) {
      ElMessage.warning(UNAPPROVE_STATUS_MESSAGE)
      return
    }
    if (error instanceof Error && error.message) {
      ElMessage.warning(error.message)
    }
  }
}

const handleRetryPost = async () => {
  if (!props.orderId || order.value?.version == null) return
  try {
    await ElMessageBox.confirm('确认重试该入库单的库存过账？', '重试过账确认', { type: 'warning' })
    await inboundApi.retryPost(props.orderId, order.value.version)
    ElMessage.success('重试过账成功')
    emit('refresh')
    await loadDetail(props.orderId)
  } catch (error) {
    if (error instanceof Error && error.message === FORBIDDEN_RETRY_POST_MESSAGE) {
      ElMessage.error(FORBIDDEN_RETRY_POST_MESSAGE)
      return
    }
    if (error instanceof Error && error.message === RETRY_POST_STATUS_MESSAGE) {
      ElMessage.warning(RETRY_POST_STATUS_MESSAGE)
      return
    }
    if (error instanceof Error && error.message) {
      ElMessage.warning(error.message)
    }
  }
}

const handlePostApproved = async () => {
  if (!props.orderId || order.value?.version == null) return
  try {
    await ElMessageBox.confirm('确认执行该入库单的库存过账？', '执行过账确认', { type: 'warning' })
    await inboundApi.postApproved(props.orderId, order.value.version)
    ElMessage.success('过账成功')
    emit('refresh')
    await loadDetail(props.orderId)
  } catch (error) {
    if (error instanceof Error && error.message === FORBIDDEN_POST_APPROVED_MESSAGE) {
      ElMessage.error(FORBIDDEN_POST_APPROVED_MESSAGE)
      return
    }
    if (error instanceof Error && error.message === POST_APPROVED_STATUS_MESSAGE) {
      ElMessage.warning(POST_APPROVED_STATUS_MESSAGE)
      return
    }
    if (error instanceof Error && error.message) {
      ElMessage.warning(error.message)
    }
  }
}

const handleReject = async () => {
  if (!rejectRemark.value.trim()) { ElMessage.warning('请填写驳回原因'); return }
  try {
    await inboundApi.reject(props.orderId!, rejectRemark.value)
    ElMessage.success('已驳回')
    rejectDialogVisible.value = false
    rejectRemark.value = ''
    emit('refresh')
    await loadDetail(props.orderId!)
  } catch (error) {
    if (error instanceof Error && error.message === FORBIDDEN_REJECT_MESSAGE) {
      ElMessage.error(FORBIDDEN_REJECT_MESSAGE)
      return
    }
    if (error instanceof Error && error.message === REJECT_STATUS_MESSAGE) {
      ElMessage.warning(REJECT_STATUS_MESSAGE)
      return
    }
    if (error instanceof Error && error.message) {
      ElMessage.warning(error.message)
    }
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="入库单详情" width="860px" :close-on-click-modal="false">
    <div v-loading="loading">
      <template v-if="order">
        <!-- 基础信息 -->
        <el-descriptions :column="3" border class="mb-16">
          <el-descriptions-item label="入库单号">{{ order.inboundNo }}</el-descriptions-item>
          <el-descriptions-item label="入库类型">
            {{ INBOUND_SOURCE_TYPE_MAP[order.sourceType] || order.sourceType }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(effectiveStatus(order))" size="small">{{ statusLabel(effectiveStatus(order)) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="过账状态">
            <el-tag :type="postStatusType(order.postStatus)" size="small">{{ postStatusLabel(order.postStatus) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="入库组织">{{ (order as any).receivingOrgName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="供应商">{{ (order as any).supplierName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="来源单号">{{ (order as any).sourceOrderNo || '—' }}</el-descriptions-item>
          <el-descriptions-item label="总金额(元)">
            {{ order.totalAmount ? Number(order.totalAmount).toFixed(2) : '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ order.createdAt }}</el-descriptions-item>
          <el-descriptions-item v-if="(order as any).submitterName" label="提交人">{{ (order as any).submitterName }}</el-descriptions-item>
          <el-descriptions-item v-if="order.submittedAt" label="提交时间">{{ order.submittedAt }}</el-descriptions-item>
          <el-descriptions-item v-if="order.approvedAt" label="审批时间">{{ order.approvedAt }}</el-descriptions-item>
          <el-descriptions-item v-if="normalizedPostStatus(order.postStatus) === 'post_failed' && order.postErrorMessage" label="过账失败原因" :span="3">
            {{ order.postErrorMessage }}
          </el-descriptions-item>
          <el-descriptions-item v-if="order.approveRemark" label="审批意见" :span="3">
            {{ order.approveRemark }}
          </el-descriptions-item>
          <el-descriptions-item v-if="order.attachments?.length" label="附件" :span="3">
            <div class="attachment-list">
              <div v-for="attachment in attachmentList" :key="attachment.url" class="attachment-item">
                <span class="attachment-name">{{ attachment.name }}</span>
                <el-button
                  link
                  type="primary"
                  :disabled="!attachment.accessible"
                  @click="handlePreviewAttachment(attachment)"
                >查看</el-button>
                <el-button
                  link
                  type="primary"
                  :disabled="!attachment.accessible"
                  @click="handleDownloadAttachment(attachment)"
                >下载</el-button>
                <span v-if="!attachment.accessible" class="attachment-hint">历史附件需重新上传后才能操作</span>
              </div>
            </div>
          </el-descriptions-item>
          <el-descriptions-item v-if="order.remark" label="备注" :span="3">{{ order.remark }}</el-descriptions-item>
        </el-descriptions>

        <!-- 明细列表 -->
        <div class="section-title">入库明细</div>
        <el-table :data="order.items ?? []" border size="small" stripe>
          <el-table-column type="index" label="序号" width="55" align="center" />
          <el-table-column prop="materialName" label="物料名称" min-width="120" />
          <el-table-column prop="spec"         label="规格"     width="100" />
          <el-table-column prop="unit"         label="单位"     width="70" align="center" />
          <el-table-column prop="quantity"     label="数量"     width="90" align="right" />
          <el-table-column label="单价(元)" width="100" align="right">
            <template #default="{ row }">{{ row.unitCost ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="小计(元)" width="100" align="right">
            <template #default="{ row }">
              {{ row.totalCost ? Number(row.totalCost).toFixed(2) : '—' }}
            </template>
          </el-table-column>
          <el-table-column prop="batchNo"        label="批次号"   width="110" />
          <el-table-column prop="productionDate" label="生产日期" width="110" />
          <el-table-column prop="expiryDate"     label="到期日期" width="110" />
          <el-table-column prop="warehouseName"  label="存放仓库" min-width="120" />
          <el-table-column prop="locationName"   label="存放仓位" min-width="100" />
        </el-table>

        <!-- 审批操作 (pending状态) -->
        <div v-if="order.status === 'pending'" class="approve-bar">
          <el-button type="success" v-permission="INBOUND_PERMISSIONS.AUDIT" @click="handleApprove">审批通过</el-button>
          <el-button type="danger"  v-permission="INBOUND_PERMISSIONS.AUDIT" @click="rejectDialogVisible = true">驳回</el-button>
        </div>
        <div v-else-if="order.status === 'approved' && normalizedPostStatus(order.postStatus) === 'unposted'" class="approve-bar">
          <el-button type="warning" v-permission="INBOUND_PERMISSIONS.AUDIT" @click="handlePostApproved">执行过账</el-button>
          <el-button type="warning" v-permission="INBOUND_PERMISSIONS.AUDIT" @click="handleUnapprove">反审核</el-button>
        </div>
        <div v-else-if="effectiveStatus(order) === 'completed'" class="approve-bar">
          <el-button type="warning" v-permission="INBOUND_PERMISSIONS.AUDIT" @click="handleUnapprove">反审核</el-button>
        </div>
        <div v-else-if="order.status === 'approved' && normalizedPostStatus(order.postStatus) === 'post_failed'" class="approve-bar">
          <el-button type="warning" v-permission="INBOUND_PERMISSIONS.AUDIT" @click="handleRetryPost">重试过账</el-button>
          <el-button type="warning" v-permission="INBOUND_PERMISSIONS.AUDIT" @click="handleUnapprove">反审核</el-button>
        </div>
      </template>
    </div>
  </el-dialog>

  <!-- 驳回理由弹窗 -->
  <el-dialog v-model="rejectDialogVisible" title="驳回原因" width="400px" append-to-body>
    <el-input v-model="rejectRemark" type="textarea" :rows="3" placeholder="请填写驳回原因" />
    <template #footer>
      <el-button @click="rejectDialogVisible = false">取消</el-button>
      <el-button type="danger" @click="handleReject">确认驳回</el-button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.mb-16 { margin-bottom: 16px; }
.section-title {
  font-size: 14px;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 8px;
}
.approve-bar {
  margin-top: 16px;
  display: flex;
  gap: 12px;
}
.attachment-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.attachment-item {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.attachment-name {
  color: $text-primary;
}
.attachment-hint {
  font-size: 12px;
  color: $text-secondary;
}
</style>