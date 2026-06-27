package com.xykj.scm.controller;

import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.scm.dto.SupplierAuditDTO;
import com.xykj.scm.dto.SupplierCancelDTO;
import com.xykj.scm.dto.SupplierCreateDTO;
import com.xykj.scm.dto.SupplierDisableDTO;
import com.xykj.scm.dto.SupplierImportResultDTO;
import com.xykj.scm.dto.SupplierQueryDTO;
import com.xykj.scm.dto.SupplierUpdateDTO;
import com.xykj.scm.service.SupplierService;
import com.xykj.scm.vo.SupplierDuplicateCheckVO;
import com.xykj.scm.vo.SupplierImportValidationVO;
import com.xykj.scm.vo.SupplierQualificationFileVO;
import com.xykj.scm.vo.SupplierStatisticsVO;
import com.xykj.scm.vo.SupplierVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 供应商管理接口
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/scm/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    /**
     * 供应商列表
     */
    @GetMapping
    public R<PageResult<SupplierVO>> list(@Valid SupplierQueryDTO query) {
        return R.ok(supplierService.list(query));
    }

    /**
     * 供应商统计
     */
    @GetMapping("/statistics")
    public R<SupplierStatisticsVO> statistics(@Valid SupplierQueryDTO query) {
        return R.ok(supplierService.getStatistics(query));
    }

    /**
     * 供应商详情
     */
    @GetMapping("/{id:\\d+}")
    public R<SupplierVO> detail(@PathVariable Long id) {
        return R.ok(supplierService.getDetail(id));
    }

    /**
     * 上传供应商资质文件
     */
    @PostMapping("/files/upload")
    public R<SupplierQualificationFileVO> uploadQualificationFile(@RequestParam("file") MultipartFile file) {
        return R.ok(supplierService.uploadQualificationFile(file));
    }

    /**
     * 下载供应商资质文件
     */
    @GetMapping("/files/download")
    public void downloadQualificationFile(
            @RequestParam("fileUrl") String fileUrl,
            @RequestParam(value = "fileName", required = false) String fileName,
            HttpServletResponse response
    ) {
        supplierService.downloadQualificationFile(fileUrl, fileName, response);
    }

    /**
     * 下载供应商导入模板
     */
    @GetMapping("/import/template")
    public void downloadImportTemplate(HttpServletResponse response) {
        supplierService.downloadImportTemplate(response);
    }

    /**
     * 校验供应商编码/名称/证件号是否重复
     */
    @GetMapping("/check-duplicate")
    public R<SupplierDuplicateCheckVO> checkDuplicate(
            @RequestParam(value = "excludeId", required = false) Long excludeId,
            @RequestParam(value = "supplierCode", required = false) String supplierCode,
            @RequestParam(value = "supplierName", required = false) String supplierName,
            @RequestParam(value = "licenseNo", required = false) String licenseNo,
            @RequestParam(value = "foodLicenseNo", required = false) String foodLicenseNo
    ) {
        return R.ok(supplierService.checkDuplicate(excludeId, supplierCode, supplierName, licenseNo, foodLicenseNo));
    }

    /**
     * 导入前校验供应商文件
     */
    @PostMapping("/import/validate")
    public R<SupplierImportValidationVO> validateImportFile(@RequestParam("file") MultipartFile file) {
        return R.ok(supplierService.validateImportFile(file));
    }

    /**
     * 导入供应商
     */
    @PostMapping("/import")
    public R<SupplierImportResultDTO> importSuppliers(@RequestParam("file") MultipartFile file) {
        return R.ok(supplierService.importSuppliers(file));
    }

    /**
     * 导出供应商
     */
    @GetMapping("/export")
    public void exportSuppliers(SupplierQueryDTO query, HttpServletResponse response) {
        supplierService.exportSuppliers(query, response);
    }

    /**
     * 下载导入错误文件
     */
    @GetMapping("/import/errors/{fileName}")
    public void downloadImportErrorFile(@PathVariable String fileName, HttpServletResponse response) {
        supplierService.downloadImportErrorFile(fileName, response);
    }

    /**
     * 删除供应商资质文件
     */
    @DeleteMapping("/files")
    public R<Void> deleteQualificationFile(
            @RequestParam("fileUrl") String fileUrl,
            @RequestParam(value = "fileName", required = false) String fileName
    ) {
        supplierService.deleteQualificationFile(fileUrl, fileName);
        return R.ok();
    }

    /**
     * 新增供应商
     */
    @PostMapping
    public R<Long> create(@Valid @RequestBody SupplierCreateDTO dto) {
        return R.ok(supplierService.create(dto));
    }

    /**
     * 编辑供应商
     */
    @PutMapping("/{id:\\d+}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody SupplierUpdateDTO dto) {
        supplierService.update(id, dto);
        return R.ok();
    }

    /**
     * 审核供应商
     */
    @PutMapping("/{id:\\d+}/audit")
    public R<Void> audit(@PathVariable Long id, @Valid @RequestBody SupplierAuditDTO dto) {
        supplierService.audit(id, dto);
        return R.ok();
    }

    /**
     * 禁用供应商
     */
    @PutMapping("/{id:\\d+}/disable")
    public R<Void> disable(@PathVariable Long id, @Valid @RequestBody SupplierDisableDTO dto) {
        supplierService.disable(id, dto);
        return R.ok();
    }

    /**
     * 启用供应商
     */
    @PutMapping("/{id:\\d+}/enable")
    public R<Void> enable(@PathVariable Long id) {
        supplierService.enable(id);
        return R.ok();
    }

    /**
     * 注销供应商
     */
    @PutMapping("/{id:\\d+}/cancel")
    public R<Void> cancel(@PathVariable Long id, @Valid @RequestBody SupplierCancelDTO dto) {
        supplierService.cancel(id, dto);
        return R.ok();
    }

    /**
     * 删除供应商
     */
    @DeleteMapping("/{id:\\d+}")
    public R<Void> delete(@PathVariable Long id) {
        supplierService.delete(id);
        return R.ok();
    }
}
