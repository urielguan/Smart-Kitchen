<script setup lang="ts">
import type { OutboundImportTask } from '@/types/outbound'

interface Props {
  task: OutboundImportTask | null
  loading?: boolean
}

const props = defineProps<Props>()
const emit = defineEmits<{
  resume: []
  terminate: []
  downloadErrorFile: [fileName: string]
}>()

const handleDownload = () => {
  if (!props.task?.errorFileName) return
  emit('downloadErrorFile', props.task.errorFileName)
}
</script>

<template>
  <div v-if="task" class="outbound-import-task-drawer">
    <div class="outbound-import-task-drawer__header">
      <div>
        <div class="outbound-import-task-drawer__title">导入任务</div>
        <div class="outbound-import-task-drawer__meta">任务号：{{ task.taskNo }}</div>
      </div>
      <el-tag type="warning">{{ task.taskStatus }}</el-tag>
    </div>

    <div class="outbound-import-task-drawer__summary">
      <span>总数 {{ task.totalCount }}</span>
      <span>成功 {{ task.successCount }}</span>
      <span>失败 {{ task.failureCount }}</span>
    </div>

    <div class="outbound-import-task-drawer__actions">
      <el-button
        v-if="task.canResume"
        type="primary"
        :loading="loading"
        @click="emit('resume')"
      >继续执行</el-button>
      <el-button
        v-if="task.canTerminate"
        :loading="loading"
        @click="emit('terminate')"
      >终止清空</el-button>
      <el-button
        v-if="task.errorFileName"
        link
        type="danger"
        @click="handleDownload"
      >下载错误文件</el-button>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.outbound-import-task-drawer {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px;
  border-radius: 8px;
  background: #f5f7fa;
}

.outbound-import-task-drawer__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.outbound-import-task-drawer__title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.outbound-import-task-drawer__meta,
.outbound-import-task-drawer__summary {
  font-size: 13px;
  color: #606266;
}

.outbound-import-task-drawer__summary,
.outbound-import-task-drawer__actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
</style>
