<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Close, Search } from '@element-plus/icons-vue'
import { useOrgStore } from '@/stores/modules/org'
import type { OrgTreeNode } from '@/types/org'

interface OrgOption extends OrgTreeNode {
  disabled?: boolean
  displayName?: string
}

const props = withDefaults(defineProps<{
  modelValue: boolean
  selectedOrgId?: number | null
  selectedOrgName?: string
}>(), {
  selectedOrgId: null,
  selectedOrgName: ''
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: [payload: { orgId: number; orgName: string }]
}>()

const orgStore = useOrgStore()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

const DEFAULT_PAGE_NUM = 1
const DEFAULT_PAGE_SIZE = 10

const searchKeyword = ref('')
const activeTreeId = ref<number | null>(null)
const draftSelectedOrgId = ref<number | null>(null)
const pageNum = ref(DEFAULT_PAGE_NUM)
const pageSize = ref(DEFAULT_PAGE_SIZE)

const loadAllTree = async () => {
  await orgStore.fetchAllTree()
}

const isSelectableOrg = (node: OrgTreeNode | null): node is OrgTreeNode => {
  return !!node && node.status === 'active' && node.orgType === 'canteen'
}

onMounted(() => {
  if (orgStore.allTreeData.length === 0) {
    loadAllTree()
  }
})

const filterActiveTree = (nodes: OrgTreeNode[]): OrgTreeNode[] => nodes
  .filter(node => node.status === 'active')
  .map(node => ({
    ...node,
    children: node.children ? filterActiveTree(node.children) : undefined
  }))

const filterTreeByOrgType = (nodes: OrgTreeNode[], orgType: string): OrgOption[] => nodes.reduce<OrgOption[]>((result, node) => {
  const filteredChildren = node.children ? filterTreeByOrgType(node.children, orgType) : undefined
  const matchesCurrent = node.orgType === orgType

  if (!matchesCurrent && (!filteredChildren || filteredChildren.length === 0)) {
    return result
  }

  result.push({
    ...node,
    disabled: !matchesCurrent,
    children: filteredChildren && filteredChildren.length > 0 ? filteredChildren : undefined
  })

  return result
}, [])

const findOrgById = (nodes: OrgTreeNode[], targetId: number): OrgTreeNode | null => {
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

const isOrgInTree = (nodes: OrgOption[], targetId: number): boolean => {
  for (const node of nodes) {
    if (node.id === targetId) {
      return true
    }
    if (node.children?.length && isOrgInTree(node.children as OrgOption[], targetId)) {
      return true
    }
  }
  return false
}

const keywordMatches = (node: OrgTreeNode, keyword: string): boolean => {
  const normalizedKeyword = keyword.trim().toLowerCase()
  if (!normalizedKeyword) {
    return true
  }
  return node.orgName.toLowerCase().includes(normalizedKeyword)
    || node.orgCode.toLowerCase().includes(normalizedKeyword)
}

const filterTreeByKeyword = (nodes: OrgOption[], keyword: string): OrgOption[] => {
  const trimmedKeyword = keyword.trim()
  if (!trimmedKeyword) {
    return nodes
  }

  return nodes.reduce<OrgOption[]>((result, node) => {
    const filteredChildren = node.children
      ? filterTreeByKeyword(node.children as OrgOption[], trimmedKeyword)
      : undefined
    const matchesCurrent = keywordMatches(node, trimmedKeyword)

    if (!matchesCurrent && (!filteredChildren || filteredChildren.length === 0)) {
      return result
    }

    result.push({
      ...node,
      children: filteredChildren && filteredChildren.length > 0 ? filteredChildren : undefined
    })

    return result
  }, [])
}

const appendCurrentSelectionFallback = (nodes: OrgOption[]): OrgOption[] => {
  if (!props.selectedOrgId || isOrgInTree(nodes, props.selectedOrgId)) {
    return nodes
  }

  const currentNode = findOrgById(orgStore.allTreeData, props.selectedOrgId)
  if (!currentNode) {
    return nodes
  }

  return [
    {
      ...currentNode,
      disabled: true,
      displayName: `${currentNode.orgName}${currentNode.status !== 'active' ? '（已停用）' : '（当前已选）'}`
    },
    ...nodes
  ]
}

const filterNavigationTree = (nodes: OrgOption[]): OrgOption[] => nodes.reduce<OrgOption[]>((result, node) => {
  const filteredChildren = node.children ? filterNavigationTree(node.children as OrgOption[]) : undefined
  const shouldKeep = node.orgType !== 'canteen'

  if (!shouldKeep && (!filteredChildren || filteredChildren.length === 0)) {
    return result
  }

  result.push({
    ...node,
    disabled: false,
    children: filteredChildren && filteredChildren.length > 0 ? filteredChildren : undefined
  })

  return result
}, [])

const activeTreeData = computed<OrgOption[]>(() => filterActiveTree(orgStore.allTreeData) as OrgOption[])
const navigationTreeData = computed<OrgOption[]>(() => filterNavigationTree(activeTreeData.value))
const filteredNavigationTree = computed<OrgOption[]>(() => filterTreeByKeyword(navigationTreeData.value, searchKeyword.value))

const flattenTreeNodes = (nodes: OrgOption[], level = 1): Array<OrgOption & { level: number }> => {
  return nodes.flatMap(node => {
    const currentNode = {
      ...node,
      level
    }

    const children = node.children ? flattenTreeNodes(node.children as OrgOption[], level + 1) : []
    return [currentNode, ...children]
  })
}

const flattenSelectableOrgs = (nodes: OrgOption[], level = 1, parentPath: string[] = []): Array<OrgOption & { level: number; pathLabel: string }> => {
  return nodes.flatMap(node => {
    const currentPath = [...parentPath, node.displayName || node.orgName]
    const currentNode = {
      ...node,
      level,
      pathLabel: currentPath.join(' / ')
    }

    const children = node.children ? flattenSelectableOrgs(node.children as OrgOption[], level + 1, currentPath) : []
    return [currentNode, ...children]
  })
}

const navigationTreeRows = computed(() => flattenTreeNodes(filteredNavigationTree.value))

const activeNavigationNode = computed(() => {
  if (!activeTreeId.value) {
    return null
  }
  return findOrgById(activeTreeData.value, activeTreeId.value)
})

const selectableOrgRows = computed(() => {
  const scopedNodes = activeNavigationNode.value ? [activeNavigationNode.value] : filteredNavigationTree.value
  const canteenTree = filterTreeByOrgType(scopedNodes, 'canteen')
  return flattenSelectableOrgs(appendCurrentSelectionFallback(canteenTree)).filter(node => node.orgType === 'canteen')
})

const total = computed(() => selectableOrgRows.value.length)
const pagedSelectableOrgRows = computed(() => {
  const start = (pageNum.value - 1) * pageSize.value
  return selectableOrgRows.value.slice(start, start + pageSize.value)
})

const selectedOrgRow = computed(() => selectableOrgRows.value.find(item => item.id === draftSelectedOrgId.value) || null)
const currentSelectedText = computed(() => selectedOrgRow.value?.orgName || props.selectedOrgName || '未选择实施组织')

const findParentOrgPath = (nodes: OrgTreeNode[], targetId: number, parents: OrgTreeNode[] = []): OrgTreeNode[] | null => {
  for (const node of nodes) {
    if (node.id === targetId) {
      return [...parents, node]
    }
    if (node.children?.length) {
      const foundPath = findParentOrgPath(node.children, targetId, [...parents, node])
      if (foundPath) {
        return foundPath
      }
    }
  }
  return null
}

const resolveNavigationAnchorId = (selectedOrgId: number | null): number | null => {
  if (!selectedOrgId) {
    return null
  }

  const path = findParentOrgPath(orgStore.allTreeData, selectedOrgId)
  if (!path?.length) {
    return null
  }

  const nearestNonCanteen = [...path].reverse().find(node => node.orgType !== 'canteen' && node.status === 'active')
  return nearestNonCanteen?.id ?? null
}

const resetDraftSelection = () => {
  draftSelectedOrgId.value = props.selectedOrgId ?? null
  activeTreeId.value = resolveNavigationAnchorId(props.selectedOrgId ?? null)
  pageNum.value = DEFAULT_PAGE_NUM
  pageSize.value = DEFAULT_PAGE_SIZE
}

watch([searchKeyword, activeTreeId], () => {
  pageNum.value = DEFAULT_PAGE_NUM
})

watch([total, pageSize], () => {
  const maxPage = Math.max(1, Math.ceil(total.value / pageSize.value))
  if (pageNum.value > maxPage) {
    pageNum.value = maxPage
  }
})

watch(visible, (value) => {
  if (value) {
    loadAllTree()
    searchKeyword.value = ''
    resetDraftSelection()
  }
})

const handleClose = () => {
  visible.value = false
}

const handleTreeNodeClick = (node: OrgOption) => {
  activeTreeId.value = node.id
}

const handleRowClick = (row: OrgOption & { level: number; pathLabel: string }) => {
  if (row.disabled) {
    return
  }
  draftSelectedOrgId.value = row.id
}

const handlePageChange = (page: number) => {
  pageNum.value = page
  const visibleIds = new Set(pagedSelectableOrgRows.value.map(item => item.id))
  if (draftSelectedOrgId.value && !visibleIds.has(draftSelectedOrgId.value)) {
    draftSelectedOrgId.value = null
  }
}

const handleConfirm = () => {
  const selectedId = draftSelectedOrgId.value
  if (!selectedId) {
    return
  }
  const matched = findOrgById(orgStore.allTreeData, selectedId)
  if (!isSelectableOrg(matched)) {
    return
  }
  emit('confirm', {
    orgId: matched.id,
    orgName: matched.orgName
  })
  visible.value = false
}
</script>

<template>
  <el-dialog
    v-model="visible"
    width="1100px"
    destroy-on-close
    :close-on-click-modal="false"
    :show-close="false"
    class="plan-org-select-dialog"
  >
    <template #header>
      <div class="plan-org-dialog__header">
        <div class="plan-org-dialog__header-content">
          <div class="plan-org-dialog__title">实施组织</div>
          <button class="plan-org-dialog__close" type="button" aria-label="关闭" @click="handleClose">
            <el-icon class="plan-org-dialog__close-icon"><Close /></el-icon>
          </button>
        </div>
      </div>
    </template>

    <div class="plan-org-dialog__body">
      <aside class="plan-org-dialog__sidebar">
        <div class="plan-org-dialog__search">
          <div class="plan-org-dialog__search-content">
            <input
              v-model="searchKeyword"
              class="plan-org-dialog__search-input"
              type="text"
              placeholder="搜索组织名称/编码"
            >
          </div>
          <div class="plan-org-dialog__search-icon">
            <el-icon><Search /></el-icon>
          </div>
        </div>

        <div class="plan-org-dialog__sidebar-divider"></div>

        <div class="plan-org-dialog__tree">
          <div
            v-for="node in navigationTreeRows"
            :key="node.id"
            class="plan-org-dialog__tree-item"
            :class="{
              'is-active': node.id === activeTreeId,
              'is-current': node.id === draftSelectedOrgId
            }"
            :style="{ paddingLeft: `${8 + (node.level - 1) * 20}px` }"
            @click="handleTreeNodeClick(node)"
          >
            <span class="plan-org-dialog__tree-caret">{{ node.children?.length ? '▸' : '' }}</span>
            <span class="plan-org-dialog__tree-label">{{ node.displayName || node.orgName }}</span>
          </div>
        </div>
      </aside>

      <section class="plan-org-dialog__content">
        <div class="plan-org-dialog__table-shell">
          <table class="plan-org-dialog__table">
            <thead>
              <tr>
                <th class="col-check">
                  <div class="plan-org-dialog__check-cell plan-org-dialog__check-cell--header">序号</div>
                </th>
                <th class="col-org-code">组织编码</th>
                <th class="col-org-name">组织名称</th>
                <th class="col-long-name">长名称</th>
                <th>组织类型</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="(row, index) in pagedSelectableOrgRows"
                :key="row.id"
                :class="{
                  'is-current': row.id === draftSelectedOrgId,
                  'is-disabled': row.disabled
                }"
                @click="handleRowClick(row)"
              >
                <td class="col-check">
                  <div class="plan-org-dialog__check-cell">
                    <span class="plan-org-dialog__checkbox" :class="{ 'is-checked': row.id === draftSelectedOrgId }"></span>
                    <span class="plan-org-dialog__index">{{ (pageNum - 1) * pageSize + index + 1 }}</span>
                  </div>
                </td>
                <td class="col-org-code">{{ row.orgCode }}</td>
                <td class="col-org-name">{{ row.orgName }}</td>
                <td class="col-long-name">{{ row.pathLabel }}</td>
                <td>食堂</td>
              </tr>
              <tr v-if="pagedSelectableOrgRows.length === 0">
                <td colspan="5" class="plan-org-dialog__empty">当前路径下暂无可选实施组织</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-if="total > 0" class="plan-org-dialog__table-footer">
          <div class="plan-org-dialog__table-total">共 {{ total }} 条</div>
          <el-pagination
            v-model:current-page="pageNum"
            v-model:page-size="pageSize"
            class="plan-org-dialog__pagination"
            :total="total"
            :page-sizes="[10, 20, 50]"
            layout="sizes, prev, pager, next"
            @current-change="handlePageChange"
          />
        </div>
      </section>
    </div>

    <template #footer>
      <div class="plan-org-dialog__footer">
        <button class="plan-org-dialog__btn plan-org-dialog__btn--secondary" type="button" @click="handleClose">取消</button>
        <button
          class="plan-org-dialog__btn plan-org-dialog__btn--primary"
          type="button"
          :disabled="!draftSelectedOrgId"
          @click="handleConfirm"
        >保存</button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.plan-org-dialog__header {
  padding: 24px 24px 16px;
  border-bottom: none;
  box-shadow: none;
}

