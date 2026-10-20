package com.whulri.geodata.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("sdms_metadata")
@JsonIgnoreProperties(ignoreUnknown = true)
public class MetadataEntity {


    @TableId(value = "\"Id\"", type = IdType.INPUT) // 注意：双引号转义
    private String id;

    /**
     * 数据编号
     */
    @TableField(value = "dataid")
    private String dataId;

    /**
     * 版本
     */
    @TableField(value = "\"Version\"")
    private String version;

    /**
     * 概要
     */
    @TableField(value = "\"Summary\"")
    private String summary;

    /**
     * 目标平台
     */
    @TableField(value = "\"Target\"")
    private String target;

    /**
     * 上版本
     */
    @TableField(value = "\"MaintenanceFre\"")
    private String maintenanceFre;

    /**
     * 当前版本
     */
    @TableField(value = "\"AdministrativeDivisions\"")
    private String administrativeDivisions;

    /**
     * 一般限制
     */
    @TableField(value = "\"NomalLimit\"")
    private String nomalLimit;

    /**
     * 其他限制
     */
    @TableField(value = "\"OtherLimit\"")
    private String otherLimit;

    /**
     * 空间参考
     */
    @TableField(value = "\"SpatialGeographical\"")
    private String spatialGeographical;

    /**
     * 开始日期
     */
    @TableField(value = "\"StartDT\"")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startDT;

    /**
     * 结束日期
     */
    @TableField(value = "\"EndDT\"")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endDT;

    /**
     * 其他信息
     */
    @TableField(value = "\"AdditionalInfo\"")
    private String additionalInfo;

    /**
     * 发布日期
     */
    @TableField(value = "\"PublishDT\"")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime publishDT;

    /**
     * 修改日期
     */
    @TableField(value = "\"ModifyDT\"")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime modifyDT;

    /**
     * 描述
     */
    @TableField(value = "\"MetaDataQualityDesc\"")
    private String metaDataQualityDesc;

    /**
     * 缩略图地址
     */
    @TableField(value = "\"ThumbnalAddress\"")
    private String thumbnalAddress;

    /**
     * 类型
     */
    @TableField(value = "\"MetaDataType\"")
    private String metaDataType;

    /**
     * 标签
     */
    @TableField(value = "\"MetaDataTag\"")
    private String metaDataTag;

    /**
     * 创建者
     */
    @TableField(value = "\"CreateBy\"")
    private String createBy;

    /**
     * 拥有者
     */
    @TableField(value = "\"Owner\"")
    private String owner;

    /**
     * 是否发布
     */
    @TableField(value = "\"IsPublish\"")
    private Integer isPublish;

    /**
     * 创建日期
     */
    @TableField(value = "\"CreateDT\"")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createDT;

    /**
     * 数据类型
     */
    @TableField(value = "datatype")
    private String dataType;

    /**
     * 元数据名称
     */
    @TableField(value = "metadataname")
    private String metadataName;

    /**
     * 空间参考
     */
    @TableField(value = "spatialreferencesystem")
    private String spatialReferenceSystem;

    /**
     * 数据年份
     */
    @TableField(value = "datayear")
    private String dataYear;

    /**
     * 数据单位
     */
    @TableField(value = "dataunit")
    private String dataUnit;

    /**
     * 数据内容描述
     */
    @TableField(value = "datacontentdec")
    private String dataContentDec;

    /**
     * 东
     */
    @TableField(value = "east")
    private BigDecimal east;

    /**
     * 西
     */
    @TableField(value = "west")
    private BigDecimal west;

    /**
     * 南
     */
    @TableField(value = "south")
    private BigDecimal south;

    /**
     * 北
     */
    @TableField(value = "north")
    private BigDecimal north;

    /**
     * 像元最小值
     */
    @TableField(value = "minpixel")
    private BigDecimal minPixel;

    /**
     * 像元最大值
     */
    @TableField(value = "maxpixel")
    private BigDecimal maxPixel;

    /**
     * 空间分辨率
     */
    @TableField(value = "spatialresolution")
    private String spatialResolution;

    /**
     * 栅格图层无效值
     */
    @TableField("nodata")
    private BigDecimal noData;

    /**
     * 最小垂向坐标
     */
    @TableField("minvertical")
    private BigDecimal minVertical;

    /**
     * 最大垂向坐标
     */
    @TableField("maxvertical")
    private BigDecimal maxVertical;

//    /**
//     * tif文件波段数
//     */
//    @TableField("rastercount")
//    private Integer rasterCount;
}
