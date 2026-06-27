import type {
  RegulatoryApiSubscription,
  RegulatoryBarItem,
  RegulatoryDashboardData,
  RegulatoryDistributionItem,
  RegulatoryDomainSection,
  RegulatoryExternalShare,
  RegulatoryHeatCard,
  RegulatoryOverviewMetric,
  RegulatoryQualityMetric,
  RegulatoryQuickRange,
  RegulatoryReportTemplate,
  RegulatoryRiskEvent,
  RegulatoryTrendPoint
} from '@/types/dashboard'
import { getRegulatoryDashboardSnapshot } from '@/api/modules/dashboard'

export const dashboardQuickRangeOptions = [
  { label: '今日', value: 'today' },
  { label: '近7日', value: '7d' },
  { label: '近30日', value: '30d' }
] as const

export const dashboardOrganizationOptions = ['集团A', '华东区域', '第一食堂', '第二食堂']
export const dashboardCanteenOptions = ['全部食堂', '第一食堂', '第二食堂', '第三食堂']
export const dashboardAreaOptions = ['全部区域', '热厨A区', '面点间', '仓储一区', '冷藏库']

const overviewMetrics: RegulatoryOverviewMetric[] = [
  { id: 'diner', title: '今日就餐人数', value: '2,186', compare: '环比 +6.3%', status: '正常', source: '就餐与排餐' },
  { id: 'recipe', title: '菜谱执行数', value: '42', unit: '个', compare: '执行率 97.7%', status: '正常', source: '菜谱与烹饪' },
  { id: 'cook', title: '烹饪任务数', value: '128', unit: '项', compare: '超时 6 项', status: '预警', source: '烹饪记录' },
  { id: 'sample', title: '留样 / 销样数', value: '116 / 109', compare: '销样及时率 93.9%', status: '预警', source: '留样管理' },
  { id: 'morning', title: '晨检人数', value: '86', unit: '人', compare: '异常 3 人', status: '异常', source: '晨检系统' },
  { id: 'device', title: '设备在线率', value: '96.8', unit: '%', compare: '异常设备 4 台', status: '预警', source: '设备状态' },
  { id: 'alarm', title: '今日告警总数', value: '19', unit: '次', compare: '紧急 3 / 严重 5', status: '紧急', source: '告警中心' },
  { id: 'comment', title: '投诉闭环率', value: '91.3', unit: '%', compare: '好评率 96.1%', status: '正常', source: '评价申诉' }
]

const domainSections: RegulatoryDomainSection[] = [
  {
    title: '食品安全专项监管',
    subtitle: '晨检、留样、烹饪温度、AI违规识别',
    metrics: [
      { name: '晨检完成率', value: '98.8%', hint: '体温超标 2 人，健康证过期 1 人', status: '预警' },
      { name: '留样合规率', value: '96.4%', hint: '留样缺失 2 条，销样延迟 7 条', status: '预警' },
      { name: '烹饪温度达标率', value: '94.7%', hint: '超时烹饪 6 次，最高偏差 7°C', status: '异常' },
      { name: 'AI违规识别趋势', value: '12 次', hint: '未戴口罩 5，未戴手套 4，动火离人 3', status: '紧急' }
    ]
  },
  {
    title: '物资与库存监管',
    subtitle: '库存预警、过期风险、台账完整性、采购溯源',
    metrics: [
      { name: '库存预警物料', value: '18 项', hint: '低库存 11，临期 5，过期 2', status: '异常' },
      { name: '过期物料统计', value: '2 批次', hint: '均已锁定，不允许继续领用', status: '紧急' },
      { name: '出入库台账完整性', value: '99.1%', hint: '缺失台账 1 笔，待补录', status: '预警' },
      { name: '食材采购溯源覆盖率', value: '97.8%', hint: '可穿透至采购、入库、烹饪、留样', status: '正常' }
    ]
  },
  {
    title: '告警与整改监管',
    subtitle: '未处理告警、超时工单、整改完成率、复查通过率',
    metrics: [
      { name: '未处理告警', value: '7 条', hint: '紧急 3 条已置顶强提醒', status: '紧急' },
      { name: '超时未处理工单', value: '4 单', hint: '均超过 SLA，待负责人督办', status: '异常' },
      { name: '整改完成率', value: '88.6%', hint: '本周已闭环 31 / 35', status: '预警' },
      { name: '复查通过率', value: '92.4%', hint: '复查未通过 2 单，已回退处理中', status: '正常' }
    ]
  }
]