.plan-org-dialog__header-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  height: 32px;
}

.plan-org-dialog__title {
  width: 80px;
  height: 30px;
  font-family: Poppins, sans-serif;
  font-weight: 500;
  font-size: 20px;
  line-height: 30px;
  text-align: center;
  color: #000;
}

.plan-org-dialog__close {
  position: relative;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 8px;
  background: #fff2e2;
  cursor: pointer;
}

.plan-org-dialog__close-icon {
  font-size: 24px;
  color: #1c1d22;
}

.plan-org-dialog__body {
  display: grid;
  grid-template-columns: 230px 1fr;
  min-height: 598px;
  border-top: 1px solid #e1e2e9;
  border-bottom: 1px solid #e1e2e9;
}

.plan-org-dialog__sidebar {
  padding: 10px 10px 0;
  border-right: 1px solid #e1e2e9;
}

.plan-org-dialog__sidebar-divider {
  width: calc(100% + 20px);
  margin: 12px 0 0 -10px;
  border-top: 1px solid #e1e2e9;
}

.plan-org-dialog__search {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 32px;
  padding: 4px 4px 4px 8px;
  border: 1px solid #dcdcdc;
  border-radius: 3px;
  background: #fff;
}

.plan-org-dialog__search-content {
  flex: 1;
}

