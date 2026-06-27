<script setup lang="ts">
import type { Material } from '@/types/material'
import { isNearExpiry, getImageUrl } from '@/utils'
import StatusTag from '@/components/common/StatusTag.vue'
import { MATERIAL_PERMISSIONS } from '@/constants/permission'

interface Props {
  data: Material[]
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  loading: false
})

const emit = defineEmits<{
  detail: [material: Material]
  edit: [material: Material]
  delete: [material: Material]
  toggleStatus: [material: Material]
  mapping: [material: Material]
}>()

/** 获取行样式类名 */
const getRowClassName = ({ row }: { row: Material }): string => {
  if (row.stockStatus === 'expired') return 'danger-row'
  if (isNearExpiry(row.expiryDate, row.warningDays)) return 'warning-row'
  return ''
}

/** 格式化库存范围 */
const formatStockRange = (row: Material): string => {
  return `${row.minStock}~${row.maxStock}`
}

/** 格式化当前库存 */
const formatCurrentStock = (row: Material): string => {
  return `${row.currentStock} ${row.unit}`
}

/** 详情 */
const handleDetail = (row: Material) => emit('detail', row)

/** 编辑 */
const handleEdit = (row: Material) => emit('edit', row)

/** 删除 */
const handleDelete = (row: Material) => emit('delete', row)

/** 切换状态 */
const handleToggleStatus = (row: Material) => emit('toggleStatus', row)
const handleMapping = (row: Material) => emit('mapping', row)
</script>

<template>
  <div class="table-container">
    <el-table
      :data="data"
      :loading="loading"
      :row-class-name="getRowClassName"
      :height="340"
      :cell-style="{ verticalAlign: 'middle' }"
      row-key="id"
    >
      <el-table-column type="index" label="序号" width="60" align="center" class-name="index-col" />
      <el-table-column prop="imageUrl" label="图片" width="72">
        <template #default="{ row }">
          <div v-if="row.imageUrl" class="material-image">
            <img :src="getImageUrl(row.imageUrl)" alt="物料图片" />
          </div>
          <span v-else class="material-emoji">📦</span>
        </template>
      </el-table-column>

      <el-table-column prop="materialName" label="物料名称" min-width="96" />

      <el-table-column prop="materialCode" label="物料编码" min-width="96" />

      <el-table-column prop="materialSpec" label="规格" min-width="96" />

      <el-table-column prop="categoryName" label="类别" min-width="96" />

      <el-table-column prop="unit" label="单位" min-width="61" />

      <el-table-column prop="shelfLifeDays" label="保质期(天)" min-width="102" />

      <el-table-column label="当前库存" min-width="96">
        <template #default="{ row }">
          {{ formatCurrentStock(row) }}
        </template>
      </el-table-column>

      <el-table-column label="库存范围" min-width="96">
        <template #default="{ row }">
          {{ formatStockRange(row) }}
        </template>
      </el-table-column>

      <el-table-column prop="stockStatus" label="库存状态" min-width="96">
        <template #default="{ row }">
          <StatusTag :status="row.stockStatus" />
        </template>
      </el-table-column>

      <el-table-column prop="status" label="物料状态" min-width="90">
        <template #default="{ row }">
          <StatusTag :status="row.status" />
        </template>
      </el-table-column>

      <el-table-column label="标准食品映射" min-width="180">
        <template #default="{ row }">
          <div v-if="row.foodItemId" class="food-mapping-cell">
            <strong>{{ row.foodName || '已映射标准食品' }}</strong>
            <span>{{ row.foodCode || `ID ${row.foodItemId}` }}</span>
          </div>
          <span v-else class="food-mapping-empty">未建立标准食品映射</span>
        </template>
      </el-table-column>

      <el-table-column label="营养链路" min-width="120">
        <template #default="{ row }">
          <StatusTag
            :status="row.nutritionSourceType ? 'active' : row.foodItemId ? 'warning' : 'inactive'"
          />
          <span class="nutrition-status-text">
            {{ row.nutritionSourceType ? '已同步' : row.foodItemId ? '待同步' : '待映射' }}
          </span>
        </template>
      </el-table-column>

      <el-table-column label="操作" min-width="270" fixed="right" class-name="action-col">
        <template #default="{ row }">
          <el-button type="primary" link @click="handleDetail(row)">详情</el-button>
          <el-button type="primary" link v-permission="MATERIAL_PERMISSIONS.EDIT" @click="handleEdit(row)">编辑</el-button>
          <el-button type="success" link v-permission="MATERIAL_PERMISSIONS.EDIT" @click="handleMapping(row)">营养映射</el-button>
          <el-button
            :type="row.status === 'active' ? 'warning' : 'success'"
            link
            v-permission="MATERIAL_PERMISSIONS.STATUS"
            @click="handleToggleStatus(row)"
          >{{ row.status === 'active' ? '停用' : '启用' }}</el-button>
          <el-button type="danger" link v-permission="MATERIAL_PERMISSIONS.DELETE" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style lang="scss" scoped>
