package com.whulri.geodata.common.dto.layerContent;

import com.whulri.geodata.common.entity.LayerContentEntity;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class LayerContentDto extends LayerContentEntity {


    /**
     * 图层类型(点线面)名称
     */
    private String DataTypeName;

    /**
     * 图层分类
     */
    private String LayerTypeName;

    /**
     * 空间参考
     */
    private String LayerRefenceName;

    /**
     * 图层默认样式名称
     */
    private String LayerDefaultStyleName;

    /**
     * 缩略图地址
     */
    private String ImgSrc;


    // region 扩展传值

    /**
     * 数据参数
     */
    private String ResultData;

    /**
     * 标识单波段还是3波段
     */
    private Integer rasterCount;

    /**
     * 栅格数据类型
     */
    private String rasterDatatype;

    /**
     * 拥有者
     */
    private String createByName;

    /**
     * 原始空间参考
     */
    private String originLayerRefence;

    /**
     * 原始最大X
     */
    private BigDecimal originMaxX;

    /**
     * 原始最小X
     */
    private BigDecimal originMinX;

    /**
     * 原始最大Y
     */
    private BigDecimal originMaxY;

    /**
     * 原始最小Y
     */
    private BigDecimal originMinY;
}
