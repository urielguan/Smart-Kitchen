<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { supplierApi } from '@/api/modules/supplier'
import { useSupplierStore } from '@/stores/modules/supplier'
import { SUPPLIER_STATUS_MAP } from '@/constants/supplier'
import type { Supplier, SupplierQualificationFile } from '@/types/supplier'
import { SUPPLIER_PERMISSIONS } from '@/constants/permission'

interface Props {
  modelValue: boolean
  supplierId?: number | null
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
  supplierId: null
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  edit: [supplier: Supplier]
}>()

const supplierStore = useSupplierStore()

/** 弹窗显示状态 */
const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

/** 供应商详情 */
const supplier = ref<Supplier | null>(null)

/** 加载状态 */
const loading = ref(false)

const canEdit = computed(() => {
  return !!supplier.value && supplier.value.status !== 'disabled' && supplier.value.status !== 'cancelled'
})

/** 信用评分颜色 */
const scoreColor = (score: number) => {
  if (score >= 90) return '#67c23a'
  if (score >= 70) return '#e6a23c'
  return '#f56c6c'
}

const formatScore = (score?: number | null) => {
  if (score === null || score === undefined || Number.isNaN(Number(score))) {
    return '—'
  }
  return Number(score).toFixed(1)
}

const riskTagType = (level?: string | null) => {
  if (level === 'high') return 'danger'
  if (level === 'medium') return 'warning'
  return 'success'
}

const riskLabel = (level?: string | null) => {
  if (level === 'high') return '高风险'
  if (level === 'medium') return '中风险'
  return '低风险'
}

const getQualificationStatusLabel = (status?: string | null) => {
  if (status === 'near_expire') return '临期'
  if (status === 'expired') return '已过期'
  if (status === 'valid') return '有效'
  return ''
}

const getQualificationStatusTagType = (status?: string | null) => {
  if (status === 'near_expire') return 'danger'
  if (status === 'expired') return 'warning'
  if (status === 'valid') return 'success'
  return 'info'
}

const getQualificationDateClass = (status?: string | null) => {
  if (status === 'near_expire') return 'qualification-date qualification-date--near-expire'
  if (status === 'expired') return 'qualification-date qualification-date--expired'
  return 'qualification-date'
}

/** 获取供应商详情 */
const fetchDetail = async () => {
  if (!props.supplierId) return

  loading.value = true
  try {
    const detail = await supplierStore.getSupplierDetail(props.supplierId)
    supplier.value = detail || null
  } finally {
    loading.value = false
  }
}

/** 监听弹窗显示，获取数据 */
watch(
  () => props.modelValue,
  async (val) => {
    if (val && props.supplierId) {
      await fetchDetail()
    } else {
      supplier.value = null
    }
  }
)

/** 弹窗打开状态下切换ID时，刷新详情 */
watch(
  () => props.supplierId,
  async (id) => {
    if (visible.value && id) {
      await fetchDetail()
    }
  }
)

/** 点击编辑 */
const handleEdit = () => {
  if (supplier.value) {
    emit('edit', supplier.value)
    visible.value = false
  }
}

/** 关闭弹窗 */
const handleClose = () => {
  visible.value = false
}

/** 查看文件 */
const handlePreviewFile = (file: SupplierQualificationFile) => {
  if (!file.url) {
    ElMessage.warning('该附件暂无可访问地址，请重新上传后再查看')
    return
  }
  window.open(file.url, '_blank', 'noopener')
}

