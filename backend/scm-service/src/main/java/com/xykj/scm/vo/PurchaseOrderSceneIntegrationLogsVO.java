package com.xykj.scm.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PurchaseOrderSceneIntegrationLogsVO {

    private PurchaseOrderSceneIntegrationBindingVO binding;

    private List<PurchaseOrderSceneIntegrationLogVO> syncLogs = new ArrayList<>();

    private List<PurchaseOrderSceneIntegrationLogVO> callbackLogs = new ArrayList<>();
}
