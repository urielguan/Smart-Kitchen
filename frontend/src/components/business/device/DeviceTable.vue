<script setup lang="ts">
import { computed, ref } from 'vue'
import { CircleCloseFilled, Document } from '@element-plus/icons-vue'
import type { Device } from '@/types'
import {
  DEVICE_TYPE_MAP,
  DEVICE_ONLINE_STATUS_ROLE_KEYWORDS,
  ONLINE_STATUS_OPTIONS,
  DEVICE_STATUS_OPTIONS,
  getAllowedManualOnlineStatusOptions,
  hasDeviceRoleKeyword,
} from '@/constants/device'
import { DEVICE_PERMISSIONS } from '@/constants/permission'
import { formatDateTime } from '@/utils'
import { useUserStore } from '@/stores/modules/user'
import { useDictCategoryStore } from '@/stores/modules/dict-category'
import { buildDictLabelMap } from '@/utils/dict-category'

interface Props {
  data: Device[]
  loading: boolean
  error?: string | null
}

defineProps<Props>()
const emit = defineEmits<{
  detail: [id: number]
  edit: [id: number]
  delete: [id: number]
  onlineStatus: [row: Device]
  toggleStatus: [id: number]
  selectionChange: [devices: Device[]]
  retry: []
}>()

const userStore = useUserStore()
const tableRef = ref()
const dictCategoryStore = useDictCategoryStore()

const onlineStatusMap = Object.fromEntries(ONLINE_STATUS_OPTIONS.map(s => [s.value, s]))
const statusMap = Object.fromEntries(DEVICE_STATUS_OPTIONS.map(s => [s.value, s]))
const deviceTypeLabelMap = computed(() => buildDictLabelMap(
  dictCategoryStore.getCachedOptions('device_type', true),
  DEVICE_TYPE_MAP
))

const getOnlineTagType = (status: string) => onlineStatusMap[status]?.type ?? 'info'
const getStatusTagType = (status: string) => statusMap[status]?.type ?? 'info'
const formatDeviceType = (row: Device) => {
  return deviceTypeLabelMap.value[row.deviceType] || row.deviceTypeName || row.deviceType || '-'
}
const canManageOnlineStatus = (row: Device) => {
  if (getAllowedManualOnlineStatusOptions(row.onlineStatus).length === 0) return false
  if (userStore.isAdmin()) return true
  return userStore.hasPermission(DEVICE_PERMISSIONS.EDIT)
    && hasDeviceRoleKeyword(userStore.userInfo?.roles, DEVICE_ONLINE_STATUS_ROLE_KEYWORDS)
}

defineExpose({
  clearSelection: () => tableRef.value?.clearSelection()
})
</script>

<template>
  <el-table ref="tableRef" :data="data" v-loading="loading" stripe border style="width: 100%" @selection-change="(selection: Device[]) => emit('selectionChange', selection)">
    <template #empty>
      <div v-if="error" class="table-error-state">
        <el-icon :size="32" color="var(--el-color-danger)"><CircleCloseFilled /></el-icon>
        <p class="table-error-title">加载失败</p>
        <p class="table-error-msg">{{ error }}</p>
        <el-button type="primary" link @click="emit('retry')">重试</el-button>
      </div>
      <div v-else class="table-empty-state">
        <el-icon :size="32" color="var(--el-text-color-placeholder)"><Document /></el-icon>
        <p>暂无设备数据</p>
      </div>
    </template>
    <el-table-column type="selection" width="50" />
    <el-table-column prop="deviceCode" label="设备编号" width="130" />
    <el-table-column prop="deviceName" label="设备名称" min-width="150" show-overflow-tooltip />
    <el-table-column label="设备类型" width="130">
      <template #default="{ row }">
        {{ formatDeviceType(row) }}
      </template>
    </el-table-column>
    <el-table-column prop="locationDesc" label="安装位置" min-width="150" show-overflow-tooltip />
    <el-table-column label="在线状态" width="100" align="center">
      <template #default="{ row }">
        <el-tag :type="getOnlineTagType(row.onlineStatus)" size="small">
          {{ row.onlineStatusName }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="设备状态" width="100" align="center">
      <template #default="{ row }">
        <el-tag :type="getStatusTagType(row.status)" size="small">
          {{ row.statusName }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="managerName" label="负责人" width="100" />
    <el-table-column prop="typeSpecificSummary" label="设备信息" min-width="200" show-overflow-tooltip />
    <el-table-column label="最后心跳" width="170">
      <template #default="{ row }">
        {{ formatDateTime(row.lastHeartbeatAt) || '-' }}
      </template>
    </el-table-column>
    <el-table-column label="操作" width="320" fixed="right">
      <template #default="{ row }">
        <el-button type="primary" link size="small" @click="emit('detail', row.id)">详情</el-button>
        <el-button type="warning" link size="small" v-permission="DEVICE_PERMISSIONS.EDIT" @click="emit('edit', row.id)">编辑</el-button>
        <el-button v-if="canManageOnlineStatus(row)" type="info" link size="small" @click="emit('onlineStatus', row)">在线修正</el-button>
        <el-button
          :type="row.status === 'active' ? 'warning' : 'success'"
          link size="small"
          v-permission="DEVICE_PERMISSIONS.EDIT"
          @click="emit('toggleStatus', row.id)"
        >{{ row.status === 'active' ? '禁用' : '启用' }}</el-button>
        <el-button type="danger" link size="small" v-permission="DEVICE_PERMISSIONS.DELETE" @click="emit('delete', row.id)">删除</el-button>
      </template>
    </el-table-column>
  </el-table>
</template>

<style lang="scss" scoped>
.table-error-state,
.table-empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px 0;
  gap: 8px;
  p { margin: 0; color: var(--el-text-color-secondary); font-size: 14px; }
}
.table-error-title {
  font-weight: 500;
  color: var(--el-color-danger) !important;
}
.table-error-msg {
  font-size: 12px !important;
  color: var(--el-text-color-placeholder) !important;
  max-width: 320px;
  word-break: break-all;
}
</style>
