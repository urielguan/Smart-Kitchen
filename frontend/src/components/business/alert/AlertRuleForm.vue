<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { useAlertRuleStore } from '@/stores/modules/alert-rule'
import {
  RULE_TYPES,
  RULE_ALERT_LEVELS,
  THRESHOLD_METRICS,
  COMPARE_OPERATORS,
  LOGIC_OPERATORS,
  NOTIFY_CHANNELS,
} from '@/types/alert-rule'
import type { AlertRuleCreateDTO, AlertRuleUpdateDTO } from '@/types/alert-rule'
import { DEVICE_TYPES } from '@/constants/device'
import { useDictCategoryStore } from '@/stores/modules/dict-category'
import { roleApi } from '@/api/modules/role'
import { employeeApi } from '@/api/modules/employee'
import { deviceApi } from '@/api/modules/device'
import { materialApi } from '@/api/modules/material'

const ruleStore = useAlertRuleStore()
const dictCategoryStore = useDictCategoryStore()

const visible = computed({
  get: () => ruleStore.formVisible,
  set: (val) => { if (!val) ruleStore.closeForm() },
})

const handleClose = () => ruleStore.closeForm()

// ========== 表单数据 ==========
const formRef = ref()
const formData = ref<{
  ruleName: string
  ruleType: string
  deviceType: string
  deviceIds: number[]
  materialIds: number[]
  alertLevel: string
  notifyChannels: string[]
  notifyUsers: string[]
  dispatchScopeRoles: string[]
  isEnabled: number
  autoDispatch: number
  conditions: Array<{ metric: string; operator: string; value: number | undefined }>
  logic: string
  duration: number | undefined
  offlineMinutes: number | undefined
}>({
  ruleName: '', ruleType: 'threshold', deviceType: '', deviceIds: [], materialIds: [],
  alertLevel: 'warning',
  notifyChannels: [], notifyUsers: [], dispatchScopeRoles: [], isEnabled: 1, autoDispatch: 0,
  conditions: [{ metric: 'temperature', operator: '>', value: undefined }], logic: 'and',
  duration: undefined, offlineMinutes: undefined,
})

const formRules = {
  ruleName: [
    { required: true, message: '请输入规则名称', trigger: 'blur' },
    { max: 100, message: '规则名称最长100个字符', trigger: 'blur' },
  ],
  ruleType: [{ required: true, message: '请选择规则类型', trigger: 'change' }],
  deviceType: [{ required: true, message: '请选择设备类型', trigger: 'change' }],
  alertLevel: [{ required: true, message: '请选择告警级别', trigger: 'change' }],
}

// 设备类型选项：从字典加载，缓存失效时自动重新获取
const deviceTypeOptions = computed(() => {
  const dictOptions = dictCategoryStore.getCachedOptions('device_type')
  if (dictOptions.length > 0) {
    return dictOptions.map(item => ({ label: item.dictName, value: item.value }))
  }
  dictCategoryStore.fetchOptions('device_type')
  return DEVICE_TYPES.map(item => ({ label: item.label, value: item.value }))
})

// 下拉选项数据
const roleOptions = ref<{ value: string; label: string }[]>([])
const employeeOptions = ref<{ value: string; label: string }[]>([])

const loadSelectOptions = async () => {
  try {
    const [roleRes, empRes] = await Promise.all([
      roleApi.getList({ status: 'active' }),
      employeeApi.getList({ status: 'active', accountStatus: 'active', pageSize: 500 }),
      dictCategoryStore.fetchOptions('device_type'),
    ])
    if (roleRes.data) {
      const roles = Array.isArray(roleRes.data) ? roleRes.data : (roleRes.data as any).list || []
      roleOptions.value = roles.map((r: any) => ({ value: String(r.id), label: r.roleName }))
    }
    if (empRes.data) {
      const pageData = empRes.data as any
      const emps = pageData.list || pageData || []
      employeeOptions.value = (Array.isArray(emps) ? emps : []).map((e: any) => ({ value: String(e.id), label: e.realName }))
    }
  } catch { /* ignore */ }
}
loadSelectOptions()

