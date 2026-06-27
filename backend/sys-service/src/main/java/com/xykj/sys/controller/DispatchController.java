package com.xykj.sys.controller;

import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.sys.dto.DispatchQueryDTO;
import com.xykj.sys.dto.ProcessFormDTO;
import com.xykj.sys.service.DispatchService;
import com.xykj.sys.vo.DispatchDetailVO;
import com.xykj.sys.vo.DispatchVO;
import com.xykj.sys.vo.WorkOrderRecordVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 派单Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/sys/dispatches")
@RequiredArgsConstructor
public class DispatchController {

    private final DispatchService dispatchService;

    /**
     * 获取派单列表
     */
    @GetMapping
    public R<PageResult<DispatchVO>> list(DispatchQueryDTO query) {
        PageResult<DispatchVO> result = dispatchService.list(query);
        return R.ok(result);
    }

    /**
     * 获取派单详情
     */
    @GetMapping("/{id}")
    public R<DispatchDetailVO> getDetail(@PathVariable Long id) {
        DispatchDetailVO vo = dispatchService.getDetail(id);
        return R.ok(vo);
    }

    /**
     * 获取处理记录列表
     */
    @GetMapping("/{id}/records")
    public R<Map<String, Object>> getRecords(@PathVariable Long id) {
        List<WorkOrderRecordVO> records = dispatchService.getRecords(id);
        Map<String, Object> result = new HashMap<>();
        result.put("total", records.size());
        result.put("list", records);
        return R.ok(result);
    }

    /**
     * 处理工单
     */
    @PostMapping("/{id}/process")
    public R<Map<String, Object>> process(@PathVariable Long id, @Valid @RequestBody ProcessFormDTO dto) {
        Map<String, Object> result = dispatchService.process(id, dto);
        return R.ok(result);
    }
}
