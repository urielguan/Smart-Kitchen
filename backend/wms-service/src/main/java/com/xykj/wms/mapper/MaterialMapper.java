package com.xykj.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.wms.entity.Material;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

/**
 * 物料 Mapper 接口
 */
@Mapper
public interface MaterialMapper extends BaseMapper<Material> {

    /**
     * 统计物料总数
     */
    @Select("SELECT COUNT(*) FROM wms_material WHERE deleted = 0")
    Long countTotal();

    /**
     * 统计库存预警物料数（当前库存 <= 最低库存）
     */
    @Select("SELECT COUNT(DISTINCT m.id) FROM wms_material m " +
            "LEFT JOIN wms_inventory i ON m.id = i.material_id " +
            "WHERE m.deleted = 0 AND m.status = 'active' " +
            "AND i.quantity <= m.min_stock")
    Long countLowStock();

    /**
     * 统计临期物料数
     */
    @Select("SELECT COUNT(*) FROM wms_inventory " +
            "WHERE expiry_date IS NOT NULL " +
            "AND DATEDIFF(expiry_date, NOW()) BETWEEN 0 AND 7 " +
            "AND status != 'expired' " +
            "AND quantity > 0")
    Long countNearExpiry();

    /**
     * 统计已过期物料数（保质期剩余天数 <= 0）
     */
    @Select("SELECT COUNT(*) FROM wms_inventory " +
            "WHERE expiry_date IS NOT NULL " +
            "AND expiry_date <= CURDATE() " +
            "AND quantity > 0")
    Long countExpired();

    /**
     * 获取物料当前库存
     */
    @Select("SELECT COALESCE(SUM(quantity), 0) FROM wms_inventory WHERE material_id = #{materialId}")
    BigDecimal getCurrentStock(@Param("materialId") Long materialId);

    /**
     * 检查物料是否有过期库存（保质期剩余天数 <= 0）
     */
    @Select("SELECT COUNT(*) FROM wms_inventory WHERE material_id = #{materialId} AND expiry_date IS NOT NULL AND expiry_date <= CURDATE() AND quantity > 0")
    int countExpiredStock(@Param("materialId") Long materialId);

    /**
     * 批量查询物料总库存
     */
    @Select("<script>" +
            "SELECT material_id, COALESCE(SUM(quantity), 0) AS total_quantity " +
            "FROM wms_inventory " +
            "WHERE material_id IN " +
            "<foreach collection='materialIds' item='materialId' open='(' separator=',' close=')'>" +
            "#{materialId}" +
            "</foreach> " +
            "GROUP BY material_id" +
            "</script>")
    List<MaterialStockTotal> selectStockTotals(@Param("materialIds") List<Long> materialIds);

    @Select("<script>" +
            "SELECT m.id AS materialId, m.material_category AS materialCategory, d.area_coefficient AS areaCoefficient " +
            "FROM wms_material m " +
            "LEFT JOIN sys_dict d ON d.dict_type = 'material_category' " +
            "AND d.dict_name = m.material_category " +
            "AND d.status = 'active' " +
            "AND d.deleted = 0 " +
            "AND d.tenant_id = m.tenant_id " +
            "WHERE m.deleted = 0 AND m.id IN " +
            "<foreach collection='materialIds' item='materialId' open='(' separator=',' close=')'>" +
            "#{materialId}" +
            "</foreach>" +
            "</script>")
    List<AreaValidationMaterial> selectAreaValidationMaterials(@Param("materialIds") List<Long> materialIds);

    interface MaterialStockTotal {
        Long getMaterialId();
        BigDecimal getTotalQuantity();
    }

    @Data
    class AreaValidationMaterial {
        private Long materialId;
        private String materialCategory;
        private BigDecimal areaCoefficient;
    }
}
