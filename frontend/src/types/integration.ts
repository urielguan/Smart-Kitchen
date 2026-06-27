export interface IntegrationMetricItem {
  code: string
  label: string
  value: number
}

export interface IntegrationProviderTemplateItem {
  id: number
  builtinFlag?: number
  providerCode: string
  providerName: string
  providerType: string
  authType: string
  protocolType: string
  callbackSupported: number
  filePullSupported: number
  sceneCodeList: string[]
  requestTemplate?: string
  responseTemplate?: string
  status: 'active' | 'inactive'
  remark?: string
  createdAt?: string
  updatedAt?: string
}

export interface IntegrationProviderTemplateQuery {
  keyword?: string
  providerType?: string
  status?: string
  pageNum?: number
  pageSize?: number
}

export interface IntegrationProviderTemplateForm {
  providerCode: string
  providerName: string
  providerType: string
  authType: string
  protocolType: string
  callbackSupported: number
  filePullSupported: number
  sceneCodes: string[]
  requestTemplate?: string
  responseTemplate?: string
  status: 'active' | 'inactive'
  remark?: string
}

export interface IntegrationSecretInput {
  secretKey: string
  secretValue: string
}

export interface IntegrationSecretMasked {
  secretKey: string
  secretMask: string
  encryptedFlag: number
}

export interface IntegrationModuleConfigItem {
  id: number
  orgId: number
  orgName?: string
  bizModule: string
  bizScene: string
  providerCode: string
  providerName?: string
  configName: string
  enabled: number
  defaultMode: 'manual' | 'third_party'
  allowDocumentSwitch: number
  forceThirdParty: number
  triggerStrategy: string
  allowManualFallback: number
  autoCoverEnabled: number
  autoCoverStrategy?: string
  allowManualConfirmCover: number
  attachmentPullEnabled: number
  callbackEnabled: number
  callbackUrl?: string
  externalNoFieldRule?: string
  accessTokenUrl?: string
  refreshTokenUrl?: string
  tokenRequestMethod?: string
  syncFrequencyMinutes?: number
  scheduleCron?: string
  timeoutMs?: number
  retryMaxCount?: number
  lastSyncAt?: string
  lastSyncStatus?: string
  lastErrorMessage?: string
  remark?: string
  secrets: IntegrationSecretMasked[]
  createdAt?: string
  updatedAt?: string
}

export interface IntegrationModuleConfigQuery {
  keyword?: string
  orgId?: number
  bizModule?: string
  bizScene?: string
  providerCode?: string
  enabled?: number | ''
  defaultMode?: string
  pageNum?: number
  pageSize?: number
}

export interface IntegrationModuleConfigForm {
  orgId: number | null
  bizModule: string
  bizScene: string
  providerCode: string
  configName: string
  enabled: number
  defaultMode: 'manual' | 'third_party'
  allowDocumentSwitch: number
  forceThirdParty: number
  triggerStrategy: string
  allowManualFallback: number
  autoCoverEnabled: number
  autoCoverStrategy: string
  allowManualConfirmCover: number
  attachmentPullEnabled: number
  callbackEnabled: number
  callbackUrl?: string
  externalNoFieldRule?: string
  accessTokenUrl?: string
  refreshTokenUrl?: string
  tokenRequestMethod: string
  syncFrequencyMinutes: number
  scheduleCron?: string
  timeoutMs: number
  retryMaxCount: number
  remark?: string
  secrets: IntegrationSecretInput[]
}

export interface IntegrationFieldMappingItem {
  id: number
  configId: number
  configName?: string
  providerCode?: string
  sourceField?: string
  sourcePath?: string
  targetField: string
  transformType: string
  transformRule?: string
  defaultValue?: string
  requiredFlag: number
  sortNo: number
  enabled: number
  errorStrategy?: string
  remark?: string
  createdAt?: string
  updatedAt?: string
}

export interface IntegrationFieldMappingQuery {
  configId?: number
  keyword?: string
  pageNum?: number
  pageSize?: number
}

export interface IntegrationFieldMappingForm {
  configId: number | null
  sourceField?: string
  sourcePath?: string
  targetField: string
  transformType: string
  transformRule?: string
  defaultValue?: string
  requiredFlag: number
  sortNo: number
  enabled: number
  errorStrategy: string
  remark?: string
}

