import { del, get, post, put } from '@/api'
import type { RequestConfig } from '@/api'
import type { ApiResponse, PageResponse } from '@/types/api'
import type {
  IntegrationCallbackLogItem,
  IntegrationCallbackLogQuery,
  IntegrationExecutionResult,
  IntegrationFieldMappingDuplicateCheckForm,
  IntegrationFieldMappingForm,
  IntegrationFieldMappingItem,
  IntegrationFieldMappingQuery,
  IntegrationFileRecordItem,
  IntegrationFileRecordQuery,
  IntegrationHealthCheckItem,
  IntegrationHealthCheckQuery,
  IntegrationModuleConfigForm,
  IntegrationModuleConfigItem,
  IntegrationModuleConfigQuery,
  IntegrationOverview,
  IntegrationProviderTemplateForm,
  IntegrationProviderTemplateItem,
  IntegrationProviderTemplateQuery,
  IntegrationStatusMappingDuplicateCheckForm,
  IntegrationStatusMappingForm,
  IntegrationStatusMappingItem,
  IntegrationStatusMappingQuery,
  IntegrationSyncLogHandleForm,
  IntegrationSyncLogItem,
  IntegrationSyncLogProviderOption,
  IntegrationSyncLogQuery,
  IntegrationSyncTaskItem,
  IntegrationSyncTaskQuery,
  IntegrationSyncTriggerForm
} from '@/types/integration'

const BASE = '/v1/integration'

