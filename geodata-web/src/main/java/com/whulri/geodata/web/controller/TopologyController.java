package com.whulri.geodata.web.controller;

import com.alibaba.fastjson.JSON;
import com.whulri.geodata.common.entity.LayerContentEntity;
import com.whulri.geodata.common.entity.ToolHisEntity;
import com.whulri.geodata.datamanage.dto.TopologyCheckDTO;
import com.whulri.geodata.datamanage.model.TopologyCheckReport;
import com.whulri.geodata.datamanage.model.TopologyIssue;
import com.whulri.geodata.datamanage.service.ILayerContentService;
import com.whulri.geodata.datamanage.service.ITopologyService;
import com.whulri.geodata.datamanage.service.IToolHisService;
import io.swagger.v3.oas.annotations.Operation;
import com.whulri.geodata.common.bridge.AuthUtil;
import com.whulri.geodata.common.result.Result;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 拓扑检查控制器
 */
@RestController
@RequestMapping("topology")
public class TopologyController {

    @Autowired
    private ITopologyService topologyService;

    @Autowired
    private IToolHisService toolHisService;

    @Autowired
    private ILayerContentService layerContentService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.topology.exchange:TopologyExchange}")
    private String topologyExchange;

    @Value("${rabbitmq.topology.routing:TopologyRouting}")
    private String topologyRoutingKey;

    @Operation(summary = "执行拓扑检查(异步)", description = "自动检查/创建拓扑，传入 layerId + 规则")
    @PostMapping("/check")
    public Result createTopologyCheck(@RequestBody TopologyCheckDTO dto) {
        try {
            // 1. 校验图层
            LayerContentEntity layer = layerContentService.getById(dto.getLayerId());
            if (layer == null) {
                return Result.fail("图层不存在");
            }

            // 2. 创建任务记录
            ToolHisEntity toolHis = new ToolHisEntity();
            toolHis.setLayerName("拓扑检查-" + layer.getLayerName());
            toolHis.setLayerId(dto.getLayerId());
            toolHis.setCreateBy(AuthUtil.getNickName());
            toolHis.setStatus("0");
            toolHis.setOrigin("10"); // 拓扑检查
            toolHis.setStorage("0");
            toolHis.setCreateDT(java.time.LocalDateTime.now());
            toolHisService.save(toolHis);

            // 3. 设置任务ID和属性表名到DTO
            dto.setTaskId(toolHis.getId());
            dto.setFeatureTable(layer.getLayerAttrTable());

            // 4. 如果没有指定拓扑名称，则使用属性表名作为拓扑名
            if (dto.getTopologyName() == null || dto.getTopologyName().isEmpty()) {
                dto.setTopologyName(layer.getLayerAttrTable());
            }

            // 5. 发送MQ消息
            rabbitTemplate.convertAndSend(topologyExchange, topologyRoutingKey, JSON.toJSONString(dto));

            // 6. 返回任务ID
            return Result.data(toolHis.getId());
        } catch (Exception e) {
            return Result.fail("创建拓扑检查任务失败: " + e.getMessage());
        }
    }

    @Operation(summary = "获取支持的规则列表", description = "返回所有支持的规则，可通过geometryType过滤")
    @GetMapping("/rules")
    public Result getSupportedRules(@RequestParam(required = false) String geometryType) {
        try {
            List<Map<String, String>> rules = topologyService.getSupportedRules(geometryType);
            return Result.data(rules);
        } catch (Exception e) {
            return Result.fail("获取规则列表失败: " + e.getMessage());
        }
    }

    @Operation(summary = "查询检查报告", description = "轮询备用")
    @GetMapping("/check/{taskId}")
    public Result getCheckReport(@PathVariable String taskId) {
        try {
            ToolHisEntity toolHis = toolHisService.getById(taskId);
            if (toolHis == null) {
                return Result.fail("任务不存在");
            }

            // 如果任务还在进行中
            if ("0".equals(toolHis.getStatus())) {
                Map<String, Object> result = new HashMap<>();
                result.put("status", "processing");
                result.put("taskId", taskId);
                result.put("message", "拓扑检查进行中...");
                return Result.data(result);
            }

            // 如果任务失败
            if ("2".equals(toolHis.getStatus())) {
                Map<String, Object> result = new HashMap<>();
                result.put("status", "failed");
                result.put("taskId", taskId);
                result.put("message", toolHis.getLayerAttrTable());
                return Result.data(result);
            }

            // 任务成功，解析报告
            String reportJson = toolHis.getLayerAttrTable();
            TopologyCheckReport report = JSON.parseObject(reportJson, TopologyCheckReport.class);
            return Result.data(report);
        } catch (Exception e) {
            return Result.fail("获取检查报告失败: " + e.getMessage());
        }
    }

    @Operation(summary = "删除拓扑", description = "按 layerId 删除")
    @DeleteMapping("/{layerId}")
    public Result deleteTopology(@PathVariable String layerId) {
        try {
            topologyService.dropTopologyByLayerId(layerId);
            return Result.success("拓扑删除成功");
        } catch (Exception e) {
            return Result.fail("拓扑删除失败: " + e.getMessage());
        }
    }

    @Operation(summary = "查询拓扑状态", description = "拓扑是否存在、是否已填充")
    @GetMapping("/status/{layerId}")
    public Result getTopologyStatus(@PathVariable String layerId) {
        try {
            Map<String, Object> status = topologyService.getTopologyStatus(layerId);
            return Result.data(status);
        } catch (Exception e) {
            return Result.fail("查询拓扑状态失败: " + e.getMessage());
        }
    }

    @Operation(summary = "创建拓扑", description = "手动创建拓扑")
    @PostMapping("/create")
    public Result createTopology(@RequestParam String topologyName,
                            @RequestParam(defaultValue = "4326") Integer srid,
                            @RequestParam(defaultValue = "0.00001") Double tolerance) {
        try {
            topologyService.createTopology(topologyName, srid, tolerance);
            return Result.success("拓扑创建成功");
        } catch (Exception e) {
            return Result.fail("拓扑创建失败: " + e.getMessage());
        }
    }

    @Operation(summary = "导入要素到拓扑", description = "")
    @PostMapping("/import")
    public Result importFeatures(@RequestParam String topologyName,
                            @RequestParam String featureTable) {
        try {
            topologyService.importFeaturesToTopology(topologyName, featureTable);
            return Result.success("要素导入成功");
        } catch (Exception e) {
            return Result.fail("要素导入失败: " + e.getMessage());
        }
    }

    @Operation(summary = "添加拓扑几何列", description = "")
    @PostMapping("/add-topo-column")
    public Result addTopoColumn(@RequestParam String topologyName,
                            @RequestParam(defaultValue = "public") String schemaName,
                            @RequestParam String tableName,
                            @RequestParam(defaultValue = "geom") String geometryColumn,
                            @RequestParam(defaultValue = "topo_geo") String columnName) {
        try {
            topologyService.addTopoGeometryColumn(topologyName, schemaName, tableName, geometryColumn, columnName);
            return Result.success("拓扑几何列添加成功");
        } catch (Exception e) {
            return Result.fail("拓扑几何列添加失败: " + e.getMessage());
        }
    }

    @Operation(summary = "填充拓扑几何数据", description = "")
    @PostMapping("/populate")
    public Result populateTopoData(@RequestParam String tableName,
                              @RequestParam(defaultValue = "geom") String geometryColumn,
                              @RequestParam(defaultValue = "topo_geo") String topoGeometryColumn,
                              @RequestParam String topologyName,
                              @RequestParam(required = false) String condition,
                              @RequestParam(defaultValue = "0.00001") Double tolerance) {
        try {
            topologyService.populateTopoGeometry(tableName, geometryColumn, topoGeometryColumn, topologyName, condition, tolerance);
            return Result.success("拓扑几何数据填充成功");
        } catch (Exception e) {
            return Result.fail("拓扑几何数据填充失败: " + e.getMessage());
        }
    }

    @Operation(summary = "执行拓扑检查(同步)", description = "")
    @PostMapping("/checkSync")
    public Result checkTopologySync(@RequestBody TopologyCheckDTO dto,
                               @RequestParam(required = false) Integer pageIndex,
                               @RequestParam(required = false) Integer pageSize) {
        try {
            // 校验图层
            LayerContentEntity layer = layerContentService.getById(dto.getLayerId());
            if (layer == null) {
                return Result.fail("图层不存在");
            }

            dto.setFeatureTable(layer.getLayerAttrTable());

            // 确保拓扑已创建
            if (dto.getTopologyName() == null || dto.getTopologyName().isEmpty()) {
                dto.setTopologyName(layer.getLayerAttrTable());
            }
            topologyService.ensureTopologyCreated(
                dto.getLayerId(),
                dto.getFeatureTable(),
                dto.getTopologyName(),
                dto.getSrid() != null ? dto.getSrid() : 4326,
                dto.getTolerance() != null ? dto.getTolerance() : 0.001
            );

            TopologyCheckReport report = topologyService.checkTopology(dto);

            // 分页处理（只在有分页参数时）
            if (pageIndex != null && pageSize != null) {
                List<TopologyIssue> allIssues = report.getIssues();
                int total = allIssues.size();
                int fromIndex = (pageIndex - 1) * pageSize;
                int toIndex = Math.min(fromIndex + pageSize, total);

                Map<String, Object> pageData = new HashMap<>();
                pageData.put("total", total);
                pageData.put("pageIndex", pageIndex);
                pageData.put("pageSize", pageSize);
                pageData.put("records", fromIndex < toIndex
                    ? allIssues.subList(fromIndex, toIndex)
                    : Collections.emptyList());

                return Result.data(pageData);
            }

            // 无分页参数，返回完整报告
            return Result.data(report);
        } catch (Exception e) {
            return Result.fail("拓扑检查失败: " + e.getMessage());
        }
    }
}
