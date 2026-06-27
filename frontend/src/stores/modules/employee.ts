import { defineStore } from 'pinia'
import { h, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { WarningFilled } from '@element-plus/icons-vue'
import { employeeApi } from '@/api/modules/employee'
import { useUserStore } from '@/stores/modules/user'
import type { Employee, EmployeeForm, EmployeeImportResult, EmployeeQuery, Role } from '@/types/employee'

/** 构建员工确认弹窗 message（图标 + 标题 + 描述） */
const renderConfirmMessage = (title: string, description: string) => () =>
  h('div', { class: 'employee-confirm' }, [
    h('div', { class: 'employee-confirm__content' }, [
      h(WarningFilled, { class: 'employee-confirm__icon' }),
      h('div', { class: 'employee-confirm__text' }, [
        h('div', { class: 'employee-confirm__title' }, title),
        h('div', { class: 'employee-confirm__description' }, description),
      ]),
    ]),
  ])

export const useEmployeeStore = defineStore('employee', () => {
  /** 列表数据 */
  const list = ref<Employee[]>([])

  /** 总数 */
  const total = ref(0)

  /** 当前页码 */
  const pageNum = ref(1)

  /** 每页条数 */
  const pageSize = ref(10)

  /** 加载状态 */
  const loading = ref(false)

  /** 搜索参数 */
  const searchParams = ref<Partial<EmployeeQuery>>({
    keyword: '',
    orgId: undefined,
    accountStatus: undefined
  })

  /** 详情弹窗 */
  const detailVisible = ref(false)

  /** 表单弹窗 */
  const formVisible = ref(false)

  /** 当前员工 ID */
  const currentEmployeeId = ref<number | null>(null)

  /** 角色列表 */
  const roles = ref<Role[]>([])

  /** 是否已初始化 */
  const initialized = ref(false)

  /** 导入弹窗 */
  const importDialogVisible = ref(false)

  /** 导入结果弹窗 */
  const importResultVisible = ref(false)

  /** 导入结果 */
  const importResult = ref<EmployeeImportResult | null>(null)

  /** 获取员工列表 */
  const fetchList = async () => {
    loading.value = true
    try {
      const params: EmployeeQuery = {
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        ...searchParams.value
      }
      const res = await employeeApi.getList(params)
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

  /** 获取角色列表 */
  const fetchRoles = async () => {
    try {
      const res = await employeeApi.getRoles({ status: 'active' })
      if (res.code === 'SUCCESS' && res.data) {
        roles.value = res.data
      }
    } catch (error: any) {
      console.error('获取角色列表失败:', error)
    }
  }

  /** 搜索 */
  const search = async (params: Partial<EmployeeQuery>) => {
    searchParams.value = { ...searchParams.value, ...params }
    pageNum.value = 1
    await fetchList()
  }

  /** 重置搜索 */
  const resetSearch = async () => {
    searchParams.value = {
      keyword: '',
      orgId: undefined,
      accountStatus: undefined
    }
    pageNum.value = 1
    await fetchList()
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

  /** 删除员工 */
  const deleteEmployee = async (id: number) => {
    try {
      await ElMessageBox({
        title: '删除员工',
        message: renderConfirmMessage('删除员工', '确认删除该员工？删除后不可恢复。'),
        customClass: 'employee-message-box',
        showClose: false,
        closeOnClickModal: false,
        closeOnPressEscape: true,
        showCancelButton: true,
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
      })

      const res = await employeeApi.delete(id)
      if (res.code === 'SUCCESS') {
        ElMessage.success('删除成功')
        await fetchList()
      }
    } catch (error: any) {
      if (error !== 'cancel') {
        // 错误消息已在 API 拦截器中显示，此处不再重复
      }
    }
  }

  /** 切换账号状态（启用/禁用） */
  const toggleAccountStatus = async (employee: Employee) => {
    const currentStatus = employee.account?.accountStatus
    const newStatus = currentStatus === 'active' ? 'inactive' : 'active'
    const actionText = newStatus === 'active' ? '启用' : '禁用'

    try {
      await ElMessageBox({
        title: `${actionText}员工`,
        message: renderConfirmMessage(`${actionText}员工`, `确认${actionText}员工「${employee.realName}」的账号？`),
        customClass: 'employee-message-box',
        showClose: false,
        closeOnClickModal: false,
        closeOnPressEscape: true,
        showCancelButton: true,
        confirmButtonText: `确认${actionText}`,
        cancelButtonText: '取消',
      })

      const res = await employeeApi.updateAccountStatus(employee.id, newStatus)
      if (res.code === 'SUCCESS') {
        ElMessage.success(`${actionText}成功`)
        await fetchList()
      }
    } catch (error: any) {
      if (error !== 'cancel') {
        // 错误消息已在 API 拦截器中显示
      }
    }
  }

  /** 打开详情弹窗 */
  const openDetail = (id: number) => {
    currentEmployeeId.value = id
    detailVisible.value = true
  }

  /** 关闭详情弹窗 */
  const closeDetail = () => {
    detailVisible.value = false
    currentEmployeeId.value = null
  }

  /** 打开表单弹窗 */
  const openForm = (id: number | null = null) => {
    currentEmployeeId.value = id
    formVisible.value = true
  }

  /** 关闭表单弹窗 */
  const closeForm = () => {
    formVisible.value = false
    currentEmployeeId.value = null
  }

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
      const res = await employeeApi.importEmployees(file)
      if (res.code === 'SUCCESS' && res.data) {
        importResult.value = res.data
        importDialogVisible.value = false
        importResultVisible.value = true
        await fetchList()
        ElMessage.success(`导入完成：共 ${res.data.total} 条，成功 ${res.data.successCount} 条`)
      }
    } catch (error: any) {
      ElMessage.error(error.message || '导入失败')
    } finally {
      loading.value = false
    }
  }

  /** 处理导出 */
  const handleExport = async (params?: { keyword?: string; orgId?: number; accountStatus?: string }) => {
    try {
      await employeeApi.exportEmployees(params)
    } catch (error: any) {
      ElMessage.error(error.message || '导出失败')
    }
  }

  /** 下载模板 */
  const downloadTemplate = () => {
    employeeApi.downloadTemplate()
  }

  /** 下载错误文件 */
  const downloadErrorFile = () => {
    if (importResult.value?.errorFileUrl) {
      const fileName = importResult.value.errorFileUrl.split('/').pop()
      if (fileName) employeeApi.downloadErrorFile(fileName)
    }
  }

  /** 初始化数据 */
  const init = async () => {
    if (initialized.value) return
    await Promise.all([fetchList(), fetchRoles()])
    initialized.value = true
  }

  return {
    // state
    list,
    total,
    pageNum,
    pageSize,
    loading,
    searchParams,
    detailVisible,
    formVisible,
    currentEmployeeId,
    roles,
    initialized,
    importDialogVisible,
    importResultVisible,
    importResult,
    // actions
    fetchList,
    fetchRoles,
    search,
    resetSearch,
    changePage,
    changePageSize,
    deleteEmployee,
    toggleAccountStatus,
    openDetail,
    closeDetail,
    openForm,
    closeForm,
    openImportDialog,
    closeImportDialog,
    closeImportResult,
    handleImport,
    handleExport,
    downloadTemplate,
    downloadErrorFile,
    init
  }
})
