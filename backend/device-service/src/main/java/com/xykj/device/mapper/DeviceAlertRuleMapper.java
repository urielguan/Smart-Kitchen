package com.xykj.device.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.device.entity.DeviceAlertRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DeviceAlertRuleMapper extends BaseMapper<DeviceAlertRule> {

    @Select("SELECT * FROM device_alert_rule " +
            "WHERE rule_type = 'threshold' AND is_enabled = 1 AND deleted = 0")
    List<DeviceAlertRule> selectEnabledThresholdRules();

    @Select("SELECT * FROM device_alert_rule " +
            "WHERE rule_type = 'offline' AND is_enabled = 1 AND deleted = 0")
    List<DeviceAlertRule> selectEnabledOfflineRules();
}
