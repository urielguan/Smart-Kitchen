import { defineStore } from 'pinia'
import { h, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { WarningFilled } from '@element-plus/icons-vue'
import { alertRuleApi } from '@/api/modules/alert-rule'
import type {
  AlertRule,
  AlertRuleQuery,
  AlertRuleCreateDTO,
  AlertRuleUpdateDTO,
} from '@/types/alert-rule'

/** 构建告警规则确认弹窗 message（图标 + 标题 + 描述） */
const renderConfirmMessage = (title: string, description: string) => () =>
  h('div', { class: 'alert-confirm' }, [
    h('div', { class: 'alert-confirm__content' }, [
      h(WarningFilled, { class: 'alert-confirm__icon' }),
      h('div', { class: 'alert-confirm__text' }, [
        h('div', { class: 'alert-confirm__title' }, title),
        h('div', { class: 'alert-confirm__description' }, description),
      ]),
    ]),
  ])

export const useAlertRuleStore = defineStore('alert-rule', () => {
  const list = ref<AlertRule[]>([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(20)
  const loading = ref(false)
  const searchParams = ref<AlertRuleQuery>({})

  const formVisible = ref(false)
  const formMode = ref<'create' | 'edit'>('create')
  const currentRule = ref<AlertRule | null>(null)
  const detailVisible = ref(false)
  const detailRule = ref<AlertRule | null>(null)

  const fetchList = async () => {
    loading.value = true
    try {
      const res = await alertRuleApi.getList({
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        ...searchParams.value,
      })
      if (res.code === 'SUCCESS' && res.data) {
        list.value = res.data.list
        total.value = res.data.total
      }
    } catch (e) {
      console.error('获取告警规则列表失败', e)
      ElMessage.error('获取告警规则列表失败')
    } finally {
      loading.value = false
    }
  }

  const init = () => fetchList()

  const search = async (params: AlertRuleQuery) => {
    searchParams.value = params
    pageNum.value = 1
    await fetchList()
  }

  const resetSearch = async () => {
    searchParams.value = {}
    pageNum.value = 1
    await fetchList()
  }

  const changePage = async (page: number) => {
    pageNum.value = page
    await fetchList()
  }

  const changePageSize = async (size: number) => {
    pageSize.value = size
    pageNum.value = 1
    await fetchList()
  }

  const openCreateForm = () => {
    formMode.value = 'create'
    currentRule.value = null
    formVisible.value = true
  }

  const openEditForm = async (id: number) => {
    try {
      const res = await alertRuleApi.getDetail(id)
      if (res.code === 'SUCCESS' && res.data) {
        formMode.value = 'edit'
        currentRule.value = res.data
        formVisible.value = true
      }
    } catch (e) {
      ElMessage.error('获取规则详情失败')
    }
  }

  const closeForm = () => {
    formVisible.value = false
    currentRule.value = null
  }

  const submitForm = async (data: AlertRuleCreateDTO | AlertRuleUpdateDTO) => {
    try {
      if (formMode.value === 'create') {
        const res = await alertRuleApi.create(data as AlertRuleCreateDTO)
        if (res.code === 'SUCCESS') {
          ElMessage.success('创建成功')
          closeForm()
          await fetchList()
        } else {
          ElMessage.error(res.message || '创建失败')
        }
      } else if (currentRule.value) {
        const res = await alertRuleApi.update(currentRule.value.id, data as AlertRuleUpdateDTO)
        if (res.code === 'SUCCESS') {
          ElMessage.success('更新成功')
          closeForm()
          await fetchList()
        } else {
          ElMessage.error(res.message || '更新失败')
        }
      }
    } catch (e) {
      // Error displayed by interceptor
    }
  }

  const toggleEnabled = async (id: number) => {
    try {
      const res = await alertRuleApi.toggleEnabled(id)
      if (res.code === 'SUCCESS') {
        ElMessage.success('状态切换成功')
        await fetchList()
      } else {
        ElMessage.error(res.message || '状态切换失败')
      }
    } catch (e) {
      // Error displayed by interceptor
    }
  }

  const deleteRule = async (id: number) => {
    try {
      await ElMessageBox({
        title: '删除确认',
        message: renderConfirmMessage('删除告警规则', '确定要删除该告警规则吗？删除后不可恢复。'),
        customClass: 'alert-message-box',
        showClose: false,
        closeOnClickModal: false,
        closeOnPressEscape: true,
        showCancelButton: true,
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
      })
      const res = await alertRuleApi.deleteRule(id)
      if (res.code === 'SUCCESS') {
        ElMessage.success('删除成功')
        await fetchList()
      } else {
        ElMessage.error(res.message || '删除失败')
      }
    } catch {
      // 用户取消
    }
  }

  const openDetail = async (id: number) => {
    try {
      const res = await alertRuleApi.getDetail(id)
      if (res.code === 'SUCCESS' && res.data) {
        detailRule.value = res.data
        detailVisible.value = true
      }
    } catch (e) {
      ElMessage.error('获取规则详情失败')
    }
  }

  const closeDetail = () => {
    detailVisible.value = false
    detailRule.value = null
  }

  return {
    list, total, pageNum, pageSize, loading, searchParams,
    formVisible, formMode, currentRule, detailVisible, detailRule,
    fetchList, init, search, resetSearch, changePage, changePageSize,
    openCreateForm, openEditForm, closeForm, submitForm,
    toggleEnabled, deleteRule, openDetail, closeDetail,
  }
})
