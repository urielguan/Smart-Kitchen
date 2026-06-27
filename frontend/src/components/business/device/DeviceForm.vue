<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { deviceApi } from '@/api/modules/device'
import type { DeviceForm as DeviceFormType } from '@/types'
import {
  DEVICE_BUSINESS_STATUS_ROLE_KEYWORDS,
  DEVICE_STATUS_OPTIONS,
  DEVICE_TYPE_MAP,
  getAllowedBusinessStatusOptions,
  hasDeviceRoleKeyword,
} from '@/constants/device'
import { useDictCategoryStore } from '@/stores/modules/dict-category'
import { buildActiveDictOptions } from '@/utils/dict-category'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/modules/user'
import { DEVICE_PERMISSIONS } from '@/constants/permission'
import { warehouseApi } from '@/api/modules/warehouse'
import { useOrgStore } from '@/stores/modules/org'

/** 各设备类型的配置项定义 */
const DEVICE_CONFIG_FIELDS: Record<string, { key: string; label: string; placeholder: string }[]> = {
  camera: [
    { key: 'rtspUrl', label: 'RTSP地址', placeholder: '如：rtsp://192.168.1.10:554/stream1' },
    { key: 'area', label: '监控区域', placeholder: '如：后厨入口' },
    { key: 'aiAlgorithm', label: 'AI算法', placeholder: '如：行为识别' },
    { key: 'resolution', label: '分辨率', placeholder: '如：1280x720' },
    { key: 'frameRate', label: '帧率(fps)', placeholder: '如：15' },
    { key: 'ptzSupport', label: '云台支持', placeholder: '如：true/false' },
    { key: 'recordingEnabled', label: '录像启用', placeholder: '如：true/false' },
    { key: 'gateway', label: '网关地址', placeholder: '如：192.168.1.1' },
    { key: 'subnetMask', label: '子网掩码', placeholder: '如：255.255.255.0' },
  ],
  sensor: [
    { key: 'location', label: '监测位置', placeholder: '如：冷库' },
    { key: 'collectFrequency', label: '采集频率', placeholder: '如：每5分钟' },
  ],
  scale: [
    { key: 'detectCategory', label: '检测品类', placeholder: '如：食材' },
    { key: 'calibrationTime', label: '校准时间', placeholder: '如：2026-01-01' },
  ],
  gas_detector: [
    { key: 'gasType', label: '监测气体', placeholder: '如：一氧化碳' },
    { key: 'alertThreshold', label: '告警阈值', placeholder: '如：50ppm' },
  ],
  sample_terminal: [
    { key: 'cookingArea', label: '烹饪区域', placeholder: '如：1号操作台' },
    { key: 'accuracy', label: '称重精度(%)', placeholder: '如：0.1' },
  ],
  health_terminal: [
    { key: 'location', label: '安装位置', placeholder: '如：食堂入口' },
    { key: 'tempThreshold', label: '体温阈值(℃)', placeholder: '如：37.3' },
  ],
}

interface Props {
  visible: boolean
  deviceId: number | null
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'update:visible': [val: boolean]
  success: []
}>()
const dictCategoryStore = useDictCategoryStore()
const userStore = useUserStore()
const orgStore = useOrgStore()

/** 输入长度提示 */
const fieldTip = ref('')
let fieldTipTimer: ReturnType<typeof setTimeout> | null = null
const showFieldTip = (field: string) => {
  if (fieldTipTimer) clearTimeout(fieldTipTimer)
  fieldTip.value = ''
  requestAnimationFrame(() => { fieldTip.value = field })
  fieldTipTimer = setTimeout(() => { fieldTip.value = '' }, 2000)
}
const handleFieldInput = (field: string, value: string) => {
  if (value.length >= 30) showFieldTip(field)
  else if (fieldTip.value === field) fieldTip.value = ''
}
const handleFieldKeydown = (field: string, value: string, e: KeyboardEvent) => {
  if (value.length >= 30 && e.key.length === 1 && !e.ctrlKey && !e.metaKey) {
    showFieldTip(field)
  }
}

const formRef = ref()
const loading = ref(false)
const originalStatus = ref<string>('active')

/** 传感器-仓位绑定 */
const boundLocationId = ref<number | null>(null)
const locations = ref<{ id: number; locationCode: string; locationName: string; warehouseName: string }[]>([])

