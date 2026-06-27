package com.xykj.sys.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class IntegrationInternalLogsVO {

    private IntegrationInternalBindingSummaryVO binding;

    private List<IntegrationInternalLogBriefVO> syncLogs = new ArrayList<>();

    private List<IntegrationInternalLogBriefVO> callbackLogs = new ArrayList<>();
}
