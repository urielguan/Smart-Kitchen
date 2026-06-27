<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { orgApi } from '@/api/modules/org'
import { formatDateTime } from '@/utils'
import type { Organization } from '@/types/org'
import { ORG_TYPE_MAP, ORG_STATUS_MAP } from '@/constants/org'
import { useDictCategoryStore } from '@/stores/modules/dict-category'
import { buildDictLabelMap } from '@/utils/dict-category'
import { ORG_PERMISSIONS } from '@/constants/permission'

interface Props {
  modelValue: boolean
  orgId?: number | null
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
  orgId: null
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  edit: [org: Organization]
}>()
const dictCategoryStore = useDictCategoryStore()

/** 弹窗显示状态 */
const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

/** 组织详情 */
const org = ref<Organization | null>(null)

/** 子组织列表 */
const children = ref<Organization[]>([])

/** 加载状态 */
const loading = ref(false)

const orgTypeLabelMap = computed(() => buildDictLabelMap(
  dictCategoryStore.getCachedOptions('org_type', true),
  Object.fromEntries(Object.entries(ORG_TYPE_MAP).map(([key, value]) => [key, value.label]))
))

/** 获取组织详情 */
const fetchDetail = async () => {
  if (!props.orgId) return

  loading.value = true
  try {
    // 并行获取详情和子组织
    const [detailRes, childrenRes] = await Promise.all([
      orgApi.getDetail(props.orgId),
      orgApi.getList({ pageNum: 1, pageSize: 100, parentId: props.orgId })
    ])

    if (detailRes.code === 'SUCCESS' && detailRes.data) {
      org.value = detailRes.data
    }

    if (childrenRes.code === 'SUCCESS' && childrenRes.data) {
      children.value = childrenRes.data.list || []
    }
  } catch (error) {
    console.error('获取组织详情失败:', error)
  } finally {
    loading.value = false
  }
}

/** 监听弹窗显示，获取详情 */
watch(
  () => props.modelValue,
  (val) => {
    if (val && props.orgId) {
      dictCategoryStore.fetchOptions('org_type', true)
      fetchDetail()
    } else {
      org.value = null
      children.value = []
    }
  }
)

/** 编辑 */
const handleEdit = () => {
  if (org.value) {
    emit('edit', org.value)
    visible.value = false
  }
}

