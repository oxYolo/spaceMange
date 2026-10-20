package com.whulri.geodata.common.dto;

import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class LayerFieldQuery extends PageDTO implements Serializable {

    private String layerId;

    private List<JSONObject> where;

    private String wkt;

    /**
     * 半径
     */
    private Double distance;

    /**
     * 经度
     */
    private Double lon;

    /**
     * 纬度
     */
    private Double lat;

    /**
     * 多图层ID
     */
    private List<String> layerIds;

}
