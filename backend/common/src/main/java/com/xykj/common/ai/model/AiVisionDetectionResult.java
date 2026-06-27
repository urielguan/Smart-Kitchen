package com.xykj.common.ai.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiVisionDetectionResult {

    private boolean success;
    private String summary;
    private String modelVersion;
    private List<ViolationItem> violations = new ArrayList<>();
    private String rawResponse;
    private String errorMessage;

    @Data
    public static class ViolationItem {
        private String violationType;
        private String violationTypeName;
        private Double confidence;
        private Integer personCount;
        private String explanation;
    }
}
