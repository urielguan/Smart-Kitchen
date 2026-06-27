package com.xykj.device.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.device.dto.DataLogQueryDTO;
import com.xykj.device.dto.DataLogReceiveDTO;
import com.xykj.device.service.DeviceDataLogService;
import com.xykj.device.vo.DataLogVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 设备数据采集日志控制器
 * API路径: /api/v1/device/data-logs
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/device/data-logs")
@RequiredArgsConstructor
public class DeviceDataLogController {

    private final DeviceDataLogService dataLogService;

    /**
     * 分页查询设备数据采集日志
     * GET /api/v1/device/data-logs
     */
    @GetMapping
    public R<PageResult<DataLogVO>> list(DataLogQueryDTO query) {
        Page<DataLogVO> page = dataLogService.list(query);
        return R.ok(PageResult.of(page));
    }

    /**
     * 接收设备数据
     * POST /api/v1/device/data-logs
     */
    @PostMapping
    public R<Map<String, Object>> receive(@Valid @RequestBody DataLogReceiveDTO dto) {
        Long id = dataLogService.receive(dto);
        return R.ok(Map.of("id", id));
    }
}
