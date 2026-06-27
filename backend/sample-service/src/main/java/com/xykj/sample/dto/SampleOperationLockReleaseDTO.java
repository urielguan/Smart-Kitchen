package com.xykj.sample.dto;

import lombok.Data;

/**
 * 留样操作锁释放请求
 */
@Data
public class SampleOperationLockReleaseDTO {

    /** 锁令牌 */
    private String lockToken;
}
