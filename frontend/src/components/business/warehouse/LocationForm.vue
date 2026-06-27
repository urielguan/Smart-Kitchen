<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { Location, LocationForm } from '@/types/warehouse'
import { LOCATION_STATUS_OPTIONS } from '@/constants/warehouse'
import { warehouseApi } from '@/api/modules/warehouse'

interface Props {
  modelValue: boolean
  warehouseId: number
  locationId?: number | null
  locationData?: Location | null
}
const props = withDefaults(defineProps<Props>(), {
  modelValue: false, locationId: null, locationData: null
})
const emit = defineEmits<{
  'update:modelValue': [val: boolean]
  success: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})
const isEdit      = computed(() => !!props.locationId)
const dialogTitle = computed(() => isEdit.value ? '编辑仓位' : '新增仓位')
const submitting  = ref(false)
const formRef     = ref()

const defaultForm = (): LocationForm => ({
  warehouseId: props.warehouseId,
  locationCode: '', locationName: '',
  locationType: '', regionCode: '', shelfCode: '', slotCode: '',
  capacity: undefined, capacityUnit: '平方米',
  temperatureMin: undefined, temperatureMax: undefined,
  humidityMin: undefined, humidityMax: undefined,
  sensorDeviceId: undefined,
  materialTypes: '', status: 'available', remark: '', version: undefined,
})

const formData = ref<LocationForm>(defaultForm())

const rules = {
  locationCode: [{ required: true, message: '请输入仓位编码', trigger: 'blur' }],
  locationName: [{ required: true, message: '请输入仓位名称', trigger: 'blur' }],
}

watch(() => props.modelValue, (val) => {
  if (val) {
    formData.value = props.locationData
      ? {
          warehouseId: props.warehouseId,
          locationCode: props.locationData.locationCode,
          locationName: props.locationData.locationName,
          locationType: props.locationData.locationType ?? '',
          regionCode: props.locationData.regionCode ?? '',
          shelfCode: props.locationData.shelfCode ?? '',
          slotCode: props.locationData.slotCode ?? '',
          capacity: props.locationData.capacity,
          capacityUnit: props.locationData.capacityUnit,
          temperatureMin: props.locationData.temperatureMin,
          temperatureMax: props.locationData.temperatureMax,
          humidityMin: props.locationData.humidityMin,
          humidityMax: props.locationData.humidityMax,
          sensorDeviceId: props.locationData.sensorDeviceId,
          materialTypes: props.locationData.materialTypes,
          status: props.locationData.status,
          remark: props.locationData.remark,
          version: props.locationData.version,
        }
      : { ...defaultForm(), warehouseId: props.warehouseId }
  }
})

const handleSubmit = async () => {
  await formRef.value.validate()
  submitting.value = true
  try {
    if (isEdit.value && props.locationId) {
      await warehouseApi.updateLocation(props.locationId, formData.value)
      ElMessage.success('编辑成功')
    } else {
      await warehouseApi.createLocation(formData.value)
      ElMessage.success('新增成功')
    }
    emit('success')
    handleClose()
  } catch {
  } finally {
    submitting.value = false
  }
}

const handleClose = () => {
  formRef.value?.resetFields()
  formData.value = defaultForm()
  visible.value = false
}
</script>

