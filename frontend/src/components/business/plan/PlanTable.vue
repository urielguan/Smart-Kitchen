<template>
  <div class="plan-table">
    <el-table
      :data="data"
      v-loading="loading"
      style="width: 100%"
      :max-height="500"
      class="gastronomy-table"
      :row-class-name="getRowClassName"
    >
      <el-table-column label="计划信息" min-width="140">
        <template #default="{ row }">
          <div class="plan-info-cell">
            <div class="plan-code">{{ row.planCode }}</div>
            <div class="plan-date">{{ row.planDate }}</div>
          </div>
        </template>
      </el-table-column>

      <el-table-column prop="mealTypeName" label="餐次" width="80" align="center">
        <template #default="{ row }">
          <div class="meal-badge" :class="row.mealType">
            {{ row.mealDisplayName || row.mealTypeName }}
          </div>
        </template>
      </el-table-column>

      <el-table-column label="就餐信息" width="120" align="center">
        <template #default="{ row }">
          <div class="count-cell">
            <span class="count-item">{{ row.expectedCountDisplay || `${row.expectedCount || 0}人` }}</span>
            <span class="count-divider">|</span>
            <span class="count-item">{{ row.recipeCount || 0 }}菜</span>
          </div>
        </template>
      </el-table-column>

      <el-table-column prop="totalServings" label="总份数" width="80" align="center">
        <template #default="{ row }">
          <span class="servings-text">{{ row.totalServings || 0 }}份</span>
        </template>
      </el-table-column>

      <el-table-column prop="estimatedCost" label="预估成本" width="100" align="right">
        <template #default="{ row }">
          <span class="cost-text">¥{{ (row.estimatedCost || 0).toFixed(2) }}</span>
        </template>
      </el-table-column>

      <el-table-column prop="nutritionPassRate" label="营养达标率" width="100" align="center">
        <template #default="{ row }">
          <div class="nutrition-rate" :class="getNutritionClass(row.nutritionPassRate)">
            <span class="rate-value">{{ (row.nutritionPassRate || 0).toFixed(1) }}%</span>
            <div class="rate-bar">
              <div class="rate-fill" :style="{ width: (row.nutritionPassRate || 0) + '%' }"></div>
            </div>
          </div>
        </template>
      </el-table-column>

      <el-table-column prop="status" label="状态" width="90" align="center">
        <template #default="{ row }">
          <div class="status-badge" :class="row.status">
            {{ RECIPE_PLAN_STATUS_MAP[row.status] || row.status }}
          </div>
        </template>
      </el-table-column>

      <el-table-column label="创建时间" width="110" align="center">
        <template #default="{ row }">
          <span class="create-time">{{ formatDate(row.createdAt) }}</span>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="200" align="center">
        <template #default="{ row }">
          <div class="action-cell">
            <el-tooltip content="详情" placement="top">
              <button class="action-btn detail" @click="$emit('detail', row)">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                  <circle cx="12" cy="12" r="3"/>
                </svg>
              </button>
            </el-tooltip>
            <el-tooltip v-if="row.status === 'draft'" content="编辑" placement="top">
              <button class="action-btn edit" @click="$emit('edit', row)">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7"/>
                  <path d="M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z"/>
                </svg>
              </button>
            </el-tooltip>
            <el-tooltip v-if="row.status === 'draft'" content="提交审核" placement="top">
              <button class="action-btn submit" @click="$emit('submit', row)">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="22" y1="2" x2="11" y2="13"/>
                  <polygon points="22 2 15 22 11 13 2 9 22 2"/>
                </svg>
              </button>
            </el-tooltip>
            <el-tooltip v-if="row.status === 'pending'" content="审核" placement="top">
              <button class="action-btn audit" @click="$emit('audit', row)">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M9 11l3 3L22 4"/>
                  <path d="M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h11"/>
                </svg>
              </button>
            </el-tooltip>
            <el-tooltip v-if="row.status === 'draft'" content="库存校验" placement="top">
              <button class="action-btn validate" @click="$emit('validate', row)">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M21 16V8a2 2 0 00-1-1.73l-7-4a2 2 0 00-2 0l-7 4A2 2 0 003 8v8a2 2 0 001 1.73l7 4a2 2 0 002 0l7-4A2 2 0 0021 16z"/>
                  <polyline points="3.27 6.96 12 12.01 20.73 6.96"/>
                  <line x1="12" y1="22.08" x2="12" y2="12"/>
                </svg>
              </button>
            </el-tooltip>
            <el-tooltip v-if="row.status === 'draft'" content="删除" placement="top">
              <button class="action-btn delete" @click="$emit('delete', row)">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="3,6 5,6 21,6"/>
                  <path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/>
                </svg>
              </button>
            </el-tooltip>
          </div>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import type { RecipePlan } from '@/types/plan'
import { RECIPE_PLAN_STATUS_MAP } from '@/types/plan'

defineProps<{
  data: RecipePlan[]
  loading: boolean
}>()

