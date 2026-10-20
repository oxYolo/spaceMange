package com.whulri.geodata.common.dto.layerContent;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 图层信息
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LayerContentInputDto {

    /**
     * 图层编号
     */
    private String id;

    /**
     * 图层名称
     */
    private String layerName;

    /**
     * 图层类型(点线面)
     */
    private String dataType;

    /**
     * 图层边界空间
     */
    private String layerBBox;

    /**
     * 图层分类
     */
    private String layerType;

    /**
     * 图层标签
     */
    private String layerTag;

    /**
     * 图层描述
     */
    private String layerDesc;

    /**
     * 图层业务表
     */
    private String layerAttrTable;

    /**
     * 图层空间表
     */
    private String layerSpatialTable;

    /**
     * 空间参考
     */
    private String layerRefence;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createDT;

    /**
     * north(北)
     */
    private BigDecimal maxY;

    /**
     * south(南)
     */
    private BigDecimal minY;

    /**
     * west(西)
     */
    private BigDecimal minX;

    /**
     * east(东)
     */
    private BigDecimal maxX;

    /**
     * 上传状态
     */
    private String uploadStatus;

    /**
     * 拥有者
     */
    private String createBy;

    /**
     * 拥有者
     */
    private String createByName;

    /**
     * 图层默认样式
     */
    private String layerDefaultStyle;

    /**
     * 上传图层样式（1-矢量图层；2-影像图层）
     */
    private String uploadFileType;

    /**
     * 上传文件名称
     */
    private String uploadFileName;

    /**
     * 文件大小
     */
    private int fileSize;

    /**
     * 显示隐藏  0:隐藏，1:显示
     */
    private Integer isShow;

    /**
     * 数据来源
     */
    private String dataSource;

    /**
     * 数据机密
     */
    private String dataConfidential;

    /**
     * 服务对象
     */
    private String serviceObject;

    /**
     * 关键词
     */
    private String keywords;

    /**
     * 数据描述
     */
    private String dataDesc;

    /**
     * 数据类型
     */
    private String dataTypes;

    /**
     * 数据精度
     */
    private String dataAccuracy;

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
    public String publishType;

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
    private boolean IsSinger;

    private Integer layerSort;

    private String fileencoding;

    private String rasterCount;

    private String rasterDatatype;

    private String parentId;
}
