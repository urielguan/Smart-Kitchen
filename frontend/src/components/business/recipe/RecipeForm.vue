<template>
  <el-dialog
    v-model="visible"
    width="758px"
    destroy-on-close
    :show-close="false"
    :close-on-click-modal="false"
    align-center
    class="recipe-form-dialog"
    modal-class="recipe-form-overlay"
    @close="handleClose"
  >
    <template #header>
      <div class="dialog-header">
        <div class="dialog-header__content">
          <div class="dialog-header__title">{{ isEdit ? '编辑菜谱' : '新增菜谱' }}</div>
          <button type="button" class="dialog-header__close" @click="handleClose" aria-label="关闭">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>
      </div>
    </template>

    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="96px"
      label-suffix="："
      class="recipe-form"
    >
      <div ref="formBodyRef" class="recipe-form__body">
        <section class="form-section form-section--base">
        <div class="section-heading">
          <span class="section-heading__bar"></span>
          <span class="section-heading__text">基础信息</span>
        </div>

        <div class="form-grid form-grid--base">
          <el-form-item label="菜谱名称" prop="menuName" required class="form-item--fixed">
            <div class="input-wrapper">
              <transition name="input-tip">
                <span v-if="fieldTip === 'menuName'" class="field-length-tip">菜谱名称最多输入30个字</span>
              </transition>
              <el-input
                v-model="formData.menuName"
                placeholder="请输入菜谱名称"
                maxlength="30"
                class="custom-input"
                @input="(val: string) => handleFieldInput('menuName', val, 30)"
                @keydown="(e: KeyboardEvent) => handleFieldKeydown('menuName', formData.menuName, 30, e)"
              />
            </div>
          </el-form-item>

          <el-form-item label="菜谱编码" required class="form-item--fixed">
            <div class="input-wrapper">
              <transition name="input-tip">
                <span v-if="fieldTip === 'menuCode'" class="field-length-tip">菜谱编码最多输入20个字符</span>
              </transition>
              <el-input
                v-model="formData.menuCode"
                placeholder="系统自动生成"
                maxlength="20"
                :disabled="isEdit"
                class="custom-input"
                :class="{ 'is-error': menuCodeError }"
                @input="(val: string) => handleFieldInput('menuCode', val, 20)"
                @keydown="(e: KeyboardEvent) => handleFieldKeydown('menuCode', formData.menuCode, 20, e)"
              />
            </div>
            <div v-if="menuCodeError" class="field-feedback field-feedback--error">{{ menuCodeError }}</div>
            <div v-else-if="menuCodeChecking" class="field-feedback">检查中...</div>
          </el-form-item>

          <el-form-item label="菜谱类别" prop="menuCategory" required class="form-item--fixed">
            <el-select
              v-model="formData.menuCategory"
              placeholder="请选择菜谱类别"
              class="custom-select"
              :loading="categoryLoading"
            >
              <el-option
                v-for="item in categoryOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="成品份量" required class="form-item--fixed form-item--serving-size">
            <div class="serving-size-control">
              <button
                type="button"
                class="serving-size-control__step"
                aria-label="减少成品份量"
                @click="handleServingSizeStep(-1)"
              >
                <span class="serving-size-control__minus"></span>
              </button>
              <input
                :value="String(formData.servingSize ?? '')"
                type="text"
                inputmode="numeric"
                maxlength="5"
                class="serving-size-control__input"
                placeholder="0"
                @input="handleServingSizeInput"
                @keydown="(e: KeyboardEvent) => handleNumberKeydown('servingSize', String(formData.servingSize ?? ''), 5, e)"
              />
              <button
                type="button"
                class="serving-size-control__step"
                aria-label="增加成品份量"
                @click="handleServingSizeStep(1)"
              >
                <span class="serving-size-control__plus"></span>
              </button>
            </div>
          </el-form-item>
        </div>

        <el-form-item label="菜谱描述" class="form-item--description form-item--full-width">
          <div class="input-wrapper input-wrapper--full">
            <transition name="input-tip">
              <span v-if="fieldTip === 'description'" class="field-length-tip">菜谱描述最多输入500个字</span>
            </transition>
            <el-input
              v-model="formData.description"
              type="textarea"
              :rows="2"
              placeholder="请输入菜谱描述"
              maxlength="500"
              class="custom-textarea custom-textarea--description"
              @input="(val: string) => handleFieldInput('description', val, 500)"
              @keydown="(e: KeyboardEvent) => handleFieldKeydown('description', formData.description, 500, e)"
            />
          </div>
        </el-form-item>
      </section>

      <section class="form-section form-section--ingredients">
        <div class="section-heading section-heading--between">
          <div class="section-heading__left">
            <span class="section-heading__bar"></span>
            <span class="section-heading__text">所需食材</span>
          </div>
          <div class="ingredient-actions">
            <div class="ingredient-count-badge" aria-label="当前食材数量">
              <span class="ingredient-count-badge__text">{{ formData.ingredients.length }}种</span>
            </div>
            <button type="button" class="outline-action-btn" @click="handleAddIngredient">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="12" y1="5" x2="12" y2="19" />
                <line x1="5" y1="12" x2="19" y2="12" />
              </svg>
              添加食材
            </button>
          </div>
        </div>

        <div class="ingredient-toolbar">
          <div class="ingredient-alert">
            <span class="ingredient-alert__icon" aria-hidden="true">
              <svg viewBox="0 0 20 20" fill="none">
                <path
                  d="M10 1.25C5.16875 1.25 1.25 5.16875 1.25 10C1.25 14.8312 5.16875 18.75 10 18.75C14.8312 18.75 18.75 14.8312 18.75 10C18.75 5.16875 14.8312 1.25 10 1.25ZM10.9375 14.375H9.0625V12.5H10.9375V14.375ZM10.9375 10.625H9.0625V5.625H10.9375V10.625Z"
                  fill="currentColor"
                />
              </svg>
            </span>
            <span>选择食材和规格后，单位自动填充。用量根据食材类型智能推荐，可手动调整。</span>
          </div>
        </div>

        <div class="ingredients-form-panel">
          <div class="ingredients-form-head" v-if="formData.ingredients.length > 0">
            <div class="ingredients-col ingredients-col--name">物料名称</div>
            <div class="ingredients-col ingredients-col--spec">规格</div>
            <div class="ingredients-col ingredients-col--quantity">用量</div>
            <div class="ingredients-col ingredients-col--unit">单位</div>
            <div class="ingredients-col ingredients-col--type">类型</div>
            <div class="ingredients-col ingredients-col--action"></div>
          </div>

          <div v-if="formData.ingredients.length > 0" class="ingredients-form-body">
            <div
              v-for="(row, index) in formData.ingredients"
              :key="`${row.materialId ?? 'new'}-${index}`"
              class="ingredient-form-row"
            >
              <div class="ingredients-col ingredients-col--name">
                <el-select
                  v-model="row.materialId"
                  filterable
                  placeholder="请输入物料名称"
                  class="ingredient-field ingredient-field--name"
                  :loading="materialStore.activeListLoading"
                  @change="(val) => handleMaterialChange(val, row)"
                >
                  <el-option
                    v-for="item in materialStore.activeList"
                    :key="item.id"
                    :label="item.materialName"
                    :value="item.id"
                  >
                    <div class="material-option">
                      <span class="material-name">{{ item.materialName }}</span>
                      <span class="material-code">{{ item.materialCode }}</span>
                      <span v-if="item.materialSpec" class="material-spec-tag">{{ item.materialSpec }}</span>
                    </div>
                  </el-option>
                </el-select>
              </div>

              <div class="ingredients-col ingredients-col--spec">
                <div class="ingredient-static-field" :class="{ 'has-value': row.materialSpec }">{{ row.materialSpec || '-' }}</div>
              </div>

              <div class="ingredients-col ingredients-col--quantity">
                <div class="ingredient-quantity-control">
                  <input
                    :value="String(row.quantity ?? '')"
                    type="text"
                    inputmode="numeric"
                    maxlength="4"
                    class="ingredient-quantity-control__input"
                    @input="handleIngredientQuantityInput($event, row)"
                  />
                  <div class="ingredient-quantity-control__controls">
                    <button
                      type="button"
                      class="ingredient-quantity-control__step ingredient-quantity-control__step--up"
                      aria-label="增加用量"
                      @click="handleIngredientQuantityStep(row, 1)"
                    >
                      <svg viewBox="0 0 16 16" fill="none">
                        <path d="M4 10L8 6L12 10" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
                      </svg>
                    </button>
                    <button
                      type="button"
                      class="ingredient-quantity-control__step ingredient-quantity-control__step--down"
                      aria-label="减少用量"
                      @click="handleIngredientQuantityStep(row, -1)"
                    >
                      <svg viewBox="0 0 16 16" fill="none">
                        <path d="M4 6L8 10L12 6" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
                      </svg>
                    </button>
                  </div>
                </div>
              </div>

              <div class="ingredients-col ingredients-col--unit">
                <el-select
                  v-model="row.unit"
                  class="ingredient-field ingredient-field--unit"
                  placeholder="单位"
                  clearable
                  filterable
                  allow-create
                  default-first-option
                >
                  <el-option v-for="u in unitOptions" :key="u" :label="u" :value="u" />
                </el-select>
              </div>

              <div class="ingredients-col ingredients-col--type">
                <el-select v-model="row.isMain" class="ingredient-field ingredient-field--type" placeholder="类型">
                  <el-option :value="true" label="主料" />
                  <el-option :value="false" label="辅料" />
                </el-select>
              </div>

              <div class="ingredients-col ingredients-col--action">
                <button type="button" class="ingredient-remove-btn" @click="handleRemoveIngredient(index)" aria-label="删除食材">
                  <img
                    src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAYAAABzenr0AAAACXBIWXMAAAsTAAALEwEAmpwYAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAOdEVYdFNvZnR3YXJlAEZpZ21hnrGWYwAAAldJREFUeAHtl0Fo02AUgN//J01jHZ00TKys7GD1oh52GHO76+bZEnfw6kVBcHjaRfBsxIunIV48aIcMvFgcqKdpV6ggZoJdRreUKnbFtVuWNWn+3/wTD4K2ScYWBvsueeF//O/9773/vQSBC81mOVhc7AXDECASQbDXCIIJqrqBZmYctGO8UOjbF8N/Q6BUWsNQLMZDMM7AkE4fw4CxCGGBEI8hXHDYDgAPu2CjVIzXvnwc54Sj+sD4xHsIQGAHtBePJtumeYfJNvyEr08f6NLp81ek4YsVP/sESsHy7GOZGeeF6LR4PJnhxSMKBZKqa+pD8EkgB9qtpoww1vl4Xw4IR/keaR4B1ilpj7K0+NkL0ampZCcFN7R5djoICHPszLXJ4f+td60BLIpZx7bPgWOPubs1AXMqeMFxRtwoqZwQzXVS6xoBxvcPr1ONpU95LiI8T1+9ddurvij1ZwYuT8x30g1UAyu5ZyP1/Fw/k1ffzJ5lMst91ZXBJ4Gu4Xa98sRu/HglJKS7ZlWbs9zbACtu1K3WdXf5pJ+9gvUBCjuVbtTXen+/0zhh9RaA0FvxoQMHcxpyYkyJRsXPJy5c0jcrS0oskcwRayve2lzXwSeBHEhnbij/kl18j2RPKWAnZW2Y2N6GjfFteYw9exLSajddzxEQYrH7lrF1r7rwdgEV3jU66VJCUmxUe/k28DQL/lB+OS1bpjnaTY/jOP1U5qYCHvDlwF5w2Acw2DaFEMHA8zaEBSHbGAYH10OJArOpaU2MZNmBoaEamGYL9gNmmFILyuUa+z3/BQJZ72eLPkujAAAAAElFTkSuQmCC"
                    alt="删除"
                    class="ingredient-remove-btn__image"
                  />
                </button>
              </div>
            </div>
          </div>

          <div v-else class="empty-ingredients">
            <span>暂无食材，请点击右上角“添加食材”</span>
          </div>
        </div>
      </section>

      <section class="form-section form-section--params">
        <div class="section-heading section-heading--between">
          <div class="section-heading__left">
            <span class="section-heading__bar"></span>
            <span class="section-heading__text">烹饪参数</span>
          </div>
          <button type="button" class="outline-action-btn outline-action-btn--small outline-action-btn--ai" @click="handleAISuggestion" :disabled="aiLoading">
            <img
              src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABIAAAASCAYAAABWzo5XAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAOdEVYdFNvZnR3YXJlAEZpZ21hnrGWYwAAAddJREFUeAGdVFFOwkAQnVmK+me9gd4AT6CcQIyQ+AdH4NcoigHjL9wA/0xALScATyBHqCew/9COb4AlWyyGMAkpM519896+3TJtiMqT1ESoykQFpL7WhGiCfIL6y6DBY7ef1wGun+U4jumDFEDoU5gCZor0XZJQwWid6QzvwmlMxaDJ4R8gBUHzSBIkhi77tzzJYuv2WbAUULktPRY6cydtilJT/LxHIzDz0X9q3ClArcZCj/+BlFtyrk/0RLkcXUKT73lUWwHNYmqq7vd77rmLwPLL5lctKWG/RjZ/veEQBgxB4GIFZLCBkDxMjWeqqWuWhTEL99ZC97FgrCxYqtICdw9UKv5GcK5KG2LpqG90gTqgsiAvtA35PJX0qdQxraR9WUBw7lhZmT2PHlwbVw2yYIOJhzrRy80PZgYlnCkFwsQ6krELsnTwHHS+KaEjgIVZ8mxfIspaqItardKWH+vQdDafHvUbXMBPG7sqD8x1WGSBrNNvDQ4MGuuQUVzaOKePKzIGy6JdMLjjjuZ6vzDkRGsYWrfnLkVTLylYCW0RuvGqoNKSjq0Z2iEO9ufnyRfnuOwElBXeeiElb/GpONV7RdsCTacU4BCmAm5FWSB6x3Btiu7H7RcY2u0MtcErsQAAAABJRU5ErkJggg=="
              alt="AI建议"
              class="outline-action-btn__image outline-action-btn__image--ai"
            />
            {{ aiLoading ? '分析中...' : 'AI建议' }}
          </button>
        </div>

        <div class="params-layout">
          <div class="params-label-row">
            <div class="params-label">烹饪时长（分钟）</div>
            <div class="params-label">最低温度（℃）</div>
            <div class="params-label">最高温度（℃）</div>
          </div>

          <div class="form-grid form-grid--params">
            <el-form-item prop="cookingTime" label-width="0" class="form-item--fixed form-item--param">
              <div class="param-number-control">
                <button
                  type="button"
                  class="param-number-control__step param-number-control__step--minus"
                  aria-label="减少烹饪时长"
                  @click="handleParamStep('cookingTime', -1)"
                >
                  <span class="param-number-control__minus"></span>
                </button>
                <input
                  :value="String(formData.cookingTime ?? '')"
                  type="text"
                  inputmode="numeric"
                  maxlength="3"
                  class="param-number-control__input"
                  @input="handleParamInput($event, 'cookingTime', 1, 999, 3)"
                />
                <button
                  type="button"
                  class="param-number-control__step param-number-control__step--plus"
                  aria-label="增加烹饪时长"
                  @click="handleParamStep('cookingTime', 1)"
                >
                  <span class="param-number-control__plus"></span>
                </button>
              </div>
            </el-form-item>

            <el-form-item prop="cookingTempMin" label-width="0" class="form-item--fixed form-item--param">
              <div class="param-number-control">
                <button
                  type="button"
                  class="param-number-control__step param-number-control__step--minus"
                  aria-label="降低最低温度"
                  @click="handleParamStep('cookingTempMin', -1)"
                >
                  <span class="param-number-control__minus"></span>
                </button>
                <input
                  :value="String(formData.cookingTempMin ?? '')"
                  type="text"
                  inputmode="numeric"
                  maxlength="3"
                  class="param-number-control__input"
                  @input="handleParamInput($event, 'cookingTempMin', 0, formData.cookingTempMax ?? 300, 3)"
                />
                <button
                  type="button"
                  class="param-number-control__step param-number-control__step--plus"
                  aria-label="提高最低温度"
                  @click="handleParamStep('cookingTempMin', 1)"
                >
                  <span class="param-number-control__plus"></span>
                </button>
              </div>
            </el-form-item>

            <el-form-item prop="cookingTempMax" label-width="0" class="form-item--fixed form-item--param">
              <div class="param-number-control">
                <button
                  type="button"
                  class="param-number-control__step param-number-control__step--minus"
                  aria-label="降低最高温度"
                  @click="handleParamStep('cookingTempMax', -1)"
                >
                  <span class="param-number-control__minus"></span>
                </button>
                <input
                  :value="String(formData.cookingTempMax ?? '')"
                  type="text"
                  inputmode="numeric"
                  maxlength="3"
                  class="param-number-control__input"
                  @input="handleParamInput($event, 'cookingTempMax', formData.cookingTempMin ?? 0, 300, 3)"
                />
                <button
                  type="button"
                  class="param-number-control__step param-number-control__step--plus"
                  aria-label="提高最高温度"
                  @click="handleParamStep('cookingTempMax', 1)"
                >
                  <span class="param-number-control__plus"></span>
                </button>
              </div>
            </el-form-item>
          </div>
        </div>
      </section>

      <section class="form-section form-section--steps">
        <div class="section-heading">
          <span class="section-heading__bar"></span>
          <span class="section-heading__text">制作步骤</span>
        </div>

        <el-input
          v-model="formData.cookingSteps"
          type="textarea"
          :rows="2"
          placeholder="请输入制作步骤，每行一个步骤"
          maxlength="1000"
          :show-word-limit="true"
          class="steps-textarea"
        />
      </section>
      </div>
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <button type="button" class="btn-cancel" @click="handleClose">取消</button>
        <button type="button" class="btn-submit" @click="handleSubmit" :disabled="submitLoading">
          {{ submitLoading ? '保存中...' : '保存' }}
        </button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, nextTick } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { Recipe, RecipeForm as RecipeFormType, RecipeIngredient, RecipeCategoryItem } from '@/types/recipe'
