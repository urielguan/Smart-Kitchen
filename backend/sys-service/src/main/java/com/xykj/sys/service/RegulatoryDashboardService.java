package com.xykj.sys.service;

import com.xykj.sys.dto.RegulatoryDashboardQueryDTO;
import com.xykj.sys.vo.RegulatoryDashboardDataVO;

/**
 * 监管看板首页服务
 */
public interface RegulatoryDashboardService {

    /**
     * 获取监管看板首页
     */
    RegulatoryDashboardDataVO getHomeSnapshot(RegulatoryDashboardQueryDTO query);
}