const form = ref<DeviceFormType>({
  deviceCode: '',
  deviceName: '',
  deviceType: '',
  status: 'active',
  orgId: userStore.userInfo?.orgId || 1,
})

/** 设备类型对应的配置项值 */
const configFields = ref<Record<string, string>>({})

const deviceTypeOptions = computed(() => buildActiveDictOptions(
  dictCategoryStore.getCachedOptions('device_type'),
  isEdit() ? form.value.deviceType : undefined,
  dictCategoryStore.getCachedOptions('device_type', true),
  DEVICE_TYPE_MAP[form.value.deviceType] || form.value.deviceType
))

const currentConfigDefs = computed(() => {
  if (!form.value.deviceType) return []
  return DEVICE_CONFIG_FIELDS[form.value.deviceType] || []
})

const canManageBusinessStatus = computed(() => {
  if (userStore.isAdmin()) return true
  const requiredPermission = isEdit() ? DEVICE_PERMISSIONS.EDIT : DEVICE_PERMISSIONS.CREATE
  return userStore.hasPermission(requiredPermission)
    && hasDeviceRoleKeyword(userStore.userInfo?.roles, DEVICE_BUSINESS_STATUS_ROLE_KEYWORDS)
})

const businessStatusOptions = computed(() => {
  const selectedStatus = form.value.status || 'active'
  const baseOptions = isEdit()
    ? getAllowedBusinessStatusOptions(originalStatus.value || selectedStatus)
    : DEVICE_STATUS_OPTIONS
  if (canManageBusinessStatus.value) {
    return baseOptions
  }
  return DEVICE_STATUS_OPTIONS.filter(option => option.value === selectedStatus)
})

const shouldShowStatusChangeReason = computed(() =>
  isEdit()
  && canManageBusinessStatus.value
  && !!originalStatus.value
  && form.value.status !== originalStatus.value
)

const rules = {
  deviceCode: [{ required: true, message: '请输入设备编码', trigger: 'blur' }],
  deviceName: [{ required: true, message: '请输入设备名称', trigger: 'blur' }],
  deviceType: [{ required: true, message: '请选择设备类型', trigger: 'change' }],
  status: [{ required: true, message: '请选择设备状态', trigger: 'change' }],
  orgId: [{ required: true, message: '请选择所属组织', trigger: 'change' }],
  statusChangeReason: [{
    validator: (_rule: unknown, value: string | undefined, callback: (error?: Error) => void) => {
      if (shouldShowStatusChangeReason.value && !value?.trim()) {
        callback(new Error('请输入设备状态变更原因'))
        return
      }
      callback()
    },
    trigger: 'blur'
  }],
}

const isEdit = () => props.deviceId !== null

/** 编辑白名单：根据设备状态控制字段是否可编辑 (AC-DEV-81~88) */
const isFieldEditable = (field: string): boolean => {
  if (!isEdit()) return true // 新增时全部可编辑
  const status = originalStatus.value
  if (status === 'active') return true // 启用状态：全部可编辑
  if (status === 'inactive') {
    // 停用状态：仅允许状态切换和备注
    return field === 'status' || field === 'statusChangeReason' || field === 'remark'
  }
  if (status === 'maintenance') {
    // 维护中：仅允许备注
    return field === 'remark'
  }
  return true
}

/** 编辑白名单提示 */
const readonlyTooltip = (field: string): string => {
  const status = originalStatus.value
  if (status === 'inactive') return '停用设备仅允许修改状态和备注'
  if (status === 'maintenance') return '维护中设备仅允许修改备注'
  return ''
}

const resetForm = () => {
  form.value = {
    deviceCode: '',
    deviceName: '',
    deviceType: '',
    status: 'active',
    statusChangeReason: undefined,
    orgId: userStore.userInfo?.orgId || 1,
  }
  originalStatus.value = 'active'
  configFields.value = {}
  boundLocationId.value = null
  locations.value = []
  formRef.value?.clearValidate()
}

/** 用户手动切换设备类型时清空配置项 */
const handleDeviceTypeChange = () => {
  configFields.value = {}
  boundLocationId.value = null
  if (form.value.deviceType === 'sensor') {
    loadLocations()
  }
}

watch(() => props.visible, async (val) => {
  if (val) {
    await dictCategoryStore.fetchOptions('device_type', false, true)
    orgStore.fetchAllTree()
    if (isEdit()) {
      fetchDetail()
    } else {
      resetForm()
    }
  }
})

