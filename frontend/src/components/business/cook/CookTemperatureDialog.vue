<script setup lang="ts">
import { computed } from 'vue'
import type { CookTaskDetail } from '@/types'

interface Props {
  modelValue: boolean
  task: CookTaskDetail | null
}

const props = defineProps<Props>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})
</script>

<template>
  <el-dialog v-model="visible" title="温度曲线" width="820px" destroy-on-close>
    <template v-if="task">
      <div class="temperature-summary">
        <div>菜品：{{ task.menuName }}</div>
        <div>目标温度：{{ task.targetTempMin || '-' }}℃ ~ {{ task.targetTempMax || '-' }}℃</div>
      </div>

      <div v-if="task.temperatureRecords?.length" class="temperature-list">
        <div
          v-for="(item, index) in task.temperatureRecords"
          :key="index"
          class="temperature-item"
          :class="{ abnormal: item.abnormal }"
        >
          <span>{{ item.recordTime || '-' }}</span>
          <strong>{{ item.temperature ? `${item.temperature}℃` : '-' }}</strong>
          <em>{{ item.remark || (item.abnormal ? '温度异常' : '温度正常') }}</em>
        </div>
      </div>
      <el-empty v-else description="暂无温度采集记录" :image-size="90" />
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.temperature-summary {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
  color: #606266;
}

.temperature-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.temperature-item {
  display: grid;
  grid-template-columns: 1.4fr 0.8fr 1fr;
  gap: 16px;
  align-items: center;
  padding: 14px 16px;
  border-radius: 8px;
  background: #f7f9fc;
  border: 1px solid #ebeef5;

  strong {
    color: #303133;
  }

  em {
    font-style: normal;
    color: #909399;
  }

  &.abnormal {
    border-color: rgba(245, 108, 108, 0.35);
    background: rgba(245, 108, 108, 0.08);

    strong,
    em {
      color: #f56c6c;
    }
  }
}

@media (max-width: 768px) {
  .temperature-summary,
  .temperature-item {
    grid-template-columns: 1fr;
    display: grid;
  }
}
</style>