import { useMaterialStore } from '@/stores/modules/material'
import { getAICookingSuggestion, getActiveCategories, getRecipeList } from '@/api/modules/recipe'
import { ElMessage } from 'element-plus'

const props = defineProps<{
  modelValue: boolean
  recipeId: number | null
  recipeData: Recipe | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: [data: RecipeFormType]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const isEdit = computed(() => !!props.recipeId)

const materialStore = useMaterialStore()

const formRef = ref<FormInstance>()
const formBodyRef = ref<HTMLElement | null>(null)
const submitLoading = ref(false)
const aiLoading = ref(false)
const menuCodeChecking = ref(false)
const menuCodeError = ref('')

const fieldTip = ref('')
let fieldTipTimer: ReturnType<typeof setTimeout> | null = null

const showFieldTip = (field: string) => {
  if (fieldTipTimer) clearTimeout(fieldTipTimer)
  fieldTip.value = ''
  requestAnimationFrame(() => { fieldTip.value = field })
  fieldTipTimer = setTimeout(() => { fieldTip.value = '' }, 2000)
}

const handleFieldInput = (field: string, value: string, max: number) => {
  if (value.length >= max) showFieldTip(field)
  else if (fieldTip.value === field) fieldTip.value = ''
}

const handleFieldKeydown = (field: string, value: string, max: number, e: KeyboardEvent) => {
  if (value.length >= max && e.key.length === 1 && !e.ctrlKey && !e.metaKey) {
    showFieldTip(field)
  }
}

const handleNumberKeydown = (field: string, value: string, max: number, e: KeyboardEvent) => {
  const current = value.replace(/[^0-9]/g, '')
  if (current.length >= max && e.key >= '0' && e.key <= '9' && !e.ctrlKey && !e.metaKey) {
    e.preventDefault()
    showFieldTip(field)
  }
}

const clampServingSize = (value: number) => {
  if (!Number.isFinite(value)) return 1
  return Math.min(99999, Math.max(1, value))
}

const handleServingSizeInput = (event: Event) => {
  const target = event.target as HTMLInputElement
  const digits = target.value.replace(/\D/g, '').slice(0, 5)

  if (digits.length >= 5) {
    showFieldTip('servingSize')
  } else if (fieldTip.value === 'servingSize') {
    fieldTip.value = ''
  }

  formData.value.servingSize = digits ? clampServingSize(Number(digits)) : 1
  target.value = String(formData.value.servingSize)
}

const handleServingSizeStep = (delta: number) => {
  formData.value.servingSize = clampServingSize((formData.value.servingSize || 1) + delta)
}

const clampIngredientQuantity = (value: number) => {
  if (!Number.isFinite(value)) return 1
  return Math.min(9999, Math.max(1, Math.trunc(value)))
}

const handleIngredientQuantityInput = (event: Event, row: RecipeIngredient) => {
  const target = event.target as HTMLInputElement
  const digits = target.value.replace(/\D/g, '').slice(0, 4)
  row.quantity = digits ? clampIngredientQuantity(Number(digits)) : 1
  target.value = String(row.quantity)
}

const handleIngredientQuantityStep = (row: RecipeIngredient, delta: number) => {
  row.quantity = clampIngredientQuantity((row.quantity || 1) + delta)
}

const clampParamValue = (value: number, min: number, max: number) => {
  if (!Number.isFinite(value)) return min
  return Math.min(max, Math.max(min, Math.trunc(value)))
}

const handleParamInput = (
  event: Event,
  field: 'cookingTime' | 'cookingTempMin' | 'cookingTempMax',
  min: number,
  max: number,
  maxLength: number
) => {
  const target = event.target as HTMLInputElement
  const digits = target.value.replace(/\D/g, '').slice(0, maxLength)

  if (digits.length >= maxLength) {
    showFieldTip(field)
  } else if (fieldTip.value === field) {
    fieldTip.value = ''
  }

  formData.value[field] = digits ? clampParamValue(Number(digits), min, max) : min
  target.value = String(formData.value[field])
  void formRef.value?.validateField(field)
}

const handleParamStep = (
  field: 'cookingTime' | 'cookingTempMin' | 'cookingTempMax',
  delta: number
) => {
  const limits = {
    cookingTime: { min: 1, max: 999 },
    cookingTempMin: { min: 0, max: formData.value.cookingTempMax ?? 300 },
    cookingTempMax: { min: formData.value.cookingTempMin ?? 0, max: 300 }
  }

  const { min, max } = limits[field]
  formData.value[field] = clampParamValue((formData.value[field] || min) + delta, min, max)
  void formRef.value?.validateField(field)
}

const unitOptions = computed(() => {
  const units = materialStore.activeList
    .map(m => m.unit)
    .filter((u): u is string => !!u)
  return [...new Set(units)].sort()
})

const categoryList = ref<RecipeCategoryItem[]>([])
const categoryLoading = ref(false)

const categoryOptions = computed(() => {
  return categoryList.value.map(cat => ({
    label: cat.categoryName,
    value: cat.categoryCode
  }))
})

const fetchCategoryList = async () => {
  categoryLoading.value = true
  try {
    const res = await getActiveCategories()
    if (res.code === 'SUCCESS' && res.data) {
      categoryList.value = res.data
    }
  } catch (error) {
    console.error('获取菜谱类别失败', error)
  } finally {
    categoryLoading.value = false
  }
}

const getMaterialById = (id: number) => {
  return materialStore.activeList.find(m => m.id === id)
}

const getMaterialNutritionState = (material: Material) => {
  if (material.nutritionSourceType) {
    return { tone: 'is-ready', label: '已同步' }
  }
  if (material.foodItemId) {
    return { tone: 'is-pending', label: '待同步' }
  }
  return { tone: 'is-missing', label: '待映射' }
}

const normalizeIngredientUnit = (unit) => {
  if (!unit) return 'g'
  const normalized = unit.trim().toLowerCase()
  if (normalized === 'kg' || normalized === 'g' || normalized === '克') return 'g'
  return unit.trim()
}

/** 判断是否为主料（肉类、海鲜、蔬菜都可能是主料） */
const isMainIngredient = (materialName: string): boolean => {
  if (!materialName) return false
  const meatKeywords = ['猪', '牛', '羊', '鸡', '鸭', '鹅', '肉', '排骨', '五花肉',
                        '鱼', '虾', '蟹', '贝', '海鲜']
  if (meatKeywords.some(keyword => materialName.includes(keyword))) return true

  const vegetableKeywords = ['青菜', '白菜', '菠菜', '生菜', '韭菜', '豆角', '花菜', '西兰花', '包菜']
  if (vegetableKeywords.some(keyword => materialName.includes(keyword))) return true

  return false
}

const isVegetable = (materialName: string): boolean => {
  if (!materialName) return false
  const vegetableKeywords = ['菜', '蔬', '瓜', '茄', '椒', '豆', '菇', '笋', '萝卜', '菠菜', '白菜', '青菜', '生菜', '芹菜', '韭菜', '卷心菜', '西兰花', '番茄', '黄瓜', '冬瓜', '南瓜', '丝瓜', '苦瓜', '土豆', '山药', '莲藕', '芋头', '洋葱', '大蒜', '生姜', '葱']
  return vegetableKeywords.some(keyword => materialName.includes(keyword))
}

const handleMaterialChange = (materialId: number, row: RecipeIngredient) => {
  const material = getMaterialById(materialId)
  if (material) {
    row.materialName = material.materialName
    row.materialSpec = material.materialSpec || ''
    row.unit = material.unit || ''
    if (row.isMain == null) {
      row.isMain = isMainIngredient(material.materialName)
    }
    if (isMainIngredient(material.materialName)) {
      row.quantity = 300
    } else if (isVegetable(material.materialName)) {
      row.quantity = 200
    } else {
      row.quantity = 50
    }
  }
}

const formData = ref<RecipeFormType>({
  menuCode: '',
  menuName: '',
  menuCategory: null,
  description: '',
  imageUrl: '',
  servingSize: 200,
  cookingTime: 30,
  cookingTempMin: 80,
  cookingTempMax: 100,
  cookingSteps: '',
  ingredients: [],
  status: 'active'
})

let menuCodeTimer: ReturnType<typeof setTimeout> | null = null
const checkMenuCodeDuplicate = async (code: string) => {
  if (!code) {
    menuCodeError.value = ''
    return
  }
  if (isEdit.value) {
    menuCodeError.value = ''
    return
  }
  menuCodeChecking.value = true
  try {
    const res = await getRecipeList({ recipeCode: code, pageNum: 1, pageSize: 1 })
    if (res.code === 'SUCCESS' && res.data?.list?.length) {
      menuCodeError.value = `编码「${code}」已存在，请更换编码`
    } else {
      menuCodeError.value = ''
    }
  } catch {
    menuCodeError.value = ''
  } finally {
    menuCodeChecking.value = false
  }
}

watch(
  () => formData.value.menuCode,
  (code) => {
    if (menuCodeTimer) clearTimeout(menuCodeTimer)
    menuCodeTimer = setTimeout(() => checkMenuCodeDuplicate(code), 500)
  }
)

const formRules: FormRules = {
  menuName: [
    { required: true, message: '请输入菜谱名称', trigger: 'blur' },
    { min: 1, max: 30, message: '长度在 1 到 30 个字符', trigger: 'blur' }
  ],
  menuCategory: [
    { required: true, message: '请选择菜谱类别', trigger: 'change' }
  ],
  cookingTime: [
    { required: true, message: '请输入烹饪时长', trigger: 'blur' }
  ],
  cookingTempMin: [
    { required: true, message: '请输入最低温度', trigger: ['blur', 'change'] },
    {
      validator: (_rule, value, callback) => {
        if (value != null && formData.value.cookingTempMax != null && value > formData.value.cookingTempMax) {
          callback(new Error('最低温度不能高于最高温度'))
        } else {
          callback()
          formRef.value?.validateField('cookingTempMax')
        }
      },
      trigger: ['blur', 'change']
    }
  ],
  cookingTempMax: [
    { required: true, message: '请输入最高温度', trigger: ['blur', 'change'] },
    {
      validator: (_rule, value, callback) => {
        if (value != null && formData.value.cookingTempMin != null && value < formData.value.cookingTempMin) {
          callback(new Error('最高温度不能低于最低温度'))
        } else {
          callback()
          formRef.value?.validateField('cookingTempMin')
        }
      },
      trigger: ['blur', 'change']
    }
  ]
}

const generateMenuCode = () => {
  const timestamp = Date.now().toString().slice(-6)
  return `CP${timestamp}`
}

const fetchMaterialList = async () => {
  await materialStore.fetchActiveList()
}

const refreshDialogDependencies = async () => {
  await Promise.all([
    fetchMaterialList(),
    fetchCategoryList()
  ])
}

onMounted(() => {
  void refreshDialogDependencies()
})

const getCategoryCodeById = (id: number | undefined): string | null => {
  const category = categoryList.value.find(cat => cat.id === id)
  return category?.categoryCode || null
}

const populateFormData = (data: Recipe | null) => {
  if (data) {
    const categoryCode = getCategoryCodeById(data.categoryId)
    formData.value = {
      menuCode: data.menuCode,
      menuName: data.menuName,
      menuCategory: categoryCode,
      description: data.description || '',
      imageUrl: data.imageUrl || '',
      servingSize: data.servingSize || 200,
      cookingTime: data.cookingTime,
      cookingTempMin: data.cookingTempMin || 80,
      cookingTempMax: data.cookingTempMax || 100,
      cookingSteps: data.cookingSteps || '',
      ingredients: [...(data.ingredients || [])],
      status: data.status
    }
  } else {
    formData.value = {
      menuCode: generateMenuCode(),
      menuName: '',
      menuCategory: null,
      description: '',
      imageUrl: '',
      servingSize: 200,
      cookingTime: 30,
      cookingTempMin: 80,
      cookingTempMax: 100,
      cookingSteps: '',
      ingredients: [{ materialId: null, materialName: '', materialSpec: '', quantity: 100, unit: '', isMain: null }],
      status: 'active'
    }
  }
}

watch(
  () => props.recipeData,
  (data) => {
    if (categoryList.value.length > 0) {
      populateFormData(data)
    }
  },
  { immediate: true }
)

watch(visible, async (val) => {
  if (val) {
    menuCodeError.value = ''
    if (categoryList.value.length > 0) {
      populateFormData(props.recipeData)
    }
    await refreshDialogDependencies()
    populateFormData(props.recipeData)
    nextTick(() => {
      formBodyRef.value?.scrollTo({ top: 0, behavior: 'auto' })
    })
  }
})

watch(
  () => categoryList.value,
  (list) => {
    if (list.length > 0 && visible.value) {
      populateFormData(props.recipeData)
    }
  }
)

const handleAddIngredient = () => {
  formData.value.ingredients.push({
    materialId: null,
    materialName: '',
    materialSpec: '',
    quantity: 100,
    unit: '',
    isMain: null
  })
}

const handleRemoveIngredient = (index: number) => {
  formData.value.ingredients.splice(index, 1)
}

const handleAISuggestion = async () => {
  if (!formData.value.menuName) {
    ElMessage.warning('请先输入菜谱名称')
    return
  }

  const validIngredients = formData.value.ingredients.filter(i => i.materialName)
  if (validIngredients.length === 0) {
    ElMessage.warning('请至少添加一种食材')
    return
  }

  aiLoading.value = true
  try {
    const res = await getAICookingSuggestion({
      menuName: formData.value.menuName,
      cookingSteps: formData.value.cookingSteps || '',
      ingredients: validIngredients.map(i => ({ materialName: i.materialName }))
    })

    if (res.code === 'SUCCESS' && res.data) {
      formData.value.cookingTime = res.data.suggestedTime
      formData.value.cookingTempMin = res.data.suggestedTempMin
      formData.value.cookingTempMax = res.data.suggestedTempMax

      ElMessage({
        type: 'success',
        message: '已为您推荐符合食品安全标准的烹饪参数',
        description: res.data.reason,
        duration: 4000
      })
    } else {
      ElMessage.error('获取AI建议失败，请稍后重试')
    }
  } catch (error) {
    console.error('AI建议请求失败', error)
    ElMessage.error('获取AI建议失败，请稍后重试')
  } finally {
    aiLoading.value = false
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    if (menuCodeError.value) {
      ElMessage.warning(menuCodeError.value)
      return
    }

    if (formData.value.ingredients.length === 0) {
      ElMessage.warning('请至少添加一条食材')
      return
    }

    const hasEmptyIngredient = formData.value.ingredients.some(item => !item.materialId)
    if (hasEmptyIngredient) {
      ElMessage.warning('请选择所有食材的物料')
      return
    }

    submitLoading.value = true
    try {
      emit('success', formData.value)
      handleClose()
    } finally {
      submitLoading.value = false
    }
  })
}

