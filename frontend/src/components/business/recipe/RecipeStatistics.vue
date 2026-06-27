<template>
  <div class="recipe-statistics">
    <!-- 1. 本周热门菜谱 TOP5 -->
    <div class="stat-card hot-recipes-card" style="--delay: 0" v-if="hotRecipes && hotRecipes.length > 0">
      <div class="hot-card-header">
        <div class="hot-card-title">本周热门菜谱</div>
        <div class="hot-card-count">TOP5</div>
      </div>
      <div class="hot-recipes-list">
        <div
          v-for="(recipe, index) in hotRecipes.slice(0, 5)"
          :key="recipe.recipeId"
          class="hot-recipe-item"
        >
          <div class="recipe-main">
            <span class="rank" :class="'rank-' + (index + 1)">{{ index + 1 }}</span>
            <span class="recipe-name">{{ recipe.recipeName }}</span>
          </div>
          <div class="recipe-meta">
            <span class="recipe-category">{{ recipe.categoryName || '-' }}</span>
            <span class="recipe-rating">
              <el-icon><Star /></el-icon>
              {{ recipe.rating?.toFixed(1) || '4.5' }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- 2. 营养配比 -->
    <div class="stat-card nutrients-card" style="--delay: 1">
      <div class="nutrients-card__title">营养配比</div>

      <div class="nutrients-card__calories">
        <span class="nutrients-card__calories-label">平均热量</span>
        <div class="nutrients-card__calories-value">
          <span class="nutrients-card__calories-number">{{ formatNumber(statistics.nutritionDistribution?.avgCalories) }}</span>
          <span class="nutrients-card__calories-unit">卡/100g</span>
        </div>
      </div>

      <div class="nutrients-card__metrics">
        <div class="nutrients-card__metric">
          <div class="nutrients-card__metric-head">
            <span class="nutrients-card__metric-name">蛋白质</span>
            <span class="nutrients-card__metric-value">{{ formatNumber(statistics.nutritionDistribution?.avgProtein) }}g（{{ formatPercent(statistics.nutritionDistribution?.proteinPercent) }}%）</span>
          </div>
          <div class="nutrients-card__metric-bar">
            <div class="nutrients-card__metric-fill nutrients-card__metric-fill--protein" :style="{ width: (statistics.nutritionDistribution?.proteinPercent || 0) + '%' }"></div>
          </div>
        </div>

        <div class="nutrients-card__metric">
          <div class="nutrients-card__metric-head">
            <span class="nutrients-card__metric-name">碳水化合物</span>
            <span class="nutrients-card__metric-value">{{ formatNumber(statistics.nutritionDistribution?.avgCarbs) }}g（{{ formatPercent(statistics.nutritionDistribution?.carbsPercent) }}%）</span>
          </div>
          <div class="nutrients-card__metric-bar">
            <div class="nutrients-card__metric-fill nutrients-card__metric-fill--carbs" :style="{ width: (statistics.nutritionDistribution?.carbsPercent || 0) + '%' }"></div>
          </div>
        </div>

        <div class="nutrients-card__metric">
          <div class="nutrients-card__metric-head">
            <span class="nutrients-card__metric-name">脂肪</span>
            <span class="nutrients-card__metric-value">{{ formatNumber(statistics.nutritionDistribution?.avgFat) }}g（{{ formatPercent(statistics.nutritionDistribution?.fatPercent) }}%）</span>
          </div>
          <div class="nutrients-card__metric-bar">
            <div class="nutrients-card__metric-fill nutrients-card__metric-fill--fat" :style="{ width: (statistics.nutritionDistribution?.fatPercent || 0) + '%' }"></div>
          </div>
        </div>
      </div>
    </div>

    <!-- 3. 菜谱收藏 -->
    <div class="stat-card favorites-card" style="--delay: 2">
      <div class="favorites-card__title">菜谱收藏</div>

      <div class="favorites-card__summary">
        <span class="favorites-card__summary-label">本周新增</span>
        <div class="favorites-card__summary-value">
          <span class="favorites-card__summary-number">{{ statistics.weeklyNewRecipes }}</span>
          <span class="favorites-card__summary-unit">道菜谱</span>
        </div>
      </div>

      <div class="favorites-card__total">{{ statistics.totalRecipes }}</div>
      <div class="favorites-card__caption">已收藏菜谱</div>
    </div>

    <!-- 4. 食材覆盖率 -->
    <div class="stat-card coverage-card" style="--delay: 3">
      <div class="coverage-card__title">食材覆盖率</div>

      <div class="coverage-card__chart">
        <svg viewBox="0 0 192 192" class="coverage-card__svg" aria-hidden="true">
          <defs>
            <linearGradient id="coverageProgressGradient" x1="96" y1="23" x2="96" y2="169" gradientUnits="userSpaceOnUse">
              <stop offset="0%" stop-color="#6e84ff" />
              <stop offset="100%" stop-color="#637aff" />
            </linearGradient>
          </defs>
          <circle
            class="coverage-card__track"
            cx="96"
            cy="96"
            :r="coverageRadius"
            :stroke-width="coverageStroke"
            :stroke-dasharray="coverageTrackDasharray"
            :stroke-dashoffset="coverageDashoffset"
          />
          <circle
            class="coverage-card__progress"
            cx="96"
            cy="96"
            :r="coverageRadius"
            :stroke-width="coverageStroke"
            :stroke-dasharray="coverageProgressDasharray"
            :stroke-dashoffset="coverageDashoffset"
          />
          <path
            class="coverage-card__inner-dash"
            :d="coverageArcPath"
            :stroke-width="coverageInnerStroke"
            :stroke-dasharray="coverageInnerDasharray"
          />
        </svg>
        <div class="coverage-card__indicator" :style="coverageIndicatorStyle"></div>

        <div class="coverage-card__data">
          <div class="coverage-card__percent">{{ formatPercent(statistics.ingredientCoverage) }}%</div>
          <div class="coverage-card__label">食材覆盖</div>
        </div>
      </div>
    </div>

    <!-- 5. 营养达标率 -->
    <div class="stat-card nutrition-pass-card" style="--delay: 4">
      <div class="nutrition-pass-card__title">营养达标率</div>

      <div class="nutrition-pass-card__chart">
        <svg viewBox="0 0 203 204" class="nutrition-pass-card__svg" aria-hidden="true">
          <defs>
            <linearGradient id="nutritionPassTrackGradient" x1="101.5" y1="27" x2="101.5" y2="177" gradientUnits="userSpaceOnUse">
              <stop offset="0%" stop-color="#c0c5ca" />
              <stop offset="100%" stop-color="#e1e2e9" />
            </linearGradient>
            <linearGradient id="nutritionPassProgressGradient" x1="101.5" y1="27" x2="101.5" y2="177" gradientUnits="userSpaceOnUse">
              <stop offset="0%" stop-color="#68c23a" />
              <stop offset="100%" stop-color="#3cc7ec" />
            </linearGradient>
          </defs>
          <circle
            class="nutrition-pass-card__track"
            cx="101.5"
            cy="102"
            :r="nutritionPassRadius"
            :stroke-width="nutritionPassStroke"
            :stroke-dasharray="nutritionPassTrackDasharray"
            :stroke-dashoffset="nutritionPassDashoffset"
          />
          <circle
            class="nutrition-pass-card__progress"
            cx="101.5"
            cy="102"
            :r="nutritionPassRadius"
            :stroke-width="nutritionPassStroke"
            :stroke-dasharray="nutritionPassProgressDasharray"
            :stroke-dashoffset="nutritionPassDashoffset"
          />
          <path
            class="nutrition-pass-card__inner-dash"
            :d="nutritionPassArcPath"
            :stroke-width="nutritionPassInnerStroke"
            :stroke-dasharray="nutritionPassInnerDasharray"
          />
        </svg>
        <div class="nutrition-pass-card__indicator" :style="nutritionPassIndicatorStyle"></div>

        <div class="nutrition-pass-card__data">
          <div class="nutrition-pass-card__percent">{{ formatPercent(statistics.nutritionPassRate) }}%</div>
          <div class="nutrition-pass-card__label">营养达标</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { RecipeStatistics, HotRecipe } from '@/types/recipe'
import { Star } from '@element-plus/icons-vue'

const props = defineProps<{
  statistics: RecipeStatistics
  hotRecipes?: HotRecipe[]
}>()

/** 格式化百分比，保留1位小数 */
const formatPercent = (value: number | undefined | null): string => {
  if (value === undefined || value === null) return '0'
  return Number(value).toFixed(1).replace(/\.0$/, '')
}

/** 格式化数字，保留1位小数 */
const formatNumber = (value: number | undefined | null): string => {
  if (value === undefined || value === null) return '0'
  return Number(value).toFixed(1).replace(/\.0$/, '')
}

const polarToCartesian = (cx: number, cy: number, radius: number, angleInDegrees: number) => {
  const angleInRadians = ((angleInDegrees - 90) * Math.PI) / 180
  return {
    x: cx + radius * Math.cos(angleInRadians),
    y: cy + radius * Math.sin(angleInRadians)
  }
}

const describeArc = (cx: number, cy: number, radius: number, startAngle: number, endAngle: number) => {
  const start = polarToCartesian(cx, cy, radius, endAngle)
  const end = polarToCartesian(cx, cy, radius, startAngle)
  const largeArcFlag = endAngle - startAngle <= 180 ? '0' : '1'

  return [`M ${start.x} ${start.y}`, `A ${radius} ${radius} 0 ${largeArcFlag} 0 ${end.x} ${end.y}`].join(' ')
}

const coveragePercent = computed(() => {
  const value = Number(props.statistics.ingredientCoverage || 0)
  return Math.min(Math.max(value, 0), 100)
})

const coverageArcLength = 300
const coverageStartAngle = 210
const coverageStroke = 20
const coverageRadius = 75
const coverageInnerRadius = 61
const coverageInnerStroke = 1
const coverageCircumference = 2 * Math.PI * coverageRadius
const coverageVisibleArc = coverageCircumference * (coverageArcLength / 360)
const coverageGapArc = coverageCircumference - coverageVisibleArc
const coverageDashoffset = `${coverageCircumference * (1 - coverageStartAngle / 360)}`
const coverageArcPath = describeArc(96, 96, coverageInnerRadius, coverageStartAngle, coverageStartAngle + coverageArcLength)

const coverageTrackDasharray = `${coverageVisibleArc} ${coverageGapArc}`
const coverageInnerDasharray = '0.8 6.2'

const coverageProgressDasharray = computed(() => {
  const progressArc = coverageVisibleArc * (coveragePercent.value / 100)
  return `${progressArc} ${coverageCircumference - progressArc}`
})

const coverageIndicatorStyle = computed(() => {
  const progressAngle = (coverageStartAngle - 90 + coverageArcLength * (coveragePercent.value / 100)) * (Math.PI / 180)
  const x = 96 + Math.cos(progressAngle) * coverageRadius
  const y = 96 + Math.sin(progressAngle) * coverageRadius

  return {
    left: `${x}px`,
    top: `${y}px`
  }
})

const nutritionPassPercent = computed(() => {
  const value = Number(props.statistics.nutritionPassRate || 0)
  return Math.min(Math.max(value, 0), 100)
})

const nutritionPassArcLength = 300
const nutritionPassStartAngle = 210
const nutritionPassStroke = 18
const nutritionPassRadius = 74
const nutritionPassInnerRadius = 60
const nutritionPassInnerStroke = 1
const nutritionPassCircumference = 2 * Math.PI * nutritionPassRadius
const nutritionPassVisibleArc = nutritionPassCircumference * (nutritionPassArcLength / 360)
const nutritionPassGapArc = nutritionPassCircumference - nutritionPassVisibleArc
const nutritionPassDashoffset = `${nutritionPassCircumference * (1 - nutritionPassStartAngle / 360)}`
const nutritionPassArcPath = describeArc(101.5, 102, nutritionPassInnerRadius, nutritionPassStartAngle, nutritionPassStartAngle + nutritionPassArcLength)

const nutritionPassTrackDasharray = `${nutritionPassVisibleArc} ${nutritionPassGapArc}`
const nutritionPassInnerDasharray = '0.8 6.2'

const nutritionPassProgressDasharray = computed(() => {
  const progressArc = nutritionPassVisibleArc * (nutritionPassPercent.value / 100)
  return `${progressArc} ${nutritionPassCircumference - progressArc}`
})

const nutritionPassIndicatorStyle = computed(() => {
  const progressAngle = (nutritionPassStartAngle - 90 + nutritionPassArcLength * (nutritionPassPercent.value / 100)) * (Math.PI / 180)
  const x = 101.5 + Math.cos(progressAngle) * nutritionPassRadius
  const y = 102 + Math.sin(progressAngle) * nutritionPassRadius

  return {
    left: `${x}px`,
    top: `${y}px`
  }
})
</script>




<style lang="scss" scoped>
@import url('https://fonts.googleapis.com/css2?family=Noto+Serif+SC:wght@400;600;700&display=swap');

// Gastronomy Color Palette
$terracotta: #c75b39;
$terracotta-light: #e8a090;
$sage: #7a9e7e;
$sage-dark: #4a7c59;
$golden: #d4a574;
$cream: #faf7f2;
$warm-gray: #8b8178;
$deep-burgundy: #8b4049;

.recipe-statistics {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: clamp(16px, 1.4vw, 32px);
  margin-bottom: 24px;
  flex-shrink: 0;
  position: relative;
  padding: 4px 0;

  // 响应式：窄屏时水平滚动
  @media (max-width: 1200px) {
    display: flex;
    gap: 16px;
    overflow-x: auto;
    padding-bottom: 8px;
  }
}

.stat-card {
  width: 100%;
  min-width: 0;
  height: auto;
  aspect-ratio: 217 / 270;
  background: #ffffff;
  border-radius: 16px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.08),
    0 4px 12px rgba(0, 0, 0, 0.04),
    inset 0 1px 0 rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(114, 136, 250, 0.08);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  animation: fadeSlideUp 0.5s ease-out backwards;
  animation-delay: calc(var(--delay) * 0.1s);

  &:hover {
    transform: translateY(-4px);
    box-shadow:
      0 4px 8px rgba(0, 0, 0, 0.1),
      0 12px 24px rgba(0, 0, 0, 0.08),
      inset 0 1px 0 rgba(255, 255, 255, 0.9);
    border-color: rgba(114, 136, 250, 0.18);
  }

  &.featured {
    background: #ffffff;
    border-color: rgba(114, 136, 250, 0.14);

    .stat-value {
      .number {
        color: $sage-dark;
        font-size: 36px;
      }
    }
  }

  &.nutrients-card,
  &.hot-recipes-card {
    .stat-content {
      flex: 1;
      display: flex;
      flex-direction: column;
      justify-content: center;
    }
  }
}

