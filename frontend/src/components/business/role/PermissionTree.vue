<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRoleStore } from '@/stores/modules/role'

type TreeKey = string | number

type PermissionTreeNode = Omit<PermissionNode, 'id' | 'children'> & {
  id: TreeKey
  children?: PermissionTreeNode[]
}

const props = defineProps<{
  modelValue: string[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string[]): void
}>()

const roleStore = useRoleStore()
const treeRef = ref<any>()
const previewTreeRef = ref<any>()
const expandAll = ref(true)
const skipNextModelSync = ref(false)
const syncingTreeChange = ref(false)
const autoCheckedMenuIds = ref(new Set<TreeKey>())

const rawTreeData = computed(() => roleStore.permissionTree as PermissionTreeNode[])
const treeData = computed<PermissionTreeNode[]>(() => rawTreeData.value)


const treeProps = {
  children: 'children',
  label: 'permissionName'
}

const nodeById = computed(() => {
  const map = new Map<TreeKey, PermissionTreeNode>()
  const walk = (nodes: PermissionTreeNode[]) => {
    nodes.forEach((node) => {
      map.set(node.id, node)
      if (node.children?.length) {
        walk(node.children)
      }
    })
  }
  walk(treeData.value)
  return map
})

const parentById = computed(() => {
  const map = new Map<TreeKey, TreeKey>()
  const walk = (nodes: PermissionTreeNode[], parentId?: TreeKey) => {
    nodes.forEach((node) => {
      if (typeof parentId !== 'undefined') {
        map.set(node.id, parentId)
      }
      if (node.children?.length) {
        walk(node.children, node.id)
      }
    })
  }
  walk(treeData.value)
  return map
})

const codeIdMap = computed(() => {
  const map = new Map<string, TreeKey>()
  nodeById.value.forEach((node, id) => {
    if (node.permissionCode && node.permissionType !== 'module') {
      map.set(node.permissionCode, id)
    }
  })
  return map
})

const idCodeMap = computed(() => {
  const map = new Map<TreeKey, string>()
  nodeById.value.forEach((node, id) => {
    if (node.permissionCode && node.permissionType !== 'module') {
      map.set(id, node.permissionCode)
    }
  })
  return map
})

const allIds = computed(() => Array.from(idCodeMap.value.keys()))

const moduleIds = computed(() => {
  const ids: TreeKey[] = []
  nodeById.value.forEach((node, id) => {
    if (node.permissionType === 'module') {
      ids.push(id)
    }
  })
  return ids
})

const selectedCodes = computed(() => Array.from(new Set(props.modelValue)))

const selectedLabelMap = computed(() => {
  const map = new Map<string, string>()
  nodeById.value.forEach((node) => {
    if (node.permissionCode && node.permissionType !== 'module') {
      map.set(node.permissionCode, node.permissionName)
    }
  })

  // Keep labels readable even when some granted permissions are outside current assignable tree.
  const detailNameMap = roleStore.currentRole?.funcPermissionNameMap || {}
  Object.entries(detailNameMap).forEach(([code, name]) => {
    if (code && name && !map.has(code)) {
      map.set(code, name)
    }
  })

  return map
})

const getParentMenuId = (id: TreeKey): TreeKey | null => {
  let currentId = parentById.value.get(id)
  while (typeof currentId !== 'undefined') {
    const parentNode = nodeById.value.get(currentId)
    if (parentNode?.permissionType === 'menu') {
      return currentId
    }
    currentId = parentById.value.get(currentId)
  }
  return null
}

const getDescendantPermissionIds = (id: TreeKey) => {
  const result: TreeKey[] = []
  const walk = (currentId: TreeKey) => {
    const node = nodeById.value.get(currentId)
    if (!node?.children?.length) {
      return
    }
    node.children.forEach((child) => {
      if (child.permissionType !== 'module') {
        result.push(child.id)
      }
      walk(child.id)
    })
  }
  walk(id)
  return result
}

const applyCheckedIds = (ids: TreeKey[]) => {
  if (!treeRef.value) {
    return
  }

  treeRef.value.setCheckedKeys([], false)
  ids.forEach((id) => {
    treeRef.value.setChecked(id, true, false)
  })
}

