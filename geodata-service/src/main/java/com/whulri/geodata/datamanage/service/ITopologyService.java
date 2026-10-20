package com.whulri.geodata.datamanage.service;

import com.whulri.geodata.datamanage.dto.TopologyCheckDTO;
import com.whulri.geodata.datamanage.model.TopologyCheckReport;

import java.util.List;
import java.util.Map;

/**
 * 拓扑服务接口
 */
public interface ITopologyService {

    /**
     * 从表创建拓扑
     *
     * @param topologyName 拓扑名称
     * @param srid 空间参考ID
     * @param tolerance 容差
     */
    void createTopology(String topologyName, int srid, double tolerance);

    /**
     * 导入要素到拓扑
     *
     * @param topologyName 拓扑名称
     * @param featureTable 属性表名
     */
    void importFeaturesToTopology(String topologyName, String featureTable);

    /**
     * 添加拓扑几何列
     *
     * @param topologyName 拓扑名称
     * @param schemaName schema名称
     * @param tableName 表名
     * @param geometryColumn 几何列
     * @param columnName 拓扑几何列名
     */
    void addTopoGeometryColumn(String topologyName, String schemaName, String tableName,
                               String geometryColumn, String columnName);

    /**
     * 填充拓扑几何数据
     *
     * @param tableName 表名
     * @param geometryColumn 几何列
     * @param topoGeometryColumn 拓扑几何列
     * @param topologyName 拓扑名称
     * @param condition 条件（可选）
     * @param tolerance 容差
     */
    void populateTopoGeometry(String tableName, String geometryColumn,
                              String topoGeometryColumn, String topologyName, String condition, double tolerance);

    /**
     * 执行拓扑检查
     *
     * @param dto 拓扑检查参数
     * @return 检查报告
     */
    TopologyCheckReport checkTopology(TopologyCheckDTO dto);

    /**
     * 同步执行拓扑检查（MQ消费者调用）
     *
     * @param dto 拓扑检查参数
     * @return 检查报告
     */
    TopologyCheckReport checkTopologySync(TopologyCheckDTO dto);

    /**
     * 删除拓扑
     *
     * @param topologyName 拓扑名称
     */
    void dropTopology(String topologyName);

    /**
     * 获取拓扑是否存在
     *
     * @param topologyName 拓扑名称
     * @return 是否存在
     */
    boolean topologyExists(String topologyName);

    /**
     * 获取支持的拓扑规则列表
     *
     * @return 规则列表
     */
    List<Map<String, String>> getSupportedRules(String geometryType);

    /**
     * 检查拓扑一致性
     *
     * @param topologyName 拓扑名称
     * @return 问题列表
     */
    List<String> checkTopologyConsistency(String topologyName);

    /**
     * 获取拓扑状态
     *
     * @param layerId 图层ID
     * @return 拓扑状态信息
     */
    Map<String, Object> getTopologyStatus(String layerId);

    /**
     * 根据图层ID删除拓扑
     *
     * @param layerId 图层ID
     */
    void dropTopologyByLayerId(String layerId);

    /**
     * 确保拓扑已创建（如果不存在则创建）
     *
     * @param layerId 图层ID
     * @param featureTable 属性表名
     * @param topologyName 拓扑名称
     * @param srid 空间参考ID
     * @param tolerance 容差
     */
    void ensureTopologyCreated(String layerId, String featureTable, String topologyName, int srid, double tolerance);
}
