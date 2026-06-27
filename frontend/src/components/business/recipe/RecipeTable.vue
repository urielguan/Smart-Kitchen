<template>
  <div class="recipe-table">
    <el-table
      :data="data"
      v-loading="loading"
      style="width: 100%"
      :max-height="460"
      class="recipe-data-table"
      :row-class-name="getRowClassName"
    >
      <el-table-column type="index" label="序号" :width="DESIGN_WIDTHS.index" align="center" class-name="index-col" :index="indexMethod" />

      <el-table-column label="图片" :width="DESIGN_WIDTHS.image" align="center">
        <template #default="{ row }">
          <div class="recipe-image-cell">
            <div class="image-frame">
              <span v-if="!row.imageUrl" class="placeholder-icon">🍽</span>
              <el-image v-else :src="row.imageUrl" fit="cover" class="recipe-thumb" />
            </div>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="菜谱名称" :min-width="DESIGN_WIDTHS.name">
        <template #default="{ row }">
          <div class="recipe-name-cell">{{ row.menuName || '-' }}</div>
        </template>
      </el-table-column>

      <el-table-column label="菜谱编码" :min-width="DESIGN_WIDTHS.code">
        <template #default="{ row }">
          <div class="text-cell">{{ row.menuCode || '-' }}</div>
        </template>
      </el-table-column>

      <el-table-column label="类别" :min-width="DESIGN_WIDTHS.category">
        <template #default="{ row }">
          <div class="text-cell">{{ row.categoryName || '-' }}</div>
        </template>
      </el-table-column>

      <el-table-column label="烹饪时长" :min-width="DESIGN_WIDTHS.duration">
        <template #default="{ row }">
          <div class="text-cell">{{ row.cookingTime || 0 }}分钟</div>
        </template>
      </el-table-column>

      <el-table-column label="烹饪温度" :min-width="DESIGN_WIDTHS.temperature">
        <template #default="{ row }">
          <div class="text-cell">{{ formatTemperature(row) }}</div>
        </template>
      </el-table-column>

      <el-table-column label="更新时间" :min-width="DESIGN_WIDTHS.updatedAt">
        <template #default="{ row }">
          <div class="text-cell">{{ formatDate(row.updatedAt) }}</div>
        </template>
      </el-table-column>

      <el-table-column label="操作" :width="DESIGN_WIDTHS.actions" align="left" fixed="right">
        <template #default="{ row }">
          <div class="action-cell">
            <button class="action-link" @click="$emit('detail', row)">详情</button>
            <button class="action-link" v-permission="RECIPE_PERMISSIONS.EDIT" @click="$emit('edit', row)">编辑</button>
            <button class="action-link" v-permission="RECIPE_PERMISSIONS.EDIT" :disabled="actionLoadingId !== null" @click="$emit('copy', row)">复制</button>
            <button class="action-link" v-permission="RECIPE_PERMISSIONS.EDIT" :disabled="actionLoadingId !== null" @click="$emit('toggle', row)">
              {{ row.status === 'active' ? '停用' : '启用' }}
            </button>
            <button class="action-link action-link--danger" v-permission="RECIPE_PERMISSIONS.DELETE" :disabled="actionLoadingId !== null" @click="$emit('delete', row)">删除</button>
          </div>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import type { Recipe } from '@/types/recipe'
import { RECIPE_PERMISSIONS } from '@/constants/permission'
import { formatDateTime } from '@/utils'

const props = defineProps<{
  data: Recipe[]
  loading: boolean
  actionLoadingId?: number | null
  pageNum: number
  pageSize: number
}>()

defineEmits<{
  detail: [row: Recipe]
  edit: [row: Recipe]
  copy: [row: Recipe]
  delete: [row: Recipe]
  toggle: [row: Recipe]
}>()

const indexMethod = (index: number) => {
  return (props.pageNum - 1) * props.pageSize + index + 1
}

const DESIGN_WIDTHS = {
  index: 60,
  image: 72,
  name: 160,
  code: 160,
  category: 85,
  duration: 102,
  temperature: 160,
  updatedAt: 163,
  actions: 260
} as const

const getRowClassName = ({ rowIndex }: { rowIndex: number }) => {
  return rowIndex % 2 === 0 ? 'recipe-row--even' : 'recipe-row--odd'
}

const formatDate = (dateStr: string) => {
  return dateStr ? formatDateTime(dateStr) : '-'
}

const formatTemperature = (row: Recipe) => {
  const min = row.cookingTempMin
  const max = row.cookingTempMax
  if (min != null && max != null) {
    return `${min}-${max}℃`
  }
  if (min != null) {
    return `${min}℃`
  }
  if (max != null) {
    return `${max}℃`
  }
  return '-'
}

const formatNumber = (value?: number | null) => {
  if (value === undefined || value === null) return '0'
  return Number(value).toFixed(1).replace(/.0$/, '')
}

