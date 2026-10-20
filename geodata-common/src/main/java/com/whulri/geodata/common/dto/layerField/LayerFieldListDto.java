package com.whulri.geodata.common.dto.layerField;

import lombok.Data;

import java.util.List;

@Data
public class LayerFieldListDto {

    private String message;
    private boolean isError;
    private String dataType;
    private List<LayerFieldDto> layerField;
}
