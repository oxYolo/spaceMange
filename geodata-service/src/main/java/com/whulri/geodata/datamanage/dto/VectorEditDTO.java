package com.whulri.geodata.datamanage.dto;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

import java.io.Serializable;

/**
 * 矢量编辑DTO
 */
@Data
public class VectorEditDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 图层ID（sdms_layer表主键）
     */
    @JSONField(name = "layerId")
    private String layerId;

    /**
     * GeoJSON FeatureCollection 对象
     */
    @JSONField(name = "geoJsonCollection")
    private JSONObject geoJsonCollection;
}