// 设备范围选项
const deviceScopeOptions = ref<{ value: number; label: string }[]>([])

const loadDeviceScopeOptions = async (deviceType: string) => {
  if (!deviceType) {
    deviceScopeOptions.value = []
    return
  }
  try {
    const res = await deviceApi.getList({ deviceType, status: 'active', pageSize: 999 } as any)
    if (res.data) {
      const pageData = res.data as any
      const devices = pageData.list || pageData || []
      deviceScopeOptions.value = (Array.isArray(devices) ? devices : []).map((d: any) => ({ value: d.id, label: d.deviceName }))
    }
  } catch { /* ignore */ }
}

// 物料范围选项
const materialScopeOptions = ref<{ value: number; label: string }[]>([])
const loadMaterialScopeOptions = async () => {
  try {
    const res = await materialApi.getList({ status: 'active', pageSize: 999 } as any)
    if (res.data) {
      const pageData = res.data as any
      const materials = pageData.list || pageData || []
      materialScopeOptions.value = (Array.isArray(materials) ? materials : []).map((m: any) => ({
        value: m.id,
        label: `${m.materialName}（${m.materialCode}）`,
      }))
    }
  } catch { /* ignore */ }
}
loadMaterialScopeOptions()

// 全选/取消全选
const isAllDevicesSelected = computed(() =>
  deviceScopeOptions.value.length > 0 && formData.value.deviceIds.length === deviceScopeOptions.value.length
)
const toggleSelectAllDevices = () => {
  if (isAllDevicesSelected.value) {
    formData.value.deviceIds = []
  } else {
    formData.value.deviceIds = deviceScopeOptions.value.map(d => d.value)
  }
}

const isAllMaterialsSelected = computed(() =>
  materialScopeOptions.value.length > 0 && formData.value.materialIds.length === materialScopeOptions.value.length
)
const toggleSelectAllMaterials = () => {
  if (isAllMaterialsSelected.value) {
    formData.value.materialIds = []
  } else {
    formData.value.materialIds = materialScopeOptions.value.map(m => m.value)
  }
}

const conditionValid = computed(() => {
  if (formData.value.ruleType === 'threshold') {
    return formData.value.conditions.every(c =>
      c.metric && c.operator && c.value !== undefined && c.value !== null)
  }
  if (formData.value.ruleType === 'offline') {
    return formData.value.offlineMinutes !== undefined && formData.value.offlineMinutes !== null && formData.value.offlineMinutes > 0
  }
  return true
})

const showMaterialScope = computed(() => formData.value.ruleType === 'material')
const showDeviceFields = computed(() => formData.value.ruleType !== 'material')

// 编辑模式赋值时跳过 ruleType watch 的重置逻辑
let skipRuleTypeWatch = false

watch(() => formData.value.ruleType, () => {
  if (skipRuleTypeWatch) return
  formData.value.conditions = [{ metric: 'temperature', operator: '>', value: undefined }]
  formData.value.logic = 'and'
  formData.value.duration = undefined
  formData.value.offlineMinutes = undefined
})

watch(() => formData.value.deviceType, (newType) => {
  if (skipRuleTypeWatch) return
  formData.value.deviceIds = []
  loadDeviceScopeOptions(newType)
})

