package com.xykj.health.service;

import com.xykj.common.result.PageResult;
import com.xykj.health.dto.HealthCertificateCreateDTO;
import com.xykj.health.dto.HealthCertificateQueryDTO;
import com.xykj.health.vo.HealthCertificateDashboardVO;
import com.xykj.health.vo.HealthCertificateVO;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

/**
 * 健康证管理服务接口
 */
public interface HealthCertificateService {

    /** 创建/更新健康证 */
    HealthCertificateVO save(HealthCertificateCreateDTO dto);

    /** 根据员工ID获取健康证 */
    HealthCertificateVO getByEmployeeId(Long employeeId);

    /** 根据ID获取健康证详情 */
    HealthCertificateVO getById(Long id);

    /** 获取健康证列表 */
    List<HealthCertificateVO> list(HealthCertificateQueryDTO query);

    /** 分页获取健康证列表 */
    PageResult<HealthCertificateVO> listPage(HealthCertificateQueryDTO query);

    /** 获取即将过期的健康证列表 */
    List<HealthCertificateVO> listExpiring(HealthCertificateQueryDTO query);

    /** 获取健康证看板数据 */
    HealthCertificateDashboardVO getDashboard(HealthCertificateQueryDTO query);

    /** 删除健康证 */
    boolean delete(Long id);

    /** 手动触发状态更新 */
    int refreshStatus();

    /** 导出健康证 */
    void exportCertificates(HealthCertificateQueryDTO query, HttpServletResponse response);
}
