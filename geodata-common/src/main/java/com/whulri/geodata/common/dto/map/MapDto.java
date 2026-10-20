package com.whulri.geodata.common.dto.map;

import com.whulri.geodata.common.entity.MapEntity;
import lombok.Data;

@Data
public class MapDto extends MapEntity {

    /**
     * 比例尺名称
     */
    private String mapScaleName;

    /**
     * 空间参考名称
     */
    private String spatialRefenceName;

    /**
     * north(北  最大纬度 度分秒)
     */
    private String maxDegLat;

    /**
     * south(南  最小纬度 度分秒)
     */
    private String minDegLat;

    /**
     * west(西  最小经度 度分秒)
     */
    private String minDegLon;

    /**
     * east(东  最大经度 度分秒)
     */
    private String maxDegLon;

    /**
     * 缩略图地址
     */
    private String imgSrc;

    private String dataType;
}
