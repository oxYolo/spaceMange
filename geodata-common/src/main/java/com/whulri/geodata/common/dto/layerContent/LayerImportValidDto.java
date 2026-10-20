package com.whulri.geodata.common.dto.layerContent;

import lombok.Data;

@Data
public class LayerImportValidDto {
    private boolean status;
    private String message;
    private LayerContentDto layer;
    private String filePath;
}
