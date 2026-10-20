package com.whulri.geodata.common.dto.layerField;

import com.whulri.geodata.common.entity.LayerFieldEntity;
import lombok.Data;

@Data
public class LayerFieldInputDto extends LayerFieldEntity {

    private Properties properties;
}
