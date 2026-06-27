package com.xykj.scm.service;

import com.xykj.scm.vo.SupplierQualificationAlertVO;

import java.util.List;

/**
 * 供应商资质临期提醒服务
 */
public interface SupplierQualificationAlertService {

    /**
     * 查询当前用户可见的资质临期提醒
     */
    List<SupplierQualificationAlertVO> listCurrentAlerts(int limit);

    /**
     * 查询当前用户可见的资质临期提醒数量
     */
    int countCurrentAlerts();

    /**
     * 每日巡检并记录提醒留痕
     */
    void scanAndRecordDailyAlerts();
}
