package com.whulri.geodata.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 图层属性参数表
 *
 * @TableName sdms_layerfield
 */
@TableName(value = "sdms_layerfield")
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(name = "LayerField对象", description = "图层字段")
public class LayerFieldEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID
     */
    @TableId(value = "id", type = IdType.NONE)
    private String id;

    /**
     * 图层目录ID
     */
    @TableField("layerid")
    private String layerID;

    /**
     * 属性名称
     */
    @TableField("attributename")
    private String attributeName;

    /**
     * 属性描述
     */
    @TableField("attributedesc")
    private String attributeDesc;

    /**
     * 属性类型
     */
    @TableField("attributetype")
    private String attributeType;

    /**
     * 属性长度
     */
    @TableField("attributelength")
    private String attributeLength;

    /**
     * 属性小数位
     */
    @TableField("attributeprecision")
    private String attributePrecision;

    /**
     * 输入控制
     */
    @TableField("attributeinputctrl")
    private String attributeInputCtrl;

    /**
     * 最大值
     */
    @TableField("attributeinputmax")
    private String attributeInputMax;

    /**
     * 最小值
     */
    @TableField("attributeinputmin")
    private String attributeInputMin;

    /**
     * 默认值
     */
    @TableField("attributedefault")
    private String attributeDefault;

    /**
     * 是否为空（通常 '0'/'1' 或 'Y'/'N'）
     */
    @TableField("attributeisnull")
    private String attributeIsNull;

    /**
     * 输入格式
     */
    @TableField("attributeinputformat")
    private String attributeInputFormat;

    /**
     * 备注
     */
    @TableField("remark")
    private String remark;

    /**
     * 创建时间
     */
    @TableField("createdt")
    private LocalDateTime createDT;

    /**
     * 单位
     */
    @TableField("attributeunit")
    private String attributeUnit;

    /**
     * 数据分类
     */
    @TableField("attributedatatype")
    private String attributeDataType;

    /**
     * 文字符连接
     */
    @TableField("attributevaluelink")
    private String attributeValueLink;

    /**
     * 数据源
     */
    @TableField("attributedatasource")
    private String attributeDataSource;

    /**
     * 计算公式
     */
    @TableField("attributecalcomp")
    private String attributeCalComp;

    /**
     * 属性排序
     */
    @TableField("attributesort")
    private Integer attributeSort;

    /**
     * 新增字段（JSON 或其他结构化字符串）
     */
    @TableField("newfields")
    private String newFields;

    /**
     * 中文名称
     */
    @TableField("chinesename")
    private String chineseName;
}
