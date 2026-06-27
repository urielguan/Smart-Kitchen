package com.xykj.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.wms.dto.OutboundOrderQueryDTO;
import com.xykj.wms.entity.OutboundOrder;
import com.xykj.wms.vo.OutboundOrderStatisticsVO;
import com.xykj.wms.vo.OutboundOrderVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface OutboundOrderMapper extends BaseMapper<OutboundOrder> {

    Page<OutboundOrderVO> selectOutboundPage(
        Page<OutboundOrderVO> page,
        @Param("q") OutboundOrderQueryDTO query
    );

    List<OutboundOrderVO> selectOutboundExportRows(@Param("q") OutboundOrderQueryDTO query);

    OutboundOrderVO selectOutboundDetail(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM wms_outbound_order WHERE deleted = 0")
    Long countTotal();

    @Select("SELECT COUNT(*) FROM wms_outbound_order WHERE status = #{status} AND deleted = 0")
    Long countByStatus(@Param("status") String status);

    @Select("""
        SELECT COALESCE(
            SUM(oi.quantity * COALESCE(mc.current_cost, CAST(0 AS DECIMAL(18,4)))),
            CAST(0 AS DECIMAL(18,4))
        )
        FROM wms_outbound_order o
        JOIN wms_outbound_order_item oi ON oi.outbound_id = o.id
        LEFT JOIN (
            SELECT
                material_id,
                CASE
                    WHEN SUM(quantity) <= 0 THEN CAST(0 AS DECIMAL(18,4))
                    ELSE SUM(COALESCE(total_cost, unit_cost * quantity, CAST(0 AS DECIMAL(18,4)))) / SUM(quantity)
                END AS current_cost
            FROM wms_inventory
            WHERE quantity > 0
            GROUP BY material_id
        ) mc ON mc.material_id = oi.material_id
        WHERE o.deleted = 0
          AND o.status = 'completed'
          AND o.completed_at IS NOT NULL
          AND DATE_FORMAT(o.completed_at, '%Y-%m') = DATE_FORMAT(NOW(), '%Y-%m')
        """)
    BigDecimal sumThisMonthAmount();

    @Select("SELECT MAX(outbound_no) FROM wms_outbound_order WHERE outbound_no LIKE CONCAT(#{prefix}, '%') AND deleted = 0")
    String getMaxOutboundNo(@Param("prefix") String prefix);

    boolean existsWarehouseOutboundOccupancy(@Param("warehouseId") Long warehouseId, @Param("statuses") List<String> statuses);

    boolean existsWarehouseOutboundHistory(@Param("warehouseId") Long warehouseId);

}
