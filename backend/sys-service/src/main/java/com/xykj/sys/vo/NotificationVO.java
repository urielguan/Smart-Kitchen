package com.xykj.sys.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 消息通知列表 VO
 */
@Data
public class NotificationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String messageId;

    private String category;

    private String categoryName;

    private String subCategory;

    private String subCategoryName;

    private String title;

    private String summary;

    private String riskLevel;

    private String riskLevelName;

    private String readStatus;

    private String readStatusName;

    private String processStatus;

    private String processStatusName;

    private String sourceModule;

    private Long relatedBusinessId;

    private String relatedBusinessType;

    private LocalDateTime sendTime;

    /** 相对时间显示 */
    private String timeDisplay;

    private String executableActions;
}
