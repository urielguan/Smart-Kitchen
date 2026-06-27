/**
 * 数据看板相关类型
 */

export type TrendType = 'up' | 'down' | 'stable'
export type TimeRange = 'today' | 'week' | 'month'

export interface DashboardMetric {
  name: string
  value: number
  unit: string
  trend: string
  trendType: TrendType
  score: number
}

export interface DashboardSection {
  id: string
  title: string
  metrics: DashboardMetric[]
}

export interface DashboardTrendPoint {
  label: string
  value: number
}

export interface DashboardCompareItem {
  label: string
  planned: number
  actual: number
}

export interface DashboardDistributionItem {
  label: string
  value: number
  color: string
}

export interface DashboardOverview {
  generatedAt: string
  sections: DashboardSection[]
  trendData: DashboardTrendPoint[]
  compareData: DashboardCompareItem[]
  distributionData: DashboardDistributionItem[]
}

export type RegulatoryStatusType = '正常' | '预警' | '异常' | '紧急'
export type RegulatoryQuickRange = 'today' | '7d' | '30d'
export type RegulatoryRiskLevel = '一般' | '严重' | '紧急'

export interface RegulatoryOverviewMetric {
  id: string
  title: string
  value: string
  unit?: string
  compare: string
  status: RegulatoryStatusType
  source: string
}

export interface RegulatoryDomainMetric {
  name: string
  value: string
  hint: string
  status: RegulatoryStatusType
}

export interface RegulatoryDomainSection {
  title: string
  subtitle: string
  metrics: RegulatoryDomainMetric[]
}

export interface RegulatoryRiskEventDrillDown {
  type: 'purchase' | 'sample' | 'alert' | 'dispatch'
  recordId?: number
  traceBatchId?: string
  alertId?: number
  dispatchId?: number
  alertNo?: string
  dispatchNo?: string
  metric?: string
  tab?: 'alerts' | 'dispatches'
  status?: string
  alertLevel?: string
  overdue?: boolean
}

export interface RegulatoryRiskEvent {
  id: string
  type: string
  title: string
  traceBatchId: string
  level: RegulatoryRiskLevel
  location: string
  time: string
  status: '待处理' | '处理中' | '已闭环' | '已挂起'
  owner: string
  sourceModule: string
  sourceTerminals: string[]
  consistency: '一致' | '待校验' | '异常'
  overtime: boolean
  drillDown?: RegulatoryRiskEventDrillDown | null
}

export interface RegulatoryTrendPoint {
  label: string
  alarm: number
  review: number
}

export interface RegulatoryDistributionItem {
  name: string
  value: number
}

export interface RegulatoryBarItem {
  label: string
  morningCheck: number
  cooking: number
}

export interface RegulatoryQualityMetric {
  label: string
  value: string
  compare: string
  target: string
  status: RegulatoryStatusType
}

export interface RegulatoryHeatCard {
  name: string
  level: string
  value: string
  status: RegulatoryStatusType
}

export interface RegulatoryReportTemplate {
  name: string
  scope: string
  updatedAt: string
}

export interface RegulatoryExternalShare {
  target: string
  mode: string
  expireAt: string
  status: string
}

export interface RegulatoryApiSubscription {
  app: string
  path: string
  limit: string
  status: string
}

export interface RegulatoryDashboardData {
  snapshotAt: string
  lastUpdatedAt: string
  overviewMetrics: RegulatoryOverviewMetric[]
  domainSections: RegulatoryDomainSection[]
  riskEvents: RegulatoryRiskEvent[]
  trendSeries: RegulatoryTrendPoint[]
  alarmDistribution: RegulatoryDistributionItem[]
  executionSeries: RegulatoryBarItem[]
  serviceQuality: RegulatoryQualityMetric[]
  heatCards: RegulatoryHeatCard[]
  reportTemplates: RegulatoryReportTemplate[]
  externalShares: RegulatoryExternalShare[]
  apiSubscriptions: RegulatoryApiSubscription[]
}