defineEmits<{
  detail: [row: RecipePlan]
  edit: [row: RecipePlan]
  submit: [row: RecipePlan]
  audit: [row: RecipePlan]
  validate: [row: RecipePlan]
  delete: [row: RecipePlan]
}>()

/** 获取行类名 */
const getRowClassName = ({ rowIndex }: { rowIndex: number }) => {
  return rowIndex % 2 === 0 ? 'even-row' : 'odd-row'
}

/** 格式化日期 */
const formatDate = (dateStr: string) => {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' }) + ' ' +
         date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

/** 获取营养达标率样式类 */
const getNutritionClass = (rate: number) => {
  if (rate >= 80) return 'excellent'
  if (rate >= 60) return 'good'
  return 'needs-improvement'
}
</script>

<style lang="scss" scoped>
@import url('https://fonts.googleapis.com/css2?family=Noto+Serif+SC:wght@400;600;700&display=swap');

$terracotta: #c75b39;
$terracotta-light: #e8a090;
$sage: #7a9e7e;
$sage-dark: #4a7c59;
$golden: #d4a574;
$cream: #faf7f2;
$warm-gray: #8b8178;
$deep-burgundy: #8b4049;

.plan-table {
  background: white;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  border: 1px solid rgba($terracotta-light, 0.15);
}

:deep(.gastronomy-table) {
  --el-table-border-color: rgba($terracotta-light, 0.1);

  .el-table__header-wrapper th {
    font-weight: 600;
    font-size: 13px;
    color: $warm-gray;
    background: transparent !important;
  }

  .el-table__body-wrapper td {
    padding: 12px 0;
  }
}

.plan-info-cell {
  .plan-code {
    font-weight: 600;
    font-size: 14px;
    color: $text-primary;
    margin-bottom: 2px;
  }

  .plan-date {
    font-size: 12px;
    color: $warm-gray;
  }
}

.meal-badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;

  &.breakfast {
    background: rgba($golden, 0.15);
    color: darken($golden, 25%);
  }
  &.lunch {
    background: rgba($terracotta, 0.12);
    color: $terracotta;
  }
  &.dinner {
    background: rgba($deep-burgundy, 0.1);
    color: $deep-burgundy;
  }
}

.count-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: 12px;

  .count-item {
    color: $text-secondary;
  }

  .count-divider {
    color: rgba($warm-gray, 0.3);
  }
}

.servings-text {
  font-weight: 500;
  color: $terracotta;
}

.cost-text {
  font-weight: 600;
  color: $sage-dark;
}

.nutrition-rate {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;

  .rate-value {
    font-size: 13px;
    font-weight: 600;
  }

  .rate-bar {
    width: 60px;
    height: 4px;
    background: rgba($warm-gray, 0.15);
    border-radius: 2px;
    overflow: hidden;

    .rate-fill {
      height: 100%;
      border-radius: 2px;
      transition: width 0.3s ease;
    }
  }

  &.excellent {
    .rate-value { color: $sage-dark; }
    .rate-fill { background: $sage-dark; }
  }
  &.good {
    .rate-value { color: $golden; }
    .rate-fill { background: $golden; }
  }
  &.needs-improvement {
    .rate-value { color: $terracotta; }
    .rate-fill { background: $terracotta; }
  }
}

.status-badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;

  &.draft {
    background: rgba($warm-gray, 0.15);
    color: $warm-gray;
  }
  &.pending {
    background: rgba($golden, 0.15);
    color: darken($golden, 30%);
  }
  &.approved {
    background: rgba($sage, 0.15);
    color: $sage-dark;
  }
  &.rejected {
    background: rgba(#d45a5a, 0.12);
    color: #d45a5a;
  }
  &.cooking {
    background: rgba($terracotta, 0.12);
    color: $terracotta;
  }
  &.completed {
    background: rgba($sage-dark, 0.15);
    color: $sage-dark;
  }
}

.create-time {
  font-size: 12px;
  color: $warm-gray;
}

.action-cell {
  display: flex;
  gap: 4px;
  justify-content: center;

  .action-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 28px;
    height: 28px;
    border: none;
    border-radius: 8px;
    cursor: pointer;
    transition: all 0.2s;
    background: transparent;

    svg {
      width: 16px;
      height: 16px;
    }

    &.detail {
      color: $sage-dark;
      &:hover { background: rgba($sage, 0.12); }
    }
    &.edit {
      color: $terracotta;
      &:hover { background: rgba($terracotta, 0.1); }
    }
    &.submit {
      color: $golden;
      &:hover { background: rgba($golden, 0.12); }
    }
    &.audit {
      color: #409eff;
      &:hover { background: rgba(#409eff, 0.1); }
    }
    &.validate {
      color: $sage;
      &:hover { background: rgba($sage, 0.12); }
    }
    &.delete {
      color: #d45a5a;
      &:hover { background: rgba(#d45a5a, 0.1); }
    }
  }
}
</style>
