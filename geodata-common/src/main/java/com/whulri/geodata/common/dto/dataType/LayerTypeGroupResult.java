package com.whulri.geodata.common.dto.dataType;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LayerTypeGroupResult {
    private String dataType;
    private String dataTypeName;
    private Integer dataCount;
}