export interface IntegrationFieldMappingDuplicateCheckForm {
  id?: number | null
  configId: number | null
  targetField: string
  enabled: number
}

export interface IntegrationStatusMappingItem {
  id: number
  configId: number
  configName?: string
  providerCode?: string
  sourceStatusCode: string
  sourceStatusName: string
  targetStatusCode: string
  finishFlag: number
  triggerBusinessAction: number
  actionCode?: string
  writeAttachmentFlag: number
  sortNo: number
  status?: number
  enabled: number
  remark?: string
  createdAt?: string
  updatedAt?: string
}

export interface IntegrationStatusMappingQuery {
  configId?: number
  keyword?: string
  pageNum?: number
  pageSize?: number
}

export interface IntegrationStatusMappingForm {
  configId: number | null
  sourceStatusCode: string
  sourceStatusName: string
  targetStatusCode: string
  finishFlag: number
  triggerBusinessAction: number
  actionCode?: string
  writeAttachmentFlag: number
  sortNo: number
  enabled: number
  remark?: string
}

export interface IntegrationStatusMappingDuplicateCheckForm {
  id?: number | null
  configId: number | null
  sourceStatusCode: string
}

export interface IntegrationOverview {
  enabledIntegrationCount: number
  syncSuccessCount: number
  syncFailureCount: number
  callbackSuccessRate: number
  averageDurationMs: number
  moduleDistribution: IntegrationMetricItem[]
  syncTrend: IntegrationMetricItem[]
  providerFailureDistribution: IntegrationMetricItem[]
  recentFailedRecords: IntegrationSyncLogItem[]
  recentTimeoutRecords: IntegrationSyncLogItem[]
  recentSignFailedRecords: IntegrationCallbackLogItem[]
}

export interface IntegrationSyncTaskItem {
  id: number
  taskNo: string
  bindingId?: number
  configId: number
  configName?: string
  providerCode?: string
  providerName?: string
  taskType: string
  triggerType: string
  taskStatus: string
  planExecuteAt?: string
  startAt?: string
  finishAt?: string
  retryCount?: number
  operatorId?: number
  operatorName?: string
  bizId: number
  bizNo: string
  bizModule: string
  bizScene: string
  externalNo?: string
  resultMessage?: string
  orgId: number
  orgName?: string
  retryMaxCount?: number
  retryAvailable?: boolean
  retryDisabledReason?: string
  createdAt?: string
}

export interface IntegrationSyncTaskQuery {
  orgId?: number
  keyword?: string
  bizModule?: string
  bizScene?: string
  providerCode?: string
  taskStatus?: string
  triggerType?: string
  pendingHandleOnly?: number
  pageNum?: number
  pageSize?: number
}

export interface IntegrationSyncTriggerForm {
  configId: number | null
  bizModule: string
  bizScene: string
  bizId: string
  bizNo: string
  externalNo: string
  maintenanceMode: 'manual' | 'third_party'
  modeSource: string
  modeLocked: number
  triggerType: string
  queryOnly: number
}

export interface IntegrationExecutionResult {
  taskId?: number
  taskNo?: string
  bindingId?: number
  syncStatus: string
  message: string
  normalizedPayload?: string
  downloadedFileCount?: number
  failedFileCount?: number
  skippedFileCount?: number
  reusedFileCount?: number
  fileTransferSummary?: string
  syncLogId?: number
  auditLogId?: number
  writeBackResult?: string
}

export interface IntegrationSyncLogItem {
  id: number
  taskId?: number
  taskNo?: string
  bindingId?: number
  configId: number
  configName?: string
  configNameSnapshot?: string
  providerCode?: string
  providerName?: string
  providerNameSnapshot?: string
  bizId: number
  bizNo: string
  bizModule: string
  bizScene: string
  externalNo?: string
  taskType?: string
  requestPayload?: string
  requestHeaders?: string
  requestBody?: string
  responsePayload?: string
  normalizedPayload?: string
  syncStatus: string
  errorCode?: string
  errorMessage?: string
  durationMs?: number
  auditLogId?: number
  resultMessage?: string
  writeBackResult?: string
  triggerType?: string
  operatorId?: number
  operatorName?: string
  orgId: number
  orgName?: string
  orgNameSnapshot?: string
  handleStatus?: string
  handledBy?: number
  handledByName?: string
  handledAt?: string
  handleRemark?: string
  createdAt?: string
}