const riskEvents: RegulatoryRiskEvent[] = [
  {
    id: 'evt-001',
    type: '告警异常',
    title: '热厨A区温度持续不达标',
    traceBatchId: 'TB-20260601-00021',
    level: '紧急',
    location: '第一食堂 / 热厨A区 / 灶台2',
    time: '2026-06-01 14:26:18',
    status: '处理中',
    owner: '张*',
    sourceModule: 'AI告警管理',
    sourceTerminals: ['Web', 'KDS', 'Device'],
    consistency: '一致',
    overtime: true,
    drillDown: {
      type: 'dispatch',
      alertId: 1001,
      alertNo: 'ALT-20260601-00021',
      metric: 'overtime-dispatches',
      tab: 'dispatches',
      overdue: true
    }
  },
  {
    id: 'evt-002',
    type: '食安风险',
    title: '留样任务超时未登记',
    traceBatchId: 'TB-20260601-00015',
    level: '严重',
    location: '第二食堂 / 留样间 / 操作台1',
    time: '2026-06-01 13:58:03',
    status: '待处理',
    owner: '李*',
    sourceModule: '留样管理',
    sourceTerminals: ['Web', 'Mobile'],
    consistency: '待校验',
    overtime: true
  },
  {
    id: 'evt-003',
    type: '库存风险',
    title: '鸡蛋批次临期且未完成锁定',
    traceBatchId: 'TB-20260531-00008',
    level: '一般',
    location: '第一食堂 / 冷藏库 / 架位B-03',
    time: '2026-06-01 11:47:51',
    status: '处理中',
    owner: '王*',
    sourceModule: '仓储管理',
    sourceTerminals: ['Web'],
    consistency: '一致',
    overtime: false,
    drillDown: {
      type: 'purchase',
      traceBatchId: 'TB-20260531-00008'
    }
  },
  {
    id: 'evt-004',
    type: '违规行为',
    title: '后厨员工未戴手套连续识别',
    traceBatchId: 'TB-20260601-00011',
    level: '严重',
    location: '第一食堂 / 面点间 / 操作台3',
    time: '2026-06-01 10:21:34',
    status: '已挂起',
    owner: '赵*',
    sourceModule: 'AI违规识别',
    sourceTerminals: ['Device', 'Web'],
    consistency: '异常',
    overtime: false,
    drillDown: {
      type: 'alert',
      alertId: 1004,
      alertNo: 'ALT-20260601-00011',
      metric: 'pending-alerts',
      tab: 'alerts',
      status: 'pending',
      alertLevel: 'critical'
    }
  },
  {
    id: 'evt-005',
    type: '评价申诉',
    title: '员工投诉菜品异味并触发追溯核查',
    traceBatchId: 'TB-20260601-00019',
    level: '严重',
    location: '第三食堂 / 午餐窗口 / 档口2',
    time: '2026-06-01 09:14:05',
    status: '已闭环',
    owner: '陈*',
    sourceModule: '评价管理',
    sourceTerminals: ['App', 'Web'],
    consistency: '一致',
    overtime: false
  }
]

const trendSeriesMap: Record<RegulatoryQuickRange, RegulatoryTrendPoint[]> = {
  today: [
    { label: '08:00', alarm: 2, review: 1 },
    { label: '10:00', alarm: 5, review: 2 },
    { label: '12:00', alarm: 8, review: 4 },
    { label: '14:00', alarm: 12, review: 6 },
    { label: '16:00', alarm: 10, review: 5 },
    { label: '18:00', alarm: 7, review: 3 }
  ],
  '7d': [
    { label: '05-26', alarm: 10, review: 8 },
    { label: '05-27', alarm: 12, review: 9 },
    { label: '05-28', alarm: 8, review: 7 },
    { label: '05-29', alarm: 15, review: 11 },
    { label: '05-30', alarm: 11, review: 8 },
    { label: '05-31', alarm: 9, review: 6 },
    { label: '06-01', alarm: 19, review: 13 }
  ],
  '30d': [
    { label: '第1周', alarm: 61, review: 48 },
    { label: '第2周', alarm: 55, review: 44 },
    { label: '第3周', alarm: 73, review: 58 },
    { label: '第4周', alarm: 69, review: 62 }
  ]
}

