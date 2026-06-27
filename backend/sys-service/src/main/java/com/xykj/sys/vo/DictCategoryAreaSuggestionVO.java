package com.xykj.sys.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物料类别面积系数 AI 建议结果
 */
@Data
public class DictCategoryAreaSuggestionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private BigDecimal areaCoefficient;

    private String reason;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime generatedAt;
}