const handleClose = () => {
  visible.value = false
}
</script>

<style lang="scss" scoped>
:deep(.recipe-form-overlay) {
  background: #33333380;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}

:deep(.recipe-form-dialog .el-dialog) {
  --el-dialog-border-radius: 12px;
  width: 758px;
  max-height: 90vh;
  margin: 0;
  padding: 0;
  background: #ffffff;
  border-radius: 12px !important;
  background-clip: padding-box;
  overflow: hidden;
  clip-path: inset(0 round 12px);
  box-shadow: 0 12px 32px rgba(28, 29, 34, 0.12);
  display: flex;
  flex-direction: column;
}

:deep(.recipe-form-dialog .el-dialog__header) {
  display: block;
  width: 100%;
  padding: 0;
  margin: 0;
  box-sizing: border-box;
  border-bottom: none;
}

:deep(.recipe-form-dialog .el-dialog__body) {
  padding: 0;
  flex: 1;
  min-height: 0;
}

:deep(.recipe-form-dialog .el-dialog__footer) {
  flex-shrink: 0;
  display: flex !important;
  align-items: flex-start !important;
  justify-content: flex-end !important;
  height: 60px !important;
  padding: 12px 24px 16px !important;
  border-top: 1px solid #e1e2e9;
  border-radius: 0 0 12px 12px;
  box-sizing: border-box;
}

.dialog-header {
  position: relative;
  width: 100%;
  min-height: 48px;
  padding: 0 24px 16px;
  display: flex;
  align-items: flex-start;
  background: #ffffff;
  border-radius: 12px 12px 0 0;
  box-sizing: border-box;

  &::after {
    content: '';
    position: absolute;
    left: 0;
    right: 0;
    bottom: 0;
    border-bottom: 1px solid #e1e2e9;
  }
}

.dialog-header__content {
  width: 100%;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.dialog-header__title {
  width: 80px;
  height: 30px;
  font-family: 'Poppins', 'PingFang SC', sans-serif;
  font-size: 20px;
  line-height: 30px;
  font-weight: 500;
  text-align: center;
  color: #000000;
}

.dialog-header__close {
  width: 32px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 4px;
  box-sizing: border-box;
  border: none;
  border-radius: 8px;
  background: #fff2e2;
  color: #1c1d22;
  cursor: pointer;

  svg {
    width: 24px;
    height: 24px;
  }
}

.recipe-form {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #ffffff;
}

.recipe-form__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 12px 24px 18px;
}

.form-section {
  margin-bottom: 18px;
}

.form-section--base {
  padding-top: 4px;
}

.form-section--ingredients {
  margin-bottom: 20px;
}

.form-section--params {
  margin-bottom: 16px;
}

.form-section--steps {
  position: relative;
  margin-bottom: 0;
  padding-bottom: 24px;

  &::after {
    content: '';
    position: absolute;
    left: -24px;
    right: -24px;
    bottom: 0;
    border-bottom: 1px solid #e1e2e9;
  }
}

.section-heading {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 10px;
}

.section-heading--between {
  justify-content: space-between;
}

.section-heading__left {
  display: flex;
  align-items: center;
  gap: 4px;
}

.section-heading__bar {
  width: 4px;
  height: 16px;
  border-radius: 2px;
  background: #5570f1;
}

.section-heading__text {
  font-size: 16px;
  line-height: 16px;
  font-weight: 500;
  color: #000000;
}

.form-grid {
  display: grid;
  gap: 14px 24px;
}

.form-grid--base {
  grid-template-columns: 316px 316px;
  row-gap: 12px;
  column-gap: 24px;
}

.form-grid--params {
  width: 688px;
  grid-template-columns: repeat(3, 220px);
  gap: 14px;
  justify-content: start;
}

.params-layout {
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.params-label-row {
  width: 688px;
  display: grid;
  grid-template-columns: repeat(3, 220px);
  gap: 14px;
  justify-content: start;
}

.params-label {
  width: 220px;
  height: 22px;
  color: rgba(0, 0, 0, 0.85);
  font-family: 'Roboto', 'PingFang SC', sans-serif;
  font-size: 14px;
  line-height: 22px;
  font-weight: 400;
  text-align: left;
}

:deep(.recipe-form .form-grid--params .el-form-item) {
  margin-bottom: 0;
}

:deep(.recipe-form .form-grid--params .el-form-item__content) {
  width: 220px;
  margin-left: 0 !important;
  justify-content: flex-start;
}

.form-item--fixed {
  width: 316px;
}

:deep(.recipe-form .form-grid--base .form-item--fixed.el-form-item) {
  display: flex !important;
  align-items: center !important;
  min-height: 32px !important;
  height: 32px !important;
  margin-bottom: 0 !important;
}

:deep(.recipe-form .form-grid--base .form-item--fixed.el-form-item .el-form-item__label) {
  height: 32px !important;
  line-height: 32px !important;
}

:deep(.recipe-form .form-grid--base .form-item--fixed.el-form-item .el-form-item__content) {
  min-height: 32px !important;
  height: 32px !important;
  align-items: center !important;
}

.form-item--full-width {
  width: 100%;
}

.form-item--description {
  margin-top: 2px;
}

.input-wrapper {
  position: relative;
  width: 100%;
  min-height: 32px;
}

.input-wrapper--full {
  width: 100%;
}

.field-length-tip {
  position: absolute;
  left: 0;
  bottom: calc(100% + 6px);
  z-index: 20;
  padding: 4px 10px;
  border-radius: 4px;
  background: #e6a23c;
  color: #ffffff;
  font-size: 12px;
  line-height: 18px;
  white-space: nowrap;

  &::after {
    content: '';
    position: absolute;
    left: 16px;
    top: 100%;
    border: 5px solid transparent;
    border-top-color: #e6a23c;
  }
}

.input-tip-enter-active,
.input-tip-leave-active {
  transition: opacity 0.2s;
}

.input-tip-enter-from,
.input-tip-leave-to {
  opacity: 0;
}

.field-feedback {
  margin-top: 4px;
  font-size: 12px;
  line-height: 20px;
  color: rgba(0, 0, 0, 0.45);
}

.field-feedback--error {
  color: #d54941;
}

.serving-size-control {
  width: 220px;
  height: 32px;
  display: flex;
  align-items: center;
}

.serving-size-control__step {
  width: 31px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 4px 10px;
  border: 1px solid #d9d9d9;
  background: #f5f7fa;
  cursor: pointer;
  flex-shrink: 0;
}

.serving-size-control__step:first-child {
  border-radius: 4px 0 0 4px;
}

.serving-size-control__step:last-child {
  border-radius: 0 4px 4px 0;
}

.serving-size-control__input {
  width: 158px;
  height: 32px;
  padding: 4px 12px;
  border-top: 1px solid #d9d9d9;
  border-right: none;
  border-bottom: 1px solid #d9d9d9;
  border-left: none;
  background: #ffffff;
  color: rgba(0, 0, 0, 0.85);
  font-family: 'Roboto', 'PingFang SC', sans-serif;
  font-size: 14px;
  line-height: 22px;
  text-align: left;
  outline: none;
  box-sizing: border-box;
}

.serving-size-control__input::placeholder {
  color: rgba(0, 0, 0, 0.35);
}

.serving-size-control__input:focus {
  position: relative;
  z-index: 1;
}

.serving-size-control__minus,
.serving-size-control__plus {
  position: relative;
  display: block;
  width: 10px;
  height: 10px;
}

.serving-size-control__minus::before,
.serving-size-control__plus::before,
.serving-size-control__plus::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  background: #7c7e81;
  border-radius: 999px;
  transform: translate(-50%, -50%);
}

.serving-size-control__minus::before,
.serving-size-control__plus::before {
  width: 10px;
  height: 2px;
}

.serving-size-control__plus::after {
  width: 2px;
  height: 10px;
}

.form-item--param {
  width: 220px;
}

.param-number-control {
  width: 220px;
  height: 32px;
  display: flex;
  align-items: center;
}

.param-number-control__step {
  width: 31px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 4px 10px;
  border: 1px solid #d9d9d9;
  background: #f5f7fa;
  cursor: pointer;
  flex-shrink: 0;
  box-sizing: border-box;
}

.param-number-control__step--minus {
  border-radius: 4px 0 0 4px;
}

.param-number-control__step--plus {
  border-radius: 0 4px 4px 0;
}

.param-number-control__input {
  width: 158px;
  height: 32px;
  padding: 4px 12px;
  border-top: 1px solid #d9d9d9;
  border-right: none;
  border-bottom: 1px solid #d9d9d9;
  border-left: none;
  background: #ffffff;
  color: #00000059;
  font-family: 'Roboto', 'PingFang SC', sans-serif;
  font-size: 14px;
  line-height: 22px;
  text-align: center;
  outline: none;
  box-sizing: border-box;
}

.param-number-control__input::placeholder {
  color: rgba(0, 0, 0, 0.35);
  text-align: center;
}

.param-number-control__input:focus {
  position: relative;
  z-index: 1;
}

.param-number-control__minus,
.param-number-control__plus {
  position: relative;
  display: block;
  width: 10px;
  height: 10px;
}

.param-number-control__minus::before,
.param-number-control__plus::before,
.param-number-control__plus::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  background: #7c7e81;
  border-radius: 999px;
  transform: translate(-50%, -50%);
}

