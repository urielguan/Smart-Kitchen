<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { DATA_SCOPE_OPTIONS } from '@/constants/role'
import { useRoleStore } from '@/stores/modules/role'
import type { DataScope, OrgTreeNode } from '@/types/role'

const props = defineProps<{
  modelValue: DataScope
  orgIds: number[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: DataScope): void
  (e: 'update:orgIds', value: number[]): void
}>()

const roleStore = useRoleStore()
const treeRef = ref<any>()
const previewTreeRef = ref<any>()
const checkStrictly = ref(true)
const expandAll = ref(true)
const dataScope = ref<DataScope>(props.modelValue)
const skipNextOrgSync = ref(false)

const linkageEnabled = computed({
  get: () => !checkStrictly.value,
  set: (val: boolean) => {
    checkStrictly.value = !val
  }
})

const treeData = computed<OrgTreeNode[]>(() => roleStore.orgTree)
const treeDataAll = computed<OrgTreeNode[]>(() => roleStore.orgTreeAll)

const treeDisplayData = computed<OrgTreeNode[]>(() => {
  const decorate = (nodes: OrgTreeNode[]): OrgTreeNode[] => {
    return nodes.map((node) => {
      const inactive = !!(node.status && node.status !== 'active')
      return {
        ...node,
        orgName: inactive ? `${node.orgName}（已停用）` : node.orgName,
        children: node.children?.length ? decorate(node.children) : node.children
      }
    })
  }
  return decorate(treeData.value)
})

const treeProps = {
  children: 'children',
  label: 'orgName'
}

const flattenIds = (nodes: OrgTreeNode[]): number[] => {
  const result: number[] = []
  const walk = (items: OrgTreeNode[]) => {
    items.forEach(item => {
      result.push(item.id)
      if (item.children?.length) {
        walk(item.children)
      }
    })
  }
  walk(nodes)
  return result
}

const buildNodeMap = (nodes: OrgTreeNode[]) => {
  const map = new Map<number, OrgTreeNode>()
  const walk = (items: OrgTreeNode[]) => {
    items.forEach((node) => {
      map.set(node.id, node)
      if (node.children?.length) {
        walk(node.children)
      }
    })
  }
  walk(nodes)
  return map
}

const allIds = computed(() => flattenIds(treeData.value))

const nodeById = computed(() => buildNodeMap(treeData.value))
const nodeByIdAll = computed(() => buildNodeMap(treeDataAll.value))

const selectedOrgIds = computed(() => props.orgIds || [])

const isAllSelected = computed(() => {
  if (!allIds.value.length) {
    return false
  }
  const selectedSet = new Set(selectedOrgIds.value)
  return allIds.value.every(id => selectedSet.has(id))
})

const getDisplayCheckedIds = (selectedSet: Set<number>) => {
  const display = new Set<number>()

  selectedSet.forEach((id) => {
    const node = nodeById.value.get(id)
    if (!node) {
      return
    }

    const childIds = (node.children || []).map(child => child.id)
    if (!childIds.length || checkStrictly.value) {
      display.add(id)
      return
    }

    const selectedChildCount = childIds.filter(childId => selectedSet.has(childId)).length
    if (selectedChildCount === 0 || selectedChildCount === childIds.length) {
      display.add(id)
      return
    }

    childIds.forEach((childId) => {
      if (selectedSet.has(childId)) {
        display.add(childId)
      }
    })
  })

  return Array.from(display)
}

const applyCheckedIds = (ids: number[]) => {
  if (!treeRef.value) {
    return
  }
  treeRef.value.setCheckedKeys([], false)
  ids.forEach((id) => {
    treeRef.value.setChecked(id, true, false)
  })
}

const syncCheckedFromModel = () => {
  if (!treeRef.value) {
    return
  }
  const selectedSet = new Set(props.orgIds || [])
  const displayIds = getDisplayCheckedIds(selectedSet)
  applyCheckedIds(displayIds)
  syncPreviewTree()
}

const syncPreviewTree = () => {
  if (!previewTreeRef.value || !treeRef.value) return
  const checkedKeys = treeRef.value.getCheckedKeys(false) as number[]
  previewTreeRef.value.setCheckedKeys([], false)
  checkedKeys.forEach((id: number) => {
    previewTreeRef.value.setChecked(id, true, false)
  })
}

watch(() => props.modelValue, (val) => {
  dataScope.value = val
}, { immediate: true })