<template>
  <el-dialog v-model="visible" width="758px" :close-on-click-modal="false"
             :show-close="false" align-center class="location-form-dialog"
             @close="handleClose">
    <template #header>
      <div class="dialog-header">
        <span class="dialog-title">{{ dialogTitle }}</span>
        <div class="close-btn" @click="handleClose">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <line x1="6" y1="6" x2="18" y2="18" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
            <line x1="6" y1="18" x2="18" y2="6" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </div>
      </div>
    </template>

    <el-form ref="formRef" :model="formData" :rules="rules" label-width="100px" label-suffix="：">
      <el-row :gutter="24">
        <el-col :span="12">
          <el-form-item label="仓位名称" prop="locationName">
            <el-input v-model="formData.locationName" placeholder="请输入仓位名称" maxlength="100" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="仓位编码" prop="locationCode">
            <el-input v-model="formData.locationCode" :readonly="isEdit" placeholder="请输入仓位编码" maxlength="100" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="仓位类型" prop="locationType">
            <el-input v-model="formData.locationType" placeholder="请输入仓位类型" maxlength="50" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="区域编码">
            <el-input v-model="formData.regionCode" placeholder="请输入区域编码" maxlength="50" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="货架编码">
            <el-input v-model="formData.shelfCode" placeholder="请输入货架编码" maxlength="50" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="货位编码">
            <el-input v-model="formData.slotCode" placeholder="请输入货位编码" maxlength="50" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="容量">
            <el-input-number v-model="formData.capacity" :min="0" style="width:100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="容量单位">
            <el-input v-model="formData.capacityUnit" placeholder="平方米/立方米" maxlength="20" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="状态">
            <el-select v-model="formData.status" placeholder="请选择状态" style="width:100%">
              <el-option v-for="s in LOCATION_STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="适用物料">
            <el-input v-model="formData.materialTypes" placeholder="逗号分隔，如：肉类,蔬菜" maxlength="100" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="备注">
            <el-input v-model="formData.remark" type="textarea" :rows="2" placeholder="备注信息" maxlength="500" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <el-button class="btn-cancel" @click="handleClose">取消</el-button>
        <el-button class="btn-save" :loading="submitting" @click="handleSubmit">保存</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss">
/* ---- Dialog 容器（unscoped） ---- */
.location-form-dialog.el-dialog {
  width: 758px;
  height: 530px;
  background: #FFFFFF;
  --el-dialog-border-radius: 12px;
  --el-dialog-padding-primary: 0;
  border-radius: 12px !important;
  overflow: hidden;
  padding: 0 !important;
}

.location-form-dialog.el-dialog .el-dialog__header {
  padding: 24px 24px 13px !important;
  margin-right: 0;
  border-bottom: 1px solid #E1E2E9;
}

.location-form-dialog.el-dialog .el-dialog__body {
  padding: 24px;
}

.location-form-dialog.el-dialog .el-dialog__footer {
  padding: 0;
  border-top: 1px solid #E1E2E9;
}
</style>

<style lang="scss" scoped>
/* ---- 头部 ---- */
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

/* ---- 底部 ---- */
.dialog-footer {
  display: flex;
  flex-direction: row;
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
  box-shadow: 0 2px 0 rgba(0, 0, 0, 0.016);
  color: #53545C;
  font-family: 'PingFang SC', sans-serif;
  font-size: 13px;
  line-height: 22px;

  &:hover,
  &:focus {
    background: #FFFFFF;
    border-color: #BEC0CA;
    color: #53545C;
  }
}

.btn-save {
  width: 60px;
  height: 32px;
  background: #7288FA;
  border-color: #7288FA;
  border-radius: 6px;
  box-shadow: 0 2px 0 rgba(0, 0, 0, 0.043);
  color: #FFFFFF;
  font-family: 'Roboto', sans-serif;
  font-size: 14px;
  line-height: 22px;

  &:hover,
  &:focus {
    background: #7288FA;
    border-color: #7288FA;
    color: #FFFFFF;
  }
}

/* ---- 表单 ---- */
:deep(.el-form-item) {
  margin-bottom: 16px;
}

:deep(.el-form-item__label) {
  font-family: 'Roboto', 'PingFang SC', sans-serif;
  font-size: 14px;
  font-weight: 400;
  color: rgba(0, 0, 0, 0.85);
  line-height: 32px;
  padding-right: 9px;
}

:deep(.el-form-item.is-required .el-form-item__label::before) {
  color: #FF4D4F !important;
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

:deep(.el-input__inner) {
  font-size: 14px;
  height: 24px;
  line-height: 24px;
}

:deep(.el-input__inner::placeholder),
:deep(.el-textarea__inner::placeholder) {
  color: rgba(0, 0, 0, 0.35);
}

/* ---- 数字输入步进器 ---- */
:deep(.el-input-number__decrease),
:deep(.el-input-number__increase) {
  width: 31px;
  height: 30px;
  background: #F5F7FA;
  border-color: #D9D9D9;
  color: #7C7E81;
}

/* ---- 文本域 ---- */
:deep(.el-textarea__inner) {
  border: 1px solid #D9D9D9;
  border-radius: 2px;
  font-size: 14px;
  padding: 5px 12px;

  &:hover {
    border-color: #7288FA;
  }

  &:focus {
    border-color: #7288FA;
    box-shadow: none;
  }
}
</style>