.param-number-control__minus::before,
.param-number-control__plus::before {
  width: 10px;
  height: 2px;
}

.param-number-control__plus::after {
  width: 2px;
  height: 10px;
}

.ingredient-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ingredient-count-badge {
  height: 32px;
  min-width: 53px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 5px 16px;
  gap: 8px;
  border-radius: 26px;
  background: #fff1f0;
}

.ingredient-count-badge__text {
  height: 22px;
  color: #d54941;
  font-family: 'PingFang SC', sans-serif;
  font-size: 13px;
  line-height: 22px;
  font-weight: 400;
  text-align: center;
}

.ingredient-alert {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border-radius: 6px;
  background: rgba(255, 216, 210, 0.2);
  color: rgba(0, 0, 0, 0.75);
  font-size: 12px;
  line-height: 20px;
}

.ingredient-alert__icon {
  width: 20px;
  height: 20px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: #d54941;

  svg {
    width: 20px;
    height: 20px;
    display: block;
  }
}

.outline-action-btn {
  height: 32px;
  padding: 5px 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 1px solid #5570f1;
  border-radius: 6px;
  background: #ffffff;
  color: #7288fa;
  font-size: 14px;
  line-height: 22px;
  cursor: pointer;
  white-space: nowrap;
  flex-shrink: 0;

  &:disabled {
    opacity: 0.55;
    cursor: not-allowed;
  }
}

