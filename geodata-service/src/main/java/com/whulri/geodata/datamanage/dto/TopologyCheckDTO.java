package com.whulri.geodata.datamanage.dto;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 拓扑检查DTO
 */
@Data
public class TopologyCheckDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 图层ID
     */
    @JSONField(name = "layerId")
    private String layerId;

    /**
     * 拓扑名称
     */
    private String topologyName;

    /**
     * 属性表名
     */
    @JSONField(name = "featureTable")
    private String featureTable;

    /**
     * 几何列（默认geom）
     */
    private String geometryColumn = "geom";

    /**
     * 拓扑几何列（默认topo_geo）
     */
    private String topoGeometryColumn = "topo_geo";

    /**
     * 检查规则列表
     */
    private List<TopologyRule> rules;

    /**
     * 操作人
     */
    private String createBy;

    /**
     * 操作人用户名
     */
    private String createByName;

    /**
     * SRID（默认4326）
     */
    private Integer srid = 4326;

    /**
     * 容差（默认0.00001，即1e-5度，约1.1米）
     */
    private Double tolerance = 0.00001;

    /**
     * 最小面积阈值（用于polygon.min_area规则）
     */
    private Double minArea;

    /**
     * 状态：0-进行中，1-成功，2-失败
     */
    private String status;

    /**
     * 页码（从1开始）
     */
    private Integer pageIndex;

    /**
     * 每页条数
     */
    private Integer pageSize;
}