watch(() => ruleStore.formVisible, (visible) => {
  if (visible) {
    loadMaterialScopeOptions()
  }
  if (visible && ruleStore.formMode === 'create') {
    const defaultDeviceType = deviceTypeOptions.value.find((d: any) => d.value)?.value || ''
    formData.value = {
      ruleName: '', ruleType: 'threshold', deviceType: defaultDeviceType, deviceIds: [], materialIds: [],
      alertLevel: 'warning',
      notifyChannels: [], notifyUsers: [], dispatchScopeRoles: [], isEnabled: 1, autoDispatch: 0,
      conditions: [{ metric: 'temperature', operator: '>', value: undefined }], logic: 'and',
      duration: undefined, offlineMinutes: undefined,
    }
    loadDeviceScopeOptions(defaultDeviceType)
  } else if (visible && ruleStore.formMode === 'edit' && ruleStore.currentRule) {
    const rule = ruleStore.currentRule
    let condition: Record<string, any> = {}
    try { condition = rule.conditionJson ? JSON.parse(rule.conditionJson) : {} } catch { /* ignore */ }

    let conditions: Array<{ metric: string; operator: string; value: number | undefined }>
    let logic: string
    let duration: number | undefined
    if (condition.conditions && Array.isArray(condition.conditions)) {
      conditions = condition.conditions.map((c: any) => ({
        metric: c.metric || 'temperature',
        operator: c.operator || '>',
        value: c.value,
      }))
      logic = condition.logic || 'and'
      duration = condition.duration
    } else if (condition.metric) {
      conditions = [{ metric: condition.metric, operator: condition.operator, value: condition.value }]
      logic = 'and'
      duration = condition.duration
    } else {
      conditions = [{ metric: 'temperature', operator: '>', value: undefined }]
      logic = 'and'
      duration = undefined
    }

    skipRuleTypeWatch = true
    formData.value = {
      ruleName: rule.ruleName, ruleType: rule.ruleType, deviceType: rule.deviceType || '',
      deviceIds: rule.deviceIds ? rule.deviceIds.split(',').filter(Boolean).map(Number) : [],
      materialIds: rule.materialIds ? rule.materialIds.split(',').filter(Boolean).map(Number) : [],
      alertLevel: rule.alertLevel,
      notifyChannels: rule.notifyChannels ? rule.notifyChannels.split(',').filter(Boolean) : [],
      notifyUsers: rule.notifyUsers ? rule.notifyUsers.split(',').filter(Boolean) : [],
      dispatchScopeRoles: rule.dispatchScopeRoles ? rule.dispatchScopeRoles.split(',').filter(Boolean) : [],
      isEnabled: rule.isEnabled, autoDispatch: rule.autoDispatch ?? 0,
      conditions, logic, duration, offlineMinutes: condition.offlineMinutes,
    }
    loadDeviceScopeOptions(rule.deviceType || '')
    nextTick(() => { skipRuleTypeWatch = false })
  }
})

const buildConditionJson = (): string => {
  if (formData.value.ruleType === 'threshold') {
    const obj: Record<string, any> = {
      logic: formData.value.logic,
      conditions: formData.value.conditions.map(c => ({
        metric: c.metric, operator: c.operator, value: c.value,
      })),
    }
    if (formData.value.duration) obj.duration = formData.value.duration
    return JSON.stringify(obj)
  }
  if (formData.value.ruleType === 'offline') {
    return JSON.stringify({ offlineMinutes: formData.value.offlineMinutes })
  }
  return JSON.stringify({})
}

const handleSubmitRule = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || !conditionValid.value) return

  const isMaterial = formData.value.ruleType === 'material'
  const data = {
    ruleName: formData.value.ruleName, ruleType: formData.value.ruleType,
    deviceType: isMaterial ? undefined : (formData.value.deviceType || undefined),
    deviceIds: isMaterial ? undefined : (formData.value.deviceIds.length > 0 ? formData.value.deviceIds.join(',') : ''),
    materialIds: isMaterial ? (formData.value.materialIds.length > 0 ? formData.value.materialIds.join(',') : '') : undefined,
    alertLevel: formData.value.alertLevel,
    conditionJson: isMaterial ? JSON.stringify({}) : buildConditionJson(),
    notifyChannels: formData.value.notifyChannels.length > 0 ? formData.value.notifyChannels.join(',') : undefined,
    notifyUsers: formData.value.notifyUsers.length > 0 ? formData.value.notifyUsers.join(',') : undefined,
    dispatchScopeRoles: formData.value.dispatchScopeRoles.length > 0 ? formData.value.dispatchScopeRoles.join(',') : undefined,
    isEnabled: formData.value.isEnabled,
    autoDispatch: formData.value.autoDispatch,
  }
  await ruleStore.submitForm(data as AlertRuleCreateDTO | AlertRuleUpdateDTO)
}

