package com.whulri.geodata.common.dto.dataStyle;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StyleInfo extends PolygonStyle {

    /**
     * 最小比例尺
     */
    @SerializedName("MinScaleDenominator")
    private String minScaleDenominator;

    /**
     * 最大比例尺
     */
    @SerializedName("MaxScaleDenominator")
    private String maxScaleDenominator;
}
