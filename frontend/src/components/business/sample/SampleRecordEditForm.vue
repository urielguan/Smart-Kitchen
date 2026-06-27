<script setup lang="ts">
import { reactive, ref, computed, watch, nextTick } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { useSampleUpload } from '@/composables/useSampleUpload'
import { getImageUrl } from '@/utils'
import type { SampleRecordDetail, SampleRecordUpdatePayload } from '@/types'

interface Props {
  modelValue: boolean
  loading?: boolean
  recordData?: SampleRecordDetail | null
}

const props = withDefaults(defineProps<Props>(), {
  recordData: null
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [payload: SampleRecordUpdatePayload]
}>()

const formRef = ref()

const sampleImagesRef = computed({
  get: () => form.sampleImages!,
  set: (val) => { form.sampleImages = val }
})
const { uploading, handleImageUpload } = useSampleUpload(sampleImagesRef)

/** 追踪已提交的原始附件数量 */
const originalImageCount = ref(0)

/** 删除附件 */
const removeImage = (index: number) => {
  sampleImagesRef.value.splice(index, 1)
}

const form = reactive<SampleRecordUpdatePayload>({
  sampleWeight: null,
  storageLocation: '',
  storageTemp: null,
  sampleImages: []
})

const rules = {
  sampleWeight: [{ required: true, message: '请输入留样重量', trigger: 'blur' }],
  storageLocation: [{ required: true, message: '请输入存放位置', trigger: 'blur' }]
}

/** 弹窗打开时回填表单 */
watch(
  () => props.modelValue,
  async (val) => {
    if (val && props.recordData) {
      await nextTick()
      form.sampleWeight = props.recordData.sampleWeight ?? null
      form.storageLocation = props.recordData.storageLocation ?? ''
      form.storageTemp = props.recordData.storageTemp ?? null
      form.sampleImages = props.recordData.sampleImages ? [...props.recordData.sampleImages] : []
      originalImageCount.value = form.sampleImages?.length ?? 0
    }
  }
)

const handleSubmit = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  emit('submit', { ...form })
}

const handleClose = () => {
  formRef.value?.resetFields()
  form.sampleWeight = null
  form.storageLocation = ''
  form.storageTemp = null
  form.sampleImages = []
  originalImageCount.value = 0
  emit('update:modelValue', false)
}
</script>

<template>
  <el-dialog :model-value="modelValue" title="编辑留样记录" width="560px" destroy-on-close @close="handleClose">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="留样重量(g)" prop="sampleWeight">
        <el-input-number v-model="form.sampleWeight" :min="0.1" :max="5000" :precision="1" placeholder="留样重量" style="width: 100%" />
      </el-form-item>
      <el-form-item label="存放位置" prop="storageLocation">
        <el-input v-model="form.storageLocation" placeholder="请输入存放位置" maxlength="200" />
      </el-form-item>
      <el-form-item label="存放温度(℃)">
        <el-input-number v-model="form.storageTemp" :precision="1" placeholder="存放温度" style="width: 100%" />
      </el-form-item>
      <el-form-item label="留样照片">
        <div class="image-upload-area">
          <div class="image-list">
            <div v-for="(img, index) in form.sampleImages" :key="index" class="image-item">
              <el-image :src="getImageUrl(img)" fit="cover" class="preview-img"
                :preview-src-list="form.sampleImages!.map(getImageUrl)" :initial-index="index" />
              <el-button class="remove-btn" type="danger" :icon="'Close'" circle size="small" @click="removeImage(index)" />
            </div>
            <el-upload
              v-if="(form.sampleImages?.length ?? 0) < 20"
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
          <div class="upload-tip">支持 JPG、PNG、GIF、WebP、MP4、PDF 格式，单文件最大 50MB，最多 20 个</div>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="loading || uploading" @click="handleSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
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