const hasNutritionData = (row: Recipe) => {
  const values = [
    row.nutritionInfo?.calories,
    row.nutritionInfo?.protein,
    row.nutritionInfo?.carbohydrate,
    row.nutritionInfo?.fat
  ]
  return values.some(value => value !== null && value !== undefined && Number(value) > 0)
}

const getCompletenessText = (value?: number) => {
  if (value === undefined || value === null) return '完整度待计算'
  return `完整度 ${formatNumber(value)}%`
}

const getNutritionStatus = (row: Recipe) => {
  const missingCount = row.missingMaterialCount == null ? null : Number(row.missingMaterialCount)
  const completeness = row.dataCompleteness == null ? null : Number(row.dataCompleteness)
  const firstMissing = row.missingMaterials?.[0]
  const hasData = hasNutritionData(row)

  if ((missingCount ?? 0) > 0) {
    return {
      label: `缺失${missingCount}个映射`,
      tone: 'warning',
      emptyTitle: '缺少映射物料',
      description: firstMissing ? `缺失映射物料：${firstMissing}` : '存在未完成标准食品映射的食材'
    }
  }

  if (hasData && completeness !== null && completeness >= 100) {
    return {
      label: '营养已联动',
      tone: 'success',
      emptyTitle: '',
      description: row.nutritionScore != null
        ? `${getCompletenessText(completeness)} · 评分 ${formatNumber(row.nutritionScore)}`
        : getCompletenessText(completeness)
    }
  }

  if (hasData && completeness === null) {
    return {
      label: '营养已计算',
      tone: 'success',
      emptyTitle: '',
      description: row.nutritionScore != null
        ? `完整度待回传 · 评分 ${formatNumber(row.nutritionScore)}`
        : '完整度待回传'
    }
  }

  if (hasData || (completeness ?? 0) > 0) {
    return {
      label: '营养待补齐',
      tone: 'pending',
      emptyTitle: '营养结果待完善',
      description: row.nutritionScore != null
        ? `${getCompletenessText(completeness)} · 评分 ${formatNumber(row.nutritionScore)}`
        : getCompletenessText(completeness)
    }
  }

  return {
    label: '待同步营养',
    tone: 'danger',
    emptyTitle: '待补齐营养基础数据',
    description: '当前菜谱食材尚未形成正式营养基础数据'
  }
}
</script>

<style lang="scss" scoped>
.recipe-table {
  width: 100%;
  background: #ffffff;
}

:deep(.recipe-data-table) {
  --el-table-border-color: #e7e7e7;
  --el-table-row-hover-bg-color: #f5f9ff;

  &::before,
  .el-table__inner-wrapper::before {
    display: none;
  }

  .el-table__header-wrapper th {
    box-sizing: border-box;
    height: 46px;
    padding: 12px 16px;
    background: #f5f9ff !important;
    border-top: 1px solid #e7e7e7;
    border-bottom: 1px solid #e7e7e7;
    font-family: 'PingFang SC', sans-serif;
    font-style: normal;
    font-weight: 400;
    font-size: 14px;
    line-height: 22px;
    color: rgba(0, 0, 0, 0.4);

    .cell {
      white-space: nowrap;
      word-break: keep-all;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    &.index-col,
    &:nth-child(2) {
      padding-left: 0;
      padding-right: 0;
    }
  }

  .el-table__body-wrapper td {
    box-sizing: border-box;
    height: 46px;
    padding: 12px 16px;
    border-bottom: 1px solid #e7e7e7;
    background: #ffffff;
    font-family: 'PingFang SC', sans-serif;
    font-style: normal;
    font-weight: 400;
    font-size: 14px;
    line-height: 22px;
    color: rgba(0, 0, 0, 0.9);

    &.index-col {
      padding-left: 0;
      padding-right: 0;
    }
  }

  .recipe-row--odd td {
    background: #fafcff;
  }

  .recipe-row--even td {
    background: #ffffff;
  }
}

.recipe-image-cell {
  display: flex;
  align-items: center;
  justify-content: center;

  .image-frame {
    width: 32px;
    height: 32px;
    border-radius: 4px;
    overflow: hidden;
    background: #eef2f7;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .placeholder-icon {
    font-size: 16px;
    opacity: 0.55;
  }

  .recipe-thumb {
    width: 32px;
    height: 32px;
    object-fit: cover;
  }
}

.recipe-name-cell,
.text-cell {
  width: 100%;
  line-height: 22px;
  color: rgba(0, 0, 0, 0.9);
}

.recipe-name-cell {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.action-cell {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: nowrap;
  overflow-x: auto;
  white-space: nowrap;
}

.action-link {
  padding: 0;
  border: none;
  background: transparent;
  font-family: 'PingFang SC', sans-serif;
  font-style: normal;
  font-weight: 400;
  font-size: 14px;
  line-height: 22px;
  color: #5570f1;
  cursor: pointer;

  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }

  &--danger {
    color: #ff7474;
  }
}
</style>
