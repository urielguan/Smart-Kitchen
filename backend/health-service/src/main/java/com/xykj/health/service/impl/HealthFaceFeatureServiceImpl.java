package com.xykj.health.service.impl;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xykj.common.annotation.AuditLog;
import com.xykj.common.context.UserContext;
import com.xykj.common.enums.AuditModule;
import com.xykj.common.enums.AuditOperationType;
import com.xykj.common.service.AuditLogService;
import com.xykj.health.dto.FaceEnrollDTO;
import com.xykj.health.dto.FaceRecognizeDTO;
import com.xykj.health.entity.HealthFaceFeature;
import com.xykj.health.mapper.HealthFaceFeatureMapper;
import com.xykj.health.service.HealthFaceFeatureService;
import com.xykj.health.vo.FaceFeatureVO;
import com.xykj.health.vo.FaceRecognizeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 人脸特征管理服务实现
 * <p>
 * 当前为模拟实现，预留接口便于对接真实AI服务（百度AI、腾讯云人脸核身等）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthFaceFeatureServiceImpl implements HealthFaceFeatureService {

    private static final String FEATURE_VERSION = "v1.0";
    private static final BigDecimal DEFAULT_MATCH_THRESHOLD = new BigDecimal("80");
    private static final int FEATURE_DIMENSION = 128;

    /** AES-256 encryptor for sensitive face data (feature vectors, image URLs) */
    private static final AES FACE_DATA_AES = SecureUtil.aes("HealthFaceAES256Key!".getBytes(StandardCharsets.UTF_8));

    private final HealthFaceFeatureMapper faceFeatureMapper;
    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(
            module = AuditModule.HEALTH_FACE_FEATURE,
            operationType = AuditOperationType.CREATE,
            targetId = "#result.id",
            desc = "'人脸录入，员工ID：' + #dto.employeeId"
    )
    public FaceFeatureVO enroll(FaceEnrollDTO dto) {
        log.info("开始人脸录入, employeeId={}, employeeName={}", dto.getEmployeeId(), dto.getEmployeeName());

        // 1. 检查是否已录入
        LambdaQueryWrapper<HealthFaceFeature> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(HealthFaceFeature::getEmployeeId, dto.getEmployeeId());
        HealthFaceFeature existing = faceFeatureMapper.selectOne(existWrapper);

        // 2. 调用AI服务提取人脸特征（模拟实现）
        FaceExtractionResult result = extractFaceFeature(dto.getFaceImageBase64());
        if (!result.success) {
            String failReason = result.failReason;
            // 针对不同的质量检测失败场景抛出明确的错误信息
            if (failReason.contains("未检测到人脸")) {
                throw new RuntimeException("未检测到人脸，请确保正脸拍摄");
            }
            if (failReason.contains("多张人脸")) {
                throw new RuntimeException("检测到多张人脸，请确保只有一人");
            }
            if (failReason.contains("质量不合格")) {
                throw new RuntimeException("人脸照片质量不合格，请重新拍摄");
            }
            throw new RuntimeException("人脸特征提取失败: " + failReason);
        }

        // 3. 保存或更新人脸特征
        HealthFaceFeature feature;
        if (existing != null) {
            feature = existing;
            feature.setFaceImageUrl(saveFaceImage(dto.getFaceImageBase64(), dto.getEmployeeId()));
            feature.setFaceFeatureVector(encryptData(result.featureVector));
            feature.setQualityScore(result.qualityScore);
            feature.setFeatureVersion(FEATURE_VERSION);
            feature.setIsActive(1);
            feature.setEnrolledAt(LocalDateTime.now());
            feature.setOrgId(UserContext.getOrgId() != null ? UserContext.getOrgId() : (dto.getOrgId() != null ? dto.getOrgId() : 1L));
            faceFeatureMapper.updateById(feature);
            log.info("人脸特征更新成功, employeeId={}", dto.getEmployeeId());
        } else {
            feature = new HealthFaceFeature();
            feature.setEmployeeId(dto.getEmployeeId());
            feature.setFaceImageUrl(saveFaceImage(dto.getFaceImageBase64(), dto.getEmployeeId()));
            feature.setFaceFeatureVector(encryptData(result.featureVector));
            feature.setQualityScore(result.qualityScore);
            feature.setFeatureVersion(FEATURE_VERSION);
            feature.setIsActive(1);
            feature.setEnrolledAt(LocalDateTime.now());
            feature.setOrgId(UserContext.getOrgId() != null ? UserContext.getOrgId() : (dto.getOrgId() != null ? dto.getOrgId() : 1L));
            faceFeatureMapper.insert(feature);
            log.info("人脸特征录入成功, employeeId={}", dto.getEmployeeId());
        }

        // 4. 更新员工表的人脸录入状态
        updateEmployeeFaceEnrolled(dto.getEmployeeId(), true);

        return buildFaceFeatureVO(feature, dto.getEmployeeName());
    }

    @Override
    public FaceRecognizeVO recognize(FaceRecognizeDTO dto) {
        log.info("开始人脸识别, expectedEmployeeId={}", dto.getExpectedEmployeeId());

        // 1. 提取人脸特征
        FaceExtractionResult result = extractFaceFeature(dto.getFaceImageBase64());
        if (!result.success) {
            return FaceRecognizeVO.builder()
                    .success(false)
                    .failReason(result.failReason)
                    .qualityScore(result.qualityScore)
                    .build();
        }

        // 2. 获取匹配阈值
        BigDecimal threshold = dto.getMatchThreshold() != null
                ? new BigDecimal(dto.getMatchThreshold().toString())
                : DEFAULT_MATCH_THRESHOLD;

        // 3. 1:1验证 or 1:N搜索
        if (dto.getExpectedEmployeeId() != null) {
            return verifyWithExpectedEmployee(dto.getExpectedEmployeeId(), result.featureVector, threshold, result.qualityScore);
        }
        return searchBestMatch(result.featureVector, threshold, result.qualityScore, dto.getOrgId());
    }

    @Override
    public FaceFeatureVO getByEmployeeId(Long employeeId) {
        LambdaQueryWrapper<HealthFaceFeature> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HealthFaceFeature::getEmployeeId, employeeId);
        wrapper.eq(HealthFaceFeature::getIsActive, 1);
        HealthFaceFeature feature = faceFeatureMapper.selectOne(wrapper);
        if (feature == null) {
            return null;
        }
        return buildFaceFeatureVO(feature, getEmployeeName(employeeId));
    }

    @Override
    public List<FaceFeatureVO> listAll(Long orgId) {
        LambdaQueryWrapper<HealthFaceFeature> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HealthFaceFeature::getIsActive, 1);
        if (orgId != null) {
            wrapper.eq(HealthFaceFeature::getOrgId, orgId);
        }
        wrapper.orderByDesc(HealthFaceFeature::getEnrolledAt);

        List<HealthFaceFeature> features = faceFeatureMapper.selectList(wrapper);
        Map<Long, String> employeeNameMap = getEmployeeNames(
                features.stream().map(HealthFaceFeature::getEmployeeId).collect(Collectors.toList())
        );

        return features.stream()
                .map(f -> buildFaceFeatureVO(f, employeeNameMap.get(f.getEmployeeId())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteByEmployeeId(Long employeeId) {
        log.info("删除人脸特征, employeeId={}", employeeId);
        LambdaQueryWrapper<HealthFaceFeature> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HealthFaceFeature::getEmployeeId, employeeId);
        int deleted = faceFeatureMapper.delete(wrapper);
        if (deleted > 0) {
            updateEmployeeFaceEnrolled(employeeId, false);

            auditLogService.log(AuditModule.HEALTH_FACE_FEATURE, AuditOperationType.DELETE,
                    null, null, "删除人脸特征，员工ID：" + employeeId, null, null);

            return true;
        }
        return false;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateActiveStatus(Long employeeId, boolean isActive) {
        LambdaUpdateWrapper<HealthFaceFeature> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(HealthFaceFeature::getEmployeeId, employeeId);
        wrapper.set(HealthFaceFeature::getIsActive, isActive ? 1 : 0);
        wrapper.set(HealthFaceFeature::getUpdatedAt, LocalDateTime.now());
        return faceFeatureMapper.update(null, wrapper) > 0;
    }

    @Override
    public boolean isEnrolled(Long employeeId) {
        LambdaQueryWrapper<HealthFaceFeature> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HealthFaceFeature::getEmployeeId, employeeId);
        wrapper.eq(HealthFaceFeature::getIsActive, 1);
        return faceFeatureMapper.selectCount(wrapper) > 0;
    }

    // ==================== 私有方法 ====================

    /**
     * 检测图片中的人脸数量（模拟实现）
     * <p>
     * 基于base64图像数据长度进行模拟判断：
     * - 空/null: 无图片，无人脸
     * - 非常大的图像: 模拟检测到多人
     * - 正常图像: 单张人脸
     */
    private int detectFaceCount(String base64Image) {
        if (base64Image == null || base64Image.trim().isEmpty()) {
            return 0;
        }
        // 模拟：非常大的base64数据（超过200KB）视为包含多张人脸
        // 正常单人人脸照片通常在20KB-150KB之间
        int estimatedSizeBytes = base64Image.length() * 3 / 4;
        if (estimatedSizeBytes > 200 * 1024) {
            // 返回2-4之间的随机数，模拟多张人脸
            return 2 + new Random().nextInt(3);
        }
        return 1;
    }

    /**
     * 分析图像质量（模拟实现）
     * <p>
     * 基于base64长度模拟亮度、清晰度等质量指标
     */
    private ImageQualityResult analyzeImageQuality(String base64Image) {
        ImageQualityResult result = new ImageQualityResult();

        if (base64Image == null || base64Image.trim().isEmpty()) {
            result.brightness = 0;
            result.clarity = 0;
            result.faceSize = "too_small";
            result.overallScore = 0;
            return result;
        }

        int length = base64Image.length();

        // 模拟亮度分析：中等长度的base64通常对应曝光正常的照片
        // 过短可能过暗，过长可能过曝
        if (length < 5000) {
            result.brightness = 20 + Math.random() * 20;  // 20-40, 偏暗
        } else if (length < 50000) {
            result.brightness = 60 + Math.random() * 30;  // 60-90, 正常
        } else {
            result.brightness = 50 + Math.random() * 40;  // 50-90, 可能过曝
        }

        // 模拟清晰度分析：较长的base64通常包含更多细节
        if (length < 3000) {
            result.clarity = 15 + Math.random() * 25;  // 15-40, 模糊
        } else if (length < 80000) {
            result.clarity = 65 + Math.random() * 30;  // 65-95, 清晰
        } else {
            result.clarity = 50 + Math.random() * 35;  // 50-85, 轻微模糊
        }

        // 模拟人脸大小判断
        if (length < 4000) {
            result.faceSize = "too_small";
        } else if (length > 150000) {
            result.faceSize = "too_large";
        } else {
            result.faceSize = "ok";
        }

        // 计算综合评分
        result.overallScore = (result.brightness + result.clarity) / 2;

        return result;
    }

    private FaceExtractionResult extractFaceFeature(String faceImageBase64) {
        // 1. 检测人脸数量
        int faceCount = detectFaceCount(faceImageBase64);
        if (faceCount == 0) {
            return new FaceExtractionResult(false, "未检测到人脸，请确保正脸拍摄", null, BigDecimal.ZERO);
        }
        if (faceCount > 1) {
            return new FaceExtractionResult(false, "检测到多张人脸，请确保只有一人", null, BigDecimal.ZERO);
        }

        // 2. 分析图像质量
        ImageQualityResult quality = analyzeImageQuality(faceImageBase64);
        if (quality.overallScore < 60) {
            return new FaceExtractionResult(false, "人脸照片质量不合格，请重新拍摄", null,
                    BigDecimal.valueOf(quality.overallScore).setScale(2, RoundingMode.HALF_UP));
        }

        // 3. 活体检测（模拟）
        boolean isLive = Math.random() > 0.05;
        if (!isLive) {
            return new FaceExtractionResult(false, "活体检测未通过，请使用真实人脸", null,
                    BigDecimal.valueOf(quality.overallScore).setScale(2, RoundingMode.HALF_UP));
        }

        // 4. 生成特征向量
        String featureVector = generateMockFeatureVector();
        log.info("人脸特征提取成功, qualityScore={}, brightness={}, clarity={}, faceSize={}",
                quality.overallScore, quality.brightness, quality.clarity, quality.faceSize);
        return new FaceExtractionResult(true, null, featureVector,
                BigDecimal.valueOf(quality.overallScore).setScale(2, RoundingMode.HALF_UP));
    }

    private BigDecimal calculateSimilarity(String vector1, String vector2) {
        double similarity = 70 + Math.random() * 30;
        return BigDecimal.valueOf(similarity).setScale(2, RoundingMode.HALF_UP);
    }

    private FaceRecognizeVO verifyWithExpectedEmployee(Long expectedEmployeeId, String inputVector,
                                                        BigDecimal threshold, BigDecimal qualityScore) {
        LambdaQueryWrapper<HealthFaceFeature> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HealthFaceFeature::getEmployeeId, expectedEmployeeId);
        wrapper.eq(HealthFaceFeature::getIsActive, 1);
        HealthFaceFeature storedFeature = faceFeatureMapper.selectOne(wrapper);

        if (storedFeature == null) {
            return FaceRecognizeVO.builder()
                    .success(false)
                    .employeeId(expectedEmployeeId)
                    .failReason("该员工尚未录入人脸")
                    .qualityScore(qualityScore)
                    .build();
        }

        BigDecimal matchScore = calculateSimilarity(inputVector, decryptData(storedFeature.getFaceFeatureVector()));
        boolean verified = matchScore.compareTo(threshold) >= 0;
        String employeeName = getEmployeeName(expectedEmployeeId);
        updateLastUsedTime(expectedEmployeeId);

        return FaceRecognizeVO.builder()
                .success(true)
                .verified(verified)
                .employeeId(expectedEmployeeId)
                .employeeName(employeeName)
                .matchScore(matchScore)
                .qualityScore(qualityScore)
                .isLive(true)
                .failReason(verified ? null : "人脸匹配度低于阈值，身份验证失败")
                .build();
    }

    private FaceRecognizeVO searchBestMatch(String inputVector, BigDecimal threshold,
                                            BigDecimal qualityScore, Long orgId) {
        LambdaQueryWrapper<HealthFaceFeature> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HealthFaceFeature::getIsActive, 1);
        if (orgId != null) {
            wrapper.eq(HealthFaceFeature::getOrgId, orgId);
        }
        List<HealthFaceFeature> allFeatures = faceFeatureMapper.selectList(wrapper);

        if (allFeatures.isEmpty()) {
            return FaceRecognizeVO.builder()
                    .success(false)
                    .failReason("系统中没有人脸记录")
                    .qualityScore(qualityScore)
                    .build();
        }

        HealthFaceFeature bestMatch = null;
        BigDecimal bestScore = BigDecimal.ZERO;

        for (HealthFaceFeature feature : allFeatures) {
            BigDecimal score = calculateSimilarity(inputVector, decryptData(feature.getFaceFeatureVector()));
            if (score.compareTo(bestScore) > 0) {
                bestScore = score;
                bestMatch = feature;
            }
        }

        if (bestMatch == null || bestScore.compareTo(threshold) < 0) {
            return FaceRecognizeVO.builder()
                    .success(false)
                    .matchScore(bestScore)
                    .failReason("未找到匹配的员工，匹配度=" + bestScore + "%，阈值=" + threshold + "%")
                    .qualityScore(qualityScore)
                    .isLive(true)
                    .build();
        }

        String employeeName = getEmployeeName(bestMatch.getEmployeeId());
        updateLastUsedTime(bestMatch.getEmployeeId());

        return FaceRecognizeVO.builder()
                .success(true)
                .verified(true)
                .employeeId(bestMatch.getEmployeeId())
                .employeeName(employeeName)
                .matchScore(bestScore)
                .qualityScore(qualityScore)
                .isLive(true)
                .build();
    }

    private void updateLastUsedTime(Long employeeId) {
        LambdaUpdateWrapper<HealthFaceFeature> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(HealthFaceFeature::getEmployeeId, employeeId);
        wrapper.set(HealthFaceFeature::getLastUsedAt, LocalDateTime.now());
        faceFeatureMapper.update(null, wrapper);
    }

    private String saveFaceImage(String base64Image, Long employeeId) {
        String rawUrl = "/upload/face/" + employeeId + "_" + System.currentTimeMillis() + ".jpg";
        return encryptData(rawUrl);
    }

    private void updateEmployeeFaceEnrolled(Long employeeId, boolean enrolled) {
        try {
            String sql = "UPDATE sys_employee SET face_enrolled = ?, updated_at = NOW() WHERE id = ?";
            jdbcTemplate.update(sql, enrolled ? 1 : 0, employeeId);
        } catch (Exception e) {
            log.warn("更新员工人脸录入状态失败, employeeId={}", employeeId, e);
        }
    }

    private String getEmployeeName(Long employeeId) {
        try {
            String sql = "SELECT employee_name FROM sys_employee WHERE id = ?";
            return jdbcTemplate.queryForObject(sql, String.class, employeeId);
        } catch (Exception e) {
            return "未知员工";
        }
    }

    private Map<Long, String> getEmployeeNames(List<Long> employeeIds) {
        if (employeeIds == null || employeeIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> result = new HashMap<>();
        try {
            String placeholders = employeeIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            String sql = "SELECT id, employee_name FROM sys_employee WHERE id IN (" + placeholders + ")";
            jdbcTemplate.query(sql, rs -> {
                result.put(rs.getLong("id"), rs.getString("employee_name"));
            });
        } catch (Exception e) {
            log.warn("批量获取员工姓名失败", e);
        }
        return result;
    }

    private String generateMockFeatureVector() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < FEATURE_DIMENSION; i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format("%.6f", random.nextDouble() * 2 - 1));
        }
        return sb.toString();
    }

    private FaceFeatureVO buildFaceFeatureVO(HealthFaceFeature feature, String employeeName) {
        return FaceFeatureVO.builder()
                .id(feature.getId())
                .employeeId(feature.getEmployeeId())
                .employeeName(employeeName)
                .faceImageUrl(decryptData(feature.getFaceImageUrl()))
                .qualityScore(feature.getQualityScore())
                .isActive(feature.getIsActive())
                .enrolledAt(feature.getEnrolledAt())
                .lastUsedAt(feature.getLastUsedAt())
                .featureVersion(feature.getFeatureVersion())
                .orgId(feature.getOrgId())
                .build();
    }

    /**
     * Encrypt plaintext using AES-256.
     *
     * @param plaintext raw data to encrypt
     * @return Base64-encoded ciphertext, or null if input is null/empty
     */
    private String encryptData(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        return FACE_DATA_AES.encryptBase64(plaintext, StandardCharsets.UTF_8);
    }

    /**
     * Decrypt AES-256 ciphertext back to plaintext.
     *
     * @param ciphertext Base64-encoded encrypted data
     * @return decrypted plaintext, or null if input is null/empty
     */
    private String decryptData(String ciphertext) {
        if (ciphertext == null || ciphertext.isEmpty()) {
            return ciphertext;
        }
        try {
            return FACE_DATA_AES.decryptStr(ciphertext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // If decryption fails the value was likely stored before encryption was enabled
            log.warn("人脸数据解密失败，返回原始值");
            return ciphertext;
        }
    }

    private static class FaceExtractionResult {
        boolean success;
        String failReason;
        String featureVector;
        BigDecimal qualityScore;

        FaceExtractionResult(boolean success, String failReason, String featureVector, BigDecimal qualityScore) {
            this.success = success;
            this.failReason = failReason;
            this.featureVector = featureVector;
            this.qualityScore = qualityScore;
        }
    }

    /**
     * 图像质量分析结果
     */
    private static class ImageQualityResult {
        double brightness;     // 亮度 (0-100)
        double clarity;        // 清晰度 (0-100)
        String faceSize;       // 人脸大小: "too_small" / "ok" / "too_large"
        double overallScore;   // 综合评分 (brightness + clarity) / 2
    }
}
