<script setup lang="ts">
import { computed, inject, type ComputedRef } from 'vue'
import type { OrgTreeNode as OrgTreeNodeType } from '@/types/org'
import { ORG_TYPE_MAP, ORG_STATUS_MAP } from '@/constants/org'
import { useDictCategoryStore } from '@/stores/modules/dict-category'
import { buildDictLabelMap } from '@/utils/dict-category'
import { ORG_PERMISSIONS } from '@/constants/permission'

interface Props {
  node: OrgTreeNodeType
  level: number
  expandedKeys: Set<number>
}

const props = defineProps<Props>()

const rowIndexMap = inject<ComputedRef<Map<number, number>>>('orgRowIndexMap')
const dictCategoryStore = useDictCategoryStore()

const emit = defineEmits<{
  toggle: [id: number]
  'add-child': [node: OrgTreeNodeType]
  'view-members': [node: OrgTreeNodeType]
  edit: [node: OrgTreeNodeType]
  delete: [node: OrgTreeNodeType]
  'update-status': [node: OrgTreeNodeType, status: 'active' | 'inactive']
}>()

/** 是否有子节点 */
const hasChildren = computed(() => props.node.children && props.node.children.length > 0)

/** 是否展开 */
const isExpanded = computed(() => props.expandedKeys.has(props.node.id))

/** 组织类型标签 */
const typeTag = computed(() => ORG_TYPE_MAP[props.node.orgType]?.tagType || 'info')
const orgTypeLabelMap = computed(() => buildDictLabelMap(
  dictCategoryStore.getCachedOptions('org_type', true),
  Object.fromEntries(Object.entries(ORG_TYPE_MAP).map(([key, value]) => [key, value.label]))
))
const typeLabel = computed(() => orgTypeLabelMap.value[props.node.orgType] || props.node.orgType)

/** 状态标签 */
const statusTag = computed(() => ORG_STATUS_MAP[props.node.status || 'active']?.tagType || 'success')
const statusLabel = computed(() => ORG_STATUS_MAP[props.node.status || 'active']?.label || '启用')

/** 行背景色：根据全局行索引奇偶交替 */
const isEven = computed(() => {
  const idx = rowIndexMap?.value?.get(props.node.id)
  return idx !== undefined && idx % 2 === 0
})

/** 是否允许新增子组织 */
const canAddChild = computed(() => props.node.status === 'active')

/** 切换展开/收起 */
const toggle = () => {
  emit('toggle', props.node.id)
}

/** 新增子组织 */
const addChild = () => {
  emit('add-child', props.node)
}

/** 查看成员 */
const viewMembers = () => {
  emit('view-members', props.node)
}

/** 编辑 */
const edit = () => {
  emit('edit', props.node)
}

/** 删除 */
const deleteNode = () => {
  emit('delete', props.node)
}

/** 更新状态 */
const toggleStatus = () => {
  const newStatus = props.node.status === 'active' ? 'inactive' : 'active'
  emit('update-status', props.node, newStatus)
}
</script>

<template>
  <div class="org-tree-node-wrapper">
    <!-- 节点行 -->
    <div
      class="org-tree-node"
      :class="{ 'row-even': isEven }"
      :style="{ paddingLeft: 16 + level * 24 + 'px' }"
    >
      <!-- 左侧：箭头 + 图标 + 信息 -->
      <div class="org-node-left">
        <!-- 箭头 -->
        <span
          class="org-arrow"
          :class="{ collapsed: !isExpanded, invisible: !hasChildren }"
          @click="toggle"
        >
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M13.1315 4.6875H2.86897C2.56116 4.6875 2.38929 5.0125 2.57991 5.23438L7.71116 11.1844C7.85804 11.3547 8.14085 11.3547 8.28929 11.1844L13.4205 5.23438C13.6112 5.0125 13.4393 4.6875 13.1315 4.6875Z" fill="#454F59" fill-opacity="0.8"/>
          </svg>
        </span>

        <!-- 图标 -->
        <span class="org-icon">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M15.4286 6.78354H13.5V4.75497C13.5 4.4389 13.2446 4.18354 12.9286 4.18354H7.30357L5.20893 2.17997C5.18228 2.15502 5.14722 2.141 5.11071 2.14069H0.571429C0.255357 2.14069 0 2.39604 0 2.71211V13.2835C0 13.5996 0.255357 13.855 0.571429 13.855H13.0357C13.2679 13.855 13.4786 13.7139 13.5661 13.4978L15.9589 7.56926C15.9857 7.5014 16 7.42819 16 7.35497C16 7.0389 15.7446 6.78354 15.4286 6.78354ZM1.28571 3.4264H4.65179L6.7875 5.46926H12.2143V6.78354H3.10714C2.875 6.78354 2.66429 6.92461 2.57679 7.14069L1.28571 10.3407V3.4264ZM12.6304 12.5693H1.69643L3.54107 7.99783H14.4768L12.6304 12.5693Z" fill="#454F59" fill-opacity="0.8"/>
          </svg>
        </span>

        <!-- 信息 -->
        <div class="org-info">
          <!-- 主信息行：名称 + 类型标签 + 状态标签 -->
          <div class="org-main">
            <span class="org-name">{{ node.orgName }}</span>
            <el-tag :type="typeTag" size="small">{{ typeLabel }}</el-tag>
            <el-tag :type="statusTag" size="small">{{ statusLabel }}</el-tag>
          </div>
          <!-- 副信息行 -->
          <div class="org-sub">
            <span>编号：{{ node.orgCode }}</span>
            <template v-if="node.parentName">
              <span class="org-sub-divider">|</span>
              <span>上级：{{ node.parentName }}</span>
            </template>
            <template v-if="node.leaderName">
              <span class="org-sub-divider">|</span>
              <span>负责人：{{ node.leaderName }}</span>
            </template>
            <span class="org-sub-divider">|</span>
            <span>成员：{{ node.memberCount || 0 }}人</span>
          </div>
        </div>
      </div>

      <!-- 右侧：操作 -->
      <div class="org-node-right">
        <!-- 新增子组织 -->
        <el-button
          type="primary"
          link
          :disabled="!canAddChild"
          :title="canAddChild ? '新增子组织' : '停用组织不可新增子组织'"
          v-permission="ORG_PERMISSIONS.CREATE"
          @click="addChild"
        >新增子组织</el-button>

        <!-- 查看成员 -->
        <el-button
          type="primary"
          link
          title="查看成员"
          @click="viewMembers"
        >查看成员</el-button>

        <!-- 编辑 -->
        <el-button type="primary" link v-permission="ORG_PERMISSIONS.EDIT" @click="edit">编辑</el-button>

        <!-- 启用/停用 -->
        <el-button
          v-if="node.status === 'active'"
          type="warning"
          link
          v-permission="ORG_PERMISSIONS.STATUS"
          @click="toggleStatus"
        >停用</el-button>
        <el-button
          v-else
          type="success"
          link
          v-permission="ORG_PERMISSIONS.STATUS"
          @click="toggleStatus"
        >启用</el-button>

        <!-- 删除 -->
        <el-button type="danger" link v-permission="ORG_PERMISSIONS.DELETE" @click="deleteNode">删除</el-button>
      </div>
    </div>

    <!-- 递归子节点 -->
    <div v-if="hasChildren" v-show="isExpanded" class="org-children">
      <OrgTreeNode
        v-for="child in node.children"
        :key="child.id"
        :node="child"
        :level="level + 1"
        :expanded-keys="expandedKeys"
        @toggle="$emit('toggle', $event)"
        @add-child="$emit('add-child', $event)"
        @view-members="$emit('view-members', $event)"
        @edit="$emit('edit', $event)"
        @delete="$emit('delete', $event)"
        @update-status="(node, status) => $emit('update-status', node, status)"
      />
    </div>
  </div>
