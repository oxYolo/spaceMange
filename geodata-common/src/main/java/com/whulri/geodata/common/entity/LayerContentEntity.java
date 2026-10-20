package com.whulri.geodata.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("sdms_layer")
public class LayerContentEntity {

    @TableId(value = "id")
    private String Id;

    /**
     * 图层名称
     */
    @TableField(value = "layername")
    private String LayerName;

    /**
     * 图层类型(点线面)
     */
    @TableField(value = "datatype")
    private String DataType;

    /**
     * 图层边界空间
     */
    @TableField(value = "layerbbox")
    private String LayerBBox;

    /**
     * 图层分类
     */
    @TableField(value = "layertype")
    private String LayerType;

    /**
     * 图层标签
     */
    @TableField(value = "layertag")
    private String LayerTag;

    /**
     * 图层描述
     */
    @TableField(value = "layerdesc")
    private String LayerDesc;

    /**
     * 图层业务表
     */
    @TableField(value = "layerattrtable")
    private String LayerAttrTable;

    /**
     * 图层空间表
     */
    @TableField(value = "layerspatialtable")
    private String LayerSpatialTable;

    /**
     * 空间参考
     */
    @TableField(value = "layerrefence")
    private String LayerRefence;
    /**
     * 原始空间参考
     */
    @TableField(value = "originlayerrefence")
    private String originLayerRefence;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "createdt")
    private LocalDateTime CreateDT;

    /**
     * north(北)
     */
    @TableField(value = "maxy")
    private BigDecimal MaxY;

    /**
     * south(南)
     */
    @TableField(value = "miny")
    private BigDecimal MinY;

    /**
     * west(西)
     */
    @TableField(value = "minx")
    private BigDecimal MinX;

    /**
     * east(东)
     */
    @TableField(value = "maxx")
    private BigDecimal MaxX;

    /**
     * 上传状态
     */
    @TableField(value = "uploadstatus")
    private String UploadStatus;

    /**
     * 拥有者
     */
    @TableField(value = "createby")
    private String CreateBy;

    /**
     * 图层默认样式
     */
    @TableField(value = "layerdefaultstyle")
    private String LayerDefaultStyle;

    /**
     * 上传图层样式（1-矢量图层；2-影像图层）
     */
    @TableField(value = "uploadfiletype")
    private String UploadFileType;

    /**
     * 上传文件名称
     */
    @TableField(value = "uploadfilename")
    private String UploadFileName;

    /**
     * 文件大小
     */
    @TableField(value = "filesize")
    private Integer FileSize;

    /**
     * 显示隐藏  0:隐藏，1:显示
     */
    @TableField(value = "isshow")
    private Integer IsShow;

    /**
     * 业务id
     */
    @TableField(value = "businessid")
    private String businessId;

    /**
     * 发布状态
     */
    @TableField(value = "publishStatus")
    private String publishStatus;

    /**
     * 发布类型
     */
    @TableField(value = "publishtype")
    private String publishType;

    /**
     * 排序
     */
    @TableField("layersort")
    private Integer layerSort;

    /**
     * 矢量文件编码
     */
    @TableField(value = "fileencoding")
    private String fileencoding;

    /**
     * 栅格文件波段数
     */
    @TableField("rastercount")
    private Integer rasterCount;

    /**
     * 栅格数据类型
     */
    @TableField("rasterdatatype")
    private String rasterDatatype;

    /**
     * 栅格数据不同值数量
     */
    @TableField("tifvariety")
    private Integer tifVariety;

    /**
     * 原始最大X
     */
    @TableField("originmaxx")
    private BigDecimal originMaxX;

    /**
     * 原始最小X
     */
    @TableField("originminx")
    private BigDecimal originMinX;

    /**
     * 原始最大Y
     */
    @TableField("originmaxy")
    private BigDecimal originMaxY;

    /**
     * 原始最小Y
     */
    @TableField("originminy")
    private BigDecimal originMinY;
}
