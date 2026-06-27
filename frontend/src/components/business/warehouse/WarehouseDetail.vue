<script setup lang="ts">
import { computed, h, ref, watch } from 'vue'
import { WarningFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { Warehouse, Location } from '@/types/warehouse'
import { WAREHOUSE_TYPE_MAP, WAREHOUSE_STATUS_OPTIONS, LOCATION_STATUS_OPTIONS } from '@/constants/warehouse'
import { warehouseApi } from '@/api/modules/warehouse'
import { useWarehouseStore } from '@/stores/modules/warehouse'
import { useDictCategoryStore } from '@/stores/modules/dict-category'
import { buildDictLabelMap } from '@/utils/dict-category'
import LocationForm from './LocationForm.vue'
import { WAREHOUSE_PERMISSIONS } from '@/constants/permission'
import { formatDateTime } from '@/utils'

interface Props {
  modelValue: boolean
  warehouseId?: number | null
}
const props = withDefaults(defineProps<Props>(), { modelValue: false, warehouseId: null })
const emit  = defineEmits<{
  'update:modelValue': [val: boolean]
  'edit': []
}>()

const store = useWarehouseStore()
const dictCategoryStore = useDictCategoryStore()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const detail           = ref<Warehouse | null>(null)
const locations        = ref<Location[]>([])
const locationKeyword  = ref('')
const locationLoading  = ref(false)

const locationFormVisible = ref(false)
const currentLocationId   = ref<number | null>(null)
const currentLocationData = ref<Location | null>(null)

const warehouseTypeLabelMap = computed(() => buildDictLabelMap(
  dictCategoryStore.getCachedOptions('warehouse_type', true),
  WAREHOUSE_TYPE_MAP
))

watch(() => props.modelValue, async (val) => {
  if (val && props.warehouseId) {
    dictCategoryStore.fetchOptions('warehouse_type', true)
    await loadDetail()
    await loadLocations()
  }
})

const loadDetail = async () => {
  if (!props.warehouseId) return
  try {
    const res = await warehouseApi.getDetail(props.warehouseId)
    if (res.code === 'SUCCESS') detail.value = res.data
  } catch {}
}

const loadLocations = async () => {
  if (!props.warehouseId) return
  locationLoading.value = true
  try {
    const res = await warehouseApi.getLocations({
      warehouseId: props.warehouseId,
      keyword: locationKeyword.value || undefined,
    })
    if (res.code === 'SUCCESS' && res.data) {
      locations.value = res.data.list ?? res.data
    }
  } catch {}
  finally { locationLoading.value = false }
}

const handleLocationSearch = () => loadLocations()

const handleDeleteLocation = async (row: Location) => {
  try {
    await ElMessageBox({
      title: '删除仓位',
      message: () => h('div', { class: 'warehouse-delete-confirm' }, [
        h('div', { class: 'warehouse-delete-confirm__content' }, [
          h(WarningFilled, { class: 'warehouse-delete-confirm__icon' }),
          h('div', { class: 'warehouse-delete-confirm__text' }, [
            h('div', { class: 'warehouse-delete-confirm__title' }, '删除仓位'),
            h('div', { class: 'warehouse-delete-confirm__description' }, '确认删除该仓位？删除后不可恢复。'),
          ]),
        ]),
      ]),
      customClass: 'warehouse-delete-message-box',
      showClose: false,
      closeOnClickModal: false,
      closeOnPressEscape: false,
      showCancelButton: true,
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    })
    await warehouseApi.deleteLocation(row.id)
    ElMessage.success('删除成功')
    await loadLocations()
    store.fetchStatistics()
  } catch {}
}

const openLocationForm = (row?: Location) => {
  currentLocationId.value   = row?.id ?? null
  currentLocationData.value = row ?? null
  locationFormVisible.value = true
}

const handleLocationSuccess = async () => {
  await loadLocations()
  await loadDetail()
  store.fetchStatistics()
}

const statusLabel = (status: string, opts: typeof LOCATION_STATUS_OPTIONS) =>
  opts.find(o => o.value === status)?.label ?? status

const statusType = (status: string, opts: typeof LOCATION_STATUS_OPTIONS) =>
  opts.find(o => o.value === status)?.type ?? 'info'

const warehouseStatusLabel = (status: string) =>
  WAREHOUSE_STATUS_OPTIONS.find(o => o.value === status)?.label ?? status

const warehouseStatusType = (status: string) =>
  WAREHOUSE_STATUS_OPTIONS.find(o => o.value === status)?.type ?? 'info'

const handleClose = () => {
  visible.value = false
}

const handleEdit = () => {
  visible.value = false
  emit('edit')
}
</script>

<template>
  <el-dialog v-model="visible" width="758px" :close-on-click-modal="false"
             :show-close="false" align-center class="warehouse-detail-dialog"
             @close="handleClose">
    <template #header>
      <div class="dialog-header">
        <span class="dialog-title">仓库详情</span>
        <div class="close-btn" @click="handleClose">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <line x1="6" y1="6" x2="18" y2="18" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
            <line x1="6" y1="18" x2="18" y2="6" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </div>
      </div>
    </template>

    <template v-if="detail">
      <!-- 基础信息 -->
      <div class="section-title info-section-title">
        <span class="title-bar" />
        <span>基础信息</span>
      </div>
      <div class="info-table">
        <div class="info-label">仓库名称</div>
        <div class="info-value">{{ detail.warehouseName }}</div>
        <div class="info-label">仓库编码</div>
        <div class="info-value">{{ detail.warehouseCode }}</div>
        <div class="info-label">仓库类型</div>
        <div class="info-value">{{ warehouseTypeLabelMap[detail.warehouseType] || detail.warehouseType }}</div>
        <div class="info-label">状态</div>
        <div class="info-value">
          <el-tag :type="warehouseStatusType(detail.status)" size="small">
            {{ warehouseStatusLabel(detail.status) }}
          </el-tag>
        </div>
        <div class="info-label">位置</div>
        <div class="info-value">{{ detail.address || '—' }}</div>
        <div class="info-label">负责人</div>
        <div class="info-value">{{ detail.managerName || '—' }}</div>
        <div class="info-label">联系方式</div>
        <div class="info-value">{{ detail.managerPhone || '—' }}</div>
        <div class="info-label">备注</div>
        <div class="info-value">{{ detail.remark || '—' }}</div>
      </div>

      <!-- 环境监测 -->
      <div v-if="detail.locationSensorData && detail.locationSensorData.length > 0" class="sensor-section">
        <div class="section-title info-section-title">
          <span class="title-bar" />
          <span>环境监测</span>
          <span class="sensor-summary">
            <span :class="['sensor-badge', `sensor-badge--${detail.tempStatus || 'normal'}`]">
              温度{{ detail.tempStatus === 'alarm' ? '告警' : detail.tempStatus === 'warning' ? '预警' : '正常' }}
            </span>
            <span :class="['sensor-badge', `sensor-badge--${detail.humidityStatus || 'normal'}`]">
              湿度{{ detail.humidityStatus === 'alarm' ? '告警' : detail.humidityStatus === 'warning' ? '预警' : '正常' }}
            </span>
          </span>
        </div>
        <div class="sensor-grid">
          <div v-for="sensor in detail.locationSensorData" :key="sensor.locationId" class="sensor-card">
            <div class="sensor-card__name">{{ sensor.locationName }}</div>
            <div class="sensor-card__row">
              <div :class="['sensor-card__item', `sensor-card__item--${sensor.tempStatus}`]">
                <span class="sensor-card__label">温度</span>
                <span class="sensor-card__value">
                  {{ sensor.currentTemperature != null ? sensor.currentTemperature + '℃' : '—' }}
                </span>
              </div>
              <div :class="['sensor-card__item', `sensor-card__item--${sensor.humidityStatus}`]">
                <span class="sensor-card__label">湿度</span>
                <span class="sensor-card__value">
                  {{ sensor.currentHumidity != null ? sensor.currentHumidity + '%' : '—' }}
                </span>
              </div>
            </div>
            <div class="sensor-card__time">
              {{ sensor.dataCollectedAt ? formatDateTime(sensor.dataCollectedAt) : '暂无数据' }}
            </div>
          </div>
        </div>
      </div>

      <!-- 仓位列表 -->
      <div class="location-header">
        <div class="section-title location-section-title">
          <span class="title-bar" />
          <span>仓库列表</span>
        </div>
        <div class="location-toolbar">
          <el-input v-model="locationKeyword" placeholder="仓位编码/名称"
                    clearable class="search-input" @keyup.enter="handleLocationSearch" />
          <el-button class="btn-search" @click="handleLocationSearch">查询</el-button>
          <el-button class="btn-add-location" v-permission="WAREHOUSE_PERMISSIONS.LOCATION_CREATE" @click="openLocationForm()">
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
              <path d="M7 1.75V12.25M1.75 7H12.25" stroke="#FFFFFF" stroke-width="1.75" stroke-linecap="round"/>
            </svg>
            <span>新增仓位</span>
          </el-button>
        </div>
      </div>

      <div class="table-wrapper">
        <el-table :data="locations" v-loading="locationLoading" class="location-table"
                  stripe
                  max-height="276"
                  :header-cell-style="{ height: '46px', padding: '0', background: '#F5F9FF', color: 'rgba(0,0,0,0.4)', fontWeight: '400', fontSize: '14px', fontFamily: 'PingFang SC, sans-serif', borderBottom: '1px solid #E7E7E7', borderTop: '1px solid #E7E7E7' }"
                  :cell-style="{ height: '46px', padding: '0', borderBottom: '1px solid #E7E7E7' }"
                  :row-style="{ height: '46px' }">
          <el-table-column type="index" label="序号" width="60" align="center" />
          <el-table-column prop="locationCode" label="仓位编码" />
          <el-table-column prop="locationName" label="仓位名称" />
          <el-table-column label="容量">
            <template #default="{ row }">
              {{ row.usedCapacity ?? 0 }} / {{ row.capacity ?? '—' }} {{ row.capacityUnit }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="102">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status, LOCATION_STATUS_OPTIONS)" size="small">
                {{ statusLabel(row.status, LOCATION_STATUS_OPTIONS) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140">
            <template #default="{ row }">
              <el-button type="primary" link size="small" v-permission="WAREHOUSE_PERMISSIONS.LOCATION_EDIT" @click="openLocationForm(row)">编辑</el-button>
              <el-button type="danger"  link size="small" v-permission="WAREHOUSE_PERMISSIONS.LOCATION_DELETE" @click="handleDeleteLocation(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>

    <!-- 仓位表单弹窗 -->
    <LocationForm
      v-if="detail"
      v-model="locationFormVisible"
      :warehouse-id="detail.id"
      :location-id="currentLocationId"
      :location-data="currentLocationData"
      @success="handleLocationSuccess"
    />

    <template #footer>
      <div class="dialog-footer">
        <el-button class="btn-cancel" @click="handleClose">取消</el-button>
        <el-button class="btn-edit" @click="handleEdit">编辑</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss">
/* ---- Dialog 容器（unscoped） ---- */
.warehouse-detail-dialog.el-dialog {
  width: 758px;
  height: 648px;
  background: #FFFFFF;
  --el-dialog-border-radius: 12px;
  --el-dialog-padding-primary: 0;
  border-radius: 12px !important;
  overflow: hidden;
  padding: 0 !important;
  display: flex !important;
  flex-direction: column !important;
  margin: auto !important;
}

.warehouse-detail-dialog.el-dialog .el-dialog__header {
  padding: 24px 24px 13px !important;
  margin-right: 0;
  border-bottom: 1px solid #E1E2E9;
}

.warehouse-detail-dialog.el-dialog .el-dialog__body {
  flex: 1;
  min-height: 0;
  padding: 16px 24px 24px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

.warehouse-detail-dialog.el-dialog .el-dialog__footer {
  padding: 12px 24px 16px !important;
  flex-shrink: 0;
  border-top: 1px solid #E1E2E9;
  box-sizing: border-box;
  text-align: right;
}

.warehouse-detail-dialog.el-dialog .el-dialog__footer .dialog-footer {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 9px;
  justify-content: flex-end;
}

/* ---- 表格样式（unscoped，覆盖 el-table 内部） ---- */
.warehouse-detail-dialog .location-table .el-table__header th .cell {
  line-height: 46px;
  color: rgba(0, 0, 0, 0.4);
}

.warehouse-detail-dialog .location-table .el-table__body td .cell {
  font-family: 'PingFang SC', sans-serif;
  font-size: 14px;
  color: rgba(0, 0, 0, 0.9);
}

.warehouse-detail-dialog .location-table .el-table__body td .cell,
.warehouse-detail-dialog .location-table .el-table__header th .cell {
  max-width: 190px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.warehouse-detail-dialog .location-table .el-button--primary.is-link {
  font-size: 14px;
  color: #7288FA;
}

.warehouse-detail-dialog .location-table .el-button--primary.is-link:hover {
  color: #5a6fd8;
}

.warehouse-detail-dialog .location-table .el-button--danger.is-link {
  font-size: 14px;
  color: #FF4D4F;
}

.warehouse-detail-dialog .location-table .el-button--danger.is-link:hover {
  color: #e04347;
}

.warehouse-detail-dialog .location-table .el-table__body tr.el-table__row--striped td {
  background: #FAFCFF !important;
}

.warehouse-detail-dialog .location-table .el-table__body tr:hover > td {
  background: #F5F9FF !important;
}

.warehouse-detail-dialog .location-table::before {
  display: none;
}
</style>

<style lang="scss" scoped>
/* ---- 头部 ---- */
.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 32px;
}

.dialog-title {
  font-family: 'Poppins', 'PingFang SC', sans-serif;
  font-weight: 500;
  font-size: 20px;
  line-height: 30px;
  color: #000000;
}

.title-bar {
  display: inline-block;
  width: 4px;
  height: 20px;
  background: #7288FA;
  border-radius: 2px;
  margin-right: 8px;
  flex-shrink: 0;
}

.close-btn {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 32px;
  height: 32px;
  background: #FFF2E2;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;

  &:hover {
    background: #FFE8CC;
  }
}

/* ---- 区块标题 ---- */
.section-title {
  display: flex;
  align-items: center;
  font-size: 15px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.85);
}

.info-section-title {
  margin-bottom: 12px;
}

.location-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 24px;
  margin-bottom: 12px;
}

.location-section-title {
  margin-bottom: 0;
}

/* ---- 基础信息表格 710×160 ---- */
.info-table {
  display: grid;
  grid-template-columns: 112px 1fr 112px 1fr;
  width: 100%;
  border-top: 1px solid #E1E2E9;
  border-left: 1px solid #E1E2E9;
}

.info-label {
  background: #F5F7FA;
  border-right: 1px solid #ECEEF5;
  border-bottom: 1px solid #E1E2E9;
  padding: 0 12px;
  height: 40px;
  display: flex;
  align-items: center;
  font-family: 'PingFang SC', sans-serif;
  font-size: 14px;
  line-height: 20px;
  color: #333333;
}

.info-value {
  border-right: 1px solid #E1E2E9;
  border-bottom: 1px solid #E1E2E9;
  padding: 0 12px;
  height: 40px;
  display: flex;
  align-items: center;
  font-family: 'PingFang SC', sans-serif;
  font-size: 14px;
  line-height: 20px;
  color: #333333;
}

/* ---- 仓位工具栏 ---- */
.location-toolbar {
  display: flex;
  align-items: center;
  gap: 0;
  justify-content: flex-end;
}

.btn-search {
  margin-left: 11px;
}

.search-input {
  width: 200px;

  :deep(.el-input__wrapper) {
    height: 32px;
    padding: 5px 8px;
    border-radius: 3px;
    box-shadow: 0 0 0 1px #DCDCDC inset !important;

    &:hover {
      box-shadow: 0 0 0 1px #DCDCDC inset !important;
    }

    &.is-focus {
      box-shadow: 0 0 0 1px #7288FA inset !important;
    }
  }

  :deep(.el-input__inner) {
    font-family: 'PingFang SC', sans-serif;
    font-size: 14px;
    height: 22px;
    line-height: 22px;

    &::placeholder {
      color: rgba(0, 0, 0, 0.4);
    }
  }
}

.btn-search {
  width: 60px;
  height: 32px;
  background: #F2F4F8;
  border: none;
  border-radius: 6px;
  font-family: 'PingFang SC', sans-serif;
  font-size: 14px;
  line-height: 22px;
  color: rgba(0, 0, 0, 0.9);
  padding: 5px 16px;

  &:hover,
  &:focus {
    background: #E8EBF0;
    border: none;
    color: rgba(0, 0, 0, 0.9);
  }
}

.btn-add-location {
  margin-left: 8px;
  width: 110px;
  height: 32px;
  background: #7288FA;
  border-color: #7288FA;
  border-radius: 6px;
  box-shadow: 0 2px 0 rgba(0, 0, 0, 0.043);
  font-family: 'Roboto', 'PingFang SC', sans-serif;
  font-size: 14px;
  line-height: 22px;
  color: #FFFFFF;
  padding: 5px 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;

  &:hover,
  &:focus {
    background: #7288FA;
    border-color: #7288FA;
    color: #FFFFFF;
  }
}

/* ---- 表格容器 ---- */
.table-wrapper {
  flex: 0 0 auto;
  min-height: 0;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

/* ---- 底部 ---- */
.btn-cancel {
  width: 58px;
  height: 32px;
  background: #FFFFFF;
  border: 1px solid #BEC0CA;
  border-radius: 6px;
  box-shadow: 0 2px 0 rgba(0, 0, 0, 0.016);
  color: #53545C;
  font-family: 'PingFang SC', sans-serif;
  font-size: 13px;
  line-height: 22px;

  &:hover,
  &:focus {
    background: #FFFFFF;
    border-color: #BEC0CA;
    color: #53545C;
  }
}

.btn-edit {
  width: 60px;
  height: 32px;
  background: #7288FA;
  border-color: #7288FA;
  border-radius: 6px;
  box-shadow: 0 2px 0 rgba(0, 0, 0, 0.043);
  color: #FFFFFF;
  font-family: 'Roboto', sans-serif;
  font-size: 14px;
  line-height: 22px;

  &:hover,
  &:focus {
    background: #7288FA;
    border-color: #7288FA;
    color: #FFFFFF;
  }
}

/* ---- 环境监测 ---- */
.sensor-section {
  margin-top: 20px;
}

.sensor-summary {
  display: inline-flex;
  gap: 8px;
  margin-left: 16px;
}

.sensor-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 500;

  &--normal { background: #f6ffed; color: #52c41a; }
  &--warning { background: #fffbe6; color: #faad14; }
  &--alarm { background: #fff2f0; color: #ff4d4f; }
}

.sensor-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px;
  margin-top: 12px;
}

.sensor-card {
  background: #fafbfc;
  border: 1px solid #eceef5;
  border-radius: 8px;
  padding: 12px 14px;
  transition: box-shadow 0.2s;

  &:hover {
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  }

  &__name {
    font-size: 13px;
    font-weight: 600;
    color: rgba(0, 0, 0, 0.85);
    margin-bottom: 8px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  &__row {
    display: flex;
    gap: 16px;
  }

  &__item {
    display: flex;
    flex-direction: column;
    gap: 2px;

    &--normal .sensor-card__value { color: #52c41a; }
    &--warning .sensor-card__value { color: #faad14; }
    &--alarm .sensor-card__value { color: #ff4d4f; }
  }

  &__label {
    font-size: 12px;
    color: rgba(0, 0, 0, 0.45);
  }

  &__value {
    font-size: 18px;
    font-weight: 600;
    font-family: 'Roboto', 'DIN Alternate', sans-serif;
  }

  &__time {
    font-size: 11px;
    color: rgba(0, 0, 0, 0.35);
    margin-top: 8px;
  }
}
</style>
