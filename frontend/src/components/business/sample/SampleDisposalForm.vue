<script setup lang="ts">
import { reactive, ref, computed } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { useSampleUpload } from '@/composables/useSampleUpload'
import { getImageUrl } from '@/utils'
import type { DisposalPayload } from '@/types'

interface Props {
  modelValue: boolean
  loading?: boolean
}

defineProps<Props>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [payload: DisposalPayload]
}>()

const formRef = ref()

const disposalImagesRef = computed({
  get: () => form.disposalImages,
  set: (val) => { form.disposalImages = val }
})
const { uploading, handleImageUpload, removeImage } = useSampleUpload(disposalImagesRef)

const form = reactive<DisposalPayload>({
  disposalImages: [],
  disposalRemark: ''
})

const rules = {
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

const handleSubmit = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  emit('submit', { ...form })
}

const handleClose = () => {
  formRef.value?.resetFields()
  form.disposalImages = []
  form.disposalRemark = ''
  emit('update:modelValue', false)
}
</script>

<template>
  <el-dialog :model-value="modelValue" title="执行销样" width="560px" destroy-on-close @close="handleClose">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="销样照片" prop="disposalImages">
        <div class="image-upload-area">
          <div class="image-list">
            <div v-for="(img, index) in form.disposalImages" :key="index" class="image-item">
              <el-image :src="getImageUrl(img)" fit="cover" class="preview-img"
                :preview-src-list="form.disposalImages.map(getImageUrl)" :initial-index="index" />
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
        <el-input v-model="form.disposalRemark" type="textarea" :rows="3" placeholder="请输入销样备注（可选）" maxlength="500" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="loading || uploading" @click="handleSubmit">确认销样</el-button>
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
