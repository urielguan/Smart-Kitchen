package com.xykj.sys.controller;

import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.sys.dto.ComplaintCreateDTO;
import com.xykj.sys.dto.ComplaintQueryDTO;
import com.xykj.sys.dto.DispatchFormDTO;
import com.xykj.sys.dto.SatisfactionFormDTO;
import com.xykj.sys.service.ComplaintService;
import com.xykj.sys.vo.ComplaintDetailVO;
import com.xykj.sys.vo.ComplaintVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 投诉Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/sys/complaints")
@RequiredArgsConstructor
public class ComplaintController {

    private final ComplaintService complaintService;

    /**
     * 获取投诉列表
     */
    @GetMapping
    public R<PageResult<ComplaintVO>> list(ComplaintQueryDTO query) {
        PageResult<ComplaintVO> result = complaintService.list(query);
        return R.ok(result);
    }

    /**
     * 获取投诉详情
     */
    @GetMapping("/{id}")
    public R<ComplaintDetailVO> getDetail(@PathVariable Long id) {
        ComplaintDetailVO vo = complaintService.getDetail(id);
        return R.ok(vo);
    }

    /**
     * 新增投诉
     */
    @PostMapping
    public R<Map<String, Object>> create(@Valid @RequestBody ComplaintCreateDTO dto) {
        Map<String, Object> result = complaintService.create(dto);
        return R.ok(result);
    }

    /**
     * 派单
     */
    @PostMapping("/{id}/dispatch")
    public R<Map<String, Object>> dispatch(@PathVariable Long id, @Valid @RequestBody DispatchFormDTO dto) {
        Map<String, Object> result = complaintService.dispatch(id, dto);
        return R.ok(result);
    }

    /**
     * 满意度评价
     */
    @PostMapping("/{id}/satisfaction")
    public R<Map<String, Object>> updateSatisfaction(@PathVariable Long id, @Valid @RequestBody SatisfactionFormDTO dto) {
        Map<String, Object> result = complaintService.updateSatisfaction(id, dto);
        return R.ok(result);
    }

    /**
     * 导出投诉
     */
    @GetMapping("/export")
    public void exportComplaints(ComplaintQueryDTO query, HttpServletResponse response) {
        complaintService.exportComplaints(query, response);
    }
}