// 阈值条件增删
const addCondition = () => {
  if (formData.value.conditions.length < 2) {
    formData.value.conditions.push({ metric: 'temperature', operator: '>', value: undefined })
  }
}
const removeCondition = (index: number) => {
  formData.value.conditions.splice(index, 1)
}
</script>

<template>
  <el-dialog
    v-model="visible"
    width="600px"
    :close-on-click-modal="false"
    :show-close="false"
    align-center
    class="alert-rule-form-dialog"
    @close="handleClose"
  >
    <template #header>
      <div class="dialog-header">
        <span class="dialog-title">{{ ruleStore.formMode === 'create' ? '新增告警规则' : '编辑告警规则' }}</span>
        <div class="close-btn" @click="handleClose">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <line x1="6" y1="6" x2="18" y2="18" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
            <line x1="6" y1="18" x2="18" y2="6" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </div>
      </div>
    </template>

    <el-form ref="formRef" :model="formData" :rules="formRules" label-width="110px">
      <el-form-item label="规则名称：" prop="ruleName">
        <el-input v-model="formData.ruleName" maxlength="100" show-word-limit placeholder="请输入规则名称" />
      </el-form-item>
      <el-form-item label="规则类型：" prop="ruleType">
        <el-select v-model="formData.ruleType" style="width: 100%">
          <el-option v-for="t in RULE_TYPES" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="showDeviceFields" label="设备类型：" prop="deviceType">
        <el-select v-model="formData.deviceType" style="width: 100%" placeholder="请选择设备类型">
          <el-option v-for="d in deviceTypeOptions.filter((d: any) => d.value)" :key="d.value" :label="d.label" :value="d.value" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="showDeviceFields" label="适用设备范围：">
        <div style="display: flex; align-items: center; gap: 8px; width: 100%">
          <el-select v-model="formData.deviceIds" multiple filterable style="flex: 1" placeholder="选择适用的设备（为空则规则不生效）">
            <el-option v-for="d in deviceScopeOptions" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
          <el-button link type="primary" size="small" style="color: #7288FA; flex-shrink: 0" @click="toggleSelectAllDevices">
            {{ isAllDevicesSelected ? '取消全选' : '全选' }}
          </el-button>
        </div>
      </el-form-item>

      <el-form-item v-if="showMaterialScope" label="适用物料范围：">
        <div style="display: flex; align-items: center; gap: 8px; width: 100%">
          <el-select v-model="formData.materialIds" multiple filterable style="flex: 1" placeholder="选择适用的物料（为空则规则不生效）">
            <el-option v-for="m in materialScopeOptions" :key="m.value" :label="m.label" :value="m.value" />
          </el-select>
          <el-button link type="primary" size="small" style="color: #7288FA; flex-shrink: 0" @click="toggleSelectAllMaterials">
            {{ isAllMaterialsSelected ? '取消全选' : '全选' }}
          </el-button>
        </div>
      </el-form-item>

      <template v-if="showMaterialScope">
        <el-form-item label="触发条件：">
          <el-alert type="info" :closable="false" description="物料告警条件继承自物料配置（预警天数、临期天数、最高/最低库存）" />
        </el-form-item>
      </template>

      <template v-if="formData.ruleType === 'threshold'">
        <el-form-item label="触发条件：" required>
          <div class="condition-builder">
            <el-row :gutter="8">
              <el-col :span="8">
                <el-select v-model="formData.conditions[0].metric" style="width: 100%">
                  <el-option v-for="m in THRESHOLD_METRICS" :key="m.value" :label="m.label" :value="m.value" />
                </el-select>
              </el-col>
              <el-col :span="6">
                <el-select v-model="formData.conditions[0].operator" style="width: 100%">
                  <el-option v-for="o in COMPARE_OPERATORS" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </el-col>
              <el-col :span="10">
                <el-input-number v-model="formData.conditions[0].value" :controls="false" :precision="2" :step="0.01" placeholder="阈值" style="width: 100%" />
              </el-col>
            </el-row>
            <template v-if="formData.conditions.length === 2">
              <div style="margin: 6px 0">
                <el-radio-group v-model="formData.logic" size="small">
                  <el-radio-button v-for="l in LOGIC_OPERATORS" :key="l.value" :value="l.value">{{ l.label }}</el-radio-button>
                </el-radio-group>
              </div>
              <el-row :gutter="8">
                <el-col :span="8">
                  <el-select v-model="formData.conditions[1].metric" style="width: 100%">
                    <el-option v-for="m in THRESHOLD_METRICS" :key="m.value" :label="m.label" :value="m.value" />
                  </el-select>
                </el-col>
                <el-col :span="6">
                  <el-select v-model="formData.conditions[1].operator" style="width: 100%">
                    <el-option v-for="o in COMPARE_OPERATORS" :key="o.value" :label="o.label" :value="o.value" />
                  </el-select>
                </el-col>
                <el-col :span="7">
                  <el-input-number v-model="formData.conditions[1].value" :controls="false" :precision="2" :step="0.01" placeholder="阈值" style="width: 100%" />
                </el-col>
                <el-col :span="3">
                  <el-button type="danger" text @click="removeCondition(1)">移除</el-button>
                </el-col>
              </el-row>
            </template>
            <el-button v-if="formData.conditions.length < 2" type="primary" text style="margin-top: 4px; color: #7288FA" @click="addCondition">+ 添加条件</el-button>
            <el-row :gutter="8" style="margin-top: 8px">
              <el-col :span="12">
                <el-input-number v-model="formData.duration" :min="0" :precision="0" :step="1" placeholder="持续时长（秒，选填）" style="width: 100%" />
              </el-col>
            </el-row>
          </div>
        </el-form-item>
      </template>

      <template v-if="formData.ruleType === 'offline'">
        <el-form-item label="触发条件：" required>
          <div class="condition-builder">
            离线超过
            <el-input-number v-model="formData.offlineMinutes" :min="1" :controls="false" style="width: 120px; margin: 0 8px" />
            分钟触发告警
          </div>
        </el-form-item>
      </template>

      <template v-if="formData.ruleType === 'ai_event'">
        <el-form-item label="触发条件：">
          <el-alert type="info" :closable="false" description="AI事件告警由算法平台自动触发，无需配置触发条件" />
        </el-form-item>
      </template>

      <el-form-item label="告警级别：" prop="alertLevel">
        <el-select v-model="formData.alertLevel" style="width: 100%">
          <el-option v-for="l in RULE_ALERT_LEVELS" :key="l.value" :label="l.label" :value="l.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="通知渠道：">
        <el-select v-model="formData.notifyChannels" multiple style="width: 100%" placeholder="选择通知渠道">
          <el-option v-for="c in NOTIFY_CHANNELS" :key="c.value" :label="c.label" :value="c.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="通知用户：">
        <el-select v-model="formData.notifyUsers" multiple filterable style="width: 100%" placeholder="选择通知用户">
          <el-option v-for="u in employeeOptions" :key="u.value" :label="u.label" :value="u.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="派单范围：">
        <el-select v-model="formData.dispatchScopeRoles" multiple filterable style="width: 100%" placeholder="选择可派单的角色">
          <el-option v-for="r in roleOptions" :key="r.value" :label="r.label" :value="r.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="自动派单：">
        <el-switch v-model="formData.autoDispatch" :active-value="1" :inactive-value="0" />
        <span style="margin-left: 8px; color: #909399; font-size: 12px">开启后该规则触发的告警将自动派单，派单失败则保持待处理状态</span>
      </el-form-item>
      <el-form-item label="是否启用：">
        <el-switch v-model="formData.isEnabled" :active-value="1" :inactive-value="0" />
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <el-button class="btn-cancel" @click="handleClose">取消</el-button>
        <el-button class="btn-primary" @click="handleSubmitRule" :disabled="!conditionValid">
          {{ ruleStore.formMode === 'create' ? '创建' : '保存' }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss">
/* ---- Dialog 容器（unscoped） ---- */
.alert-rule-form-dialog.el-dialog {
  background: #FFFFFF;
  --el-dialog-border-radius: 12px;
  --el-dialog-padding-primary: 0;
  border-radius: 12px !important;
  overflow: hidden;
  padding: 0 !important;
  display: flex !important;
  flex-direction: column !important;
  margin: auto !important;
}

.alert-rule-form-dialog .el-dialog__header {
  padding: 24px 24px 13px !important;
  margin-right: 0;
  border-bottom: 1px solid #E1E2E9;
}

.alert-rule-form-dialog .el-dialog__body {
  padding: 16px 24px 24px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.alert-rule-form-dialog .el-dialog__footer {
  padding: 0;
  border-top: 1px solid #E1E2E9;
}

/* and/or 切换按钮选中态颜色 */
.alert-rule-form-dialog .el-radio-button__original-radio:checked + .el-radio-button__inner {
  background-color: #7288FA !important;
  border-color: #7288FA !important;
  box-shadow: -1px 0 0 0 #7288FA !important;
  color: #fff !important;

  &:hover {
    color: #fff !important;
  }
}

/* 未选中按钮 hover 文字颜色 */
.alert-rule-form-dialog .el-radio-button__inner:hover {
  color: #7288FA !important;
}

/* 全选/取消全选 link 按钮 hover 颜色 */
.alert-rule-form-dialog .el-button--primary.is-link:hover {
  color: #5C75E8 !important;
}
</style>

<style lang="scss" scoped>
.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 32px;
}

.dialog-title {
  font-family: 'Poppins', 'PingFang SC', sans-serif;
  font-weight: 500;
  font-size: 20px;
  line-height: 30px;
  color: #000000;
}
/* ---- 输入框 / 选择器 ---- */
:deep(.el-input__wrapper) {
  height: 32px;
  border-radius: 4px;
  box-shadow: 0 0 0 1px #D9D9D9 inset !important;
  padding: 4px 12px;

  &:hover {
    box-shadow: 0 0 0 1px #7288FA inset !important;
  }

  &.is-focus {
    box-shadow: 0 0 0 1px #7288FA inset !important;
  }
}

:deep(.el-form-item.is-error .el-input__wrapper) {
  box-shadow: 0 0 0 1px #FF4D4F inset !important;

  &:hover,
  &.is-focus {
    box-shadow: 0 0 0 1px #FF4D4F inset !important;
  }
}

:deep(.el-input__inner) {
  font-size: 14px;
  height: 24px;
  line-height: 24px;
}

:deep(.el-input__inner::placeholder),
:deep(.el-textarea__inner::placeholder) {
  color: rgba(0, 0, 0, 0.35);
}

.close-btn {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 32px;
  height: 32px;
  background: #FFF2E2;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;

  &:hover {
    background: #FFE8CC;
  }
}

.dialog-footer {
  display: flex;
  align-items: center;
  gap: 9px;
  justify-content: flex-end;
  padding: 12px 24px 16px;
}

.btn-cancel {
  width: 58px;
  height: 32px;
  background: #FFFFFF;
  border: 1px solid #BEC0CA;
  border-radius: 6px;
  color: #53545C;
  font-size: 13px;

  &:hover, &:focus {
    background: #F5F7FA;
    border-color: #7288FA;
    color: #7288FA;
  }
}

.btn-primary {
  min-width: 58px;
  height: 32px;
  padding: 5px 16px;
  background: #7288FA;
  border-color: #7288FA;
  border-radius: 6px;
  color: #fff;

  &:hover {
    background: #5C75E8;
    border-color: #5C75E8;
    color: #fff;
  }

  &.is-disabled,
  &.is-disabled:hover {
    background: #D4DBF5;
    border-color: #D4DBF5;
    color: #fff;
  }
}

.condition-builder {
  width: 100%;
}
</style>
