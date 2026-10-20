package com.whulri.geodata.common.dto.layerTool;

import lombok.Data;

/**
 * 矢量栅格化参数
 * @author zzw
 * @date 2026/3/17 11:03
 */
@Data
public class ShpToTifDTO {

    /**
     * 属性名称
     */
    private String attributeName;

    /**
     * x像元大小
     */
    private Double x;

    /**
     * y像元大小
     */
    private Double y;

    /**
     * 高
     */
    private Integer height;

    /**
     * 宽
     */
    private Integer width;

    /**
     * 1:空间分辨率   2:网格大小
     */
    private Integer type;

}
