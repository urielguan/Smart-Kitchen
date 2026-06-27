<script setup lang="ts">
import { computed, ref, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import type { Material, MaterialForm } from '@/types/material'
import { useDictCategoryStore } from '@/stores/modules/dict-category'
import { buildActiveDictOptions } from '@/utils/dict-category'
import { generateMaterialCode, getImageUrl } from '@/utils'
import { materialApi } from '@/api/modules/material'
import { dispatchMaterialUpdated } from '@/utils/material-sync'

interface Props {
  modelValue: boolean
  materialId?: number | null
  materialData?: Material | null
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
  materialId: null,
  materialData: null
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
}>()
const dictCategoryStore = useDictCategoryStore()

/** 弹窗显示状态 */
const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

/** 是否编辑模式 */
const isEdit = computed(() => !!props.materialId)

/** 弹窗标题 */
const dialogTitle = computed(() => isEdit.value ? '编辑物料' : '新增物料')

/** 表单引用 */
const formRef = ref()

/** 图片上传状态 */
const uploading = ref(false)

/** 表单数据 */
const formData = ref<MaterialForm>(getDefaultFormData())

const materialCategoryOptions = computed(() => buildActiveDictOptions(
  dictCategoryStore.getCachedOptions('material_category'),
  props.materialData ? formData.value.categoryName : undefined,
  dictCategoryStore.getCachedOptions('material_category', true),
  formData.value.categoryName
))

/** 表单验证规则 */
const formRules = {
  materialName: [
    { required: true, message: '请输入物料名称', trigger: 'blur' }
  ],
  materialCode: [
    { required: true, message: '请输入物料编码', trigger: 'blur' }
  ],
  materialSpec: [
    { required: true, message: '请输入规格', trigger: 'blur' }
  ],
  unit: [
    { required: true, message: '请输入单位', trigger: 'blur' }
  ],
  categoryName: [
    { required: true, message: '请选择类别', trigger: 'change' }
  ],
  shelfLifeDays: [
    { required: true, message: '请输入保质期天数', trigger: 'blur' }
  ],
  nearExpiryDays: [
    { required: true, message: '请输入临期提醒天数', trigger: 'blur' }
  ],
  warningDays: [
    { required: true, message: '请输入预警天数', trigger: 'blur' }
  ],
  minStock: [
    { required: true, message: '请输入最低库存', trigger: 'blur' }
  ],
  maxStock: [
    { required: true, message: '请输入最高库存', trigger: 'blur' }
  ]
}

/** 获取默认表单数据 */
function getDefaultFormData(): MaterialForm {
  return {
    materialCode: generateMaterialCode(),
    materialName: '',
    materialSpec: '',
    unit: '',
    categoryName: '',
    shelfLifeDays: 30,
    nearExpiryDays: 7,
    warningDays: 15,
    minStock: 0,
    maxStock: 100,
    storageRequire: '',
    imageUrl: '',
    remark: ''
  }
}

/** 监听弹窗显示状态，回填表单 */
watch(
  () => props.modelValue,
  async (val) => {
    if (val) {
      await dictCategoryStore.fetchOptions('material_category', false, true)
      // 使用 nextTick 确保 props 更新完成
      await nextTick()
      // 弹窗打开时，根据是否有物料数据来回填
      if (props.materialData) {
        fillFormData(props.materialData)
      } else if (!props.materialId) {
        // 新增模式，重置表单
        formData.value = getDefaultFormData()
      }
    }
  },
  { immediate: true }
)

/** 监听物料数据变化（处理同一弹窗多次打开不同物料的情况） */
watch(
  () => props.materialData,
  (data) => {
    if (visible.value && data) {
      fillFormData(data)
    }
  },
  { deep: true }
)

/** 填充表单数据 */
function fillFormData(data: Material) {
  formData.value = {
    materialCode: data.materialCode,
    materialName: data.materialName,
    materialSpec: data.materialSpec,
    unit: data.unit,
    categoryName: data.categoryName,
    shelfLifeDays: data.shelfLifeDays,
    nearExpiryDays: data.nearExpiryDays,
    warningDays: data.warningDays,
    minStock: data.minStock,
    maxStock: data.maxStock,
    storageRequire: data.storageRequire,
    imageUrl: data.imageUrl,
    remark: data.remark
  }
}

/** 处理图片上传 */
const handleImageChange = async (file: File) => {
  // 验证文件类型
  const isImage = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'].includes(file.type)
  if (!isImage) {
    ElMessage.error('只能上传图片文件')
    return false
  }
  // 验证文件大小（最大 10MB）
  const isLt10M = file.size / 1024 / 1024 < 10
  if (!isLt10M) {
    ElMessage.error('图片大小不能超过 10MB')
    return false
  }

  try {
    uploading.value = true
    const res = await materialApi.uploadImage(file)
    if (res.code === 'SUCCESS' && res.data) {
      formData.value.imageUrl = res.data.imageUrl
      ElMessage.success('图片上传成功')
    }
  } catch (error: any) {
    ElMessage.error(error.message || '图片上传失败')
  } finally {
    uploading.value = false
  }
  return false
}

/** 删除图片 */
const handleImageRemove = () => {
  formData.value.imageUrl = ''
}

/** 提交表单 */
const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate()

  // 验证库存范围
  if (formData.value.maxStock < formData.value.minStock) {
    ElMessage.error('最高库存不能小于最低库存')
    return
  }

  try {
    if (isEdit.value && props.materialId) {
      await materialApi.update(props.materialId, formData.value)
      ElMessage.success('编辑成功')
    } else {
      await materialApi.create(formData.value)
      ElMessage.success('新增成功')
    }
    dispatchMaterialUpdated()
    emit('success')
    handleClose()
  } catch {
    // 错误消息已在 API 拦截器中显示，此处不再重复
  }
}

