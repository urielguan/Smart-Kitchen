<template>
  <el-dialog
    v-model="visible"
    width="758px"
    destroy-on-close
    :show-close="false"
    class="recipe-detail-dialog"
    @close="handleClose"
  >
    <template #header>
      <div class="dialog-header">
        <div class="header-content">
          <h3>菜谱详情</h3>
          <button class="close-btn" @click="handleClose">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/>
              <line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        <div class="header-divider"></div>
      </div>
    </template>

    <div v-if="recipe" class="recipe-detail">
      <!-- 概览卡片 top:88px -->
      <div class="detail-overview-card">
        <div class="detail-overview-media">
          <span v-if="!recipe.imageUrl" class="detail-overview-placeholder">菜谱</span>
          <el-image v-else :src="recipe.imageUrl" fit="cover" class="detail-overview-image" />
        </div>
        <div class="detail-overview-main">
          <div class="detail-overview-title-row">
            <h2 class="detail-overview-title">{{ recipe.menuName }}</h2>
            <div class="detail-status-badge" :class="recipe.status">
              <span class="detail-status-dot"></span>
              {{ recipe.status === 'active' ? '启用中' : '已停用' }}
            </div>
          </div>
          <div class="detail-overview-meta-row">
            <span class="detail-overview-code">{{ recipe.menuCode }}</span>
            <div class="detail-overview-category">
              <span class="detail-overview-category-dot" :style="{ background: getCategoryColor(recipe.menuCategory) }"></span>
              <span>{{ recipe.categoryName }}</span>
            </div>
          </div>
          <p class="detail-overview-description" :class="{ 'detail-overview-description--empty': !recipe.description }">{{ recipe.description || '暂无描述' }}</p>
          <div class="detail-overview-stats-row">
            <div class="detail-overview-stat">
              <span class="detail-overview-stat-value">{{ recipe.servingSize || '-' }}g</span>
              <span class="detail-overview-stat-label">分量</span>
            </div>
            <div class="detail-overview-divider"></div>
            <div class="detail-overview-stat">
              <span class="detail-overview-stat-value">¥{{ recipe.unitCost?.toFixed(2) || '-' }}</span>
              <span class="detail-overview-stat-label">单份成本</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 烹饪参数 top:272px -->
      <section class="detail-section detail-section--params">
        <div class="detail-section-heading">
          <span class="detail-section-bar"></span>
          <span class="detail-section-title">烹饪参数</span>
        </div>
        <div class="detail-params-grid">
          <div class="detail-param-card detail-param-card--time">
            <div class="detail-param-icon">⏱</div>
            <div class="detail-param-content">
              <div class="detail-param-value-row">
                <span class="detail-param-value">{{ recipe.cookingTime }}</span>
                <span class="detail-param-unit">分钟</span>
              </div>
              <span class="detail-param-label">烹饪时长</span>
            </div>
          </div>
          <div class="detail-param-card detail-param-card--temp">
            <div class="detail-param-icon">🌡</div>
            <div class="detail-param-content">
              <div class="detail-param-value-row">
                <span class="detail-param-value">{{ recipe.cookingTempMin }}~{{ recipe.cookingTempMax }}</span>
                <span class="detail-param-unit">℃</span>
              </div>
              <span class="detail-param-label">目标温度</span>
            </div>
          </div>
        </div>
      </section>

      <!-- 营养成分 top:402px -->
      <section class="detail-section detail-section--nutrition">
        <div class="detail-section-heading detail-section-heading--between">
          <div class="detail-section-heading-left">
            <span class="detail-section-bar"></span>
            <span class="detail-section-title">营养成分</span>
          </div>
        </div>
        <div class="detail-chip detail-chip--blue">每100g</div>
        <div class="detail-nutrition-grid">
          <div class="detail-nutrition-card" v-for="item in nutritionItems" :key="item.key">
            <div class="detail-nutrition-icon">{{ item.icon }}</div>
            <div class="detail-nutrition-value-row">
              <span class="detail-nutrition-value">{{ getNutritionValue(item.key) }}</span>
              <span class="detail-nutrition-unit">{{ item.unit }}</span>
            </div>
            <span class="detail-nutrition-label">{{ item.label }}</span>
          </div>
        </div>
        <div v-if="(recipe.vitaminInfo && hasVitaminData) || (recipe.mineralInfo && hasMineralData)" class="detail-nutrition-extra">
          <div v-if="recipe.vitaminInfo && hasVitaminData" class="detail-extra-block">
            <span class="detail-extra-title">维生素</span>
            <div class="detail-extra-tags">
              <span v-if="recipe.vitaminInfo.vitaminA" class="detail-extra-tag">维A {{ recipe.vitaminInfo.vitaminA }}μg</span>
              <span v-if="recipe.vitaminInfo.vitaminB1" class="detail-extra-tag">维B1 {{ recipe.vitaminInfo.vitaminB1 }}mg</span>
              <span v-if="recipe.vitaminInfo.vitaminB2" class="detail-extra-tag">维B2 {{ recipe.vitaminInfo.vitaminB2 }}mg</span>
              <span v-if="recipe.vitaminInfo.vitaminC" class="detail-extra-tag">维C {{ recipe.vitaminInfo.vitaminC }}mg</span>
              <span v-if="recipe.vitaminInfo.vitaminD" class="detail-extra-tag">维D {{ recipe.vitaminInfo.vitaminD }}μg</span>
              <span v-if="recipe.vitaminInfo.vitaminE" class="detail-extra-tag">维E {{ recipe.vitaminInfo.vitaminE }}mg</span>
            </div>
          </div>
          <div v-if="recipe.mineralInfo && hasMineralData" class="detail-extra-block">
            <span class="detail-extra-title">矿物质</span>
            <div class="detail-extra-tags">
              <span v-if="recipe.mineralInfo.calcium" class="detail-extra-tag">钙 {{ recipe.mineralInfo.calcium }}mg</span>
              <span v-if="recipe.mineralInfo.iron" class="detail-extra-tag">铁 {{ recipe.mineralInfo.iron }}mg</span>
              <span v-if="recipe.mineralInfo.zinc" class="detail-extra-tag">锌 {{ recipe.mineralInfo.zinc }}mg</span>
            </div>
          </div>
        </div>
      </section>

      <!-- 所需食材 top:565px -->
      <section class="detail-section detail-section--ingredients">
        <div class="detail-section-heading">
          <div class="detail-section-heading-left">
            <span class="detail-section-bar"></span>
            <span class="detail-section-title">所需食材</span>
            <div class="detail-chip detail-chip--red">{{ recipe.ingredients?.length || 0 }}种</div>
          </div>
        </div>
        <div class="detail-ingredient-list">
          <div
            v-for="(ing, index) in recipe.ingredients"
            :key="index"
            class="detail-ingredient-item"
          >
            <div class="detail-ingredient-text">
              <span class="detail-ingredient-name">{{ ing.materialName }}</span>
              <span class="detail-ingredient-meta">{{ ing.quantity }}{{ ing.unit }}</span>
            </div>
            <div class="detail-ingredient-side">
              <span class="detail-ingredient-spec">{{ ing.materialSpec || '-' }}</span>
              <span class="detail-ingredient-badge" :class="ing.isMain ? 'main' : 'sub'">{{ ing.isMain ? '主料' : '辅料' }}</span>
            </div>
          </div>
        </div>
      </section>

      <!-- 制作步骤 top:681px -->
      <section class="detail-section detail-section--steps">
        <div class="detail-section-heading detail-section-heading--between">
          <div class="detail-section-heading-left">
            <span class="detail-section-bar"></span>
            <span class="detail-section-title">制作步骤</span>
          </div>
          <button class="detail-mini-action detail-mini-action--optimize" :disabled="aiOptimizationLoading" @click="handleAIOptimization">
            {{ aiOptimizationLoading ? '分析中...' : 'AI智能菜谱优化' }}
          </button>
        </div>
        <div class="detail-steps-card">
          <div v-if="!recipe.cookingSteps" class="detail-steps-empty">暂无制作步骤</div>
          <pre v-else class="detail-steps-text">{{ recipe.cookingSteps }}</pre>
        </div>
      </section>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <div class="footer-divider"></div>
        <div class="footer-buttons">
          <button class="btn-close" @click="handleClose">关闭</button>
          <button class="btn-edit" v-permission="RECIPE_PERMISSIONS.EDIT" @click="$emit('edit', recipe)">编辑</button>
        </div>
      </div>
    </template>
  </el-dialog>

  <!-- AI营养分析弹窗 -->
  <el-dialog
    v-model="aiNutritionVisible"
    width="600px"
    title="AI营养分析结果"
    append-to-body
    class="ai-dialog"
  >
    <div v-if="aiNutritionResult" class="ai-nutrition-result">
      <div class="result-header">
        <h4>{{ aiNutritionResult.recipeName }}</h4>
        <span class="analysis-time">分析时间：{{ formatTime(aiNutritionResult.analysisTime) }}</span>
      </div>
      <div class="nutrition-grid">
        <div class="nutrition-item">
          <span class="label">热量</span>
          <span class="value">{{ aiNutritionResult.nutritionInfo?.calories || 0 }} <small>千卡</small></span>
        </div>
        <div class="nutrition-item">
          <span class="label">蛋白质</span>
          <span class="value">{{ aiNutritionResult.nutritionInfo?.protein || 0 }} <small>g</small></span>
        </div>
        <div class="nutrition-item">
          <span class="label">碳水化合物</span>
          <span class="value">{{ aiNutritionResult.nutritionInfo?.carbohydrate || 0 }} <small>g</small></span>
        </div>
        <div class="nutrition-item">
          <span class="label">脂肪</span>
          <span class="value">{{ aiNutritionResult.nutritionInfo?.fat || 0 }} <small>g</small></span>
        </div>
      </div>
      <div v-if="aiNutritionResult.vitaminInfo" class="vitamin-section">
        <h5>维生素含量</h5>
        <div class="vitamin-grid">
          <span v-if="aiNutritionResult.vitaminInfo.vitaminA">维A: {{ aiNutritionResult.vitaminInfo.vitaminA }} μg</span>
          <span v-if="aiNutritionResult.vitaminInfo.vitaminB1">维B1: {{ aiNutritionResult.vitaminInfo.vitaminB1 }} mg</span>
          <span v-if="aiNutritionResult.vitaminInfo.vitaminB2">维B2: {{ aiNutritionResult.vitaminInfo.vitaminB2 }} mg</span>
          <span v-if="aiNutritionResult.vitaminInfo.vitaminC">维C: {{ aiNutritionResult.vitaminInfo.vitaminC }} mg</span>
        </div>
      </div>
      <div v-if="aiNutritionResult.mineralInfo" class="mineral-section">
        <h5>矿物质含量</h5>
        <div class="mineral-grid">
          <span v-if="aiNutritionResult.mineralInfo.calcium">钙: {{ aiNutritionResult.mineralInfo.calcium }} mg</span>
          <span v-if="aiNutritionResult.mineralInfo.iron">铁: {{ aiNutritionResult.mineralInfo.iron }} mg</span>
          <span v-if="aiNutritionResult.mineralInfo.zinc">锌: {{ aiNutritionResult.mineralInfo.zinc }} mg</span>
        </div>
      </div>
    </div>
  </el-dialog>

  <!-- AI智能菜谱优化弹窗 -->
  <el-dialog
    v-model="aiOptimizationVisible"
    width="900px"
    :show-close="false"
    append-to-body
    class="ai-dialog ai-optimization-dialog"
  >
    <!-- Custom Header -->
    <template #header>
      <div class="ai-dialog-header">
        <div class="header-left">
          <h3>AI智能菜谱优化</h3>
        </div>
        <button class="close-btn" @click="aiOptimizationVisible = false">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18"/>
            <line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
        </button>
      </div>
    </template>

    <div class="ai-optimization-content">
      <!-- Loading State -->
      <div v-if="aiOptimizationLoading" class="loading-container">
        <div class="loading-animation">
          <div class="loading-spinner"></div>
          <div class="loading-pulse"></div>
        </div>
        <p class="loading-text">AI正在分析菜谱数据...</p>
        <p class="loading-hint">正在综合成本、营养、投诉等多维度数据分析</p>
      </div>

      <!-- Results Section -->
      <div v-else class="ai-optimization-result">
        <!-- 4 Info Cards -->
        <div class="info-cards">
          <div class="info-card cost-card">
            <div class="card-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M12 2v20M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/>
              </svg>
            </div>
            <div class="card-content">
              <span class="card-label">预估食材成本</span>
              <span class="card-value">¥{{ (aiOptimizationResult.comprehensiveDashboard?.costPercentVsAvg * 2.5 || 125).toFixed(2) }}</span>
              <span class="card-trend" :class="aiOptimizationResult.comprehensiveDashboard?.costPercentVsAvg > 100 ? 'up' : 'down'">
                {{ aiOptimizationResult.comprehensiveDashboard?.costPercentVsAvg > 100 ? '↑ 较均价高' : '↓ 较均价低' }}
              </span>
            </div>
          </div>
          <div class="info-card nutrition-card">
            <div class="card-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z"/>
              </svg>
            </div>
            <div class="card-content">
              <span class="card-label">营养评分</span>
              <span class="card-value" :class="getScoreClass(aiOptimizationResult.comprehensiveDashboard?.nutritionScore)">
                {{ aiOptimizationResult.comprehensiveDashboard?.nutritionScore || 70 }}
              </span>
              <span class="card-trend neutral">/ 100分</span>
            </div>
          </div>
          <div class="info-card rating-card">
            <div class="card-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
              </svg>
            </div>
            <div class="card-content">
              <span class="card-label">平均评分</span>
              <span class="card-value">{{ aiOptimizationResult.comprehensiveDashboard?.reviewScore?.toFixed(1) || '4.0' }}</span>
              <span class="card-trend neutral">
                <span class="stars">
                  <span v-for="i in 5" :key="i" class="star" :class="{ filled: i <= Math.round(aiOptimizationResult.comprehensiveDashboard?.reviewScore || 4) }">★</span>
                </span>
              </span>
            </div>
          </div>
          <div class="info-card complaint-card">
            <div class="card-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
              </svg>
            </div>
            <div class="card-content">
              <span class="card-label">投诉反馈</span>
              <span class="card-value" :class="{ warning: aiOptimizationResult.comprehensiveDashboard?.complaintCount > 3 }">
                {{ aiOptimizationResult.comprehensiveDashboard?.complaintCount || 0 }}
              </span>
              <span class="card-trend" :class="aiOptimizationResult.comprehensiveDashboard?.complaintCount > 3 ? 'up' : 'down'">
                {{ aiOptimizationResult.comprehensiveDashboard?.complaintCount > 3 ? '需关注' : '正常' }}
              </span>
            </div>
          </div>
        </div>

        <!-- 成本分析 -->
        <div class="analysis-section cost-analysis">
          <div class="section-title-row">
            <div class="section-icon cost">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M12 2v20M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/>
              </svg>
            </div>
            <h5>成本分析</h5>
          </div>
          <div class="analysis-content">
            <div v-if="aiOptimizationResult.costAnalysis?.recentPurchases?.length" class="recent-purchases">
              <div class="subsection-title">最近采购记录</div>
              <div class="purchase-table">
                <div class="purchase-header">
                  <span class="col-name">食材名称</span>
                  <span class="col-price">单价</span>
                  <span class="col-date">采购日期</span>
                </div>
                <div v-for="(item, idx) in aiOptimizationResult.costAnalysis.recentPurchases" :key="idx" class="purchase-row">
                  <span class="col-name">{{ item.materialName }}</span>
                  <span class="col-price">¥{{ item.unitPrice?.toFixed(2) }}</span>
                  <span class="col-date">{{ formatDate(item.purchaseDate) }}</span>
                </div>
              </div>
            </div>
            <div v-if="aiOptimizationResult.costAnalysis?.highCostAlerts?.length" class="high-cost-alerts">
              <div class="subsection-title warning">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
                  <line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/>
                </svg>
                高成本食材预警
              </div>
              <div class="alert-list">
                <div v-for="(alert, idx) in aiOptimizationResult.costAnalysis.highCostAlerts" :key="idx" class="alert-item">
                  <div class="alert-header">
                    <span class="material-name">{{ alert.materialName }}</span>
                    <div class="price-compare">
                      <span class="current-price">当前: ¥{{ alert.currentPrice?.toFixed(2) }}</span>
                      <span class="avg-price">均价: ¥{{ alert.avgPrice?.toFixed(2) }}</span>
                      <span class="price-diff">+{{ ((alert.currentPrice - alert.avgPrice) / alert.avgPrice * 100).toFixed(1) }}%</span>
                    </div>
                  </div>
                  <div class="alert-reason">{{ alert.reason }}</div>
                  <div class="alert-suggestion">{{ alert.aiSuggestion }}</div>
                </div>
              </div>
            </div>
            <div v-if="!aiOptimizationResult.costAnalysis?.recentPurchases?.length && !aiOptimizationResult.costAnalysis?.highCostAlerts?.length" class="empty-section">
              暂无成本分析数据
            </div>
          </div>
        </div>

        <!-- 投诉反馈分析 -->
        <div class="analysis-section complaint-analysis">
          <div class="section-title-row">
            <div class="section-icon complaint">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
              </svg>
            </div>
            <h5>投诉反馈分析</h5>
          </div>
          <div class="analysis-content">
            <div class="complaint-chart">
              <div class="chart-bar-item">
                <div class="bar-label">
                  <span class="bar-name">口味问题</span>
                  <span class="bar-value">{{ aiOptimizationResult.complaintAnalysis?.tasteIssues || 0 }}</span>
                </div>
                <div class="bar-track">
                  <div class="bar-fill taste" :style="{ width: getBarWidth(aiOptimizationResult.complaintAnalysis?.tasteIssues, aiOptimizationResult.complaintAnalysis) }"></div>
                </div>
              </div>
              <div class="chart-bar-item">
                <div class="bar-label">
                  <span class="bar-name">质量问题</span>
                  <span class="bar-value">{{ aiOptimizationResult.complaintAnalysis?.qualityIssues || 0 }}</span>
                </div>
                <div class="bar-track">
                  <div class="bar-fill quality" :style="{ width: getBarWidth(aiOptimizationResult.complaintAnalysis?.qualityIssues, aiOptimizationResult.complaintAnalysis) }"></div>
                </div>
              </div>
              <div class="chart-bar-item">
                <div class="bar-label">
                  <span class="bar-name">份量问题</span>
                  <span class="bar-value">{{ aiOptimizationResult.complaintAnalysis?.portionIssues || 0 }}</span>
                </div>
                <div class="bar-track">
                  <div class="bar-fill portion" :style="{ width: getBarWidth(aiOptimizationResult.complaintAnalysis?.portionIssues, aiOptimizationResult.complaintAnalysis) }"></div>
                </div>
              </div>
              <div class="chart-bar-item">
                <div class="bar-label">
                  <span class="bar-name">其他问题</span>
                  <span class="bar-value">{{ aiOptimizationResult.complaintAnalysis?.otherIssues || 0 }}</span>
                </div>
                <div class="bar-track">
                  <div class="bar-fill other" :style="{ width: getBarWidth(aiOptimizationResult.complaintAnalysis?.otherIssues, aiOptimizationResult.complaintAnalysis) }"></div>
                </div>
              </div>
            </div>
            <div v-if="aiOptimizationResult.complaintAnalysis?.complaintSuggestions" class="complaint-suggestions">
              <div class="subsection-title">改进建议</div>
              <p>{{ aiOptimizationResult.complaintAnalysis.complaintSuggestions }}</p>
            </div>
          </div>
        </div>

        <!-- AI优化建议 -->
        <div class="analysis-section suggestions-analysis">
          <div class="section-title-row">
            <div class="section-icon suggestion">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
                <line x1="12" y1="17" x2="12.01" y2="17"/>
              </svg>
            </div>
            <h5>AI优化建议</h5>
          </div>
          <div class="analysis-content">
            <div class="suggestions-list">
              <div
                v-for="(item, index) in aiOptimizationResult.optimizationSuggestions"
                :key="index"
                class="suggestion-item"
                :class="item.priority"
              >
                <div class="suggestion-header">
                  <div class="suggestion-title-row">
                    <span class="suggestion-name">{{ item.suggestionName }}</span>
                    <el-tag
                      :type="item.priority === 'high' ? 'danger' : item.priority === 'medium' ? 'warning' : 'success'"
                      size="small"
                      class="priority-tag"
                    >
                      {{ item.priority === 'high' ? '高优先' : item.priority === 'medium' ? '中优先' : '低优先' }}
                    </el-tag>
                    <span class="source-tag">{{ item.source }}</span>
                  </div>
                </div>
                <p class="suggestion-desc">{{ item.description }}</p>
                <p v-if="item.improvementTrend" class="suggestion-trend">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="23 6 13.5 15.5 8.5 10.5 1 18"/>
                    <polyline points="17 6 23 6 23 12"/>
                  </svg>
                  {{ item.improvementTrend }}
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { Recipe, RecipeCategory, RecipeIngredient, RecipeNutritionResult } from '@/types/recipe'
import { RECIPE_CATEGORY_MAP } from '@/constants/recipe'
import { RECIPE_PERMISSIONS } from '@/constants/permission'
import { getAINutritionAnalysis, getAIOptimizationAnalysis } from '@/api/modules/recipe'
import { ElMessage } from 'element-plus'