const syncModuleVisualState = () => {
  if (!treeRef.value) {
    return
  }

  const checkedSet = new Set<TreeKey>(treeRef.value.getCheckedKeys(false) as TreeKey[])

  moduleIds.value.forEach((moduleId) => {
    const descendantIds = getDescendantPermissionIds(moduleId)
    const total = descendantIds.length
    const checkedCount = descendantIds.filter(id => checkedSet.has(id)).length

    const moduleNode = treeRef.value.getNode(moduleId)
    if (!moduleNode) {
      return
    }

    if (total > 0 && checkedCount === total) {
      treeRef.value.setChecked(moduleId, true, false)
      moduleNode.indeterminate = false
      return
    }

    treeRef.value.setChecked(moduleId, false, false)
    moduleNode.indeterminate = checkedCount > 0
  })
}

const getDisplayCheckedIds = (selectedIdSet: Set<TreeKey>) => {
  const display = new Set<TreeKey>()

  selectedIdSet.forEach((id) => {
    const node = nodeById.value.get(id)
    if (!node || node.permissionType === 'module') {
      return
    }
    display.add(id)
  })

  return Array.from(display)
}

const syncCheckedFromModel = () => {
  if (!treeRef.value) {
    return
  }

  const selectedIdSet = new Set<TreeKey>()
  props.modelValue.forEach((code) => {
    const id = codeIdMap.value.get(code)
    if (typeof id !== 'undefined') {
      selectedIdSet.add(id)
    }
  })

  autoCheckedMenuIds.value = new Set<TreeKey>()
  const displayIds = getDisplayCheckedIds(selectedIdSet)
  applyCheckedIds(displayIds)
  syncModuleVisualState()
  syncPreviewTree()
}

const syncPreviewTree = () => {
  if (!previewTreeRef.value) return
  const checkedKeys = treeRef.value?.getCheckedKeys(false) as TreeKey[] || []
  previewTreeRef.value.setCheckedKeys([], false)
  checkedKeys.forEach((id: TreeKey) => {
    previewTreeRef.value.setChecked(id, true, false)
  })
  // 同步 module 半选状态
  const checkedSet = new Set<TreeKey>(checkedKeys)
  moduleIds.value.forEach((moduleId) => {
    const descendantIds = getDescendantPermissionIds(moduleId)
    const total = descendantIds.length
    const checkedCount = descendantIds.filter(id => checkedSet.has(id)).length
    const moduleNode = previewTreeRef.value.getNode(moduleId)
    if (!moduleNode) return
    previewTreeRef.value.setChecked(moduleId, false, false)
    moduleNode.indeterminate = checkedCount > 0 && checkedCount < total
    if (checkedCount === total && total > 0) {
      previewTreeRef.value.setChecked(moduleId, true, false)
      moduleNode.indeterminate = false
    }
  })
}

watch(() => props.modelValue, () => {
  if (skipNextModelSync.value) {
    skipNextModelSync.value = false
    return
  }
  nextTick(syncCheckedFromModel)
}, { immediate: true })

watch(treeData, () => {
  nextTick(syncCheckedFromModel)
}, { deep: true })

const emitByTreeState = () => {
  if (!treeRef.value) {
    return
  }

  const checkedKeys = treeRef.value.getCheckedKeys(false) as TreeKey[]
  const selectedIds = new Set<TreeKey>(checkedKeys)

  const buttonIds = new Set<TreeKey>()
  const menuIds = new Set<TreeKey>()

  selectedIds.forEach((id) => {
    const node = nodeById.value.get(id)
    if (!node) {
      return
    }

    if (node.permissionType === 'button') {
      buttonIds.add(id)
      const parentMenuId = getParentMenuId(id)
      if (parentMenuId !== null) {
        menuIds.add(parentMenuId)
      }
      return
    }

    if (node.permissionType === 'menu') {
      menuIds.add(id)
    }
  })

  const finalIds = Array.from(new Set<TreeKey>([...menuIds, ...buttonIds]))
  const normalizedCodes = Array.from(new Set(finalIds
    .map(id => idCodeMap.value.get(id))
    .filter((code): code is string => !!code)))

  const currentCodes = Array.from(new Set(props.modelValue))
  const unchanged = normalizedCodes.length === currentCodes.length
    && normalizedCodes.every(code => currentCodes.includes(code))

  // When normalization adds parent menus for selected buttons, keep model->tree sync enabled.
  skipNextModelSync.value = unchanged
  emit('update:modelValue', normalizedCodes)
}

