package com.whulri.geodata.common.dto.mapRelation;

import com.whulri.geodata.common.entity.LayerContentEntity;
import com.whulri.geodata.common.entity.MapRelationEntity;
import lombok.Data;

@Data
public class MapRelationDto extends MapRelationEntity {

    /**
     * 地图名称
     */
    private String MapName;

    /**
     * 图层目录名称
     */
    private String DataConfigName;

    /**
     * 图层样式名称
     */
    private String DataStyleName;

    /**
     *图层类型
     */
    private String dataTypeName;

    /**
     *显示隐藏
     */
    private Integer isShow;

    /**
     * 图层详细信息
     */
    private LayerContentEntity layers;
}
