<script setup lang="ts">
import { ref, watch } from 'vue'
import { deviceApi } from '@/api/modules/device'
import type { DeviceDetail, DeviceStatusLog, DataLog } from '@/types'
import { ONLINE_STATUS_OPTIONS, DEVICE_STATUS_OPTIONS, DEVICE_CONFIG_LABEL_MAP } from '@/constants/device'
import { formatDateTime } from '@/utils'

/** 将未知 camelCase 键转为可读格式（如 subnetMask → Subnet Mask） */
const humanizeKey = (key: string): string => {
  return key.replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/^([a-z])/, (_, c) => c.toUpperCase())
}

const configLabel = (key: string): string => DEVICE_CONFIG_LABEL_MAP[key] || humanizeKey(key)

interface Props {
  visible: boolean
  deviceId: number | null
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'update:visible': [val: boolean]
}>()

const loading = ref(false)
const detail = ref<DeviceDetail | null>(null)
const statusLogs = ref<DeviceStatusLog[]>([])
const activeTab = ref('info')

// 数据日志相关
const dataLogList = ref<DataLog[]>([])
const dataLogTotal = ref(0)
const dataLogLoading = ref(false)
const dataLogLoaded = ref(false)
const dataLogFilter = ref({
  dataType: '',
  dateRange: [] as string[],
})

const onlineStatusMap = Object.fromEntries(ONLINE_STATUS_OPTIONS.map(s => [s.value, s]))
const statusMap = Object.fromEntries(DEVICE_STATUS_OPTIONS.map(s => [s.value, s]))

const DATA_TYPE_OPTIONS = [
  { label: '全部', value: '' },
  { label: '温度', value: 'temperature' },
  { label: '湿度', value: 'humidity' },
  { label: '重量', value: 'weight' },
  { label: '心跳', value: 'heartbeat' },
]

const buildDayRange = (dateRange: string[]) => {
  if (dateRange.length !== 2) return null

  const [startDate, endDate] = dateRange
  return {
    startTime: `${startDate} 00:00:00`,
    endTime: `${endDate} 23:59:59`,
  }
}

const fetchDetail = async () => {
  if (!props.deviceId) return
  loading.value = true
  try {
    const [detailRes, logRes] = await Promise.all([
      deviceApi.getDetail(props.deviceId),
      deviceApi.getStatusLogs(props.deviceId),
    ])
    if (detailRes.code === 'SUCCESS' && detailRes.data) {
      detail.value = detailRes.data
    }
    if (logRes.code === 'SUCCESS' && logRes.data) {
      statusLogs.value = logRes.data
    }
  } finally {
    loading.value = false
  }
}

const fetchDataLogs = async () => {
  if (!props.deviceId) return
  dataLogLoading.value = true
  try {
    const params: Record<string, string | number> = {
      deviceId: props.deviceId,
      pageNum: 1,
      pageSize: 50,
    }
    if (dataLogFilter.value.dataType) {
      params.dataType = dataLogFilter.value.dataType
    }
    if (dataLogFilter.value.dateRange?.length === 2) {
      const range = buildDayRange(dataLogFilter.value.dateRange)
      if (range) {
        params.startTime = range.startTime
        params.endTime = range.endTime
      }
    }
    const res = await deviceApi.getDataLogs(params)
    if (res.code === 'SUCCESS' && res.data) {
      dataLogList.value = res.data.list
      dataLogTotal.value = res.data.total
    }
  } finally {
    dataLogLoading.value = false
  }
}

watch(() => props.visible, (val) => {
  if (val) {
    fetchDetail()
    dataLogList.value = []
    dataLogTotal.value = 0
    dataLogFilter.value = { dataType: '', dateRange: [] }
    dataLogLoaded.value = false
    activeTab.value = 'info'
  }
})

watch(activeTab, (val) => {
  if (val === 'dataLog' && !dataLogLoaded.value && props.deviceId) {
    dataLogLoaded.value = true
    fetchDataLogs()
  }
})

const handleDataLogSearch = () => fetchDataLogs()

const handleDataLogReset = () => {
  dataLogFilter.value = { dataType: '', dateRange: [] }
  fetchDataLogs()
}

