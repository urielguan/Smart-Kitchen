<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { MEAL_TYPE_MAP, SAMPLE_STATUS_MAP, SAMPLE_MANUAL_DISPOSAL_SCENE_OPTIONS } from '@/constants/sample'
import { useSampleUpload } from '@/composables/useSampleUpload'
import { getImageUrl } from '@/utils'
import type { ManualDisposalSupplementPayload, SampleRecordDetail } from '@/types'

interface Props {
  modelValue: boolean
  loading?: boolean
  recordData?: SampleRecordDetail | null
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [payload: ManualDisposalSupplementPayload]
}>()

const formRef = ref()

const form = reactive<ManualDisposalSupplementPayload>({
  supplementScene: 'system_missing',
  supplementRemark: '',
  disposalImages: [],
  disposalRemark: ''
})

const disposalImagesRef = computed({
  get: () => form.disposalImages,
  set: (value) => { form.disposalImages = value }
})

const { uploading, handleImageUpload, removeImage } = useSampleUpload(disposalImagesRef)

const rules = {
  supplementScene: [{ required: true, message: '请选择补录场景', trigger: 'change' }],
  supplementRemark: [
    { required: true, message: '请填写补录备注', trigger: 'blur' },
    { min: 5, max: 200, message: '补录备注长度需在 5 到 200 个字符之间', trigger: 'blur' }
  ],
  disposalImages: [{
    validator: (_rule: any, value: string[], callback: any) => {
      if (!value || value.length === 0) {
        callback(new Error('请至少上传一张销样照片'))
      } else {
        callback()
      }
    },
    trigger: 'change'
  }]
}

const resetForm = () => {
  formRef.value?.resetFields()
  form.supplementScene = 'system_missing'
  form.supplementRemark = ''
  form.disposalImages = []
  form.disposalRemark = ''
}

const handleClose = () => {
  resetForm()
  emit('update:modelValue', false)
}

const handleSubmit = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  emit('submit', {
    supplementScene: form.supplementScene,
    supplementRemark: form.supplementRemark.trim(),
    disposalImages: [...form.disposalImages],
    disposalRemark: form.disposalRemark?.trim() || ''
  })
}

watch(
  () => props.modelValue,
  (value) => {
    if (!value) {
      resetForm()
    }
  }
)
</script>

<template>
  <el-dialog :model-value="modelValue" title="销样手工补录" width="720px" destroy-on-close @close="handleClose">
    <el-alert
      title="该入口仅用于系统漏单、接口异常、设备离线、历史迁移纠错、运维补闭环等异常兜底场景；禁止替代日常正常销样流程。"
      type="warning"
      :closable="false"
      style="margin-bottom: 16px"
    />

    <div v-if="recordData" class="record-summary">
      <div class="summary-title">关联有效留样任务</div>
      <el-row :gutter="16">
        <el-col :span="12"><div class="summary-item"><span>留样编号</span><strong>{{ recordData.sampleNo }}</strong></div></el-col>
        <el-col :span="12"><div class="summary-item"><span>烹饪任务</span><strong>{{ recordData.taskNo || '-' }}</strong></div></el-col>
        <el-col :span="12"><div class="summary-item"><span>菜谱名称</span><strong>{{ recordData.menuName }}</strong></div></el-col>
        <el-col :span="12"><div class="summary-item"><span>留样日期</span><strong>{{ recordData.sampleDate }}</strong></div></el-col>
        <el-col :span="12"><div class="summary-item"><span>餐次</span><strong>{{ MEAL_TYPE_MAP[recordData.mealType] || recordData.mealType || '-' }}</strong></div></el-col>
        <el-col :span="12"><div class="summary-item"><span>当前状态</span><strong>{{ SAMPLE_STATUS_MAP[recordData.status]?.label || recordData.status || '-' }}</strong></div></el-col>
      </el-row>
    </div>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
      <el-form-item label="补录场景" prop="supplementScene">
        <el-select v-model="form.supplementScene" style="width: 100%">
          <el-option
            v-for="option in SAMPLE_MANUAL_DISPOSAL_SCENE_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="补录备注" prop="supplementRemark">
        <el-input
          v-model="form.supplementRemark"
          type="textarea"
          :rows="3"
          maxlength="200"
          show-word-limit
          placeholder="请填写补录依据、异常原因和排查结论"
        />
      </el-form-item>

      <el-form-item label="销样照片" prop="disposalImages">
        <div class="image-upload-area">
          <div class="image-list">
            <div v-for="(img, index) in form.disposalImages" :key="index" class="image-item">
              <el-image
                :src="getImageUrl(img)"
                fit="cover"
                class="preview-img"
                :preview-src-list="form.disposalImages.map(getImageUrl)"
                :initial-index="index"
              />
              <el-button class="remove-btn" type="danger" :icon="'Close'" circle size="small" @click="removeImage(index)" />
            </div>
            <el-upload
              v-if="form.disposalImages.length < 20"
              action="#"
              :show-file-list="false"
              :before-upload="handleImageUpload"
              accept="image/*,video/mp4,.pdf"
            >
              <div class="image-placeholder">
                <el-icon><Plus /></el-icon>
                <span>上传照片</span>
              </div>
            </el-upload>
          </div>
          <div class="upload-tip">请至少上传一张销样照片，最多 20 个附件，单文件最大 50MB</div>
        </div>
      </el-form-item>

      <el-form-item label="销样备注">
        <el-input
          v-model="form.disposalRemark"
          type="textarea"
          :rows="3"
          maxlength="300"
          show-word-limit
          placeholder="请输入销样备注（可选）"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="loading || uploading" @click="handleSubmit">确认补录</el-button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.record-summary {
  margin-bottom: 16px;
  padding: 16px;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.summary-title {
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.summary-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  font-size: 13px;

  span {
    color: #909399;
  }

  strong {
    color: #303133;
    text-align: right;
  }
}

.image-upload-area {
  width: 100%;
}

.image-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.image-item {
  position: relative;
  width: 100px;
  height: 100px;
}

.preview-img {
  width: 100px;
  height: 100px;
  border-radius: 4px;
  border: 1px solid #dcdfe6;
}

.remove-btn {
  position: absolute;
  top: -6px;
  right: -6px;
  width: 20px;
  height: 20px;
}

.image-placeholder {
  width: 100px;
  height: 100px;
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #8c939d;
  cursor: pointer;
  transition: border-color 0.3s;
  gap: 4px;

  &:hover {
    border-color: #409eff;
  }

  .el-icon {
    font-size: 24px;
  }

  span {
    font-size: 12px;
  }
}

.upload-tip {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}
</style>
