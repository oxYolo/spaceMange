package com.whulri.geodata.common.dto.layerTool;

import lombok.Data;

@Data
public class CutDto {

    private String wkt;

    private Double lon;

    private Double lat;

    private Double distance;

}