/** AI营养分析结果类型 */
interface AINutritionResult {
  recipeId: number
  recipeName: string
  nutritionInfo: {
    protein: number
    carbohydrate: number
    fat: number
    calories: number
  }
  vitaminInfo?: {
    vitaminA?: number
    vitaminB1?: number
    vitaminB2?: number
    vitaminC?: number
  }
  mineralInfo?: {
    calcium?: number
    iron?: number
    zinc?: number
  }
  analysisTime: string
}

const props = defineProps<{
  modelValue: boolean
  recipe: Recipe | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  edit: [recipe: Recipe]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

/** 营养成分配置 */
const nutritionItems = [
  { key: 'calories', label: '热量', unit: '千卡', icon: '🔥', color: 'linear-gradient(135deg, #ff9a56 0%, #ff6b6b 100%)' },
  { key: 'protein', label: '蛋白质', unit: 'g', icon: '💪', color: 'linear-gradient(135deg, #c75b39 0%, #e8a090 100%)' },
  { key: 'carbohydrate', label: '碳水', unit: 'g', icon: '🌾', color: 'linear-gradient(135deg, #d4a574 0%, #e8c09a 100%)' },
  { key: 'fat', label: '脂肪', unit: 'g', icon: '🥑', background: 'linear-gradient(135deg, #7a9e7e 0%, #a8c4aa 100%)', color: 'linear-gradient(135deg, #7a9e7e 0%, #a8c4aa 100%)' },
  { key: 'sodium', label: '钠', unit: 'mg', icon: '🧂', color: 'linear-gradient(135deg, #8b8178 0%, #b0a69e 100%)' },
  { key: 'fiber', label: '膳食纤维', unit: 'g', icon: '🥬', color: 'linear-gradient(135deg, #4a7c59 0%, #7a9e7e 100%)' }
]

/** 获取营养值 */
const getNutritionValue = (key: string) => {
  const value = props.recipe?.nutritionInfo?.[key as keyof typeof props.recipe.nutritionInfo]
  return formatNumber(value)
}

/** 格式化数字，保留1位小数 */
const formatNumber = (value: number | undefined | null): string => {
  if (value === undefined || value === null) return '0'
  return Number(value).toFixed(1).replace(/\.0$/, '')
}

/** 检查是否有维生素数据 */
const hasVitaminData = computed(() => {
  if (!props.recipe?.vitaminInfo) return false
  const vi = props.recipe.vitaminInfo
  return !!(vi.vitaminA || vi.vitaminB1 || vi.vitaminB2 || vi.vitaminC || vi.vitaminD || vi.vitaminE)
})

/** 检查是否有矿物质数据 */
const hasMineralData = computed(() => {
  if (!props.recipe?.mineralInfo) return false
  const mi = props.recipe.mineralInfo
  return !!(mi.calcium || mi.iron || mi.zinc)
})

const router = useRouter()

/** 营养计算结果（基于当前菜谱同步） */
const nutritionResult = ref<RecipeNutritionResult | null>(null)
const nutritionResultLoading = ref(false)

/** AI营养分析结果 */
const aiNutritionResult = ref<AINutritionResult | null>(null)
const aiNutritionLoading = ref(false)
const aiNutritionVisible = ref(false)

const nutritionCompletenessText = computed(() => {
  if (nutritionResult.value?.dataCompleteness === undefined || nutritionResult.value?.dataCompleteness === null) return '未计算'
  return `${nutritionResult.value.dataCompleteness}%`
})

const nutritionMissingText = computed(() => {
  if (!nutritionResult.value?.missingMaterials?.length) return '当前菜谱食材已具备营养映射'
  return `缺少 ${nutritionResult.value.missingMaterialCount || nutritionResult.value.missingMaterials.length} 个物料映射：${nutritionResult.value.missingMaterials.join('、')}`
})

const hasMeaningfulNutrition = computed(() => {
  const values = [
    nutritionResult.value?.calories,
    nutritionResult.value?.protein,
    nutritionResult.value?.carbohydrate,
    nutritionResult.value?.fat,
    nutritionResult.value?.sodium,
    nutritionResult.value?.fiber,
    nutritionResult.value?.vitaminA,
    nutritionResult.value?.vitaminB1,
    nutritionResult.value?.vitaminB2,
    nutritionResult.value?.vitaminC,
    nutritionResult.value?.vitaminD,
    nutritionResult.value?.vitaminE,
    nutritionResult.value?.calcium,
    nutritionResult.value?.iron,
    nutritionResult.value?.zinc
  ]
  return values.some(value => value !== null && value !== undefined && Number(value) > 0)
})

const buildNutritionResultFromRecipe = (recipe: Recipe | null): RecipeNutritionResult | null => {
  if (!recipe?.id) return null

  const ingredients = recipe.ingredients || []
  const fallbackMissingMaterials = ingredients
    .filter(ingredient => !ingredient.foodItemId && !ingredient.foodItemName && !ingredient.nutritionSourceType)
    .map(ingredient => ingredient.materialName || '未命名物料')
  const missingMaterials = recipe.missingMaterials?.length ? recipe.missingMaterials : fallbackMissingMaterials
  const totalIngredients = ingredients.length
  const resolvedIngredients = Math.max(totalIngredients - missingMaterials.length, 0)
  const dataCompleteness = recipe.dataCompleteness ?? (
    totalIngredients === 0
      ? 0
      : Number(((resolvedIngredients * 100) / totalIngredients).toFixed(2))
  )

  return {
    recipeId: recipe.id,
    calories: recipe.nutritionInfo?.calories ?? 0,
    protein: recipe.nutritionInfo?.protein ?? 0,
    carbohydrate: recipe.nutritionInfo?.carbohydrate ?? 0,
    fat: recipe.nutritionInfo?.fat ?? 0,
    sodium: recipe.nutritionInfo?.sodium ?? 0,
    fiber: recipe.nutritionInfo?.fiber ?? 0,
    vitaminA: recipe.vitaminInfo?.vitaminA ?? 0,
    vitaminB1: recipe.vitaminInfo?.vitaminB1 ?? 0,
    vitaminB2: recipe.vitaminInfo?.vitaminB2 ?? 0,
    vitaminC: recipe.vitaminInfo?.vitaminC ?? 0,
    vitaminD: recipe.vitaminInfo?.vitaminD ?? 0,
    vitaminE: recipe.vitaminInfo?.vitaminE ?? 0,
    calcium: recipe.mineralInfo?.calcium ?? 0,
    iron: recipe.mineralInfo?.iron ?? 0,
    zinc: recipe.mineralInfo?.zinc ?? 0,
    nutritionScore: recipe.nutritionScore,
    passStatus: missingMaterials.length > 0 ? 'warn' : undefined,
    dataCompleteness,
    missingMaterialCount: recipe.missingMaterialCount ?? missingMaterials.length,
    missingMaterials
  }
}

const syncNutritionResultFromRecipe = () => {
  if (!props.recipe?.id || !visible.value) return
  nutritionResultLoading.value = true
  try {
    nutritionResult.value = buildNutritionResultFromRecipe(props.recipe)
  } finally {
    nutritionResultLoading.value = false
  }
}

/** AI智能菜谱优化结果类型 */
interface AIOptimizationResult {
  recipeId: number
  recipeName: string
  comprehensiveDashboard?: {
    costPercentVsAvg: number
    nutritionScore: number
    reviewScore: number
    complaintCount: number
  }
  costAnalysis?: {
    recentPurchases: Array<{
      materialName: string
      unitPrice: number
      purchaseDate: string
    }>
    highCostAlerts: Array<{
      materialName: string
      reason: string
      currentPrice: number
      avgPrice: number
      aiSuggestion: string
    }>
  }
  complaintAnalysis?: {
    tasteIssues: number
    qualityIssues: number
    portionIssues: number
    otherIssues: number
    complaintSuggestions: string
    recentReviews?: Array<{
      score: number
      content: string
      reviewTime: string
    }>
  }
  optimizationSuggestions?: Array<{
    suggestionName: string
    source: string
    priority: string
    description: string
    improvementTrend: string
  }>
}

/** AI智能菜谱优化结果 */
const aiOptimizationResult = ref<AIOptimizationResult | null>(null)
const aiOptimizationLoading = ref(false)
const aiOptimizationVisible = ref(false)

/** 投诉分析类型 */
interface ComplaintAnalysis {
  tasteIssues: number
  qualityIssues: number
  portionIssues: number
  otherIssues: number
}

/** 获取类别颜色 */
const getCategoryColor = (category: RecipeCategory) => {
  return RECIPE_CATEGORY_MAP[category]?.color || '#909399'
}

/** 获取评分颜色 */
const getScoreColor = (score: number) => {
  if (score >= 80) return '#4a7c59'
  if (score >= 60) return '#d4a574'
  return '#c75b39'
}

/** 格式化时间 */
const formatTime = (time: string) => {
  if (!time) return '-'
  return time.replace('T', ' ').substring(0, 19)
}

/** 关闭弹窗 */
const handleClose = () => {
  visible.value = false
}

/** AI营养分析 */
const handleAINutrition = async () => {
  if (!props.recipe?.id) return
  aiNutritionLoading.value = true
  try {
    const res = await getAINutritionAnalysis(props.recipe.id)
    if (res.code === 'SUCCESS') {
      aiNutritionResult.value = res.data
      aiNutritionVisible.value = true
    }
  } catch (error) {
    ElMessage.error('AI营养分析失败')
  } finally {
    aiNutritionLoading.value = false
  }
}

const goToMaterialNutrition = async () => {
  visible.value = false
  await router.push({
    path: '/material',
    query: {
      nutritionStatus: 'unmapped',
      keyword: nutritionResult.value?.missingMaterials?.[0]
    }
  })
}

watch(() => [props.modelValue, props.recipe] as const, ([show, recipe]) => {
  if (show && recipe?.id) {
    syncNutritionResultFromRecipe()
  } else if (!show) {
    nutritionResult.value = null
  }
}, { immediate: true })

/** AI智能菜谱优化 - 直接开始分析 */
const handleAIOptimization = async () => {
  if (!props.recipe?.id) return
  aiOptimizationResult.value = null
  aiOptimizationVisible.value = true
  // 直接开始分析
  aiOptimizationLoading.value = true
  try {
    const res = await getAIOptimizationAnalysis(props.recipe.id)
    if (res.code === 'SUCCESS') {
      aiOptimizationResult.value = res.data
    }
  } catch (error) {
    ElMessage.error('AI智能菜谱优化分析失败')
  } finally {
    aiOptimizationLoading.value = false
  }
}

/** 计算投诉分析柱状图宽度 */
const getBarWidth = (value: number | undefined, analysis: ComplaintAnalysis | undefined) => {
  if (!analysis) return '0%'
  const total = (analysis.tasteIssues || 0) + (analysis.qualityIssues || 0) + (analysis.portionIssues || 0) + (analysis.otherIssues || 0)
  if (total === 0) return '0%'
  return `${((value || 0) / total) * 100}%`
}

/** 获取评分类名 */
const getScoreClass = (score: number | undefined) => {
  if (!score) return ''
  if (score >= 80) return 'excellent'
  if (score >= 60) return 'good'
  return 'needs-improvement'
}

/** 格式化日期 */
const formatDate = (dateStr: string | undefined) => {
  if (!dateStr) return '-'
  return dateStr.replace('T', ' ').substring(0, 10)
}
</script>

<style lang="scss" scoped>
$terracotta: #c75b39;
$terracotta-light: #e8a090;
$cream: #faf7f2;
$warm-gray: #8b8178;

/* ── Dialog Frame ── */
:deep(.recipe-detail-dialog .el-dialog) {
  position: absolute;
  width: 758px;
  height: 780px;
  left: calc(50% - 758px / 2);
  top: calc(50% - 780px / 2);
  margin: 0;
  background: #ffffff;
  border-radius: 12px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

:deep(.recipe-detail-dialog .el-dialog__header),
:deep(.recipe-detail-dialog .el-dialog__footer) {
  padding: 0;
  margin: 0;
}

:deep(.recipe-detail-dialog .el-dialog__header) {
  padding-right: 0 !important;
}

:deep(.recipe-detail-dialog .el-dialog__body) {
  flex: 1;
  min-height: 0;
  padding: 0;
}

/* ── Header ── */
.dialog-header {
  position: relative;
  width: 100%;
  height: 48px;
  padding: 0;
  box-sizing: border-box;
  background: #ffffff;
}

.header-content {
  position: absolute;
  left: 24px;
  top: 0;
  width: 710px;
  height: 48px;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 0;
  box-sizing: border-box;

  h3 {
    margin: 0;
    height: 32px;
    font-weight: 500;
    font-size: 20px;
    line-height: 32px;
    color: #000000;
  }
}

.header-divider {
  position: absolute;
  left: 0;
  top: 48px;
  width: 100%;
  height: 0;
  border-top: 1px solid #e1e2e9;
}

.close-btn {
  width: 32px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 4px;
  border: none;
  border-radius: 8px;
  background: #fff2e2;
  color: #1c1d22;
  cursor: pointer;
  flex-shrink: 0;

  svg {
    width: 24px;
    height: 24px;
  }
}

/* ── Body Content ── */
.recipe-detail {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 16px 24px 0;
  background: #ffffff;
  box-sizing: border-box;
}

.detail-overview-card,
.detail-section {
  box-sizing: border-box;
}

/* ── Overview Card ── top:88px in Figma → 16px padding-top accounts for header+divider */
.detail-overview-card {
  min-height: 160px;
  display: flex;
  gap: 20px;
  padding: 16px;
  border: 1px solid #e1e2e9;
  border-radius: 10px;
  background: #ffffff;
}

.detail-overview-media {
  width: 128px;
  height: 128px;
  border-radius: 16px;
  overflow: hidden;
  background: #f5f5f5;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.detail-overview-image {
  width: 100%;
  height: 100%;
}

.detail-overview-placeholder {
  font-size: 20px;
  line-height: 28px;
  color: #8c8c8c;
}

.detail-overview-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.detail-overview-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 34px;
}

.detail-overview-title {
  margin: 0;
  font-weight: 600;
  font-size: 24px;
  line-height: 34px;
  color: #000000;
}

.detail-status-badge {
  height: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  padding: 0 10px;
  border-radius: 100px;
  font-size: 12px;
  line-height: 17px;

  &.active {
    background: rgba(74, 124, 89, 0.1);
    color: #4a7c59;
  }

  &:not(.active) {
    background: rgba(153, 153, 153, 0.12);
    color: #999999;
  }
}

.detail-status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.detail-overview-meta-row {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 8px;
  color: #333333;
  font-size: 14px;
  line-height: 20px;
}

.detail-overview-category {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #999999;
  font-size: 12px;
  line-height: 17px;
}

.detail-overview-category-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.detail-overview-description {
  margin: 8px 0 0;
  font-size: 14px;
  line-height: 20px;
  color: #666666;
  word-break: break-word;

  &--empty {
    color: #999999;
  }
}

.detail-overview-stats-row {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-top: 18px;
}

.detail-overview-stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.detail-overview-stat-value {
  font-weight: 600;
  font-size: 24px;
  line-height: 34px;
  color: #333333;
}

.detail-overview-stat-label {
  font-size: 14px;
  line-height: 20px;
  color: #333333;
}

.detail-overview-divider {
  width: 1px;
  height: 34px;
  background: #c0c5ca;
}

/* ── Section Common ── */
.detail-section {
  margin-top: 24px;
}

.detail-section-heading {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-bottom: 16px;
}

.detail-section-heading--between {
  justify-content: space-between;
}

.detail-section-heading-left {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.detail-section-bar {
  width: 4px;
  height: 16px;
  border-radius: 2px;
  background: #5570f1;
}

.detail-section-title {
  font-weight: 500;
  font-size: 16px;
  line-height: 16px;
  color: #000000;
}

.detail-chip {
  height: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 12px;
  border-radius: 100px;
  font-size: 12px;
  line-height: 17px;

  &--blue {
    background: #eff9ff;
    color: #096dd9;
    margin-bottom: 16px;
  }

  &--red {
    background: #fff1f0;
    color: #cf1322;
  }
}

.detail-mini-action {
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 5px 16px;
  border: 1px solid #5570f1;
  border-radius: 6px;
  background: #ffffff;
  color: #7288fa;
  font-size: 14px;
  line-height: 22px;
  cursor: pointer;

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
}

/* ── Cooking Params ── */
.detail-params-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.detail-param-card {
  min-height: 74px;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 16px;
  border-radius: 10px;
  box-sizing: border-box;

  &--time {
    background: #f6fcfa;
  }

  &--temp {
    background: #fff9f9;
  }
}

.detail-param-icon {
  width: 50px;
  height: 50px;
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  background: rgba(114, 136, 250, 0.08);
  flex-shrink: 0;
}

.detail-param-content {
  display: flex;
  flex-direction: column;
}

.detail-param-value-row {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.detail-param-value {
  font-weight: 600;
  font-size: 24px;
  line-height: 34px;
  color: #333333;
}

.detail-param-unit,
.detail-param-label {
  font-size: 14px;
  line-height: 20px;
  color: #333333;
}

/* ── Nutrition ── */
.detail-nutrition-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 16px;
}

.detail-nutrition-card {
  min-height: 104px;
  border: 1px solid #e1e2e9;
  border-radius: 10px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  padding: 14px 8px 10px;
  box-sizing: border-box;
  background: #ffffff;
}

.detail-nutrition-icon {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: #f5f7fa;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}

.detail-nutrition-value-row {
  display: flex;
  align-items: baseline;
  gap: 2px;
  margin-top: 10px;
}

.detail-nutrition-value {
  font-size: 16px;
  line-height: 22px;
  color: #333333;
}

.detail-nutrition-unit,
.detail-nutrition-label {
  font-size: 14px;
  line-height: 20px;
}

.detail-nutrition-unit {
  color: #333333;
}

.detail-nutrition-label {
  color: #999999;
  margin-top: 6px;
}

.detail-nutrition-extra {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-extra-block {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.detail-extra-title {
  min-width: 56px;
  font-size: 14px;
  line-height: 20px;
  color: #333333;
}

.detail-extra-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.detail-extra-tag {
  display: inline-flex;
  align-items: center;
  height: 28px;
  padding: 0 10px;
  border-radius: 14px;
  background: #f5f7fa;
  color: #53545c;
  font-size: 12px;
  line-height: 17px;
}

/* ── Ingredients ── */
.detail-ingredient-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.detail-ingredient-item {
  min-height: 54px;
  padding: 10px 16px;
  border-radius: 12px;
  background: #f8f7f7;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  box-sizing: border-box;
}

.detail-ingredient-text,
.detail-ingredient-side {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.detail-ingredient-side {
  align-items: flex-end;
  flex-shrink: 0;
}

.detail-ingredient-name,
.detail-ingredient-meta,
.detail-ingredient-spec {
  font-size: 14px;
  line-height: 18px;
}

.detail-ingredient-name {
  color: #667187;
  word-break: break-all;
}

.detail-ingredient-meta,
.detail-ingredient-spec {
  color: #e96466;
}

.detail-ingredient-badge {
  height: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 10px;
  border-radius: 100px;
  font-size: 12px;
  line-height: 17px;

  &.main {
    background: #fff1f0;
    color: #cf1322;
  }

  &.sub {
    background: #fff7e6;
    color: #fa8c16;
  }
}

/* ── Steps ── */
.detail-steps-card {
  width: 100%;
  height: 160px;
  padding: 16px;
  border: 1px solid #e1e2e9;
  border-radius: 10px;
  background: #ffffff;
  box-sizing: border-box;
  overflow-y: auto;
}

.detail-steps-empty {
  font-size: 14px;
  line-height: 20px;
  color: #999999;
}

.detail-steps-text {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 14px;
  line-height: 22px;
  color: #333333;
}

/* ── Footer ── */
.dialog-footer {
  position: relative;
  width: 100%;
  height: 60px;
  padding: 0;
  box-sizing: border-box;
  background: #ffffff;
  display: flex;
  flex-direction: column;
}

.footer-divider {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 0;
  border-top: 1px solid #e1e2e9;
}

.footer-buttons {
  position: absolute;
  right: 24px;
  top: 12px;
  display: flex;
  align-items: center;
  gap: 9px;
}

.btn-close,
.btn-edit {
  box-sizing: border-box;
  height: 32px;
  border-radius: 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.btn-close {
  width: 58px;
  border: 1px solid #bec0ca;
  background: #ffffff;
  box-shadow: 0 2px 0 rgba(0, 0, 0, 0.016);
  color: #53545c;
  font-size: 13px;
  line-height: 22px;
}

.btn-edit {
  width: 60px;
  border: none;
  background: #7288fa;
  box-shadow: 0 2px 0 rgba(0, 0, 0, 0.043);
  color: #ffffff;
  font-size: 14px;
  line-height: 22px;
}

.ai-dialog {
  :deep(.el-dialog) {
    border-radius: 16px;
  }

  :deep(.el-dialog__header) {
    padding: 16px 20px;
    border-bottom: 1px solid rgba(193, 197, 202, 0.4);
    margin: 0;
  }

  :deep(.el-dialog__body) {
    padding: 20px;
    max-height: 60vh;
    overflow-y: auto;
  }
}

.ai-nutrition-result {
  .result-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;

    h4 {
      margin: 0;
      font-size: 18px;
      font-weight: 600;
      color: #1c1d22;
    }

    .analysis-time {
      font-size: 12px;
      color: #8b8178;
    }
  }

  .nutrition-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
    margin-bottom: 20px;
  }

  .nutrition-item {
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 12px;
    background: #ffffff;
    border-radius: 10px;
    border: 1px solid rgba(193, 197, 202, 0.4);

    .label {
      font-size: 12px;
      color: #8b8178;
    }

    .value {
      font-size: 20px;
      font-weight: 700;
      color: #1c1d22;

      small {
        font-size: 12px;
        font-weight: 400;
        color: #8b8178;
      }
    }
  }

  .vitamin-section,
  .mineral-section {
    margin-bottom: 16px;

    h5 {
      margin: 0 0 10px;
      font-size: 14px;
      font-weight: 600;
      color: #1c1d22;
    }
  }

  .vitamin-grid,
  .mineral-grid {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;

    span {
      padding: 6px 12px;
      background: #f5f7fa;
      border-radius: 6px;
      font-size: 13px;
      color: #53545c;
    }
  }
}

.ai-optimization-dialog {
  :deep(.el-dialog__header) {
    padding: 0;
    margin: 0;
  }

  :deep(.el-dialog__body) {
    padding: 0;
    max-height: 75vh;
    overflow-y: auto;
  }
}

.ai-dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 18px 24px;
  background: #ffffff;
  border-bottom: 1px solid #e1e2e9;

  .header-left {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  h3 {
    margin: 0;
    font-size: 18px;
    font-weight: 700;
    color: #1c1d22;
  }

  .close-btn {
    width: 36px;
    height: 36px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: #ffffff;
    border: 1px solid #d9dce3;
    border-radius: 10px;
    cursor: pointer;

    svg {
      width: 18px;
      height: 18px;
      color: #8b8178;
    }
  }
}

.ai-optimization-content {
  padding: 24px;
  background: #f8fafc;
}

.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  text-align: center;
}

.loading-animation {
  position: relative;
  width: 80px;
  height: 80px;
  margin-bottom: 24px;
}

.loading-spinner {
  position: absolute;
  inset: 0;
  border: 3px solid rgba(114, 136, 250, 0.14);
  border-top-color: #7288fa;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

.loading-pulse {
  position: absolute;
  inset: 15px;
  background: linear-gradient(135deg, rgba(114, 136, 250, 0.18) 0%, rgba(85, 112, 241, 0.18) 100%);
  border-radius: 50%;
  animation: pulse 1.5s ease-in-out infinite;
}

.loading-text {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 600;
  color: #1c1d22;
}

.loading-hint {
  margin: 0;
  font-size: 13px;
  color: #8b8178;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes pulse {
  0%,
  100% {
    transform: scale(1);
    opacity: 0.5;
  }

  50% {
    transform: scale(1.1);
    opacity: 0.8;
  }
}

.ai-optimization-result {
  .info-cards {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 16px;
    margin-bottom: 24px;
  }

  .info-card {
    display: flex;
    align-items: flex-start;
    gap: 14px;
    padding: 18px;
    background: #ffffff;
    border-radius: 14px;
    border: 1px solid #e7eaf0;
  }

  .card-icon {
    width: 44px;
    height: 44px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 12px;
    flex-shrink: 0;

    svg {
      width: 22px;
      height: 22px;
    }
  }

  .cost-card .card-icon {
    background: #fff7e6;
    color: #d48806;
  }

  .nutrition-card .card-icon {
    background: #f6ffed;
    color: #389e0d;
  }

  .rating-card .card-icon {
    background: #fff2e8;
    color: #d46b08;
  }

  .complaint-card .card-icon {
    background: #fff1f0;
    color: #cf1322;
  }

  .card-content {
    flex: 1;
    min-width: 0;
  }

  .card-label {
    display: block;
    margin-bottom: 6px;
    font-size: 12px;
    color: #8b8178;
  }

  .card-value {
    display: block;
    margin-bottom: 4px;
    font-size: 24px;
    font-weight: 700;
    line-height: 1.2;
    color: #1c1d22;

    &.excellent {
      color: #389e0d;
    }

    &.good {
      color: #d48806;
    }

    &.needs-improvement,
    &.warning {
      color: #cf1322;
    }
  }

  .card-trend {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;

    &.up {
      color: #cf1322;
    }

    &.down {
      color: #389e0d;
    }

    &.neutral {
      color: #8b8178;
    }
  }

  .stars {
    display: flex;
    gap: 1px;
  }

  .star {
    font-size: 12px;
    color: #fadb14;
    opacity: 0.35;

    &.filled {
      opacity: 1;
    }
  }
}

.analysis-section {
  margin-bottom: 24px;
  background: #ffffff;
  border-radius: 14px;
  border: 1px solid #e7eaf0;
  overflow: hidden;
}

.section-title-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  background: #f8fafc;
  border-bottom: 1px solid #e7eaf0;

  h5 {
    margin: 0;
    font-size: 15px;
    font-weight: 600;
    color: #1c1d22;
  }
}

.section-icon {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;

  svg {
    width: 18px;
    height: 18px;
  }

  &.cost {
    background: #fff7e6;
    color: #d48806;
  }

  &.complaint {
    background: #fff1f0;
    color: #cf1322;
  }

  &.suggestion {
    background: #f6ffed;
    color: #389e0d;
  }
}

.analysis-content {
  padding: 20px;
}

.subsection-title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 12px;
  font-size: 13px;
  font-weight: 500;
  color: #53545c;

  &.warning {
    color: #cf1322;

    svg {
      width: 16px;
      height: 16px;
    }
  }
}

.cost-analysis {
  .purchase-table {
    margin-bottom: 20px;
  }

  .purchase-header,
  .purchase-row {
    display: flex;
    padding: 10px 14px;
    border-radius: 8px;
    font-size: 13px;
  }

  .purchase-header {
    background: #f5f7fa;
    color: #8b8178;
    font-weight: 500;
  }

  .purchase-row {
    margin-top: 6px;
    background: #fafafa;
  }

  .col-name {
    flex: 1;
  }

  .col-price,
  .col-date {
    width: 100px;
    text-align: right;
  }

  .purchase-row .col-name {
    font-weight: 500;
    color: #1c1d22;
  }

  .purchase-row .col-price {
    color: #cf1322;
    font-weight: 600;
  }

  .purchase-row .col-date {
    color: #8b8178;
  }

  .alert-list {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .alert-item {
    padding: 16px;
    background: rgba(255, 107, 107, 0.04);
    border-radius: 12px;
    border-left: 4px solid #cf1322;
  }

  .alert-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    flex-wrap: wrap;
    gap: 10px;
    margin-bottom: 10px;
  }

  .material-name {
    font-weight: 600;
    font-size: 14px;
    color: #1c1d22;
  }

  .price-compare {
    display: flex;
    gap: 12px;
    font-size: 13px;
  }

  .current-price,
  .price-diff {
    color: #cf1322;
  }

  .avg-price {
    color: #389e0d;
  }

  .price-diff {
    font-weight: 600;
  }

  .alert-reason {
    margin-bottom: 8px;
    font-size: 13px;
    color: #53545c;
  }

  .alert-suggestion {
    padding: 10px 12px;
    background: #f6ffed;
    border-radius: 8px;
    font-size: 12px;
    color: #389e0d;
  }

  .empty-section {
    padding: 30px;
    text-align: center;
    color: #8b8178;
    font-size: 13px;
  }
}

.complaint-analysis {
  .complaint-chart {
    display: flex;
    flex-direction: column;
    gap: 14px;
    margin-bottom: 20px;
  }

  .bar-label {
    display: flex;
    justify-content: space-between;
    margin-bottom: 6px;
  }

  .bar-name {
    font-size: 13px;
    color: #1c1d22;
  }

  .bar-value {
    font-size: 13px;
    font-weight: 600;
    color: #cf1322;
  }

  .bar-track {
    height: 8px;
    background: #edf0f5;
    border-radius: 4px;
    overflow: hidden;
  }

  .bar-fill {
    height: 100%;
    border-radius: 4px;
    transition: width 0.5s ease;

    &.taste {
      background: linear-gradient(90deg, #ff7a45 0%, #ff9c6e 100%);
    }

    &.quality {
      background: linear-gradient(90deg, #faad14 0%, #ffc53d 100%);
    }

    &.portion {
      background: linear-gradient(90deg, #52c41a 0%, #73d13d 100%);
    }

    &.other {
      background: linear-gradient(90deg, #8c8c8c 0%, #bfbfbf 100%);
    }
  }

  .complaint-suggestions {
    padding: 16px;
    background: #f8fafc;
    border-radius: 10px;

    .subsection-title {
      margin-bottom: 10px;
    }

    p {
      margin: 0;
      font-size: 13px;
      line-height: 1.6;
      color: #1c1d22;
    }
  }
}

.suggestions-analysis {
  .suggestions-list {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .suggestion-item {
    padding: 18px;
    background: #ffffff;
    border-radius: 12px;
    border: 1px solid #e7eaf0;

    &.high {
      border-left: 4px solid #cf1322;
    }

    &.medium {
      border-left: 4px solid #faad14;
    }

    &.low {
      border-left: 4px solid #52c41a;
    }
  }

  .suggestion-header {
    margin-bottom: 12px;
  }

  .suggestion-title-row {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }

  .suggestion-name {
    font-weight: 600;
    font-size: 14px;
    color: #1c1d22;
  }

  .priority-tag {
    font-size: 11px;
    font-weight: 500;
  }

  .source-tag {
    font-size: 11px;
    padding: 2px 8px;
    background: #f5f7fa;
    color: #8b8178;
    border-radius: 4px;
  }

  .suggestion-desc {
    margin: 0 0 10px;
    font-size: 13px;
    line-height: 1.6;
    color: #1c1d22;
  }

  .suggestion-trend {
    display: flex;
    align-items: center;
    gap: 6px;
    margin: 0;
    padding: 8px 12px;
    background: #f6ffed;
    border-radius: 8px;
    font-size: 12px;
    color: #389e0d;

    svg {
      width: 14px;
      height: 14px;
    }
  }
}
</style>

<style lang="scss">
.recipe-detail-dialog .el-overlay-dialog {
  overflow: hidden;
}
</style>
