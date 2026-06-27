<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { MEAL_TYPE_MAP } from '@/constants/sample'
import { getSampleManualTaskOptions } from '@/api/modules/sample'
import { formatDateTime } from '@/utils'
import type { SampleAvailableCookTask, SampleRecordCreatePayload } from '@/types'

interface Props {
  modelValue: boolean
  loading?: boolean
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [payload: SampleRecordCreatePayload]
}>()

const formRef = ref()
const taskOptions = ref<SampleAvailableCookTask[]>([])
const optionsLoading = ref(false)

const form = reactive<SampleRecordCreatePayload>({
  taskId: null
})

const rules = {
  taskId: [{ required: true, message: '请选择关联烹饪任务', trigger: 'change' }]
}

const selectedTask = computed(() => taskOptions.value.find(task => task.id === form.taskId) ?? null)

const loadTaskOptions = async () => {
  optionsLoading.value = true
  try {
    const res = await getSampleManualTaskOptions()
    if (res.code === 'SUCCESS' && res.data) {
      taskOptions.value = res.data
    } else {
      taskOptions.value = []
    }
  } catch {
    taskOptions.value = []
  } finally {
    optionsLoading.value = false
  }
}

const handleSubmit = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || !form.taskId) return
  emit('submit', { taskId: form.taskId })
}

const resetForm = () => {
  formRef.value?.resetFields()
  form.taskId = null
}

const handleClose = () => {
  resetForm()
  taskOptions.value = []
  emit('update:modelValue', false)
}

watch(() => props.modelValue, (value) => {
  if (value) {
    loadTaskOptions()
  } else {
    resetForm()
  }
})
</script>

<template>
  <el-dialog :model-value="modelValue" title="新增留样" width="640px" destroy-on-close @close="handleClose">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" v-loading="optionsLoading">
      <el-form-item label="烹饪任务" prop="taskId">
        <el-select
          v-model="form.taskId"
          filterable
          clearable
          placeholder="请选择当前业务日已完成的烹饪任务"
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

      <div class="form-tip">普通角色仅允许当前业务日已完成烹饪任务的当日补录，所选任务会生成一条待留样任务。</div>

      <template v-if="selectedTask">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="任务编号">
              <el-input :model-value="selectedTask.taskNo" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="菜谱名称">
              <el-input :model-value="selectedTask.menuName" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="留样日期">
              <el-input :model-value="selectedTask.sampleDate" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="餐次">
              <el-input :model-value="MEAL_TYPE_MAP[selectedTask.mealType] || selectedTask.mealType" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="完成时间">
              <el-input :model-value="formatDateTime(selectedTask.completedAt) || '-'" disabled />
            </el-form-item>
          </el-col>
        </el-row>
      </template>

      <el-empty v-else description="请选择当前业务日已完成烹饪任务" :image-size="80" />
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="loading || optionsLoading" @click="handleSubmit">确认新增</el-button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.form-tip {
  margin: -6px 0 16px;
  color: #909399;
  font-size: 13px;
  line-height: 1.5;
}
</style>
