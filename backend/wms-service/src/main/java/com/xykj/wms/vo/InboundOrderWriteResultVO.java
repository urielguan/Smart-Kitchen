package com.xykj.wms.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InboundOrderWriteResultVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long version;
}