.outline-action-btn__image {
  display: block;
  flex: none;
}

.outline-action-btn__image--ai {
  width: 18px;
  height: 18px;
}

.outline-action-btn--small {
  padding: 5px 12px;
}

.outline-action-btn--ai {
  box-sizing: border-box;
  width: 99px;
  min-width: 99px;
  height: 32px;
  padding: 5px 16px;
  gap: 8px;
  border: 1px solid #5570f1;
  border-radius: 6px;
  color: #7288fa;
  font-family: 'Roboto', 'PingFang SC', sans-serif;
  font-size: 14px;
  line-height: 22px;
  font-weight: 400;
  text-align: center;
}

.ingredients-form-panel {
  width: 100%;
}

.ingredients-form-head {
  display: grid;
  grid-template-columns: 200px 110px 110px 80px 80px 32px;
  column-gap: 16px;
  align-items: center;
  margin-top: 12px;
  margin-bottom: 12px;
}

.ingredients-form-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.ingredient-form-row {
  display: grid;
  grid-template-columns: 200px 110px 110px 80px 80px 32px;
  column-gap: 16px;
  align-items: center;
}

.ingredients-col {
  min-width: 0;
}

.ingredients-col--action {
  display: flex;
  justify-content: flex-end;
}

.ingredients-form-head .ingredients-col {
  color: rgba(0, 0, 0, 0.85);
  font-family: 'Roboto', 'PingFang SC', sans-serif;
  font-size: 14px;
  line-height: 22px;
  font-weight: 400;
  text-align: left;
}