const alarmDistributionMap: Record<RegulatoryQuickRange, RegulatoryDistributionItem[]> = {
  today: [
    { name: '紧急', value: 3 },
    { name: '异常', value: 5 },
    { name: '预警', value: 8 },
    { name: '正常跟踪', value: 3 }
  ],
  '7d': [
    { name: '紧急', value: 9 },
    { name: '异常', value: 18 },
    { name: '预警', value: 31 },
    { name: '正常跟踪', value: 12 }
  ],
  '30d': [
    { name: '紧急', value: 27 },
    { name: '异常', value: 51 },
    { name: '预警', value: 98 },
    { name: '正常跟踪', value: 42 }
  ]
}

const executionSeriesMap: Record<RegulatoryQuickRange, RegulatoryBarItem[]> = {
  today: [
    { label: '早餐', morningCheck: 100, cooking: 96 },
    { label: '午餐', morningCheck: 98, cooking: 92 },
    { label: '晚餐', morningCheck: 97, cooking: 90 }
  ],
  '7d': [
    { label: '周一', morningCheck: 97, cooking: 93 },
    { label: '周二', morningCheck: 99, cooking: 94 },
    { label: '周三', morningCheck: 98, cooking: 91 },
    { label: '周四', morningCheck: 100, cooking: 95 },
    { label: '周五', morningCheck: 97, cooking: 92 },
    { label: '周六', morningCheck: 96, cooking: 89 },
    { label: '周日', morningCheck: 98, cooking: 94 }
  ],
  '30d': [
    { label: '第1周', morningCheck: 98, cooking: 91 },
    { label: '第2周', morningCheck: 99, cooking: 92 },
    { label: '第3周', morningCheck: 98, cooking: 94 },
    { label: '第4周', morningCheck: 99, cooking: 95 }
  ]
}

const serviceQuality: RegulatoryQualityMetric[] = [
  { label: '违规率', value: '1.8%', compare: '环比 -0.4%', target: '目标 ≤ 2.0%', status: '正常' },
  { label: '溯源响应时长', value: '11 分钟', compare: '同比 -2 分钟', target: '超时单 1 笔', status: '预警' },
  { label: '食材浪费率', value: '2.3%', compare: '环比 +0.3%', target: '目标 ≤ 2.0%', status: '预警' },
  { label: '就餐满意度', value: '96.1 分', compare: '好评率 96.1%', target: '投诉闭环 91.3%', status: '正常' }
]

const heatCards: RegulatoryHeatCard[] = [
  { name: '热厨A区', level: '高风险', value: '3 起紧急事件', status: '紧急' },
  { name: '仓储一区', level: '中风险', value: '临期与过期 4 批次', status: '异常' },
  { name: '面点间', level: '关注', value: 'AI违规识别 2 起', status: '预警' },
  { name: '冷藏库', level: '稳定', value: '设备在线率 100%', status: '正常' }
]

const reportTemplates: RegulatoryReportTemplate[] = [
  { name: '集团监管日报', scope: '组织 + 食堂 + 近1日', updatedAt: '今天 14:10' },
  { name: '食安专项周报', scope: '食品安全专项 + 近7日', updatedAt: '今天 09:20' },
  { name: '告警整改月度复盘', scope: '告警与整改 + 近30日', updatedAt: '昨天 18:40' }
]

const externalShares: RegulatoryExternalShare[] = [
  { target: '市场监管部门', mode: '二维码 / 外链', expireAt: '2026-06-08 23:59', status: '生效中' },
  { target: '集团运营中心', mode: '内部分享', expireAt: '长期', status: '生效中' }
]

const apiSubscriptions: RegulatoryApiSubscription[] = [
  { app: 'regulator-openapi', path: '/openapi/v1/dashboard/overview', limit: '120次/分钟', status: '正常' },
  { app: 'group-bi-sync', path: '/openapi/v1/dashboard/risk-events', limit: '60次/分钟', status: '正常' }
]

