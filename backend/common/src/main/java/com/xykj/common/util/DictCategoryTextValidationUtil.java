package com.xykj.common.util;

import com.xykj.common.exception.BizException;
import cn.hutool.core.util.StrUtil;

import java.util.regex.Pattern;

/**
 * 字典分类维护文本字段校验工具
 */
public final class DictCategoryTextValidationUtil {

    public static final int DICT_CATEGORY_CODE_MAX_LENGTH = 50;
    public static final int DICT_CATEGORY_NAME_MAX_LENGTH = 50;
    public static final int DICT_CATEGORY_REMARK_MAX_LENGTH = 200;

    private static final Pattern DICT_CATEGORY_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");
    private static final Pattern DICT_CATEGORY_NAME_PATTERN = Pattern.compile("^[\\u4E00-\\u9FFFA-Za-z0-9 /()（）·_-]+$");
    private static final Pattern DICT_CATEGORY_REMARK_PATTERN = Pattern.compile("^[\\u4E00-\\u9FFFA-Za-z0-9 /()（）【】·,，。.;；:：!?！？、_\\r\\n-]*$");

    private DictCategoryTextValidationUtil() {
    }

    public static void validateDictCategoryCode(String value) {
        if (value == null) {
            return;
        }
        if (value.length() > DICT_CATEGORY_CODE_MAX_LENGTH) {
            throw BizException.validationFailed("分类项编码长度不能超过50个字符");
        }
        if (!DICT_CATEGORY_CODE_PATTERN.matcher(value).matches()) {
            throw BizException.validationFailed("分类项编码只能包含字母、数字、下划线和短横线");
        }
    }

    public static void validateDictCategoryName(String value) {
        if (StrUtil.isBlank(value)) {
            return;
        }
        if (value.length() > DICT_CATEGORY_NAME_MAX_LENGTH) {
            throw BizException.validationFailed("分类项名称长度不能超过50个字符");
        }
        if (!DICT_CATEGORY_NAME_PATTERN.matcher(value).matches()) {
            throw BizException.validationFailed("分类项名称不允许输入特殊字符");
        }
    }

    public static void validateDictCategoryRemark(String value) {
        if (value == null) {
            return;
        }
        if (value.length() > DICT_CATEGORY_REMARK_MAX_LENGTH) {
            throw BizException.validationFailed("备注长度不能超过200个字符");
        }
        if (!DICT_CATEGORY_REMARK_PATTERN.matcher(value).matches()) {
            throw BizException.validationFailed("备注不允许输入特殊字符");
        }
    }
}