watch(() => props.orgIds, () => {
  if (skipNextOrgSync.value) {
    skipNextOrgSync.value = false
    return
  }
  nextTick(syncCheckedFromModel)
}, { immediate: true, deep: true })

watch(treeData, () => {
  nextTick(syncCheckedFromModel)
}, { deep: true })

const handleScopeChange = (val: DataScope) => {
  dataScope.value = val
  emit('update:modelValue', val)
  if (val !== 'custom') {
    emit('update:orgIds', [])
  }
}

const emitTreeSelection = () => {
  if (!treeRef.value) {
    return
  }

  const checked = treeRef.value.getCheckedKeys(false) as number[]
  const nextSelected = checkStrictly.value
    ? checked
    : Array.from(new Set([...checked, ...(treeRef.value.getHalfCheckedKeys() as number[])]))

  skipNextOrgSync.value = true
  emit('update:orgIds', nextSelected)
}

const handleTreeCheck = () => {
  emitTreeSelection()
  nextTick(syncPreviewTree)
}

const handleToggleSelect = () => {
  if (!treeRef.value) return
  treeRef.value.setCheckedKeys(isAllSelected.value ? [] : allIds.value)
  emitTreeSelection()
  nextTick(syncPreviewTree)
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

const handleCheckStrictlyChange = () => {
  nextTick(syncCheckedFromModel)
}

const handlePreviewCheck = () => {
  nextTick(syncPreviewTree)
}
</script>

<template>
  <div class="data-scope-panel">
    <div class="scope-options">
      <el-select v-model="dataScope" placeholder="请选择数据权限范围" style="width: 320px" @change="handleScopeChange">
        <el-option
          v-for="option in DATA_SCOPE_OPTIONS"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>
    </div>

    <div v-if="dataScope === 'custom'" class="custom-scope">
      <div class="custom-tools">
        <el-button class="btn-tool" @click="handleToggleSelect">
          {{ isAllSelected ? '全不选' : '全选' }}
        </el-button>
        <el-button class="btn-tool" @click="handleToggleExpand">
          {{ expandAll ? '折叠' : '展开' }}
        </el-button>
        <el-switch
          v-model="linkageEnabled"
          inline-prompt
          active-text="父子联动"
          inactive-text="关闭联动"
          @change="handleCheckStrictlyChange"
        />
      </div>

      <div class="custom-body">
        <div class="tree-box">
          <div class="box-header">组织权限（{{ allIds.length }}）</div>
          <div class="box-divider"></div>
          <el-tree
            ref="treeRef"
            node-key="id"
            show-checkbox
            :props="treeProps"
            :data="treeDisplayData"
            :check-strictly="checkStrictly"
            :default-expand-all="expandAll"
            @check="handleTreeCheck"
          />
        </div>

        <div class="selected-box">
          <div class="box-header">已授权（{{ selectedOrgIds.length }}）</div>
          <div class="box-divider"></div>
          <el-tree
            ref="previewTreeRef"
            node-key="id"
            show-checkbox
            :props="treeProps"
            :data="treeDisplayData"
            :check-strictly="true"
            :default-expand-all="expandAll"
            class="preview-tree"
            @check="handlePreviewCheck"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss">
/* ---- Checkbox 颜色（unscoped） ---- */
.role-form-dialog .data-scope-panel .el-checkbox__input.is-checked .el-checkbox__inner {
  background-color: #7288FA;
  border-color: #7288FA;
}

.role-form-dialog .data-scope-panel .el-checkbox__input.is-indeterminate .el-checkbox__inner {
  background-color: #7288FA;
  border-color: #7288FA;
}

.role-form-dialog .data-scope-panel .el-checkbox__input.is-focus .el-checkbox__inner {
  border-color: #7288FA;
}

/* ---- 开关颜色 ---- */
.role-form-dialog .data-scope-panel .el-switch.is-checked .el-switch__core {
  background-color: #7288FA;
  border-color: #7288FA;
}
</style>

<style lang="scss" scoped>
.data-scope-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.scope-options {
  display: flex;
  align-items: center;
}

.custom-tools {
  display: flex;
  align-items: center;
  gap: 0px;
  margin-bottom: 8px;
  flex-wrap: wrap;

  :deep(.el-switch) {
    margin-left: 10px;
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

.custom-body {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  min-height: 320px;
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

.tree-box :deep(.el-tree),
.selected-box :deep(.el-tree) {
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
