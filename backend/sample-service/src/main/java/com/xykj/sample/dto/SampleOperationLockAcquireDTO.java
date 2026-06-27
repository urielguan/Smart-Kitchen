package com.xykj.sample.dto;

import lombok.Data;

/**
 * 留样操作锁抢占请求
 */
@Data
public class SampleOperationLockAcquireDTO {

    /** 操作类型：register/edit/dispose/manual_disposal_supplement/void/archive/ai_evaluate */
    private String operationType;
}
