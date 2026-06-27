<template>
  <el-dialog
    v-model="dialogVisible"
    title="手动晨检"
    width="560px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div class="health-check-form">
      <el-form ref="formRef" :model="formData" :rules="rules" label-width="100px">
        <el-form-item label="员工" prop="employeeId">
          <el-select v-model="formData.employeeId" placeholder="选择员工" filterable @change="handleEmployeeChange">
            <el-option
              v-for="item in pendingList"
              :key="item.employeeId"
              :label="item.employeeName"
              :value="item.employeeId"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <el-form v-if="selectedEmployee" ref="checkFormRef" :model="checkData" :rules="checkRules" label-width="100px" class="check-form">
        <el-divider content-position="left">
          <el-icon><User /></el-icon>
          {{ selectedEmployee.employeeName }} 的晨检
        </el-divider>

        <el-form-item label="体温" prop="temperature">
          <el-input-number
            v-model="checkData.temperature"
            :min="35"
            :max="42"
            :precision="1"
            :step="0.1"
            placeholder="请输入体温"
          />
          <span class="unit">℃</span>
          <el-tag v-if="tempStatus" :type="tempStatusType" size="small" class="temp-tag">
            {{ tempStatusText }}
          </el-tag>
        </el-form-item>

        <el-form-item label="手部卫生" prop="handHygiene">
          <el-radio-group v-model="checkData.handHygiene">
            <el-radio value="pass">
              <el-tag type="success">通过</el-tag>
            </el-radio>
            <el-radio value="fail">
              <el-tag type="danger">不通过</el-tag>
            </el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="着装检查" prop="uniformCheck">
          <el-radio-group v-model="checkData.uniformCheck">
            <el-radio value="pass">
              <el-tag type="success">通过</el-tag>
            </el-radio>
            <el-radio value="fail">
              <el-tag type="danger">不通过</el-tag>
            </el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="健康证状态">
          <el-tag :type="certStatusType">
            {{ certStatusText }}
          </el-tag>
          <span v-if="selectedEmployee.certificateExpiryDate" class="cert-expiry">
            有效期至: {{ selectedEmployee.certificateExpiryDate }}
          </span>
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="checkData.remark" type="textarea" :rows="2" placeholder="备注信息" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button v-if="selectedEmployee" type="primary" :loading="submitting" @click="handleSubmit">
        提交晨检
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { User } from '@element-plus/icons-vue'
import { createHealthCheckRecord } from '@/api/modules/health-check'
import type { HealthCheckRecord } from '@/types/health-check'

interface Props {
  visible: boolean
  employee?: HealthCheckRecord | null
  pendingList: HealthCheckRecord[]
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  employee: null,
  pendingList: () => []
})

const emit = defineEmits<{
  (e: 'update:visible', visible: boolean): void
  (e: 'success'): void
}>()

// 双向绑定 visible
const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
})

// refs
const formRef = ref()
const checkFormRef = ref()

// 状态
const submitting = ref(false)

// 员工选择
const selectedEmployee = ref<HealthCheckRecord | null>(null)

// 表单数据
const formData = ref({
  employeeId: null as number | null
})

const checkData = ref({
  temperature: 36.5,
  handHygiene: 'pass',
  uniformCheck: 'pass',
  remark: ''
})

// 表单校验
const rules = {
  employeeId: [{ required: true, message: '请选择员工', trigger: 'change' }]
}

const checkRules = {
  temperature: [{ required: true, message: '请输入体温', trigger: 'blur' }],
  handHygiene: [{ required: true, message: '请检查手部卫生', trigger: 'change' }],
  uniformCheck: [{ required: true, message: '请检查着装', trigger: 'change' }]
}

// 计算属性
const tempStatus = computed(() => {
  const temp = checkData.value.temperature
  if (temp < 36.0) return 'low'
  if (temp >= 37.3) return 'high'
  return 'normal'
})

const tempStatusType = computed(() => {
  const types: Record<string, string> = {
    low: 'warning',
    normal: 'success',
    high: 'danger'
  }
  return types[tempStatus.value]
})

const tempStatusText = computed(() => {
  const texts: Record<string, string> = {
    low: '体温偏低',
    normal: '体温正常',
    high: '体温异常'
  }
  return texts[tempStatus.value]
})

const certStatusType = computed(() => {
  const status = selectedEmployee.value?.certificateStatus
  const types: Record<string, string> = {
    valid: 'success',
    expiring: 'warning',
    expired: 'danger',
    pending: 'info'
  }
  return types[status || 'pending']
})

const certStatusText = computed(() => {
  const status = selectedEmployee.value?.certificateStatus
  const texts: Record<string, string> = {
    valid: '有效',
    expiring: '即将过期',
    expired: '已过期',
    pending: '未办理'
  }
  return texts[status || 'pending']
})

// 监听visible变化，初始化
watch(() => props.visible, (val) => {
  if (val) {
    resetForm()
    if (props.employee) {
      selectedEmployee.value = props.employee
      formData.value.employeeId = props.employee.employeeId
    }
  }
})

// 员工选择变化
function handleEmployeeChange(employeeId: number) {
  const employee = props.pendingList.find(e => e.employeeId === employeeId)
  selectedEmployee.value = employee || null
}

// 提交晨检
async function handleSubmit() {
  if (!selectedEmployee.value) {
    ElMessage.warning('请先选择员工')
    return
  }

  // 表单校验
  try {
    await formRef.value?.validate()
    await checkFormRef.value?.validate()
  } catch {
    return
  }

  submitting.value = true

  try {
    const payload = {
      employeeId: selectedEmployee.value.employeeId,
      employeeName: selectedEmployee.value.employeeName,
      checkDate: new Date().toISOString().split('T')[0],
      temperature: checkData.value.temperature,
      handHygiene: checkData.value.handHygiene,
      uniformCheck: checkData.value.uniformCheck,
      remark: checkData.value.remark,
      orgId: selectedEmployee.value.orgId,
    }

    const res = await createHealthCheckRecord(payload)

    if (res.code === 'SUCCESS') {
      ElMessage.success('晨检提交成功')
      emit('success')
      handleClose()
    } else {
      ElMessage.error(res.message || '提交失败')
    }
  } catch (error: any) {
    ElMessage.error(error.message || '提交失败')
  } finally {
    submitting.value = false
  }
}

// 重置表单
function resetForm() {
  selectedEmployee.value = null
  formData.value = { employeeId: null }
  checkData.value = {
    temperature: 36.5,
    handHygiene: 'pass',
    uniformCheck: 'pass',
    remark: ''
  }
}

// 关闭弹窗
function handleClose() {
  emit('update:visible', false)
  resetForm()
}

</script>

<style lang="scss" scoped>
.health-check-form {
  .el-form {
    width: 100%;
  }

  .check-form {
    margin-top: 20px;

    .unit {
      margin-left: 8px;
      color: #909399;
    }

    .temp-tag {
      margin-left: 12px;
    }

    .cert-expiry {
      margin-left: 12px;
      color: #909399;
      font-size: 13px;
    }
  }
}

.el-divider__text {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
