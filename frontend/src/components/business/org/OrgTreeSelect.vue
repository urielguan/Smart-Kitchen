<script setup lang="ts">
import { computed, ref, onMounted, onActivated, watch, nextTick } from 'vue'
import { useOrgStore } from '@/stores/modules/org'

interface Props {
  modelValue: number | null
  placeholder?: string
  disabled?: boolean
  /** 仅显示启用组织 */
  activeOnly?: boolean
  /** 按组织类型过滤 */
  orgType?: string
  /** 排除的节点 ID（通常用于编辑时排除自身及子孙节点） */
  excludeId?: number | null
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: null,
  placeholder: '请选择',
  disabled: false,
  activeOnly: false,
  orgType: undefined,
  excludeId: null
})

const emit = defineEmits<{
  'update:modelValue': [value: number | null]
}>()

const orgStore = useOrgStore()

/** 选择器引用 */
const selectRef = ref()

/** 记录已展开的节点 ID */
const expandedKeys = ref<number[]>([])

/** 选中的值 */
const value = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

/** 组件挂载时，如果组织树为空则自动加载 */
onMounted(() => {
  if (orgStore.allTreeData.length === 0) {
    orgStore.fetchAllTree()
  }
})

/** 组件被 keep-alive 激活时同步最新组织树，避免修改组织后下拉选项滞后 */
onActivated(() => {
  orgStore.fetchAllTree()
})

/** 首次加载组织树时，默认展开前两级 */
watch(() => orgStore.allTreeData, (data) => {
  if (data.length > 0 && expandedKeys.value.length === 0) {
    const keys: number[] = []
    const collectKeys = (nodes: any[], level: number) => {
      if (level < 2) {
        for (const node of nodes) {
          keys.push(node.id)
          if (node.children?.length) {
            collectKeys(node.children, level + 1)
          }
        }
      }
    }
    collectKeys(data, 0)
    expandedKeys.value = keys
  }
}, { immediate: true })

/** 展开/折叠节点时同步记录 */
const handleNodeExpand = (node: any) => {
  if (!expandedKeys.value.includes(node.id)) {
    expandedKeys.value = [...expandedKeys.value, node.id]
  }
}

const handleNodeCollapse = (node: any) => {
  expandedKeys.value = expandedKeys.value.filter(id => id !== node.id)
}

/** 组织树数据（可选仅保留启用状态） */
const baseTreeData = computed(() => {
  const treeData = props.activeOnly
    ? filterActiveTree(orgStore.allTreeData)
    : orgStore.allTreeData

  const typedTree = props.orgType
    ? filterTreeByOrgType(treeData, props.orgType)
    : treeData

  // 编辑态下如果当前值被过滤掉，也要保留一个只读选项用于正确展示名称，避免仅显示 ID
  if (props.modelValue && !isOrgInTree(typedTree, props.modelValue)) {
    const currentNode = findOrgById(orgStore.allTreeData, props.modelValue)
    if (currentNode) {
      return [
        ...typedTree,
        {
          id: currentNode.id,
          orgName: `${currentNode.orgName}${currentNode.status !== 'active' ? '（已停用）' : '（当前已选）'}`,
          orgCode: currentNode.orgCode,
          orgType: currentNode.orgType,
          status: currentNode.status,
          disabled: true
        }
      ]
    }
  }

  return typedTree
})

/** 过滤组织树，排除指定节点及其子孙节点 */
const filteredTreeData = computed(() => {
  if (!props.excludeId) {
    return baseTreeData.value
  }
  return filterTree(baseTreeData.value, props.excludeId)
})

/** 递归过滤，仅保留启用组织 */
function filterActiveTree(nodes: any[]): any[] {
  return nodes
    .filter(node => node.status === 'active')
    .map(node => ({
      ...node,
      children: node.children ? filterActiveTree(node.children) : undefined
    }))
    .filter(node => {
      if (node.children && node.children.length === 0) {
        delete node.children
      }
      return true
    })
}

/** 递归过滤，仅保留指定组织类型 */
function filterTreeByOrgType(nodes: any[], orgType: string): any[] {
  return nodes.reduce<any[]>((result, node) => {
    const filteredChildren = node.children ? filterTreeByOrgType(node.children, orgType) : undefined
    const matchesCurrent = node.orgType === orgType

    if (!matchesCurrent && (!filteredChildren || filteredChildren.length === 0)) {
      return result
    }

    result.push({
      ...node,
      disabled: !matchesCurrent || node.disabled,
      children: filteredChildren && filteredChildren.length > 0 ? filteredChildren : undefined
    })

    return result
  }, [])
}

/** 递归过滤树节点 */
function filterTree(nodes: any[], excludeId: number): any[] {
  return nodes
    .filter(node => node.id !== excludeId)
    .map(node => ({
      ...node,
      children: node.children ? filterTree(node.children, excludeId) : undefined
    }))
    .filter(node => {
      // 如果过滤后 children 为空数组，移除 children 属性
      if (node.children && node.children.length === 0) {
        delete node.children
      }
      return true
    })
}

/** 判断树中是否包含指定组织 */
function isOrgInTree(nodes: any[], targetId: number): boolean {
  for (const node of nodes) {
    if (node.id === targetId) {
      return true
    }
    if (node.children?.length && isOrgInTree(node.children, targetId)) {
      return true
    }
  }
  return false
}

/** 在树中查找指定组织 */
function findOrgById(nodes: any[], targetId: number): any | null {
  for (const node of nodes) {
    if (node.id === targetId) {
      return node
    }
    if (node.children?.length) {
      const found = findOrgById(node.children, targetId)
      if (found) {
        return found
      }
    }
  }
  return null
}

/** 过滤节点 */
const filterNode = (value: string, data: any): boolean => {
  if (!value) return true
  return data.orgName.includes(value) || data.orgCode.includes(value)
}
</script>

<template>
  <el-tree-select
    ref="selectRef"
    v-model="value"
    :data="filteredTreeData"
    :props="{
      label: 'orgName',
      value: 'id',
      children: 'children'
    }"
    :placeholder="placeholder"
    :disabled="disabled"
    :filterable="true"
    :filter-method="filterNode"
    no-data-text="暂无组织数据"
    no-match-text="无匹配组织"
    loading-text="组织数据加载中"
    clearable
    check-strictly
    :render-after-expand="false"
    node-key="id"
    :expanded-keys="expandedKeys"
    @node-expand="handleNodeExpand"
    @node-collapse="handleNodeCollapse"
    style="width: 100%"
  />
</template>
