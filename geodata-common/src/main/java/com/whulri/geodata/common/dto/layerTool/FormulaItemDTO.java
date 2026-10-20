package com.whulri.geodata.common.dto.layerTool;

import lombok.Data;

/**
 * 栅格叠加计算公式元素
 * @author luwl
 * @date 2026/4/2
 */
@Data
public class FormulaItemDTO {

    /**
     * 类型：layer（图层ID）或 symbol（操作符）
     */
    private String dataType;

    /**
     * 值：layer时为图层ID，symbol时为+ - * / 等操作符
     */
    private String value;

}
