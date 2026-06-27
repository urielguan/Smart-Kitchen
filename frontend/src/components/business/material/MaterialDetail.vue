<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { materialApi } from '@/api/modules/material'
import { calcRemainingDays, formatDateTime, getImageUrl } from '@/utils'
import type { Material } from '@/types/material'
import StatusTag from '@/components/common/StatusTag.vue'
import { MATERIAL_PERMISSIONS } from '@/constants/permission'

interface Props {
  modelValue: boolean
  materialId?: number | null
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
  materialId: null
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  edit: [material: Material]
  mapping: [material: Material]
}>()

/** 弹窗显示状态 */
const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

/** 物料详情 */
const material = ref<Material | null>(null)

/** 加载状态 */
const loading = ref(false)

/** 获取物料详情 */
const fetchDetail = async () => {
  if (!props.materialId) return

  loading.value = true
  try {
    const res = await materialApi.getDetail(props.materialId)
    material.value = res.data
  } catch (error) {
    console.error('获取物料详情失败:', error)
  } finally {
    loading.value = false
  }
}

/** 监听弹窗显示，获取详情 */
watch(
  () => props.modelValue,
  (val) => {
    if (val && props.materialId) {
      fetchDetail()
    } else {
      material.value = null
    }
  }
)

/** 到期日期显示 */
const expiryDateDisplay = computed(() => {
  if (!material.value?.expiryDate) return '—'
  const days = calcRemainingDays(material.value.expiryDate)
  return `${material.value.expiryDate}（剩余 ${days} 天）`
})

/** 编辑 */
const handleEdit = () => {
  if (material.value) {
    emit('edit', material.value)
    visible.value = false
  }
}

/** 关闭弹窗 */
const handleClose = () => {
  visible.value = false
}

const handleSyncNutrition = async () => {
  if (!material.value?.id) return
  loading.value = true
  try {
    const res = await materialApi.syncNutrition(material.value.id)
    if (res.code === 'SUCCESS') {
      ElMessage.success(`营养同步完成：${res.data?.foodName || material.value.materialName}`)
      await fetchDetail()
    }
  } finally {
    loading.value = false
  }
}

const handleOpenMapping = () => {
  if (!material.value) return
  emit('mapping', material.value)
  visible.value = false
}
</script>

<template>
  <el-dialog
    v-model="visible"
    width="758px"
    :close-on-click-modal="false"
    :show-close="false"
    align-center
    @close="handleClose"
    class="material-detail-dialog"
  >
    <template #header>
      <div class="dialog-header">
        <span class="dialog-title">物料详情</span>
        <div class="close-btn" @click="handleClose">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <line x1="6" y1="6" x2="18" y2="18" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
            <line x1="6" y1="18" x2="18" y2="6" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </div>
      </div>
    </template>

    <div v-loading="loading">
      <template v-if="material">
        <!-- 基础信息 -->
        <div class="section-title info-section-title">
          <span class="title-bar" />
          <span>基础信息</span>
        </div>
        <div class="info-table">
          <div class="info-label">物料名称</div>
          <div class="info-value">{{ material.materialName }}</div>
          <div class="info-label">物料编码</div>
          <div class="info-value">{{ material.materialCode }}</div>
          <div class="info-label">规格</div>
          <div class="info-value">{{ material.materialSpec }}</div>
          <div class="info-label">单位</div>
          <div class="info-value">{{ material.unit }}</div>
          <div class="info-label">类别</div>
          <div class="info-value">{{ material.categoryName }}</div>
          <div class="info-label">保质期</div>
          <div class="info-value">{{ material.shelfLifeDays }} 天</div>
          <div class="info-label">临期日期</div>
          <div class="info-value">{{ material.nearExpiryDays || '—' }} 天</div>
          <div class="info-label">预警天数</div>
          <div class="info-value">{{ material.warningDays || '—' }} 天</div>
          <div class="info-label">创建时间</div>
          <div class="info-value">{{ formatDateTime(material.createdAt) }}</div>
          <div class="info-label"></div>
          <div class="info-value"></div>
        </div>

        <!-- 库存信息 -->
        <div class="section-title info-section-title" style="margin-top: 20px">
          <span class="title-bar" />
          <span>库存信息</span>
        </div>
        <div class="info-table">
          <div class="info-label">当前库存</div>
          <div class="info-value">{{ material.currentStock }} {{ material.unit }}</div>
          <div class="info-label">库存状态</div>
          <div class="info-value"><StatusTag :status="material.stockStatus" /></div>
          <div class="info-label">最低库存</div>
          <div class="info-value">{{ material.minStock }} {{ material.unit }}</div>
          <div class="info-label">最高库存</div>
          <div class="info-value">{{ material.maxStock }} {{ material.unit }}</div>
        </div>

        <!-- 营养与标准食品映射 -->
        <div class="section-title info-section-title" style="margin-top: 20px">
          <span class="title-bar" />
          <span>营养与标准食品映射</span>
        </div>
        <div class="info-table">
          <div class="info-label">标准食品关联</div>
          <div class="info-value">
            {{ material.foodName || '未映射' }}
            <span v-if="material.foodCode" class="sub-value">（{{ material.foodCode }}）</span>
          </div>
          <div class="info-label">营养来源</div>
          <div class="info-value">{{ material.nutritionSourceType || '未同步' }}</div>
          <div class="info-label">同步时间</div>
          <div class="info-value">{{ material.nutritionSyncedAt || '未同步' }}</div>
          <div class="info-label">营养状态</div>
          <div class="info-value">
            <StatusTag :status="material.nutritionSourceType ? 'active' : 'inactive'" />
          </div>
          <div class="info-label">基础营养</div>
          <div class="info-value info-value--span3">
            热量 {{ material.calories ?? '—' }} /
            蛋白 {{ material.protein ?? '—' }} /
            碳水 {{ material.carbohydrate ?? '—' }} /
            脂肪 {{ material.fat ?? '—' }}
          </div>
        </div>
        <div class="detail-actions">
          <el-button size="small" @click="handleOpenMapping">调整映射</el-button>
          <el-button size="small" @click="handleSyncNutrition">重新同步营养</el-button>
        </div>

        <!-- 存储要求 & 备注 -->
        <div class="section-title info-section-title" style="margin-top: 20px">
          <span class="title-bar" />
          <span>存储要求 & 备注</span>
        </div>
        <div class="info-table">
          <div class="info-label">存储要求</div>
          <div class="info-value info-value--span3">{{ material.storageRequire || '—' }}</div>
          <div class="info-label">备注</div>
          <div class="info-value info-value--span3">{{ material.remark || '—' }}</div>
        </div>

        <!-- 物料图片 -->
        <template v-if="material.imageUrl">
          <div class="section-title info-section-title" style="margin-top: 20px">
            <span class="title-bar" />
            <span>物料图片</span>
          </div>
          <div class="detail-image-wrapper">
            <img :src="getImageUrl(material.imageUrl)" alt="物料图片" class="detail-image" />
          </div>
        </template>
      </template>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button class="btn-cancel" @click="handleClose">取消</el-button>
        <el-button class="btn-edit" v-permission="MATERIAL_PERMISSIONS.EDIT" @click="handleEdit">编辑</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss">
