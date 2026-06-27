package com.xykj.sys.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 满意度评价DTO
 */
@Data
public class SatisfactionFormDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 满意度：satisfied=满意，neutral=一般，dissatisfied=不满意
     */
    @NotBlank(message = "满意度不能为空")
    private String satisfaction;

    /**
     * 满意度备注
     */
    private String satisfactionRemark;
}
