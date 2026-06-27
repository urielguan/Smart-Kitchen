import type { SupplierQualificationExpiryStatus } from '@/types/supplier'

export interface SupplierQualificationAlert {
  supplierId: number
  supplierCode: string
  supplierName: string
  title: string
  content: string
  daysRemaining: number | null
  qualificationNames: string[]
  licenseExpiresAt: string | null
  licenseExpiryStatus?: SupplierQualificationExpiryStatus | null
  licenseRemainingDays?: number | null
  foodLicenseExpiresAt: string | null
  foodLicenseExpiryStatus?: SupplierQualificationExpiryStatus | null
  foodLicenseRemainingDays?: number | null
  generatedAt: string
}