/** 加载所有仓位（用于传感器绑定下拉） */
const loadLocations = async () => {
  try {
    const res = await warehouseApi.getAllLocationsForBinding()
    if (res.code === 'SUCCESS' && res.data) {
      locations.value = res.data
    }
  } catch { /* non-critical */ }
}

/** 加载当前传感器的仓位绑定 */
const loadCurrentBinding = async () => {
  if (!props.deviceId) return
  try {
    const res = await warehouseApi.getLocationBySensor(props.deviceId)
    if (res.code === 'SUCCESS' && res.data) {
      boundLocationId.value = res.data.id
    } else {
      boundLocationId.value = null
    }
  } catch {
    boundLocationId.value = null
  }
}

watch(() => form.value.status, (value) => {
  if (!isEdit() || value !== originalStatus.value) {
    return
  }
  form.value.statusChangeReason = undefined
})

const fetchDetail = async () => {
  if (!props.deviceId) return
  loading.value = true
  try {
    const res = await deviceApi.getDetail(props.deviceId)
    if (res.code === 'SUCCESS' && res.data) {
      const d = res.data
      form.value = {
        deviceCode: d.deviceCode,
        deviceName: d.deviceName,
        deviceType: d.deviceType,
        status: d.status || 'active',
        statusChangeReason: undefined,
        deviceModel: d.deviceModel || undefined,
        manufacturer: d.manufacturer || undefined,
        sn: d.sn || undefined,
        macAddress: d.macAddress || undefined,
        ipAddress: d.ipAddress || undefined,
        locationDesc: d.locationDesc || undefined,
        installDate: d.installDate || undefined,
        warrantyExpiresAt: d.warrantyExpiresAt || undefined,
        maintenanceCycleDays: d.maintenanceCycleDays || undefined,
        nextMaintenanceAt: d.nextMaintenanceAt || undefined,
        managerName: d.managerName || undefined,
        managerPhone: d.managerPhone || undefined,
        orgId: d.orgId,
        remark: d.remark || undefined,
      }
      originalStatus.value = d.status || 'active'
      // 解析 configParams 到配置项表单
      if (d.configParams && typeof d.configParams === 'object') {
        configFields.value = { ...d.configParams }
      } else {
        configFields.value = {}
      }
      // 传感器设备：加载仓位列表和当前绑定
      if (d.deviceType === 'sensor') {
        loadLocations()
        loadCurrentBinding()
      }
    }
  } finally {
    loading.value = false
  }
}

/** 将配置项组装为 JSON 字符串 */
const buildConfigParams = (): string | undefined => {
  const entries = Object.entries(configFields.value).filter(([, v]) => v !== '' && v != null)
  if (entries.length === 0) return undefined
  return JSON.stringify(Object.fromEntries(entries))
}

