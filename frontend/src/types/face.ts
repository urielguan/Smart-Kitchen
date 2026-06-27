/**
 * 人脸特征相关类型定义
 */

/**
 * 人脸录入请求
 */
export interface FaceEnrollPayload {
  employeeId: number
  employeeName?: string
  faceImageBase64: string
  source?: 'web' | 'mobile' | 'terminal'
  orgId?: number
  tenantId?: number
}

/**
 * 人脸识别请求
 */
export interface FaceRecognizePayload {
  faceImageBase64: string
  expectedEmployeeId?: number
  matchThreshold?: number
  orgId?: number
}

/**
 * 人脸特征信息
 */
export interface FaceFeature {
  id: number
  employeeId: number
  employeeName: string
  faceImageUrl: string
  qualityScore: number
  isActive: number
  enrolledAt: string
  lastUsedAt?: string
  featureVersion: string
  orgId: number
}

/**
 * 人脸识别结果
 */
export interface FaceRecognizeResult {
  success: boolean
  verified: boolean
  employeeId?: number
  employeeName?: string
  matchScore?: number
  failReason?: string
  qualityScore?: number
  isLive?: boolean
}

/**
 * 人脸识别状态
 */
export type FaceRecognitionStatus = 'idle' | 'capturing' | 'recognizing' | 'success' | 'failed'

/**
 * 摄像头配置
 */
export interface CameraConfig {
  width: number
  height: number
  facingMode: 'user' | 'environment'
  quality: number // 0-1
}