/** 关闭弹窗 */
const handleClose = () => {
  formRef.value?.resetFields()
  formData.value = getDefaultFormData()
  visible.value = false
}
</script>

<template>
  <el-dialog
    v-model="visible"
    width="758px"
    :close-on-click-modal="false"
    :show-close="false"
    align-center
    @close="handleClose"
    class="material-form-dialog"
  >
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

    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="120px"
      label-suffix="："
    >
      <el-row :gutter="24">
        <el-col :span="12">
          <el-form-item label="物料名称" prop="materialName">
            <el-input v-model="formData.materialName" placeholder="请输入物料名称" maxlength="100" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="物料编码" prop="materialCode">
            <el-input
              v-model="formData.materialCode"
              :readonly="isEdit"
              placeholder="自动生成"
              maxlength="50"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="规格" prop="materialSpec">
            <el-input v-model="formData.materialSpec" placeholder="如：500g/袋" maxlength="100" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="单位" prop="unit">
            <el-input v-model="formData.unit" placeholder="如：袋、kg、桶" maxlength="20" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="类别" prop="categoryName">
            <el-select v-model="formData.categoryName" placeholder="请选择类别" style="width: 100%">
              <el-option
                v-for="item in materialCategoryOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="保质期(天)" prop="shelfLifeDays">
            <el-input-number
              v-model="formData.shelfLifeDays"
              :min="1"
              :step="1"
              :precision="0"
              step-strictly
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="临期提醒天数" prop="nearExpiryDays">
            <el-input-number
              v-model="formData.nearExpiryDays"
              :min="1"
              :step="1"
              :precision="0"
              step-strictly
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="预警天数" prop="warningDays">
            <el-input-number
              v-model="formData.warningDays"
              :min="1"
              :step="1"
              :precision="0"
              step-strictly
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="最低库存" prop="minStock">
            <el-input-number
              v-model="formData.minStock"
              :min="0"
              :step="1"
              :precision="0"
              step-strictly
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="最高库存" prop="maxStock">
            <el-input-number
              v-model="formData.maxStock"
              :min="0"
              :step="1"
              :precision="0"
              step-strictly
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="存储要求">
            <el-input v-model="formData.storageRequire" placeholder="如：冷藏 0-4℃" maxlength="200" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="备注">
            <el-input
              v-model="formData.remark"
              type="textarea"
              :rows="3"
              placeholder="备注信息"
              maxlength="300"
              show-word-limit
            />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="物料图片">
            <div class="image-upload-wrapper">
              <el-upload
                class="image-uploader"
                action="#"
                :show-file-list="false"
                :before-upload="handleImageChange"
                accept="image/*"
              >
                <div v-if="formData.imageUrl" class="image-preview">
                  <img :src="getImageUrl(formData.imageUrl)" alt="物料图片" />
                  <div class="image-actions">
                    <span @click.stop="handleImageRemove">删除</span>
                  </div>
                </div>
                <div v-else class="image-placeholder">
                  <el-icon><Plus /></el-icon>
                  <span>上传图片</span>
                </div>
              </el-upload>
              <div class="image-tip">支持 JPG、PNG、GIF、WebP 格式，最大 10MB</div>
            </div>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <el-button class="btn-cancel" @click="handleClose">取消</el-button>
        <el-button class="btn-save" :loading="uploading" @click="handleSubmit">保存</el-button>
      </div>
    </template>
  </el-dialog>
</template>

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
    background: #F5F7FA;
    border-color: #7288FA;
    color: #7288FA;
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
    background: #5C75E8;
    border-color: #5C75E8;
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

/* ---- 图片上传 ---- */
.image-upload-wrapper {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.image-uploader {
  flex-shrink: 0;
  width: 100px;
  height: 100px;
  background: #FAFAFA;
  border: 1px solid #D9D9D9;
  border-radius: 6px;
  cursor: pointer;
  overflow: hidden;
  transition: border-color 0.3s;
  display: flex;

  &:hover {
    border-color: #7288FA;
  }

  :deep(.el-upload) {
    width: 100%;
    height: 100%;
    display: flex;
  }
}

.image-preview {
  width: 100px;
  height: 100px;
  position: relative;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .image-actions {
    position: absolute;
    inset: 0;
    background: rgba(0, 0, 0, 0.5);
    display: flex;
    align-items: center;
    justify-content: center;
    opacity: 0;
    transition: opacity 0.3s;

    span {
      color: #fff;
      font-size: 12px;
      cursor: pointer;
    }
  }

  &:hover .image-actions {
    opacity: 1;
  }
}

.image-placeholder {
  width: 100px;
  height: 100px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #8c939d;
  gap: 4px;

  .el-icon {
    font-size: 24px;
  }

  span {
    font-size: 12px;
  }
}

.image-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}
</style>

<style lang="scss">
/* ---- Dialog 容器（unscoped，因为 el-dialog teleport 到 body） ---- */
.material-form-dialog.el-dialog {
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

.material-form-dialog.el-dialog .el-dialog__header {
  padding: 24px 24px 13px !important;
  margin-right: 0;
  border-bottom: 1px solid #E1E2E9;
}

.material-form-dialog.el-dialog .el-dialog__body {
  padding: 16px 24px 24px 12px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.material-form-dialog.el-dialog .el-dialog__footer {
  padding: 0;
  border-top: 1px solid #E1E2E9;
}
</style>
