package com.whulri.geodata.common.dto.mapRelation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StyleInputDto {

    private String mapId;
    private String layerStr;
    private String styleStr;
}
