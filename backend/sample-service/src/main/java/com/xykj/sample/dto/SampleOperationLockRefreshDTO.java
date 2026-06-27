package com.xykj.sample.dto;

import lombok.Data;

/**
 * 留样操作锁续租请求
 */
@Data
public class SampleOperationLockRefreshDTO {

    /** 锁令牌 */
    private String lockToken;
}
