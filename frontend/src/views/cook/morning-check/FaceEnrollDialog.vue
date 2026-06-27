<template>
  <el-dialog
    v-model="dialogVisible"
    title="人脸录入"
    width="480px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div class="face-enroll-page">
      <!-- 步骤指示 -->
      <el-steps :active="currentStep" simple finish-status="success">
        <el-step title="选择员工" />
        <el-step title="拍照录入" />
        <el-step title="确认完成" />
      </el-steps>

      <!-- Step 1: 选择员工 -->
      <div v-if="currentStep === 0" class="step-content">
        <el-form label-width="80px">
          <el-form-item label="员工">
            <el-tooltip :visible="showEmpTip" content="员工搜索最多输入50个字符" placement="top">
              <el-select
              ref="employeeSelectRef"
              v-model="selectedEmployeeId"
              placeholder="搜索员工姓名、工号或手机号"
              filterable
              remote
              :remote-method="handleEmployeeSearch"
              :loading="searching"
              style="width: 100%"
            >
              <el-option
                v-for="emp in employeeOptions"
                :key="emp.id"
                :label="`${emp.employeeName} (${emp.employeeCode})`"
                :value="emp.id"
              >
                <div class="employee-option">
                  <span class="name">{{ emp.employeeName }}</span>
                  <span class="code">{{ emp.employeeCode }}</span>
                  <span v-if="emp.phone" class="phone">{{ emp.phone }}</span>
                  <el-tag v-if="emp.faceEnrolled" type="success" size="small">已录入</el-tag>
                  <el-tag v-else type="info" size="small">未录入</el-tag>
                </div>
              </el-option>
            </el-select>
            </el-tooltip>
          </el-form-item>
        </el-form>
      </div>

      <!-- Step 2: 拍照录入 -->
      <div v-if="currentStep === 1" class="step-content">
        <div class="selected-employee">
          <el-tag type="info">{{ selectedEmployeeName }}</el-tag>
        </div>

        <FaceCamera
          ref="faceCameraRef"
          :show-guide="true"
          :mirrored="true"
          @capture="handleCapture"
          @error="handleCameraError"
        />

        <!-- 质量提示 -->
        <div class="quality-tips">
          <el-alert
            title="拍照提示"
            type="info"
            :closable="false"
            show-icon
          >
            <ul>
              <li>确保光线充足，避免逆光</li>
              <li>正对摄像头，保持面部完整可见</li>
              <li>摘掉帽子、口罩等遮挡物</li>
            </ul>
          </el-alert>
        </div>
      </div>

      <!-- Step 3: 确认完成 -->
      <div v-if="currentStep === 2" class="step-content">
        <el-result
          :icon="enrollSuccess ? 'success' : 'error'"
          :title="enrollSuccess ? '人脸录入成功' : '人脸录入失败'"
          :sub-title="enrollMessage"
        >
          <template #extra>
            <div v-if="enrollSuccess" class="enroll-result">
              <div class="quality-score">
                <span>照片质量评分:</span>
                <el-progress
                  :percentage="enrollQuality"
                  :color="enrollQuality >= 80 ? '#67c23a' : enrollQuality >= 60 ? '#e6a23c' : '#f56c6c'"
                  :stroke-width="20"
                  :text-inside="true"
                />
              </div>
              <el-button type="primary" @click="handleEnrollAnother">
                继续录入其他员工
              </el-button>
            </div>
            <el-button v-else type="primary" @click="currentStep = 1">
              重新拍照
            </el-button>
          </template>
        </el-result>
      </div>
    </div>

    <template #footer>
      <el-button @click="handleClose">{{ currentStep === 2 ? '关闭' : '取消' }}</el-button>
      <el-button
        v-if="currentStep === 0 && selectedEmployeeId"
        type="primary"
        @click="currentStep = 1"
      >
        下一步
      </el-button>
      <el-button
        v-if="currentStep === 1"
        type="primary"
        :loading="enrolling"
        @click="submitEnroll"
      >
        确认录入
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, watch } from 'vue'
import { ElMessage } from 'element-plus'
import FaceCamera from '@/components/business/face/FaceCamera.vue'
import { enrollFace } from '@/api/modules/face'
import { employeeApi } from '@/api/modules/employee'

// Use employeeApi from the import

interface Employee {
  id: number
  employeeName: string
  employeeCode: string
  phone: string
  faceEnrolled: boolean
}

interface Props {
  visible: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'success'): void
}>()

// 双向绑定 visible
const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
})

// 步骤
const currentStep = ref(0)

// 员工选择
const employeeSelectRef = ref<any>()
const selectedEmployeeId = ref<number>()
const searching = ref(false)
const employeeOptions = ref<Employee[]>([])

