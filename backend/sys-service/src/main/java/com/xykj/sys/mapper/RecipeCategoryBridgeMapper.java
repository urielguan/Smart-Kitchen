package com.xykj.sys.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.sys.dto.DictCategoryQueryDTO;
import com.xykj.sys.entity.RecipeCategoryBridge;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;

/**
 * 菜谱类别桥接 Mapper
 */
@Mapper
public interface RecipeCategoryBridgeMapper extends BaseMapper<RecipeCategoryBridge> {

    @Delete("DELETE FROM recipe_category WHERE id = #{id} AND deleted = 0")
    int physicalDeleteById(Long id);

    @Select({
            "<script>",
            "SELECT rc.*, COUNT(r.id) AS referenceCount",
            "FROM recipe_category rc",
            "LEFT JOIN recipe r ON r.category_id = rc.id",
            "  AND r.deleted = 0",
            "  AND (r.tenant_id = #{tenantId} OR r.tenant_id IS NULL)",
            "WHERE rc.deleted = 0",
            "<choose>",
            "  <when test='query.sourceType == \"system\"'>",
            "    AND rc.category_code IN",
            "    <foreach collection='systemCodes' item='code' open='(' separator=',' close=')'>",
            "      #{code}",
            "    </foreach>",
            "  </when>",
            "  <when test='query.sourceType == \"custom\"'>",
            "    AND rc.tenant_id = #{tenantId}",
            "    AND rc.category_code NOT IN",
            "    <foreach collection='systemCodes' item='code' open='(' separator=',' close=')'>",
            "      #{code}",
            "    </foreach>",
            "  </when>",
            "  <otherwise>",
            "    AND (rc.category_code IN",
            "      <foreach collection='systemCodes' item='code' open='(' separator=',' close=')'>",
            "        #{code}",
            "      </foreach>",
            "      OR rc.tenant_id = #{tenantId})",
            "  </otherwise>",
            "</choose>",
            "<if test='query.keyword != null and query.keyword != \"\"'>",
            "  AND (rc.category_name LIKE CONCAT('%', #{query.keyword}, '%')",
            "    OR rc.category_code LIKE CONCAT('%', #{query.keyword}, '%'))",
            "</if>",
            "<if test='query.status != null and query.status != \"\"'>",
            "  AND rc.status = #{query.status}",
            "</if>",
            "GROUP BY rc.id",
            "ORDER BY rc.sort_order ASC, rc.updated_at DESC",
            "</script>"
    })
    IPage<RecipeCategoryBridge> selectPageWithReferenceCount(
            IPage<RecipeCategoryBridge> page,
            @Param("tenantId") Long tenantId,
            @Param("systemCodes") Collection<String> systemCodes,
            @Param("query") DictCategoryQueryDTO queryDTO
    );
}
