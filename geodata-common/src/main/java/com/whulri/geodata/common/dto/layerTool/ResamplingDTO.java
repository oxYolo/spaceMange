package com.whulri.geodata.common.dto.layerTool;

import lombok.Data;

/** 重采样参数
 * @author zzw
 * @date 2026/3/17 11:23
 */
@Data
public class ResamplingDTO {

    /**
     * 输出栅格宽度（像素数）
     */
    private Integer xsize;

    /**
     * 输出栅格高度（像素数）
     */
    private Integer ysize;

    /**
     * 格网X方向单元格大小（地理单位 米）
     */
    private Double xCell;

    /**
     * 格网Y方向单元格大小（地理单位米）
     */
    private Double yCell;

    /**
     * 重采样方法，默认bilinear
     * 可选值: nearest, bilinear, cubic, cubicspline, lanczos, average, mode
     */
    private String resamplingMethod = "bilinear";

}
