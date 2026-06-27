<script setup lang="ts">
import { ref, watch } from 'vue'
import type { HealthCheckRecord, HealthCheckCreatePayload } from '@/types/health-check'
import { HYGIENE_CHECK_OPTIONS } from '@/constants/health-check'

interface Props {
  visible: boolean
  loading: boolean
  employee?: HealthCheckRecord | null
}

const props = defineProps<Props>()

const emit = defineEmits<{
  (e: 'submit', data: HealthCheckCreatePayload): void
  (e: 'close'): void
}>()

const form = ref<HealthCheckCreatePayload>({
  employeeId: 0,
  employeeName: '',
  checkDate: new Date().toISOString().split('T')[0],
  temperature: undefined,
  faceImageUrl: '',
  faceMatchScore: undefined,
  handHygiene: 'pass',
  uniformCheck: 'pass',
  checkerId: undefined,
  remark: '',
  tenantId: 1,
})

watch(
  () => props.visible,
  (newVal) => {
    if (newVal && props.employee) {
      form.value = {
        employeeId: props.employee.employeeId,
        employeeName: props.employee.employeeName,
        checkDate: props.employee.checkDate,
        temperature: undefined,
        faceImageUrl: '',
        faceMatchScore: undefined,
        handHygiene: 'pass',
        uniformCheck: 'pass',
        checkerId: undefined,
        remark: '',
        tenantId: 1,
      }
    }
  },
  { immediate: true }
)

const handleSubmit = () => {
  if (!form.value.employeeId || !form.value.employeeName) {
    return
  }
  if (!form.value.checkDate) {
    return
  }
  emit('submit', form.value)
}
</script>

<template>
  <el-dialog
    :model-value="props.visible"
    title="执行晨检"
    width="600px"
    :close-on-click-modal="false"
    @close="emit('close')"
  >
    <el-form :model="form" label-width="100px" v-loading="props.loading">
      <el-form-item label="员工姓名" required>
        <el-input v-model="form.employeeName" disabled />
      </el-form-item>
      <el-form-item label="晨检日期" required>
        <el-date-picker
          v-model="form.checkDate"
          type="date"
          value-format="YYYY-MM-DD"
          disabled
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="体温 (℃)">
        <el-input-number
          v-model="form.temperature"
          :min="35"
          :max="42"
          :precision="1"
          :step="0.1"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="人脸照片URL">
        <el-input v-model="form.faceImageUrl" placeholder="请输入人脸照片URL" />
      </el-form-item>
      <el-form-item label="人脸匹配度">
        <el-input-number
          v-model="form.faceMatchScore"
          :min="0"
          :max="100"
          :precision="2"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="手部卫生">
        <el-select v-model="form.handHygiene" style="width: 100%">
          <el-option
            v-for="opt in HYGIENE_CHECK_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="着装检查">
        <el-select v-model="form.uniformCheck" style="width: 100%">
          <el-option
            v-for="opt in HYGIENE_CHECK_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit />
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="emit('close')">取消</el-button>
        <el-button type="primary" :loading="props.loading" @click="handleSubmit">
          提交
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
