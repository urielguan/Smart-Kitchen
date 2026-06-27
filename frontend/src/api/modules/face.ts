/**
 * 人脸识别API
 */
import { get, post, put, del } from '@/api'
import type { FaceEnrollPayload, FaceRecognizePayload, FaceFeature, FaceRecognizeResult } from '@/types/face'
import type { ApiResponse } from '@/types'

const API_PREFIX = '/v1/health/face'

/**
 * 人脸录入
 * POST /api/v1/health/face/enroll
 */
export function enrollFace(data: FaceEnrollPayload) {
  return post<FaceFeature>(`${API_PREFIX}/enroll`, data)
}

/**
 * 人脸识别
 * POST /api/v1/health/face/recognize
 */
export function recognizeFace(data: FaceRecognizePayload) {
  return post<FaceRecognizeResult>(`${API_PREFIX}/recognize`, data)
}

/**
 * 获取员工人脸信息
 * GET /api/v1/health/face/employee/{employeeId}
 */
export function getFaceByEmployeeId(employeeId: number) {
  return get<FaceFeature>(`${API_PREFIX}/employee/${employeeId}`)
}

/**
 * 获取人脸列表
 * GET /api/v1/health/face/list
 */
export function getFaceList(orgId?: number) {
  return get<FaceFeature[]>(`${API_PREFIX}/list`, { orgId })
}

/**
 * 删除员工人脸
 * DELETE /api/v1/health/face/employee/{employeeId}
 */
export function deleteFaceByEmployeeId(employeeId: number) {
  return del<boolean>(`${API_PREFIX}/employee/${employeeId}`)
}

/**
 * 更新人脸启用状态
 * PUT /api/v1/health/face/employee/{employeeId}/status
 */
export function updateFaceStatus(employeeId: number, isActive: boolean) {
  return put<boolean>(`${API_PREFIX}/employee/${employeeId}/status?isActive=${isActive}`)
}

/**
 * 检查员工是否已录入人脸
 * GET /api/v1/health/face/employee/{employeeId}/enrolled
 */
export function checkFaceEnrolled(employeeId: number) {
  return get<boolean>(`${API_PREFIX}/employee/${employeeId}/enrolled`)
}
