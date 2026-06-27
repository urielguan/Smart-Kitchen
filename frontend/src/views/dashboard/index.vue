<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { useRouter, type RouteLocationRaw } from 'vue-router'
import { useAlertStore } from '@/stores/modules/alert'
import { useUserStore } from '@/stores/modules/user'
import orgApi from '@/api/modules/org'
import StatCard from '@/components/common/StatCard.vue'
import {
  dashboardQuickRangeOptions,
  loadRegulatoryDashboardData
} from '@/providers/regulatory-dashboard'
import type { OrgTreeNode } from '@/types/org'
import type {
  RegulatoryApiSubscription,
  RegulatoryBarItem,
  RegulatoryDistributionItem,
  RegulatoryDomainSection,
  RegulatoryExternalShare,
  RegulatoryHeatCard,
  RegulatoryOverviewMetric,
  RegulatoryQualityMetric,
  RegulatoryQuickRange,
  RegulatoryReportTemplate,
  RegulatoryRiskEvent,
  RegulatoryRiskLevel,
  RegulatoryStatusType,
  RegulatoryTrendPoint
} from '@/types/dashboard'

echarts.use([BarChart, LineChart, PieChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

interface FocusState {
  title: string
  source: string
  keyword?: string
}

interface RiskEventActionLog {
  id: string
  action: string
  detail: string
  operator: string
  time: string
  tone: 'primary' | 'warning' | 'success' | 'info'
}

const ALL_ORGANIZATIONS_LABEL = '全部组织'
const ALL_CANTEENS_LABEL = '全部食堂'
const ALL_AREAS_LABEL = '全部区域'

interface AssignFormState {
  assignee: string
  deadline: string
  note: string
}

interface RectificationFormState {
  title: string
  level: '紧急' | '高' | '中'
  assignee: string
  deadline: string
  measure: string
}

const organization = ref(ALL_ORGANIZATIONS_LABEL)
const canteen = ref(ALL_CANTEENS_LABEL)
const area = ref(ALL_AREAS_LABEL)
const quickRange = ref<RegulatoryQuickRange>('today')
const dateRange = ref<[Date, Date] | null>(null)
const autoRefresh = ref(true)
const snapshotAt = ref('2026-06-01 14:30:00')
const lastUpdatedAt = ref('2026-06-01 14:30:05')
const currentFocus = ref<FocusState | null>(null)
const selectedEvent = ref<RegulatoryRiskEvent | null>(null)
const detailDrawerVisible = ref(false)
const router = useRouter()
const alertStore = useAlertStore()
const userStore = useUserStore()
const quickRangeOptions = dashboardQuickRangeOptions
const organizationOptions = ref<string[]>([ALL_ORGANIZATIONS_LABEL])
const canteenOptions = ref<string[]>([ALL_CANTEENS_LABEL])
const areaOptions = ref<string[]>([ALL_AREAS_LABEL])
const quickRangeLabelMap: Record<RegulatoryQuickRange, string> = {
  today: '今日',
  '7d': '近7日',
  '30d': '近30日'
}

const formatDateValue = (date: Date) => {
  const pad = (value: number) => `${value}`.padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

const resolveDashboardDateRange = () => {
  if (dateRange.value?.length === 2) {
    return {
      startDate: formatDateValue(dateRange.value[0]),
      endDate: formatDateValue(dateRange.value[1])
    }
  }

  const end = new Date()
  const start = new Date(end)
  const offset = quickRange.value === 'today' ? 0 : quickRange.value === '7d' ? 6 : 29
  start.setDate(start.getDate() - offset)

  return {
    startDate: formatDateValue(start),
    endDate: formatDateValue(end)
  }
}

const buildDashboardRouteQuery = (query: Record<string, string | undefined> = {}) => {
  const { startDate, endDate } = resolveDashboardDateRange()
  return {
    from: 'dashboard',
    quickRange: quickRange.value,
    organization: organization.value !== ALL_ORGANIZATIONS_LABEL ? organization.value : undefined,
    canteen: canteen.value !== ALL_CANTEENS_LABEL ? canteen.value : undefined,
    area: area.value !== ALL_AREAS_LABEL ? area.value : undefined,
    startDate,
    endDate,
    ...query
  }
}

const overviewMetrics = ref<RegulatoryOverviewMetric[]>([])
const domainSections = ref<RegulatoryDomainSection[]>([])
const riskEvents = ref<RegulatoryRiskEvent[]>([])
const trendSeries = ref<RegulatoryTrendPoint[]>([])
const alarmDistribution = ref<RegulatoryDistributionItem[]>([])
const executionSeries = ref<RegulatoryBarItem[]>([])
const serviceQuality = ref<RegulatoryQualityMetric[]>([])
const heatCards = ref<RegulatoryHeatCard[]>([])
const reportTemplates = ref<RegulatoryReportTemplate[]>([])
const externalShares = ref<RegulatoryExternalShare[]>([])
const apiSubscriptions = ref<RegulatoryApiSubscription[]>([])
const currentDutyOwner = '监管值班经理'
const dashboardLoadError = ref('')
const riskEventLogs = ref<Record<string, RiskEventActionLog[]>>({})
const assignDialogVisible = ref(false)
const rectifyDialogVisible = ref(false)
const assignFormRef = ref<FormInstance>()
const rectifyFormRef = ref<FormInstance>()
const assigneeOptions = ['监管值班经理', '仓储主管', '食安专员', '采购经理', '后厨经理']
const assignForm = ref<AssignFormState>({
  assignee: '',
  deadline: '',
  note: ''
})
const rectifyForm = ref<RectificationFormState>({
  title: '',
  level: '高',
  assignee: '',
  deadline: '',
  measure: ''
})
const assignFormRules: FormRules<AssignFormState> = {
  assignee: [{ required: true, message: '请选择责任人', trigger: 'change' }],
  deadline: [{ required: true, message: '请选择处理时限', trigger: 'change' }]
}
const rectifyFormRules: FormRules<RectificationFormState> = {
  title: [{ required: true, message: '请输入工单标题', trigger: 'blur' }],
  assignee: [{ required: true, message: '请选择整改负责人', trigger: 'change' }],
  deadline: [{ required: true, message: '请选择整改时限', trigger: 'change' }],
  measure: [{ required: true, message: '请输入整改措施', trigger: 'blur' }]
}

const filteredEvents = computed(() => {
  return riskEvents.value.filter((event) => {
    const canteenMatch = canteen.value === ALL_CANTEENS_LABEL || event.location.includes(canteen.value)
    const areaMatch = area.value === ALL_AREAS_LABEL || event.location.includes(area.value)
    const focusMatch = !currentFocus.value?.keyword
      || event.title.includes(currentFocus.value.keyword)
      || event.type.includes(currentFocus.value.keyword)
      || event.sourceModule.includes(currentFocus.value.keyword)
    return canteenMatch && areaMatch && focusMatch
  })
})

const topBannerMessage = computed(() => {
  if (dashboardLoadError.value) {
    return `监管看板远端数据加载失败，当前展示降级数据。原因：${dashboardLoadError.value}`
  }
  const urgentCount = riskEvents.value.filter((item) => item.level === '紧急').length
  const overtimeCount = riskEvents.value.filter((item) => item.overtime).length
  return `当前快照存在 ${urgentCount} 条紧急风险事件，${overtimeCount} 条超时待处理事项，请优先处置。`
})
const activeQuickRangeLabel = computed(() => quickRangeLabelMap[quickRange.value])
const hasAlarmDistributionData = computed(() => alarmDistribution.value.some((item) => item.value > 0))
const hasExecutionSeriesData = computed(() => executionSeries.value.some((item) => item.morningCheck > 0 || item.cooking > 0))
const hasTrendSeriesData = computed(() => trendSeries.value.some((item) => item.alarm > 0 || item.review > 0))
const hasHeatCardData = computed(() => heatCards.value.length > 0)
const hasReportTemplateData = computed(() => reportTemplates.value.length > 0)
const hasExternalShareData = computed(() => externalShares.value.length > 0)
const hasApiSubscriptionData = computed(() => apiSubscriptions.value.length > 0)
const pieChartSubtitle = computed(() => hasAlarmDistributionData.value
  ? `基于 ${activeQuickRangeLabel.value} 真实告警等级数据聚合`
  : `当前 ${activeQuickRangeLabel.value} 暂无可展示的真实告警等级数据`
)
const pieChartEmptyDescription = computed(() => `当前 ${activeQuickRangeLabel.value} 暂无真实告警等级分布数据`)
const barChartTitle = computed(() => quickRange.value === 'today' ? '当日餐次晨检合格率与烹饪达标率' : `${activeQuickRangeLabel.value}晨检合格率与烹饪达标率`)
const barChartSubtitle = computed(() => {
  if (quickRange.value === 'today') {
    return hasExecutionSeriesData.value
      ? '按早餐 / 午餐 / 晚餐查看晨检记录与测温记录的真实达标情况'
      : '当前餐次暂无晨检记录或测温记录'
  }
  return hasExecutionSeriesData.value
    ? `按 ${activeQuickRangeLabel.value} 时间维度查看晨检记录与测温记录的真实达标数据`
    : `当前 ${activeQuickRangeLabel.value} 暂无晨检记录或测温记录`
})
const barChartEmptyDescription = computed(() => `当前 ${activeQuickRangeLabel.value} 暂无晨检记录或测温达标统计`)
const lineChartTitle = computed(() => `${activeQuickRangeLabel.value}告警事件与评价记录趋势`)
const lineChartSubtitle = computed(() => hasTrendSeriesData.value
  ? `按 ${activeQuickRangeLabel.value} 查看 device_alert 与 sys_meal_review 的真实变化`
  : `当前 ${activeQuickRangeLabel.value} 暂无 device_alert 或评价记录趋势数据`
)
const lineChartEmptyDescription = computed(() => `当前 ${activeQuickRangeLabel.value} 暂无告警事件或评价记录趋势`)
const heatCardSubtitle = computed(() => hasHeatCardData.value
  ? '联合设备告警、留样异常、库存风险定位重点区域'
  : '当前暂无可识别的高风险区域'
)
const heatCardEmptyDescription = computed(() => `当前 ${activeQuickRangeLabel.value} 暂无可识别的风险热力区域`)
const riskEventEmptyDescription = computed(() => {
  if (currentFocus.value) {
    return '当前联动条件下暂无匹配的真实风险事件'
  }
  if (canteen.value !== ALL_CANTEENS_LABEL || area.value !== ALL_AREAS_LABEL) {
    return '当前筛选条件下暂无真实风险事件'
  }
  return '当前快照暂无待跟进的真实风险事件'
})

const flattenOrgTree = (nodes: OrgTreeNode[]): OrgTreeNode[] => {
  const result: OrgTreeNode[] = []
  const walk = (items: OrgTreeNode[]) => {
    items.forEach((item) => {
      result.push(item)
      if (item.children?.length) {
        walk(item.children)
      }
    })
  }
  walk(nodes)
  return result
}

const extractAreaName = (location: string) => {
  const segments = location
    .split('/')
    .map((item) => item.trim())
    .filter(Boolean)
  if (segments.length >= 2) {
    return segments[1]
  }
  if (segments.length === 1) {
    return segments[0]
  }
  return ''
}

const syncAreaOptions = (events: RegulatoryRiskEvent[]) => {
  const areas = new Set<string>([ALL_AREAS_LABEL])
  if (area.value !== ALL_AREAS_LABEL) {
    areas.add(area.value)
  }
  events
    .map((event) => extractAreaName(event.location))
    .filter(Boolean)
    .forEach((item) => areas.add(item))
  areaOptions.value = Array.from(areas)
}

const loadOrganizationOptions = async () => {
  try {
    const response = await orgApi.getTree({
      status: 'active',
      includeChildren: true
    })
    const nodes = response.data || []
    const flatNodes = flattenOrgTree(nodes)
    const orgNames = Array.from(new Set(flatNodes.map((item) => item.orgName).filter(Boolean)))
    const canteenNames = Array.from(new Set(
      flatNodes
        .filter((item) => item.orgType === 'canteen')
        .map((item) => item.orgName)
        .filter(Boolean)
    ))

    organizationOptions.value = [ALL_ORGANIZATIONS_LABEL, ...orgNames]
    canteenOptions.value = [ALL_CANTEENS_LABEL, ...canteenNames]

    if (!organizationOptions.value.includes(organization.value)) {
      organization.value = userStore.userInfo?.orgName && organizationOptions.value.includes(userStore.userInfo.orgName)
        ? userStore.userInfo.orgName
        : ALL_ORGANIZATIONS_LABEL
    }
    if (!canteenOptions.value.includes(canteen.value)) {
      canteen.value = ALL_CANTEENS_LABEL
    }
  } catch (error) {
    console.warn('加载真实组织树失败，回退默认筛选项', error)
    organizationOptions.value = [ALL_ORGANIZATIONS_LABEL]
    canteenOptions.value = [ALL_CANTEENS_LABEL]
  }
}

const pieChartRef = ref<HTMLDivElement>()
const barChartRef = ref<HTMLDivElement>()
const lineChartRef = ref<HTMLDivElement>()

let pieChart: echarts.ECharts | null = null
let barChart: echarts.ECharts | null = null
let lineChart: echarts.ECharts | null = null
let autoRefreshTimer: number | null = null

const statusTagTypeMap: Record<RegulatoryStatusType, 'success' | 'warning' | 'danger' | 'info'> = {
  正常: 'success',
  预警: 'warning',
  异常: 'danger',
  紧急: 'danger'
}

const statusClassMap: Record<RegulatoryStatusType, string> = {
  正常: 'is-normal',
  预警: 'is-warning',
  异常: 'is-danger',
  紧急: 'is-urgent'
}

const getStatusTagType = (status: RegulatoryStatusType) => statusTagTypeMap[status]
const getStatusClass = (status: RegulatoryStatusType) => statusClassMap[status]
const getRiskLevelTagType = (level: RegulatoryRiskLevel): 'success' | 'warning' | 'danger' | 'info' => {
  if (level === '紧急') return 'danger'
  if (level === '严重') return 'warning'
  return 'info'
}
const getRiskLevelClass = (level: RegulatoryRiskLevel): string => {
  if (level === '紧急') return 'is-urgent'
  if (level === '严重') return 'is-danger'
  return 'is-warning'
}

const setFocus = (title: string, source: string, keyword?: string) => {
  currentFocus.value = { title, source, keyword }
}

const clearFocus = () => {
  currentFocus.value = null
}

const getOverviewMetricRoute = (metric: RegulatoryOverviewMetric): RouteLocationRaw | null => {
  switch (metric.id) {
    case 'diner':
    case 'recipe':
      return {
        name: 'Plan',
        query: buildDashboardRouteQuery({
          metric: metric.id
        })
      }
    case 'cook':
      return {
        name: 'Cook',
        query: buildDashboardRouteQuery({
          metric: 'cook-temperature',
          autoOpen: '1'
        })
      }
    case 'sample':
      return {
        name: 'Sample',
        query: buildDashboardRouteQuery({
          metric: 'sample-compliance',
          autoOpen: '1'
        })
      }
    case 'morning':
      return {
        name: 'MorningCheck',
        query: buildDashboardRouteQuery({
          metric: 'morning-check',
          tab: 'completed',
          autoOpen: '1'
        })
      }
    case 'device':
      return {
        name: 'Device',
        query: buildDashboardRouteQuery({
          metric: 'device-online-rate',
          autoOpen: '1'
        })
      }
    case 'alarm':
      return {
        name: 'Alert',
        query: buildDashboardRouteQuery({
          metric: 'pending-alerts',
          tab: 'alerts',
          autoOpen: '1'
        })
      }
    case 'comment':
      return {
        name: 'Evaluation',
        query: buildDashboardRouteQuery({
          metric: 'meal-review',
          tab: 'review'
        })
      }
    default:
      return null
  }
}

const handleOverviewMetricClick = async (metric: RegulatoryOverviewMetric) => {
  const routeTarget = getOverviewMetricRoute(metric)
  if (routeTarget) {
    await router.push(routeTarget)
    return
  }
  setFocus(metric.title, '指标卡穿透', metric.source)
}

const getDomainMetricRoute = (
  section: RegulatoryDomainSection,
  metric: RegulatoryDomainSection['metrics'][number]
): RouteLocationRaw | null => {
  if (section.title !== '食品安全专项监管') {
    if (section.title === '物资与库存监管') {
      if (metric.name === '库存预警物料') {
        return {
          name: 'Inventory',
          query: buildDashboardRouteQuery({
            metric: 'inventory-low-stock',
            stockStatus: 'low',
            autoOpen: '1'
          })
        }
      }

      if (metric.name === '过期物料统计') {
        return {
          name: 'Inventory',
          query: buildDashboardRouteQuery({
            metric: 'inventory-expired',
            stockStatus: 'expired',
            shelfLifeLevel: 'expired',
            autoOpen: '1'
          })
        }
      }

      if (metric.name === '出入库台账完整性') {
        return {
          name: 'Inbound',
          query: buildDashboardRouteQuery({
            metric: 'inbound-ledger',
            sourceType: 'purchase',
            autoOpen: '1'
          })
        }
      }

      if (metric.name === '食材采购溯源覆盖率') {
        return {
          name: 'Purchase',
          query: buildDashboardRouteQuery({
            metric: 'purchase-traceability'
          })
        }
      }
    }

    if (section.title === '告警与整改监管') {
      if (metric.name === '未处理告警') {
        return {
          name: 'Alert',
          query: buildDashboardRouteQuery({
            metric: 'pending-alerts',
            tab: 'alerts',
            status: 'pending',
            alertLevel: 'critical',
            autoOpen: '1'
          })
        }
      }

      if (metric.name === '超时未处理工单') {
        return {
          name: 'Alert',
          query: buildDashboardRouteQuery({
            metric: 'overtime-dispatches',
            tab: 'dispatches',
            overdue: '1',
            autoOpen: '1'
          })
        }
      }

      if (metric.name === '整改完成率') {
        return {
          name: 'Alert',
          query: buildDashboardRouteQuery({
            metric: 'rectification-completed',
            tab: 'dispatches',
            status: 'completed',
            autoOpen: '1'
          })
        }
      }

      if (metric.name === '复查通过率') {
        return {
          name: 'Alert',
          query: buildDashboardRouteQuery({
            metric: 'review-passed',
            tab: 'dispatches',
            status: 'reviewed',
            autoOpen: '1'
          })
        }
      }
    }

    return null
  }

  if (metric.name === '晨检完成率') {
    return {
      name: 'MorningCheck',
      query: buildDashboardRouteQuery({
        metric: 'morning-check',
        tab: 'completed',
        status: 'completed_abnormal',
        checkResult: 'fail',
        autoOpen: '1'
      })
    }
  }

  if (metric.name === '留样合规率') {
    return {
      name: 'Sample',
      query: buildDashboardRouteQuery({
        metric: 'sample-compliance',
        status: 'pending_sample',
        autoOpen: '1'
      })
    }
  }

  if (metric.name === '烹饪温度达标率') {
    return {
      name: 'Cook',
      query: buildDashboardRouteQuery({
        metric: 'cook-temperature',
        temperatureAbnormal: '1',
        autoOpen: '1'
      })
    }
  }

  if (metric.name === 'AI违规识别趋势') {
    return {
      name: 'Violation',
      query: buildDashboardRouteQuery({
        metric: 'ai-violation',
        status: 'pending',
        alertLevel: 'urgent',
        autoOpen: '1'
      })
    }
  }

  return null
}

const getQualityMetricRoute = (metric: RegulatoryQualityMetric): RouteLocationRaw | null => {
  switch (metric.label) {
    case '违规率':
      return {
        name: 'Violation',
        query: buildDashboardRouteQuery({
          metric: 'ai-violation',
          status: 'pending',
          autoOpen: '1'
        })
      }
    case '溯源响应时长':
      return {
        name: 'Sample',
        query: buildDashboardRouteQuery({
          metric: 'sample-compliance',
          autoOpen: '1'
        })
      }
    case '食材浪费率':
      return {
        name: 'Outbound',
        query: buildDashboardRouteQuery({
          metric: 'waste-rate'
        })
      }
    case '就餐满意度':
      return {
        name: 'Evaluation',
        query: buildDashboardRouteQuery({
          metric: 'meal-review'
        })
      }
    default:
      return null
  }
}

const handleQualityMetricClick = async (metric: RegulatoryQualityMetric) => {
  const routeTarget = getQualityMetricRoute(metric)
  if (routeTarget) {
    await router.push(routeTarget)
    return
  }
  setFocus(metric.label, '经营与服务质量', metric.label)
}

const getHeatCardRoute = (item: RegulatoryHeatCard): RouteLocationRaw | null => {
  if (/库|仓/.test(item.name)) {
    return {
      name: 'Inventory',
      query: buildDashboardRouteQuery({
        keyword: item.name,
        metric: item.status === '紧急' ? 'inventory-expired' : 'inventory-low-stock',
        autoOpen: '1'
      })
    }
  }

  if (/留样/.test(item.name)) {
    return {
      name: 'Sample',
      query: buildDashboardRouteQuery({
        metric: 'sample-compliance',
        autoOpen: '1'
      })
    }
  }

  if (/后厨|热厨|面点|操作台/.test(item.name)) {
    return {
      name: 'Violation',
      query: buildDashboardRouteQuery({
        metric: 'ai-violation',
        autoOpen: '1'
      })
    }
  }

  return {
    name: 'Alert',
    query: buildDashboardRouteQuery({
      metric: 'pending-alerts',
      tab: 'alerts',
      autoOpen: '1'
    })
  }
}

const handleHeatCardClick = async (item: RegulatoryHeatCard) => {
  const routeTarget = getHeatCardRoute(item)
  if (routeTarget) {
    await router.push(routeTarget)
    return
  }
  setFocus(item.name, '区域风险热力', item.name)
}

const handleDomainMetricClick = async (
  section: RegulatoryDomainSection,
  metric: RegulatoryDomainSection['metrics'][number]
) => {
  const routeTarget = getDomainMetricRoute(section, metric)
  if (routeTarget) {
    await router.push(routeTarget)
    return
  }

  setFocus(metric.name, section.title, metric.name)
}

const formatActionTime = () => {
  const now = new Date()
  const pad = (value: number) => `${value}`.padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
}

const appendRiskEventLog = (
  eventId: string,
  action: string,
  detail: string,
  tone: RiskEventActionLog['tone'] = 'info',
  operator = currentDutyOwner
) => {
  const currentLogs = riskEventLogs.value[eventId] || []
  riskEventLogs.value = {
    ...riskEventLogs.value,
    [eventId]: [
      {
        id: `${eventId}-${Date.now()}-${currentLogs.length}`,
        action,
        detail,
        operator,
        time: formatActionTime(),
        tone
      },
      ...currentLogs
    ]
  }
}

const ensureRiskEventLogs = (event: RegulatoryRiskEvent) => {
  if (riskEventLogs.value[event.id]?.length) {
    return
  }
  appendRiskEventLog(event.id, '事件接入', `风险事件由 ${event.sourceModule} 推送至监管看板`, 'info', '系统')
}

const openEventDetail = (event: RegulatoryRiskEvent) => {
  ensureRiskEventLogs(event)
  selectedEvent.value = event
  detailDrawerVisible.value = true
}

const patchRiskEvent = (eventId: string, updater: (event: RegulatoryRiskEvent) => RegulatoryRiskEvent) => {
  riskEvents.value = riskEvents.value.map((event) => event.id === eventId ? updater(event) : event)
  if (selectedEvent.value?.id === eventId) {
    const latestEvent = riskEvents.value.find((event) => event.id === eventId) || null
    selectedEvent.value = latestEvent
  }
}

const getRiskEventDrillDownLabel = (event: RegulatoryRiskEvent) => {
  if (event.drillDown?.type === 'purchase') {
    return '穿透采购订单明细'
  }
  if (event.drillDown?.type === 'sample') {
    return '穿透留样明细'
  }
  if (event.drillDown?.type === 'alert') {
    return '进入关联告警项'
  }
  if (event.drillDown?.type === 'dispatch') {
    return '进入整改工单队列'
  }
  return '暂无可用下钻'
}

const navigateToRiskEventDrillDown = async (event: RegulatoryRiskEvent) => {
  if (!event.drillDown) {
    ElMessage.warning('当前风险事件暂未配置可用下钻链路')
    return
  }

  if (event.drillDown.type === 'purchase') {
    await router.push({
      name: 'Purchase',
      query: buildDashboardRouteQuery({
        traceBatchId: event.drillDown.traceBatchId || event.traceBatchId,
        autoOpen: '1'
      })
    })
    detailDrawerVisible.value = false
    return
  }

  if (event.drillDown.type === 'sample') {
    await router.push({
      name: 'Sample',
      query: buildDashboardRouteQuery({
        recordId: event.drillDown.recordId ? String(event.drillDown.recordId) : undefined,
        traceBatchId: event.drillDown.traceBatchId || event.traceBatchId
      })
    })
    detailDrawerVisible.value = false
    return
  }

  if (event.drillDown.type === 'alert' || event.drillDown.type === 'dispatch') {
    await router.push({
      name: 'Alert',
      query: buildDashboardRouteQuery({
        alertId: event.drillDown.alertId ? String(event.drillDown.alertId) : undefined,
        dispatchId: event.drillDown.dispatchId ? String(event.drillDown.dispatchId) : undefined,
        dispatchNo: event.drillDown.dispatchNo,
        metric: event.drillDown.metric,
        tab: event.drillDown.tab,
        status: event.drillDown.status,
        alertLevel: event.drillDown.alertLevel,
        overdue: event.drillDown.overdue ? '1' : undefined,
        autoOpen: '1'
      })
    })
    detailDrawerVisible.value = false
  }
}

const getRiskEventAdvice = (event: RegulatoryRiskEvent) => {
  if (event.drillDown?.type === 'purchase') {
    return '建议优先核查采购订单溯源信息、批次锁定状态与上下游入库流转是否一致。'
  }
  if (event.drillDown?.type === 'sample') {
    return '建议优先核查留样登记、销样时效与追溯链是否完整，确认是否存在闭环缺口。'
  }
  if (event.drillDown?.type === 'dispatch') {
    return '建议优先进入整改工单队列核查派单、处理进度与 SLA 超时情况，再决定是否升级督办。'
  }
  if (event.drillDown?.type === 'alert') {
    return '建议优先进入关联告警项核实证据、派单状态与处置闭环，再决定是否继续升级整改。'
  }
  if (event.sourceModule.includes('AI')) {
    return '建议先复核识别截图或视频证据，再决定是否进入人工复查或工单处置。'
  }
  return '建议先核对事件来源记录、责任人处理状态与跨端一致性，再决定下钻路径。'
}

const getRiskEventStatusSummary = (event: RegulatoryRiskEvent) => {
  if (event.status === '已闭环') {
    return '当前事件已闭环，可用于复盘追溯与过程审计。'
  }
  if (event.overtime) {
    return '当前事件存在超时风险，建议优先进入主链路核查并补齐处置动作。'
  }
  return '当前事件仍在处置过程中，建议先确认责任归属与业务链路状态。'
}

const getRiskEventActionTone = (event: RegulatoryRiskEvent) => {
  if (event.status === '已闭环') {
    return '已完成闭环，可继续查看上下游链路进行复盘。'
  }
  if (event.status === '已挂起') {
    return '当前事件已挂起，建议补充证据或等待外部确认后再恢复。'
  }
  if (event.overtime) {
    return '当前事件已触发超时，建议先认领并立即进入整改。'
  }
  return '当前事件仍可继续推进，请优先执行主处置动作。'
}

const selectedEventLogs = computed(() => {
  if (!selectedEvent.value) {
    return []
  }
  return riskEventLogs.value[selectedEvent.value.id] || []
})

const resetAssignForm = () => {
  assignForm.value = {
    assignee: selectedEvent.value?.owner || '',
    deadline: '',
    note: ''
  }
  assignFormRef.value?.clearValidate()
}

const resetRectifyForm = () => {
  rectifyForm.value = {
    title: selectedEvent.value ? `${selectedEvent.value.title}整改工单` : '',
    level: selectedEvent.value?.level === '紧急' ? '紧急' : selectedEvent.value?.level === '严重' ? '高' : '中',
    assignee: selectedEvent.value?.owner || '',
    deadline: '',
    measure: ''
  }
  rectifyFormRef.value?.clearValidate()
}

const getRectifyAssigneeOptions = async () => {
  if (!selectedEvent.value?.drillDown?.alertId) {
    return assigneeOptions
  }
  await alertStore.fetchDispatchEmployees()
  const handlerNames = alertStore.handlers.map((item) => item.name)
  return handlerNames.length ? handlerNames : assigneeOptions
}

const openAssignDialog = () => {
  if (!selectedEvent.value) return
  resetAssignForm()
  assignDialogVisible.value = true
}

const openRectifyDialog = () => {
  if (!selectedEvent.value) return
  resetRectifyForm()
  void getRectifyAssigneeOptions().then((options) => {
    if (options.length) {
      assigneeOptions.splice(0, assigneeOptions.length, ...options)
      if (!options.includes(rectifyForm.value.assignee)) {
        rectifyForm.value.assignee = options[0] || ''
      }
    }
    rectifyDialogVisible.value = true
  })
}

const claimRiskEvent = () => {
  if (!selectedEvent.value) return
  patchRiskEvent(selectedEvent.value.id, (event) => ({
    ...event,
    owner: currentDutyOwner,
    status: event.status === '待处理' ? '处理中' : event.status
  }))
  appendRiskEventLog(selectedEvent.value.id, '认领事件', `由 ${currentDutyOwner} 接手跟进该风险事件`, 'primary')
  ElMessage.success('已认领该风险事件')
}

const submitAssignForm = async () => {
  if (!assignFormRef.value || !selectedEvent.value) return
  await assignFormRef.value.validate(async (valid) => {
    if (!valid || !selectedEvent.value) return
    patchRiskEvent(selectedEvent.value.id, (event) => ({
      ...event,
      owner: assignForm.value.assignee,
      status: event.status === '待处理' ? '处理中' : event.status
    }))
    appendRiskEventLog(
      selectedEvent.value.id,
      '指派责任人',
      `已指派给 ${assignForm.value.assignee}，处理时限 ${assignForm.value.deadline}${assignForm.value.note ? `，备注：${assignForm.value.note}` : ''}`,
      'primary'
    )
    assignDialogVisible.value = false
    ElMessage.success('责任人已指派')
  })
}

const submitRectifyForm = async () => {
  if (!rectifyFormRef.value || !selectedEvent.value) return
  await rectifyFormRef.value.validate(async (valid) => {
    if (!valid || !selectedEvent.value) return
    const alertId = selectedEvent.value.drillDown?.alertId
    if (alertId) {
      await alertStore.fetchDispatchEmployees()
      const matchedHandler = alertStore.handlers.find((item) => item.name === rectifyForm.value.assignee)
      if (!matchedHandler) {
        ElMessage.warning('当前整改负责人不在告警派单处理人范围内，请重新选择')
        return
      }

      const dispatchResult = await alertStore.createDispatch(alertId, {
        dispatchType: 'manual',
        handlerId: matchedHandler.id,
        priority: rectifyForm.value.level === '紧急' ? 'high' : rectifyForm.value.level === '高' ? 'medium' : 'low',
        deadline: rectifyForm.value.deadline,
        remark: `${rectifyForm.value.title}；整改措施：${rectifyForm.value.measure}`
      })

      if (!dispatchResult) {
        ElMessage.error('整改工单创建失败')
        return
      }

      patchRiskEvent(selectedEvent.value.id, (event) => ({
        ...event,
        owner: rectifyForm.value.assignee,
        status: '处理中',
        overtime: false,
        drillDown: {
          ...(event.drillDown || {}),
          type: 'dispatch',
          alertId: dispatchResult.alertId,
          alertNo: dispatchResult.alertNo,
          dispatchId: dispatchResult.dispatchId,
          dispatchNo: dispatchResult.dispatchNo,
          metric: 'overtime-dispatches',
          tab: 'dispatches'
        }
      }))
      appendRiskEventLog(
        selectedEvent.value.id,
        '创建整改工单',
        `工单「${dispatchResult.dispatchNo}」已创建，关联告警 ${dispatchResult.alertNo}，指派 ${dispatchResult.handlerName}，整改时限 ${dispatchResult.deadline || rectifyForm.value.deadline}`,
        'warning'
      )
      appendRiskEventLog(
        selectedEvent.value.id,
        '整改措施',
        rectifyForm.value.measure,
        'info',
        dispatchResult.handlerName
      )
      rectifyDialogVisible.value = false
      ElMessage.success(`整改工单已创建：${dispatchResult.dispatchNo}`)
      return
    }

    patchRiskEvent(selectedEvent.value.id, (event) => ({
      ...event,
      owner: rectifyForm.value.assignee,
      status: '处理中',
      overtime: false
    }))
    appendRiskEventLog(
      selectedEvent.value.id,
      '创建整改工单',
      `工单「${rectifyForm.value.title}」已创建，指派 ${rectifyForm.value.assignee}，级别 ${rectifyForm.value.level}，整改时限 ${rectifyForm.value.deadline}`,
      'warning'
    )
    appendRiskEventLog(
      selectedEvent.value.id,
      '整改措施',
      rectifyForm.value.measure,
      'info',
      rectifyForm.value.assignee
    )
    rectifyDialogVisible.value = false
    ElMessage.success('整改工单已创建')
  })
}

const suspendRiskEvent = async () => {
  if (!selectedEvent.value) return
  await ElMessageBox.confirm('确认将该风险事件挂起？挂起后应等待补充证据或外部确认。', '挂起事件', {
    confirmButtonText: '确认挂起',
    cancelButtonText: '取消',
    type: 'warning'
  })
  patchRiskEvent(selectedEvent.value.id, (event) => ({
    ...event,
    status: '已挂起'
  }))
  appendRiskEventLog(selectedEvent.value.id, '挂起待核', '事件已挂起，等待补充证据或外部确认', 'info')
  ElMessage.success('已挂起该风险事件')
}

const closeRiskEvent = async () => {
  if (!selectedEvent.value) return
  await ElMessageBox.confirm('确认将该风险事件标记为已闭环？该操作用于模拟完成处置。', '闭环事件', {
    confirmButtonText: '确认闭环',
    cancelButtonText: '取消',
    type: 'warning'
  })
  patchRiskEvent(selectedEvent.value.id, (event) => ({
    ...event,
    status: '已闭环',
    overtime: false
  }))
  appendRiskEventLog(selectedEvent.value.id, '标记闭环', '事件处置完成，已转入复盘审计状态', 'success')
  ElMessage.success('该风险事件已闭环')
}

const getPieOption = () => ({
  tooltip: { trigger: 'item' as const },
  legend: { bottom: 0, textStyle: { color: '#5d6b82' } },
  series: [
    {
      name: '告警等级分布',
      type: 'pie' as const,
      radius: ['42%', '72%'],
      center: ['50%', '44%'],
      label: { formatter: '{b}\n{d}%' },
      data: alarmDistribution.value
    }
  ]
})

const resolveAlertLevelQuery = (name?: string) => {
  if (name === '紧急') return 'critical'
  if (name === '预警') return 'warning'
  if (name === '正常跟踪') return 'info'
  return undefined
}

const resolveMealTypeByLabel = (label?: string) => {
  if (label === '早餐') return 'breakfast'
  if (label === '午餐') return 'lunch'
  if (label === '晚餐') return 'dinner'
  return undefined
}

const getBarOption = () => ({
  tooltip: { trigger: 'axis' as const },
  legend: { top: 0 },
  grid: { left: 24, right: 12, top: 40, bottom: 24 },
  xAxis: {
    type: 'category' as const,
    data: executionSeries.value.map((item) => item.label)
  },
  yAxis: {
    type: 'value' as const,
    min: 0,
    max: 100
  },
  series: [
    {
      name: '晨检合格率',
      type: 'bar' as const,
      barMaxWidth: 24,
      data: executionSeries.value.map((item) => item.morningCheck),
      itemStyle: { color: '#409eff', borderRadius: [6, 6, 0, 0] }
    },
    {
      name: '烹饪达标率',
      type: 'bar' as const,
      barMaxWidth: 24,
      data: executionSeries.value.map((item) => item.cooking),
      itemStyle: { color: '#67c23a', borderRadius: [6, 6, 0, 0] }
    }
  ]
})

const getLineOption = () => ({
  tooltip: { trigger: 'axis' as const },
  legend: { top: 0 },
  grid: { left: 24, right: 12, top: 40, bottom: 24 },
  xAxis: {
    type: 'category' as const,
    data: trendSeries.value.map((item) => item.label)
  },
  yAxis: {
    type: 'value' as const
  },
  series: [
    {
      name: '告警事件数',
      type: 'line' as const,
      smooth: true,
      data: trendSeries.value.map((item) => item.alarm),
      itemStyle: { color: '#f56c6c' },
      lineStyle: { width: 3, color: '#f56c6c' }
    },
    {
      name: '评价记录数',
      type: 'line' as const,
      smooth: true,
      data: trendSeries.value.map((item) => item.review),
      itemStyle: { color: '#e6a23c' },
      lineStyle: { width: 3, color: '#e6a23c' }
    }
  ]
})

const updateCharts = () => {
  pieChart?.setOption(getPieOption())
  barChart?.setOption(getBarOption())
  lineChart?.setOption(getLineOption())
}

const ensureChartsInitialized = () => {
  if (pieChartRef.value && !pieChart) {
    pieChart = echarts.init(pieChartRef.value)
    pieChart.on('click', (params: { name?: string }) => {
      if (!params.name) return
      const routeTarget: RouteLocationRaw = {
        name: 'Alert',
        query: buildDashboardRouteQuery({
          metric: 'pending-alerts',
          tab: 'alerts',
          alertLevel: resolveAlertLevelQuery(params.name),
          autoOpen: '1'
        })
      }
      void router.push(routeTarget)
    })
  }

  if (barChartRef.value && !barChart) {
    barChart = echarts.init(barChartRef.value)
    barChart.on('click', (params: { seriesName?: string; name?: string }) => {
      if (!params.seriesName || !params.name) return
      if (params.seriesName.includes('晨检')) {
        void router.push({
          name: 'MorningCheck',
          query: buildDashboardRouteQuery({
            metric: 'morning-check',
            tab: 'completed',
            autoOpen: '1'
          })
        })
        return
      }
      void router.push({
        name: 'Cook',
        query: buildDashboardRouteQuery({
          metric: 'cook-temperature',
          mealType: resolveMealTypeByLabel(params.name),
          autoOpen: '1'
        })
      })
    })
  }

  if (lineChartRef.value && !lineChart) {
    lineChart = echarts.init(lineChartRef.value)
    lineChart.on('click', (params: { seriesName?: string; name?: string }) => {
      if (!params.seriesName || !params.name) return
      if (params.seriesName.includes('告警')) {
        void router.push({
          name: 'Alert',
          query: buildDashboardRouteQuery({
            metric: 'pending-alerts',
            tab: 'alerts',
            autoOpen: '1'
          })
        })
        return
      }
      void router.push({
        name: 'Evaluation',
        query: buildDashboardRouteQuery({
          metric: 'meal-review'
        })
      })
    })
  }
}

const fetchDashboardData = async () => {
  try {
    const data = await loadRegulatoryDashboardData({
      quickRange: quickRange.value,
      organization: organization.value !== ALL_ORGANIZATIONS_LABEL ? organization.value : undefined,
      canteen: canteen.value !== ALL_CANTEENS_LABEL ? canteen.value : undefined,
      area: area.value !== ALL_AREAS_LABEL ? area.value : undefined,
      dateRange: dateRange.value
    })

    snapshotAt.value = data.snapshotAt
    lastUpdatedAt.value = data.lastUpdatedAt
    overviewMetrics.value = data.overviewMetrics
    domainSections.value = data.domainSections
    riskEvents.value = data.riskEvents
    trendSeries.value = data.trendSeries
    alarmDistribution.value = data.alarmDistribution
    executionSeries.value = data.executionSeries
    serviceQuality.value = data.serviceQuality
    heatCards.value = data.heatCards
    reportTemplates.value = data.reportTemplates
    externalShares.value = data.externalShares
    apiSubscriptions.value = data.apiSubscriptions
    syncAreaOptions(data.riskEvents)
    dashboardLoadError.value = ''
  } catch (error) {
    dashboardLoadError.value = error instanceof Error ? error.message : '监管看板数据加载失败'
    ElMessage.warning('监管看板数据加载失败，已保留当前页面内容')
  }
}

const initCharts = async () => {
  await nextTick()
  ensureChartsInitialized()
  updateCharts()
}

const resizeCharts = () => {
  pieChart?.resize()
  barChart?.resize()
  lineChart?.resize()
}

const refreshDashboard = async () => {
  await fetchDashboardData()
  await nextTick()
  ensureChartsInitialized()
  updateCharts()
}

const startAutoRefresh = () => {
  if (autoRefreshTimer) {
    window.clearInterval(autoRefreshTimer)
  }
  if (!autoRefresh.value) return
  autoRefreshTimer = window.setInterval(() => {
    void refreshDashboard()
  }, 5000)
}

watch([quickRange, organization, canteen, area, dateRange], async () => {
  try {
    await refreshDashboard()
  } catch {
  }
})

watch(autoRefresh, () => {
  startAutoRefresh()
})

onMounted(async () => {
  try {
    await loadOrganizationOptions()
    await fetchDashboardData()
    await initCharts()
    startAutoRefresh()
    window.addEventListener('resize', resizeCharts)
  } catch (error) {
    ElMessage.warning('监管看板初始化失败，请稍后刷新重试')
  }
})

onUnmounted(() => {
  if (autoRefreshTimer) {
    window.clearInterval(autoRefreshTimer)
  }
  window.removeEventListener('resize', resizeCharts)
  pieChart?.dispose()
  barChart?.dispose()
  lineChart?.dispose()
})
</script>

<template>
  <div class="dashboard-page">
    <el-alert
      type="error"
      :closable="false"
      show-icon
      class="risk-banner"
      :title="topBannerMessage"
    />

    <section class="toolbar-panel">
      <div class="toolbar-main">
        <div class="toolbar-filters">
          <el-select v-model="organization" placeholder="组织" size="large">
            <el-option v-for="item in organizationOptions" :key="item" :label="item" :value="item" />
          </el-select>
          <el-select v-model="canteen" placeholder="食堂" size="large">
            <el-option v-for="item in canteenOptions" :key="item" :label="item" :value="item" />
          </el-select>
          <el-select v-model="area" placeholder="区域" size="large">
            <el-option v-for="item in areaOptions" :key="item" :label="item" :value="item" />
          </el-select>
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            size="large"
          />
          <el-segmented v-model="quickRange" :options="quickRangeOptions" size="large" />
        </div>

        <div class="toolbar-actions">
          <div class="snapshot-block">
            <span class="snapshot-label">当前数据快照时点</span>
            <strong>{{ snapshotAt }}</strong>
            <span class="snapshot-sub">最后刷新 {{ lastUpdatedAt }}</span>
          </div>
          <div class="refresh-actions">
            <el-switch v-model="autoRefresh" active-text="5秒自动刷新" />
            <el-button type="primary" plain @click="refreshDashboard">实时刷新</el-button>
            <el-button v-if="currentFocus" @click="clearFocus">清除联动</el-button>
          </div>
        </div>
      </div>
      <div v-if="currentFocus" class="focus-bar">
        <el-tag type="warning" effect="dark">当前穿透</el-tag>
        <strong>{{ currentFocus.title }}</strong>
        <span>{{ currentFocus.source }}</span>
      </div>
    </section>

    <section class="overview-grid">
      <div
        v-for="metric in overviewMetrics"
        :key="metric.id"
        class="overview-item"
        :class="getStatusClass(metric.status)"
      >
        <StatCard
          :title="metric.title"
          :value="metric.value"
          :unit="metric.unit"
          :color="metric.status === '紧急' || metric.status === '异常' ? 'danger' : metric.status === '预警' ? 'warning' : 'success'"
          clickable
          @click="handleOverviewMetricClick(metric)"
        />
        <div class="overview-meta">
          <el-tag size="small" :type="getStatusTagType(metric.status)" effect="light">{{ metric.status }}</el-tag>
          <span>{{ metric.compare }}</span>
          <span class="source">{{ metric.source }}</span>
        </div>
      </div>
    </section>

    <section class="domain-grid">
      <el-card v-for="section in domainSections" :key="section.title" shadow="never" class="domain-card">
        <template #header>
          <div class="card-header">
            <div>
              <h3>{{ section.title }}</h3>
              <p>{{ section.subtitle }}</p>
            </div>
          </div>
        </template>
        <div class="domain-metrics">
          <div
            v-for="metric in section.metrics"
            :key="metric.name"
            class="domain-metric"
            :class="getStatusClass(metric.status)"
            @click="handleDomainMetricClick(section, metric)"
          >
            <div class="metric-top">
              <span class="metric-name">{{ metric.name }}</span>
              <el-tag size="small" :type="getStatusTagType(metric.status)" effect="plain">{{ metric.status }}</el-tag>
            </div>
            <div class="metric-value">{{ metric.value }}</div>
            <div class="metric-hint">{{ metric.hint }}</div>
          </div>
        </div>
      </el-card>
    </section>

    <section class="chart-grid">
      <el-card shadow="never" class="chart-card">
        <template #header>
          <div class="card-header">
            <div>
              <h3>告警等级分布</h3>
              <p>{{ pieChartSubtitle }}</p>
            </div>
          </div>
        </template>
        <div v-show="hasAlarmDistributionData" ref="pieChartRef" class="chart-box" />
        <el-empty v-if="!hasAlarmDistributionData" :description="pieChartEmptyDescription" />
      </el-card>

      <el-card shadow="never" class="chart-card">
        <template #header>
          <div class="card-header">
            <div>
              <h3>{{ barChartTitle }}</h3>
              <p>{{ barChartSubtitle }}</p>
            </div>
          </div>
        </template>
        <div v-show="hasExecutionSeriesData" ref="barChartRef" class="chart-box" />
        <el-empty v-if="!hasExecutionSeriesData" :description="barChartEmptyDescription" />
      </el-card>

      <el-card shadow="never" class="chart-card wide">
        <template #header>
          <div class="card-header">
            <div>
              <h3>{{ lineChartTitle }}</h3>
              <p>{{ lineChartSubtitle }}</p>
            </div>
          </div>
        </template>
        <div v-show="hasTrendSeriesData" ref="lineChartRef" class="chart-box" />
        <el-empty v-if="!hasTrendSeriesData" :description="lineChartEmptyDescription" />
      </el-card>
    </section>

    <section class="quality-grid">
      <el-card shadow="never" class="quality-card">
        <template #header>
          <div class="card-header">
            <div>
              <h3>经营与服务质量成效指标</h3>
              <p>基于 AI 告警、留样、出库和评价表的真实经营服务指标</p>
            </div>
          </div>
        </template>
        <div class="quality-list">
          <div
            v-for="item in serviceQuality"
            :key="item.label"
            class="quality-item"
            :class="getStatusClass(item.status)"
            @click="handleQualityMetricClick(item)"
          >
            <div class="quality-top">
              <span>{{ item.label }}</span>
              <el-tag size="small" :type="getStatusTagType(item.status)">{{ item.status }}</el-tag>
            </div>
            <div class="quality-value">{{ item.value }}</div>
            <div class="quality-bottom">
              <span>{{ item.compare }}</span>
              <span>{{ item.target }}</span>
            </div>
          </div>
        </div>
      </el-card>

      <el-card shadow="never" class="quality-card">
        <template #header>
          <div class="card-header">
            <div>
              <h3>区域风险热力 / 状态卡片</h3>
              <p>{{ heatCardSubtitle }}</p>
            </div>
          </div>
        </template>
        <div v-if="hasHeatCardData" class="heat-list">
          <div
            v-for="item in heatCards"
            :key="item.name"
            class="heat-item"
            :class="getStatusClass(item.status)"
            @click="handleHeatCardClick(item)"
          >
            <div class="heat-name">{{ item.name }}</div>
            <div class="heat-level">{{ item.level }}</div>
            <div class="heat-value">{{ item.value }}</div>
          </div>
        </div>
        <el-empty v-else :description="heatCardEmptyDescription" />
      </el-card>
    </section>

    <section class="detail-grid">
      <el-card shadow="never" class="detail-card">
        <template #header>
          <div class="card-header">
            <div>
              <h3>典型风险事件滚动列表</h3>
              <p>自动标红真实风险事件，支持按 trace_batch_id 追溯上下游链路</p>
            </div>
          </div>
        </template>
        <div v-if="filteredEvents.length" class="event-list">
          <div
            v-for="event in filteredEvents"
            :key="event.id"
            class="event-item clickable-card"
            :class="getRiskLevelClass(event.level)"
            @click="openEventDetail(event)"
          >
            <div class="event-top">
              <div class="event-title-group">
                <el-tag size="small" :type="getRiskLevelTagType(event.level)">{{ event.level }}</el-tag>
                <span class="event-type">{{ event.type }}</span>
                <strong class="event-title">{{ event.title }}</strong>
              </div>
              <el-tag size="small" :type="event.status === '已闭环' ? 'success' : event.status === '处理中' ? 'warning' : 'danger'">
                {{ event.status }}
              </el-tag>
            </div>
            <div class="event-meta">
              <span>位置：{{ event.location }}</span>
              <span>发生时间：{{ event.time }}</span>
            </div>
            <div class="event-meta">
              <span>trace_batch_id：{{ event.traceBatchId }}</span>
              <span>责任人：{{ event.owner }}</span>
              <span>来源模块：{{ event.sourceModule }}</span>
            </div>
            <div class="event-bottom">
              <div class="terminal-tags">
                <el-tag v-for="terminal in event.sourceTerminals" :key="terminal" size="small" effect="plain">{{ terminal }}</el-tag>
              </div>
              <div class="event-flags">
                <el-tag size="small" :type="event.consistency === '一致' ? 'success' : event.consistency === '待校验' ? 'warning' : 'danger'">
                  跨端一致性：{{ event.consistency }}
                </el-tag>
                <el-tag v-if="event.overtime" size="small" type="danger">超时待处理</el-tag>
              </div>
            </div>
          </div>
        </div>
        <el-empty v-else :description="riskEventEmptyDescription" />
      </el-card>

      <el-card shadow="never" class="detail-card">
        <template #header>
          <div class="card-header">
            <div>
              <h3>风险事件明细表</h3>
              <p>与卡片、图表使用统一筛选口径和统一快照时点</p>
            </div>
          </div>
        </template>
        <el-table :data="filteredEvents" stripe>
          <el-table-column prop="title" label="事件标题" min-width="220" />
          <el-table-column prop="location" label="发生位置" min-width="220" />
          <el-table-column prop="traceBatchId" label="trace_batch_id" min-width="180" />
          <el-table-column prop="sourceModule" label="来源模块" min-width="130" />
          <el-table-column prop="time" label="发生时间" min-width="170" />
          <el-table-column label="风险等级" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="getRiskLevelTagType(row.level)">{{ row.level }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === '已闭环' ? 'success' : row.status === '处理中' ? 'warning' : 'danger'">
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEventDetail(row)">查看详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card shadow="never" class="detail-card side-panel">
        <template #header>
          <div class="card-header">
            <div>
              <h3>监管动作区</h3>
              <p>报表模板、外部分享、OpenAPI 订阅骨架</p>
            </div>
          </div>
        </template>

        <div class="side-block">
          <div class="side-title">自定义报表模板</div>
          <div v-if="hasReportTemplateData" class="side-list">
            <div v-for="item in reportTemplates" :key="item.name" class="side-item">
              <strong>{{ item.name }}</strong>
              <span>{{ item.scope }}</span>
              <em>最近更新 {{ item.updatedAt }}</em>
            </div>
          </div>
          <el-empty v-else description="当前未配置真实报表模板" :image-size="72" />
        </div>

        <div class="side-block">
          <div class="side-title">外部分享</div>
          <div v-if="hasExternalShareData" class="side-list">
            <div v-for="item in externalShares" :key="item.target" class="side-item">
              <strong>{{ item.target }}</strong>
              <span>{{ item.mode }}</span>
              <em>失效时间 {{ item.expireAt }} · {{ item.status }}</em>
            </div>
          </div>
          <el-empty v-else description="当前未配置真实外部分享渠道" :image-size="72" />
        </div>

        <div class="side-block">
          <div class="side-title">OpenAPI 订阅</div>
          <div v-if="hasApiSubscriptionData" class="side-list">
            <div v-for="item in apiSubscriptions" :key="item.app" class="side-item mono-item">
              <strong>{{ item.app }}</strong>
              <span>{{ item.path }}</span>
              <em>{{ item.limit }} · {{ item.status }}</em>
            </div>
          </div>
          <el-empty v-else description="当前未配置真实 API 订阅" :image-size="72" />
        </div>
      </el-card>
    </section>

    <el-drawer v-model="detailDrawerVisible" title="风险事件详情" size="560px" class="event-detail-drawer">
      <div v-if="selectedEvent" class="drawer-content">
        <div class="drawer-hero" :class="getRiskLevelClass(selectedEvent.level)">
          <div class="drawer-top">
            <div class="drawer-badges">
              <el-tag :type="getRiskLevelTagType(selectedEvent.level)" effect="dark">{{ selectedEvent.level }}</el-tag>
              <el-tag :type="selectedEvent.status === '已闭环' ? 'success' : selectedEvent.status === '处理中' ? 'warning' : 'danger'">
                {{ selectedEvent.status }}
              </el-tag>
              <el-tag v-if="selectedEvent.overtime" type="danger" effect="plain">超时待处理</el-tag>
            </div>
            <div class="drawer-batch">批次 {{ selectedEvent.traceBatchId }}</div>
          </div>
          <div class="drawer-title">{{ selectedEvent.title }}</div>
          <div class="drawer-summary">{{ getRiskEventStatusSummary(selectedEvent) }}</div>
          <div class="drawer-meta-strip">
            <span>{{ selectedEvent.type }}</span>
            <span>{{ selectedEvent.location }}</span>
            <span>{{ selectedEvent.time }}</span>
          </div>
        </div>

        <div class="drawer-section">
          <div class="drawer-section__header">
            <h4>事件基础信息</h4>
            <span>监管留痕字段</span>
          </div>
          <div class="drawer-grid is-two-columns">
            <div class="drawer-field">
              <label>来源模块</label>
              <span>{{ selectedEvent.sourceModule }}</span>
            </div>
            <div class="drawer-field">
              <label>责任人</label>
              <span>{{ selectedEvent.owner }}</span>
            </div>
            <div class="drawer-field">
              <label>来源终端集合</label>
              <span>{{ selectedEvent.sourceTerminals.join(', ') }}</span>
            </div>
            <div class="drawer-field">
              <label>跨端一致性状态</label>
              <span>{{ selectedEvent.consistency }}</span>
            </div>
            <div class="drawer-field">
              <label>事件类型</label>
              <span>{{ selectedEvent.type }}</span>
            </div>
            <div class="drawer-field">
              <label>trace_batch_id</label>
              <span class="drawer-mono">{{ selectedEvent.traceBatchId }}</span>
            </div>
            <div v-if="selectedEvent.drillDown?.dispatchNo" class="drawer-field">
              <label>整改工单号</label>
              <span class="drawer-mono">{{ selectedEvent.drillDown.dispatchNo }}</span>
            </div>
          </div>
        </div>

        <div class="drawer-section">
          <div class="drawer-section__header">
            <h4>处置建议</h4>
            <span>按当前事件上下文生成</span>
          </div>
          <div class="drawer-advice-card">
            <div class="drawer-advice-title">建议处理路径</div>
            <p>{{ getRiskEventAdvice(selectedEvent) }}</p>
          </div>
        </div>

        <div class="drawer-section">
          <div class="drawer-section__header">
            <h4>人工处置工作台</h4>
            <span>认领、整改、挂起、闭环</span>
          </div>
          <div class="drawer-action-board">
            <div class="drawer-action-board__summary">
              <strong>当前处置建议</strong>
              <p>{{ getRiskEventActionTone(selectedEvent) }}</p>
            </div>
            <div v-if="selectedEvent.drillDown?.dispatchNo" class="drawer-action-board__dispatch">
              <span>已关联真实工单</span>
              <code>{{ selectedEvent.drillDown.dispatchNo }}</code>
            </div>
            <div class="drawer-action-board__buttons">
              <el-button
                type="primary"
                plain
                :disabled="selectedEvent.status === '已闭环' || selectedEvent.owner === currentDutyOwner"
                @click="openAssignDialog"
              >
                指派责任人
              </el-button>
              <el-button
                type="warning"
                plain
                :disabled="selectedEvent.status === '已闭环'"
                @click="openRectifyDialog"
              >
                整改工单
              </el-button>
              <el-button
                type="primary"
                plain
                :disabled="selectedEvent.status === '已闭环' || selectedEvent.owner === currentDutyOwner"
                @click="claimRiskEvent"
              >
                认领事件
              </el-button>
              <el-button
                plain
                :disabled="selectedEvent.status === '已闭环' || selectedEvent.status === '已挂起'"
                @click="suspendRiskEvent"
              >
                挂起待核
              </el-button>
              <el-button
                type="success"
                plain
                :disabled="selectedEvent.status === '已闭环'"
                @click="closeRiskEvent"
              >
                标记闭环
              </el-button>
            </div>
          </div>
        </div>

        <div class="drawer-section">
          <div class="drawer-section__header">
            <h4>业务穿透</h4>
            <span>优先主操作，次要操作置灰</span>
          </div>
          <div class="trace-actions">
            <div class="trace-actions__primary">
              <el-button
                type="primary"
                size="large"
                :disabled="!selectedEvent.drillDown"
                @click="navigateToRiskEventDrillDown(selectedEvent)"
              >
                {{ getRiskEventDrillDownLabel(selectedEvent) }}
              </el-button>
              <span class="trace-actions__hint">
                {{ selectedEvent.drillDown ? '进入关联业务明细后继续核查' : '当前事件暂未接入可用业务明细链路' }}
              </span>
            </div>
            <div class="trace-actions__secondary">
              <el-button type="primary" plain disabled>穿透入库批次</el-button>
              <el-button type="primary" plain disabled>穿透烹饪记录</el-button>
              <el-button type="primary" plain disabled>穿透留样销样</el-button>
            </div>
          </div>
        </div>

        <div class="drawer-section">
          <div class="drawer-section__header">
            <h4>处置记录</h4>
            <span>动作留痕与审计轨迹</span>
          </div>
          <div class="event-log-list">
            <div v-for="item in selectedEventLogs" :key="item.id" class="event-log-item">
              <div class="event-log-item__dot" :class="`is-${item.tone}`"></div>
              <div class="event-log-item__content">
                <div class="event-log-item__top">
                  <strong>{{ item.action }}</strong>
                  <span>{{ item.time }}</span>
                </div>
                <p>{{ item.detail }}</p>
                <em>操作人：{{ item.operator }}</em>
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="assignDialogVisible" title="指派责任人" width="520px" destroy-on-close>
      <el-form ref="assignFormRef" :model="assignForm" :rules="assignFormRules" label-width="96px">
        <el-form-item label="责任人" prop="assignee">
          <el-select v-model="assignForm.assignee" placeholder="请选择责任人">
            <el-option v-for="item in assigneeOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="处理时限" prop="deadline">
          <el-date-picker
            v-model="assignForm.deadline"
            type="datetime"
            placeholder="请选择处理时限"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="指派备注">
          <el-input v-model="assignForm.note" type="textarea" :rows="4" maxlength="120" show-word-limit placeholder="补充指派说明、督办要求或升级原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAssignForm">确认指派</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rectifyDialogVisible" title="整改工单" width="620px" destroy-on-close>
      <el-form ref="rectifyFormRef" :model="rectifyForm" :rules="rectifyFormRules" label-width="100px">
        <el-form-item label="工单标题" prop="title">
          <el-input v-model="rectifyForm.title" maxlength="40" show-word-limit placeholder="请输入整改工单标题" />
        </el-form-item>
        <el-form-item label="整改级别" prop="level">
          <el-segmented
            v-model="rectifyForm.level"
            :options="[
              { label: '紧急', value: '紧急' },
              { label: '高', value: '高' },
              { label: '中', value: '中' }
            ]"
          />
        </el-form-item>
        <el-form-item label="整改负责人" prop="assignee">
          <el-select v-model="rectifyForm.assignee" placeholder="请选择整改负责人">
            <el-option v-for="item in assigneeOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="整改时限" prop="deadline">
          <el-date-picker
            v-model="rectifyForm.deadline"
            type="datetime"
            placeholder="请选择整改时限"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="整改措施" prop="measure">
          <el-input v-model="rectifyForm.measure" type="textarea" :rows="5" maxlength="300" show-word-limit placeholder="请输入整改方案、核查步骤和复查要求" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rectifyDialogVisible = false">取消</el-button>
        <el-button type="warning" @click="submitRectifyForm">创建工单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 100%;
  padding: 4px;
  color: $text-primary;
}

.risk-banner {
  :deep(.el-alert__title) {
    font-weight: 600;
  }
}

.toolbar-panel,
.domain-card,
.chart-card,
.quality-card,
.detail-card {
  border: 1px solid $border-lighter;
  box-shadow: $box-shadow-base;
}

.toolbar-panel {
  background: $bg-white;
  border-radius: $border-radius-large;
  padding: 16px 18px;
}

.toolbar-main {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.toolbar-filters,
.toolbar-actions,
.refresh-actions {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.toolbar-filters {
  flex: 1;
}

.toolbar-filters :deep(.el-select) {
  width: 150px;
}

.snapshot-block {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 240px;
  padding: 10px 14px;
  border-radius: 14px;
  background: linear-gradient(135deg, #f4f8ff, #eef6ff);
  border: 1px solid #d8e7ff;
}

.snapshot-label,
.snapshot-sub {
  color: $text-secondary;
  font-size: 12px;
}

.focus-bar {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 10px;
  color: $text-secondary;
  font-size: 13px;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.overview-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.overview-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 8px;
  font-size: 12px;
  color: $text-secondary;
}

.overview-meta .source {
  margin-left: auto;
}

.domain-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.card-header h3 {
  font-size: 16px;
  font-weight: 700;
  color: $text-primary;
}

.card-header p {
  margin-top: 4px;
  color: $text-secondary;
  font-size: 12px;
}

.domain-metrics {
  display: grid;
  gap: 12px;
}

.domain-metric,
.quality-item,
.heat-item,
.event-item {
  padding: 14px;
  border-radius: 16px;
  border: 1px solid $border-lighter;
  background: #fafcff;
}

.metric-top,
.quality-top,
.event-top {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
}

.metric-name,
.quality-top span:first-child {
  font-weight: 600;
}

.metric-value,
.quality-value {
  margin-top: 8px;
  font-size: 26px;
  font-weight: 700;
}

.metric-hint,
.quality-bottom,
.event-meta,
.event-bottom {
  margin-top: 8px;
  color: $text-secondary;
  font-size: 12px;
}

.quality-bottom,
.event-meta,
.event-bottom {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.chart-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1.4fr;
  gap: 16px;
}

.chart-card.wide {
  grid-column: auto;
}

.chart-box {
  height: 280px;
}

.quality-grid {
  display: grid;
  grid-template-columns: 1.35fr 1fr;
  gap: 16px;
}

.quality-list,
.heat-list,
.event-list {
  display: grid;
  gap: 12px;
}

.heat-name {
  font-weight: 700;
}

.heat-level {
  margin-top: 6px;
  font-size: 22px;
  font-weight: 700;
}

.heat-value {
  margin-top: 6px;
  font-size: 12px;
  color: $text-secondary;
}

.detail-grid {
  display: grid;
  grid-template-columns: 1.05fr 1.1fr 0.78fr;
  gap: 16px;
}

.event-title-group {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.event-type {
  color: $text-secondary;
}

.event-title {
  font-size: 15px;
}

.clickable-card,
.domain-metric,
.quality-item,
.heat-item {
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.clickable-card:hover,
.domain-metric:hover,
.quality-item:hover,
.heat-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.08);
}

.terminal-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.event-flags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-left: auto;
}

.side-panel {
  background: linear-gradient(180deg, #fbfdff, #f5f9ff);
}

.side-block + .side-block {
  margin-top: 18px;
}

.side-title {
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 10px;
}

.side-list {
  display: grid;
  gap: 10px;
}

.side-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px;
  border-radius: 14px;
  border: 1px solid $border-lighter;
  background: #fff;
}

.side-item span,
.side-item em {
  font-size: 12px;
  color: $text-secondary;
}

.mono-item span {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
}

.drawer-content {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding-bottom: 12px;
}

.drawer-hero {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 18px;
  border-radius: 18px;
  border: 1px solid rgba(64, 158, 255, 0.12);
  background:
    radial-gradient(circle at top right, rgba(64, 158, 255, 0.1), transparent 34%),
    linear-gradient(180deg, #fbfdff, #f4f8ff);
}

.drawer-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.drawer-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.drawer-batch {
  font-size: 12px;
  color: $text-secondary;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
}

.drawer-title {
  font-size: 22px;
  font-weight: 700;
  line-height: 1.4;
  letter-spacing: 0.01em;
}

.drawer-summary {
  color: $text-regular;
  font-size: 14px;
  line-height: 1.7;
}

.drawer-meta-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.drawer-meta-strip span {
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(148, 163, 184, 0.18);
  color: $text-secondary;
  font-size: 12px;
}

.drawer-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.drawer-section__header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.drawer-section__header h4 {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: $text-primary;
}

.drawer-section__header span {
  font-size: 12px;
  color: $text-secondary;
}

.drawer-grid {
  display: grid;
  gap: 12px;
}

.drawer-grid.is-two-columns {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.drawer-field {
  display: grid;
  gap: 6px;
  padding: 14px 16px;
  border-radius: 14px;
  border: 1px solid rgba(148, 163, 184, 0.12);
  background: linear-gradient(180deg, #fafcff, #f6f9fd);
}

.drawer-field label {
  font-size: 12px;
  color: $text-secondary;
}

.drawer-field span {
  font-size: 14px;
  color: $text-primary;
}

.drawer-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
}

.drawer-advice-card {
  padding: 16px 18px;
  border-radius: 16px;
  border: 1px solid rgba(230, 162, 60, 0.18);
  background: linear-gradient(180deg, #fffaf1, #fffdfa);
}

.drawer-advice-title {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 700;
  color: #ad6a00;
}

.drawer-advice-card p {
  margin: 0;
  color: $text-regular;
  line-height: 1.7;
  font-size: 14px;
}

.drawer-action-board {
  display: grid;
  gap: 12px;
  padding: 16px 18px;
  border-radius: 16px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: linear-gradient(180deg, #f8fafc, #ffffff);
}

.drawer-action-board__summary strong {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  color: $text-primary;
}

.drawer-action-board__summary p {
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  color: $text-regular;
}

.drawer-action-board__dispatch {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(245, 158, 11, 0.08);
  color: #9a6700;
  font-size: 13px;
}

.drawer-action-board__dispatch code {
  padding: 3px 8px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.9);
  color: #7c5200;
  font-size: 12px;
}

.drawer-action-board__buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.trace-actions {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.trace-actions__primary {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  padding: 16px 18px;
  border-radius: 16px;
  background: linear-gradient(180deg, #f5f9ff, #ffffff);
  border: 1px solid rgba(64, 158, 255, 0.14);
}

.trace-actions__hint {
  font-size: 12px;
  color: $text-secondary;
}

.trace-actions__secondary {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.event-log-list {
  display: grid;
  gap: 12px;
}

.event-log-item {
  display: grid;
  grid-template-columns: 14px minmax(0, 1fr);
  gap: 12px;
  align-items: flex-start;
}

.event-log-item__dot {
  width: 10px;
  height: 10px;
  margin-top: 6px;
  border-radius: 999px;
  background: #c0c4cc;
  box-shadow: 0 0 0 4px rgba(192, 196, 204, 0.14);
}

.event-log-item__dot.is-primary {
  background: #409eff;
  box-shadow: 0 0 0 4px rgba(64, 158, 255, 0.14);
}

.event-log-item__dot.is-warning {
  background: #e6a23c;
  box-shadow: 0 0 0 4px rgba(230, 162, 60, 0.14);
}

.event-log-item__dot.is-success {
  background: #67c23a;
  box-shadow: 0 0 0 4px rgba(103, 194, 58, 0.14);
}

.event-log-item__dot.is-info {
  background: #909399;
  box-shadow: 0 0 0 4px rgba(144, 147, 153, 0.14);
}

.event-log-item__content {
  padding: 14px 16px;
  border-radius: 14px;
  border: 1px solid rgba(148, 163, 184, 0.12);
  background: linear-gradient(180deg, #fcfdff, #f8fafc);
}

.event-log-item__top {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
}

.event-log-item__top strong {
  font-size: 14px;
  color: $text-primary;
}

.event-log-item__top span,
.event-log-item__content em {
  font-size: 12px;
  color: $text-secondary;
  font-style: normal;
}

.event-log-item__content p {
  margin: 0 0 8px;
  font-size: 14px;
  line-height: 1.7;
  color: $text-regular;
}

:deep(.event-detail-drawer .el-drawer__header) {
  margin-bottom: 8px;
  font-weight: 700;
}

:deep(.el-dialog .el-form-item__content .el-select),
:deep(.el-dialog .el-form-item__content .el-segmented) {
  width: 100%;
}

.is-normal {
  border-color: rgba(103, 194, 58, 0.22);
  background: linear-gradient(180deg, #f7fdf6, #ffffff);
}

.is-warning {
  border-color: rgba(230, 162, 60, 0.25);
  background: linear-gradient(180deg, #fffaf2, #ffffff);
}

.is-danger {
  border-color: rgba(245, 108, 108, 0.24);
  background: linear-gradient(180deg, #fff7f7, #ffffff);
}

.is-urgent {
  border-color: rgba(245, 108, 108, 0.36);
  background: linear-gradient(180deg, #fff1f1, #ffffff);
  box-shadow: inset 0 0 0 1px rgba(245, 108, 108, 0.14);
}

@media (max-width: 1440px) {
  .overview-grid,
  .domain-grid,
  .chart-grid,
  .quality-grid,
  .detail-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 1024px) {
  .toolbar-main,
  .overview-grid,
  .domain-grid,
  .chart-grid,
  .quality-grid,
  .detail-grid {
    grid-template-columns: 1fr;
    flex-direction: column;
  }

  .overview-grid,
  .domain-grid,
  .chart-grid,
  .quality-grid,
  .detail-grid {
    display: grid;
  }

  .drawer-grid.is-two-columns {
    grid-template-columns: 1fr;
  }

  .drawer-top,
  .drawer-section__header {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
