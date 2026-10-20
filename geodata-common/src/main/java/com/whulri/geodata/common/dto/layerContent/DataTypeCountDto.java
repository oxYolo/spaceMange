package com.whulri.geodata.common.dto.layerContent;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 图层统计
 */
@Data
@Schema(name = "图层统计")
public class DataTypeCountDto {

    /**
     * 分类ID
     */
    @Schema(description = "分类ID")
    private String id;

    /**
     * 分类名称
     */
    @Schema(description = "分类名称")
    private String name;

    /**
     * 统计
     */
    @Schema(description = "统计")
    private Integer value;
}
