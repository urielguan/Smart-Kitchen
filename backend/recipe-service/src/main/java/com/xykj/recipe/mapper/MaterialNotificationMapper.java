package com.xykj.recipe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.recipe.entity.MaterialNotification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 物料预警通知Mapper
 */
@Mapper
public interface MaterialNotificationMapper extends BaseMapper<MaterialNotification> {

    /**
     * 查询未读通知数量
     */
    @Select("SELECT COUNT(*) FROM recipe_material_notification WHERE status = 'unread' AND (org_id = #{orgId} OR #{orgId} IS NULL)")
    int countUnread(@Param("orgId") Long orgId);

    /**
     * 查询高优先级未读通知
     */
    @Select("SELECT * FROM recipe_material_notification WHERE status = 'unread' AND priority = 'high' AND (org_id = #{orgId} OR #{orgId} IS NULL) ORDER BY created_at DESC LIMIT #{limit}")
    List<MaterialNotification> getHighPriorityUnread(@Param("orgId") Long orgId, @Param("limit") int limit);

    /**
     * 批量更新状态
     */
    @Select("<script>" +
            "UPDATE recipe_material_notification SET status = 'read', updated_at = NOW() " +
            "WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    int batchUpdateStatus(@Param("ids") List<Long> ids, @Param("status") String status);
}
