package com.xykj.device.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.device.dto.AlertRuleCreateDTO;
import com.xykj.device.dto.AlertRuleQueryDTO;
import com.xykj.device.dto.AlertRuleUpdateDTO;
import com.xykj.device.vo.AlertRuleVO;

/**
 * 告警规则配置服务接口
 */
public interface DeviceAlertRuleService {

    /**
     * 分页查询告警规则
     */
    Page<AlertRuleVO> list(AlertRuleQueryDTO query);

    /**
     * 获取规则详情
     */
    AlertRuleVO getDetail(Long id);

    /**
     * 创建告警规则
     */
    Long create(AlertRuleCreateDTO dto);

    /**
     * 更新告警规则
     */
    void update(Long id, AlertRuleUpdateDTO dto);

    /**
     * 删除告警规则
     */
    void delete(Long id);

    /**
     * 切换启用/禁用状态
     */
    void toggleEnabled(Long id);
}
