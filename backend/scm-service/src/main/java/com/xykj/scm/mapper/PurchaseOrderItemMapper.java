package com.xykj.scm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.scm.entity.PurchaseOrderItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 采购订单明细 Mapper
 */
@Mapper
public interface PurchaseOrderItemMapper extends BaseMapper<PurchaseOrderItem> {

    @Delete("DELETE FROM scm_purchase_order_item WHERE order_id = #{orderId}")
    void deleteByOrderId(@Param("orderId") Long orderId);
}
