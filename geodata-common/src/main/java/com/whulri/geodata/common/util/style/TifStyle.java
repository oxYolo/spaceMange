package com.whulri.geodata.common.util.style;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * TIF样式
 */
@Data
@Schema(name = "TifStyle", description = "TIF样式")
public class TifStyle {

    /**
     * 填充方式
     */
    @Schema(description = "填充方式")
    private String fillWay;

    /**
     * 填充颜色
     */
    @Schema(description = "填充颜色")
    private String color;

    @Schema(description = "开始值")
    private String quantityOne;

    /**
     * 属性
     */
    @Schema(description = "属性")
    private String quantity;

    /**
     * 标题
     */
    @Schema(description = "标题")
    private String label;

    /**
     * 透明度
     */
    @Schema(description = "透明度")
    private String opacity;

    @Schema(description = "范围值记录")
    private String rangeValue;
}
