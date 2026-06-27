<script setup lang="ts">
import { computed } from 'vue'
import type { StockStatus } from '@/types/material'

interface Props {
  status: StockStatus | string
}

const props = defineProps<Props>()

/** 状态配置 */
const statusConfig = computed(() => {
  const config: Record<string, { label: string; type: string }> = {
    normal: { label: '正常', type: 'success' },
    low: { label: '库存不足', type: 'warning' },
    high: { label: '库存积压', type: 'primary' },
    expired: { label: '已过期', type: 'danger' },
    active: { label: '启用', type: 'success' },
    inactive: { label: '停用', type: 'danger' },
    pending: { label: '待审核', type: 'warning' },
    completed: { label: '已完成', type: 'success' },
    rejected: { label: '已驳回', type: 'danger' }
  }
  return config[props.status] || { label: props.status, type: 'info' }
})
</script>

<template>
  <el-tag :type="statusConfig.type" size="default">
    {{ statusConfig.label }}
  </el-tag>
</template>