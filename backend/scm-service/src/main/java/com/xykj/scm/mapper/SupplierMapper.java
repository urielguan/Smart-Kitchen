package com.xykj.scm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.scm.entity.Supplier;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 供应商 Mapper
 */
@Mapper
public interface SupplierMapper extends BaseMapper<Supplier> {

    /**
     * 供应商总数
     */
    @Select("SELECT COUNT(*) FROM scm_supplier WHERE deleted = 0")
    Long countTotal();

    /**
     * 按组织范围统计供应商总数
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM scm_supplier WHERE deleted = 0 " +
            "<if test='orgIds != null and orgIds.size() > 0'> AND org_id IN " +
            "<foreach collection='orgIds' item='orgId' open='(' separator=',' close=')'>#{orgId}</foreach></if>" +
            "</script>")
    Long countTotalByOrgIds(@Param("orgIds") List<Long> orgIds);

    /**
     * 按状态统计
     */
    @Select("SELECT COUNT(*) FROM scm_supplier WHERE deleted = 0 AND status = #{status}")
    Long countByStatus(@Param("status") String status);

    /**
     * 按组织范围和状态统计
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM scm_supplier WHERE deleted = 0 AND status = #{status} " +
            "<if test='orgIds != null and orgIds.size() > 0'> AND org_id IN " +
            "<foreach collection='orgIds' item='orgId' open='(' separator=',' close=')'>#{orgId}</foreach></if>" +
            "</script>")
    Long countByStatusAndOrgIds(@Param("status") String status, @Param("orgIds") List<Long> orgIds);

    /**
     * 统计同租户下供应商编码数量（排除指定ID，仅统计未删除数据）
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM scm_supplier " +
            "WHERE deleted = 0 " +
            "AND tenant_id = #{tenantId} " +
            "AND supplier_code = #{supplierCode} " +
            "<if test='excludeId != null'>AND id &lt;&gt; #{excludeId}</if>" +
            "</script>")
    Long countByTenantAndSupplierCode(
            @Param("tenantId") Long tenantId,
            @Param("supplierCode") String supplierCode,
            @Param("excludeId") Long excludeId
    );

    /**
     * 统计同租户下供应商名称数量（排除指定ID，仅统计未删除数据）
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM scm_supplier " +
            "WHERE deleted = 0 " +
            "AND tenant_id = #{tenantId} " +
            "AND supplier_name = #{supplierName} " +
            "<if test='excludeId != null'>AND id &lt;&gt; #{excludeId}</if>" +
            "</script>")
    Long countByTenantAndSupplierName(
            @Param("tenantId") Long tenantId,
            @Param("supplierName") String supplierName,
            @Param("excludeId") Long excludeId
    );

    /**
     * 资质30天内到期统计（营业执照或食品许可证）
     */
    @Select("SELECT COUNT(*) FROM scm_supplier WHERE deleted = 0 AND (" +
            "(license_expires_at IS NOT NULL AND DATEDIFF(license_expires_at, NOW()) BETWEEN 0 AND 30) " +
            "OR (food_license_expires_at IS NOT NULL AND DATEDIFF(food_license_expires_at, NOW()) BETWEEN 0 AND 30)" +
            ")")
    Long countNearExpire();

    /**
     * 按组织范围统计资质近30天到期数
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM scm_supplier WHERE deleted = 0 AND (" +
            "(license_expires_at IS NOT NULL AND DATEDIFF(license_expires_at, NOW()) BETWEEN 0 AND 30) " +
            "OR (food_license_expires_at IS NOT NULL AND DATEDIFF(food_license_expires_at, NOW()) BETWEEN 0 AND 30)" +
            ") " +
            "<if test='orgIds != null and orgIds.size() > 0'> AND org_id IN " +
            "<foreach collection='orgIds' item='orgId' open='(' separator=',' close=')'>#{orgId}</foreach></if>" +
            "</script>")
    Long countNearExpireByOrgIds(@Param("orgIds") List<Long> orgIds);

    /**
     * 统计同租户下标准化后的统一社会信用代码数量
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM scm_supplier " +
            "WHERE deleted = 0 " +
            "AND tenant_id = #{tenantId} " +
            "AND UPPER(TRIM(unified_credit_code)) = #{normalizedUnifiedCreditCode} " +
            "<if test='excludeId != null'>AND id &lt;&gt; #{excludeId}</if>" +
            "</script>")
    Long countByTenantAndNormalizedUnifiedCreditCode(
            @Param("tenantId") Long tenantId,
            @Param("normalizedUnifiedCreditCode") String normalizedUnifiedCreditCode,
            @Param("excludeId") Long excludeId
    );

    /**
     * 统计同租户下标准化后的营业执照编号数量（排除指定ID，仅统计未删除数据）
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM scm_supplier " +
            "WHERE deleted = 0 " +
            "AND tenant_id = #{tenantId} " +
            "AND TRIM(license_no) = #{normalizedLicenseNo} " +
            "<if test='excludeId != null'>AND id &lt;&gt; #{excludeId}</if>" +
            "</script>")
    Long countByTenantAndNormalizedLicenseNo(
            @Param("tenantId") Long tenantId,
            @Param("normalizedLicenseNo") String normalizedLicenseNo,
            @Param("excludeId") Long excludeId
    );

    /**
     * 统计同租户下标准化后的食品许可证号数量（排除指定ID，仅统计未删除数据）
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM scm_supplier " +
            "WHERE deleted = 0 " +
            "AND tenant_id = #{tenantId} " +
            "AND TRIM(food_license_no) = #{normalizedFoodLicenseNo} " +
            "<if test='excludeId != null'>AND id &lt;&gt; #{excludeId}</if>" +
            "</script>")
    Long countByTenantAndNormalizedFoodLicenseNo(
            @Param("tenantId") Long tenantId,
            @Param("normalizedFoodLicenseNo") String normalizedFoodLicenseNo,
            @Param("excludeId") Long excludeId
    );

    /**
     * 查询同租户下标准化后的统一社会信用代码命中的供应商
     */
    @Select("<script>" +
            "SELECT * FROM scm_supplier " +
            "WHERE deleted = 0 " +
            "AND tenant_id = #{tenantId} " +
            "AND UPPER(TRIM(unified_credit_code)) IN " +
            "<foreach collection='normalizedUnifiedCreditCodes' item='code' open='(' separator=',' close=')'>#{code}</foreach>" +
            "</script>")
    List<Supplier> selectByTenantAndNormalizedUnifiedCreditCodes(
            @Param("tenantId") Long tenantId,
            @Param("normalizedUnifiedCreditCodes") List<String> normalizedUnifiedCreditCodes
    );
}