.plan-org-dialog__search-input {
  width: 100%;
  border: none;
  outline: none;
  font-size: 14px;
  line-height: 22px;
  color: rgba(0, 0, 0, 0.9);
}

.plan-org-dialog__search-input::placeholder {
  color: rgba(0, 0, 0, 0.4);
}

.plan-org-dialog__search-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  color: rgba(0, 0, 0, 0.6);
}

.plan-org-dialog__tree {
  margin-top: 10px;
  max-height: 510px;
  overflow: auto;
}

.plan-org-dialog__tree-item {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 34px;
  padding-top: 4px;
  padding-right: 8px;
  padding-bottom: 4px;
  cursor: pointer;
  color: rgba(0, 0, 0, 0.9);
}

.plan-org-dialog__tree-item.is-active,
.plan-org-dialog__tree-item.is-current {
  background: #f5f9ff;
}

.plan-org-dialog__tree-caret {
  width: 16px;
  color: rgba(0, 0, 0, 0.6);
  flex: 0 0 16px;
}

.plan-org-dialog__tree-label {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  line-height: 22px;
}

.plan-org-dialog__content {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.plan-org-dialog__table-shell {
  flex: 1;
  padding: 10px 0 0;
  overflow-y: auto;
  overflow-x: hidden;
}

.plan-org-dialog__table-footer {
  position: relative;
  display: flex;
  align-items: center;
  align-self: stretch;
  flex: none;
  order: 1;
  height: 64px;
  min-height: 64px;
  padding: 0 24px;
  background: #fff;
}

.plan-org-dialog__table-total {
  display: flex;
  align-items: center;
  flex: none;
  width: 85px;
  height: 22px;
  font-family: 'PingFang SC', sans-serif;
  font-size: 14px;
  line-height: 22px;
  color: rgba(0, 0, 0, 0.6);
}

.plan-org-dialog__table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}

