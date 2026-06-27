/** 供应商状态 */
export type SupplierStatus = 'draft' | 'pending' | 'active' | 'rejected' | 'disabled' | 'cancelled'
export type SupplierQualificationExpiryStatus = 'valid' | 'near_expire' | 'expired'

/** 供应商资质文件 */
export interface SupplierQualificationFile {
  id: number
  name: string
  size: string
  url?: string | null
}

/** 供应商信息 */
export interface Supplier {
  id: number
  supplierCode: string
  supplierName: string
  contactName: string
  contactPhone: string
  contactEmail: string
  address: string
  /** 供应商类型（单选） */
  supplierType: string
  /** 社会信用代码 */
  unifiedCreditCode: string
  /** 银行账号 */
  bankAccount: string
  /** 开户行 */
  bankName: string
  licenseNo: string
  licenseExpiresAt: string | null
  licenseExpiryStatus?: SupplierQualificationExpiryStatus | null
  licenseRemainingDays?: number | null
  foodLicenseNo: string
  foodLicenseExpiresAt: string | null
  foodLicenseExpiryStatus?: SupplierQualificationExpiryStatus | null
  foodLicenseRemainingDays?: number | null
  creditScore: number
  scoreQualification: number | null
  scoreQuality: number | null
  scorePrice: number | null
  scoreDelivery: number | null
  scoreUpdatedAt?: string | null
  scoreStatisticsPeriod?: string | null
  aiLevel?: string | null
  recommendPriority?: string | null
  riskWarningLevel?: 'low' | 'medium' | 'high' | null
  scoreQualitySampleInsufficient?: boolean
  scorePriceSampleInsufficient?: boolean
  scoreDeliverySampleInsufficient?: boolean
  optimizationSuggestions?: string[]
  status: SupplierStatus
  disableReason: string | null
  cancelReason: string | null
  qualificationFiles: SupplierQualificationFile[]
  auditAt: string | null
  auditRemark: string | null
  createdByName?: string | null
  updatedByName?: string | null
  auditByName?: string | null
  tenantId: number
  createdAt: string
  updatedAt: string
}

/** 供应商表单 */
export interface SupplierForm {
  supplierCode: string
  supplierName: string
  contactName: string
  contactPhone: string
  contactEmail: string
  address: string
  /** 供应商类型（单选） */
  supplierType: string
  /** 社会信用代码 */
  unifiedCreditCode: string
  /** 银行账号 */
  bankAccount: string
  /** 开户行 */
  bankName: string
  licenseNo: string
  licenseExpiresAt: string | null
  foodLicenseNo: string
  foodLicenseExpiresAt: string | null
  status: SupplierStatus
  disableReason?: string | null
  qualificationFiles: SupplierQualificationFile[]
}

export interface SupplierDisablePayload extends SupplierForm {
  reason: string
}

export interface SupplierCancelPayload {
  reason: string
}

/** 供应商查询参数 */
export interface SupplierQuery {
  keyword?: string
  status?: SupplierStatus | ''
}

/** 供应商统计 */
export interface SupplierStatistics {
  total: number
  activeCount: number
  pendingCount: number
  nearExpireCount: number
}

/** 供应商导入失败明细 */
export interface SupplierImportFailure {
  rowNum: number
  supplierCode: string
  supplierName: string
  supplierTypeRawValue?: string | null
  unifiedCreditCode: string
  documentType?: string | null
  documentNo?: string | null
  failedField: string
  errorMessage: string
}

/** 供应商导入结果 */
export interface SupplierImportResult {
  total: number
  successCount: number
  failCount: number
  hasErrors: boolean
  errorFileUrl: string | null
  failures: SupplierImportFailure[]
}

/** 供应商唯一性校验结果 */
export interface SupplierDuplicateCheckResult {
  supplierCodeDuplicate: boolean
  supplierCodeMessage: string | null
  supplierNameDuplicate: boolean
  supplierNameMessage: string | null
  licenseNoDuplicate: boolean
  licenseNoMessage: string | null
  foodLicenseNoDuplicate: boolean
  foodLicenseNoMessage: string | null
}

/** 供应商导入预校验冲突明细 */
export interface SupplierImportValidationConflict {
  rowNum: number
  field: string
  conflictValue: string | null
  message: string
}

/** 供应商导入预校验结果 */
export interface SupplierImportValidationResult {
  effectiveRowCount: number
  rowLimitExceeded: boolean
  duplicateConflicts: SupplierImportValidationConflict[]
}
