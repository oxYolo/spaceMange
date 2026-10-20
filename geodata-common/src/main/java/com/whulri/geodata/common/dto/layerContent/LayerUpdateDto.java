package com.whulri.geodata.common.dto.layerContent;

import lombok.Data;

@Data
public class LayerUpdateDto {
    private String id;
    private String parentId;
    private String type;
    private Integer sort;
}
