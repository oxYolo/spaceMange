package com.whulri.geodata.common.dto.dataStyle;

import com.google.gson.annotations.SerializedName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 多边形样式
 */
@Data
@Schema(name = "PolygonStyle", description = "多边形样式")
public class PolygonStyle {

    /**
     * 样式类型，1点，2线，3面
     */
    @Schema(description = "样式类型，1点，2线，3面")
    @SerializedName("Type")
    private String type;

    /**
     * 是否填充
     */
    @Schema(description = "是否填充")
    @SerializedName("IsIcon")
    private Boolean isIcon = false;

    /**
     * 图片路径
     */
    @Schema(description = "图片路径")
    @SerializedName("IconPath")
    private String iconPath;

    /**
     * 图片大小
     */
    @Schema(description = "图片大小")
    @SerializedName("IconSize")
    private String iconSize;

    /**
     * 图片角度
     */
    @Schema(description = "图片角度")
    @SerializedName("IconRotation")
    private String iconRotation;

    /**
     * 点类型（圆形，三角形，方形）
     */
    @Schema(description = "点类型（圆形，三角形，方形）")
    @SerializedName("PointType")
    private String pointType;

    /**
     * 填充图标
     */
    @Schema(description = "填充图标")
    @SerializedName("WellKnownNameIcon")
    private String wellKnownNameIcon;

    /**
     * 填充边界
     */
    @Schema(description = "填充边界")
    @SerializedName("strokeIcon")
    private String strokeIcon;

    /**
     * 填充宽度
     */
    @Schema(description = "填充宽度")
    @SerializedName("strokeWidthIcon")
    private String strokeWidthIcon;

    /**
     * 大小
     */
    @Schema(description = "大小")
    @SerializedName("GraphicSize")
    private String graphicSize;

    /**
     * 在线资源路径
     */
    @Schema(description = "在线资源路径")
    @SerializedName("OnlineHref")
    private String onlineHref;

    /**
     * 虚线偏移
     */
    @Schema(description = "虚线偏移")
    @SerializedName("strokeDashoffset")
    private String strokeDashoffset;

    /**
     * 虚线数组
     */
    @Schema(description = "虚线数组")
    @SerializedName("strokeDasharray")
    private String strokeDasharray;

    /**
     * 是否默认规则
     */
    @Schema(description = "是否默认规则")
    @SerializedName("IsDefaultRule")
    private Boolean isDefaultRule = false;

    /**
     * 是否渲染线
     */
    @Schema(description = "是否渲染线")
    @SerializedName("IsOutline")
    private Boolean isOutline = false;

    /**
     * 线宽度
     */
    @Schema(description = "线宽度")
    @SerializedName("LineWidth")
    private Double lineWidth;

    /**
     * 线颜色
     */
    @Schema(description = "线颜色")
    @SerializedName("OutlineColor")
    private String outlineColor;

    /**
     * 线透明度
     */
    @Schema(description = "线透明度")
    @SerializedName("LineTransparency")
    private Double lineTransparency;

    /**
     * 线型（实线1，虚线2, 点线3， 虚点线 4）
     */
    @Schema(description = "线型（实线1，虚线2, 点线3， 虚点线 4）")
    @SerializedName("LineType")
    private Integer lineType;

    /**
     * 是否填充
     */
    @Schema(description = "是否填充")
    @SerializedName("IsFill")
    private Boolean isFill = false;

    /**
     * 填充透明度
     */
    @Schema(description = "填充透明度")
    @SerializedName("FillTransparency")
    private Double fillTransparency;

    /**
     * 填充颜色
     */
    @Schema(description = "填充颜色")
    @SerializedName("PolygonColor")
    private String polygonColor;

    /**
     * 是否是字体
     */
    @Schema(description = "是否是字体")
    @SerializedName("IsFont")
    private Boolean isFont;

    /**
     * 字体名称
     */
    @Schema(description = "字体名称")
    @SerializedName("FontName")
    private String fontName;

    /**
     * 字体大小
     */
    @Schema(description = "字体大小")
    @SerializedName("FontSize")
    private String fontSize;

    /**
     * 字体编码
     */
    @Schema(description = "字体编码")
    @SerializedName("charUnicode")
    private String charUnicode;

    /**
     * 字体颜色
     */
    @Schema(description = "字体颜色")
    @SerializedName("FontColor")
    private String fontColor;

    /**
     * 字体角度
     */
    @Schema(description = "字体角度")
    @SerializedName("FontRotation")
    private String fontRotation;

    /**
     * 是否标注
     */
    @Schema(description = "是否标注")
    @SerializedName("IsMark")
    private Boolean isMark;

    /**
     * 选择的字段名称
     */
    @Schema(description = "选择的字段名称")
    @SerializedName("MarkName")
    private String markName;

    /**
     * 标注字体名称
     */
    @Schema(description = "标注字体名称")
    @SerializedName("MarkFontName")
    private String markFontName;

    /**
     * 标注的字体大小
     */
    @Schema(description = "标注的字体大小")
    @SerializedName("MarkFontSize")
    private Double markFontSize;

    /**
     * 水平偏移
     */
    @Schema(description = "水平偏移")
    @SerializedName("DisplacementX")
    private String displacementX;

    /**
     * 垂直偏移
     */
    @Schema(description = "垂直偏移")
    @SerializedName("DisplacementY")
    private String displacementY;

    /**
     * 填充颜色
     */
    @Schema(description = "填充颜色")
    @SerializedName("FillColor")
    private String fillColor;

    @Schema(description = "最小比例尺")
    public String minScaleDenominator;

    @Schema(description = "最大比例尺")
    public String maxScaleDenominator;
}