const handleSubmit = async () => {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  loading.value = true
  try {
    const payload = { ...form.value, configParams: buildConfigParams() }
    const res = isEdit()
      ? await deviceApi.update(props.deviceId!, payload)
      : await deviceApi.create(payload)
    if (res.code === 'SUCCESS') {
      // 传感器设备：保存绑定关系
      if (form.value.deviceType === 'sensor') {
        const deviceId = isEdit() ? props.deviceId! : (res.data as number)
        if (deviceId) {
          try {
            await warehouseApi.bindSensorToDevice(deviceId, boundLocationId.value)
          } catch (e) {
            console.warn('传感器绑定失败', e)
          }
        }
      }
      ElMessage.success(isEdit() ? '编辑成功' : '新增成功')
      emit('update:visible', false)
      emit('success')
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (e: any) {
    // 版本冲突（乐观锁）：提示用户并自动刷新表单数据
    if (e?.message && (e.message.includes('已被其他用户修改') || e.message.includes('冲突'))) {
      ElMessage.warning('该设备已被其他用户修改，正在刷新最新数据...')
      if (isEdit()) {
        await fetchDetail()
      }
    } else {
      ElMessage.error(e?.message || '操作失败')
    }
  } finally {
    loading.value = false
  }
}

const handleClose = () => {
  emit('update:visible', false)
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="isEdit() ? '编辑设备' : '新增设备'"
    width="680px"
    destroy-on-close
    @close="handleClose"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" v-loading="loading">
      <el-alert
        v-if="isEdit() && originalStatus === 'inactive'"
        title="设备已停用，仅可修改状态和备注"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />
      <el-alert
        v-if="isEdit() && originalStatus === 'maintenance'"
        title="设备维护中，仅可修改备注"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="设备编码" prop="deviceCode">
            <el-input v-model="form.deviceCode" placeholder="请输入设备编码" :disabled="isEdit()" maxlength="30" @input="(v) => handleFieldInput('deviceCode', v)" @keydown="(e) => handleFieldKeydown('deviceCode', form.deviceCode, e)" />
            <transition name="el-fade-in"><span v-if="fieldTip === 'deviceCode'" class="field-tip">设备编码最多输入30个字符</span></transition>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="设备名称" prop="deviceName">
            <el-input v-model="form.deviceName" placeholder="请输入设备名称" maxlength="30" :disabled="isEdit() && !isFieldEditable('deviceName')" @input="(v) => handleFieldInput('deviceName', v)" @keydown="(e) => handleFieldKeydown('deviceName', form.deviceName, e)" />
            <transition name="el-fade-in"><span v-if="fieldTip === 'deviceName'" class="field-tip">设备名称最多输入30个字符</span></transition>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="设备类型" prop="deviceType">
            <el-tooltip v-if="isEdit()" content="设备类型不可修改" placement="top">
              <span style="width:100%;display:inline-block">
                <el-select v-model="form.deviceType" placeholder="请选择设备类型" style="width:100%" :disabled="isEdit()" @change="handleDeviceTypeChange">
                  <el-option v-for="t in deviceTypeOptions" :key="t.value" :label="t.label" :value="t.value" />
                </el-select>
              </span>
            </el-tooltip>
            <el-select v-else v-model="form.deviceType" placeholder="请选择设备类型" style="width:100%" @change="handleDeviceTypeChange">
              <el-option v-for="t in deviceTypeOptions" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="设备状态" prop="status">
            <el-select v-model="form.status" placeholder="请选择设备状态" style="width:100%" :disabled="!canManageBusinessStatus">
              <el-option v-for="status in businessStatusOptions" :key="status.value" :label="status.label" :value="status.value" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="设备型号">
            <el-input v-model="form.deviceModel" placeholder="请输入设备型号" maxlength="30" :disabled="!isFieldEditable('deviceModel')" @input="(v) => handleFieldInput('deviceModel', v)" @keydown="(e) => handleFieldKeydown('deviceModel', form.deviceModel, e)" />
            <transition name="el-fade-in"><span v-if="fieldTip === 'deviceModel'" class="field-tip">设备型号最多输入30个字符</span></transition>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="生产厂商">
            <el-input v-model="form.manufacturer" placeholder="请输入生产厂商" maxlength="30" :disabled="!isFieldEditable('manufacturer')" @input="(v) => handleFieldInput('manufacturer', v)" @keydown="(e) => handleFieldKeydown('manufacturer', form.manufacturer, e)" />
            <transition name="el-fade-in"><span v-if="fieldTip === 'manufacturer'" class="field-tip">生产厂商最多输入30个字符</span></transition>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row v-if="shouldShowStatusChangeReason" :gutter="16">
        <el-col :span="12">
          <el-form-item label="状态原因" prop="statusChangeReason">
            <el-input
              v-model="form.statusChangeReason"
              type="textarea"
              :rows="2"
              maxlength="100"
              show-word-limit
              placeholder="请输入本次设备状态变更原因"
            />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="序列号">
            <el-input v-model="form.sn" placeholder="请输入设备序列号" maxlength="30" :disabled="!isFieldEditable('sn')" @input="(v) => handleFieldInput('sn', v)" @keydown="(e) => handleFieldKeydown('sn', form.sn, e)" />
            <transition name="el-fade-in"><span v-if="fieldTip === 'sn'" class="field-tip">序列号最多输入30个字符</span></transition>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="所属组织" prop="orgId">
            <el-tree-select
              v-model="form.orgId"
              :data="orgStore.allTreeData"
              :props="{ label: 'orgName', value: 'id', children: 'children' }"
              placeholder="请选择所属组织"
              check-strictly
              filterable
              style="width:100%"
            />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="MAC地址">
            <el-input v-model="form.macAddress" placeholder="请输入MAC地址" maxlength="30" :disabled="!isFieldEditable('macAddress')" @input="(v) => handleFieldInput('macAddress', v)" @keydown="(e) => handleFieldKeydown('macAddress', form.macAddress, e)" />
            <transition name="el-fade-in"><span v-if="fieldTip === 'macAddress'" class="field-tip">MAC地址最多输入30个字符</span></transition>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="IP地址">
            <el-input v-model="form.ipAddress" placeholder="请输入IP地址" maxlength="30" :disabled="!isFieldEditable('ipAddress')" @input="(v) => handleFieldInput('ipAddress', v)" @keydown="(e) => handleFieldKeydown('ipAddress', form.ipAddress, e)" />
            <transition name="el-fade-in"><span v-if="fieldTip === 'ipAddress'" class="field-tip">IP地址最多输入30个字符</span></transition>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="安装位置">
            <el-input v-model="form.locationDesc" placeholder="请输入安装位置" maxlength="30" :disabled="!isFieldEditable('locationDesc')" @input="(v) => handleFieldInput('locationDesc', v)" @keydown="(e) => handleFieldKeydown('locationDesc', form.locationDesc, e)" />
            <transition name="el-fade-in"><span v-if="fieldTip === 'locationDesc'" class="field-tip">安装位置最多输入30个字符</span></transition>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="负责人">
            <el-input v-model="form.managerName" placeholder="请输入负责人姓名" maxlength="30" :disabled="!isFieldEditable('managerName')" @input="(v) => handleFieldInput('managerName', v)" @keydown="(e) => handleFieldKeydown('managerName', form.managerName, e)" />
            <transition name="el-fade-in"><span v-if="fieldTip === 'managerName'" class="field-tip">负责人最多输入30个字符</span></transition>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="联系电话">
            <el-input v-model="form.managerPhone" placeholder="请输入联系电话" maxlength="20" :disabled="!isFieldEditable('managerPhone')" @input="(v) => handleFieldInput('managerPhone', v)" @keydown="(e) => handleFieldKeydown('managerPhone', form.managerPhone, e)" />
            <transition name="el-fade-in"><span v-if="fieldTip === 'managerPhone'" class="field-tip">联系电话最多输入20个字符</span></transition>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="维保周期(天)">
            <el-input-number v-model="form.maintenanceCycleDays" :min="0" style="width:100%" :disabled="!isFieldEditable('maintenanceCycleDays')" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="安装日期">
            <el-date-picker v-model="form.installDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择安装日期" :editable="false" style="width:100%" :disabled="!isFieldEditable('installDate')" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="保修到期">
            <el-date-picker v-model="form.warrantyExpiresAt" type="date" value-format="YYYY-MM-DD" placeholder="请选择保修到期日期" :editable="false" style="width:100%" :disabled="!isFieldEditable('warrantyExpiresAt')" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="下次维保">
            <el-date-picker v-model="form.nextMaintenanceAt" type="date" value-format="YYYY-MM-DD" placeholder="请选择下次维保日期" :editable="false" style="width:100%" :disabled="!isFieldEditable('nextMaintenanceAt')" />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 设备类型特有配置 -->
      <template v-if="currentConfigDefs.length > 0">
        <el-divider content-position="left">设备特有配置</el-divider>
        <el-row :gutter="16">
          <el-col v-for="field in currentConfigDefs" :key="field.key" :span="12">
            <el-form-item :label="field.label">
              <el-input v-model="configFields[field.key]" :placeholder="field.placeholder" maxlength="30" @input="(v) => handleFieldInput('config_' + field.key, v)" @keydown="(e) => handleFieldKeydown('config_' + field.key, configFields[field.key], e)" />
              <transition name="el-fade-in"><span v-if="fieldTip === 'config_' + field.key" class="field-tip">{{ field.label }}最多输入30个字符</span></transition>
            </el-form-item>
          </el-col>
        </el-row>
      </template>

      <!-- 传感器-仓位绑定 -->
      <template v-if="form.deviceType === 'sensor'">
        <el-divider content-position="left">仓位绑定</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="绑定仓位">
              <el-select v-model="boundLocationId" placeholder="选择绑定的仓位" clearable filterable style="width:100%">
                <el-option v-for="loc in locations" :key="loc.id"
                  :label="`${loc.locationName} (${loc.locationCode})${loc.warehouseName ? ' - ' + loc.warehouseName : ''}`"
                  :value="loc.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
      </template>

      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入备注" maxlength="300" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.field-tip {
  display: inline-block;
  margin-top: 4px;
  font-size: 12px;
  color: #e6a23c;
}
</style>