.featured-badge {
  position: absolute;
  top: 12px;
  right: 12px;
  background: linear-gradient(135deg, $sage 0%, $sage-dark 100%);
  color: white;
  font-size: 10px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 20px;
  letter-spacing: 0.5px;
}

.stat-visual {
  position: relative;
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-icon-wrapper {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  z-index: 1;

  &.recipes {
    background: linear-gradient(135deg, $terracotta 0%, #d4785c 100%);
    box-shadow: 0 4px 12px rgba($terracotta, 0.3);
  }

  &.coverage {
    background: linear-gradient(135deg, $golden 0%, #e8c09a 100%);
    box-shadow: 0 4px 12px rgba($golden, 0.3);
  }

  &.nutrition {
    background: linear-gradient(135deg, $sage 0%, $sage-dark 100%);
    box-shadow: 0 4px 12px rgba($sage, 0.3);
  }

  &.distribution {
    background: linear-gradient(135deg, $deep-burgundy 0%, #a85a66 100%);
    box-shadow: 0 4px 12px rgba($deep-burgundy, 0.3);
  }

  &.hot {
    background: linear-gradient(135deg, $deep-burgundy 0%, #a85a66 100%);
    box-shadow: 0 4px 12px rgba($deep-burgundy, 0.3);
  }
}

.stat-icon {
  width: 22px;
  height: 22px;
  color: white;
}

.stat-ring {
  position: absolute;
  width: 48px;
  height: 48px;
  border: 2px dashed rgba($terracotta, 0.2);
  border-radius: 50%;
  animation: slowRotate 20s linear infinite;

  &.pulse {
    border-color: rgba($sage, 0.25);
    animation: slowRotate 20s linear infinite, pulse 2s ease-in-out infinite;
  }
}

.stat-header {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.stat-title {
  font-family: 'Noto Serif SC', serif;
  font-size: 14px;
  font-weight: 600;
  color: $deep-burgundy;
}

.stat-content {
  flex: 1;
  margin-top: 8px;
}

.stat-value {
  display: flex;
  align-items: baseline;
  gap: 2px;

  .number {
    font-family: 'Noto Serif SC', serif;
    font-size: 32px;
    font-weight: 700;
    color: $terracotta;
    line-height: 1;
    letter-spacing: -1px;
  }

  .unit {
    font-size: 14px;
    color: $warm-gray;
    margin-left: 2px;
  }

  &.large .number {
    font-size: 36px;
  }
}

.stat-label {
  font-size: 13px;
  color: $warm-gray;
  margin-top: 4px;
  font-weight: 500;
}

.stat-trend {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;

  .trend-badge {
    font-size: 11px;
    font-weight: 600;
    padding: 2px 8px;
    border-radius: 12px;

    &.up {
      background: rgba($sage, 0.15);
      color: $sage-dark;
    }
  }

  .trend-text {
    font-size: 11px;
    color: $warm-gray;
  }
}

.progress-bar {
  height: 6px;
  background: rgba($golden, 0.2);
  border-radius: 3px;
  margin-top: 10px;
  overflow: hidden;

  .progress-fill {
    height: 100%;
    background: linear-gradient(90deg, $golden 0%, #e8c09a 100%);
    border-radius: 3px;
    transition: width 0.8s cubic-bezier(0.4, 0, 0.2, 1);
  }
}

.stat-gauge {
  margin-top: 8px;

  .gauge-svg {
    width: 100%;
    height: 40px;
  }
}

.nutrients-card {
  padding: 18px 16px 16px;
  justify-content: flex-start;

  .nutrients-card__title {
    width: 96px;
    height: 20px;
    font-family: 'PingFang SC', sans-serif;
    font-style: normal;
    font-weight: 400;
    font-size: 16px;
    line-height: 20px;
    color: #333333;
    margin-bottom: 16px;
  }

  .nutrients-card__calories {
    width: 100%;
    height: 44px;
    display: flex;
    flex-direction: row;
    justify-content: space-between;
    align-items: center;
    padding: 8px;
    gap: 16px;
    background: linear-gradient(90deg, #fbf1ee 0%, #fbf7f2 100%);
    border-radius: 12px;
    margin-bottom: 15px;
    box-sizing: border-box;
  }

  .nutrients-card__calories-label {
    font-family: 'PingFang SC', sans-serif;
    font-style: normal;
    font-weight: 400;
    font-size: 14px;
    line-height: 20px;
    color: #666666;
    white-space: nowrap;
  }

  .nutrients-card__calories-value {
    display: flex;
    align-items: flex-end;
    gap: 2px;
  }

  .nutrients-card__calories-number {
    font-family: 'PingFang SC', sans-serif;
    font-style: normal;
    font-weight: 600;
    font-size: 20px;
    line-height: 28px;
    color: #ff4d4f;
  }

  .nutrients-card__calories-unit {
    font-family: 'PingFang SC', sans-serif;
    font-style: normal;
    font-weight: 400;
    font-size: 10px;
    line-height: 14px;
    color: #666666;
    margin-bottom: 2px;
  }

  .nutrients-card__metrics {
    width: 100%;
    display: flex;
    flex-direction: column;
    gap: 15px;
  }

  .nutrients-card__metric {
    width: 100%;
  }

  .nutrients-card__metric-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 7px;
    gap: 12px;
  }

  .nutrients-card__metric-name,
  .nutrients-card__metric-value {
    font-family: 'PingFang SC', sans-serif;
    font-style: normal;
    font-weight: 400;
    font-size: 12px;
    line-height: 17px;
    color: #666666;
  }

  .nutrients-card__metric-name {
    white-space: nowrap;
  }

  .nutrients-card__metric-value {
    text-align: right;
    white-space: nowrap;
  }

  .nutrients-card__metric-bar {
    width: 100%;
    height: 10px;
    background: #f0eeeb;
    border-radius: 15px;
    overflow: hidden;
  }

  .nutrients-card__metric-fill {
    height: 10px;
    border-radius: 15px;
    min-width: 0;
    transition: width 0.8s cubic-bezier(0.4, 0, 0.2, 1);

    &--protein {
      background: #ff4d4f;
    }

    &--carbs {
      background: #fdad00;
    }

    &--fat {
      background: #719877;
    }
  }
}

// 热门菜谱卡片样式
.hot-recipes-card {
  justify-content: flex-start;
  padding: 13px 16px 16px;

  .hot-card-header {
    width: 100%;
    height: 30px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 9px;
    flex-shrink: 0;
  }

  .hot-card-title {
    font-family: 'PingFang SC', sans-serif;
    font-weight: 400;
    font-size: 16px;
    line-height: 20px;
    color: #333333;
  }

  .hot-card-count {
    font-family: 'PingFang SC', sans-serif;
    font-weight: 600;
    font-size: 24px;
    line-height: 30px;
    color: #ff4d4f;
  }

  .hot-recipes-list {
    width: 100%;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .hot-recipe-item {
    width: 100%;
    height: 34px;
    box-sizing: border-box;
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 7px 8px;
    background: rgba(217, 217, 217, 0.1);
    border: 1px solid #ebeef2;
    border-radius: 6px;
    transition: all 0.2s;

    &:hover {
      background: #f8fafc;
      border-color: #d8dee8;
    }
  }

  .recipe-main {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
    flex: 1;
  }

  .rank {
    width: 20px;
    height: 20px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 3px;
    font-family: 'PingFang SC', sans-serif;
    font-weight: 600;
    font-size: 12px;
    line-height: 17px;
    flex-shrink: 0;
    color: #ffffff;
    background: #edebe8;

    &.rank-1 {
      background: linear-gradient(90deg, #ffce22 0%, #ffbc3c 100%);
    }

    &.rank-2 {
      background: linear-gradient(90deg, #ff7474 0%, #ff4d4f 100%);
    }

    &.rank-3 {
      background: linear-gradient(90deg, #68c6f9 0%, #3ca7ff 100%);
    }

    &.rank-4,
    &.rank-5 {
      color: #7c7e81;
      background: #edebe8;
    }
  }

  .recipe-name {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-family: 'PingFang SC', sans-serif;
    font-weight: 400;
    font-size: 12px;
    line-height: 17px;
    color: #000000;
  }

  .recipe-meta {
    display: flex;
    justify-content: flex-end;
    align-items: center;
    gap: 4px;
    margin-left: 8px;
    flex-shrink: 0;
  }

  .recipe-category {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    height: 16px;
    padding: 0 6px;
    background: #f2f0ef;
    border-radius: 100px;
    font-family: 'PingFang SC', sans-serif;
    font-weight: 400;
    font-size: 10px;
    line-height: 14px;
    color: #8f857c;
  }

  .recipe-rating {
    display: inline-flex;
    align-items: center;
    gap: 2px;
    font-family: 'PingFang SC', sans-serif;
    font-weight: 400;
    font-size: 12px;
    line-height: 17px;
    color: #fdad00;

    .el-icon {
      font-size: 12px;
      color: #fdad00;
    }
  }
}

// 菜谱收藏卡片样式
.favorites-card {
  justify-content: flex-start;
  padding: 18px 16px 16px;

  .favorites-card__title {
    font-family: 'PingFang SC', sans-serif;
    font-weight: 400;
    font-size: 16px;
    line-height: 20px;
    color: #333333;
    margin-bottom: 18px;
  }

  .favorites-card__summary {
    width: 100%;
    min-height: 60px;
    box-sizing: border-box;
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 10px 12px;
    gap: 12px;
    background: linear-gradient(135deg, #eefaf4 0%, #f6fcf8 100%);
    border: 1px solid #dff3e7;
    border-radius: 16px;
  }

  .favorites-card__summary-label {
    font-family: 'PingFang SC', sans-serif;
    font-weight: 400;
    font-size: 14px;
    line-height: 20px;
    color: #666666;
    white-space: nowrap;
  }

  .favorites-card__summary-value {
    display: flex;
    align-items: flex-end;
    gap: 2px;
    min-width: 0;
  }

  .favorites-card__summary-number {
    font-family: 'PingFang SC', sans-serif;
    font-weight: 600;
    font-size: 24px;
    line-height: 30px;
    color: #38cb89;
  }

  .favorites-card__summary-unit {
    font-family: 'PingFang SC', sans-serif;
    font-weight: 400;
    font-size: 10px;
    line-height: 14px;
    color: #8b8178;
    margin-bottom: 4px;
    white-space: nowrap;
  }

  .favorites-card__total {
    margin-top: 26px;
    font-family: 'DIN Alternate', 'PingFang SC', sans-serif;
    font-weight: 700;
    font-size: 64px;
    line-height: 1;
    text-align: center;
    color: #38cb89;
    letter-spacing: -2px;
  }

  .favorites-card__caption {
    margin-top: 12px;
    font-family: 'PingFang SC', sans-serif;
    font-weight: 400;
    font-size: 14px;
    line-height: 20px;
    text-align: center;
    color: #666666;
  }
}

// 食材覆盖率卡片样式
.coverage-card {
  justify-content: flex-start;
  padding: 0;

  .coverage-card__title {
    position: absolute;
    left: 16px;
    top: 18px;
    font-family: 'PingFang SC', sans-serif;
    font-weight: 400;
    font-size: 16px;
    line-height: 20px;
    color: #333333;
  }

  .coverage-card__chart {
    position: absolute;
    left: 50%;
    top: 54px;
    width: 192px;
    height: 192px;
    transform: translateX(-50%);
  }

  .coverage-card__svg {
    width: 192px;
    height: 192px;
    overflow: visible;
    filter: drop-shadow(0 10px 20px rgba(99, 122, 255, 0.08));
  }

  .coverage-card__track,
  .coverage-card__progress {
    fill: none;
    stroke-linecap: round;
    transform: rotate(-90deg);
    transform-origin: center;
  }

  .coverage-card__track {
    stroke: #f0f2f5;
    opacity: 0.98;
    filter: drop-shadow(0 2px 6px rgba(15, 23, 42, 0.05));
  }

  .coverage-card__progress {
    stroke: url(#coverageProgressGradient);
    filter: drop-shadow(0 4px 10px rgba(99, 122, 255, 0.2));
  }

  .coverage-card__indicator {
    position: absolute;
    width: 6px;
    height: 6px;
    margin-left: -3px;
    margin-top: -3px;
    border-radius: 50%;
    background: #ffffff;
    box-shadow:
      0 0 0 1.5px rgba(99, 122, 255, 0.16),
      0 1px 3px rgba(15, 23, 42, 0.08);
    z-index: 3;
  }

  .coverage-card__inner-dash {
    fill: none;
    stroke: #999999;
    stroke-linecap: round;
    opacity: 0.95;
  }

  .coverage-card__data {
    position: absolute;
    left: 50%;
    top: 50%;
    transform: translate(-50%, -50%);
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    gap: 6px;
    text-align: center;
    z-index: 2;
  }

  .coverage-card__percent {
    font-family: 'Poppins', 'PingFang SC', sans-serif;
    font-weight: 700;
    font-size: 34px;
    line-height: 36px;
    color: #333333;
    white-space: nowrap;
  }

  .coverage-card__label {
    font-family: 'PingFang SC', sans-serif;
    font-weight: 400;
    font-size: 16px;
    line-height: 22px;
    color: #666666;
  }
}

// 营养达标率卡片样式
.nutrition-pass-card {
  justify-content: flex-start;
  padding: 0;

  .nutrition-pass-card__title {
    position: absolute;
    left: 16px;
    top: 18px;
    font-family: 'PingFang SC', sans-serif;
    font-weight: 400;
    font-size: 16px;
    line-height: 20px;
    color: #333333;
  }

  .nutrition-pass-card__chart {
    position: absolute;
    left: 50%;
    top: 52px;
    width: 203px;
    height: 204px;
    transform: translateX(-50%);
  }

  .nutrition-pass-card__svg {
    width: 203px;
    height: 204px;
    overflow: visible;
    filter: drop-shadow(0 10px 24px rgba(129, 212, 254, 0.18));
  }

  .nutrition-pass-card__track,
  .nutrition-pass-card__progress {
    fill: none;
    stroke-linecap: round;
    transform: rotate(-90deg);
    transform-origin: center;
  }

  .nutrition-pass-card__track {
    stroke: url(#nutritionPassTrackGradient);
    filter: drop-shadow(0 2.6px 7.8px rgba(129, 212, 254, 0.16));
  }

  .nutrition-pass-card__progress {
    stroke: url(#nutritionPassProgressGradient);
    filter: drop-shadow(0 4px 12px rgba(60, 199, 236, 0.22));
  }

  .nutrition-pass-card__indicator {
    position: absolute;
    width: 3.2px;
    height: 3.2px;
    margin-left: -1.6px;
    margin-top: -1.6px;
    border-radius: 50%;
    background: #ffffff;
    box-shadow: 0 0 0 0.52px rgba(0, 0, 0, 0.15);
    z-index: 3;
  }

  .nutrition-pass-card__inner-dash {
    fill: none;
    stroke: #999999;
    stroke-linecap: round;
    opacity: 0.95;
  }

  .nutrition-pass-card__data {
    position: absolute;
    left: 50%;
    top: 50%;
    transform: translate(-50%, -50%);
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    gap: 2px;
    text-align: center;
    z-index: 2;
  }

  .nutrition-pass-card__percent {
    font-family: 'Poppins', 'PingFang SC', sans-serif;
    font-weight: 700;
    font-size: 34px;
    line-height: 36px;
    color: #666666;
    white-space: nowrap;
  }

  .nutrition-pass-card__label {
    font-family: 'PingFang SC', sans-serif;
    font-weight: 400;
    font-size: 16px;
    line-height: 22px;
    color: #666666;
  }
}

// Animations
@keyframes fadeSlideUp {
  from {
    opacity: 0;
    transform: translateY(16px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes slowRotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}
</style>
