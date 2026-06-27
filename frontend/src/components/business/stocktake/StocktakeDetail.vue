<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { stocktakeApi } from '@/api/modules/stocktake'
import { STOCKTAKE_DIFF_DIRECTION_MAP, STOCKTAKE_DIFF_DIRECTION_TYPE_MAP, STOCKTAKE_STATUS_MAP, STOCKTAKE_STATUS_TYPE_MAP, STOCKTAKE_TYPE_MAP } from '@/constants/stocktake'
import StocktakeSummaryCard from './StocktakeSummaryCard.vue'
import type { StocktakeOrderDetail, StocktakeVersionDetail } from '@/types/stocktake'

interface Props {
  modelValue: boolean
  detail?: StocktakeOrderDetail | null
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
  detail: null,
  loading: false,
})

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const latestVersion = computed(() => props.detail?.versions?.[0])
const versionLoading = ref(false)
const versionDetail = ref<StocktakeVersionDetail | null>(null)

watch(() => props.detail?.id, () => {
  versionDetail.value = null
})

const loadVersionDetail = async (versionNo?: number) => {
  if (!props.detail?.id || !versionNo) return
  versionLoading.value = true
  try {
    const res = await stocktakeApi.getVersionDetail(props.detail.id, versionNo)
    if (res.code === 'SUCCESS' && res.data) {
      versionDetail.value = res.data
    }
  } catch {
  } finally {
    versionLoading.value = false
  }
}

const getAttachmentName = (url: string) => decodeURIComponent(url.split('/').pop() || '附件')

const handleDownloadAttachment = async (url: string) => {
  if (!props.detail?.id) return
  try {
    await stocktakeApi.downloadAttachment(props.detail.id, url)
  } catch {
  }
}

const statusType = (status?: string) => status ? (STOCKTAKE_STATUS_TYPE_MAP[status] || 'info') : 'info'
const stocktakeTypeLabel = (type?: string) => type ? (STOCKTAKE_TYPE_MAP[type] || type) : '—'
const statusLabel = (status?: string) => status ? (STOCKTAKE_STATUS_MAP[status] || status) : '—'
const diffDirectionLabel = (value?: string) => value ? (STOCKTAKE_DIFF_DIRECTION_MAP[value] || value) : '—'
const diffDirectionType = (value?: string) => value ? (STOCKTAKE_DIFF_DIRECTION_TYPE_MAP[value] || 'info') : 'info'
const formatNumber = (value?: number, digits = 2) => {
  if (value === undefined || value === null) return '—'
  return Number(value).toFixed(digits)
}
</script>

