package com.xykj.wms.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MaterialFoodMappingVO {
    private Long id;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private Long foodItemId;
    private String foodCode;
    private String foodName;
    private String matchStatus;
    private String remark;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime confirmedAt;
}
