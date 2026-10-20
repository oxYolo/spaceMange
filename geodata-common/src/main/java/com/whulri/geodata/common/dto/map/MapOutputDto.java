package com.whulri.geodata.common.dto.map;

import com.whulri.geodata.common.entity.MapEntity;
import lombok.Data;

@Data
public class MapOutputDto extends MapEntity {
    /**
     * 地图分类编码
     */
    private String mapTypeCode;

    /**
     * 比例尺名称
     */
    private String mapScaleName;

    /**
     * 空间参考名称
     */
    private String spatialRefenceName;

    /**
     *  north(北)
     */
    private String maxYName;

    /**
     * south(南)
     */
    private String minYName;

    /**
     * west(西)
     */
    private String minXName;

    /**
     * east(东)
     */
    private String maxXName;

    /**
     * 缩略图地址
     */
    private String imgSrc;
}
