package com.xykj.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("wms_inbound_order_idempotency")
public class InboundOrderActionIdempotency implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long inboundOrderId;
    private String action;
    private String idempotencyKey;
    private Long requestVersion;
    private Long responseVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
