package com.whulri.geodata.common.dto.dataStyle;

import com.whulri.geodata.common.dto.layerContent.LayerContentOutputDto;
import com.whulri.geodata.common.entity.DataStyleEntity;
import com.whulri.geodata.common.util.style.TifStyle;
import lombok.Data;

import java.util.List;

@Data
public class DataStyleOutputDto extends DataStyleEntity {
    /**
     * 样式类型名称
     */
    private String styleTypeName;

    /**
     * 样式默认图层名称
     */
    private String styleDefaultLayerName;

    /**
     * 样式内容
     */
    private LayerContentOutputDto layerContent;

    /**
     * 样式信息
     */
    private StyleInfo styleInfo;

    /**
     * 样式信息
     */
    private StyleInfo styleInfoDefault;


    /**
     * 规则信息
     */
    private List<RuleData> ruleDatas;

    /**
     * 字符信息
     */
    private List<String> symbolRulesdata;

    /**
     * 栅格样式信息
     */
    private List<TifStyle> tifStyles;

    /**
     * 三波段透明色（configType=5 时从 symbol 回填）
     */
    private String tifThreeColor;
}
