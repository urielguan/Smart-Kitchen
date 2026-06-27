<script setup lang="ts">
import { CircleCheck, CircleClose, WarningFilled } from '@element-plus/icons-vue'
import type { AiCheckResult } from '@/types/health-check'

interface Props {
  result: AiCheckResult | null
}

const props = defineProps<Props>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

/**
 * 获取结果 banner 样式类型
 */
function getResultBannerClass(result: AiCheckResult | null): string {
  if (!result) return ''
  if (result.checkResult === 'pass') return 'banner-pass'
  if (result.hasWarning) return 'banner-warning'
  return 'banner-fail'
}

/**
 * 获取结果图标
 */
function getResultIcon(result: AiCheckResult | null): string {
  if (!result) return ''
  if (result.checkResult === 'pass') return 'success'
  if (result.hasWarning) return 'warning'
  return 'fail'
}

/**
 * 获取结果标题文字
 */
function getResultTitle(result: AiCheckResult | null): string {
  if (!result) return ''
  if (result.checkResult === 'pass' && !result.hasWarning) return '晨检通过'
  if (result.checkResult === 'pass' && result.hasWarning) return '晨检通过（有警告）'
  return '晨检未通过'
}

/**
 * 获取检查项的状态类型
 */
function getItemStatus(checkResult?: 'pass' | 'fail'): string {
  if (!checkResult) return 'info'
  return checkResult === 'pass' ? 'success' : 'danger'
}

/**
 * 获取检查项的图标
 */
function getItemIcon(checkResult?: 'pass' | 'fail'): typeof CircleCheck | typeof CircleClose {
  if (!checkResult || checkResult === 'pass') return CircleCheck
  return CircleClose
}

/**
 * 格式化温度
 */
function formatTemperature(temp?: number): string {
  if (temp == null) return '-'
  return `${temp}℃`
}

/**
 * 获取温度状态文字
 */
function getTempStatusLabel(status?: string): string {
  if (!status) return '-'
  const map: Record<string, string> = {
    normal: '体温正常',
    low: '体温偏低',
    high: '体温异常'
  }
  return map[status] || status
}
</script>

