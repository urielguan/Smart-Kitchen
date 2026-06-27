package com.xykj.scm.service;

import com.xykj.common.result.PageResult;
import com.xykj.scm.dto.SupplierAuditDTO;
import com.xykj.scm.dto.SupplierCancelDTO;
import com.xykj.scm.dto.SupplierCreateDTO;
import com.xykj.scm.dto.SupplierDisableDTO;
import com.xykj.scm.dto.SupplierImportResultDTO;
import com.xykj.scm.dto.SupplierQueryDTO;
import com.xykj.scm.dto.SupplierUpdateDTO;
import com.xykj.scm.vo.SupplierDuplicateCheckVO;
import com.xykj.scm.vo.SupplierImportValidationVO;
import com.xykj.scm.vo.SupplierQualificationFileVO;
import com.xykj.scm.vo.SupplierStatisticsVO;
import com.xykj.scm.vo.SupplierVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 供应商服务接口
 */
public interface SupplierService {

    /**
     * 供应商分页列表
     */
    PageResult<SupplierVO> list(SupplierQueryDTO query);

    /**
     * 供应商详情
     */
    SupplierVO getDetail(Long id);

    /**
     * 上传供应商资质文件
     */
    SupplierQualificationFileVO uploadQualificationFile(MultipartFile file);

    /**
     * 删除供应商资质文件
     */
    void deleteQualificationFile(String fileUrl, String fileName);

    /**
     * 下载供应商资质文件
     */
    void downloadQualificationFile(String fileUrl, String fileName, HttpServletResponse response);

    /**
     * 下载供应商导入模板
     */
    void downloadImportTemplate(HttpServletResponse response);

    /**
     * 导入前校验供应商文件
     */
    SupplierImportValidationVO validateImportFile(MultipartFile file);

    /**
     * 校验供应商编码/名称/证件号是否重复
     */
    SupplierDuplicateCheckVO checkDuplicate(
            Long excludeId,
            String supplierCode,
            String supplierName,
            String licenseNo,
            String foodLicenseNo
    );

    /**
     * 导入供应商
     */
    SupplierImportResultDTO importSuppliers(MultipartFile file);

    /**
     * 导出供应商
     */
    void exportSuppliers(SupplierQueryDTO query, HttpServletResponse response);

    /**
     * 下载导入错误文件
     */
    void downloadImportErrorFile(String fileName, HttpServletResponse response);

    /**
     * 新增供应商
     */
    Long create(SupplierCreateDTO dto);

    /**
     * 编辑供应商
     */
    void update(Long id, SupplierUpdateDTO dto);

    /**
     * 审核供应商
     */
    void audit(Long id, SupplierAuditDTO dto);

    /**
     * 禁用供应商
     */
    void disable(Long id, SupplierDisableDTO dto);

    /**
     * 启用供应商
     */
    void enable(Long id);

    /**
     * 注销供应商
     */
    void cancel(Long id, SupplierCancelDTO dto);

    /**
     * 删除供应商
     */
    void delete(Long id);

    /**
     * 供应商统计
     */
    SupplierStatisticsVO getStatistics(SupplierQueryDTO query);
}
