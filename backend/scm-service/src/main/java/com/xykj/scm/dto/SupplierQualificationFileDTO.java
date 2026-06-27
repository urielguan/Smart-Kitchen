package com.xykj.scm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 供应商资质文件
 */
@Data
public class SupplierQualificationFileDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "资质文件ID不能为空")
    private Long id;

    @NotBlank(message = "资质文件名不能为空")
    @Size(max = 255, message = "资质文件名长度不能超过255个字符")
    private String name;

    @NotBlank(message = "资质文件大小不能为空")
    @Size(max = 30, message = "资质文件大小长度不能超过30个字符")
    private String size;

    @Size(max = 1000, message = "资质文件地址长度不能超过1000个字符")
    private String url;
}
