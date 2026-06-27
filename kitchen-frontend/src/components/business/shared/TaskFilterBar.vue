<script setup lang="ts">
import { ref, watch } from 'vue'
import { useCookStore } from '@/stores/modules/cook'
import { getMealPeriods } from '@/utils/meal-period'

defineProps<{
  showChefFilter?: boolean
  showOrgFilter?: boolean
  showLocationFilter?: boolean
  showAlertLevelFilter?: boolean
}>()

const cookStore = useCookStore()
const mealPeriods = getMealPeriods()

const localDate = ref(cookStore.searchParams.planDate || '')
const localMeal = ref(cookStore.searchParams.mealType || '')
const localKeyword = ref(cookStore.searchParams.keyword || '')
const localChef = ref(cookStore.searchParams.chefName || '')
const localOrg = ref(cookStore.searchParams.orgId?.toString() || '')
const localLocation = ref(cookStore.searchParams.deviceLocation || '')
const localAlertLevel = ref(cookStore.searchParams.alertLevel || '')

const alertLevelOptions = [
  { value: '', label: '全部预警' },
  { value: 'any', label: '有预警' },
  { value: 'critical', label: '严重' },
  { value: 'warning', label: '警告' },
  { value: 'info', label: '提示' }
]

let debounceTimer: ReturnType<typeof setTimeout> | null = null

const triggerSearch = () => {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    cookStore.search({
      planDate: localDate.value,
      mealType: localMeal.value,
      keyword: localKeyword.value,
      chefName: cookStore.searchParams.chefName !== undefined ? localChef.value : undefined,
      orgId: localOrg.value ? Number(localOrg.value) : null,
      deviceLocation: localLocation.value,
      alertLevel: localAlertLevel.value
    } as any)
  }, 300)
}

const onDateChange = () => triggerSearch()
const onMealChange = () => triggerSearch()
const onKeywordInput = () => triggerSearch()
const onChefInput = () => triggerSearch()
const onOrgChange = () => triggerSearch()
const onLocationInput = () => triggerSearch()
const onAlertLevelChange = () => triggerSearch()

const navigateDate = (offset: number) => {
  let base: Date
  if (localDate.value) {
    base = new Date(localDate.value)
  } else {
    base = new Date()
  }
  base.setDate(base.getDate() + offset)
  const y = base.getFullYear()
  const m = String(base.getMonth() + 1).padStart(2, '0')
  const d = String(base.getDate()).padStart(2, '0')
  localDate.value = `${y}-${m}-${d}`
  onDateChange()
}

const goToToday = () => {
  const now = new Date()
  localDate.value = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
  onDateChange()
}

// Sync from store (e.g. on resetSearch)
watch(() => cookStore.searchParams, (params) => {
  if (params.planDate !== localDate.value) localDate.value = params.planDate || ''
  if (params.mealType !== localMeal.value) localMeal.value = params.mealType || ''
  if (params.keyword !== localKeyword.value) localKeyword.value = params.keyword || ''
  if (params.chefName !== localChef.value) localChef.value = params.chefName || ''
  const orgStr = params.orgId?.toString() || ''
  if (orgStr !== localOrg.value) localOrg.value = orgStr
  if ((params.deviceLocation || '') !== localLocation.value) localLocation.value = params.deviceLocation || ''
  if ((params.alertLevel || '') !== localAlertLevel.value) localAlertLevel.value = params.alertLevel || ''
}, { deep: true })
</script>

