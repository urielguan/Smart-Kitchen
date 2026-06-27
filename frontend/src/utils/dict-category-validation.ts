export const DICT_CATEGORY_CODE_MAX_LENGTH = 50
export const DICT_CATEGORY_NAME_MAX_LENGTH = 50
export const DICT_CATEGORY_REMARK_MAX_LENGTH = 200

export const DICT_CATEGORY_CODE_PATTERN = /^[A-Za-z0-9_-]+$/
export const DICT_CATEGORY_NAME_PATTERN = /^[\u4E00-\u9FFFA-Za-z0-9 /()（）·_-]+$/
export const DICT_CATEGORY_REMARK_PATTERN = /^[\u4E00-\u9FFFA-Za-z0-9 /()（）【】·,，。.;；:：!?！？、_\r\n-]*$/

export const DICT_CATEGORY_CODE_PATTERN_MESSAGE = '分类项编码只能包含字母、数字、下划线和短横线'
export const DICT_CATEGORY_NAME_PATTERN_MESSAGE = '分类项名称不允许输入特殊字符'
export const DICT_CATEGORY_REMARK_PATTERN_MESSAGE = '备注不允许输入特殊字符'

export const isValidDictCategoryCode = (value?: string | null) => {
  if (!value) {
    return true
  }
  return DICT_CATEGORY_CODE_PATTERN.test(value)
}

export const isValidDictCategoryName = (value?: string | null) => {
  if (!value) {
    return true
  }
  return DICT_CATEGORY_NAME_PATTERN.test(value)
}

export const isValidDictCategoryRemark = (value?: string | null) => {
  if (!value) {
    return true
  }
  return DICT_CATEGORY_REMARK_PATTERN.test(value)
}
