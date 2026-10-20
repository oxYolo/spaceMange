package com.whulri.geodata.common.dto.symbol;

import com.whulri.geodata.common.entity.SymbolEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SymbolDto {

    /**
     * 图标id
     */
    private String id;

    /**
     * 符号名称
     */
    private String symbolName;

    /**
     * 符号类型
     */
    private String symbolType;

    /**
     * 符号分类
     */
    private String symbolClassify;

    /**
     * 符号描述
     */
    private String symbolDesc;

    /**
     * 图片或字体
     */
    private String imageorFontType;

    /**
     * 图片或字体路径
     */
    private String path;

    /**
     * 图片长
     */
    private String imageLong;

    /**
     * 图片宽
     */
    private String imageWide;

    /**
     * 图片角度
     */
    private String imageAngle;

    /**
     * 字体大小
     */
    private String fontSize;

    /**
     * 字体颜色
     */
    private String fontColor;

    /**
     * 符号类型名称
     */
    private String symbolTypeName;

    /**
     * 创建时间
     */
    private LocalDateTime createDT;

    /**
     * 创建人
     */
    private String createBy;

    /**
     * 字体名称
     */
    private String fontFamily;

    /**
     * 字符的Unicode
     */
    private String charUnicode;

    /**
     * 字体角度
     */
    private String fontAngle;
}