.ingredient-field {
  width: 100%;
}

.ingredient-static-field {
  width: 110px;
  height: 32px;
  display: flex;
  align-items: center;
  padding: 5px 8px;
  border: 1px solid #dcdcdc;
  border-radius: 3px;
  background: #ffffff;
  color: rgba(0, 0, 0, 0.35);
  font-size: 14px;
  line-height: 22px;
  box-sizing: border-box;
}

.ingredient-static-field.has-value {
  color: rgba(0, 0, 0, 0.9);
}

.ingredient-quantity-control {
  position: relative;
  width: 110px;
  height: 32px;
  display: flex;
  align-items: center;
  padding: 1px 0 1px 8px;
  gap: 8px;
  border: 1px solid #0052d9;
  border-radius: 3px;
  background: #ffffff;
  box-sizing: border-box;
  overflow: hidden;

  &::after {
    content: '';
    position: absolute;
    top: 0;
    right: 0;
    width: 1px;
    height: 100%;
    background: #0052d9;
    pointer-events: none;
  }
}

.ingredient-quantity-control__input {
  width: 66px;
  height: 22px;
  padding: 0;
  border: none;
  background: transparent;
  color: rgba(0, 0, 0, 0.9);
  font-family: 'PingFang SC', sans-serif;
  font-size: 14px;
  line-height: 22px;
  outline: none;
}

