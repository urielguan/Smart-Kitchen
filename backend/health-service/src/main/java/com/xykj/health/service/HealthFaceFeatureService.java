package com.xykj.health.service;

import com.xykj.health.dto.FaceEnrollDTO;
import com.xykj.health.dto.FaceRecognizeDTO;
import com.xykj.health.vo.FaceFeatureVO;
import com.xykj.health.vo.FaceRecognizeVO;

import java.util.List;

/**
 * 人脸特征管理服务接口
 */
public interface HealthFaceFeatureService {

    /**
     * 人脸录入
     * 提取人脸特征并存储
     *
     * @param dto 人脸录入请求
     * @return 人脸特征信息
     */
    FaceFeatureVO enroll(FaceEnrollDTO dto);

    /**
     * 人脸识别
     * 比对人脸特征，返回匹配结果
     *
     * @param dto 人脸识别请求
     * @return 识别结果
     */
    FaceRecognizeVO recognize(FaceRecognizeDTO dto);

    /**
     * 获取员工人脸特征信息
     *
     * @param employeeId 员工ID
     * @return 人脸特征信息
     */
    FaceFeatureVO getByEmployeeId(Long employeeId);

    /**
     * 获取所有已录入人脸的员工列表
     *
     * @param orgId 组织ID（可选）
     * @return 人脸特征列表
     */
    List<FaceFeatureVO> listAll(Long orgId);

    /**
     * 删除员工人脸特征
     *
     * @param employeeId 员工ID
     * @return 是否成功
     */
    boolean deleteByEmployeeId(Long employeeId);

    /**
     * 更新人脸启用状态
     *
     * @param employeeId 员工ID
     * @param isActive   是否启用
     * @return 是否成功
     */
    boolean updateActiveStatus(Long employeeId, boolean isActive);

    /**
     * 检查员工是否已录入人脸
     *
     * @param employeeId 员工ID
     * @return 是否已录入
     */
    boolean isEnrolled(Long employeeId);
}
