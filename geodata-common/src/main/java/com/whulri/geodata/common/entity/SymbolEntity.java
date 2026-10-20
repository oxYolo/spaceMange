package com.whulri.geodata.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 符号管理表
 */
@Data
@TableName("sdms_symbol")
@AllArgsConstructor
@NoArgsConstructor
public class SymbolEntity {

    @TableId("id")
    private String id;

    /**
     * 符号名称
     */
    @TableField(value = "symbolname")
    private String symbolName;

    /**
     * 符号类型
     */
    @TableField(value = "symboltype")
    private String symbolType;

    /**
     * 符号分类
     */
    @TableField(value = "symbolclassify")
    private String symbolClassify;

    /**
     * 符号描述
     */
    @TableField(value = "symbolcdesc")
    private String symbolDesc;

    /**
     * 图片或字体
     */
    @TableField(value = "imageorfonttype")
    private String imageorFontType;

    /**
     * 图片或字体路径
     */
    @TableField(value = "path")
    private String path;

    /**
     * 图片长
     */
    @TableField(value = "imagelong")
    private String imagelong;

    /**
     * 图片宽
     */
    @TableField(value = "imagewide")
    private String imageWide;

    /**
     * 图片角度
     */
    @TableField(value = "imageangle")
    private String imageAngle;

    /**
     * 字体大小
     */
    @TableField(value = "fontsize")
    private String fontSize;

    /**
     * 字体颜色
     */
    @TableField(value = "fontcolor")
    private String fontColor;

    /**
     * 字体角度
     */
    @TableField(value = "fontangle")
    private String fontAngle;

    /**
     * 创建人
     */
    @TableField(value = "createby")
    private String createBy;

    /**
     * 创建时间
     */
    @TableField(value = "createdt")
    private LocalDateTime CreateDT;

    /**
     * 字体名称
     */
    @TableField(value = "fontfamily")
    private String fontFamily;

    /**
     * 字体符号
     */
    @TableField(value = "charunicode")
    private String charUnicode;

}