.ingredient-quantity-control__controls {
  width: 28px;
  height: 30px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex-shrink: 0;
}

.ingredient-quantity-control__step {
  width: 28px;
  height: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 4px 9px;
  border: none;
  background: #f3f3f3;
  color: rgba(0, 0, 0, 0.6);
  cursor: pointer;
}

.ingredient-quantity-control__step svg {
  width: 16px;
  height: 16px;
}
:deep(.ingredient-field .el-select__wrapper) {
  min-height: 32px;
  padding: 4px 12px;
  border-radius: 4px;
  border: 1px solid #d9d9d9;
  box-shadow: none;
  background: #ffffff;
}

:deep(.ingredient-field--name .el-select__wrapper) {
  width: 200px;
}

:deep(.ingredient-field .el-input__inner),
:deep(.ingredient-field .el-select__selected-item) {
  font-size: 14px;
  line-height: 22px;
  color: rgba(0, 0, 0, 0.9);
}

:deep(.ingredient-field .el-input__inner::placeholder),
:deep(.ingredient-field .el-select__placeholder) {
  color: rgba(0, 0, 0, 0.35);
}

:deep(.ingredient-field--unit .el-select__wrapper),
:deep(.ingredient-field--type .el-select__wrapper) {
  min-height: 32px;
  padding: 4px 12px;
}