<template>
  <el-drawer v-model="visible" title="盘点单详情" size="920px" :close-on-click-modal="false">
    <div v-loading="loading" class="detail-content">
      <template v-if="detail">
        <el-descriptions :column="3" border class="mb-16">
          <el-descriptions-item label="盘点单号">{{ detail.stocktakeNo }}</el-descriptions-item>
          <el-descriptions-item label="盘点日期">{{ detail.stocktakeDate }}</el-descriptions-item>
          <el-descriptions-item label="盘点类型">{{ stocktakeTypeLabel(detail.stocktakeType) }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(detail.status)" size="small">{{ statusLabel(detail.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="盘点仓库">{{ detail.warehouseNames || detail.warehouseName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="盘点仓位">{{ detail.locationNames || detail.locationName || '全部仓位' }}</el-descriptions-item>
          <el-descriptions-item label="盘点人">{{ detail.checkerName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="盘点开始">{{ detail.startAt || '—' }}</el-descriptions-item>
          <el-descriptions-item label="盘点结束">{{ detail.endAt || '—' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detail.createdAt || '—' }}</el-descriptions-item>
          <el-descriptions-item label="审核人">{{ detail.approverName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="审核时间">{{ detail.approvedAt || '—' }}</el-descriptions-item>
          <el-descriptions-item label="审核备注">{{ detail.approveRemark || '—' }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.rejectRemark" label="驳回原因" :span="3">
            <span class="danger-text">{{ detail.rejectRemark }}</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.voidReason" label="作废原因" :span="3">
            <span class="danger-text">{{ detail.voidReason }}</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.remark" label="备注" :span="3">{{ detail.remark }}</el-descriptions-item>
        </el-descriptions>

        <div class="section-title">汇总概览</div>
        <StocktakeSummaryCard
          :item-count="detail.itemCount"
          :diff-qty-total="detail.diffQtyTotal"
          :diff-rate="(detail.diffRate ?? 0) * 100"
          :surplus-amount="detail.surplusAmount"
          :deficit-amount="detail.deficitAmount"
        />

        <div class="section-title mt-16">盘点明细</div>
        <el-table :data="detail.items || []" border size="small" stripe>
          <el-table-column type="index" label="序号" width="55" align="center" />
          <el-table-column prop="materialName" label="物料名称" min-width="140" />
          <el-table-column prop="spec" label="规格" width="120" />
          <el-table-column prop="batchNo" label="批次号" width="120" />
          <el-table-column prop="systemQty" label="系统库存" width="110" align="right" />
          <el-table-column prop="actualQty" label="实际库存" width="110" align="right" />
          <el-table-column prop="diffQty" label="差异数量" width="110" align="right" />
          <el-table-column label="差异方向" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="diffDirectionType(row.diffDirection)" size="small">{{ diffDirectionLabel(row.diffDirection) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="差异金额" width="110" align="right">
            <template #default="{ row }">{{ formatNumber(row.diffAmount) }}</template>
          </el-table-column>
          <el-table-column prop="diffReason" label="差异原因" min-width="140" show-overflow-tooltip />
          <el-table-column prop="recognitionSource" label="识别来源" width="100" />
          <el-table-column label="AI 置信度" width="100" align="right">
            <template #default="{ row }">{{ row.aiConfidence === undefined || row.aiConfidence === null ? '—' : `${Number(row.aiConfidence * 100).toFixed(0)}%` }}</template>
          </el-table-column>
          <el-table-column prop="lineRemark" label="行备注" min-width="140" show-overflow-tooltip />
        </el-table>

        <div class="section-title mt-16">附件</div>
        <div v-if="detail.attachments?.length" class="attachment-list">
          <a v-for="url in detail.attachments" :key="url" href="javascript:void(0)" @click.prevent="handleDownloadAttachment(url)">{{ getAttachmentName(url) }}</a>
        </div>
        <div v-else class="empty-text">暂无附件</div>

        <div class="section-title mt-16">最近一次提交版本</div>
        <div v-if="latestVersion" class="version-card" v-loading="versionLoading">
          <div class="version-header">
            <span>版本 V{{ latestVersion.versionNo }}</span>
            <el-button link type="primary" @click="loadVersionDetail(latestVersion.versionNo)">查看版本详情</el-button>
          </div>
          <div class="version-meta">
            <span>提交人：{{ latestVersion.submitterName || '—' }}</span>
            <span>提交时间：{{ latestVersion.submittedAt || '—' }}</span>
            <span>差异数量：{{ formatNumber(latestVersion.diffQtyTotal, 3) }}</span>
          </div>
          <el-descriptions v-if="versionDetail" :column="2" border class="mt-12">
            <el-descriptions-item label="版本号">V{{ versionDetail.versionNo }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ statusLabel(versionDetail.status) }}</el-descriptions-item>
            <el-descriptions-item label="提交人">{{ versionDetail.submitterName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="提交时间">{{ versionDetail.submittedAt || '—' }}</el-descriptions-item>
          </el-descriptions>
        </div>
        <div v-else class="empty-text">暂无提交版本</div>

        <div class="section-title mt-16">操作日志</div>
        <el-timeline v-if="detail.operationLogs?.length">
          <el-timeline-item v-for="log in detail.operationLogs" :key="log.id" :timestamp="log.createdAt" placement="top">
            <div class="log-title">{{ log.actionName }} - {{ log.operatorName || '系统' }}</div>
            <div class="log-content">{{ log.content || '—' }}</div>
          </el-timeline-item>
        </el-timeline>
        <div v-else class="empty-text">暂无操作日志</div>
      </template>
    </div>
  </el-drawer>
</template>

<style lang="scss" scoped>
.detail-content {
  padding: 0 16px 16px;
}

.mb-16 {
  margin-bottom: 16px;
}

.mt-12 {
  margin-top: 12px;
}

.mt-16 {
  margin-top: 16px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 8px;
}

.danger-text {
  color: #f56c6c;
}

.attachment-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.version-card {
  background: $bg-white;
  border-radius: $border-radius-large;
  padding: 16px;
  box-shadow: $box-shadow-base;
}

.version-header,
.version-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.version-meta {
  margin-top: 8px;
  color: $text-regular;
  font-size: 13px;
}

.log-title {
  font-weight: 600;
  color: $text-primary;
}

.log-content,
.empty-text {
  color: $text-regular;
  font-size: 13px;
}
</style>
