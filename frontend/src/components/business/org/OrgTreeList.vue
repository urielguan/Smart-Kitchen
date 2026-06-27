<script setup lang="ts">
import { computed, provide } from 'vue'
import type { OrgTreeNode } from '@/types/org'
import OrgTreeNodeComponent from './OrgTreeNode.vue'

interface Props {
  data: OrgTreeNode[]
  loading?: boolean
  expandedKeys: Set<number>
}

const props = withDefaults(defineProps<Props>(), {
  loading: false
})

const emit = defineEmits<{
  toggle: [id: number]
  'add-child': [node: OrgTreeNode]
  'view-members': [node: OrgTreeNode]
  edit: [node: OrgTreeNode]
  delete: [node: OrgTreeNode]
  'update-status': [node: OrgTreeNode, status: 'active' | 'inactive']
}>()

/** 将可见树扁平化，生成 nodeId -> 全局行索引 的 Map */
const rowIndexMap = computed(() => {
  const map = new Map<number, number>()
  let index = 0
  const walk = (nodes: OrgTreeNode[]) => {
    for (const node of nodes) {
      map.set(node.id, index++)
      if (node.children?.length && props.expandedKeys.has(node.id)) {
        walk(node.children)
      }
    }
  }
  walk(props.data)
  return map
})

provide('orgRowIndexMap', rowIndexMap)

/** 事件转发 */
const handleToggle = (id: number) => emit('toggle', id)
const handleAddChild = (node: OrgTreeNode) => emit('add-child', node)
const handleViewMembers = (node: OrgTreeNode) => emit('view-members', node)
const handleEdit = (node: OrgTreeNode) => emit('edit', node)
const handleDelete = (node: OrgTreeNode) => emit('delete', node)
const handleUpdateStatus = (node: OrgTreeNode, status: 'active' | 'inactive') => emit('update-status', node, status)
</script>

<template>
  <div class="org-tree-list" v-loading="loading">
    <!-- 空状态 -->
    <div v-if="!loading && data.length === 0" class="org-tree-empty">
      暂无组织数据，点击「新增组织」创建
    </div>

    <!-- 树节点列表 -->
    <template v-else>
      <OrgTreeNodeComponent
        v-for="node in data"
        :key="node.id"
        :node="node"
        :level="0"
        :expanded-keys="expandedKeys"
        @toggle="handleToggle"
        @add-child="handleAddChild"
        @view-members="handleViewMembers"
        @edit="handleEdit"
        @delete="handleDelete"
        @update-status="handleUpdateStatus"
      />
    </template>
  </div>
</template>

<style lang="scss" scoped>
.org-tree-list {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 0 16px;
}

.org-tree-empty {
  text-align: center;
  color: $text-secondary;
  padding: 60px 20px;
  font-size: 14px;
}
</style>
