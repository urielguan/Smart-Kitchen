<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { MEAL_TYPE_MAP, SAMPLE_RECORD_ORIGIN_OPTIONS } from '@/constants/sample'
import { getSampleHistoryTaskOptions } from '@/api/modules/sample'
import { employeeApi } from '@/api/modules/employee'
import { useSampleUpload } from '@/composables/useSampleUpload'
import { getImageUrl, formatDateTime } from '@/utils'
import type { Employee, SampleAvailableCookTask, SampleHistoryRecordCreatePayload } from '@/types'

interface Props {
  modelValue: boolean
  loading?: boolean
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [payload: SampleHistoryRecordCreatePayload]
}>()

const formRef = ref()
const employees = ref<Employee[]>([])
const taskOptions = ref<SampleAvailableCookTask[]>([])
const taskOptionsLoading = ref(false)

const form = reactive({
  businessDate: '',
  taskId: null as number | null,
  recordOriginType: 'manual_history' as 'manual_history' | 'offline_delayed',
  supplementReason: '',
  supplementRemark: '',
  sampleWeight: null as number | null,
  sampleImages: [] as string[],
  storageLocation: '',
  storageTemp: null as number | null,
  sampledBy: null as number | null
})

const sampleImagesRef = computed({
  get: () => form.sampleImages,
  set: (value) => { form.sampleImages = value }
})

const { uploading, handleImageUpload } = useSampleUpload(sampleImagesRef)

const selectedTask = computed(() => taskOptions.value.find(task => task.id === form.taskId) ?? null)

const rules = {
  businessDate: [{ required: true, message: '请选择历史业务日期', trigger: 'change' }],
  taskId: [{ required: true, message: '请选择关联烹饪任务', trigger: 'change' }],
  recordOriginType: [{ required: true, message: '请选择补录类型', trigger: 'change' }],
  supplementReason: [{ required: true, message: '请输入补录原因', trigger: 'blur' }],
  supplementRemark: [{ required: true, message: '请输入补录备注', trigger: 'blur' }],
  sampleWeight: [{ required: true, message: '请输入留样重量', trigger: 'blur' }],
  storageLocation: [{ required: true, message: '请输入存放位置', trigger: 'blur' }],
  sampledBy: [{ required: true, message: '请选择留样人员', trigger: 'change' }]
}

const disabledDate = (date: Date) => {
  const candidate = new Date(date)
  candidate.setHours(0, 0, 0, 0)
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return candidate.getTime() >= today.getTime()
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

const loadTaskOptions = async () => {
  if (!form.businessDate) {
    taskOptions.value = []
    return
  }
  taskOptionsLoading.value = true
  try {
    const res = await getSampleHistoryTaskOptions({ businessDate: form.businessDate })
    if (res.code === 'SUCCESS' && res.data) {
      taskOptions.value = res.data
    } else {
      taskOptions.value = []
    }
  } catch {
    taskOptions.value = []
  } finally {
    taskOptionsLoading.value = false
  }
}

const resetForm = () => {
  formRef.value?.resetFields()
  form.businessDate = ''
  form.taskId = null
  form.recordOriginType = 'manual_history'
  form.supplementReason = ''
  form.supplementRemark = ''
  form.sampleWeight = null
  form.sampleImages = []
  form.storageLocation = ''
  form.storageTemp = null
  form.sampledBy = null
  taskOptions.value = []
}

const handleClose = () => {
  resetForm()
  emit('update:modelValue', false)
}

const handleSubmit = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || !form.taskId || !form.sampleWeight || !form.sampledBy) return
  emit('submit', {
    taskId: form.taskId,
    recordOriginType: form.recordOriginType,
    supplementReason: form.supplementReason.trim(),
    supplementRemark: form.supplementRemark.trim(),
    sampleWeight: form.sampleWeight,
    sampleImages: form.sampleImages,
    storageLocation: form.storageLocation.trim(),
    storageTemp: form.storageTemp,
    sampledBy: form.sampledBy
  })
}

watch(
  () => form.businessDate,
  async (value, previousValue) => {
    if (value === previousValue) return
    form.taskId = null
    await loadTaskOptions()
  }
)

