<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import type { Supplier } from '@/types/supplier'
import { SUPPLIER_STATUS_MAP } from '@/constants/supplier'
import { SUPPLIER_PERMISSIONS } from '@/constants/permission'

interface Props {
  data: Supplier[]
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  loading: false
})

const emit = defineEmits<{
  detail: [row: Supplier]
  audit: [row: Supplier]
  edit: [row: Supplier]
  enable: [row: Supplier]
  cancel: [row: Supplier]
  delete: [row: Supplier]
}>()

/** 表格容器引用 */
const tableContainerRef = ref<HTMLElement | null>(null)

/** 表格高度 */
const tableHeight = ref<number | undefined>(undefined)

/** ResizeObserver 实例 */
let resizeObserver: ResizeObserver | null = null

/** 计算表格高度 */
const updateTableHeight = () => {
  if (tableContainerRef.value) {
    tableHeight.value = tableContainerRef.value.clientHeight
  }
}

onMounted(() => {
  if (tableContainerRef.value) {
    resizeObserver = new ResizeObserver(updateTableHeight)
    resizeObserver.observe(tableContainerRef.value)
    updateTableHeight()
  }
})

onUnmounted(() => {
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
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
  if (status === 'near_expire') return 'qualification-date--near-expire'
  if (status === 'expired') return 'qualification-date--expired'
  return ''
}

const canEdit = (row: Supplier) => row.status !== 'disabled' && row.status !== 'cancelled'
const canEnable = (row: Supplier) => row.status === 'disabled'
const canCancel = (row: Supplier) => row.status !== 'cancelled'
const canDelete = (row: Supplier) => row.status !== 'cancelled'
</script>

<template>
  <div ref="tableContainerRef" class="table-container">
    <el-table
      :data="data"
      :loading="loading"
      stripe
      :height="tableHeight"
    >
      <el-table-column prop="supplierName" label="供应商名称" min-width="160">
        <template #default="{ row }">
          <strong>{{ row.supplierName }}</strong>
        </template>
      </el-table-column>

      <el-table-column prop="supplierCode" label="供应商编码" min-width="120" />

      <el-table-column prop="unifiedCreditCode" label="统一社会信用代码" min-width="220">
        <template #default="{ row }">
          {{ row.unifiedCreditCode || '—' }}
        </template>
      </el-table-column>

      <el-table-column prop="contactName" label="联系人" min-width="90">
        <template #default="{ row }">
          {{ row.contactName || '—' }}
        </template>
      </el-table-column>

      <el-table-column prop="contactPhone" label="联系电话" min-width="130">
        <template #default="{ row }">
          {{ row.contactPhone || '—' }}
        </template>
      </el-table-column>

      <el-table-column prop="supplierType" label="供应商类型" min-width="120">
        <template #default="{ row }">
          <el-tag v-if="row.supplierType" size="small">{{ row.supplierType }}</el-tag>
          <span v-else>—</span>
        </template>
      </el-table-column>

      <el-table-column label="AI综合评分" min-width="460" align="center">
        <el-table-column prop="creditScore" label="综合评分" min-width="92" align="center">
          <template #default="{ row }">
            <span :style="{ color: scoreColor(row.creditScore), fontWeight: 600 }">
              {{ formatScore(row.creditScore) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="scoreQualification" label="资质完整性" min-width="92" align="center">
          <template #default="{ row }">
            {{ formatScore(row.scoreQualification) }}
          </template>
        </el-table-column>
        <el-table-column prop="scoreQuality" label="历史供货质量" min-width="92" align="center">
          <template #default="{ row }">
            {{ formatScore(row.scoreQuality) }}
          </template>
        </el-table-column>
        <el-table-column prop="scorePrice" label="价格稳定性" min-width="92" align="center">
          <template #default="{ row }">
            {{ formatScore(row.scorePrice) }}
          </template>
        </el-table-column>
        <el-table-column prop="scoreDelivery" label="履约准时率" min-width="92" align="center">
          <template #default="{ row }">
            {{ formatScore(row.scoreDelivery) }}
          </template>
        </el-table-column>
      </el-table-column>

      <el-table-column prop="licenseExpiresAt" label="营业执照到期" min-width="130">
        <template #default="{ row }">
          <div class="qualification-cell">
            <span :class="['qualification-date', getQualificationDateClass(row.licenseExpiryStatus)]">
              {{ row.licenseExpiresAt || '—' }}
            </span>
            <el-tag
              v-if="row.licenseExpiryStatus"
              :type="getQualificationStatusTagType(row.licenseExpiryStatus)"
              size="small"
            >
              {{ getQualificationStatusLabel(row.licenseExpiryStatus) }}
            </el-tag>
          </div>
        </template>
      </el-table-column>

      <el-table-column prop="foodLicenseExpiresAt" label="食品许可证到期" min-width="150">
        <template #default="{ row }">
          <div class="qualification-cell">
            <span :class="['qualification-date', getQualificationDateClass(row.foodLicenseExpiryStatus)]">
              {{ row.foodLicenseExpiresAt || '—' }}
            </span>
            <el-tag
              v-if="row.foodLicenseExpiryStatus"
              :type="getQualificationStatusTagType(row.foodLicenseExpiryStatus)"
              size="small"
            >
              {{ getQualificationStatusLabel(row.foodLicenseExpiryStatus) }}
            </el-tag>
          </div>
        </template>
      </el-table-column>

      <el-table-column prop="status" label="状态" min-width="90">
        <template #default="{ row }">
          <el-tag :type="SUPPLIER_STATUS_MAP[row.status]?.tagType" size="small">
            {{ SUPPLIER_STATUS_MAP[row.status]?.label }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="操作" min-width="300" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="emit('detail', row)">详情</el-button>
          <el-button
            v-if="row.status === 'pending'"
            v-permission="SUPPLIER_PERMISSIONS.APPROVE"
            type="success"
            link
            @click="emit('audit', row)"
          >
            审核
          </el-button>
          <el-button
            v-if="canEdit(row)"
            type="primary"
            link
            v-permission="SUPPLIER_PERMISSIONS.EDIT"
            @click="emit('edit', row)"
          >
            编辑
          </el-button>
          <el-button
            v-if="canEnable(row)"
            type="success"
            link
            v-permission="SUPPLIER_PERMISSIONS.STATUS"
            @click="emit('enable', row)"
          >
            启用
          </el-button>
          <el-button
            v-if="canCancel(row)"
            type="warning"
            link
            v-permission="SUPPLIER_PERMISSIONS.CANCEL"
            @click="emit('cancel', row)"
          >
            注销
          </el-button>
          <el-button
            v-if="canDelete(row)"
            type="danger"
            link
            v-permission="SUPPLIER_PERMISSIONS.DELETE"
            @click="emit('delete', row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style lang="scss" scoped>
.table-container {
  flex: 1;
  min-height: 0;
  background: $bg-white;
  border-radius: $border-radius-large;
  box-shadow: $box-shadow-base;
  overflow: hidden;
}

.qualification-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
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
</style>
