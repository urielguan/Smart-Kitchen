package com.xykj.health.controller;

import com.xykj.common.result.R;
import com.xykj.health.dto.FaceEnrollDTO;
import com.xykj.health.dto.FaceRecognizeDTO;
import com.xykj.health.service.HealthFaceFeatureService;
import com.xykj.health.vo.FaceFeatureVO;
import com.xykj.health.vo.FaceRecognizeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 人脸特征管理控制器
 * API路径: /api/v1/health/face
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/health/face")
@RequiredArgsConstructor
@Tag(name = "人脸特征管理", description = "人脸录入、识别、管理接口")
public class FaceFeatureController {

    private final HealthFaceFeatureService faceFeatureService;

    /**
     * 人脸录入
     * POST /api/v1/health/face/enroll
     */
    @PostMapping("/enroll")
    @Operation(summary = "人脸录入", description = "为员工录入人脸特征，支持更新已有特征")
    public R<FaceFeatureVO> enroll(@Valid @RequestBody FaceEnrollDTO dto) {
        log.info("人脸录入请求: employeeId={}", dto.getEmployeeId());
        FaceFeatureVO vo = faceFeatureService.enroll(dto);
        return R.ok(vo);
    }

    /**
     * 人脸识别
     * POST /api/v1/health/face/recognize
     */
    @PostMapping("/recognize")
    @Operation(summary = "人脸识别", description = "通过人脸识别验证或搜索员工身份")
    public R<FaceRecognizeVO> recognize(@Valid @RequestBody FaceRecognizeDTO dto) {
        log.info("人脸识别请求: expectedEmployeeId={}", dto.getExpectedEmployeeId());
        FaceRecognizeVO vo = faceFeatureService.recognize(dto);
        return R.ok(vo);
    }

    /**
     * 获取员工人脸信息
     * GET /api/v1/health/face/employee/{employeeId}
     */
    @GetMapping("/employee/{employeeId}")
    @Operation(summary = "获取员工人脸信息", description = "根据员工ID获取人脸特征信息")
    public R<FaceFeatureVO> getByEmployeeId(
            @Parameter(description = "员工ID") @PathVariable Long employeeId) {
        FaceFeatureVO vo = faceFeatureService.getByEmployeeId(employeeId);
        if (vo == null) {
            return R.fail("该员工尚未录入人脸");
        }
        return R.ok(vo);
    }

    /**
     * 获取所有已录入人脸的员工列表
     * GET /api/v1/health/face/list
     */
    @GetMapping("/list")
    @Operation(summary = "获取人脸列表", description = "获取所有已录入人脸的员工列表")
    public R<List<FaceFeatureVO>> listAll(
            @Parameter(description = "组织ID") @RequestParam(required = false) Long orgId) {
        List<FaceFeatureVO> list = faceFeatureService.listAll(orgId);
        return R.ok(list);
    }

    /**
     * 删除员工人脸
     * DELETE /api/v1/health/face/employee/{employeeId}
     */
    @DeleteMapping("/employee/{employeeId}")
    @Operation(summary = "删除员工人脸", description = "删除指定员工的人脸特征数据")
    public R<Boolean> deleteByEmployeeId(
            @Parameter(description = "员工ID") @PathVariable Long employeeId) {
        log.info("删除人脸请求: employeeId={}", employeeId);
        boolean result = faceFeatureService.deleteByEmployeeId(employeeId);
        if (result) {
            return R.ok("删除成功", true);
        }
        return R.fail("删除失败，未找到该员工的人脸记录");
    }

    /**
     * 更新人脸启用状态
     * PUT /api/v1/health/face/employee/{employeeId}/status
     */
    @PutMapping("/employee/{employeeId}/status")
    @Operation(summary = "更新人脸状态", description = "启用或禁用员工人脸识别功能")
    public R<Boolean> updateStatus(
            @Parameter(description = "员工ID") @PathVariable Long employeeId,
            @Parameter(description = "是否启用") @RequestParam Boolean isActive) {
        log.info("更新人脸状态: employeeId={}, isActive={}", employeeId, isActive);
        boolean result = faceFeatureService.updateActiveStatus(employeeId, isActive);
        if (result) {
            return R.ok("状态更新成功", true);
        }
        return R.fail("状态更新失败");
    }

    /**
     * 检查员工是否已录入人脸
     * GET /api/v1/health/face/employee/{employeeId}/enrolled
     */
    @GetMapping("/employee/{employeeId}/enrolled")
    @Operation(summary = "检查人脸录入状态", description = "检查指定员工是否已录入人脸")
    public R<Boolean> isEnrolled(
            @Parameter(description = "员工ID") @PathVariable Long employeeId) {
        Boolean enrolled = faceFeatureService.isEnrolled(employeeId);
        return R.ok(enrolled);
    }
}
