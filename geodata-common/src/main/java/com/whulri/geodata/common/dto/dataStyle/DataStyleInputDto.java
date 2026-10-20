package com.whulri.geodata.common.dto.dataStyle;

import com.whulri.geodata.common.dto.layerContent.LayerContentOutputDto;
import com.whulri.geodata.common.entity.DataStyleEntity;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.whulri.geodata.common.util.style.TifStyle;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataStyleInputDto extends DataStyleEntity {

    /**
     * 样式信息
     */
    private StyleInfo styleInfo;

    /**
     * 默认信息
     */
    private StyleInfo styleInfoDefault;

    /**
     * 规则信息
     */
    private List<RuleData> ruleDatas;

    /**
     * 栅格样式信息
     */
    private List<TifStyle> tifStyles;


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
     * 字符信息
     */
    private List<String> symbolRulesdata;

    /**
     * 图层数据id
     */
    private String layerId;
    /**
     * tif样式类型  1:灰度拉伸 2:分类设置 3:唯一值
     */
    private String type;
    /**
     * 三波段透明色颜色设置
     */
    private String tifThreeColor;

    /**
     * 波段数
     */
    private Integer bandIndex;
}
