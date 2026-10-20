package com.whulri.geodata.common.dto.dataStyle;

import com.whulri.geodata.common.entity.DataStyleEntity;
import lombok.Data;

@Data
public class DataStyleDto extends DataStyleEntity {

    /**
     * 样式类型名称
     */
    private String styleTypeName;
}
