package com.xykj.sys.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 物料类别面积系数历史回溯重算请求
 */
@Data
public class DictCategoryAreaCoefficientRecalcDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "请选择需要重算的面积系数修正版本")
    private Long correctionId;
}
