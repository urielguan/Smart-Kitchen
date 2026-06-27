<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled, Download } from '@element-plus/icons-vue'
import { deviceApi } from '@/api/modules/device'
import { useDictCategoryStore } from '@/stores/modules/dict-category'
import { DEVICE_TYPES } from '@/constants/device'
import { mergeDictOptions } from '@/utils/dict-category'

interface Props {
  visible: boolean
  loading: boolean
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'update:visible': [val: boolean]
  import: [file: File]
  'download-template': []
}>()

const dictCategoryStore = useDictCategoryStore()
const fileRef = ref<File | null>(null)
const fileList = ref<any[]>([])
const uploadRef = ref<any>()

const deviceTypeOptions = computed(() => mergeDictOptions(
  DEVICE_TYPES.map((item) => ({ label: item.label, value: item.value })),
  dictCategoryStore.getCachedOptions('device_type', true)
))

const deviceTypeTipText = computed(() =>
  deviceTypeOptions.value.map((opt) => `${opt.value}（${opt.label}）`).join('、')
)

watch(() => props.visible, (val) => {
  if (val) {
    dictCategoryStore.fetchOptions('device_type', true)
  } else {
    fileRef.value = null
    fileList.value = []
  }
})

const handleClose = () => {
  emit('update:visible', false)
}

const handleDownloadTemplate = () => {
  deviceApi.downloadTemplate()
}

const handleChange = (file: any) => {
  const fileName = file.name.toLowerCase()
  if (!fileName.endsWith('.xlsx') && !fileName.endsWith('.xls')) {
    ElMessage.warning('请选择 Excel 文件（.xlsx 或 .xls）')
    // 清除无效文件，防止停留在上传区域
    fileList.value = []
    fileRef.value = null
    nextTick(() => {
      uploadRef.value?.clearFiles()
    })
    return false
  }
  if (fileList.value.length >= 1) {
    ElMessage.warning('只能上传一个文件')
    return false
  }
  fileRef.value = file.raw
  fileList.value = [file]
  return false
}

const handleRemove = () => {
  fileRef.value = null
  fileList.value = []
}

const handleExceed = () => {
  ElMessage.warning('只能上传一个文件，请先删除已选择的文件')
}

const handleConfirm = async () => {
  if (!fileRef.value) {
    ElMessage.warning('请先选择要导入的文件')
    return
  }
  emit('import', fileRef.value)
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    title="导入设备"
    width="500px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div class="import-content">
      <div class="template-section">
        <el-button type="primary" link @click="handleDownloadTemplate">
          <el-icon><Download /></el-icon>
          下载导入模板
        </el-button>
        <span class="template-hint">下载模板并按要求填写设备数据</span>
      </div>

      <el-divider />

      <el-upload
        ref="uploadRef"
        class="upload-area"
        drag
        :auto-upload="false"
        :limit="1"
        :file-list="fileList"
        accept=".xlsx,.xls"
        :on-change="handleChange"
        :on-remove="handleRemove"
        :on-exceed="handleExceed"
      >
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">将 Excel 文件拖到此处，或 <em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">仅支持 .xlsx 或 .xls 格式的 Excel 文件</div>
        </template>
      </el-upload>

      <div class="import-tips">
        <h4>导入说明：</h4>
        <ul>
          <li>模板中带 * 为必填字段（设备编码、设备名称、设备类型），请勿修改表头</li>
          <li>设备编码（必填）在当前租户内必须唯一，编码重复的行将标记为失败</li>
          <li>设备类型（必填）可选值：{{ deviceTypeTipText }}，必须为已启用的字典项</li>
          <li>其余字段均为选填：设备型号、厂商、序列号、MAC地址、IP地址、安装位置、负责人姓名/电话、维保周期、安装日期、保修到期、下次维保、备注</li>
          <li>同一文件中不允许出现重复的设备编码，重复行将标记为失败</li>
          <li>导入为纯新增模式，已存在的设备编码不会更新，该行将标记为失败</li>
          <li>日期字段格式为 yyyy-MM-dd（如 2026-01-01），维保周期单位为天</li>
          <li>导入完成后会返回成功数、失败数和失败原因，并支持下载错误文件</li>
        </ul>
      </div>
    </div>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :disabled="!fileRef" :loading="loading" @click="handleConfirm">确认导入</el-button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.import-content {
  .template-section {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 20px;
    .template-hint { color: #909399; font-size: 13px; }
  }
  .upload-area {
    width: 100%;
    :deep(.el-upload-dragger) { width: 100%; }
  }
  .import-tips {
    margin-top: 16px;
    padding: 12px;
    background: #f5f7fa;
    border-radius: 4px;
    h4 { margin: 0 0 8px 0; font-size: 13px; color: #606266; }
    ul { margin: 0; padding-left: 20px; li { font-size: 12px; color: #909399; line-height: 1.8; } }
  }
}
</style>
