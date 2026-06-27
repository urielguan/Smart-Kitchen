package com.xykj.sys.enums;

import cn.hutool.core.util.StrUtil;
import lombok.Getter;

import java.util.Arrays;

/**
 * 字典分类固定大类
 */
@Getter
public enum DictCategoryType {

    RECIPE_CATEGORY("recipe_category", "菜谱类别", ValueStorageMode.RECIPE),
    WAREHOUSE_TYPE("warehouse_type", "仓库类型", ValueStorageMode.CODE),
    MATERIAL_CATEGORY("material_category", "物料类别", ValueStorageMode.NAME),
    SUPPLIER_TYPE("supplier_type", "供应商类型", ValueStorageMode.NAME),
    DEVICE_TYPE("device_type", "设备类型", ValueStorageMode.CODE),
    ORG_TYPE("org_type", "组织类型", ValueStorageMode.CODE),
    EMPLOYEE_POSITION("employee_position", "员工职位", ValueStorageMode.CODE);

    private final String code;
    private final String name;
    private final ValueStorageMode valueStorageMode;

    DictCategoryType(String code, String name, ValueStorageMode valueStorageMode) {
        this.code = code;
        this.name = name;
        this.valueStorageMode = valueStorageMode;
    }

    public boolean useCodeAsValue() {
        return valueStorageMode == ValueStorageMode.CODE;
    }

    public boolean useNameAsValue() {
        return valueStorageMode == ValueStorageMode.NAME;
    }

    public boolean useRecipeTable() {
        return valueStorageMode == ValueStorageMode.RECIPE;
    }

    public static DictCategoryType of(String code) {
        return Arrays.stream(values())
                .filter(item -> StrUtil.equals(item.getCode(), code))
                .findFirst()
                .orElse(null);
    }

    public enum ValueStorageMode {
        CODE,
        NAME,
        RECIPE
    }
}
