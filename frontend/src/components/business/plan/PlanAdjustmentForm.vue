<template>
  <PlanForm
    v-model="visible"
    mode="adjustment"
    :edit-data="plan"
    @submit="handleSubmit"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import type { RecipePlanDetail, RecipePlanFormSubmitPayload } from '@/types/plan'
import { createPlanAdjustment } from '@/api/modules/plan-adjustment'
import PlanForm from './PlanForm.vue'

const props = defineProps<{
  modelValue: boolean
  plan: RecipePlanDetail | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'success': []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const handleSubmit = async (payload: RecipePlanFormSubmitPayload, done?: () => void) => {
  if (!props.plan) {
    done?.()
    return
  }

  try {
    await createPlanAdjustment(props.plan.id, {
      adjustReason: payload.adjustReason || '',
      adjustType: payload.adjustType || 'modify',
      afterData: payload.afterData || '{}'
    })
    ElMessage.success('调整申请已提交')
    visible.value = false
    emit('success')
  } catch (error: any) {
    ElMessage.error(error.message || '提交失败')
  } finally {
    done?.()
  }
}
</script>
