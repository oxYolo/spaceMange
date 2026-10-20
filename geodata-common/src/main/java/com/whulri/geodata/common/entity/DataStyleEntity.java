package com.whulri.geodata.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Date;

@Data
@TableName("sdms_datastyle")
@Schema(name = "DataStyleEntity 数据样式实体", description = "数据样式表")
@AllArgsConstructor
@NoArgsConstructor
public class DataStyleEntity {


    @TableId(value = "id", type = IdType.INPUT)
    @Schema(description = "样式编号")
    private String id;

    @TableField(value = "stylename")
    @Schema(description = "样式名称")
    private String styleName;

    @TableField(value = "styletype")
    @Schema(description = "样式分类")
    private String styleType;

    @TableField(value = "stylecontent")
    @Schema(description = "样式内容")
    private String styleContent;

    @TableField(value = "styledesc")
    @Schema(description = "样式描述")
    private String styleDesc;

    @TableField(value = "createdt")
    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createDT;

    @TableField(value = "createby")
    @Schema(description = "创建人")
    private String createBy;

    @TableField(value = "styledatatype")
    @Schema(description = "样式类型")
    private String styleDataType;

    @TableField(value = "styleconfigtype")
    @Schema(description = "样式配置类型")
    private String styleConfigType;

    @TableField(value = "styledefaultlayer")
    @Schema(description = "样式默认图层")
    private String styleDefaultLayer;

    @TableField(value = "stylerenderfield")
    @Schema(description = "样式渲染字段")
    private String styleRenderField;

    @TableField(value = "stylerenderfieldname")
    @Schema(description = "渲染字段名称")
    private String styleRenderFieldName;

    @TableField(value = "stylerendercolorband")
    @Schema(description = "样式渲染颜色带")
    private String styleRenderColorBand;

    @TableField(value = "stylerenderrule")
    @Schema(description = "样式渲染预设规则")
    private String styleRenderRule;

    @Schema(description = "符号")
    private String symbol;
}
