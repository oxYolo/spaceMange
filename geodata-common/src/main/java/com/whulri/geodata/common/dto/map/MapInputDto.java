package com.whulri.geodata.common.dto.map;

import com.whulri.geodata.common.entity.MapEntity;
import lombok.Data;

@Data
public class MapInputDto extends MapEntity {

    /**
     * 地图分类列表
     */
    private String mapTypeList;

    /**
     * （0和1）
     */
    private String type;

    /**
     * 数据类型(地图类／图层类)
     */
    private String dictCodeID;


}