<template>
  <div v-if="props.result" class="ai-check-result-detail">
    <!-- 结果汇总 banner -->
    <div class="result-banner" :class="getResultBannerClass(props.result)">
      <div class="banner-content">
        <el-icon v-if="getResultIcon(props.result) === 'success'" class="banner-icon"><CircleCheck /></el-icon>
        <el-icon v-else-if="getResultIcon(props.result) === 'warning'" class="banner-icon"><WarningFilled /></el-icon>
        <el-icon v-else class="banner-icon"><CircleClose /></el-icon>
        <span class="banner-title">{{ getResultTitle(props.result) }}</span>
        <span class="banner-no">编号: {{ props.result.checkNo }}</span>
      </div>
    </div>

    <!-- 员工信息卡片 -->
    <div class="employee-card">
      <el-avatar :size="48" :src="props.result.avatarUrl" class="employee-avatar">
        {{ props.result.employeeName?.charAt(0) }}
      </el-avatar>
      <div class="employee-details">
        <div class="employee-name">{{ props.result.employeeName || '未知员工' }}</div>
        <div class="employee-meta">
          <span v-if="props.result.employeeNo" class="meta-item">工号: {{ props.result.employeeNo }}</span>
          <span v-if="props.result.position" class="meta-item">岗位: {{ props.result.position }}</span>
          <span v-if="props.result.checkTime" class="meta-item">检查时间: {{ props.result.checkTime }}</span>
        </div>
      </div>
    </div>

    <!-- 检查项目列表 -->
    <div class="check-items">
      <h4 class="items-title">检查项目</h4>

      <!-- 人脸识别 -->
      <div class="check-item">
        <div class="item-header">
          <span class="item-label">人脸识别</span>
          <el-tag :type="getItemStatus(props.result.faceMatchResult)" size="default">
            <component :is="getItemIcon(props.result.faceMatchResult)" style="margin-right: 4px" />
            {{ props.result.faceMatchResult === 'pass' ? '通过' : props.result.faceMatchResult === 'fail' ? '未通过' : '未检测' }}
          </el-tag>
        </div>
        <div v-if="props.result.faceMatchScore != null" class="item-detail">
          匹配分数: {{ props.result.faceMatchScore }}%
        </div>
        <div v-if="props.result.faceImageUrl" class="item-detail">
          <el-image :src="props.result.faceImageUrl" style="width: 80px; height: 80px; border-radius: 6px; margin-top: 8px" fit="cover" />
        </div>
      </div>

      <!-- 体温检测 -->
      <div class="check-item">
        <div class="item-header">
          <span class="item-label">体温检测</span>
          <el-tag :type="getItemStatus(props.result.tempCheckResult)" size="default">
            <component :is="getItemIcon(props.result.tempCheckResult)" style="margin-right: 4px" />
            {{ props.result.tempCheckResult === 'pass' ? '通过' : props.result.tempCheckResult === 'fail' ? '未通过' : '未检测' }}
          </el-tag>
        </div>
        <div class="item-detail">
          <span class="temp-display">{{ formatTemperature(props.result.temperature) }}</span>
          <span v-if="props.result.tempStatus" class="temp-status"> ({{ getTempStatusLabel(props.result.tempStatus) }})</span>
        </div>
      </div>

      <!-- 健康证 -->
      <div class="check-item">
        <div class="item-header">
          <span class="item-label">健康证</span>
          <el-tag :type="getItemStatus(props.result.certCheckResult)" size="default">
            <component :is="getItemIcon(props.result.certCheckResult)" style="margin-right: 4px" />
            {{ props.result.certCheckResult === 'pass' ? '通过' : props.result.certCheckResult === 'fail' ? '未通过' : '未检测' }}
          </el-tag>
        </div>
        <div class="item-detail">
          <span v-if="props.result.certNo">证书编号: {{ props.result.certNo }}</span>
          <span v-if="props.result.certExpiryDate" class="cert-expiry">有效期至: {{ props.result.certExpiryDate }}</span>
        </div>
        <div v-if="props.result.certCheckMessage" class="item-message">
          {{ props.result.certCheckMessage }}
        </div>
      </div>

      <!-- 手部卫生 -->
      <div class="check-item">
        <div class="item-header">
          <span class="item-label">手部卫生</span>
          <el-tag :type="getItemStatus(props.result.handHygiene)" size="default">
            <component :is="getItemIcon(props.result.handHygiene)" style="margin-right: 4px" />
            {{ props.result.handHygiene === 'pass' ? '合格' : props.result.handHygiene === 'fail' ? '不合格' : '未检测' }}
          </el-tag>
        </div>
        <div v-if="props.result.handHygieneMessage" class="item-message">
          {{ props.result.handHygieneMessage }}
        </div>
      </div>

      <!-- 着装检查 -->
      <div class="check-item">
        <div class="item-header">
          <span class="item-label">着装检查</span>
          <el-tag :type="getItemStatus(props.result.uniformCheck)" size="default">
            <component :is="getItemIcon(props.result.uniformCheck)" style="margin-right: 4px" />
            {{ props.result.uniformCheck === 'pass' ? '合格' : props.result.uniformCheck === 'fail' ? '不合格' : '未检测' }}
          </el-tag>
        </div>
        <div v-if="props.result.uniformCheckMessage" class="item-message">
          {{ props.result.uniformCheckMessage }}
        </div>
      </div>
    </div>

    <!-- 失败原因列表 -->
    <div v-if="props.result.failReasons && props.result.failReasons.length > 0" class="reasons-section">
      <h4 class="items-title fail-title">未通过原因</h4>
      <ul class="reasons-list">
        <li v-for="(reason, index) in props.result.failReasons" :key="index" class="reason-item">
          <el-icon color="#f56c6c"><CircleClose /></el-icon>
          <span>{{ reason }}</span>
        </li>
      </ul>
    </div>

    <!-- 警告信息列表 -->
    <div v-if="props.result.warningMessages && props.result.warningMessages.length > 0" class="reasons-section">
      <h4 class="items-title warning-title">警告信息</h4>
      <ul class="reasons-list warning-list">
        <li v-for="(msg, index) in props.result.warningMessages" :key="index" class="reason-item">
          <el-icon color="#e6a23c"><WarningFilled /></el-icon>
          <span>{{ msg }}</span>
        </li>
      </ul>
    </div>

    <!-- 关闭按钮 -->
    <div class="result-footer">
      <el-button type="primary" @click="emit('close')">关闭</el-button>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.ai-check-result-detail {
  max-width: 600px;
}