watch(
  () => props.modelValue,
  async (value) => {
    if (value) {
      await loadEmployees()
    } else {
      resetForm()
    }
  }
)
</script>

<template>
  <el-dialog :model-value="modelValue" title="历史补录" width="760px" destroy-on-close @close="handleClose">
    <el-alert
      title="该入口仅用于跨日漏单、往期漏单和离线迟传修复，必须填写补录原因和备注，操作将单独记入审计日志。"
      type="warning"
      :closable="false"
      style="margin-bottom: 16px"
    />

    <div v-loading="taskOptionsLoading">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="业务日期" prop="businessDate">
            <el-date-picker
              v-model="form.businessDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="请选择历史业务日期"
              style="width: 100%"
              :disabled-date="disabledDate"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="补录类型" prop="recordOriginType">
            <el-select v-model="form.recordOriginType" style="width: 100%">
              <el-option
                v-for="option in SAMPLE_RECORD_ORIGIN_OPTIONS"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="烹饪任务" prop="taskId">
        <el-select
          v-model="form.taskId"
          filterable
          clearable
          placeholder="请选择该业务日已完成且未存在有效留样的烹饪任务"
          style="width: 100%"
        >
          <el-option
            v-for="task in taskOptions"
            :key="task.id"
            :label="`${task.taskNo} - ${task.menuName}`"
            :value="task.id"
          />
        </el-select>
      </el-form-item>

      <template v-if="selectedTask">
        <div class="task-card">
          <div class="task-card__title">关联烹饪任务</div>
          <el-row :gutter="16">
            <el-col :span="12"><div class="task-item"><span>任务编号</span><strong>{{ selectedTask.taskNo || '-' }}</strong></div></el-col>
            <el-col :span="12"><div class="task-item"><span>菜谱名称</span><strong>{{ selectedTask.menuName }}</strong></div></el-col>
            <el-col :span="12"><div class="task-item"><span>业务日期</span><strong>{{ selectedTask.sampleDate }}</strong></div></el-col>
            <el-col :span="12"><div class="task-item"><span>餐次</span><strong>{{ MEAL_TYPE_MAP[selectedTask.mealType] || selectedTask.mealType }}</strong></div></el-col>
            <el-col :span="24"><div class="task-item"><span>完成时间</span><strong>{{ formatDateTime(selectedTask.completedAt) || '-' }}</strong></div></el-col>
          </el-row>
        </div>
      </template>

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
            <el-input v-model="form.storageLocation" maxlength="100" show-word-limit placeholder="请输入存放位置" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="存放温度(℃)">
            <el-input-number v-model="form.storageTemp" :precision="1" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="补录原因" prop="supplementReason">
            <el-input
              v-model="form.supplementReason"
              type="textarea"
              :rows="3"
              maxlength="500"
              show-word-limit
              placeholder="请填写补录原因，如跨日漏单、系统漏单、离线迟传等"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="补录备注" prop="supplementRemark">
            <el-input
              v-model="form.supplementRemark"
              type="textarea"
              :rows="3"
              maxlength="500"
              show-word-limit
              placeholder="请补充异常背景、处理说明或运维修复事由"
            />
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
                :preview-src-list="form.sampleImages.map(getImageUrl)"
                :initial-index="index"
              />
              <el-button class="remove-btn" type="danger" :icon="'Close'" circle size="small" @click="removeImage(index)" />
            </div>
            <el-upload
              v-if="form.sampleImages.length < 20"
              action="#"
              :show-file-list="false"
              :before-upload="handleImageUpload"
              accept="image/*,video/mp4,.pdf"
            >
              <div class="image-placeholder">
                <el-icon><Plus /></el-icon>
                <span>上传附件</span>
              </div>
            </el-upload>
          </div>
          <div class="upload-tip">支持 JPG、PNG、GIF、WebP、MP4、PDF 格式，单文件最大 50MB，最多 20 个</div>
        </div>
      </el-form-item>
    </el-form>
    </div>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="loading || uploading || taskOptionsLoading" @click="handleSubmit">确认补录</el-button>
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
  display: flex;
  width: 100px;
  height: 100px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: 1px dashed #c0c4cc;
  border-radius: 4px;
  color: #909399;
  background: #fafafa;
}

.upload-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}
</style>
