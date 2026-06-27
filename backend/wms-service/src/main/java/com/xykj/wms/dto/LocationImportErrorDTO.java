package com.xykj.wms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocationImportErrorDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer rowNumber;
    private String field;
    private String reason;
}