:deep(.ingredient-field--unit .el-select__caret),
:deep(.ingredient-field--type .el-select__caret) {
  color: rgba(0, 0, 0, 0.35);
}

.ingredient-remove-btn {
  width: 32px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: none;
  border-radius: 4px;
  background: transparent;
  cursor: pointer;
}

.ingredient-remove-btn__image {
  width: 32px;
  height: 32px;
  display: block;
  border-radius: 4px;
}

.empty-ingredients {
  padding: 20px 16px;
  text-align: center;
  color: rgba(0, 0, 0, 0.45);
  font-size: 14px;
  line-height: 22px;
  border-top: 1px solid #e1e2e9;
}

.material-option {
  display: flex;
  align-items: center;
  gap: 8px;
}

.material-main,
.material-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.material-meta {
  flex-wrap: wrap;
}

.material-name {
  color: rgba(0, 0, 0, 0.85);
}

.material-status-tag {
  display: inline-flex;
  align-items: center;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 12px;
  line-height: 18px;
  white-space: nowrap;

  &.is-ready {
    background: rgba(82, 196, 26, 0.12);
    color: #389e0d;
  }

  &.is-pending {
    background: rgba(250, 173, 20, 0.14);
    color: #d48806;
  }

  &.is-missing {
    background: rgba(0, 0, 0, 0.06);
    color: rgba(0, 0, 0, 0.55);
  }
}

.material-code,
.material-spec-tag,
.material-food-name {
  color: rgba(0, 0, 0, 0.45);
  font-size: 12px;
}

.steps-textarea {
  width: 694px;
  max-width: 100%;
}

:deep(.recipe-form .custom-textarea--description .el-textarea__inner) {
  min-height: 54px !important;
  height: 54px;
  padding: 5px 12px;
  border-radius: 2px;
  background: #ffffff;
  border: 1px solid #d9d9d9;
  box-shadow: none;
  font-family: 'Roboto', 'PingFang SC', sans-serif;
  font-size: 14px;
  line-height: 22px;
  color: rgba(0, 0, 0, 0.85);
  resize: none;
}

:deep(.recipe-form .custom-textarea--description .el-textarea__inner::placeholder) {
  color: rgba(0, 0, 0, 0.35);
}

:deep(.recipe-form .custom-textarea--description .el-input__count) {
  display: none;
}

:deep(.recipe-form .steps-textarea .el-textarea__inner) {
  min-height: 54px !important;
  height: 54px;
  padding: 5px 12px;
  border-radius: 2px;
  background: #ffffff;
  border: 1px solid #d9d9d9;
  box-shadow: none;
  font-family: 'Roboto', 'PingFang SC', sans-serif;
  font-size: 14px;
  line-height: 22px;
  color: rgba(0, 0, 0, 0.85);
  resize: none;
}

:deep(.recipe-form .steps-textarea .el-textarea__inner::placeholder) {
  color: rgba(0, 0, 0, 0.35);
}

:deep(.recipe-form .steps-textarea .el-input__count) {
  display: none;
}

.dialog-footer {
  height: 32px;
  width: 127px;
  margin-left: auto;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 9px;
  background: #ffffff;
  box-sizing: border-box;
  flex-shrink: 0;
}

.btn-cancel,
.btn-submit {
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 5px 16px;
  border-radius: 6px;
  cursor: pointer;
  box-sizing: border-box;
  white-space: nowrap;
  flex-shrink: 0;
}

.btn-cancel {
  width: 58px;
  border: 1px solid #bec0ca;
  background: #ffffff;
  color: #53545c;
  font-family: 'PingFang SC', sans-serif;
  font-size: 13px;
  line-height: 22px;
  font-weight: 400;
  text-align: center;
  box-shadow: 0 2px 0 rgba(0, 0, 0, 0.016);
}

.btn-submit {
  width: 60px;
  border: none;
  background: #7288fa;
  color: #ffffff;
  font-family: 'Roboto', 'PingFang SC', sans-serif;
  font-size: 14px;
  line-height: 22px;
  font-weight: 400;
  text-align: center;
  box-shadow: 0 2px 0 rgba(0, 0, 0, 0.043);

  &:disabled {
    opacity: 0.55;
    cursor: not-allowed;
  }
}

:deep(.recipe-form .el-form-item) {
  margin-bottom: 0;
}

:deep(.recipe-form .el-form-item__label) {
  height: 32px;
  padding-right: 8px;
  color: rgba(0, 0, 0, 0.85);
  font-size: 14px;
  line-height: 22px;
}

:deep(.recipe-form .el-form-item__content) {
  display: flex;
  align-items: flex-start;
  min-height: 32px;
}

:deep(.recipe-form .form-item--serving-size .el-form-item__content) {
  position: relative;
}

:deep(.recipe-form .el-input__wrapper),
:deep(.recipe-form .el-textarea__inner),
:deep(.recipe-form .el-select__wrapper) {
  box-shadow: none;
  border-radius: 4px;
  border: 1px solid #d9d9d9;
}

:deep(.recipe-form .el-input__wrapper),
:deep(.recipe-form .el-select__wrapper) {
  min-height: 32px;
  padding: 4px 12px;
}

:deep(.recipe-form .el-form-item__content .el-input),
:deep(.recipe-form .el-form-item__content .el-select),
:deep(.recipe-form .el-form-item__content .el-input-number) {
  width: 220px;
}

:deep(.recipe-form .form-grid--base .el-form-item__content) {
  min-height: 32px;
  align-items: center;
}

:deep(.recipe-form .form-grid--base .el-input__wrapper),
:deep(.recipe-form .form-grid--base .el-select__wrapper) {
  min-height: 32px;
  height: 32px;
}

:deep(.recipe-form .form-grid--base .el-form-item__content .el-input),
:deep(.recipe-form .form-grid--base .el-form-item__content .el-select) {
  height: 32px;
}

:deep(.recipe-form .el-input.is-disabled .el-input__wrapper) {
  background: #f5f7fa;
  color: rgba(0, 0, 0, 0.45);
}

:deep(.recipe-form .el-input-number) {
  width: 100%;
}

:deep(.recipe-form .el-input-number .el-input__wrapper) {
  border-radius: 4px 0 0 4px;
}

:deep(.recipe-form .el-textarea__inner) {
  min-height: 88px;
  padding: 5px 12px;
}

:deep(.recipe-form .el-textarea .el-input__count) {
  background: transparent;
}

@media (max-width: 820px) {
  .form-grid--base,
  .form-grid--params {
    grid-template-columns: 1fr;
  }

  .form-item--fixed,
  .form-item--param,
  .serving-size-control,
  .param-number-control,
  .params-label-row,
  .params-label,
  :deep(.recipe-form .el-form-item__content .el-input),
  :deep(.recipe-form .el-form-item__content .el-select) {
    width: 100%;
  }

  .ingredient-toolbar {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
