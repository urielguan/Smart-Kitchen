<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { MEAL_TYPE_MAP, SAMPLE_STATUS_MAP } from '@/constants/sample'
import { useSampleUpload } from '@/composables/useSampleUpload'
import { employeeApi } from '@/api/modules/employee'
import { getImageUrl } from '@/utils'
import type { Employee, SampleRecordDetail, SampleRecordRegisterPayload } from '@/types'

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
  submit: [payload: SampleRecordRegisterPayload]
}>()

const formRef = ref()
const employees = ref<Employee[]>([])

const form = reactive<SampleRecordRegisterPayload>({
  sampleWeight: null,
  sampleImages: [],
  storageLocation: '',
  storageTemp: null,
  sampledBy: null
})

const sampleImagesRef = computed({
  get: () => form.sampleImages!,
  set: (value) => { form.sampleImages = value }
})

const { uploading, handleImageUpload } = useSampleUpload(sampleImagesRef)

const rules = {
  sampleWeight: [{ required: true, message: '请输入留样重量', trigger: 'blur' }],
  storageLocation: [{ required: true, message: '请输入存放位置', trigger: 'blur' }],
  sampledBy: [{ required: true, message: '请选择留样人员', trigger: 'change' }]
}

const removeImage = (index: number) => {
  sampleImagesRef.value.splice(index, 1)
}

const loadEmployees = async () => {
  try {
    const res = await employeeApi.getList({ pageSize: 200, status: 'active', accountStatus: 'active' })
    if (res.code === 'SUCCESS' && res.data) {
      employees.value = res.data.list ?? []
    } else {
      employees.value = []
    }
  } catch {
    employees.value = []
  }
}

const fillForm = async () => {
  if (!props.recordData) return
  await nextTick()
  form.sampleWeight = props.recordData.sampleWeight ?? null
  form.sampleImages = props.recordData.sampleImages ? [...props.recordData.sampleImages] : []
  form.storageLocation = props.recordData.storageLocation ?? ''
  form.storageTemp = props.recordData.storageTemp ?? null
  form.sampledBy = props.recordData.sampledBy ?? null
}

const resetForm = () => {
  formRef.value?.resetFields()
  form.sampleWeight = null
  form.sampleImages = []
  form.storageLocation = ''
  form.storageTemp = null
  form.sampledBy = null
}

const handleSubmit = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  emit('submit', { ...form })
}

const handleClose = () => {
  resetForm()
  emit('update:modelValue', false)
}

watch(
  () => props.modelValue,
  async (value) => {
    if (value) {
      await Promise.all([loadEmployees(), fillForm()])
    } else {
      resetForm()
    }
  }
)
</script>

<template>
  <el-dialog :model-value="modelValue" title="留样登记" width="720px" destroy-on-close @close="handleClose">
    <template v-if="recordData">
      <div class="task-card">
        <div class="task-card__title">关联烹饪任务</div>
        <el-row :gutter="16">
          <el-col :span="12"><div class="task-item"><span>任务编号</span><strong>{{ recordData.taskNo || '-' }}</strong></div></el-col>
          <el-col :span="12"><div class="task-item"><span>菜谱名称</span><strong>{{ recordData.menuName }}</strong></div></el-col>
          <el-col :span="12"><div class="task-item"><span>留样日期</span><strong>{{ recordData.sampleDate }}</strong></div></el-col>
          <el-col :span="12"><div class="task-item"><span>餐次</span><strong>{{ MEAL_TYPE_MAP[recordData.mealType] || recordData.mealType }}</strong></div></el-col>
          <el-col :span="12"><div class="task-item"><span>来源</span><strong>{{ recordData.sourceLabel || '-' }}</strong></div></el-col>
          <el-col :span="12"><div class="task-item"><span>当前状态</span><strong>{{ SAMPLE_STATUS_MAP[recordData.status]?.label || recordData.status || '-' }}</strong></div></el-col>
        </el-row>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="留样人员" prop="sampledBy">
              <el-select
                v-model="form.sampledBy"
                filterable
                clearable
                placeholder="请选择留样人员"
                style="width: 100%"
              >
                <el-option
                  v-for="employee in employees"
                  :key="employee.id"
                  :label="employee.realName"
                  :value="employee.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="留样重量(g)" prop="sampleWeight">
              <el-input-number v-model="form.sampleWeight" :min="0.1" :max="5000" :precision="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="存放位置" prop="storageLocation">
              <el-input v-model="form.storageLocation" placeholder="请输入存放位置" maxlength="100" :show-word-limit="true" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="存放温度(℃)">
              <el-input-number v-model="form.storageTemp" :precision="1" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="留样照片">
          <div class="image-upload-area">
            <div class="image-list">
              <div v-for="(img, index) in form.sampleImages" :key="index" class="image-item">
                <el-image
                  :src="getImageUrl(img)"
                  fit="cover"
                  class="preview-img"
                  :preview-src-list="form.sampleImages!.map(getImageUrl)"
                  :initial-index="index"
                />
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
    </template>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="loading || uploading" @click="handleSubmit">确认登记</el-button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.task-card {
  margin-bottom: 16px;
  padding: 16px;
  background: #f8fafc;
  border: 1px solid #ebeef5;
  border-radius: 8px;
}

.task-card__title {
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.task-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 0;

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