<template>
  <div class="task-filter-bar">
    <div class="tfb-date-group">
      <button class="tfb-nav-btn" @click="navigateDate(-1)" title="前一天">&#8249;</button>
      <input
        type="date"
        class="tfb-input tfb-date"
        :value="localDate"
        @change="localDate = ($event.target as HTMLInputElement).value; onDateChange()"
        placeholder="日期"
      />
      <button class="tfb-nav-btn" @click="navigateDate(1)" title="后一天">&#8250;</button>
      <button class="tfb-nav-btn tfb-nav-btn--today" @click="goToToday" title="回到今天">Today</button>
    </div>
    <select
      v-if="showOrgFilter && cookStore.orgList.length > 1"
      class="tfb-input tfb-select tfb-org"
      :value="localOrg"
      @change="localOrg = ($event.target as HTMLSelectElement).value; onOrgChange()"
    >
      <option value="">全部组织</option>
      <option v-for="org in cookStore.orgList" :key="org.id" :value="org.id">{{ org.name }}</option>
    </select>
    <select
      class="tfb-input tfb-select"
      :value="localMeal"
      @change="localMeal = ($event.target as HTMLSelectElement).value; onMealChange()"
    >
      <option value="">全部餐次</option>
      <option v-for="p in mealPeriods" :key="p.type" :value="p.type">{{ p.label }}</option>
    </select>
    <input
      type="text"
      class="tfb-input tfb-search"
      :value="localKeyword"
      @input="localKeyword = ($event.target as HTMLInputElement).value; onKeywordInput()"
      placeholder="搜索菜名..."
    />
    <input
      v-if="showChefFilter"
      type="text"
      class="tfb-input tfb-chef"
      :value="localChef"
      @input="localChef = ($event.target as HTMLInputElement).value; onChefInput()"
      placeholder="厨师..."
    />
    <input
      v-if="showLocationFilter"
      type="text"
      class="tfb-input tfb-location"
      :value="localLocation"
      @input="localLocation = ($event.target as HTMLInputElement).value; onLocationInput()"
      placeholder="位置..."
    />
    <select
      v-if="showAlertLevelFilter"
      class="tfb-input tfb-select tfb-alert"
      :value="localAlertLevel"
      @change="localAlertLevel = ($event.target as HTMLSelectElement).value; onAlertLevelChange()"
    >
      <option v-for="opt in alertLevelOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
    </select>
  </div>
</template>

<style scoped lang="scss">
@use '@/assets/styles/kds-theme' as *;

.task-filter-bar {
  display: flex;
  gap: 6px;
  padding: 6px 16px 8px;
  border-bottom: 1px solid $kds-border;
  align-items: center;
}

.tfb-date-group {
  display: flex;
  gap: 2px;
  align-items: center;
}

.tfb-nav-btn {
  background: $kds-surface-2;
  border: 1px solid $kds-border;
  border-radius: $kds-radius-sm;
  color: $kds-text-dim;
  font-size: 14px;
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  padding: 0;
  transition: all 0.15s;
  flex-shrink: 0;

  &:hover {
    color: $kds-text;
    border-color: $kds-border-light;
  }

  &--today {
    width: auto;
    font-size: 9px;
    padding: 0 6px;
  }
}

.tfb-input {
  background: $kds-surface-2;
  border: 1px solid $kds-border;
  border-radius: $kds-radius-sm;
  color: $kds-text;
  font-size: 11px;
  padding: 4px 8px;
  outline: none;
  transition: border-color 0.15s;

  &::placeholder {
    color: $kds-text-muted;
  }

  &:focus {
    border-color: #4f8cff;
  }

  // Date picker icon color
  &::-webkit-calendar-picker-indicator {
    filter: invert(0.7);
    cursor: pointer;
  }
}

.tfb-date {
  width: 105px;
}

.tfb-select {
  width: 80px;
  cursor: pointer;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='10' height='6'%3E%3Cpath d='M0 0l5 6 5-6z' fill='%238b8f96'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 8px center;
  padding-right: 22px;

  option {
    background: $kds-surface;
    color: $kds-text;
  }
}

.tfb-org {
  width: 100px;
}

.tfb-search {
  flex: 1;
  min-width: 60px;
}

.tfb-chef {
  width: 70px;
}

.tfb-location {
  width: 80px;
}

.tfb-alert {
  width: 80px;
}
</style>
