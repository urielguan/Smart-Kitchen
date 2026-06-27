package com.xykj.wms.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class InboundAreaValidationPreviewVO implements Serializable {

    private List<ItemResult> itemResults = new ArrayList<>();
    private List<LocationSummary> locationSummaries = new ArrayList<>();
    private boolean hasExceeded;
    private boolean hasSkipped;
    private String globalMessage;

    public boolean getHasExceeded() {
        return hasExceeded;
    }

    public boolean getHasSkipped() {
        return hasSkipped;
    }

    @Data
    public static class ItemResult implements Serializable {
        private String lineKey;
        private Long warehouseId;
        private Long locationId;
        private BigDecimal areaCoefficient;
        private BigDecimal expectedOccupiedArea;
        private BigDecimal currentOccupiedArea;
        private BigDecimal projectedOccupiedArea;
        private BigDecimal locationCapacity;
        private String validationResult;
        private String message;
    }

    @Data
    public static class LocationSummary implements Serializable {
        private Long warehouseId;
        private Long locationId;
        private String locationName;
        private BigDecimal currentOccupiedArea;
        private BigDecimal expectedIncrementArea;
        private BigDecimal projectedOccupiedArea;
        private BigDecimal locationCapacity;
        private String validationResult;
        private boolean hasSkippedItems;
        private String message;
    }
}