.plan-org-dialog__table th,
.plan-org-dialog__table td {
  height: 46px;
  padding: 12px 16px;
  font-size: 14px;
  line-height: 22px;
  text-align: left;
  color: rgba(0, 0, 0, 0.9);
}

.plan-org-dialog__table th {
  border-bottom: 1px solid #e7e7e7;
}

.plan-org-dialog__table tbody tr:not(:last-child) td {
  border-bottom: 1px solid #e7e7e7;
}

.plan-org-dialog__table th.col-check {
  padding: 12px 16px;
}

.plan-org-dialog__table th.col-org-code,
.plan-org-dialog__table td.col-org-code,
.plan-org-dialog__table th.col-org-name,
.plan-org-dialog__table td.col-org-name,
.plan-org-dialog__table th.col-long-name,
.plan-org-dialog__table td.col-long-name {
  width: 161.8px;
  min-width: 161.8px;
  max-width: 161.8px;
  padding: 12px 24px 12px 16px;
}

.plan-org-dialog__table td.col-check {
  padding: 12px 10px;
}

.plan-org-dialog__table th {
  background: #f5f9ff;
  color: rgba(0, 0, 0, 0.4);
  font-weight: 400;
}

.plan-org-dialog__table tbody tr:nth-child(even) {
  background: #fafcff;
}