.result-banner {
  border-radius: 8px;
  padding: 16px 20px;
  margin-bottom: 20px;

  .banner-content {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .banner-icon {
    font-size: 28px;
  }

  .banner-title {
    font-size: 18px;
    font-weight: 600;
  }

  .banner-no {
    margin-left: auto;
    font-size: 13px;
    opacity: 0.8;
  }

  &.banner-pass {
    background: #f0f9eb;
    border: 1px solid #e1f3d8;
    color: #67c23a;
  }

  &.banner-fail {
    background: #fef0f0;
    border: 1px solid #fde2e2;
    color: #f56c6c;
  }

  &.banner-warning {
    background: #fdf6ec;
    border: 1px solid #faecd8;
    color: #e6a23c;
  }
}

.employee-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 8px;
  margin-bottom: 20px;

  .employee-avatar {
    flex-shrink: 0;
    font-size: 20px;
  }

  .employee-details {
    .employee-name {
      font-size: 16px;
      font-weight: 600;
      color: #303133;
    }

    .employee-meta {
      margin-top: 6px;
      display: flex;
      gap: 16px;
      flex-wrap: wrap;

      .meta-item {
        font-size: 13px;
        color: #909399;
      }
    }
  }
}

.check-items {
  margin-bottom: 20px;

  .items-title {
    font-size: 15px;
    font-weight: 600;
    color: #303133;
    margin: 0 0 12px 0;
    padding-bottom: 8px;
    border-bottom: 1px solid #ebeef5;
  }
}

.check-item {
  padding: 12px 0;
  border-bottom: 1px dashed #ebeef5;

  &:last-child {
    border-bottom: none;
  }

  .item-header {
    display: flex;
    align-items: center;
    justify-content: space-between;

    .item-label {
      font-size: 14px;
      font-weight: 500;
      color: #606266;
    }
  }

  .item-detail {
    margin-top: 8px;
    font-size: 13px;
    color: #909399;

    .temp-display {
      font-size: 16px;
      font-weight: 600;
      color: #303133;
    }

    .temp-status {
      color: #909399;
    }

    .cert-expiry {
      margin-left: 12px;
    }
  }

  .item-message {
    margin-top: 6px;
    font-size: 13px;
    color: #e6a23c;
    background: #fdf6ec;
    padding: 6px 10px;
    border-radius: 4px;
  }
}

.reasons-section {
  margin-bottom: 16px;

  .items-title {
    font-size: 15px;
    font-weight: 600;
    margin: 0 0 8px 0;

    &.fail-title {
      color: #f56c6c;
    }

    &.warning-title {
      color: #e6a23c;
    }
  }

  .reasons-list {
    list-style: none;
    padding: 0;
    margin: 0;

    &.warning-list {
      .reason-item {
        border-bottom-color: #faecd8;
      }
    }

    .reason-item {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 8px 12px;
      border-bottom: 1px solid #fde2e2;
      font-size: 14px;
      color: #606266;

      &:last-child {
        border-bottom: none;
      }
    }
  }
}

.result-footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
}
</style>
