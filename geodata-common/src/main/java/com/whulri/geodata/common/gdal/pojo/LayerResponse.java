package com.whulri.geodata.common.gdal.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LayerResponse implements Serializable {

    @JsonProperty("featureTypes")
    private LayerSet featureTypes;
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    // LayerSet 类
    public static class LayerSet {

        @JsonProperty("featureType")
        private List<Layer> featureType;
    }
}