.plan-org-dialog__table tbody tr:last-child td {
  border-bottom: none;
}

.plan-org-dialog__table tbody tr.is-current {
  background: #eef3ff;
}

.plan-org-dialog__table tbody tr.is-disabled {
  opacity: 0.6;
}

.plan-org-dialog__table tbody tr {
  cursor: pointer;
}

.col-check {
  width: 60px;
  min-width: 60px;
  max-width: 60px;
  text-align: center;
}

.plan-org-dialog__check-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
}

.plan-org-dialog__check-cell--header {
  color: rgba(0, 0, 0, 0.4);
}

.plan-org-dialog__checkbox {
  position: relative;
  display: inline-flex;
  width: 16px;
  height: 16px;
  box-sizing: border-box;
  border: 1px solid #dcdcdc;
  border-radius: 3px;
  background: rgba(255, 255, 255, 0.9);
  flex: 0 0 16px;
}

.plan-org-dialog__checkbox.is-checked {
  border-color: #7288fa;
  background: #7288fa;
}

.plan-org-dialog__checkbox.is-checked::after {
  content: '';
  position: absolute;
  left: 4px;
  top: 1px;
  width: 4px;
  height: 8px;
  border: solid #fff;
  border-width: 0 2px 2px 0;
  transform: rotate(45deg);
}

.plan-org-dialog__index {
  flex: 0 0 auto;
  font-size: 14px;
  line-height: 22px;
  color: rgba(0, 0, 0, 0.9);
}

.col-org-code,
.col-org-name,
.col-long-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.plan-org-dialog__empty {
  text-align: center;
  color: rgba(0, 0, 0, 0.4);
}

.plan-org-dialog__footer {
  position: relative;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 9px;
  padding: 12px 24px 16px;
  background: #fff;
  margin-top: -1px;
}