.table-container {
  background: #FFFFFF;
  padding: 0 16px;

  :deep(.el-table) {
    --el-table-index-cell-vertical-align: middle;
    --el-table-border-color: #E7E7E7;
    --el-table-row-height: 46px;

    .el-table__cell {
      padding-left: 0;
      padding-right: 0;
      font-family: 'PingFang SC', sans-serif;
      font-weight: 400;
      font-size: 14px;
      line-height: 22px;
      color: #000000E5;
    }
  }

  :deep(.el-table__body tr) {
    height: 46px;
    border-bottom: 1px solid #E7E7E7;

    td {
      height: 46px;
    }

    &:nth-child(odd) td {
      background-color: #FFFFFF;
    }

    &:nth-child(even) td {
      background-color: #F5F9FF;
    }
  }

  :deep(.el-table__inner-wrapper::before) {
    display: none;
  }

  :deep(.el-table thead th) {
    font-family: 'PingFang SC', sans-serif;
    font-weight: 400;
    font-size: 14px;
    line-height: 22px;
    color: #00000066;
    background-color: #F5F9FF !important;
    border-bottom: 1px solid #E7E7E7;
  }

  :deep(.el-table thead th:first-child) {
    border-top-left-radius: 0;
  }

  :deep(.el-table thead th:last-child) {
    border-top-right-radius: 0;
  }

  :deep(.index-col) {
    font-family: 'PingFang SC', sans-serif;
    font-weight: 400;
    font-size: 14px;
    line-height: 22px;
    color: #000000E5;
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

  /* 操作列：详情、编辑（primary link）按钮颜色 */
  :deep(.el-button--primary.is-link) {
    color: #5570F1;

    &:hover {
      color: #2E45D6;
    }

    &:focus {
      color: #5570F1;
    }
  }

  /* 操作列：删除（danger link）按钮颜色 */
  :deep(.el-button--danger.is-link) {
    color: #FF7474;

    &:hover {
      color: #FF3D3D;
    }

    &:focus {
      color: #FF7474;
    }
  }

  /* 操作列：启用（success link）、停用（warning link）按钮颜色加深 */
  :deep(.el-button--success.is-link) {
    color: #43C08B;

    &:hover {
      color: #1E9E6B;
    }

    &:focus {
      color: #43C08B;
    }
  }

  :deep(.el-button--warning.is-link) {
    color: #ED8A40;

    &:hover {
      color: #C56318;
    }

    &:focus {
      color: #ED8A40;
    }
  }

  /* 操作列：cell 允许溢出，让按钮 focus 描边完整显示 */
  :deep(.action-col .cell) {
    overflow: visible;
  }

  :deep(.danger-row td) {
    background-color: #fef0f0 !important;
  }

  :deep(.warning-row td) {
    background-color: #fffbe6 !important;
  }
}

.material-emoji {
  font-size: 24px;
}

.material-image {
  width: 32px;
  height: 32px;
  border-radius: 4px;
  overflow: hidden;
  display: flex;
  align-items: center;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.nutrition-status-text {
  margin-left: 6px;
  color: #606266;
  font-size: 13px;
}

.food-mapping-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;

  strong {
    font-size: 13px;
    color: #1f2937;
    font-weight: 600;
  }

  span {
    font-size: 12px;
    color: #667085;
  }
}

.food-mapping-empty {
  font-size: 13px;
  color: #98a2b3;
}
</style>
