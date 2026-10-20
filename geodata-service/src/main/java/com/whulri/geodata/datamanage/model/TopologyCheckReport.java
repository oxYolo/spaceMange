package com.whulri.geodata.datamanage.model;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 拓扑检查报告
 */
@Data
public class TopologyCheckReport implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 拓扑名称
     */
    private String topologyName;

    /**
     * 图层ID
     */
    private String layerId;

    /**
     * 属性表名
     */
    private String featureTable;

    /**
     * 检查状态：PASS-通过，FAIL-失败
     */
    private String status;

    /**
     * 检查时间
     */
    private String checkTime;

    /**
     * 总问题数
     */
    private Integer totalIssues;

    /**
     * 检查的规则列表
     */
    private List<String> checkedRules = new ArrayList<>();

    /**
     * 具体问题列表
     */
    private List<TopologyIssue> issues = new ArrayList<>();

    /**
     * 是否通过
     */
    public boolean isPassed() {
        return "PASS".equals(status);
    }
}
