package com.xykj.scm.controller;

import com.xykj.common.result.R;
import com.xykj.scm.service.SupplierQualificationAlertService;
import com.xykj.scm.vo.SupplierQualificationAlertVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 供应商资质临期提醒接口
 */
@RestController
@RequestMapping("/api/v1/scm/supplier-qualification-alerts")
@RequiredArgsConstructor
public class SupplierQualificationAlertController {

    private final SupplierQualificationAlertService supplierQualificationAlertService;

    /**
     * 查询当前用户可见的供应商资质临期提醒
     */
    @GetMapping
    public R<List<SupplierQualificationAlertVO>> list(
            @RequestParam(defaultValue = "10") int limit
    ) {
        return R.ok(supplierQualificationAlertService.listCurrentAlerts(limit));
    }

    /**
     * 查询当前用户可见的供应商资质临期提醒数量
     */
    @GetMapping("/count")
    public R<Map<String, Integer>> count() {
        return R.ok(Map.of("count", supplierQualificationAlertService.countCurrentAlerts()));
    }
}