/* ---- Dialog 容器（unscoped） ---- */
.material-detail-dialog.el-dialog {
  width: 758px;
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

.material-detail-dialog.el-dialog .el-dialog__header {
  padding: 24px 24px 13px !important;
  margin-right: 0;
  border-bottom: 1px solid #E1E2E9;
}

.material-detail-dialog.el-dialog .el-dialog__body {
  flex: 1;
  min-height: 0;
  padding: 16px 24px 24px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

.material-detail-dialog.el-dialog .el-dialog__footer {
  padding: 12px 24px 16px !important;
  flex-shrink: 0;
  border-top: 1px solid #E1E2E9;
  box-sizing: border-box;
  text-align: right;
}

.material-detail-dialog.el-dialog .el-dialog__footer .dialog-footer {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 9px;
  justify-content: flex-end;
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

.title-bar {
  display: inline-block;
  width: 4px;
  height: 20px;
  background: #7288FA;
  border-radius: 2px;
  margin-right: 8px;
  flex-shrink: 0;
}

/* ---- 基础信息表格 ---- */
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
  min-height: 40px;
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
  min-height: 40px;
  display: flex;
  align-items: center;
  font-family: 'PingFang SC', sans-serif;
  font-size: 14px;
  line-height: 20px;
  color: #333333;

  &--span3 {
    grid-column: span 3;
    height: auto;
    min-height: 40px;
    padding: 5px 12px;
    white-space: pre-wrap;
    word-break: break-all;
  }
}

/* ---- 物料图片 ---- */
.detail-image-wrapper {
  text-align: center;
}

.detail-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;

  :deep(.el-button) {
    background: #FFFFFF;
    border: 1px solid #BEC0CA;
    color: #53545C;

    &:hover,
    &:focus {
      background: #F5F7FA;
      border-color: #7288FA;
      color: #7288FA;
    }
  }
}

.sub-value {
  color: #667085;
}

.detail-image {
  max-width: 200px;
  max-height: 200px;
  border-radius: 8px;
  object-fit: cover;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

/* ---- 底部按钮 ---- */
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
    background: #F5F7FA;
    border-color: #7288FA;
    color: #7288FA;
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
    background: #5C75E8;
    border-color: #5C75E8;
    color: #FFFFFF;
  }
}
</style>

<style lang="scss">
.material-detail-dialog {
  .el-dialog {
    border-radius: 12px;
  }

  .el-dialog__header {
    padding: 24px 24px 16px;
    margin-right: 0;
    border-bottom: 1px solid #E1E2E9;
  }

  .el-dialog__body {
    padding: 16px 24px 24px;
  }
}
</style>
