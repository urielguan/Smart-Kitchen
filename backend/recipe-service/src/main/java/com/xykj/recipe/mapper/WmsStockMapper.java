package com.xykj.recipe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.recipe.entity.WmsStock;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

/**
 * 物料库存Mapper（跨服务查询WMS库存表）
 */
@Mapper
public interface WmsStockMapper extends BaseMapper<WmsStock> {

    /**
     * 根据物料ID查询可用库存总量（排除盘点锁定）
     * 使用 wms_inventory 表， quantity 字段
     */
    @Select("SELECT IFNULL(SUM(quantity), 0) FROM wms_inventory WHERE material_id = #{materialId} AND status != 'locked'")
    BigDecimal getAvailableStockByMaterialId(@Param("materialId") Long materialId);

    /**
     * 根据物料ID列表批量查询可用库存（排除盘点锁定）
     * 使用 wms_inventory 表， quantity 字段
     */
    @Select("<script>" +
            "SELECT material_id, IFNULL(SUM(quantity), 0) as available_stock " +
            "FROM wms_inventory " +
            "WHERE material_id IN " +
            "<foreach collection='materialIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            " AND status != 'locked' " +
            "GROUP BY material_id" +
            "</script>")
    List<StockSummary> getAvailableStockByMaterialIds(@Param("materialIds") List<Long> materialIds);


    /**
     * 根据物料ID列表批量查询库存详情（包含到期日期）
     * 使用 MIN 获取最近到期日期
     * 排除盘点锁定状态(status='locked')的库存
     */
    @Select("<script>" +
            "SELECT material_id, IFNULL(SUM(quantity), 0) as available_stock, " +
            "MIN(expiry_date) as nearest_expiry_date, " +
            "SUM(CASE WHEN expiry_date IS NOT NULL AND expiry_date &lt;= DATE_ADD(CURDATE(), INTERVAL 7 DAY) THEN quantity ELSE 0 END) as expiring_stock " +
            "FROM wms_inventory " +
            "WHERE material_id IN " +
            "<foreach collection='materialIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            " AND status != 'locked' " +
            "GROUP BY material_id" +
            "</script>")
    List<StockDetail> getStockDetailsByMaterialIds(@Param("materialIds") List<Long> materialIds);

    /**
     * 库存汇总DTO
     */
    @Data
    class StockSummary {
        private Long materialId;
        private BigDecimal availableStock;
    }

    /**
     * 库存详情DTO
     */
    @Data
    class StockDetail {
        private Long materialId;
        private BigDecimal availableStock;
        private java.time.LocalDate nearestExpiryDate;
        private BigDecimal expiringStock;
    }
}