const handleClose = () => {
  emit('update:visible', false)
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    title="设备详情"
    width="780px"
    @close="handleClose"
  >
    <div v-loading="loading" v-if="detail">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="基本信息" name="info">
          <!-- 基础信息 -->
          <h4 style="margin: 0 0 12px">基础信息</h4>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="设备名称">{{ detail.deviceName }}</el-descriptions-item>
            <el-descriptions-item label="设备编码">{{ detail.deviceCode }}</el-descriptions-item>
            <el-descriptions-item label="设备类型">{{ detail.deviceTypeName }}</el-descriptions-item>
            <el-descriptions-item label="设备型号">{{ detail.deviceModel || '-' }}</el-descriptions-item>
            <el-descriptions-item label="生产厂商">{{ detail.manufacturer || '-' }}</el-descriptions-item>
            <el-descriptions-item label="序列号">{{ detail.sn || '-' }}</el-descriptions-item>
            <el-descriptions-item label="MAC地址">{{ detail.macAddress || '-' }}</el-descriptions-item>
            <el-descriptions-item label="IP地址">{{ detail.ipAddress || '-' }}</el-descriptions-item>
            <el-descriptions-item label="所属组织">{{ detail.orgName || '-' }}</el-descriptions-item>
          </el-descriptions>

          <!-- 设备状态 -->
          <h4 style="margin: 20px 0 12px">设备状态</h4>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="在线状态">
              <el-tag :type="onlineStatusMap[detail.onlineStatus]?.type ?? 'info'" size="small">
                {{ detail.onlineStatusName }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="设备状态">
              <el-tag :type="statusMap[detail.status]?.type ?? 'info'" size="small">
                {{ detail.statusName }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="最后心跳">{{ formatDateTime(detail.lastHeartbeatAt) || '-' }}</el-descriptions-item>
            <el-descriptions-item label="安装位置">{{ detail.locationDesc || '-' }}</el-descriptions-item>
          </el-descriptions>

          <!-- 负责人与维保 -->
          <h4 style="margin: 20px 0 12px">负责人与维保</h4>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="负责人">{{ detail.managerName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="联系电话">{{ detail.managerPhone || '-' }}</el-descriptions-item>
            <el-descriptions-item label="安装日期">{{ detail.installDate || '-' }}</el-descriptions-item>
            <el-descriptions-item label="保修到期">{{ detail.warrantyExpiresAt || '-' }}</el-descriptions-item>
            <el-descriptions-item label="维保周期">{{ detail.maintenanceCycleDays ? detail.maintenanceCycleDays + '天' : '-' }}</el-descriptions-item>
            <el-descriptions-item label="下次维保">{{ detail.nextMaintenanceAt || '-' }}</el-descriptions-item>
          </el-descriptions>

          <!-- 设备特有配置 -->
          <template v-if="detail.configParams && Object.keys(detail.configParams).length">
            <h4 style="margin: 20px 0 12px">设备特有配置</h4>
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item v-for="(val, key) in detail.configParams" :key="key" :label="configLabel(String(key))">
                <template v-if="typeof val === 'boolean'">{{ val ? '是' : '否' }}</template>
                <template v-else-if="String(key) === 'frameRate'">{{ val }} fps</template>
                <template v-else>{{ val ?? '-' }}</template>
              </el-descriptions-item>
            </el-descriptions>
          </template>

          <!-- 备注 -->
          <template v-if="detail.remark">
            <h4 style="margin: 20px 0 12px">备注</h4>
            <p style="color: #666; margin: 0">{{ detail.remark }}</p>
          </template>

          <template v-if="statusLogs.length > 0">
            <h4 style="margin: 20px 0 12px">状态履历</h4>
            <el-table :data="statusLogs" size="small" border>
              <el-table-column prop="statusTypeName" label="状态类型" width="90" />
              <el-table-column label="变更轨迹" min-width="180">
                <template #default="{ row }">
                  <span>{{ row.fromStatusName || '初始值' }}</span>
                  <span> → </span>
                  <span>{{ row.toStatusName || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="sourceTypeName" label="来源" width="90" />
              <el-table-column prop="reason" label="原因" min-width="180" show-overflow-tooltip />
              <el-table-column prop="operatorName" label="操作人" width="100" />
              <el-table-column label="时间" width="170">
                <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
              </el-table-column>
            </el-table>
          </template>
        </el-tab-pane>

        <el-tab-pane label="数据记录" name="dataLog">
          <!-- 数据筛选 -->
          <div style="display: flex; gap: 10px; margin-bottom: 12px; align-items: center">
            <el-select v-model="dataLogFilter.dataType" placeholder="数据类型" clearable style="width: 120px" size="small">
              <el-option v-for="opt in DATA_TYPE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
            <el-date-picker
              v-model="dataLogFilter.dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              value-format="YYYY-MM-DD"
              size="small"
              :editable="false"
              style="width: 320px"
            />
            <el-button type="primary" size="small" @click="handleDataLogSearch">查询</el-button>
            <el-button size="small" @click="handleDataLogReset">重置</el-button>
          </div>

          <el-table :data="dataLogList" v-loading="dataLogLoading" stripe border size="small" max-height="400">
            <el-table-column label="采集时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.collectedAt) }}</template>
            </el-table-column>
            <el-table-column prop="dataTypeName" label="数据类型" width="100" />
            <el-table-column label="数据值" width="120">
              <template #default="{ row }">{{ row.dataValue ?? '-' }}{{ row.dataUnit ? ' ' + row.dataUnit : '' }}</template>
            </el-table-column>
            <el-table-column prop="deviceCode" label="设备编码" width="130" />
          </el-table>

          <div v-if="dataLogTotal === 0 && !dataLogLoading" style="text-align: center; padding: 20px; color: #909399">
            暂无数据记录
          </div>
          <div v-if="dataLogTotal > 0" style="text-align: right; margin-top: 8px; color: #909399; font-size: 12px">
            共 {{ dataLogTotal }} 条记录
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
    </template>
  </el-dialog>
</template>
