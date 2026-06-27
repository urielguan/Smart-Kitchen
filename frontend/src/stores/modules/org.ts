import { defineStore } from 'pinia'
import { ref, computed, h } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { WarningFilled } from '@element-plus/icons-vue'
import { orgApi } from '@/api/modules/org'
import type { Organization, OrgQuery, OrgStatistics, OrgTreeNode, OrganizationImportResult } from '@/types/org'

/** 构建组织确认弹窗 message（图标 + 标题 + 描述） */
const renderConfirmMessage = (title: string, description: string) => () =>
  h('div', { class: 'org-confirm' }, [
    h('div', { class: 'org-confirm__content' }, [
      h(WarningFilled, { class: 'org-confirm__icon' }),
      h('div', { class: 'org-confirm__text' }, [
        h('div', { class: 'org-confirm__title' }, title),
        h('div', { class: 'org-confirm__description' }, description),
      ]),
    ]),
  ])

export const useOrgStore = defineStore('org', () => {
  /** 列表数据 */
  const list = ref<Organization[]>([])

  /** 总数 */
  const total = ref(0)

  /** 当前页码 */
  const pageNum = ref(1)

  /** 每页条数 */
  const pageSize = ref(10)

  /** 加载状态 */
  const loading = ref(false)

  /** 搜索参数 */
  const searchParams = ref<Partial<OrgQuery>>({
    keyword: '',
    orgType: undefined,
    status: undefined,
    includeChildren: false
  })

  /** 统计数据 */
  const statistics = ref<OrgStatistics>({
    total: 0,
    groupCount: 0,
    companyCount: 0,
    canteenCount: 0,
    deptCount: 0,
    activeCount: 0,
    inactiveCount: 0
  })

  /** 组织树数据（列表页当前查询结果） */
  const treeData = ref<OrgTreeNode[]>([])

  /** 组织树数据（当前用户有权限的全量数据） */
  const allTreeData = ref<OrgTreeNode[]>([])

  /** 展开的节点 ID 集合 */
  const expandedKeys = ref<Set<number>>(new Set())

  /** 过滤后的树形数据 */
  const filteredTreeData = computed(() => treeData.value)

  /** 详情弹窗 */
  const detailVisible = ref(false)

  /** 表单弹窗 */
  const formVisible = ref(false)

  /** 当前组织 ID */
  const currentOrgId = ref<number | null>(null)

  /** 导入弹窗 */
  const importDialogVisible = ref(false)

  /** 导入结果弹窗 */
  const importResultVisible = ref(false)

  /** 导入结果数据 */
  const importResult = ref<OrganizationImportResult | null>(null)

  /** 是否已初始化 */
  const initialized = ref(false)

  /** 获取组织列表 */
  const fetchList = async () => {
    loading.value = true
    try {
      const params: OrgQuery = {
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        ...searchParams.value
      }
      const res = await orgApi.getList(params)
      if (res.code === 'SUCCESS' && res.data) {
        list.value = res.data.list
        total.value = res.data.total
      }
    } catch (error: any) {
      ElMessage.error(error.message || '获取列表失败')
    } finally {
      loading.value = false
    }
  }

  /** 获取统计数据 */
  const fetchStatistics = async () => {
    try {
      const res = await orgApi.getStatistics()
      if (res.code === 'SUCCESS' && res.data) {
        statistics.value = res.data
      }
    } catch (error: any) {
      console.error('获取统计数据失败:', error)
    }
  }

  /** 获取组织树（列表页当前查询结果） */
  const fetchTree = async () => {
    try {
      const res = await orgApi.getTree({
        keyword: searchParams.value.keyword,
        orgType: searchParams.value.orgType,
        status: searchParams.value.status,
        includeChildren: searchParams.value.includeChildren
      })
      if (res.code === 'SUCCESS' && res.data) {
        treeData.value = res.data
        // 首次加载或搜索条件变更时初始化展开状态，其余情况保留已有展开状态
        if (expandedKeys.value.size === 0) {
          initExpandedKeys(res.data, 0)
        }
      }
    } catch (error: any) {
      console.error('获取组织树失败:', error)
    }
  }

  /** 获取组织树（全量权限范围，用于下拉选择） */
  const fetchAllTree = async () => {
    try {
      const res = await orgApi.getTree()
      if (res.code === 'SUCCESS' && res.data) {
        allTreeData.value = res.data
      }
    } catch (error: any) {
      console.error('获取全量组织树失败:', error)
    }
  }

  /** 初始化展开状态（前两级默认展开） */
  const initExpandedKeys = (nodes: OrgTreeNode[], level: number) => {
    if (level < 2) {
      nodes.forEach(node => {
        expandedKeys.value.add(node.id)
        if (node.children?.length) {
          initExpandedKeys(node.children, level + 1)
        }
      })
    }
  }

  /** 切换节点展开/收起 */
  const toggleExpand = (id: number) => {
    if (expandedKeys.value.has(id)) {
      expandedKeys.value.delete(id)
    } else {
      expandedKeys.value.add(id)
    }
  }

  /** 搜索 */
  const search = async (params: Partial<OrgQuery>) => {
    searchParams.value = {
      ...searchParams.value,
      ...params
    }
    pageNum.value = 1
    expandedKeys.value = new Set()
    await fetchTree()
  }

  /** 重置搜索 */
  const resetSearch = async () => {
    searchParams.value = {
      keyword: '',
      orgType: undefined,
      status: undefined,
      includeChildren: false
    }
    pageNum.value = 1
    expandedKeys.value = new Set()
    await fetchTree()
  }

  /** 分页切换 */
  const changePage = async (page: number) => {
    pageNum.value = page
    await fetchList()
  }

  /** 每页条数改变 */
  const changePageSize = async (size: number) => {
    pageSize.value = size
    pageNum.value = 1
    await fetchList()
  }

  /** 删除组织 */
  const deleteOrg = async (id: number) => {
    try {
      await ElMessageBox({
        title: '删除组织',
        message: renderConfirmMessage('删除组织', '确认删除该组织？删除后不可恢复。'),
        customClass: 'material-message-box',
        showClose: false,
        closeOnClickModal: false,
        closeOnPressEscape: true,
        showCancelButton: true,
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
      })

      const res = await orgApi.delete(id)
      if (res.code === 'SUCCESS') {
        ElMessage.success('删除成功')
        await fetchList()
        await fetchStatistics()
        await Promise.all([fetchTree(), fetchAllTree()])
      }
    } catch (error: any) {
      // 用户取消确认框时不处理，其他错误已在响应拦截器中处理
      if (error === 'cancel') {
        // 用户取消，不显示任何提示
      }
      // 其他错误已在 api/index.ts 响应拦截器中统一显示，此处不再重复
    }
  }

  /** 从树数据中查找组织名称 */
  const findOrgName = (id: number, nodes: OrgTreeNode[] = treeData.value): string => {
    for (const node of nodes) {
      if (node.id === id) return node.orgName
      if (node.children?.length) {
        const found = findOrgName(id, node.children)
        if (found) return found
      }
    }
    return ''
  }

  /** 更新组织状态 */
  const updateStatus = async (id: number, status: 'active' | 'inactive') => {
    try {
      const actionText = status === 'active' ? '启用' : '停用'
      const orgName = findOrgName(id)
      const displayName = orgName ? `「${orgName}」` : '该组织'
      await ElMessageBox({
        title: `${actionText}组织`,
        message: renderConfirmMessage(`${actionText}组织`, `确认${actionText}组织${displayName}？`),
        customClass: 'material-message-box',
        showClose: false,
        closeOnClickModal: false,
        closeOnPressEscape: true,
        showCancelButton: true,
        confirmButtonText: `确认${actionText}`,
        cancelButtonText: '取消',
      })

      const res = await orgApi.updateStatus(id, status)
      if (res.code === 'SUCCESS') {
        ElMessage.success(`${actionText}成功`)
        await fetchList()
        await fetchStatistics()
        await Promise.all([fetchTree(), fetchAllTree()])
      }
    } catch (error: any) {
      // 用户取消确认框时不处理
      if (error === 'cancel') {
        // 用户取消，不显示任何提示
      }
      // 其他错误已在 api/index.ts 响应拦截器中统一显示，此处不再重复
    }
  }

  /** 打开详情弹窗 */
  const openDetail = (id: number) => {
    currentOrgId.value = id
    detailVisible.value = true
  }

  /** 关闭详情弹窗 */
  const closeDetail = () => {
    detailVisible.value = false
    currentOrgId.value = null
  }

  /** 打开表单弹窗 */
  const openForm = (id: number | null = null) => {
    currentOrgId.value = id
    formVisible.value = true
  }

  /** 关闭表单弹窗 */
  const closeForm = () => {
    formVisible.value = false
    currentOrgId.value = null
  }

  /** 获取组织详情 */
  const getOrgDetail = async (id: number): Promise<Organization | undefined> => {
    try {
      const res = await orgApi.getDetail(id)
      if (res.code === 'SUCCESS' && res.data) {
        return res.data
      }
    } catch (error: any) {
      console.error('获取组织详情失败:', error)
    }
    return undefined
  }

  /** 初始化数据 */
  const init = async () => {
    if (initialized.value) return
    await Promise.all([fetchList(), fetchStatistics(), fetchTree(), fetchAllTree()])
    initialized.value = true
  }

  // ==================== 导入导出相关方法 ====================

  /** 打开导入弹窗 */
  const openImportDialog = () => {
    importDialogVisible.value = true
  }

  /** 关闭导入弹窗 */
  const closeImportDialog = () => {
    importDialogVisible.value = false
  }

  /** 关闭导入结果弹窗 */
  const closeImportResult = () => {
    importResultVisible.value = false
    importResult.value = null
  }

  /** 处理导入 */
  const handleImport = async (file: File) => {
    loading.value = true
    try {
      const res = await orgApi.importOrganizations(file)
      if (res.code === 'SUCCESS' && res.data) {
        importResult.value = res.data
        importDialogVisible.value = false
        importResultVisible.value = true
        // 刷新数据
        await Promise.all([fetchList(), fetchStatistics(), fetchTree()])
        ElMessage.success(`导入完成：共 ${res.data.total} 条，成功 ${res.data.successCount} 条`)
      }
    } catch (error: any) {
      ElMessage.error(error.message || '导入失败')
    } finally {
      loading.value = false
    }
  }

  /** 处理导出 */
  const handleExport = async (params?: { orgType?: string; status?: string; keyword?: string; includeChildren?: boolean }) => {
    try {
      await orgApi.exportOrganizations(params)
    } catch (error: any) {
      ElMessage.error(error.message || '导出失败')
    }
  }

  /** 下载模板 */
  const downloadTemplate = () => {
    orgApi.downloadTemplate()
  }

  /** 下载错误文件 */
  const downloadErrorFile = () => {
    if (importResult.value?.errorFileUrl) {
      const fileName = importResult.value.errorFileUrl.split('/').pop()
      if (fileName) {
        orgApi.downloadErrorFile(fileName)
      }
    }
  }

  return {
    // state
    list,
    total,
    pageNum,
    pageSize,
    loading,
    searchParams,
    statistics,
    treeData,
    allTreeData,
    filteredTreeData,
    expandedKeys,
    detailVisible,
    formVisible,
    currentOrgId,
    importDialogVisible,
    importResultVisible,
    importResult,
    initialized,
    // actions
    fetchList,
    fetchStatistics,
    fetchTree,
    fetchAllTree,
    search,
    resetSearch,
    changePage,
    changePageSize,
    toggleExpand,
    deleteOrg,
    updateStatus,
    openDetail,
    closeDetail,
    openForm,
    closeForm,
    getOrgDetail,
    init,
    // 导入导出
    openImportDialog,
    closeImportDialog,
    closeImportResult,
    handleImport,
    handleExport,
    downloadTemplate,
    downloadErrorFile
  }
})
