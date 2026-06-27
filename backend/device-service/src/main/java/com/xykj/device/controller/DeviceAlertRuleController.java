package com.xykj.device.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.device.dto.AlertRuleCreateDTO;
import com.xykj.device.dto.AlertRuleQueryDTO;
import com.xykj.device.dto.AlertRuleUpdateDTO;
import com.xykj.device.service.DeviceAlertRuleService;
import com.xykj.device.vo.AlertRuleVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 告警规则配置控制器
 * API路径: /api/v1/device/alert-rules
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/device/alert-rules")
@RequiredArgsConstructor
public class DeviceAlertRuleController {

    private final DeviceAlertRuleService alertRuleService;

    /**
     * 分页查询告警规则
     * GET /api/v1/device/alert-rules
     */
    @GetMapping
    public R<PageResult<AlertRuleVO>> list(AlertRuleQueryDTO query) {
        Page<AlertRuleVO> page = alertRuleService.list(query);
        return R.ok(PageResult.of(page));
    }

    /**
     * 获取告警规则详情
     * GET /api/v1/device/alert-rules/{id}
     */
    @GetMapping("/{id}")
    public R<AlertRuleVO> getDetail(@PathVariable Long id) {
        AlertRuleVO vo = alertRuleService.getDetail(id);
        return R.ok(vo);
    }

    /**
     * 创建告警规则
     * POST /api/v1/device/alert-rules
     */
    @PostMapping
    public R<Long> create(@Valid @RequestBody AlertRuleCreateDTO dto) {
        Long id = alertRuleService.create(dto);
        return R.ok(id);
    }

    /**
     * 更新告警规则
     * PUT /api/v1/device/alert-rules/{id}
     */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody AlertRuleUpdateDTO dto) {
        alertRuleService.update(id, dto);
        return R.ok();
    }

    /**
     * 删除告警规则
     * DELETE /api/v1/device/alert-rules/{id}
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        alertRuleService.delete(id);
        return R.ok();
    }

    /**
     * 切换启用/禁用状态
     * POST /api/v1/device/alert-rules/{id}/toggle-enabled
     */
    @PostMapping("/{id}/toggle-enabled")
    public R<Void> toggleEnabled(@PathVariable Long id) {
        alertRuleService.toggleEnabled(id);
        return R.ok();
    }
}