const handleTreeCheck = (checkedNode: PermissionTreeNode, checkedInfo: { checkedKeys: TreeKey[] }) => {
  if (!treeRef.value || syncingTreeChange.value) {
    return
  }

  const selectedSet = new Set<TreeKey>(checkedInfo.checkedKeys || [])
  const isChecked = selectedSet.has(checkedNode.id)

  if (checkedNode.permissionType === 'module') {
    syncingTreeChange.value = true
    const childIds = getDescendantPermissionIds(checkedNode.id)
    childIds.forEach((childId) => {
      treeRef.value.setChecked(childId, isChecked, false)
    })
    syncingTreeChange.value = false
  }

  if (checkedNode.permissionType === 'menu') {
    syncingTreeChange.value = true
    const childIds = getDescendantPermissionIds(checkedNode.id)
    childIds.forEach((childId) => {
      treeRef.value.setChecked(childId, isChecked, false)
    })
    autoCheckedMenuIds.value.delete(checkedNode.id)
    syncingTreeChange.value = false
  }

  if (checkedNode.permissionType === 'button') {
    // 自动选中/取消选中子项（将嵌套按钮作为父项处理，类似于alert:list）
    if (checkedNode.children?.length) {
      syncingTreeChange.value = true
      const childIds = getDescendantPermissionIds(checkedNode.id)
      childIds.forEach((childId) => {
        treeRef.value.setChecked(childId, isChecked, false)
      })
      syncingTreeChange.value = false
    }

    // 检查子按钮时自动检查父按钮祖先，自动勾选（例如alert:dispatch->alert:list）
    if (isChecked) {
      let ancestorId = parentById.value.get(checkedNode.id)
      while (typeof ancestorId !== 'undefined') {
        const ancestorNode = nodeById.value.get(ancestorId)
        if (ancestorNode?.permissionType === 'button' && !selectedSet.has(ancestorId)) {
          syncingTreeChange.value = true
          treeRef.value.setChecked(ancestorId, true, false)
          syncingTreeChange.value = false
        }
        ancestorId = parentById.value.get(ancestorId)
      }
    }

    const parentMenuId = getParentMenuId(checkedNode.id)
    if (parentMenuId !== null) {
      if (isChecked && !selectedSet.has(parentMenuId)) {
        syncingTreeChange.value = true
        autoCheckedMenuIds.value.add(parentMenuId)
        treeRef.value.setChecked(parentMenuId, true, false)
        syncingTreeChange.value = false
      }

      if (!isChecked && autoCheckedMenuIds.value.has(parentMenuId)) {
        const latestCheckedKeys = treeRef.value.getCheckedKeys(false) as TreeKey[]
        const latestSet = new Set<TreeKey>(latestCheckedKeys)
        const siblingButtonIds = getDescendantPermissionIds(parentMenuId).filter((id) => {
          const node = nodeById.value.get(id)
          return node?.permissionType === 'button'
        })
        const hasAnyCheckedButton = siblingButtonIds.some(id => latestSet.has(id))

        if (!hasAnyCheckedButton) {
          syncingTreeChange.value = true
          treeRef.value.setChecked(parentMenuId, false, false)
          autoCheckedMenuIds.value.delete(parentMenuId)
          syncingTreeChange.value = false
        }
      }
    }
  }

  syncModuleVisualState()
  emitByTreeState()
}

const isAllSelected = computed(() => {
  if (!allIds.value.length) {
    return false
  }
  const selectedIds = new Set<TreeKey>()
  props.modelValue.forEach((code) => {
    const id = codeIdMap.value.get(code)
    if (typeof id !== 'undefined') {
      selectedIds.add(id)
    }
  })
  return allIds.value.every(id => selectedIds.has(id))
})

const handleToggleSelect = () => {
  if (!treeRef.value) {
    return
  }
  treeRef.value.setCheckedKeys(isAllSelected.value ? [] : allIds.value)
  emitByTreeState()
}

