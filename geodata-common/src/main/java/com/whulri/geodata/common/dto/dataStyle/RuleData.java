package com.whulri.geodata.common.dto.dataStyle;

import lombok.Data;

@Data
public class RuleData {
    /**
     * 几何样式
     */
    private String polygonStyle;

    /**
     * 开始值
     */
    private String valueOne;

    /**
     *  值
     */
    private String value;

    /**
     * 标题
     */
    private String title;

    /**
     * 符号
     */
    private String count;

    /**
     * 比较操作符: >, >=, <, <=, =
     */
    private String operator;

    /// <summary>
    /// 最小比例尺
    /// </summary>
    public String minScaleDenominator;
    /// <summary>
    /// 最大比例尺
    /// </summary>
    public String maxScaleDenominator;

}
