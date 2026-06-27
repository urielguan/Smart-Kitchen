package com.xykj.sys.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.sys.entity.Organization;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 组织Mapper接口
 */
@Mapper
public interface OrganizationMapper extends BaseMapper<Organization> {

    /**
     * 根据组织类型统计数量
     */
    @Select("SELECT COUNT(*) FROM sys_organization WHERE org_type = #{orgType} AND deleted = 0")
    Long countByOrgType(@Param("orgType") String orgType);

    /**
     * 根据状态统计数量
     */
    @Select("SELECT COUNT(*) FROM sys_organization WHERE status = #{status} AND deleted = 0")
    Long countByStatus(@Param("status") String status);

    /**
     * 检查组织编码是否存在（排除指定ID）
     */
    @Select("SELECT COUNT(*) FROM sys_organization WHERE org_code = #{orgCode} AND deleted = 0 AND (#{excludeId} IS NULL OR id != #{excludeId})")
    Long countByOrgCodeExcludeId(@Param("orgCode") String orgCode, @Param("excludeId") Long excludeId);

    /**
     * 统计子组织数量
     */
    @Select("SELECT COUNT(*) FROM sys_organization WHERE parent_id = #{parentId} AND deleted = 0")
    Long countChildrenByParentId(@Param("parentId") Long parentId);
}
