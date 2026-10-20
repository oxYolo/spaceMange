package com.whulri.geodata.common.dto.layerContent;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 图层信息
 */
@Data
public class LayerContentOutputDto {

    /**
     * 图层编号
     */
    private String Id;

    /**
     * 图层名称
     */
    private String LayerName;

    /**
     * 图层类型(点线面)
     */
    private String DataType;

    /**
     * 图层边界空间
     */
    private String LayerBBox;

    /**
     * 图层分类
     */
    private String LayerType;

    /**
     * 图层标签
     */
    private String LayerTag;

    /**
     * 图层描述
     */
    private String LayerDesc;

    /**
     * 图层业务表
     */
    private String LayerAttrTable;

    /**
     * 图层空间表
     */
    private String LayerSpatialTable;

    /**
     * 空间参考
     */
    private String LayerRefence;

    /**
     * 创建时间
     */
    private LocalDateTime CreateDT;

    /**
     * 图层样式ID
     */
    private String DataStyleID;

    /**
     * 图层样式名称
     */
    private String DataStyleName;

    /**
     * 图层排序
     */
    private Integer DataSort;

    /**
     * north(北)
     */
    private BigDecimal MaxY;

    /**
     * south(南)
     */
    private BigDecimal MinY;

    /**
     * west(西)
     */
    private BigDecimal MinX;

    /**
     * east(东)
     */
    private BigDecimal MaxX;

    /**
     * 上传状态
     */
    private String UploadStatus;

    /**
     * 拥有者
     */
    private String CreateBy;

    /**
     * 图层默认样式
     */
    private String LayerDefaultStyle;

    /**
     * 上传图层样式（1-矢量图层；2-影像图层）
     */
    private String UploadFileType;

    /**
     * 上传文件名称
     */
    private String UploadFileName;

    /**
     * 显示隐藏  0:显示，1:隐藏
     */
    private Integer IsShow;

    /**
     * 缩略图地址
     */
    private String ImgSrc;

    /**
     * 文件大小
     */
    private int FileSize;

    /**
     * 业务id
     */
    private String businessId;

    /**
     * 发布状态
     */
    private String publishStatus;

    /**
     * 发布类型
     */
    private String publishType;
}