export interface IntegrationSyncLogQuery {
  orgId?: number
  keyword?: string
  bizModule?: string
  bizScene?: string
  providerCode?: string
  syncStatus?: string
  triggerType?: string
  handleStatus?: string
  startTime?: string
  endTime?: string
  pageNum?: number
  pageSize?: number
}

export interface IntegrationSyncLogProviderOption {
  providerCode: string
  providerName: string
}

export interface IntegrationSyncLogHandleForm {
  handleStatus: string
  handleRemark?: string
}

export interface IntegrationCallbackLogItem {
  id: number
  bindingId?: number
  configId?: number
  configName?: string
  providerCode: string
  providerName?: string
  callbackUri?: string
  callbackHeaders?: string
  callbackPayload?: string
  clientIp?: string
  signResult?: string
  idempotentKey?: string
  processStatus?: string
  processResult?: string
  errorMessage?: string
  bizId?: number
  bizNo?: string
  bizModule?: string
  bizScene?: string
  externalNo?: string
  taskId?: number
  taskNo?: string
  syncLogId?: number
  auditLogId?: number
  orgId?: number
  orgName?: string
  createdAt?: string
}

export interface IntegrationCallbackLogQuery {
  orgId?: number
  bizModule?: string
  bizScene?: string
  providerCode?: string
  signResult?: string
  processStatus?: string
  startTime?: string
  endTime?: string
  keyword?: string
  pageNum?: number
  pageSize?: number
}

export interface IntegrationFileRecordItem {
  id: number
  bindingId?: number
  configId?: number
  configName?: string
  configNameSnapshot?: string
  bizModule: string
  bizScene: string
  bizId?: number
  bizNo?: string
  providerCode: string
  providerName?: string
  providerNameSnapshot?: string
  sourceFileName?: string
  sourceFileUrl?: string
  sourceUrlSignature?: string
  minioFileUrl?: string
  fileHash?: string
  fileSize?: string
  mimeType?: string
  downloadStatus?: string
  storageStatus?: string
  errorCode?: string
  errorMessage?: string
  taskId?: number
  taskNo?: string
  syncLogId?: number
  syncLogCreatedAt?: string
  syncLogStatus?: string
  syncLogResultMessage?: string
  syncLogErrorMessage?: string
  auditLogId?: number
  orgId?: number
  orgName?: string
  orgNameSnapshot?: string
  createdAt?: string
  updatedAt?: string
}

export interface IntegrationFileRecordQuery {
  orgId?: number
  bindingId?: number
  bizModule?: string
  bizScene?: string
  bizNo?: string
  providerCode?: string
  downloadStatus?: string
  storageStatus?: string
  startTime?: string
  endTime?: string
  keyword?: string
  pageNum?: number
  pageSize?: number
}

export interface IntegrationHealthCheckItem {
  configId: number
  configName: string
  orgId?: number
  orgName?: string
  bizModule?: string
  bizScene?: string
  providerCode: string
  providerName?: string
  enabled: number
  callbackEnabled?: number
  authSuccess?: boolean
  reachable?: boolean
  callbackReachable?: boolean
  authMessage?: string
  reachableMessage?: string
  callbackMessage?: string
  testMessage?: string
  lastTestAt?: string
  lastTestStatus?: string
  lastTestMessage?: string
  successRate24h?: number
  callbackSuccessRate24h?: number
  averageDurationMs24h?: number
  lastSyncAt?: string
  lastSyncStatus?: string
  lastErrorMessage?: string
  recentFailedLogs: IntegrationSyncLogItem[]
  recentTestLogs: IntegrationHealthCheckLogItem[]
}

export interface IntegrationHealthCheckLogItem {
  id: number
  testStatus?: string
  authSuccess?: boolean
  reachable?: boolean
  callbackReachable?: boolean
  authMessage?: string
  reachabilityMessage?: string
  callbackMessage?: string
  testMessage?: string
  errorCode?: string
  errorMessage?: string
  requestPayload?: string
  requestHeaders?: string
  requestBody?: string
  responsePayload?: string
  operatorId?: number
  operatorName?: string
  createdAt?: string
}

export interface IntegrationHealthCheckQuery {
  keyword?: string
  orgId?: number
  configId?: number
  bizModule?: string
  bizScene?: string
  providerCode?: string
  pageNum?: number
  pageSize?: number
}