const deepClone = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T

const formatNow = () => {
  const now = new Date()
  const pad = (value: number) => `${value}`.padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
}

type RegulatoryDashboardDataSource = 'mock' | 'remote' | 'auto'

export interface RegulatoryDashboardQuery {
  quickRange: RegulatoryQuickRange
  organization?: string
  canteen?: string
  area?: string
  dateRange?: [Date, Date] | null
}

const formatDate = (date: Date) => {
  const pad = (value: number) => `${value}`.padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

const isRegulatoryDashboardData = (value: unknown): value is RegulatoryDashboardData => {
  if (!value || typeof value !== 'object') {
    return false
  }

  const payload = value as Partial<RegulatoryDashboardData>
  return typeof payload.snapshotAt === 'string'
    && typeof payload.lastUpdatedAt === 'string'
    && Array.isArray(payload.overviewMetrics)
    && Array.isArray(payload.domainSections)
    && Array.isArray(payload.riskEvents)
    && Array.isArray(payload.trendSeries)
    && Array.isArray(payload.alarmDistribution)
    && Array.isArray(payload.executionSeries)
    && Array.isArray(payload.serviceQuality)
    && Array.isArray(payload.heatCards)
    && Array.isArray(payload.reportTemplates)
    && Array.isArray(payload.externalShares)
    && Array.isArray(payload.apiSubscriptions)
}

const resolveDashboardDataSource = (): RegulatoryDashboardDataSource => {
  const mode = import.meta.env.VITE_DASHBOARD_DATA_SOURCE
  if (mode === 'mock' || mode === 'remote' || mode === 'auto') {
    return mode
  }
  return 'auto'
}

const buildMockRegulatoryDashboardData = (query: RegulatoryDashboardQuery): RegulatoryDashboardData => {
  const timestamp = formatNow()

  return {
    snapshotAt: timestamp,
    lastUpdatedAt: timestamp,
    overviewMetrics: deepClone(overviewMetrics),
    domainSections: deepClone(domainSections),
    riskEvents: deepClone(riskEvents),
    trendSeries: deepClone(trendSeriesMap[query.quickRange]),
    alarmDistribution: deepClone(alarmDistributionMap[query.quickRange]),
    executionSeries: deepClone(executionSeriesMap[query.quickRange]),
    serviceQuality: deepClone(serviceQuality),
    heatCards: deepClone(heatCards),
    reportTemplates: deepClone(reportTemplates),
    externalShares: deepClone(externalShares),
    apiSubscriptions: deepClone(apiSubscriptions)
  }
}

const fetchRemoteRegulatoryDashboardData = async (
  query: RegulatoryDashboardQuery,
  silentError = false
): Promise<RegulatoryDashboardData | null> => {
  const [startDate, endDate] = query.dateRange ?? []
  const response = await getRegulatoryDashboardSnapshot(
    {
      quickRange: query.quickRange,
      organization: query.organization,
      canteen: query.canteen,
      area: query.area,
      startDate: startDate ? formatDate(startDate) : undefined,
      endDate: endDate ? formatDate(endDate) : undefined
    },
    { silentError }
  )

  return isRegulatoryDashboardData(response.data) ? response.data : null
}

export const loadMockRegulatoryDashboardData = async (
  query: RegulatoryDashboardQuery
): Promise<RegulatoryDashboardData> => {
  return buildMockRegulatoryDashboardData(query)
}

export const loadRegulatoryDashboardData = async (
  query: RegulatoryDashboardQuery
): Promise<RegulatoryDashboardData> => {
  const mode = resolveDashboardDataSource()

  if (mode === 'mock') {
    return loadMockRegulatoryDashboardData(query)
  }

  if (mode === 'remote' || mode === 'auto') {
    try {
      const data = await fetchRemoteRegulatoryDashboardData(query, mode === 'auto')
      if (data) {
        return data
      }
      if (mode === 'remote') {
        throw new Error('数据监管看板接口返回结构无效')
      }
    } catch (error) {
      if (mode === 'remote') {
        throw error
      }
    }
    return loadMockRegulatoryDashboardData(query)
  }

  return loadMockRegulatoryDashboardData(query)
}