.plan-org-dialog__btn {
  box-sizing: border-box;
  display: inline-flex;
  justify-content: center;
  align-items: center;
  height: 32px;
  padding: 5px 16px;
  border-radius: 6px;
  white-space: nowrap;
  cursor: pointer;
}

.plan-org-dialog__btn--secondary {
  width: 58px;
  border: 1px solid #bec0ca;
  background: #fff;
  color: #53545c;
  box-shadow: 0 2px 0 rgba(0, 0, 0, 0.016);
  font-family: 'PingFang SC', sans-serif;
  font-weight: 400;
  font-size: 13px;
  line-height: 22px;
  text-align: center;
}

.plan-org-dialog__btn--primary {
  width: 60px;
  border: none;
  background: #7288fa;
  color: #fff;
  box-shadow: 0 2px 0 rgba(0, 0, 0, 0.043);
  font-family: 'Roboto', sans-serif;
  font-weight: 400;
  font-size: 14px;
  line-height: 22px;
  text-align: center;
}

.plan-org-dialog__btn--primary:disabled {
  cursor: not-allowed;
  opacity: 1;
  background: #bec7f7;
  color: rgba(255, 255, 255, 0.9);
  box-shadow: none;
}
</style>

<style lang="scss">
.plan-org-select-dialog {
  .el-dialog {
    width: 1100px;
    max-width: calc(100vw - 32px);
    border-radius: 12px;
    background: #fff;
  }

  .el-dialog__header {
    margin-right: 0;
    margin-bottom: 0;
    padding: 0;
    border: none !important;
    box-shadow: none !important;
  }

  .el-dialog__body {
    padding: 0;
    border: none !important;
    box-shadow: none !important;
  }

  .el-dialog__footer {
    padding: 0;
    border-top: none;
  }

  .plan-org-dialog__pagination {
    margin-left: auto;
  }

  .plan-org-dialog__pagination.el-pagination {
    --el-pagination-button-width: 32px;
    --el-pagination-button-height: 32px;
    --el-pagination-button-disabled-bg-color: #fff;
    --el-pagination-bg-color: #fff;
    --el-pagination-text-color: rgba(0, 0, 0, 0.9);
    --el-pagination-hover-color: #7288fa;
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 16px;
    height: 32px;
    color: rgba(0, 0, 0, 0.9);
    font-family: 'PingFang SC', sans-serif;
  }

  .plan-org-dialog__pagination .el-pagination__sizes {
    margin: 0;
  }

  .plan-org-dialog__pagination .el-select .el-input__wrapper {
    min-width: 112px;
    height: 32px;
    padding: 5px 8px;
    border-radius: 3px;
    box-shadow: none;
    border: 1px solid #dcdcdc;
  }

  .plan-org-dialog__pagination .el-select .el-input__inner {
    font-size: 14px;
    line-height: 22px;
    color: rgba(0, 0, 0, 0.9);
  }

  .plan-org-dialog__pagination .btn-prev,
  .plan-org-dialog__pagination .btn-next,
  .plan-org-dialog__pagination .el-pager li {
    min-width: 32px;
    width: 32px;
    height: 32px;
    margin: 0;
    border: 1px solid #dcdcdc;
    border-radius: 3px;
    background: #fff;
    font-size: 14px;
    font-weight: 400;
    line-height: 22px;
    color: rgba(0, 0, 0, 0.9);
  }

  .plan-org-dialog__pagination .el-pager {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .plan-org-dialog__pagination .btn-prev,
  .plan-org-dialog__pagination .btn-next {
    display: inline-flex;
    align-items: center;
    justify-content: center;
  }

  .plan-org-dialog__pagination .btn-prev:disabled {
    border-color: transparent;
    background: #fff;
    color: rgba(0, 0, 0, 0.26);
  }

  .plan-org-dialog__pagination .el-pager li.is-active {
    border-color: #7288fa;
    background: #7288fa;
    color: rgba(255, 255, 255, 0.9);
  }

  .plan-org-dialog__pagination .el-pager li.btn-quickprev,
  .plan-org-dialog__pagination .el-pager li.btn-quicknext {
    color: rgba(0, 0, 0, 0.9);
  }
}
</style>
