package com.xykj.sys.service;

import com.xykj.common.result.PageResult;
import com.xykj.sys.dto.SupervisionDashboardQueryDTO;
import com.xykj.sys.dto.SupervisionTraceQueryDTO;
import com.xykj.sys.dto.SupervisionViolationQueryDTO;
import com.xykj.sys.vo.SupervisionDashboardVO;
import com.xykj.sys.vo.SupervisionTraceVO;
import com.xykj.sys.vo.SupervisionViolationDetailVO;
import com.xykj.sys.vo.SupervisionViolationVO;

/**
 * 数据监管服务
 */
public interface SupervisionService {

    /**
     * 获取监管看板首页
     */
    SupervisionDashboardVO getDashboard(SupervisionDashboardQueryDTO query);

    /**
     * 获取违规记录列表
     */
    PageResult<SupervisionViolationVO> listViolations(SupervisionViolationQueryDTO query);

    /**
     * 获取违规记录详情
     */
    SupervisionViolationDetailVO getViolationDetail(Long id);

    /**
     * 获取溯源响应记录列表
     */
    PageResult<SupervisionTraceVO> listTraces(SupervisionTraceQueryDTO query);
}
