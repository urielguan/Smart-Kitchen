package com.xykj.sys.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 消息通知详情 VO
 */
@Data
public class NotificationDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String messageId;

    private String category;

    private String categoryName;

    private String subCategory;

    private String subCategoryName;

    private String title;

    private String summary;

    private String body;

    private String riskLevel;

    private String riskLevelName;

    private String readStatus;

    private String readStatusName;

    private String processStatus;

    private String processStatusName;

    private String sourceModule;

    private Long relatedBusinessId;

    private String relatedBusinessType;

    private Long relatedOrgId;

    private Long relatedWarehouseId;

    private Long relatedMaterialId;

    private LocalDateTime sendTime;

    private LocalDateTime expiryTime;

    private String pushChannels;

    private Object sourceSnapshot;

    private Object executableActions;

    private Boolean allowDelete;
}