export const integrationApi = {
  getOverview(orgId?: number): Promise<ApiResponse<IntegrationOverview>> {
    return get(`${BASE}/overview`, orgId ? { orgId } : {})
  },
  pageProviders(params: IntegrationProviderTemplateQuery): Promise<ApiResponse<PageResponse<IntegrationProviderTemplateItem>>> {
    return get(`${BASE}/providers`, params)
  },
  getProvider(id: number): Promise<ApiResponse<IntegrationProviderTemplateItem>> {
    return get(`${BASE}/providers/${id}`)
  },
  createProvider(data: IntegrationProviderTemplateForm): Promise<ApiResponse<{ id: number }>> {
    return post(`${BASE}/providers`, data)
  },
  updateProvider(id: number, data: IntegrationProviderTemplateForm): Promise<ApiResponse<void>> {
    return put(`${BASE}/providers/${id}`, data)
  },
  removeProvider(id: number): Promise<ApiResponse<void>> {
    return del(`${BASE}/providers/${id}`)
  },
  changeProviderStatus(id: number, status: 'active' | 'inactive'): Promise<ApiResponse<void>> {
    return put(`${BASE}/providers/${id}/status`, { status })
  },

  pageModuleConfigs(params: IntegrationModuleConfigQuery): Promise<ApiResponse<PageResponse<IntegrationModuleConfigItem>>> {
    return get(`${BASE}/module-configs`, params)
  },
  getModuleConfig(id: number): Promise<ApiResponse<IntegrationModuleConfigItem>> {
    return get(`${BASE}/module-configs/${id}`)
  },
  createModuleConfig(data: IntegrationModuleConfigForm, config?: RequestConfig): Promise<ApiResponse<{ id: number }>> {
    return post(`${BASE}/module-configs`, data, config)
  },
  updateModuleConfig(id: number, data: IntegrationModuleConfigForm, config?: RequestConfig): Promise<ApiResponse<void>> {
    return put(`${BASE}/module-configs/${id}`, data, config)
  },
  removeModuleConfig(id: number): Promise<ApiResponse<void>> {
    return del(`${BASE}/module-configs/${id}`)
  },
  changeModuleConfigStatus(id: number, enabled: number): Promise<ApiResponse<void>> {
    return put(`${BASE}/module-configs/${id}/status`, { enabled })
  },
  testModuleConfig(id: number): Promise<ApiResponse<IntegrationHealthCheckItem>> {
    return post(`${BASE}/module-configs/${id}/test`)
  },

  pageFieldMappings(params: IntegrationFieldMappingQuery): Promise<ApiResponse<PageResponse<IntegrationFieldMappingItem>>> {
    return get(`${BASE}/field-mappings`, params)
  },
  getFieldMapping(id: number): Promise<ApiResponse<IntegrationFieldMappingItem>> {
    return get(`${BASE}/field-mappings/${id}`)
  },
  checkFieldMappingDuplicate(data: IntegrationFieldMappingDuplicateCheckForm, config?: RequestConfig): Promise<ApiResponse<void>> {
    return post(`${BASE}/field-mappings/check-duplicate`, data, config)
  },
  createFieldMapping(data: IntegrationFieldMappingForm, config?: RequestConfig): Promise<ApiResponse<{ id: number }>> {
    return post(`${BASE}/field-mappings`, data, config)
  },
  updateFieldMapping(id: number, data: IntegrationFieldMappingForm, config?: RequestConfig): Promise<ApiResponse<void>> {
    return put(`${BASE}/field-mappings/${id}`, data, config)
  },
  removeFieldMapping(id: number): Promise<ApiResponse<void>> {
    return del(`${BASE}/field-mappings/${id}`)
  },

  pageStatusMappings(params: IntegrationStatusMappingQuery): Promise<ApiResponse<PageResponse<IntegrationStatusMappingItem>>> {
    return get(`${BASE}/status-mappings`, params)
  },
  getStatusMapping(id: number): Promise<ApiResponse<IntegrationStatusMappingItem>> {
    return get(`${BASE}/status-mappings/${id}`)
  },
  checkStatusMappingDuplicate(data: IntegrationStatusMappingDuplicateCheckForm, config?: RequestConfig): Promise<ApiResponse<void>> {
    return post(`${BASE}/status-mappings/check-duplicate`, data, config)
  },
  createStatusMapping(data: IntegrationStatusMappingForm, config?: RequestConfig): Promise<ApiResponse<{ id: number }>> {
    return post(`${BASE}/status-mappings`, data, config)
  },
  updateStatusMapping(id: number, data: IntegrationStatusMappingForm, config?: RequestConfig): Promise<ApiResponse<void>> {
    return put(`${BASE}/status-mappings/${id}`, data, config)
  },
  removeStatusMapping(id: number): Promise<ApiResponse<void>> {
    return del(`${BASE}/status-mappings/${id}`)
  },

  pageSyncTasks(params: IntegrationSyncTaskQuery): Promise<ApiResponse<PageResponse<IntegrationSyncTaskItem>>> {
    return get(`${BASE}/sync/tasks`, params)
  },
  getSyncTask(id: number): Promise<ApiResponse<IntegrationSyncTaskItem>> {
    return get(`${BASE}/sync/tasks/${id}`)
  },
  triggerSync(data: IntegrationSyncTriggerForm): Promise<ApiResponse<IntegrationExecutionResult>> {
    return post(`${BASE}/sync/tasks/trigger`, data)
  },
  retrySyncTask(id: number): Promise<ApiResponse<IntegrationExecutionResult>> {
    return post(`${BASE}/sync/tasks/${id}/retry`)
  },

  pageSyncLogs(params: IntegrationSyncLogQuery): Promise<ApiResponse<PageResponse<IntegrationSyncLogItem>>> {
    return get(`${BASE}/sync/logs`, params)
  },
  getSyncLog(id: number): Promise<ApiResponse<IntegrationSyncLogItem>> {
    return get(`${BASE}/sync/logs/${id}`)
  },
  getSyncLogProviderOptions(): Promise<ApiResponse<IntegrationSyncLogProviderOption[]>> {
    return get(`${BASE}/sync/logs/options/providers`)
  },
  updateSyncLogHandleStatus(id: number, data: IntegrationSyncLogHandleForm): Promise<ApiResponse<void>> {
    return put(`${BASE}/sync/logs/${id}/handle-status`, data)
  },

  pageCallbackLogs(params: IntegrationCallbackLogQuery): Promise<ApiResponse<PageResponse<IntegrationCallbackLogItem>>> {
    return get(`${BASE}/callback/logs`, params)
  },
  getCallbackLog(id: number): Promise<ApiResponse<IntegrationCallbackLogItem>> {
    return get(`${BASE}/callback/logs/${id}`)
  },
  getCallbackLogProviderOptions(): Promise<ApiResponse<IntegrationSyncLogProviderOption[]>> {
    return get(`${BASE}/callback/logs/options/providers`)
  },

  pageFileRecords(params: IntegrationFileRecordQuery): Promise<ApiResponse<PageResponse<IntegrationFileRecordItem>>> {
    return get(`${BASE}/files`, params)
  },
  getFileRecord(id: number): Promise<ApiResponse<IntegrationFileRecordItem>> {
    return get(`${BASE}/files/${id}`)
  },
  getFileRecordProviderOptions(): Promise<ApiResponse<IntegrationSyncLogProviderOption[]>> {
    return get(`${BASE}/files/options/providers`)
  },

  pageHealthChecks(params: IntegrationHealthCheckQuery): Promise<ApiResponse<PageResponse<IntegrationHealthCheckItem>>> {
    return get(`${BASE}/health-checks`, params)
  },
  getHealthCheck(configId: number): Promise<ApiResponse<IntegrationHealthCheckItem>> {
    return get(`${BASE}/health-checks/${configId}`)
  },
  getHealthCheckProviderOptions(): Promise<ApiResponse<IntegrationSyncLogProviderOption[]>> {
    return get(`${BASE}/health-checks/options/providers`)
  }
}
