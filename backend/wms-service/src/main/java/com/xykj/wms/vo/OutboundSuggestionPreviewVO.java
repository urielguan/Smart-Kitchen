package com.xykj.wms.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class OutboundSuggestionPreviewVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean success;
    private LocalDateTime generateTime;
    private String ruleVersion;
    private SummaryVO summary;
    private List<DetailVO> details = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();

    @Data
    public static class SummaryVO implements Serializable {
        private static final long serialVersionUID = 1L;

        private Integer detailCount;
        private Integer fullMatchedCount;
        private Integer partialMatchedCount;
        private Integer failedCount;
    }

    @Data
    public static class DetailVO implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long detailId;
        private Integer lineNo;
        private Long materialId;
        private String materialName;
        private String specName;
        private BigDecimal requestQty;
        private BigDecimal matchedQty;
        private BigDecimal unmatchedQty;
        private String suggestStatus;
        private String message;
        private List<SuggestionVO> suggestions = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
    }

    @Data
    public static class SuggestionVO implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long sourceStockDetailId;
        private Long warehouseId;
        private String warehouseName;
        private Long locationId;
        private String locationName;
        private String batchNo;
        private LocalDate productionDate;
        private LocalDate expiryDate;
        private Long remainingShelfLifeDays;
        private BigDecimal availableQty;
        private BigDecimal suggestQty;
        private String reason;
    }
}
