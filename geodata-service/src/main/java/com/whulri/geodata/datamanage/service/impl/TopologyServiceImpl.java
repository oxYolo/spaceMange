package com.whulri.geodata.datamanage.service.impl;

import com.whulri.geodata.datamanage.dto.TopologyCheckDTO;
import com.whulri.geodata.datamanage.dto.TopologyRule;
import com.whulri.geodata.datamanage.dto.TopologyRuleEnum;
import com.whulri.geodata.datamanage.model.TopologyCheckReport;
import com.whulri.geodata.datamanage.model.TopologyIssue;
import com.whulri.geodata.datamanage.service.ITopologyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 拓扑服务实现
 */
@Slf4j
@Service
public class TopologyServiceImpl implements ITopologyService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String TOPOLOGY_SCHEMA = "topology";

    // ==================== 枚举常量引用（统一使用新命名）====================
    private static final TopologyRuleEnum POINT_OVERLAP = TopologyRuleEnum.POINT_OVERLAP;
    private static final TopologyRuleEnum GEOM_INVALID_POINT = TopologyRuleEnum.POINT_INVALID;
    private static final TopologyRuleEnum GEOM_EMPTY_POINT = TopologyRuleEnum.GEOM_EMPTY_POINT;
    private static final TopologyRuleEnum POINT_COINCIDENT = TopologyRuleEnum.POINT_COINCIDENT;

    private static final TopologyRuleEnum LINE_SELF_INTERSECT = TopologyRuleEnum.LINE_SELF_INTERSECT;
    private static final TopologyRuleEnum LINE_OVERLAP = TopologyRuleEnum.LINE_OVERLAP;
    private static final TopologyRuleEnum LINE_INTERSECT = TopologyRuleEnum.LINE_INTERSECT;
    private static final TopologyRuleEnum GEOM_INVALID_LINE = TopologyRuleEnum.LINE_INVALID;
    private static final TopologyRuleEnum GEOM_EMPTY_LINE = TopologyRuleEnum.GEOM_EMPTY_LINE;
    private static final TopologyRuleEnum LINE_DANGLE = TopologyRuleEnum.LINE_DANGLE;

    private static final TopologyRuleEnum POLYGON_OVERLAP = TopologyRuleEnum.POLYGON_OVERLAP;
    private static final TopologyRuleEnum POLYGON_GAP = TopologyRuleEnum.POLYGON_GAP;
    private static final TopologyRuleEnum POLYGON_SELF_INTERSECT = TopologyRuleEnum.POLYGON_SELF_INTERSECT;
    private static final TopologyRuleEnum POLYGON_MIN_AREA = TopologyRuleEnum.POLYGON_MIN_AREA;
    private static final TopologyRuleEnum GEOM_INVALID_POLYGON = TopologyRuleEnum.POLYGON_INVALID;
    private static final TopologyRuleEnum GEOM_EMPTY_POLYGON = TopologyRuleEnum.GEOM_EMPTY_POLYGON;

    private static final TopologyRuleEnum MUST_NOT_OVERLAP = TopologyRuleEnum.MUST_NOT_OVERLAP;
    private static final TopologyRuleEnum MUST_BE_INSIDE = TopologyRuleEnum.MUST_BE_INSIDE;

    @Override
    public void createTopology(String topologyName, int srid, double tolerance) {
        if (topologyExists(topologyName)) {
            log.info("拓扑 {} 已存在，跳过创建", topologyName);
            return;
        }

        String createTopologySql = String.format(
            "SELECT topology.CreateTopology('%s', %d, %f)",
            topologyName, srid, tolerance
        );
        jdbcTemplate.execute(createTopologySql);
        log.info("拓扑 {} 创建成功", topologyName);
    }

    @Override
    public void importFeaturesToTopology(String topologyName, String featureTable) {
        if (!topologyExists(topologyName)) {
            throw new RuntimeException("拓扑不存在，请先创建拓扑");
        }

        // 逐行导入要素，避免批量导入时重叠/相交导致的 "geometry crosses a node" 错误
        String gidsSql = String.format("SELECT gid FROM %s WHERE geom IS NOT NULL AND ST_IsValid(geom) ORDER BY gid", featureTable);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(gidsSql);

        int success = 0;
        int skipped = 0;
        for (Map<String, Object> row : rows) {
            Long gid = ((Number) row.get("gid")).longValue();
            try {
                // 先尝试以整体几何导入
                String sql = String.format(
                    "SELECT topology.TopoGeo_AddPolygon('%s', ST_Force2D(geom), 0.001) FROM %s WHERE gid = %d",
                    topologyName, featureTable, gid
                );
                jdbcTemplate.execute(sql);
                success++;
            } catch (Exception e) {
                // 整体导入失败，尝试拆分 MultiPolygon 逐个导入
                try {
                    String dumpSql = String.format(
                        "SELECT topology.TopoGeo_AddPolygon('%s', ST_Force2D((ST_Dump(geom)).geom), 0.001) FROM %s WHERE gid = %d",
                        topologyName, featureTable, gid
                    );
                    jdbcTemplate.execute(dumpSql);
                    success++;
                } catch (Exception e2) {
                    log.warn("要素 gid={} 导入拓扑失败，跳过: {}", gid, e2.getMessage());
                    skipped++;
                }
            }
        }
        log.info("拓扑导入完成: 成功 {}, 跳过 {}", success, skipped);
    }

    @Override
    public void addTopoGeometryColumn(String topologyName, String schemaName, String tableName,
                                       String geometryColumn, String columnName) {
        if (!topologyExists(topologyName)) {
            throw new RuntimeException("拓扑 " + topologyName + " 不存在，请先创建拓扑");
        }

        String checkTableSql = String.format(
            "SELECT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = '%s' AND table_name = '%s')",
            schemaName, tableName
        );
        Boolean tableExists = jdbcTemplate.queryForObject(checkTableSql, Boolean.class);
        if (!tableExists) {
            throw new RuntimeException("表 " + schemaName + "." + tableName + " 不存在");
        }

        String checkTopoColumnSql = String.format(
            "SELECT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = '%s' AND table_name = '%s' AND column_name = '%s')",
            schemaName, tableName, columnName
        );
        Boolean topoColumnExists = jdbcTemplate.queryForObject(checkTopoColumnSql, Boolean.class);
        if (topoColumnExists) {
            throw new RuntimeException("拓扑几何列 " + columnName + " 已存在于表 " + schemaName + "." + tableName);
        }

        String featureType = inferFeatureType(tableName, geometryColumn);
        String addColumnSql = String.format(
            "SELECT topology.AddTopoGeometryColumn('%s', '%s', '%s', '%s', '%s')",
            topologyName, schemaName, tableName, columnName, featureType
        );
        log.info("执行SQL: {}", addColumnSql);

        jdbcTemplate.execute(addColumnSql);
        log.info("已为表 {} 添加拓扑几何列 {}", tableName, columnName);
    }

    @Override
    public void populateTopoGeometry(String tableName, String geometryColumn,
                                      String topoGeometryColumn, String topologyName, String condition, double tolerance) {
        Integer layerId = getLayerId(topologyName, "public", tableName, topoGeometryColumn);
        String whereClause = condition != null && !condition.isEmpty() ? " AND " + condition : "";

        // 1. 尝试快速批量导入
        String bulkSql = String.format(
            "UPDATE %s SET %s = topology.toTopoGeom(ST_Force2D(%s), '%s', %d, %s) WHERE ST_IsValid(%s)%s",
            tableName, topoGeometryColumn, geometryColumn, topologyName, layerId, String.valueOf(tolerance), geometryColumn, whereClause
        );

        try {
            jdbcTemplate.execute(bulkSql);
            log.info("批量导入拓扑几何成功，表：{}，条件：{}，容差：{}", tableName, condition != null ? condition : "无", tolerance);
            return;
        } catch (Exception bulkEx) {
            // 2. 批量失败，尝试逐条导入
            log.warn("批量导入失败，切换到逐条导入模式，原因：{}", bulkEx.getMessage());
            importOneByOne(tableName, geometryColumn, topoGeometryColumn, topologyName, layerId, whereClause, tolerance);
        }
    }

    private void importOneByOne(String tableName, String geometryColumn, String topoGeometryColumn,
                                 String topologyName, Integer layerId, String whereClause, double tolerance) {
        // 查询所有有效几何
        String querySql = String.format(
            "SELECT gid FROM %s WHERE ST_IsValid(%s)%s",
            tableName, geometryColumn, whereClause
        );

        List<Long> gids = jdbcTemplate.queryForList(querySql, Long.class);
        List<Long> failedGids = new ArrayList<>();
        int successCount = 0;

        for (Long gid : gids) {
            String singleSql = String.format(
                "UPDATE %s SET %s = topology.toTopoGeom(ST_Force2D(%s), '%s', %d, %s) WHERE gid = %d",
                tableName, topoGeometryColumn, geometryColumn, topologyName, layerId, String.valueOf(tolerance), gid
            );
            try {
                jdbcTemplate.execute(singleSql);
                successCount++;
            } catch (Exception e) {
                failedGids.add(gid);
                log.debug("gid={} 导入失败，跳过：{}", gid, e.getMessage());
            }
        }

        log.info("逐条导入完成：成功={}，跳过={}，失败gid={}",
                 successCount, failedGids.size(), failedGids);
    }

    @Override
    public TopologyCheckReport checkTopology(TopologyCheckDTO dto) {
        return checkTopologySync(dto);
    }

    @Override
    public TopologyCheckReport checkTopologySync(TopologyCheckDTO dto) {
        TopologyCheckReport report = new TopologyCheckReport();
        report.setTaskId(dto.getTaskId());
        report.setTopologyName(dto.getTopologyName());
        report.setLayerId(dto.getLayerId());
        report.setFeatureTable(dto.getFeatureTable());
        report.setCheckTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        List<TopologyIssue> allIssues = new ArrayList<>();
        List<String> checkedRules = new ArrayList<>();

        List<TopologyRule> rules = dto.getRules();
        if (rules == null || rules.isEmpty()) {
            rules = getDefaultRules();
        }

        // 检查拓扑一致性（已注释，拓扑元数据损坏不影响要素规则检查）
        // List<String> consistencyIssues = checkTopologyConsistency(dto.getTopologyName());
        // for (String issue : consistencyIssues) {
        //     allIssues.add(new TopologyIssue(null, "TOPOLOGY_CONSISTENCY", "拓扑一致性", issue));
        //     checkedRules.add("TOPOLOGY_CONSISTENCY");
        // }

        // 检查要素拓扑规则
        for (TopologyRule rule : rules) {
            String ruleCode = rule.getRuleCode();
            if (TopologyRuleEnum.getByCode(ruleCode) == null) {
                continue;
            }

            List<TopologyIssue> ruleIssues = checkSingleRule(dto, rule);
            if (!ruleIssues.isEmpty()) {
                allIssues.addAll(ruleIssues);
                checkedRules.add(rule.getRuleName());
            }
        }

        report.setIssues(allIssues);
        report.setCheckedRules(checkedRules);
        report.setStatus(allIssues.isEmpty() ? "PASS" : "FAIL");
        report.setTotalIssues(allIssues.size());

        return report;
    }

    private List<TopologyRule> getDefaultRules() {
        List<TopologyRule> rules = new ArrayList<>();
        for (TopologyRuleEnum ruleEnum : TopologyRuleEnum.values()) {
            TopologyRule rule = new TopologyRule();
            rule.setRuleCode(ruleEnum.getCode());
            rule.setRuleName(ruleEnum.getName());
            rules.add(rule);
        }
        return rules;
    }

    private List<TopologyIssue> checkSingleRule(TopologyCheckDTO dto, TopologyRule rule) {
        String featureTable = dto.getFeatureTable();
        String topologyName = dto.getTopologyName();
        String ruleCode = rule.getRuleCode();

        // 拓扑就绪时，优先使用拓扑方法
        boolean topoReady = topologyName != null && !topologyName.isEmpty() && topologyExists(topologyName);

        if (topoReady) {
            List<TopologyIssue> issues = checkTopologyBased(topologyName, featureTable, ruleCode, dto.getMinArea());
            // 返回非 null 表示拓扑已处理此规则
            if (issues != null) {
                return issues;
            }
            // 返回 null 表示此规则没有拓扑实现；返回空列表表示拓扑未检出问题，回退 SQL 兜底
        }

        // 拓扑不支持此规则，走空间 SQL
        List<TopologyIssue> issues = new ArrayList<>();
        if (TopologyRuleEnum.isPointRule(ruleCode)) {
            issues.addAll(checkPointTopology(featureTable, ruleCode));
        } else if (TopologyRuleEnum.isLineRule(ruleCode)) {
            issues.addAll(checkLineTopology(featureTable, ruleCode));
        } else if (TopologyRuleEnum.isPolygonRule(ruleCode)) {
            issues.addAll(checkPolygonTopology(featureTable, ruleCode, dto.getMinArea()));
        } else if (TopologyRuleEnum.isCrossLayerRule(ruleCode)) {
            issues.addAll(checkCrossLayerTopology(featureTable, rule));
        }

        return issues;
    }

    /**
     * 基于拓扑表的规则检查（利用 node/edge/face/relation 表）
     * 仅对支持拓扑表查询的规则有效，其余返回空列表
     */
    /**
     * 基于拓扑表的规则检查
     * 返回 null 表示此规则没有拓扑实现，调用方应走 SQL
     * 返回 List（可能为空）表示拓扑已处理此规则
     */
    private List<TopologyIssue> checkTopologyBased(String topologyName, String featureTable,
                                                    String ruleCode, Double minArea) {
        // 检查拓扑表是否有数据
        try {
            Integer relationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + topologyName + ".relation", Integer.class);
            if (relationCount == null || relationCount == 0) {
                return null; // 拓扑表无数据，走 SQL
            }
        } catch (Exception e) {
            return null; // 拓扑表不存在或查询失败，走 SQL
        }

        List<TopologyIssue> issues = new ArrayList<>();

        switch (ruleCode) {
            case "point.overlap":
                issues.addAll(checkPointOverlapTopology(topologyName, featureTable));
                break;
            case "line.overlap":
                issues.addAll(checkLineOverlapTopology(topologyName, featureTable));
                break;
            case "polygon.overlap":
                issues.addAll(checkPolygonOverlapTopology(topologyName, featureTable));
                break;
            case "polygon.gap":
                issues.addAll(checkPolygonGapTopology(topologyName, featureTable));
                break;
            case "line.self_intersect":
                issues.addAll(checkLineSelfIntersectTopology(topologyName, featureTable));
                break;
            case "line.intersect":
                issues.addAll(checkLineIntersectTopology(topologyName, featureTable));
                break;
            case "line.dangle":
                issues.addAll(checkLineDangleTopology(topologyName, featureTable));
                break;
            case "polygon.self_intersect":
                issues.addAll(checkPolygonSelfIntersectTopology(topologyName, featureTable));
                break;
            default:
                return null; // 此规则没有拓扑实现，走 SQL
        }

        return issues;
    }

    // ==================== 点要素检查 ====================

    private List<TopologyIssue> checkPointTopology(String featureTable, String ruleCode) {
        List<TopologyIssue> issues = new ArrayList<>();

        switch (ruleCode) {
            case "point.overlap":
                issues.addAll(checkPointOverlap(featureTable));
                break;
            case "point.invalid":
                issues.addAll(checkPointInvalid(featureTable));
                break;
            case "point.empty":
                issues.addAll(checkEmptyGeometry(featureTable, "POINT"));
                break;
            case "point.coincident":
                issues.addAll(checkPointCoincident(featureTable, 0.00001));
                break;
        }

        return issues;
    }

    /**
     * 点重叠检查 - 精确重合（空间 SQL 兜底）
     * 返回重合点（已经是精确的）
     */
    private List<TopologyIssue> checkPointOverlap(String featureTable) {
        List<TopologyIssue> issues = new ArrayList<>();

        String sql = String.format(
            "SELECT a.gid as gid1, b.gid as gid2, ST_AsText(a.geom) as geom FROM %s a, %s b " +
            "WHERE a.gid < b.gid " +
            "AND ST_GeometryType(a.geom) IN ('ST_Point', 'ST_MultiPoint') " +
            "AND ST_GeometryType(b.geom) IN ('ST_Point', 'ST_MultiPoint') " +
            "AND ST_IsValid(a.geom) AND ST_IsValid(b.geom) " +
            "AND ST_Equals(a.geom, b.geom)",
            featureTable, featureTable
        );

        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> row : result) {
                Long gid1 = ((Number) row.get("gid1")).longValue();
                Long gid2 = ((Number) row.get("gid2")).longValue();
                String geom = row.get("geom") != null ? row.get("geom").toString() : null;
                issues.add(new TopologyIssue(gid1, POINT_OVERLAP.getCode(), POINT_OVERLAP.getName(),
                    "点要素ID " + gid1 + " 与 ID " + gid2 + " 重合", geom));
                issues.add(new TopologyIssue(gid2, POINT_OVERLAP.getCode(), POINT_OVERLAP.getName(),
                    "点要素ID " + gid2 + " 与 ID " + gid1 + " 重合", geom));
            }
        } catch (Exception e) {
            log.warn("点重叠检查失败: {}", e.getMessage());
        }

        return issues;
    }

    /**
     * 点重叠检查 - 使用拓扑表 node + relation
     * 被多个要素共享的 node 即为重合点
     */
    private List<TopologyIssue> checkPointOverlapTopology(String topologyName, String featureTable) {
        List<TopologyIssue> issues = new ArrayList<>();

        String sql = String.format(
            "WITH shared_nodes AS ( " +
            "  SELECT element_id " +
            "  FROM %1$s.relation " +
            "  WHERE element_type = 1 " +
            "  GROUP BY element_id " +
            "  HAVING COUNT(DISTINCT topogeo_id) > 1 " +
            ") " +
            "SELECT f1.gid as gid1, f2.gid as gid2, " +
            "  ST_AsText(n.geom) as error_geom " +
            "FROM shared_nodes sn " +
            "JOIN %1$s.node n ON n.node_id = sn.element_id " +
            "JOIN %1$s.relation r1 ON r1.element_id = sn.element_id AND r1.element_type = 1 " +
            "JOIN %1$s.relation r2 ON r2.element_id = sn.element_id AND r2.element_type = 1 " +
            "  AND r2.topogeo_id > r1.topogeo_id " +
            "JOIN %2$s f1 ON (f1.topo_geo).id = r1.topogeo_id " +
            "JOIN %2$s f2 ON (f2.topo_geo).id = r2.topogeo_id",
            topologyName, featureTable
        );

        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> row : result) {
                Long gid1 = ((Number) row.get("gid1")).longValue();
                Long gid2 = ((Number) row.get("gid2")).longValue();
                String errorGeom = row.get("error_geom") != null ? row.get("error_geom").toString() : null;
                issues.add(new TopologyIssue(gid1, POINT_OVERLAP.getCode(), POINT_OVERLAP.getName(),
                    "点要素ID " + gid1 + " 与 ID " + gid2 + " 重合", errorGeom));
                issues.add(new TopologyIssue(gid2, POINT_OVERLAP.getCode(), POINT_OVERLAP.getName(),
                    "点要素ID " + gid2 + " 与 ID " + gid1 + " 重合", errorGeom));
            }
        } catch (Exception e) {
            log.warn("拓扑表点重叠检查失败: {}", e.getMessage());
        }

        return issues;
    }

    /**
     * 点无效检查
     */
    private List<TopologyIssue> checkPointInvalid(String featureTable) {
        List<TopologyIssue> issues = new ArrayList<>();

        String sql = String.format(
            "SELECT gid, ST_AsText(geom) as geom FROM %s " +
            "WHERE (geom IS NULL OR NOT ST_IsValid(geom)) " +
            "AND (ST_GeometryType(geom) IN ('ST_Point', 'ST_MultiPoint') OR geom IS NULL)",
            featureTable
        );

        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> row : result) {
                Long gid = ((Number) row.get("gid")).longValue();
                String geom = row.get("geom") != null ? row.get("geom").toString() : null;
                issues.add(new TopologyIssue(gid, GEOM_INVALID_POINT.getCode(), GEOM_INVALID_POINT.getName(),
                    "点要素ID " + gid + " 几何对象无效", geom));
            }
        } catch (Exception e) {
            log.warn("点无效检查失败: {}", e.getMessage());
        }

        return issues;
    }

    /**
     * 点过近检查 - 使用容差判断
     */
    private List<TopologyIssue> checkPointCoincident(String featureTable, Double tolerance) {
        List<TopologyIssue> issues = new ArrayList<>();

        String sql = String.format(
            "SELECT a.gid as gid1, b.gid as gid2, ST_Distance(a.geom, b.geom) as dist, " +
            "ST_AsText(a.geom) as geom1, ST_AsText(b.geom) as geom2 FROM %s a, %s b " +
            "WHERE a.gid < b.gid " +
            "AND ST_GeometryType(a.geom) IN ('ST_Point', 'ST_MultiPoint') " +
            "AND ST_GeometryType(b.geom) IN ('ST_Point', 'ST_MultiPoint') " +
            "AND ST_IsValid(a.geom) AND ST_IsValid(b.geom) " +
            "AND ST_DWithin(a.geom, b.geom, %.9f) " +
            "AND NOT ST_Equals(a.geom, b.geom)",
            featureTable, featureTable, tolerance
        );

        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> row : result) {
                Long gid1 = ((Number) row.get("gid1")).longValue();
                Long gid2 = ((Number) row.get("gid2")).longValue();
                Double dist = ((Number) row.get("dist")).doubleValue();
                String geom1 = row.get("geom1") != null ? row.get("geom1").toString() : null;
                String geom2 = row.get("geom2") != null ? row.get("geom2").toString() : null;
                issues.add(new TopologyIssue(gid1, POINT_COINCIDENT.getCode(), POINT_COINCIDENT.getName(),
                    "点要素ID " + gid1 + " 与 ID " + gid2 + " 过近（距离：" + String.format("%.8f", dist) + "）", geom1));
                issues.add(new TopologyIssue(gid2, POINT_COINCIDENT.getCode(), POINT_COINCIDENT.getName(),
                    "点要素ID " + gid2 + " 与 ID " + gid1 + " 过近（距离：" + String.format("%.8f", dist) + "）", geom2));
            }
        } catch (Exception e) {
            log.warn("点过近检查失败: {}", e.getMessage());
        }

        return issues;
    }

    // ==================== 线要素检查 ====================

    private List<TopologyIssue> checkLineTopology(String featureTable, String ruleCode) {
        List<TopologyIssue> issues = new ArrayList<>();

        switch (ruleCode) {
            case "line.self_intersect":
                issues.addAll(checkLineSelfIntersect(featureTable));
                break;
            case "line.overlap":
                issues.addAll(checkLineOverlap(featureTable));
                break;
            case "line.intersect":
                issues.addAll(checkLineIntersect(featureTable));
                break;
            case "line.invalid":
                issues.addAll(checkLineInvalid(featureTable));
                break;
            case "line.empty":
                issues.addAll(checkEmptyGeometry(featureTable, "LINE"));
                break;
            case "line.dangle":
                issues.addAll(checkLineDangle(featureTable));
                break;
        }

        return issues;
    }

    /**
     * 线自相交检查
     * ST_Node 在自相交点处打断线段，出现多次的端点即为自相交点
     */
    private List<TopologyIssue> checkLineSelfIntersect(String featureTable) {
        List<TopologyIssue> issues = new ArrayList<>();

        String sql = String.format(
            "WITH self_intersect AS (" +
            "  SELECT gid, geom FROM %s" +
            "  WHERE ST_IsValid(geom)" +
            "  AND ST_GeometryType(geom) IN ('ST_LineString','ST_MultiLineString')" +
            "  AND NOT ST_IsSimple(geom)" +
            "), noded AS (" +
            "  SELECT gid, (ST_Dump(ST_Node(geom))).geom AS seg FROM self_intersect" +
            "), endpoints AS (" +
            "  SELECT gid, ST_StartPoint(seg) AS pt FROM noded" +
            "  UNION ALL" +
            "  SELECT gid, ST_EndPoint(seg) AS pt FROM noded" +
            "), dup_pts AS (" +
            "  SELECT gid, pt FROM endpoints GROUP BY gid, pt HAVING COUNT(*) > 1" +
            ") " +
            "SELECT gid, ST_AsText(ST_Force2D(ST_Collect(pt))) AS error_geom FROM dup_pts GROUP BY gid",
            featureTable
        );

        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> row : result) {
                Long gid = ((Number) row.get("gid")).longValue();
                String errorGeom = row.get("error_geom") != null ? row.get("error_geom").toString() : "";
                issues.add(new TopologyIssue(gid, LINE_SELF_INTERSECT.getCode(), LINE_SELF_INTERSECT.getName(),
                    "线要素ID " + gid + " 存在自相交", errorGeom));
            }
        } catch (Exception e) {
            log.warn("线自相交检查失败: {}", e.getMessage());
        }

        return issues;
    }

    /**
     * 线段重叠检查 - 完全重叠的线段（空间 SQL 兜底）
     * 返回重叠部分几何 ST_Intersection
     */
    private List<TopologyIssue> checkLineOverlap(String featureTable) {
        List<TopologyIssue> issues = new ArrayList<>();

        String sql = String.format(
            "SELECT a.gid as gid1, b.gid as gid2, " +
            "  ST_AsText(ST_Intersection(a.geom, b.geom)) as error_geom " +
            "FROM %s a " +
            "JOIN %s b ON a.gid < b.gid AND a.geom && b.geom " +
            "WHERE ST_GeometryType(a.geom) IN ('ST_LineString', 'ST_MultiLineString') " +
            "AND ST_GeometryType(b.geom) IN ('ST_LineString', 'ST_MultiLineString') " +
            "AND ST_IsValid(a.geom) AND ST_IsValid(b.geom) " +
            "AND ST_Equals(a.geom, b.geom)",
            featureTable, featureTable
        );

        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> row : result) {
                Long gid1 = ((Number) row.get("gid1")).longValue();
                Long gid2 = ((Number) row.get("gid2")).longValue();
                String errorGeom = row.get("error_geom") != null ? row.get("error_geom").toString() : null;
                issues.add(new TopologyIssue(gid1, LINE_OVERLAP.getCode(), LINE_OVERLAP.getName(),
                    "线要素ID " + gid1 + " 与 ID " + gid2 + " 线段重叠", errorGeom));
                issues.add(new TopologyIssue(gid2, LINE_OVERLAP.getCode(), LINE_OVERLAP.getName(),
                    "线要素ID " + gid2 + " 与 ID " + gid1 + " 线段重叠", errorGeom));
            }
        } catch (Exception e) {
            log.warn("线段重叠检查失败: {}", e.getMessage());
        }

        return issues;
    }

    /**
     * 线段重叠检查 - 使用拓扑表 edge + relation
     * 被多个要素共享的 edge 即为重叠线段
     */
    private List<TopologyIssue> checkLineOverlapTopology(String topologyName, String featureTable) {
        List<TopologyIssue> issues = new ArrayList<>();

        String sql = String.format(
            "WITH shared_edges AS ( " +
            "  SELECT element_id " +
            "  FROM %1$s.relation " +
            "  WHERE element_type = 2 " +
            "  GROUP BY element_id " +
            "  HAVING COUNT(DISTINCT topogeo_id) > 1 " +
            ") " +
            "SELECT f1.gid as gid1, f2.gid as gid2, " +
            "  ST_AsText(e.geom) as error_geom " +
            "FROM shared_edges se " +
            "JOIN %1$s.edge e ON e.edge_id = se.element_id " +
            "JOIN %1$s.relation r1 ON r1.element_id = se.element_id AND r1.element_type = 2 " +
            "JOIN %1$s.relation r2 ON r2.element_id = se.element_id AND r2.element_type = 2 " +
            "  AND r2.topogeo_id > r1.topogeo_id " +
            "JOIN %2$s f1 ON (f1.topo_geo).id = r1.topogeo_id " +
            "JOIN %2$s f2 ON (f2.topo_geo).id = r2.topogeo_id",
            topologyName, featureTable
        );

        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> row : result) {
                Long gid1 = ((Number) row.get("gid1")).longValue();
                Long gid2 = ((Number) row.get("gid2")).longValue();
                String errorGeom = row.get("error_geom") != null ? row.get("error_geom").toString() : null;
                issues.add(new TopologyIssue(gid1, LINE_OVERLAP.getCode(), LINE_OVERLAP.getName(),
                    "线要素ID " + gid1 + " 与 ID " + gid2 + " 线段重叠", errorGeom));
                issues.add(new TopologyIssue(gid2, LINE_OVERLAP.getCode(), LINE_OVERLAP.getName(),
                    "线要素ID " + gid2 + " 与 ID " + gid1 + " 线段重叠", errorGeom));
            }
        } catch (Exception e) {
            log.warn("拓扑表线段重叠检查失败: {}", e.getMessage());
        }

        return issues;
    }

    /**
     * 线自相交检查（拓扑方法）
     * 拓扑中线被 noded 打断成 edge，正常端点出现 1 次，中间点 2 次，自相交点 4+ 次
     */
    private List<TopologyIssue> checkLineSelfIntersectTopology(String topologyName, String featureTable) {
        List<TopologyIssue> issues = new ArrayList<>();

        String sql = String.format(
            "WITH line_edges AS (" +
            "  SELECT r.topogeo_id, e.start_node, e.end_node" +
            "  FROM %1$s.relation r JOIN %1$s.edge e ON e.edge_id = r.element_id" +
            "  WHERE r.element_type = 2" +
            "), node_visits AS (" +
            "  SELECT topogeo_id, start_node AS node_id FROM line_edges" +
            "  UNION ALL" +
            "  SELECT topogeo_id, end_node AS node_id FROM line_edges" +
            "), hot_nodes AS (" +
            "  SELECT topogeo_id, node_id FROM node_visits" +
            "  GROUP BY topogeo_id, node_id HAVING COUNT(*) > 2" +
            ") " +
            "SELECT f.gid AS gid, ST_AsText(ST_Force2D(n.geom)) AS error_geom " +
            "FROM hot_nodes hn " +
            "JOIN %1$s.node n ON n.node_id = hn.node_id " +
            "JOIN %2$s f ON (f.topo_geo).id = hn.topogeo_id",
            topologyName, featureTable
        );

        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> row : result) {
                Long gid = ((Number) row.get("gid")).longValue();
                String errorGeom = row.get("error_geom") != null ? row.get("error_geom").toString() : "";
                issues.add(new TopologyIssue(gid, LINE_SELF_INTERSECT.getCode(), LINE_SELF_INTERSECT.getName(),
                    "线要素ID " + gid + " 存在自相交", errorGeom));
            }
        } catch (Exception e) {
            log.warn("拓扑线自相交检查失败: {}", e.getMessage());
        }

        return issues;
    }

    /**
     * 线线相交检查（拓扑方法）
     * 找到被不同 topogeo_id 共享的节点，排除双方都是端点的情况（端点对端点是 Touches 不是 Intersects）
     */
    private List<TopologyIssue> checkLineIntersectTopology(String topologyName, String featureTable) {
        List<TopologyIssue> issues = new ArrayList<>();

        String sql = String.format(
            "WITH line_edges AS (" +
            "  SELECT r.topogeo_id, e.start_node, e.end_node" +
            "  FROM %1$s.relation r JOIN %1$s.edge e ON e.edge_id = r.element_id" +
            "  WHERE r.element_type = 2" +
            "), node_visits AS (" +
            "  SELECT topogeo_id, start_node AS node_id FROM line_edges" +
            "  UNION ALL" +
            "  SELECT topogeo_id, end_node AS node_id FROM line_edges" +
            "), endpoints AS (" +
            "  SELECT topogeo_id, node_id FROM node_visits" +
            "  GROUP BY topogeo_id, node_id HAVING COUNT(*) = 1" +
            "), cross_nodes AS (" +
            "  SELECT DISTINCT nv1.topogeo_id AS tg1, nv2.topogeo_id AS tg2, nv1.node_id" +
            "  FROM node_visits nv1 JOIN node_visits nv2" +
            "  ON nv1.node_id = nv2.node_id AND nv1.topogeo_id < nv2.topogeo_id" +
            ") " +
            "SELECT f1.gid as gid1, f2.gid as gid2, ST_AsText(ST_Force2D(n.geom)) AS error_geom " +
            "FROM cross_nodes cn " +
            "JOIN %1$s.node n ON n.node_id = cn.node_id " +
            "JOIN %2$s f1 ON (f1.topo_geo).id = cn.tg1 " +
            "JOIN %2$s f2 ON (f2.topo_geo).id = cn.tg2 " +
            "WHERE NOT (" +
            "  EXISTS (SELECT 1 FROM endpoints ep WHERE ep.topogeo_id = cn.tg1 AND ep.node_id = cn.node_id)" +
            "  AND" +
            "  EXISTS (SELECT 1 FROM endpoints ep WHERE ep.topogeo_id = cn.tg2 AND ep.node_id = cn.node_id)" +
            ")",
            topologyName, featureTable
        );

        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> row : result) {
                Long gid1 = ((Number) row.get("gid1")).longValue();
                Long gid2 = ((Number) row.get("gid2")).longValue();
                String errorGeom = row.get("error_geom") != null ? row.get("error_geom").toString() : "";
                issues.add(new TopologyIssue(gid1, LINE_INTERSECT.getCode(), LINE_INTERSECT.getName(),
                    "线要素ID " + gid1 + " 与 ID " + gid2 + " 非端点相交", errorGeom));
                issues.add(new TopologyIssue(gid2, LINE_INTERSECT.getCode(), LINE_INTERSECT.getName(),
                    "线要素ID " + gid2 + " 与 ID " + gid1 + " 非端点相交", errorGeom));
            }
        } catch (Exception e) {
            log.warn("拓扑线线相交检查失败: {}", e.getMessage());
        }

        return issues;
    }

    /**
     * 线悬挂检查（拓扑方法）
     * 悬挂端点 = 在 edge 表中度数为 1 的 node（只有一条边连接）
     */
    private List<TopologyIssue> checkLineDangleTopology(String topologyName, String featureTable) {
        List<TopologyIssue> issues = new ArrayList<>();

        String sql = String.format(
            "WITH node_degree AS (" +
            "  SELECT node_id FROM (" +
            "    SELECT start_node AS node_id FROM %1$s.edge" +
            "    UNION ALL" +
            "    SELECT end_node AS node_id FROM %1$s.edge" +
            "  ) nd GROUP BY node_id HAVING COUNT(*) = 1" +
            "), dangling AS (" +
            "  SELECT DISTINCT r.topogeo_id AS tg_id," +
            "    CASE WHEN dn.node_id = e.start_node THEN e.start_node ELSE e.end_node END AS dangle_node" +
            "  FROM node_degree dn" +
            "  JOIN %1$s.edge e ON dn.node_id = e.start_node OR dn.node_id = e.end_node" +
            "  JOIN %1$s.relation r ON r.element_id = e.edge_id AND r.element_type = 2" +
            ") " +
            "SELECT f.gid, ST_AsText(ST_Force2D(n.geom)) AS error_geom " +
            "FROM dangling d " +
            "JOIN %1$s.node n ON n.node_id = d.dangle_node " +
            "JOIN %2$s f ON (f.topo_geo).id = d.tg_id",
            topologyName, featureTable
        );

        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> row : result) {
                Long gid = ((Number) row.get("gid")).longValue();
                String errorGeom = row.get("error_geom") != null ? row.get("error_geom").toString() : "";
                issues.add(new TopologyIssue(gid, LINE_DANGLE.getCode(), LINE_DANGLE.getName(),
                    "线要素ID " + gid + " 存在悬挂点", errorGeom));
            }
        } catch (Exception e) {
            log.warn("拓扑线悬挂检查失败: {}", e.getMessage());
        }

        return issues;
    }

    /**
     * 线线相交检查 - 排除端点相交，只检测非端点相交
     * 返回交点几何 ST_Intersection
     */
    private List<TopologyIssue> checkLineIntersect(String featureTable) {
        List<TopologyIssue> issues = new ArrayList<>();

        String sql = String.format(
            "SELECT a.gid as gid1, b.gid as gid2, " +
            "  ST_AsText(ST_Intersection(a.geom, b.geom)) as error_geom " +
            "FROM %s a, %s b " +
            "WHERE a.gid < b.gid " +
            "AND (ST_GeometryType(a.geom) = 'ST_LineString' OR ST_GeometryType(a.geom) = 'ST_MultiLineString') " +
            "AND (ST_GeometryType(b.geom) = 'ST_LineString' OR ST_GeometryType(b.geom) = 'ST_MultiLineString') " +
            "AND ST_IsValid(a.geom) AND ST_IsValid(b.geom) " +
            "AND ST_Intersects(a.geom, b.geom) " +
            "AND NOT ST_Touches(a.geom, b.geom)",
            featureTable, featureTable
        );

        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> row : result) {
                Long gid1 = ((Number) row.get("gid1")).longValue();
                Long gid2 = ((Number) row.get("gid2")).longValue();
                String errorGeom = row.get("error_geom") != null ? row.get("error_geom").toString() : null;
                issues.add(new TopologyIssue(gid1, LINE_INTERSECT.getCode(), LINE_INTERSECT.getName(),
                    "线要素ID " + gid1 + " 与 ID " + gid2 + " 非端点相交", errorGeom));
                issues.add(new TopologyIssue(gid2, LINE_INTERSECT.getCode(), LINE_INTERSECT.getName(),
                    "线要素ID " + gid2 + " 与 ID " + gid1 + " 非端点相交", errorGeom));
            }
        } catch (Exception e) {
            log.warn("线线相交检查失败: {}", e.getMessage());
        }

        return issues;
    }

    /**
     * 线无效检查
     */
    private List<TopologyIssue> checkLineInvalid(String featureTable) {
        List<TopologyIssue> issues = new ArrayList<>();

        String sql = String.format(
            "SELECT gid, ST_AsText(geom) as geom FROM %s " +
            "WHERE (geom IS NULL OR NOT ST_IsValid(geom)) " +
            "AND (ST_GeometryType(geom) IN ('ST_LineString', 'ST_MultiLineString') OR geom IS NULL)",
            featureTable
        );

        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> row : result) {
                Long gid = ((Number) row.get("gid")).longValue();
                String geom = row.get("geom") != null ? row.get("geom").toString() : null;
                issues.add(new TopologyIssue(gid, GEOM_INVALID_LINE.getCode(), GEOM_INVALID_LINE.getName(),
                    "线要素ID " + gid + " 几何对象无效", geom));
            }
        } catch (Exception e) {
            log.warn("线无效检查失败: {}", e.getMessage());
        }

        return issues;
    }

    /**
     * 线悬挂检查 - 端点悬空
     */
    private List<TopologyIssue> checkLineDangle(String featureTable) {
        List<TopologyIssue> issues = new ArrayList<>();

        String sql = String.format(
            "WITH dumped_lines AS ( " +
            "  SELECT gid, (ST_Dump(geom)).geom AS geom " +
            "  FROM %s " +
            "  WHERE ST_GeometryType(geom) IN ('ST_LineString', 'ST_MultiLineString') " +
            "  AND ST_IsValid(geom) " +
            "), " +
            "endpoints AS ( " +
            "  SELECT gid, ST_StartPoint(geom) AS pt FROM dumped_lines " +
            "  UNION ALL " +
            "  SELECT gid, ST_EndPoint(geom) AS pt FROM dumped_lines " +
            "), " +
            "point_counts AS ( " +
            "  SELECT pt, COUNT(*) AS cnt FROM endpoints GROUP BY pt " +
            "), " +
            "dangling_pts AS ( " +
            "  SELECT pt FROM point_counts WHERE cnt = 1 " +
            ") " +
            "SELECT DISTINCT e.gid, ST_AsText(e.pt) AS wkt " +
            "FROM endpoints e " +
            "JOIN dangling_pts d ON e.pt = d.pt",
            featureTable
        );

        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> row : result) {
                Object gidVal = row.get("gid");
                Long gid = gidVal instanceof Integer ? ((Integer) gidVal).longValue() : (Long) gidVal;
                String geom = row.get("wkt") != null ? row.get("wkt").toString() : null;
                issues.add(new TopologyIssue(gid, LINE_DANGLE.getCode(), LINE_DANGLE.getName(),
                    "线要素ID " + gid + " 存在悬挂点", geom));
            }
        } catch (Exception e) {
            log.warn("线悬挂检查失败: {}", e.getMessage());
        }

        return issues;
    }

    // ==================== 面要素检查 ====================

    private List<TopologyIssue> checkPolygonTopology(String featureTable, String ruleCode, Double minArea) {
        List<TopologyIssue> issues = new ArrayList<>();

        switch (ruleCode) {
            case "polygon.overlap":
                issues.addAll(checkPolygonOverlap(featureTable));
                break;
            case "polygon.gap":
                issues.addAll(checkPolygonGap(featureTable));
                break;
            case "polygon.self_intersect":
                issues.addAll(checkPolygonSelfIntersect(featureTable));
                break;
            case "polygon.min_area":
                issues.addAll(checkPolygonMinArea(featureTable, minArea));
                break;
            case "polygon.invalid":
                issues.addAll(checkPolygonInvalid(featureTable));
                break;
            case "polygon.empty":
                issues.addAll(checkEmptyGeometry(featureTable, "POLYGON"));
                break;
        }

        return issues;
    }

    /**
     * 面重叠检查（空间 SQL 兜底）
     * 返回重叠区域几何 ST_Intersection
     */
    private List<TopologyIssue> checkPolygonOverlap(String featureTable) {
        List<TopologyIssue> issues = new ArrayList<>();

        String sql = String.format(
            "SELECT a.gid as gid1, b.gid as gid2, " +
            "  ST_AsText(ST_Intersection(ST_Buffer(a.geom, 0), ST_Buffer(b.geom, 0))) as error_geom " +
            "FROM " +
            "(SELECT * FROM %s WHERE ST_IsValid(geom) AND ST_GeometryType(geom) IN ('ST_Polygon', 'ST_MultiPolygon')) a, " +
            "(SELECT * FROM %s WHERE ST_IsValid(geom) AND ST_GeometryType(geom) IN ('ST_Polygon', 'ST_MultiPolygon')) b " +
            "WHERE a.gid < b.gid " +
            "AND ST_Intersects(a.geom, b.geom) " +
            "AND NOT ST_Contains(a.geom, b.geom) " +
            "AND NOT ST_Contains(b.geom, a.geom) " +
            "AND ST_Area(ST_Intersection(ST_Buffer(a.geom, 0), ST_Buffer(b.geom, 0))) > 0",
            featureTable, featureTable
        );

        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> row : result) {
                Long gid1 = ((Number) row.get("gid1")).longValue();
                Long gid2 = ((Number) row.get("gid2")).longValue();
                String errorGeom = row.get("error_geom") != null ? row.get("error_geom").toString() : null;
                issues.add(new TopologyIssue(gid1, POLYGON_OVERLAP.getCode(), POLYGON_OVERLAP.getName(),
                    "面要素ID " + gid1 + " 与 ID " + gid2 + " 重叠", errorGeom));
                issues.add(new TopologyIssue(gid2, POLYGON_OVERLAP.getCode(), POLYGON_OVERLAP.getName(),
                    "面要素ID " + gid2 + " 与 ID " + gid1 + " 重叠", errorGeom));
            }
        } catch (Exception e) {
            log.warn("面重叠检查失败: {}", e.getMessage());
        }

        return issues;
    }

    /**
     * 面重叠检查 - 使用拓扑表 face + relation
     * 被多个要素共享的 face 即为重叠区域
     */
    private List<TopologyIssue> checkPolygonOverlapTopology(String topologyName, String featureTable) {
        List<TopologyIssue> issues = new ArrayList<>();

        String sql = String.format(
            "WITH shared_faces AS ( " +
            "  SELECT element_id " +
            "  FROM %1$s.relation " +
            "  WHERE element_type = 3 " +
            "  GROUP BY element_id " +
            "  HAVING COUNT(DISTINCT topogeo_id) > 1 " +
            ") " +
            "SELECT f1.gid as gid1, f2.gid as gid2, " +
            "  ST_AsText(topology.ST_GetFaceGeometry('%2$s', sf.element_id)) as error_geom " +
            "FROM shared_faces sf " +
            "JOIN %1$s.relation r1 ON r1.element_id = sf.element_id AND r1.element_type = 3 " +
            "JOIN %1$s.relation r2 ON r2.element_id = sf.element_id AND r2.element_type = 3 " +
            "  AND r2.topogeo_id > r1.topogeo_id " +
            "JOIN %3$s f1 ON (f1.topo_geo).id = r1.topogeo_id " +
            "JOIN %3$s f2 ON (f2.topo_geo).id = r2.topogeo_id",
            topologyName, topologyName, featureTable
        );

        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> row : result) {
                Long gid1 = ((Number) row.get("gid1")).longValue();
                Long gid2 = ((Number) row.get("gid2")).longValue();
                String errorGeom = row.get("error_geom") != null ? row.get("error_geom").toString() : "";
                issues.add(new TopologyIssue(gid1, POLYGON_OVERLAP.getCode(), POLYGON_OVERLAP.getName(),
                    "面要素ID " + gid1 + " 与 ID " + gid2 + " 重叠", errorGeom));
                issues.add(new TopologyIssue(gid2, POLYGON_OVERLAP.getCode(), POLYGON_OVERLAP.getName(),
                    "面要素ID " + gid2 + " 与 ID " + gid1 + " 重叠", errorGeom));
            }
        } catch (Exception e) {
            log.warn("拓扑表面重叠检查失败: {}", e.getMessage());
        }

        return issues;
    }

    /**
     * 面缝隙检查 - 相邻面之间存在空白间隙（空间 SQL 兜底）
     * 返回缝隙区域几何（两面的边界之间区域）
     */
    private List<TopologyIssue> checkPolygonGap(String featureTable) {
        List<TopologyIssue> issues = new ArrayList<>();

        String sql = String.format(
            "WITH valid_polys AS ( " +
            "  SELECT gid, geom FROM %s " +
            "  WHERE ST_IsValid(geom) AND ST_GeometryType(geom) IN ('ST_Polygon', 'ST_MultiPolygon')" +
            "), " +
            "candidates AS ( " +
            "  SELECT a.gid AS gid1, b.gid AS gid2 " +
            "  FROM valid_polys a, valid_polys b " +
            "  WHERE a.gid < b.gid AND ST_DWithin(a.geom, b.geom, 0.0001) " +
            ") " +
            "SELECT c.gid1, c.gid2, " +
            "  ST_AsText(ST_Difference(" +
            "    ST_Buffer(ST_Intersection(ST_Buffer(a.geom, 0.00005), ST_Buffer(b.geom, 0.00005)), -0.00005), " +
            "    ST_Union(ST_Buffer(a.geom, 0), ST_Buffer(b.geom, 0))" +
            "  )) as error_geom " +
            "FROM candidates c " +
            "JOIN valid_polys a ON a.gid = c.gid1 " +
            "JOIN valid_polys b ON b.gid = c.gid2 " +
            "WHERE ST_Touches(a.geom, b.geom) AND NOT ST_Intersects(a.geom, b.geom)",
            featureTable
        );

        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> row : result) {
                Long gid1 = ((Number) row.get("gid1")).longValue();
                Long gid2 = ((Number) row.get("gid2")).longValue();
                String errorGeom = row.get("error_geom") != null ? row.get("error_geom").toString() : null;
                issues.add(new TopologyIssue(gid1, POLYGON_GAP.getCode(), POLYGON_GAP.getName(),
                    "面要素ID " + gid1 + " 与 ID " + gid2 + " 之间存在缝隙", errorGeom));
                issues.add(new TopologyIssue(gid2, POLYGON_GAP.getCode(), POLYGON_GAP.getName(),
                    "面要素ID " + gid2 + " 与 ID " + gid1 + " 之间存在缝隙", errorGeom));
            }
        } catch (Exception e) {
            log.warn("面缝隙检查失败: {}", e.getMessage());
        }

        return issues;
    }

    /**
     * 面缝隙检查 - 使用拓扑表 face
     * 不被任何要素覆盖的 face（排除 universal face id=0）即为缝隙
     */
    private List<TopologyIssue> checkPolygonGapTopology(String topologyName, String featureTable) {
        List<TopologyIssue> issues = new ArrayList<>();

        // 找缝隙面 → 通过 edge 的 left_face/right_face 找相邻面 → 映射到要素 gid
        String sql = String.format(
            "WITH gap_faces AS (" +
            "  SELECT f.face_id FROM %1$s.face f" +
            "  WHERE f.face_id > 0" +
            "  AND f.face_id NOT IN (SELECT element_id FROM %1$s.relation WHERE element_type = 3)" +
            "), gap_neighbors AS (" +
            "  SELECT gf.face_id AS gap_face_id," +
            "    CASE WHEN e.left_face = gf.face_id THEN e.right_face ELSE e.left_face END AS neighbor_face_id" +
            "  FROM gap_faces gf" +
            "  JOIN %1$s.edge e ON e.left_face = gf.face_id OR e.right_face = gf.face_id" +
            "  WHERE CASE WHEN e.left_face = gf.face_id THEN e.right_face ELSE e.left_face END > 0" +
            "), neighbor_gids AS (" +
            "  SELECT DISTINCT gn.gap_face_id, f.gid AS gid" +
            "  FROM gap_neighbors gn" +
            "  JOIN %1$s.relation r ON r.element_id = gn.neighbor_face_id AND r.element_type = 3" +
            "  JOIN %2$s f ON (f.topo_geo).id = r.topogeo_id" +
            "), gap_with_neighbors AS (" +
            "  SELECT gap_face_id," +
            "    (array_agg(DISTINCT gid ORDER BY gid))[1] AS gid1," +
            "    (array_agg(DISTINCT gid ORDER BY gid))[2] AS gid2" +
            "  FROM neighbor_gids GROUP BY gap_face_id" +
            ") " +
            "SELECT gn.gid1, gn.gid2," +
            "  ST_AsText(topology.ST_GetFaceGeometry('%1$s', gn.gap_face_id)) AS error_geom " +
            "FROM gap_with_neighbors gn",
            topologyName, featureTable
        );

        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> row : result) {
                Long gid1 = row.get("gid1") != null ? ((Number) row.get("gid1")).longValue() : null;
                Long gid2 = row.get("gid2") != null ? ((Number) row.get("gid2")).longValue() : null;
                String errorGeom = row.get("error_geom") != null ? row.get("error_geom").toString() : "";
                if (gid1 != null && gid2 != null) {
                    issues.add(new TopologyIssue(gid1, POLYGON_GAP.getCode(), POLYGON_GAP.getName(),
                        "面要素ID " + gid1 + " 与 ID " + gid2 + " 之间存在缝隙", errorGeom));
                    issues.add(new TopologyIssue(gid2, POLYGON_GAP.getCode(), POLYGON_GAP.getName(),
                        "面要素ID " + gid2 + " 与 ID " + gid1 + " 之间存在缝隙", errorGeom));
                } else if (gid1 != null) {
                    issues.add(new TopologyIssue(gid1, POLYGON_GAP.getCode(), POLYGON_GAP.getName(),
                        "面要素ID " + gid1 + " 附近存在缝隙", errorGeom));
                }
            }
        } catch (Exception e) {
            log.warn("拓扑表面缝隙检查失败: {}", e.getMessage());
        }

        return issues;
    }

    /**
     * 面自相交检查（拓扑辅助方法）
     * 拓扑导入时 WHERE ST_IsValid(geom) 过滤了无效几何，所以自相交多边形的 topo_geo 为 NULL
     * 查找 topo_geo IS NULL 且无效的多边形，比全表扫描 ST_IsValid 更高效
     */
    private List<TopologyIssue> checkPolygonSelfIntersectTopology(String topologyName, String featureTable) {
        List<TopologyIssue> issues = new ArrayList<>();

        String sql = String.format(
            "SELECT gid, ST_AsText((ST_IsValidDetail(geom)).geom) AS error_geom " +
            "FROM %s " +
            "WHERE topo_geo IS NULL " +
            "AND ST_GeometryType(geom) IN ('ST_Polygon','ST_MultiPolygon') " +
            "AND NOT ST_IsValid(geom)",
            featureTable
        );

        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> row : result) {
                Long gid = ((Number) row.get("gid")).longValue();
                String errorGeom = row.get("error_geom") != null ? row.get("error_geom").toString() : "";
                issues.add(new TopologyIssue(gid, POLYGON_SELF_INTERSECT.getCode(), POLYGON_SELF_INTERSECT.getName(),
                    "面要素ID " + gid + " 存在自相交", errorGeom));
            }
        } catch (Exception e) {
            log.warn("拓扑面自相交检查失败: {}", e.getMessage());
        }

        return issues;
    }

    /**
     * 面自相交检查
     * 返回自相交点（ST_IsValidDetail 获取错误位置）
     */
    private List<TopologyIssue> checkPolygonSelfIntersect(String featureTable) {
        List<TopologyIssue> issues = new ArrayList<>();

        // 使用 ST_IsValidDetail 获取自相交的精确位置
        String sql = String.format(
            "SELECT gid, ST_AsText((ST_IsValidDetail(geom)).geom) as error_geom " +
            "FROM %s " +
            "WHERE (ST_GeometryType(geom) = 'ST_Polygon' OR ST_GeometryType(geom) = 'ST_MultiPolygon') " +
            "AND NOT ST_IsValid(geom)",
            featureTable
        );

        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> row : result) {
                Long gid = ((Number) row.get("gid")).longValue();
                String errorGeom = row.get("error_geom") != null ? row.get("error_geom").toString() : null;
                issues.add(new TopologyIssue(gid, POLYGON_SELF_INTERSECT.getCode(), POLYGON_SELF_INTERSECT.getName(),
                    "面要素ID " + gid + " 存在自相交", errorGeom));
            }
        } catch (Exception e) {
            log.warn("面自相交检查失败: {}", e.getMessage());
        }

        return issues;
    }

    /**
     * 面积过小检查 - 小于最小面积阈值
     * 返回质心点 ST_PointOnSurface（而非完整面几何）
     */
    private List<TopologyIssue> checkPolygonMinArea(String featureTable, Double minArea) {
        List<TopologyIssue> issues = new ArrayList<>();

        if (minArea == null || minArea <= 0) {
            minArea = 0.00000001; // 默认最小面积
        }

        String sql = String.format(
            "SELECT gid, ST_Area(geom) as area, ST_AsText(ST_PointOnSurface(geom)) as error_geom FROM %s " +
            "WHERE (geom IS NULL OR ST_GeometryType(geom) IN ('ST_Polygon', 'ST_MultiPolygon')) " +
            "AND ST_IsValid(geom) " +
            "AND ST_Area(geom) < %.9f",
            featureTable, minArea
        );

        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> row : result) {
                Long gid = ((Number) row.get("gid")).longValue();
                Double area = ((Number) row.get("area")).doubleValue();
                String errorGeom = row.get("error_geom") != null ? row.get("error_geom").toString() : null;
                issues.add(new TopologyIssue(gid, POLYGON_MIN_AREA.getCode(), POLYGON_MIN_AREA.getName(),
                    "面要素ID " + gid + " 面积过小（面积：" + String.format("%.6f", area) + "）", errorGeom));
            }
        } catch (Exception e) {
            log.warn("面积过小检查失败: {}", e.getMessage());
        }

        return issues;
    }

    /**
     * 面无效检查
     */
    private List<TopologyIssue> checkPolygonInvalid(String featureTable) {
        List<TopologyIssue> issues = new ArrayList<>();

        String sql = String.format(
            "SELECT gid, ST_AsText(geom) as geom FROM %s " +
            "WHERE (geom IS NULL OR NOT ST_IsValid(geom)) " +
            "AND (ST_GeometryType(geom) IN ('ST_Polygon', 'ST_MultiPolygon') OR geom IS NULL)",
            featureTable
        );

        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> row : result) {
                Long gid = ((Number) row.get("gid")).longValue();
                String geom = row.get("geom") != null ? row.get("geom").toString() : null;
                issues.add(new TopologyIssue(gid, GEOM_INVALID_POLYGON.getCode(), GEOM_INVALID_POLYGON.getName(),
                    "面要素ID " + gid + " 几何对象无效", geom));
            }
        } catch (Exception e) {
            log.warn("面无效检查失败: {}", e.getMessage());
        }

        return issues;
    }

    // ==================== 通用检查 ====================

    /**
     * 空几何检查 - NULL或ST_IsEmpty检查
     * @param geometryType POINT/LINE/POLYGON 过滤
     */
    private List<TopologyIssue> checkEmptyGeometry(String featureTable, String geometryType) {
        List<TopologyIssue> issues = new ArrayList<>();

        String geomFilter;
        switch (geometryType) {
            case "POINT":
                geomFilter = "ST_GeometryType(geom) IN ('ST_Point', 'ST_MultiPoint')";
                break;
            case "LINE":
                geomFilter = "ST_GeometryType(geom) IN ('ST_LineString', 'ST_MultiLineString')";
                break;
            case "POLYGON":
                geomFilter = "ST_GeometryType(geom) IN ('ST_Polygon', 'ST_MultiPolygon')";
                break;
            default:
                geomFilter = "1=1";
        }

        String sql = String.format(
            "SELECT gid, ST_AsText(geom) as geom FROM %s " +
            "WHERE (%s) AND (geom IS NULL OR ST_IsEmpty(geom))",
            featureTable, geomFilter
        );

        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
            String ruleCode;
            String ruleName;
            switch (geometryType) {
                case "POINT":
                    ruleCode = GEOM_EMPTY_POINT.getCode();
                    ruleName = GEOM_EMPTY_POINT.getName();
                    break;
                case "LINE":
                    ruleCode = GEOM_EMPTY_LINE.getCode();
                    ruleName = GEOM_EMPTY_LINE.getName();
                    break;
                case "POLYGON":
                    ruleCode = GEOM_EMPTY_POLYGON.getCode();
                    ruleName = GEOM_EMPTY_POLYGON.getName();
                    break;
                default:
                    ruleCode = "geom.empty";
                    ruleName = "空几何";
            }

            for (Map<String, Object> row : result) {
                Long gid = ((Number) row.get("gid")).longValue();
                String geom = row.get("geom") != null ? row.get("geom").toString() : null;
                issues.add(new TopologyIssue(gid, ruleCode, ruleName,
                    "要素ID " + gid + " 几何为空或NULL", geom));
            }
        } catch (Exception e) {
            log.warn("空几何检查失败: {}", e.getMessage());
        }

        return issues;
    }

    // ==================== 跨图层检查 ====================

    private List<TopologyIssue> checkCrossLayerTopology(String featureTable, TopologyRule rule) {
        List<TopologyIssue> issues = new ArrayList<>();
        String ruleCode = rule.getRuleCode();
        String targetTable = rule.getTargetFeatureTable();

        if (targetTable == null || targetTable.isEmpty()) {
            return issues;
        }

        switch (ruleCode) {
            case "must_not_overlap":
                issues.addAll(checkCrossLayerOverlap(featureTable, targetTable));
                break;
            case "must_be_inside":
                issues.addAll(checkCrossLayerMustBeInside(featureTable, targetTable));
                break;
        }

        return issues;
    }

    /**
     * 跨图层不得重叠检查
     * 返回重叠区域几何
     */
    private List<TopologyIssue> checkCrossLayerOverlap(String featureTable, String targetTable) {
        List<TopologyIssue> issues = new ArrayList<>();

        String sql = String.format(
            "SELECT a.gid as gid1, b.gid as gid2, " +
            "  ST_AsText(ST_Intersection(a.geom, b.geom)) as error_geom " +
            "FROM %s a, %s b " +
            "WHERE a.geom IS NOT NULL AND b.geom IS NOT NULL " +
            "AND ST_IsValid(a.geom) AND ST_IsValid(b.geom) " +
            "AND (ST_GeometryType(a.geom) IN ('ST_Polygon', 'ST_MultiPolygon')) " +
            "AND (ST_GeometryType(b.geom) IN ('ST_Polygon', 'ST_MultiPolygon')) " +
            "AND ST_Overlaps(a.geom, b.geom)",
            featureTable, targetTable
        );

        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> row : result) {
                Long gid1 = ((Number) row.get("gid1")).longValue();
                Long gid2 = ((Number) row.get("gid2")).longValue();
                String errorGeom = row.get("error_geom") != null ? row.get("error_geom").toString() : null;
                issues.add(new TopologyIssue(gid1, MUST_NOT_OVERLAP.getCode(), MUST_NOT_OVERLAP.getName(),
                    "要素ID " + gid1 + " 与目标图层要素 " + gid2 + " 重叠", errorGeom));
            }
        } catch (Exception e) {
            log.warn("跨图层重叠检查失败: {}", e.getMessage());
        }

        return issues;
    }

    /**
     * 跨图层必须在内部检查
     * 返回要素在目标图层外部的部分几何
     */
    private List<TopologyIssue> checkCrossLayerMustBeInside(String featureTable, String targetTable) {
        List<TopologyIssue> issues = new ArrayList<>();

        String sql = String.format(
            "SELECT a.gid as gid1, " +
            "  ST_AsText(ST_Difference(a.geom, " +
            "    (SELECT ST_Union(b.geom) FROM %s b WHERE b.geom IS NOT NULL AND ST_IsValid(b.geom) " +
            "     AND ST_GeometryType(b.geom) IN ('ST_Polygon', 'ST_MultiPolygon'))" +
            "  )) as error_geom " +
            "FROM %s a " +
            "WHERE a.geom IS NOT NULL " +
            "AND ST_IsValid(a.geom) " +
            "AND NOT EXISTS ( " +
            "  SELECT 1 FROM %s b " +
            "  WHERE b.geom IS NOT NULL AND ST_IsValid(b.geom) " +
            "  AND (ST_GeometryType(b.geom) IN ('ST_Polygon', 'ST_MultiPolygon')) " +
            "  AND ST_Contains(b.geom, a.geom) " +
            ")",
            targetTable, featureTable, targetTable
        );

        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> row : result) {
                Long gid = ((Number) row.get("gid1")).longValue();
                String errorGeom = row.get("error_geom") != null ? row.get("error_geom").toString() : null;
                issues.add(new TopologyIssue(gid, MUST_BE_INSIDE.getCode(), MUST_BE_INSIDE.getName(),
                    "要素ID " + gid + " 未被目标图层包含", errorGeom));
            }
        } catch (Exception e) {
            log.warn("跨图层包含检查失败: {}", e.getMessage());
        }

        return issues;
    }

    // ==================== 拓扑管理方法 ====================

    @Override
    public void dropTopology(String topologyName) {
        if (!topologyExists(topologyName)) {
            log.info("拓扑 {} 不存在，跳过删除", topologyName);
            return;
        }

        String dropTopologySql = String.format(
            "SELECT topology.DropTopology('%s')",
            topologyName
        );
        jdbcTemplate.execute(dropTopologySql);
        log.info("拓扑 {} 删除成功", topologyName);
    }

    @Override
    public boolean topologyExists(String topologyName) {
        String checkTopologySql = "SELECT id FROM topology.topology WHERE name = ?";
        List<Map<String, Object>> existingTopology = jdbcTemplate.queryForList(checkTopologySql, topologyName);
        return !existingTopology.isEmpty();
    }

    @Override
    public List<Map<String, String>> getSupportedRules(String geometryType) {
        List<Map<String, String>> rules = new ArrayList<>();
        for (TopologyRuleEnum ruleEnum : TopologyRuleEnum.values()) {
            // 类型过滤：如果指定了类型且不匹配，则跳过
            if (geometryType != null && !geometryType.isEmpty()
                    && !ruleEnum.getGeometryType().equalsIgnoreCase(geometryType)) {
                continue;
            }
            Map<String, String> rule = new HashMap<>();
            rule.put("code", ruleEnum.getCode());
            rule.put("name", ruleEnum.getName());
            rule.put("geometryType", ruleEnum.getGeometryType());
            rules.add(rule);
        }
        return rules;
    }

    @Override
    public List<String> checkTopologyConsistency(String topologyName) {
        List<String> issues = new ArrayList<>();

        String validateSql = String.format(
            "SELECT * FROM topology.ValidateTopology('%s')",
            topologyName
        );

        try {
            List<Map<String, Object>> validateResults = jdbcTemplate.queryForList(validateSql);

            for (Map<String, Object> result : validateResults) {
                StringBuilder errorMsg = new StringBuilder();
                for (Map.Entry<String, Object> entry : result.entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();
                    if (value != null) {
                        errorMsg.append(key).append(": ").append(value).append("; ");
                    }
                }
                if (errorMsg.length() > 0) {
                    issues.add(errorMsg.toString().trim());
                }
            }
        } catch (Exception e) {
            log.error("拓扑验证失败: {}", e.getMessage());
            issues.add("拓扑验证失败: " + e.getMessage());
        }

        return issues;
    }

    @Override
    public Map<String, Object> getTopologyStatus(String layerId) {
        Map<String, Object> status = new HashMap<>();
        status.put("layerId", layerId);

        // 获取图层的属性表名作为拓扑名
        String topologyName = getFeatureTableByLayerId(layerId);
        if (topologyName == null) {
            status.put("exists", false);
            status.put("hasTopoColumn", false);
            status.put("error", "图层不存在");
            return status;
        }

        boolean exists = topologyExists(topologyName);
        status.put("exists", exists);
        status.put("topologyName", topologyName);

        if (exists) {
            // 检查拓扑几何列是否已填充
            String checkColumnSql = String.format(
                "SELECT COUNT(*) as cnt FROM information_schema.columns " +
                "WHERE table_schema = 'public' AND table_name = '%s' AND column_name = 'topo_geo'",
                topologyName
            );
            try {
                Integer count = jdbcTemplate.queryForObject(checkColumnSql, Integer.class);
                status.put("hasTopoColumn", count != null && count > 0);

                // 检查数据是否已填充
                if (count != null && count > 0) {
                    String checkDataSql = String.format(
                        "SELECT COUNT(*) FROM %s WHERE topo_geo IS NOT NULL",
                        topologyName
                    );
                    Integer dataCount = jdbcTemplate.queryForObject(checkDataSql, Integer.class);
                    status.put("dataPopulated", dataCount != null && dataCount > 0);
                    status.put("dataCount", dataCount != null ? dataCount : 0);
                } else {
                    status.put("dataPopulated", false);
                    status.put("dataCount", 0);
                }
            } catch (Exception e) {
                status.put("hasTopoColumn", false);
                status.put("dataPopulated", false);
            }
        } else {
            status.put("hasTopoColumn", false);
            status.put("dataPopulated", false);
            status.put("dataCount", 0);
        }

        return status;
    }

    @Override
    public void dropTopologyByLayerId(String layerId) {
        // 获取图层的属性表名作为拓扑名
        String topologyName = getFeatureTableByLayerId(layerId);
        if (topologyName != null && topologyExists(topologyName)) {
            dropTopology(topologyName);
        }
    }

    @Override
    public void ensureTopologyCreated(String layerId, String featureTable, String topologyName, int srid, double tolerance) {
        // 1. 创建拓扑（如果不存在）
        if (!topologyExists(topologyName)) {
            createTopology(topologyName, srid, tolerance);
        }

        // 2. 检查拓扑几何列是否存在
        String checkColumnSql = String.format(
            "SELECT EXISTS(SELECT 1 FROM information_schema.columns " +
            "WHERE table_schema = 'public' AND table_name = '%s' AND column_name = 'topo_geo')",
            featureTable
        );
        Boolean columnExists = jdbcTemplate.queryForObject(checkColumnSql, Boolean.class);

        if (columnExists) {
            // 列存在，检查是否有数据填充
            String checkDataSql = String.format(
                "SELECT COUNT(*) FROM %s WHERE topo_geo IS NOT NULL",
                featureTable
            );
            Integer dataCount = jdbcTemplate.queryForObject(checkDataSql, Integer.class);
            if (dataCount == null || dataCount == 0) {
                populateTopoGeometry(featureTable, "geom", "topo_geo", topologyName, null, tolerance);
            }
        } else {
            // 4. 添加拓扑几何列
            addTopoGeometryColumn(topologyName, "public", featureTable, "geom", "topo_geo");

            // 5. 填充拓扑数据
            populateTopoGeometry(featureTable, "geom", "topo_geo", topologyName, null, tolerance);
        }
    }

    /**
     * 根据图层ID获取属性表名
     */
    private String getFeatureTableByLayerId(String layerId) {
        String sql = "SELECT layerattrtable FROM sdms_layer WHERE id = ?";
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, layerId);
            if (results.isEmpty()) {
                return null;
            }
            return (String) results.get(0).get("layerattrtable");
        } catch (Exception e) {
            log.warn("查询图层属性表名失败: {}", e.getMessage());
            return null;
        }
    }

    private Integer getLayerId(String topologyName, String schemaName, String tableName, String columnName) {
        String checkTopologyExistsSql = "SELECT id FROM topology.topology WHERE name = ?";
        List<Map<String, Object>> topologyResults = jdbcTemplate.queryForList(checkTopologyExistsSql, topologyName);
        if (topologyResults.isEmpty()) {
            throw new RuntimeException("拓扑 " + topologyName + " 不存在，请先创建拓扑");
        }

        Object topologyId = topologyResults.get(0).get("id");

        String layerIdSql = String.format(
            "SELECT layer_id FROM topology.layer " +
            "WHERE topology_id = %s " +
            "AND schema_name = '%s' AND table_name = '%s' AND feature_column = '%s'",
            String.valueOf(topologyId), schemaName, tableName, columnName
        );

        List<Map<String, Object>> layerResults = jdbcTemplate.queryForList(layerIdSql);
        if (layerResults.isEmpty()) {
            throw new RuntimeException("未找到对应的layer_id，请先添加拓扑几何列");
        }

        Integer layerId = (Integer) layerResults.get(0).get("layer_id");
        if (layerId == null) {
            throw new RuntimeException("未找到对应的layer_id，请先添加拓扑几何列");
        }

        return layerId;
    }

    private String inferFeatureType(String tableName, String geometryColumn) {
        String geometryTypeSql = String.format(
            "SELECT DISTINCT ST_GeometryType(%s) as geom_type FROM %s LIMIT 1",
            geometryColumn, tableName
        );
        List<Map<String, Object>> geometryTypes = jdbcTemplate.queryForList(geometryTypeSql);

        if (geometryTypes.isEmpty()) {
            throw new RuntimeException("表 " + tableName + " 中没有几何数据");
        }

        String geomType = (String) geometryTypes.get(0).get("geom_type");

        switch (geomType) {
            case "ST_Point":
                return "POINT";
            case "ST_MultiPoint":
                return "MULTIPOINT";
            case "ST_LineString":
                return "LINE";
            case "ST_MultiLineString":
                return "MULTILINE";
            case "ST_Polygon":
                return "POLYGON";
            case "ST_MultiPolygon":
                return "MULTIPOLYGON";
            default:
                throw new RuntimeException("不支持的几何类型: " + geomType);
        }
    }
}
