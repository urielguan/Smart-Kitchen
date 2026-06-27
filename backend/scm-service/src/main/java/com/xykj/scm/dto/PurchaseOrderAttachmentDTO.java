package com.xykj.scm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 采购订单附件入参
 */
@Data
public class PurchaseOrderAttachmentDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    @NotBlank(message = "附件名称不能为空")
    @Size(max = 255, message = "附件名称长度不能超过255个字符")
    private String name;

    @Size(max = 30, message = "附件大小长度不能超过30个字符")
    private String size;

    @NotBlank(message = "附件地址不能为空")
    @Size(max = 1000, message = "附件地址长度不能超过1000个字符")
    private String url;

    private Integer sortOrder;
}
