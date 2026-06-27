<script setup lang="ts">
import type { AiEvaluateResult } from '@/types'
import { SAMPLE_PERMISSIONS } from '@/constants/permission'

interface Props {
  result: AiEvaluateResult | null
  loading?: boolean
  error?: string
  canTrigger?: boolean
}

defineProps<Props>()

const emit = defineEmits<{
  trigger: []
}>()

const getRiskLevelType = (level: string) => {
  const map: Record<string, string> = { low: 'success', medium: 'warning', high: 'danger' }
  return map[level] || 'info'
}

const getRiskLevelText = (level: string) => {
  const map: Record<string, string> = { low: '低风险', medium: '中风险', high: '高风险' }
  return map[level] || level
}

const getStarArray = (level: number) => Array.from({ length: 5 }, (_, i) => i < level)

const getScoreColor = (score: number) => {
  if (score >= 90) return '#67c23a'
  if (score >= 80) return '#409eff'
  if (score >= 60) return '#e6a23c'
  return '#f56c6c'
}

const isDimAlert = (score: number, riskLevel?: string) => {
  return score < 60 || riskLevel === 'high'
}
</script>

<template>
  <div class="ai-evaluate-panel">
    <div v-if="!result && !loading && !error" class="no-result">
      <p>暂无AI评估结果</p>
      <el-button v-if="canTrigger" type="primary" size="small" v-permission="SAMPLE_PERMISSIONS.AI_EVALUATE" @click="emit('trigger')">触发AI评估</el-button>
    </div>

    <div v-if="!result && !loading && error" class="error-state">
      <el-icon :size="20" color="#f56c6c"><WarningFilled /></el-icon>
      <span class="error-text">{{ error }}</span>
      <el-button v-if="canTrigger" type="primary" size="small" @click="emit('trigger')">重试</el-button>
    </div>

    <div v-if="loading" class="loading-state">
      <el-icon class="is-loading" :size="24"><Loading /></el-icon>
      <span>AI评估中...</span>
    </div>

    <div v-if="result && !loading" class="evaluate-result" :class="{ 'risk-alert': result.riskLevel === 'high' }">
      <div class="score-header">
        <div class="score-circle" :style="{ borderColor: getScoreColor(result.finalScore) }">
          <span class="score-value">{{ result.finalScore }}</span>
          <span class="score-label">综合评分</span>
        </div>
        <div class="score-meta">
          <div class="star-row">
            <el-icon v-for="(filled, i) in getStarArray(result.starLevel)" :key="i" class="star" :class="{ filled }">
              <StarFilled v-if="filled" />
              <Star v-else />
            </el-icon>
          </div>
          <el-tag :type="getRiskLevelType(result.riskLevel) as any" size="small">{{ getRiskLevelText(result.riskLevel) }}</el-tag>
        </div>
      </div>

      <div class="dimension-section">
        <div class="section-title">维度评分</div>
        <div class="dimension-item" :class="{ 'dim-alert': isDimAlert(result.dimensionScores.colorScore, result.riskLevel) }">
          <span class="dim-label">色泽</span>
          <el-progress :percentage="result.dimensionScores.colorScore" :stroke-width="12" :color="getScoreColor(result.dimensionScores.colorScore)" />
          <span class="dim-score">{{ result.dimensionScores.colorScore }}</span>
        </div>
        <div class="dimension-item" :class="{ 'dim-alert': isDimAlert(result.dimensionScores.shapeScore, result.riskLevel) }">
          <span class="dim-label">形态</span>
          <el-progress :percentage="result.dimensionScores.shapeScore" :stroke-width="12" :color="getScoreColor(result.dimensionScores.shapeScore)" />
          <span class="dim-score">{{ result.dimensionScores.shapeScore }}</span>
        </div>
        <div class="dimension-item" :class="{ 'dim-alert': isDimAlert(result.dimensionScores.donenessScore, result.riskLevel) }">
          <span class="dim-label">熟度</span>
          <el-progress :percentage="result.dimensionScores.donenessScore" :stroke-width="12" :color="getScoreColor(result.dimensionScores.donenessScore)" />
          <span class="dim-score">{{ result.dimensionScores.donenessScore }}</span>
        </div>
      </div>

      <div class="analysis-section">
        <div class="section-title">维度分析</div>
        <div class="analysis-item" :class="{ 'dim-alert': isDimAlert(result.dimensionScores.colorScore, result.riskLevel) }">
          <strong>色泽：</strong>{{ result.dimensionAnalysis.colorAnalysis }}
        </div>
        <div class="analysis-item" :class="{ 'dim-alert': isDimAlert(result.dimensionScores.shapeScore, result.riskLevel) }">
          <strong>形态：</strong>{{ result.dimensionAnalysis.shapeAnalysis }}
        </div>
        <div class="analysis-item" :class="{ 'dim-alert': isDimAlert(result.dimensionScores.donenessScore, result.riskLevel) }">
          <strong>熟度：</strong>{{ result.dimensionAnalysis.donenessAnalysis }}
        </div>
      </div>

      <div v-if="result.suggestions?.length" class="suggestion-section" :class="{ 'suggestion-alert': result.riskLevel === 'high' }">
        <div class="section-title">
          <template v-if="result.riskLevel === 'high'">
            <el-icon style="color: #f56c6c; vertical-align: middle"><WarningFilled /></el-icon> 风险提示
          </template>
          <template v-else>优化建议</template>
        </div>
        <ul class="suggestion-list">
          <li v-for="(s, i) in result.suggestions" :key="i">{{ s }}</li>
        </ul>
      </div>

      <div v-if="canTrigger" class="re-evaluate">
        <el-button size="small" v-permission="SAMPLE_PERMISSIONS.AI_EVALUATE" @click="emit('trigger')">重新评估</el-button>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { Loading, WarningFilled, Star, StarFilled } from '@element-plus/icons-vue'
