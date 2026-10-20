package com.whulri.geodata.common.dto.dataType;

import lombok.Data;

@Data
public class DataTypeUpdateDto {

    private String id;
    private String parentId;
    private Integer codeSort;
}
