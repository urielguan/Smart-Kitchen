<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { Warehouse, WarehouseForm } from '@/types/warehouse'
import { WAREHOUSE_STATUS_OPTIONS } from '@/constants/warehouse'
import { useDictCategoryStore } from '@/stores/modules/dict-category'
import { useUserStore } from '@/stores/modules/user'
import { buildActiveDictOptions } from '@/utils/dict-category'
import { warehouseApi } from '@/api/modules/warehouse'

interface Props {
  modelValue: boolean
  warehouseId?: number | null
  warehouseData?: Warehouse | null
}
const props = withDefaults(defineProps<Props>(), {
  modelValue: false, warehouseId: null, warehouseData: null
})
const emit = defineEmits<{
  'update:modelValue': [val: boolean]
  success: []
}>()
const dictCategoryStore = useDictCategoryStore()
const userStore = useUserStore()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})
const isEdit      = computed(() => !!props.warehouseId)
const dialogTitle = computed(() => isEdit.value ? '编辑仓库' : '新增仓库')
const submitting  = ref(false)
const formRef     = ref()

const warehouseTypeOptions = computed(() => buildActiveDictOptions(
  dictCategoryStore.getCachedOptions('warehouse_type'),
  props.warehouseData ? formData.value.warehouseType : undefined,
  dictCategoryStore.getCachedOptions('warehouse_type', true),
  props.warehouseData?.warehouseTypeName || formData.value.warehouseType
))

const defaultForm = (): WarehouseForm => ({
  warehouseCode: '', warehouseName: '', warehouseType: dictCategoryStore.getCachedOptions('warehouse_type')[0]?.value || '',
  capacity: undefined, capacityUnit: '平方米',
  address: '', managerName: '', managerPhone: '',
  status: 'active', remark: '', version: undefined,
  temperatureMin: undefined, temperatureMax: undefined,
  humidityMin: undefined, humidityMax: undefined,
})

const formData = ref<WarehouseForm>(defaultForm())

const rules = {
  warehouseCode: [{ required: true, message: '请输入仓库编码', trigger: 'blur' }],
  warehouseName: [{ required: true, message: '请输入仓库名称', trigger: 'blur' }],
  warehouseType: [{ required: true, message: '请选择仓库类型', trigger: 'change' }],
}

watch(() => props.modelValue, async (val) => {
  if (val) {
    await dictCategoryStore.fetchOptions('warehouse_type', false, true)
    if (props.warehouseData) {
      const d = props.warehouseData
      formData.value = {
        warehouseCode: d.warehouseCode, warehouseName: d.warehouseName,
        warehouseType: d.warehouseType, capacity: d.capacity,
        capacityUnit: d.capacityUnit, address: d.address,
        managerName: d.managerName, managerPhone: d.managerPhone,
        status: d.status, remark: d.remark, version: d.version,
        temperatureMin: d.temperatureMin, temperatureMax: d.temperatureMax,
        humidityMin: d.humidityMin, humidityMax: d.humidityMax,
      }
    } else {
      formData.value = defaultForm()
    }
  }
})

const handleSubmit = async () => {
  if (!isEdit.value && userStore.userInfo?.orgId == null) {
    ElMessage.error('当前账号未绑定组织，请联系管理员处理')
    return
  }

  await formRef.value.validate()
  submitting.value = true
  try {
    if (isEdit.value && props.warehouseId) {
      await warehouseApi.update(props.warehouseId, formData.value)
      ElMessage.success('编辑成功')
    } else {
      await warehouseApi.create(formData.value)
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
             :show-close="false" align-center class="warehouse-form-dialog"
             @close="handleClose">
    <template #header>
      <div class="dialog-header">
        <span class="dialog-title">{{ dialogTitle }}</span>
        <div class="close-btn" @click="handleClose">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
            <line x1="6" y1="6" x2="18" y2="18" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
            <line x1="6" y1="18" x2="18" y2="6" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </div>
      </div>
    </template>

    <el-form ref="formRef" :model="formData" :rules="rules" label-width="100px" label-suffix="：">
      <el-row :gutter="24">
        <el-col :span="12">
          <el-form-item label="仓库名称" prop="warehouseName">
            <el-input v-model="formData.warehouseName" placeholder="请输入仓库名称" maxlength="100" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="仓库编码" prop="warehouseCode">
            <el-input v-model="formData.warehouseCode" :readonly="isEdit" placeholder="请输入仓库编码" maxlength="50" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="仓库类型" prop="warehouseType">
            <el-select v-model="formData.warehouseType" placeholder="请选择仓库类型" style="width:100%">
              <el-option v-for="t in warehouseTypeOptions" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="状态">
            <el-select v-model="formData.status" placeholder="请选择状态" style="width:100%">
              <el-option v-for="s in WAREHOUSE_STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
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
          <el-form-item label="负责人">
            <el-input v-model="formData.managerName" placeholder="请输入负责人姓名" maxlength="50" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="联系方式">
            <el-input v-model="formData.managerPhone" placeholder="请输入联系电话" maxlength="20" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="位置">
            <el-input v-model="formData.address" placeholder="请输入仓库位置/地址" maxlength="200" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="备注">
            <el-input v-model="formData.remark" type="textarea" :rows="2" placeholder="备注信息" maxlength="300" />
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
/* ---- Dialog 容器（unscoped，因为 el-dialog teleport 到 body） ---- */
.warehouse-form-dialog.el-dialog {
  width: 758px;
  max-height: 80vh;
  background: #FFFFFF;
  --el-dialog-border-radius: 12px;
  --el-dialog-padding-primary: 0;
  border-radius: 12px !important;
  overflow: hidden;
  padding: 0 !important;
  display: flex !important;
  flex-direction: column !important;
}

.warehouse-form-dialog.el-dialog .el-dialog__header {
  padding: 24px 24px 13px !important;
  margin-right: 0;
  border-bottom: 1px solid #E1E2E9;
}

.warehouse-form-dialog.el-dialog .el-dialog__body {
  padding: 24px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.warehouse-form-dialog.el-dialog .el-dialog__footer {
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