</script>

<style lang="scss" scoped>
.ai-evaluate-panel {
  padding: 8px 0;
}

.no-result {
  text-align: center;
  padding: 20px;
  color: #909399;
}

.error-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 20px;
  background: #fef0f0;
  border-radius: 8px;
}

.error-text {
  color: #f56c6c;
  font-size: 14px;
}

.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 24px;
  color: #409eff;
}

.score-header {
  display: flex;
  align-items: center;
  gap: 24px;
  margin-bottom: 20px;
}

.score-circle {
  width: 90px;
  height: 90px;
  border-radius: 50%;
  border: 3px solid;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.score-value {
  font-size: 24px;
  font-weight: bold;
  line-height: 1.2;
}

.score-label {
  font-size: 11px;
  color: #909399;
}

.score-meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.star-row {
  display: flex;
  gap: 2px;
}

.star {
  font-size: 20px;
  color: #dcdfe6;

  &.filled {
    color: #f7ba2a;
  }
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 10px;
}

.dimension-section {
  margin-bottom: 16px;
  padding: 12px;
  background: #f8fafc;
  border-radius: 8px;
}

.dimension-item {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;

  &:last-child {
    margin-bottom: 0;
  }
}

.dim-label {
  width: 40px;
  font-size: 13px;
  color: #606266;
  flex-shrink: 0;
}

.dim-score {
  width: 36px;
  text-align: right;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  flex-shrink: 0;
}

.analysis-section,
.suggestion-section {
  margin-bottom: 16px;
}

.analysis-item {
  font-size: 13px;
  color: #606266;
  line-height: 1.8;

  strong {
    color: #303133;
  }
}

.suggestion-list {
  margin: 0;
  padding-left: 18px;

  li {
    font-size: 13px;
    color: #606266;
    line-height: 1.8;
  }
}

.re-evaluate {
  text-align: right;
}

/* AC-FS-27: 高风险异常高亮 */
.risk-alert {
  padding: 12px;
  border: 2px dashed #f56c6c;
  border-radius: 8px;
  background: #fff5f5;
}

.dim-alert {
  :deep(.dim-label),
  :deep(.dim-score),
  > .dim-label,
  > .dim-score,
  strong {
    color: #f56c6c !important;
  }
}

.suggestion-alert {
  background: #fef0f0;
  padding: 12px;
  border-radius: 8px;

  .suggestion-list li {
    color: #f56c6c;
    font-weight: 500;
  }
}
</style>
