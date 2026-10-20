package com.whulri.geodata.datamanage.model;

import lombok.Data;

import java.io.Serializable;

/**
 * 拓扑问题
 */
@Data
public class TopologyIssue implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 问题要素的 gid
     */
    private Long gid;

    /**
     * 规则代码
     */
    private String ruleCode;

    /**
     * 规则名称
     */
    private String ruleName;

    /**
     * 问题描述
     */
    private String message;

    /**
     * 几何对象（WKT 格式）
     */
    private String geom;

    public TopologyIssue() {
    }

    public TopologyIssue(Long gid, String ruleCode, String ruleName, String message) {
        this.gid = gid;
        this.ruleCode = ruleCode;
        this.ruleName = ruleName;
        this.message = message;
    }

    public TopologyIssue(Long gid, String ruleCode, String ruleName, String message, String geom) {
        this.gid = gid;
        this.ruleCode = ruleCode;
        this.ruleName = ruleName;
        this.message = message;
        this.geom = geom;
    }
}
