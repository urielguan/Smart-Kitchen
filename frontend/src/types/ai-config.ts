export interface AiServiceConfigItem {
  id: number
  serviceName: string
  serviceType: 'text' | 'vision'
  baseUrl: string
  apiKeyMasked: string
  modelName: string
  applicableModules: string[]
  status: 'active' | 'inactive'
  lastTestStatus?: 'success' | 'failed'
  lastTestMessage?: string
  lastTestAt?: string
  remark?: string
  createdAt?: string
  updatedAt?: string
}

export interface AiServiceConfigForm {
  serviceName: string
  serviceType: 'text' | 'vision'
  baseUrl: string
  apiKey: string
  modelName: string
  applicableModules: string[]
  remark?: string
}

export interface AiServiceConfigQuery {
  keyword?: string
  serviceType?: 'text' | 'vision' | ''
  status?: 'active' | 'inactive' | ''
  pageNum?: number
  pageSize?: number
}

export interface AiRequestLogItem {
  id: number
  requestType: string
  serviceType: string
  moduleCode: string
  modelName: string
  targetUrl: string
  durationMs?: number
  status: string
  errorMessage?: string
  responseSummary?: string
  createdAt?: string
}