/** 关闭弹窗 */
const handleClose = () => {
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
    class="org-detail-dialog"
  >
    <template #header>
      <div class="dialog-header">
        <span class="dialog-title">组织详情</span>
        <div class="close-btn" @click="handleClose">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <line x1="6" y1="6" x2="18" y2="18" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
            <line x1="6" y1="18" x2="18" y2="6" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </div>
      </div>
    </template>

    <div v-loading="loading" class="detail-body">
      <template v-if="org">
        <!-- 基本信息 -->
        <div class="section-title">
          <span class="title-bar" />
          <span>基本信息</span>
        </div>
        <div class="info-table">
          <div class="info-label">组织名称</div>
          <div class="info-value">{{ org.orgName }}</div>
          <div class="info-label">组织编码</div>
          <div class="info-value">{{ org.orgCode }}</div>
          <div class="info-label">组织类型</div>
          <div class="info-value">{{ orgTypeLabelMap[org.orgType] || org.orgType }}</div>
          <div class="info-label">组织层级</div>
          <div class="info-value">第 {{ org.level }} 级</div>
          <div class="info-label">上级组织</div>
          <div class="info-value">{{ org.parentName || '—' }}</div>
          <div class="info-label">组织路径</div>
          <div class="info-value">{{ org.path }}</div>
          <div class="info-label">状态</div>
          <div class="info-value">
            <el-tag :type="ORG_STATUS_MAP[org.status]?.tagType" size="small">
              {{ ORG_STATUS_MAP[org.status]?.label }}
            </el-tag>
          </div>
          <div class="info-label">排序序号</div>
          <div class="info-value">{{ org.sortOrder }}</div>
        </div>

        <!-- 联系信息 -->
        <div class="section-title" style="margin-top: 20px">
          <span class="title-bar" />
          <span>联系信息</span>
        </div>
        <div class="info-table">
          <div class="info-label">负责人</div>
          <div class="info-value">{{ org.leaderName || '—' }}</div>
          <div class="info-label">联系电话</div>
          <div class="info-value">{{ org.contactPhone || '—' }}</div>
          <div class="info-label">地址</div>
          <div class="info-value info-value--span3">{{ org.address || '—' }}</div>
        </div>

        <!-- 子组织 -->
        <div class="section-title" style="margin-top: 20px">
          <span class="title-bar" />
          <span>子组织</span>
          <span class="children-count">（共 {{ children.length }} 个）</span>
        </div>
        <div v-if="children.length > 0" class="children-list">
          <div v-for="child in children" :key="child.id" class="children-item">
            <span class="children-name">{{ child.orgName }}</span>
            <el-tag :type="ORG_TYPE_MAP[child.orgType]?.tagType || 'info'" size="small">
              {{ orgTypeLabelMap[child.orgType] || child.orgType }}
            </el-tag>
            <el-tag :type="ORG_STATUS_MAP[child.status]?.tagType" size="small">
              {{ ORG_STATUS_MAP[child.status]?.label }}
            </el-tag>
          </div>
        </div>
        <div v-else class="children-empty">暂无子组织</div>

        <!-- 时间信息 -->
        <div class="section-title" style="margin-top: 20px">
          <span class="title-bar" />
          <span>时间信息</span>
        </div>
        <div class="info-table">
          <div class="info-label">创建时间</div>
          <div class="info-value">{{ formatDateTime(org.createdAt) }}</div>
          <div class="info-label">更新时间</div>
          <div class="info-value">{{ formatDateTime(org.updatedAt) }}</div>
        </div>
      </template>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button class="btn-edit" v-permission="ORG_PERMISSIONS.EDIT" @click="handleEdit">编辑</el-button>
        <el-button class="btn-cancel" @click="handleClose">关闭</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss">
/* ---- Dialog 容器（unscoped） ---- */
.org-detail-dialog.el-dialog {
  width: 758px;
  max-height: 80vh;
  background: #FFFFFF;
  --el-dialog-border-radius: 12px;
  --el-dialog-padding-primary: 0;
  border-radius: 12px !important;
  overflow: hidden;
  padding: 0 !important;
  display: flex !important;
  flex-direction: column !important;
}

.org-detail-dialog.el-dialog .el-dialog__header {
  padding: 24px 24px 13px !important;
  margin-right: 0;
  border-bottom: 1px solid #E1E2E9;
}

.org-detail-dialog.el-dialog .el-dialog__body {
  padding: 16px 24px 24px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.org-detail-dialog.el-dialog .el-dialog__footer {
  padding: 0;
  border-top: 1px solid #E1E2E9;
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

/* ---- 底部 ---- */
.dialog-footer {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 9px;
  justify-content: flex-end;
  padding: 12px 24px 16px;
}

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
  height: 32px;
  padding: 5px 16px;
  background: #7288FA;
  border: 1px solid #7288FA;
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

/* ---- 内容 ---- */
.detail-body {
  min-height: 200px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.9);
  margin-bottom: 12px;

  .title-bar {
    width: 4px;
    height: 20px;
    background: #7288FA;
    border-radius: 2px;
  }

  .children-count {
    font-weight: 400;
    color: rgba(0, 0, 0, 0.4);
    font-size: 12px;
  }
}

.info-table {
  display: grid;
  grid-template-columns: 112px 1fr 112px 1fr;
  border-top: 1px solid #E1E2E9;
  border-left: 1px solid #E1E2E9;
}

.info-label {
  background: #F5F7FA;
  padding: 0 12px;
  display: flex;
  align-items: center;
  min-height: 40px;
  font-size: 14px;
  color: #333;
  border-right: 1px solid #E1E2E9;
  border-bottom: 1px solid #E1E2E9;
}

.info-value {
  padding: 0 12px;
  display: flex;
  align-items: center;
  min-height: 40px;
  font-size: 14px;
  color: rgba(0, 0, 0, 0.9);
  word-break: break-all;
  border-right: 1px solid #E1E2E9;
  border-bottom: 1px solid #E1E2E9;

  &--span3 {
    grid-column: span 3;
  }
}

.children-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 150px;
  overflow-y: auto;
}

.children-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: #F5F7FA;
  border-radius: 4px;

  .children-name {
    flex: 1;
    font-size: 14px;
    color: rgba(0, 0, 0, 0.9);
  }
}

.children-empty {
  padding: 20px;
  text-align: center;
  color: rgba(0, 0, 0, 0.4);
  font-size: 14px;
  background: #F5F7FA;
  border-radius: 4px;
}
</style>
