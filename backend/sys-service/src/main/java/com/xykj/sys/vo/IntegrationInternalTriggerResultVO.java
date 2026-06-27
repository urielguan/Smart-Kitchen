package com.xykj.sys.vo;

import lombok.Data;

@Data
public class IntegrationInternalTriggerResultVO {

    private IntegrationExecutionResultVO executionResult;

    private IntegrationInternalBindingSummaryVO binding;
}
