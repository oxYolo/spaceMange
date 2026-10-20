package com.whulri.geodata.common.dto.dataType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.whulri.geodata.common.entity.DataTypeEntity;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataTypeInputDto extends DataTypeEntity {
}