const handleToggleExpand = () => {
  expandAll.value = !expandAll.value
  const store: any = treeRef.value?.store
  store?._getAllNodes?.().forEach((node: any) => {
    node.expanded = expandAll.value
  })
  const previewStore: any = previewTreeRef.value?.store
  previewStore?._getAllNodes?.().forEach((node: any) => {
    node.expanded = expandAll.value
  })
}

/** 阻止右侧预览树勾选变更，立即恢复 */
const handlePreviewCheck = () => {
  nextTick(syncPreviewTree)
}
</script>

<template>
  <div class="permission-panel">
    <div class="permission-panel__tools">
      <el-button class="btn-tool" @click="handleToggleSelect">
        {{ isAllSelected ? '全不选' : '全选' }}
      </el-button>
      <el-button class="btn-tool" @click="handleToggleExpand">
        {{ expandAll ? '折叠' : '展开' }}
      </el-button>
    </div>

    <div class="permission-panel__body">
      <div class="tree-box">
        <div class="box-header">功能权限（{{ allIds.length }}）</div>
        <div class="box-divider"></div>
        <el-tree
          ref="treeRef"
          node-key="id"
          show-checkbox
          :props="treeProps"
          :data="treeData"
          :check-strictly="true"
          :default-expand-all="expandAll"
          @check="handleTreeCheck"
        />
      </div>

      <div class="selected-box">
        <div class="box-header">已授权（{{ selectedCodes.length }}）</div>
        <div class="box-divider"></div>
        <el-tree
          ref="previewTreeRef"
          node-key="id"
          show-checkbox
          :props="treeProps"
          :data="treeData"
          :check-strictly="true"
          :default-expand-all="expandAll"
          class="preview-tree"
          @check="handlePreviewCheck"
        />
      </div>
    </div>
  </div>
</template>

<style lang="scss">
/* ---- Checkbox 颜色（unscoped） ---- */
.role-form-dialog .el-checkbox__input.is-checked .el-checkbox__inner {
  background-color: #7288FA;
  border-color: #7288FA;
}

.role-form-dialog .el-checkbox__input.is-indeterminate .el-checkbox__inner {
  background-color: #7288FA;
  border-color: #7288FA;
}

.role-form-dialog .el-checkbox__input.is-focus .el-checkbox__inner {
  border-color: #7288FA;
}
</style>

<style lang="scss" scoped>
.permission-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;

  &__tools {
    display: flex;
    align-items: center;
    gap: 0px;
    flex-wrap: wrap;
  }

  &__body {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 10px;
    min-height: 320px;
  }
}

.btn-tool {
  height: 32px;
  padding: 5px 16px;
  background: #FFFFFF;
  border: 1px solid #BEC0CA;
  border-radius: 6px;
  box-shadow: 0px 2px 0px rgba(0, 0, 0, 0.016);
  color: #606266;
  font-size: 13px;

  &:hover,
  &:focus {
    background: #F5F7FA;
    border-color: #7288FA;
    color: #7288FA;
  }
}

.tree-box,
.selected-box {
  border: 1px solid $border-light;
  border-radius: 6px;
  overflow: auto;
}

.box-header {
  font-weight: 500;
  font-size: 14px;
  color: #303133;
  padding: 10px 12px;
  margin-bottom: 0;
}

.box-divider {
  height: 1px;
  background: #E1E2E9;
  margin: 0 0 8px;
}

.tree-box .el-tree,
.selected-box .el-tree {
  padding: 0 12px 10px;
}

/* ---- 右侧预览树：不可交互 ---- */
.preview-tree {
  :deep(.el-tree-node__content) {
    cursor: default;
  }

  :deep(.el-checkbox__inner) {
    cursor: default;
    pointer-events: none;
  }

  :deep(.el-checkbox__input.is-checked .el-checkbox__inner) {
    background-color: #C0C4CC;
    border-color: #C0C4CC;
  }

  :deep(.el-tree-node__content:has(.is-checked) .el-tree-node__label) {
    color: rgba(0, 0, 0, 0.26);
  }

  :deep(.el-checkbox__input.is-indeterminate .el-checkbox__inner) {
    background-color: #C0C4CC;
    border-color: #C0C4CC;
  }
}
</style>
