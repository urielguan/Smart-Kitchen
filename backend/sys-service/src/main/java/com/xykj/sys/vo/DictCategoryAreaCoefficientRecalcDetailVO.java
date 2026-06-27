package com.xykj.sys.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 物料类别面积系数历史回溯重算明细 VO
 */
@Data
public class DictCategoryAreaCoefficientRecalcDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String detailCode;

    private String detailName;

    private String detailType;

    private String status;

    private Long affectedRecordCount;

    private BigDecimal quantityTotal;

    private BigDecimal oldAreaTotal;

    private BigDecimal newAreaTotal;

    private BigDecimal deltaAreaTotal;

    private String detailMessage;

    private String snapshotPayload;
}
