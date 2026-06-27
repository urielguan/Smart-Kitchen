<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'

interface Props {
  modelValue: boolean
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
  loading: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  approve: [remark?: string]
  reject: [remark: string]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const form = reactive({
  approveRemark: '',
  rejectRemark: '',
})

watch(() => props.modelValue, (value) => {
  if (value) {
    form.approveRemark = ''
    form.rejectRemark = ''
  }
})

const handleApprove = () => {
  emit('approve', form.approveRemark || undefined)
}

const handleReject = () => {
  if (!form.rejectRemark.trim()) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  emit('reject', form.rejectRemark.trim())
}
</script>

<template>
  <el-dialog v-model="visible" title="审核盘点单" width="520px" append-to-body>
    <el-form label-width="88px">
      <el-form-item label="通过备注">
        <el-input
          v-model="form.approveRemark"
          type="textarea"
          :rows="3"
          maxlength="300"
          show-word-limit
          placeholder="选填，审核通过时可填写备注"
        />
      </el-form-item>
      <el-form-item label="驳回原因" required>
        <el-input
          v-model="form.rejectRemark"
          type="textarea"
          :rows="3"
          maxlength="300"
          show-word-limit
          placeholder="驳回时必填"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="danger" :loading="loading" @click="handleReject">驳回</el-button>
      <el-button type="success" :loading="loading" @click="handleApprove">通过</el-button>
    </template>
  </el-dialog>
</template>
