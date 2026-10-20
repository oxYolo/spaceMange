package com.whulri.geodata.common.dto.layerTool;

import lombok.Data;

import java.util.List;

/** 矢量交并补
 * @author zzw
 * @date 2026/3/17 11:26
 */
@Data
public class IntersectionAndUnionDto {

    /**
     * 两个待处理的图层ID
     */
    private List<String> layerIds;

    /**
     * 操作类型: intersection(交集) / union(并集) / symDifference(对称差集)
     */
    private String operationType;
}