/** 下载文件 */
const handleDownloadFile = async (file: SupplierQualificationFile) => {
  if (!file.url) {
    ElMessage.warning('该附件暂无可访问地址，请重新上传后再下载')
    return
  }
  try {
    await supplierApi.downloadQualificationFile(file.url, file.name)
  } catch {
    // 统一错误提示已由请求拦截器处理
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="🏢 供应商详情"
    width="700px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div v-loading="loading">
      <template v-if="supplier">
        <!-- 基本信息 -->
        <div class="detail-section">
          <div class="detail-section-title">基本信息</div>
          <div class="detail-grid">
            <div class="detail-item">
              <span class="detail-label">供应商名称</span>
              <span class="detail-value">{{ supplier.supplierName }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">供应商编码</span>
              <span class="detail-value">{{ supplier.supplierCode }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">状态</span>
              <span class="detail-value">
                <el-tag :type="SUPPLIER_STATUS_MAP[supplier.status]?.tagType" size="small">
                  {{ SUPPLIER_STATUS_MAP[supplier.status]?.label }}
                </el-tag>
              </span>
            </div>
            <div class="detail-item">
              <span class="detail-label">AI综合评分</span>
              <span
                class="detail-value"
                :style="{ color: scoreColor(supplier.creditScore), fontWeight: 600 }"
              >{{ formatScore(supplier.creditScore) }}</span>
            </div>
            <div class="detail-item span-2">
              <span class="detail-label">供应商类型</span>
              <span class="detail-value">
                <el-tag v-if="supplier.supplierType" size="small">{{ supplier.supplierType }}</el-tag>
                <span v-else>—</span>
              </span>
            </div>
            <div class="detail-item span-2">
              <span class="detail-label">地址</span>
              <span class="detail-value">{{ supplier.address || '—' }}</span>
            </div>
            <div v-if="supplier.disableReason" class="detail-item span-2">
              <span class="detail-label">禁用原因</span>
              <span class="detail-value">{{ supplier.disableReason }}</span>
            </div>
            <div v-if="supplier.cancelReason" class="detail-item span-2">
              <span class="detail-label">注销原因</span>
              <span class="detail-value">{{ supplier.cancelReason }}</span>
            </div>
          </div>
        </div>

        <!-- 联系信息 -->
        <div class="detail-section">
          <div class="detail-section-title">联系信息</div>
          <div class="detail-grid">
            <div class="detail-item">
              <span class="detail-label">联系人</span>
              <span class="detail-value">{{ supplier.contactName || '—' }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">联系电话</span>
              <span class="detail-value">{{ supplier.contactPhone || '—' }}</span>
            </div>
            <div class="detail-item span-2">
              <span class="detail-label">联系邮箱</span>
              <span class="detail-value">{{ supplier.contactEmail || '—' }}</span>
            </div>
          </div>
        </div>

        <!-- 银行信息 -->
        <div class="detail-section">
          <div class="detail-section-title">银行信息</div>
          <div class="detail-grid">
            <div class="detail-item">
              <span class="detail-label">社会信用代码</span>
              <span class="detail-value">{{ supplier.unifiedCreditCode || '—' }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">开户行</span>
              <span class="detail-value">{{ supplier.bankName || '—' }}</span>
            </div>
            <div class="detail-item span-2">
              <span class="detail-label">银行账号</span>
              <span class="detail-value">{{ supplier.bankAccount || '—' }}</span>
            </div>
          </div>
        </div>

        <!-- 资质信息 -->
        <div class="detail-section">
          <div class="detail-section-title">资质信息</div>
          <div class="detail-grid">
            <div class="detail-item">
              <span class="detail-label">营业执照编号</span>
              <span class="detail-value">{{ supplier.licenseNo || '—' }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">营业执照到期</span>
              <div class="detail-value qualification-value">
                <span :class="getQualificationDateClass(supplier.licenseExpiryStatus)">{{ supplier.licenseExpiresAt || '—' }}</span>
                <el-tag
                  v-if="supplier.licenseExpiryStatus"
                  :type="getQualificationStatusTagType(supplier.licenseExpiryStatus)"
                  size="small"
                >
                  {{ getQualificationStatusLabel(supplier.licenseExpiryStatus) }}
                </el-tag>
              </div>
            </div>
            <div class="detail-item">
              <span class="detail-label">食品经营许可证编号</span>
              <span class="detail-value">{{ supplier.foodLicenseNo || '—' }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">食品许可证到期</span>
              <div class="detail-value qualification-value">
                <span :class="getQualificationDateClass(supplier.foodLicenseExpiryStatus)">{{ supplier.foodLicenseExpiresAt || '—' }}</span>
                <el-tag
                  v-if="supplier.foodLicenseExpiryStatus"
                  :type="getQualificationStatusTagType(supplier.foodLicenseExpiryStatus)"
                  size="small"
                >
                  {{ getQualificationStatusLabel(supplier.foodLicenseExpiryStatus) }}
                </el-tag>
              </div>
            </div>
            <div class="detail-item span-2">
              <span class="detail-label">资质文件</span>
              <div class="detail-value file-list-wrap">
                <template v-if="supplier.qualificationFiles?.length">
                  <div
                    v-for="file in supplier.qualificationFiles"
                    :key="file.id"
                    class="qualification-file-item"
                  >
                    <span class="file-name">{{ file.name }}</span>
                    <span class="file-size">{{ file.size }}</span>
                    <el-button type="primary" link @click="handlePreviewFile(file)">查看</el-button>
                    <el-button type="primary" link @click="handleDownloadFile(file)">下载</el-button>
                  </div>
                </template>
                <span v-else>—</span>
              </div>
            </div>
          </div>
        </div>

        <!-- AI综合评分 -->
        <div class="detail-section">
          <div class="detail-section-title">AI 综合评分</div>
          <div class="detail-grid">
            <div class="detail-item">
              <span class="detail-label">综合评分</span>
              <span
                class="detail-value score-main"
                :style="{ color: scoreColor(supplier.creditScore) }"
              >{{ formatScore(supplier.creditScore) }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">资质完整性</span>
              <span class="detail-value">{{ formatScore(supplier.scoreQualification) }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">历史供货质量</span>
              <div class="detail-value score-with-hint">
                <span>{{ formatScore(supplier.scoreQuality) }}</span>
                <el-tag v-if="supplier.scoreQualitySampleInsufficient" size="small" type="info">数据样本不足</el-tag>
              </div>
            </div>
            <div class="detail-item">
              <span class="detail-label">价格稳定性</span>
              <div class="detail-value score-with-hint">
                <span>{{ formatScore(supplier.scorePrice) }}</span>
                <el-tag v-if="supplier.scorePriceSampleInsufficient" size="small" type="info">数据样本不足</el-tag>
              </div>
            </div>
            <div class="detail-item">
              <span class="detail-label">履约准时率</span>
              <div class="detail-value score-with-hint">
                <span>{{ formatScore(supplier.scoreDelivery) }}</span>
                <el-tag v-if="supplier.scoreDeliverySampleInsufficient" size="small" type="info">数据样本不足</el-tag>
              </div>
            </div>
            <div class="detail-item">
              <span class="detail-label">等级评定</span>
              <span class="detail-value">{{ supplier.aiLevel || '—' }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">采购推荐优先级</span>
              <span class="detail-value">{{ supplier.recommendPriority || '—' }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">风险预警</span>
              <span class="detail-value">
                <el-tag :type="riskTagType(supplier.riskWarningLevel)" size="small">
                  {{ riskLabel(supplier.riskWarningLevel) }}
                </el-tag>
              </span>
            </div>
            <div class="detail-item">
              <span class="detail-label">评分更新时间</span>
              <span class="detail-value">{{ supplier.scoreUpdatedAt || '—' }}</span>
            </div>
            <div class="detail-item span-2">
              <span class="detail-label">统计周期说明</span>
              <span class="detail-value">{{ supplier.scoreStatisticsPeriod || '—' }}</span>
            </div>
            <div class="detail-item span-2">
              <span class="detail-label">供应商优化管理建议</span>
              <div class="detail-value suggestion-list">
                <template v-if="supplier.optimizationSuggestions?.length">
                  <div
                    v-for="(suggestion, index) in supplier.optimizationSuggestions"
                    :key="`${index}-${suggestion}`"
                    class="suggestion-item"
                  >
                    {{ suggestion }}
                  </div>
                </template>
                <span v-else>暂无建议</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 时间信息 -->
        <div class="detail-section">
          <div class="detail-section-title">时间信息</div>
          <div class="detail-grid">
            <div class="detail-item">
              <span class="detail-label">创建时间</span>
              <span class="detail-value">{{ supplier.createdAt }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">创建人</span>
              <span class="detail-value">{{ supplier.createdByName || '—' }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">更新时间</span>
              <span class="detail-value">{{ supplier.updatedAt || '—' }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">修改人</span>
              <span class="detail-value">{{ supplier.updatedByName || '—' }}</span>
            </div>
            <div v-if="supplier.auditAt" class="detail-item">
              <span class="detail-label">审核时间</span>
              <span class="detail-value">{{ supplier.auditAt }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">审核人</span>
              <span class="detail-value">{{ supplier.auditByName || '—' }}</span>
            </div>
            <div v-if="supplier.auditRemark" class="detail-item">
              <span class="detail-label">审核备注</span>
              <span class="detail-value">{{ supplier.auditRemark }}</span>
            </div>
          </div>
        </div>
      </template>
    </div>

    <template #footer>
      <el-button v-if="canEdit" type="primary" v-permission="SUPPLIER_PERMISSIONS.EDIT" @click="handleEdit">编辑</el-button>
      <el-button @click="handleClose">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.detail-section {
  margin-bottom: 20px;

  &-title {
    font-size: 14px;
    font-weight: 600;
    color: $text-primary;
    padding-bottom: 8px;
    border-bottom: 1px solid $border-lighter;
    margin-bottom: 12px;
  }
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px 20px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 3px;

  &.span-2 {
    grid-column: span 2;
  }
}

.detail-label {
  font-size: 12px;
  color: $text-secondary;
}

.detail-value {
  font-size: 14px;
  color: $text-primary;
  word-break: break-all;

  &.score-main {
    font-size: 18px;
    font-weight: 600;
  }
}

.score-with-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.qualification-value {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.qualification-date {
  &--near-expire {
    color: #f56c6c;
    font-weight: 600;
  }

  &--expired {
    color: #e6a23c;
    font-weight: 600;
  }
}

.suggestion-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.suggestion-item {
  line-height: 1.5;
  padding: 6px 10px;
  border-radius: $border-radius-base;
  background: $bg-base;
}

.file-list-wrap {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.qualification-file-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border: 1px solid $border-lighter;
  border-radius: $border-radius-base;
  background: $bg-base;

  .file-name {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .file-size {
    color: $text-secondary;
    font-size: 12px;
  }
}
</style>
