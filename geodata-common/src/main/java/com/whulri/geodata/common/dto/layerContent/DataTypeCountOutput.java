package com.whulri.geodata.common.dto.layerContent;

import lombok.Data;

import java.util.List;

@Data
public class DataTypeCountOutput {
    private List<DataTypeCountDto> data;
    private List<String> colors;
    private List<String> names;
}
