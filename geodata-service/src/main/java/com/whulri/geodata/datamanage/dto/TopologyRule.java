package com.whulri.geodata.datamanage.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 拓扑规则
 */
@Data
public class TopologyRule implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 规则代码
     */
    private String ruleCode;

    /**
     * 规则名称
     */
    private String ruleName;

    /**
     * 目标图层ID（跨图层检查时使用）
     */
    private String targetLayerId;

    /**
     * 目标属性表（跨图层检查时使用）
     */
    private String targetFeatureTable;
}