/** 限制搜索输入50字符 */
const showEmpTip = ref(false)
let empTipTimer: ReturnType<typeof setTimeout> | null = null
const triggerEmpTip = () => {
  if (empTipTimer) clearTimeout(empTipTimer)
  showEmpTip.value = false
  requestAnimationFrame(() => { showEmpTip.value = true })
  empTipTimer = setTimeout(() => { showEmpTip.value = false }, 2000)
}
const handleEmployeeSearch = (query: string) => {
  if (query.length >= 50) {
    triggerEmpTip()
    nextTick(() => {
      const input = employeeSelectRef.value?.$el?.querySelector('.el-select__input') as HTMLInputElement
      if (input) input.value = query.slice(0, 50)
    })
    query = query.slice(0, 50)
  } else {
    showEmpTip.value = false
  }
  searchEmployees(query)
}

/** 对话框打开后给内部 input 添加 maxlength */
watch(() => props.visible, (val) => {
  if (val) {
    nextTick(() => {
      const input = employeeSelectRef.value?.$el?.querySelector('.el-select__input') as HTMLInputElement
      if (input) input.setAttribute('maxlength', '50')
    })
  }
})
// 人脸录入
const faceCameraRef = ref<InstanceType<typeof FaceCamera>>()
const capturedBase64 = ref('')
const enrolling = ref(false)
const enrollSuccess = ref(false)
const enrollMessage = ref('')
const enrollQuality = ref(0)

const selectedEmployeeName = computed(() => {
  const emp = employeeOptions.value.find(e => e.id === selectedEmployeeId.value)
  return emp?.employeeName || ''
})

// 搜索员工（对接真实API）
async function searchEmployees(query: string) {
  if (!query) {
    employeeOptions.value = []
    return
  }

  searching.value = true
  try {
    const res = await employeeApi.getList({ keyword: query, pageNum: 1, pageSize: 20, status: 'active', accountStatus: 'active' })
    if (res.code === 'SUCCESS' && res.data?.list) {
      employeeOptions.value = res.data.list.map((emp: any) => ({
        id: emp.id,
        employeeName: emp.realName,
        employeeCode: emp.employeeNo || '',
        phone: emp.phone || '',
        faceEnrolled: emp.faceEnrolled || false,
      }))
    } else {
      employeeOptions.value = []
    }
  } catch {
    employeeOptions.value = []
  } finally {
    searching.value = false
  }
}

// 拍照回调
function handleCapture(base64: string) {
  capturedBase64.value = base64
}

// 摄像头错误
function handleCameraError(message: string) {
  ElMessage.error(message)
}

// 提交录入
async function submitEnroll() {
  if (!selectedEmployeeId.value) {
    ElMessage.warning('请先选择员工')
    return
  }

  if (!capturedBase64.value) {
    // 自动拍照
    faceCameraRef.value?.capturePhoto()
    if (!capturedBase64.value) {
      ElMessage.warning('请先拍照')
      return
    }
  }

  enrolling.value = true

  try {
    const res = await enrollFace({
      employeeId: selectedEmployeeId.value,
      employeeName: selectedEmployeeName.value,
      faceImageBase64: capturedBase64.value,
      source: 'web'
    })

    if (res.code === 'SUCCESS') {
      enrollSuccess.value = true
      enrollMessage.value = `${selectedEmployeeName.value} 的人脸特征已成功录入系统`
      enrollQuality.value = res.data?.qualityScore ?? 0
      currentStep.value = 2
      emit('success')
    } else {
      enrollSuccess.value = false
      enrollMessage.value = res.message || '录入失败，请重试'
      currentStep.value = 2
    }
  } catch (error: any) {
    enrollSuccess.value = false
    enrollMessage.value = error.message || '录入请求失败'
    currentStep.value = 2
  } finally {
    enrolling.value = false
  }
}

// 继续录入
function handleEnrollAnother() {
  currentStep.value = 0
  selectedEmployeeId.value = undefined
  capturedBase64.value = ''
  enrollSuccess.value = false
  enrollMessage.value = ''
  enrollQuality.value = 0
}

// 关闭弹窗
function handleClose() {
  emit('update:visible', false)
  currentStep.value = 0
  selectedEmployeeId.value = undefined
  capturedBase64.value = ''
}
</script>

<style lang="scss" scoped>
.face-enroll-page {
  .step-content {
    margin-top: 24px;
    min-height: 300px;
  }

  .selected-employee {
    display: flex;
    justify-content: center;
    margin-bottom: 16px;
  }

  .employee-option {
    display: flex;
    align-items: center;
    gap: 8px;

    .name {
      font-weight: 500;
    }

    .code {
      color: #909399;
      font-size: 12px;
    }

    .phone {
      color: #909399;
      font-size: 12px;
      margin-left: 8px;
    }
  }

  .quality-tips {
    margin-top: 16px;

    ul {
      margin: 0;
      padding-left: 16px;
      font-size: 13px;
      line-height: 1.8;
    }
  }

  .enroll-result {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 16px;

    .quality-score {
      width: 200px;

      span {
        display: block;
        margin-bottom: 8px;
        font-size: 14px;
        color: #606266;
      }
    }
  }
}
</style>
