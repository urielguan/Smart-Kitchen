package com.xykj.scm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.scm.entity.PurchaseOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 采购订单 Mapper
 */
@Mapper
public interface PurchaseOrderMapper extends BaseMapper<PurchaseOrder> {
}