</template>

<style lang="scss" scoped>
.org-tree-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px 8px;
  border-bottom: 1px solid #E7E7E7;
  background: #FFFFFF;
  transition: background 0.15s;
  min-height: 46px;

  &.row-even {
    background: #F5F9FF;
    &:hover {
      background: #EBF2FF;
    }
  }

  &:hover {
    background: #fafafa;
  }
}

.org-node-left {
  display: flex;
  align-items: flex-start;
  flex: 1;
  min-width: 0;
  gap: 6px;
}

.org-arrow {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  margin-top: 2px;
  cursor: pointer;
  color: rgba(69, 79, 89, 0.8);
  flex-shrink: 0;
  transition: transform 0.2s;

  &.collapsed {
    transform: rotate(-90deg);
  }

  &.invisible {
    visibility: hidden;
    cursor: default;
  }
}

.org-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-top: 4px;
}

.org-info {
  min-width: 0;
  flex: 1;
}

.org-main {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}

.org-name {
  font-weight: 600;
  font-size: 16px;
  color: #303133;
}

.org-sub {
  font-size: 14px;
  color: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;

  .org-sub-divider {
    color: #D9D9D9;
  }
}

.org-node-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  margin-left: 12px;

  :deep(.el-button) {
    margin-left: 0;
  }

  :deep(.el-button--primary.is-link) {
    color: #5570F1;
    &:hover { color: #2E45D6; }
    &:focus { color: #5570F1; }
  }

  :deep(.el-button--danger.is-link) {
    color: #FF7474;
    &:hover { color: #FF3D3D; }
    &:focus { color: #FF7474; }
  }

  :deep(.el-button--warning.is-link) {
    color: #ED8A40;
    &:hover { color: #D97706; }
    &:focus { color: #ED8A40; }
  }

  :deep(.el-button--success.is-link) {
    color: #43C08B;
    &:hover { color: #2BA471; }
    &:focus { color: #43C08B; }
  }

  :deep(.el-button.is-disabled) {
    opacity: 0.45;
  }
}

.org-children {
  // 子节点容器无需额外样式
}

/* ---- Tag 样式 ---- */
:deep(.el-tag--success) {
  background: #E3F9E9;
  border: 1px solid #2BA471;
  border-radius: 3px;
  color: #2BA471;
  height: 24px;
  padding: 2px 8px;
  line-height: 20px;
}

:deep(.el-tag--primary) {
  background: #E8F3FF;
  border: 1px solid #3370FF;
  border-radius: 3px;
  color: #3370FF;
  height: 24px;
  padding: 2px 8px;
  line-height: 20px;
}

:deep(.el-tag--warning) {
  background: #FFF1E9;
  border: 1px solid #E37318;
  border-radius: 3px;
  color: #E37318;
  height: 24px;
  padding: 2px 8px;
  line-height: 20px;
}

:deep(.el-tag--danger) {
  background: #FFF0ED;
  border: 1px solid #D54941;
  border-radius: 3px;
  color: #D54941;
  height: 24px;
  padding: 2px 8px;
  line-height: 20px;
}

:deep(.el-tag--info) {
  background: #F4F4F5;
  border: 1px solid #909399;
  border-radius: 3px;
  color: #909399;
  height: 24px;
  padding: 2px 8px;
  line-height: 20px;
}
</style>
