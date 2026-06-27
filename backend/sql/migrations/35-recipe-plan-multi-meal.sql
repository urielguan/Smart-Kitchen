-- 菜谱计划多餐次扩展
-- 1. recipe_plan_item 增加餐次维度字段
-- 2. 支持日/周/月计划中的多餐次与自定义餐次

ALTER TABLE `recipe_plan_item`
    ADD COLUMN `meal_key` VARCHAR(64) DEFAULT NULL COMMENT '餐次唯一标识' AFTER `category_name`,
    ADD COLUMN `meal_type` VARCHAR(20) DEFAULT NULL COMMENT '餐次类型' AFTER `meal_key`,
    ADD COLUMN `meal_name` VARCHAR(100) DEFAULT NULL COMMENT '餐次名称' AFTER `meal_type`,
    ADD COLUMN `meal_expected_count` INT DEFAULT NULL COMMENT '餐次就餐人数' AFTER `meal_name`,
    ADD COLUMN `meal_sort_order` INT DEFAULT 0 COMMENT '餐次排序' AFTER `meal_expected_count`;
