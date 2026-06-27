import { defineStore } from 'pinia'
import { ref, computed, h } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { WarningFilled } from '@element-plus/icons-vue'
import { roleApi } from '@/api/modules/role'
import { orgApi } from '@/api/modules/org'
import type {
  Role,
  RoleForm,
  RoleGroup,
  RoleGroupForm,
  RoleEmployee,
  RoleStatistics,
  PermissionNode,
  OrgTreeNode
} from '@/types/role'

/** 构建确认弹窗 message（图标 + 标题 + 描述） */
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

export const useRoleStore = defineStore('role', () => {
  // ==================== 状态 ====================

  const UNAUTHORIZED_GROUP_ID = -1
  const UNAUTHORIZED_GROUP_NAME = '未授权分组'

  /** 分组列表 */
  const groups = ref<RoleGroup[]>([])

  /** 角色列表 */
  const roles = ref<Role[]>([])

  /** 加载状态 */
  const loading = ref(false)

  /** 搜索关键字 */
  const keyword = ref('')

  /** 统计数据 */
  const statistics = ref<RoleStatistics>({
    total: 0,
    activeCount: 0,
    inactiveCount: 0,
    groupCount: 0
  })

  /** 详情弹窗 */
  const detailVisible = ref(false)

  /** 角色表单弹窗 */
  const formVisible = ref(false)

  /** 分组表单弹窗 */
  const groupFormVisible = ref(false)

  /** 关联员工弹窗 */
  const employeesVisible = ref(false)

  /** 员工选择弹窗（用于添加成员） */
  const memberSelectVisible = ref(false)

  /** 关联员工分页状态 */
  const employeePageNum = ref(1)
  const employeePageSize = ref(20)
  const employeeTotal = ref(0)

  /** 当前编辑的角色 ID */
  const currentRoleId = ref<number | null>(null)

  /** 当前编辑的分组 ID */
  const currentGroupId = ref<number | null>(null)

  /** 新增角色时默认的分组 ID */
  const defaultGroupId = ref<number | null>(null)

  /** 当前角色关联的员工 */
  const currentEmployees = ref<RoleEmployee[]>([])

  /** 全量已关联员工 ID（用于选择员工时排除） */
  const allEmployeeIds = ref<number[]>([])

  /** 当前角色详情 */
  const currentRole = ref<Role | null>(null)

  /** 当前用户可授功能权限树 */
  const permissionTree = ref<PermissionNode[]>([])

  /** 当前用户可管组织树（仅启用） */
  const orgTree = ref<OrgTreeNode[]>([])

  /** 当前用户可管组织树（全量，用于名称回显） */
  const orgTreeAll = ref<OrgTreeNode[]>([])

  /** 展开的分组 ID 列表 */
  const expandedGroupIds = ref<number[]>([])

  /** 是否已初始化 */
  const initialized = ref(false)

  /** 当前选中的分组 ID（null = 全部） */
  const selectedGroupId = ref<number | null>(null)

  /** 角色列表页码 */
  const pageNum = ref(1)

  /** 角色列表每页条数 */
  const pageSize = ref(20)

  // ==================== 计算属性 ====================

  /** 根据关键字和分组过滤后的角色 */
  const filteredRoles = computed(() => {
    let result = roles.value
    // 按分组过滤
    if (selectedGroupId.value !== null) {
      if (selectedGroupId.value === UNAUTHORIZED_GROUP_ID) {
        result = result.filter(r => r.groupVisible === false)
      } else {
        result = result.filter(r => r.groupId === selectedGroupId.value && r.groupVisible !== false)
      }
    }
    // 按关键字过滤
    if (keyword.value.trim()) {
      const kw = keyword.value.toLowerCase()
      result = result.filter(r =>
        r.roleName.toLowerCase().includes(kw) ||
        r.roleCode.toLowerCase().includes(kw)
      )
    }
    return result
  })

  /** 过滤后的角色总数 */
  const filteredTotal = computed(() => filteredRoles.value.length)

  /** 分页后的角色列表 */
  const pagedRoles = computed(() => {
    const start = (pageNum.value - 1) * pageSize.value
    return filteredRoles.value.slice(start, start + pageSize.value)
  })

  /** 各分组角色数量 Map */
  const groupRoleCountMap = computed(() => {
    const map = new Map<number, number>()
    groups.value.forEach(g => map.set(g.id, 0))
    let unauthCount = 0
    roles.value.forEach(r => {
      if (r.groupVisible === false) {
        unauthCount++
      } else {
        const count = map.get(r.groupId) || 0
        map.set(r.groupId, count + 1)
      }
    })
    map.set(UNAUTHORIZED_GROUP_ID, unauthCount)
    return map
  })

  const normalizedGroups = computed<RoleGroup[]>(() => {
    const hasUnauthorizedRole = roles.value.some(role => role.groupVisible === false)
    if (!hasUnauthorizedRole) {
      return groups.value
    }
    return [
      ...groups.value,
      {
        id: UNAUTHORIZED_GROUP_ID,
        groupName: UNAUTHORIZED_GROUP_NAME,
        sortOrder: Number.MAX_SAFE_INTEGER,
        remark: '角色可见但所属分组无权限',
        createdAt: '',
        updatedAt: ''
      }
    ]
  })

  /** 按分组组织的角色 */
  const rolesByGroup = computed(() => {
    const map = new Map<number, Role[]>()
    normalizedGroups.value.forEach(g => {
      map.set(g.id, [])
    })
    filteredRoles.value.forEach(r => {
      const groupKey = r.groupVisible === false ? UNAUTHORIZED_GROUP_ID : r.groupId
      const list = map.get(groupKey)
      if (list) {
        list.push(r)
      }
    })
    return map
  })

  // ==================== Actions ====================

  /** 获取分组列表 */
  const fetchGroups = async () => {
    try {
      const res = await roleApi.getGroupList()
      if (res.code === 'SUCCESS' && res.data) {
        groups.value = res.data.sort((a, b) => a.sortOrder - b.sortOrder)
        // 默认展开所有分组
        if (expandedGroupIds.value.length === 0) {
          expandedGroupIds.value = groups.value.map(g => g.id)
        }
      }
    } catch (error: any) {
      console.error('获取分组列表失败:', error)
    }
  }

  /** 获取角色列表 */
  const fetchRoles = async () => {
    loading.value = true
    try {
      const res = await roleApi.getList()
      if (res.code === 'SUCCESS' && res.data) {
        roles.value = res.data
        // 更新统计数据
        statistics.value = {
          total: roles.value.length,
          activeCount: roles.value.filter(r => r.status === 'active').length,
          inactiveCount: roles.value.filter(r => r.status === 'inactive').length,
          groupCount: groups.value.length
        }
      }
    } catch (error: any) {
      ElMessage.error(error.message || '获取角色列表失败')
    } finally {
      loading.value = false
    }
  }

  /** 获取当前用户可授权限树 */
  const fetchPermissionTree = async () => {
    try {
      const res = await roleApi.getPermissionTree()
      if (res.code === 'SUCCESS' && res.data) {
        permissionTree.value = res.data
      }
    } catch (error: any) {
      console.error('获取权限树失败:', error)
      permissionTree.value = []
    }
  }

  /** 获取当前用户可管理组织树 */
  const fetchOrgTree = async () => {
    try {
      const res = await orgApi.getTree()
      if (res.code === 'SUCCESS' && res.data) {
        orgTree.value = res.data
        orgTreeAll.value = res.data
      } else {
        orgTree.value = []
        orgTreeAll.value = []
      }
    } catch (error: any) {
      console.error('获取组织树失败:', error)
      orgTree.value = []
      orgTreeAll.value = []
    }
  }

  /** 初始化数据 */
  const init = async () => {
    if (initialized.value) return
    await fetchGroups()
    await fetchRoles()
    await fetchPermissionTree()
    await fetchOrgTree()
    selectedGroupId.value = null
    initialized.value = true
  }

  /** 搜索 */
  const search = (kw: string) => {
    keyword.value = kw
    pageNum.value = 1
  }

  /** 选择分组 */
  const selectGroup = (id: number | null) => {
    selectedGroupId.value = id
    pageNum.value = 1
  }

  /** 切换页码 */
  const changePage = (page: number) => {
    pageNum.value = page
  }

  /** 切换每页条数 */
  const changePageSize = (size: number) => {
    pageSize.value = size
    pageNum.value = 1
  }

  /** 新增分组 */
  const createGroup = async (data: RoleGroupForm) => {
    try {
      const res = await roleApi.createGroup(data)
      if (res.code === 'SUCCESS') {
        ElMessage.success('分组新增成功')
        await fetchGroups()
        closeGroupForm()
      }
    } catch (error: any) {
      // 错误消息已在 API 拦截器中显示，此处不再重复
      console.error('分组新增失败:', error.message)
    }
  }

  /** 更新分组 */
  const updateGroup = async (id: number, data: Partial<RoleGroupForm>) => {
    try {
      const res = await roleApi.updateGroup(id, data)
      if (res.code === 'SUCCESS') {
        ElMessage.success('分组编辑成功')
        await fetchGroups()
        closeGroupForm()
      }
    } catch (error: any) {
      // 错误消息已在 API 拦截器中显示，此处不再重复
      console.error('分组编辑失败:', error.message)
    }
  }

  /** 删除分组 */
  const deleteGroup = async (id: number) => {
    const roleCount = roles.value.filter(r => r.groupId === id).length
    if (roleCount > 0) {
      ElMessage.warning(`该分组下有 ${roleCount} 个角色，请先移除或删除角色后再操作`)
      return
    }

    try {
      await ElMessageBox({
        title: '删除分组',
        message: renderConfirmMessage('删除分组', '确定要删除该分组吗？此操作不可恢复。'),
        customClass: 'material-message-box',
        showClose: false,
        closeOnClickModal: false,
        closeOnPressEscape: true,
        showCancelButton: true,
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
      })

      const res = await roleApi.deleteGroup(id)
      if (res.code === 'SUCCESS') {
        ElMessage.success('分组删除成功')
        await fetchGroups()
      }
    } catch (error: any) {
      if (error !== 'cancel') {
        // 错误消息已在 API 拦截器中显示，这里不再重复显示
        console.error('分组删除失败:', error.message)
      }
    }
  }

  /** 新增角色 */
  const createRole = async (data: RoleForm) => {
    try {
      const res = await roleApi.create(data)
      if (res.code === 'SUCCESS') {
        ElMessage.success('角色新增成功')
        await fetchRoles()
        closeForm()
      }
    } catch (error: any) {
      // 错误消息已在 API 拦截器中显示，这里不再重复显示
      console.error('角色新增失败:', error.message)
    }
  }

  /** 更新角色 */
  const updateRole = async (id: number, data: Partial<RoleForm>) => {
    try {
      const res = await roleApi.update(id, data)
      if (res.code === 'SUCCESS') {
        ElMessage.success('角色编辑成功')
        await fetchRoles()
        closeForm()
      }
    } catch (error: any) {
      // 错误消息已在 API 拦截器中显示，这里不再重复显示
      console.error('角色编辑失败:', error.message)
    }
  }

  /** 删除角色 */
  const deleteRole = async (id: number) => {
    try {
      await ElMessageBox({
        title: '删除角色',
        message: renderConfirmMessage('删除角色', '确定要删除该角色吗？此操作不可恢复。'),
        customClass: 'material-message-box',
        showClose: false,
        closeOnClickModal: false,
        closeOnPressEscape: true,
        showCancelButton: true,
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
      })

      const res = await roleApi.delete(id)
      if (res.code === 'SUCCESS') {
        ElMessage.success('角色删除成功')
        await fetchRoles()
      }
    } catch (error: any) {
      if (error !== 'cancel') {
        // 错误消息已在 API 拦截器中显示，这里不再重复显示
        console.error('角色删除失败:', error.message)
      }
    }
  }

  /** 获取角色关联员工 */
  const fetchRoleEmployees = async (roleId: number, pageNum?: number) => {
    try {
      if (pageNum !== undefined) {
        employeePageNum.value = pageNum
      }
      const [res, allRes] = await Promise.all([
        roleApi.getRoleEmployees(roleId, { pageNum: employeePageNum.value, pageSize: employeePageSize.value }),
        roleApi.getRoleEmployees(roleId, { pageNum: 1, pageSize: 9999 })
      ])
      if (res.code === 'SUCCESS' && res.data) {
        currentEmployees.value = res.data.list || []
        employeeTotal.value = res.data.total || 0
      }
      if (allRes.code === 'SUCCESS' && allRes.data) {
        allEmployeeIds.value = (allRes.data.list || []).map(e => e.id)
      }
    } catch (error: any) {
      console.error('获取关联员工失败:', error)
      currentEmployees.value = []
      employeeTotal.value = 0
    }
  }

  /** 打开分组表单弹窗 */
  const openGroupForm = (id: number | null = null) => {
    currentGroupId.value = id
    groupFormVisible.value = true
  }

  /** 关闭分组表单弹窗 */
  const closeGroupForm = () => {
    groupFormVisible.value = false
    currentGroupId.value = null
  }

  /** 打开角色表单弹窗 */
  const openForm = async (id: number | null = null, groupId: number | null = null, roleData: Role | null = null) => {
    currentRoleId.value = id
    defaultGroupId.value = groupId
    currentRole.value = roleData

    if (!roleData && id) {
      try {
        const res = await roleApi.getDetail(id)
        if (res.code === 'SUCCESS' && res.data) {
          currentRole.value = res.data
        }
      } catch (error: any) {
        console.error('获取角色详情失败:', error)
      }
    }

    formVisible.value = true
  }

  /** 关闭角色表单弹窗 */
  const closeForm = () => {
    formVisible.value = false
    currentRoleId.value = null
    defaultGroupId.value = null
  }

  /** 打开详情弹窗 */
  const openDetail = async (id: number) => {
    currentRoleId.value = id
    // 调用详情接口获取完整数据
    try {
      const res = await roleApi.getDetail(id)
      if (res.code === 'SUCCESS' && res.data) {
        currentRole.value = res.data
      }
    } catch (error: any) {
      console.error('获取角色详情失败:', error)
    }
    detailVisible.value = true
  }

  /** 关闭详情弹窗 */
  const closeDetail = (resetRoleId = true) => {
    detailVisible.value = false
    if (resetRoleId) {
      currentRoleId.value = null
      currentRole.value = null
    }
  }

  /** 打开关联员工弹窗 */
  const openEmployees = async (roleId: number) => {
    currentRoleId.value = roleId
    employeePageNum.value = 1
    await fetchRoleEmployees(roleId)
    employeesVisible.value = true
  }

  /** 关闭关联员工弹窗 */
  const closeEmployees = () => {
    employeesVisible.value = false
    currentRoleId.value = null
    currentEmployees.value = []
    employeeTotal.value = 0
    allEmployeeIds.value = []
  }

  /** 打开员工选择弹窗 */
  const openMemberSelect = () => {
    memberSelectVisible.value = true
  }

  /** 关闭员工选择弹窗 */
  const closeMemberSelect = () => {
    memberSelectVisible.value = false
  }

  /** 添加角色成员 */
  const addRoleMembers = async (employeeIds: number[]) => {
    if (!currentRoleId.value) return
    try {
      const res = await roleApi.addRoleMembers(currentRoleId.value, employeeIds)
      if (res.code === 'SUCCESS') {
        ElMessage.success(`成功添加 ${res.data.addedCount} 名成员`)
        closeMemberSelect()
        await fetchRoleEmployees(currentRoleId.value)
        updateRoleMemberCount(currentRoleId.value, res.data.memberCount)
      }
    } catch (error: any) {
      console.error('添加角色成员失败:', error)
    }
  }

  /** 批量移除角色成员 */
  const batchRemoveRoleMembers = async (employeeIds: number[]): Promise<boolean> => {
    if (!currentRoleId.value) return false
    try {
      await ElMessageBox({
        title: '移除成员',
        message: renderConfirmMessage('移除成员', `确定要移除选中的 ${employeeIds.length} 名成员吗？`),
        customClass: 'material-message-box',
        showClose: false,
        closeOnClickModal: false,
        closeOnPressEscape: true,
        showCancelButton: true,
        confirmButtonText: '确认移除',
        cancelButtonText: '取消',
      })
      const res = await roleApi.batchRemoveRoleMembers(currentRoleId.value, employeeIds)
      if (res.code === 'SUCCESS') {
        ElMessage.success(`成功移除 ${res.data.removedCount} 名成员`)
        await fetchRoleEmployees(currentRoleId.value)
        updateRoleMemberCount(currentRoleId.value, res.data.memberCount)
        return true
      }
    } catch (error: any) {
      if (error !== 'cancel') {
        console.error('移除角色成员失败:', error)
      }
    }
    return false
  }

  /** 更新角色列表中的成员数量 */
  const updateRoleMemberCount = (roleId: number, count: number) => {
    const role = roles.value.find(r => r.id === roleId)
    if (role) {
      role.memberCount = count
    }
  }

  /** 切换分组展开状态 */
  const toggleGroupExpand = (groupId: number) => {
    const index = expandedGroupIds.value.indexOf(groupId)
    if (index > -1) {
      expandedGroupIds.value.splice(index, 1)
    } else {
      expandedGroupIds.value.push(groupId)
    }
  }

  /** 获取当前编辑的角色数据 */
  const getCurrentRole = () => {
    // 优先返回详情数据（包含完整信息）
    if (currentRole.value) return currentRole.value
    if (!currentRoleId.value) return null
    // 使用 == 进行宽松比较，避免类型不匹配问题
    return roles.value.find(r => r.id == currentRoleId.value) || null
  }

  /** 获取当前编辑的分组数据 */
  const getCurrentGroup = () => {
    if (!currentGroupId.value) return null
    // 使用 == 进行宽松比较，避免类型不匹配问题
    return groups.value.find(g => g.id == currentGroupId.value) || null
  }

  return {
    // state
    groups,
    normalizedGroups,
    roles,
    loading,
    keyword,
    statistics,
    detailVisible,
    formVisible,
    groupFormVisible,
    employeesVisible,
    currentRoleId,
    currentGroupId,
    defaultGroupId,
    currentRole,
    permissionTree,
    orgTree,
    orgTreeAll,
    currentEmployees,
    allEmployeeIds,
    employeePageNum,
    employeePageSize,
    employeeTotal,
    memberSelectVisible,
    expandedGroupIds,
    initialized,
    selectedGroupId,
    pageNum,
    pageSize,
    // computed
    filteredRoles,
    filteredTotal,
    pagedRoles,
    groupRoleCountMap,
    rolesByGroup,
    // actions
    init,
    fetchGroups,
    fetchRoles,
    fetchPermissionTree,
    fetchOrgTree,
    search,
    selectGroup,
    changePage,
    changePageSize,
    createGroup,
    updateGroup,
    deleteGroup,
    createRole,
    updateRole,
    deleteRole,
    fetchRoleEmployees,
    openGroupForm,
    closeGroupForm,
    openForm,
    closeForm,
    openDetail,
    closeDetail,
    openEmployees,
    closeEmployees,
    openMemberSelect,
    closeMemberSelect,
    addRoleMembers,
    batchRemoveRoleMembers,
    toggleGroupExpand,
    getCurrentRole,
    getCurrentGroup
  }
})
