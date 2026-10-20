package com.whulri.geodata.common.dto.layerTool;

import lombok.Data;

import java.util.List;

/**
 * 栅格叠加计算
 * @author zzw
 * @date 2026/3/17 11:26
 */
@Data
public class OverlayCalculationDTO {

    /**
     * 公式元素列表
     * type=layer时value为图层ID，type=symbol时value为操作符(+-*\/)
     */
    private List<FormulaItemDTO> formulaList;

}
