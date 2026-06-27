package com.xykj.wms.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class OutboundSuggestionRevalidateVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean valid;
    private String result;
    private List<DetailVO> details = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();

    @Data
    public static class DetailVO implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long detailId;
        private Integer lineNo;
        private boolean valid;
        private String result;
        private String message;
    }
}
