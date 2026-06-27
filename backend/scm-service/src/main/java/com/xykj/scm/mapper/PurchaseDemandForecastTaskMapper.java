package com.xykj.scm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.scm.entity.PurchaseDemandForecastTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 采购需求预测任务主表 Mapper
 */
@Mapper
public interface PurchaseDemandForecastTaskMapper extends BaseMapper<PurchaseDemandForecastTask> {
}
