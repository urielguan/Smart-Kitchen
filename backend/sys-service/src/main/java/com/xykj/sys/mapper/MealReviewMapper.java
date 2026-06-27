package com.xykj.sys.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.sys.entity.MealReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用餐评价Mapper接口
 */
@Mapper
public interface MealReviewMapper extends BaseMapper<MealReview> {

    /**
     * 生成评价编号
     * 格式：RV-yyyyMMdd{3位序号}
     */
    @Select("SELECT CONCAT('RV-', DATE_FORMAT(NOW(), '%Y%m%d'), LPAD(IFNULL(MAX(CAST(SUBSTRING(review_no, 12) AS UNSIGNED)), 0) + 1, 3, '0')) " +
            "FROM sys_meal_review " +
            "WHERE review_no LIKE CONCAT('RV-', DATE_FORMAT(NOW(), '%Y%m%d'), '%') AND deleted = 0")
    String generateReviewNo();

    /**
     * 根据员工ID统计评价数
     */
    @Select("SELECT COUNT(*) FROM sys_meal_review WHERE employee_id = #{employeeId} AND deleted = 0")
    Long countByEmployeeId(@Param("employeeId") Long employeeId);
}
