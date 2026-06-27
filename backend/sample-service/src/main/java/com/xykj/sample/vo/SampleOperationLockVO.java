package com.xykj.sample.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 留样操作锁 VO
 */
@Data
public class SampleOperationLockVO {

    /** 是否存在有效锁 */
    private Boolean locked;

    /** 锁令牌，仅抢占/续租接口返回 */
    private String lockToken;

    /** 操作类型 */
    private String operationType;

    /** 操作类型名称 */
    private String operationTypeLabel;

    /** 操作人ID */
    private Long operatorId;

    /** 操作人姓名 */
    private String operatorName;

    /** 是否当前用户占有 */
    private Boolean ownedByCurrentUser;

    /** 抢占时间 */
    private LocalDateTime acquiredAt;

    /** 过期时间 */
    private LocalDateTime expiresAt;
}
