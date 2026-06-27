package com.xykj.auth.vo;

import lombok.Data;

@Data
public class PermissionVO {

    private Long permissionId;
    private String permissionCode;
    private String permissionName;
    private String permissionType;
    private String resourcePath;
}
