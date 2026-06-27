package com.xykj.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("material_food_mapping")
public class MaterialFoodMapping implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long materialId;
    private Long foodItemId;
    private String matchStatus;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
