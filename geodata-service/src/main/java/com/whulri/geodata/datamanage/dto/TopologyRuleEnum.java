package com.whulri.geodata.datamanage.dto;

import lombok.Getter;

/**
 * 拓扑规则枚举
 * 统一命名规范：point.overlap, line.self_intersect, polygon.gap 等
 */
@Getter
public enum TopologyRuleEnum {

    // ==================== 点要素规则 (POINT) ====================
    /**
     * 点重叠 - 精确重合的点
     */
    POINT_OVERLAP("point.overlap", "点重叠", "POINT"),

    /**
     * 几何无效 - 点几何无效
     */
    POINT_INVALID("point.invalid", "几何无效", "POINT"),

    /**
     * 空几何 - NULL或空几何
     */
    GEOM_EMPTY_POINT("point.empty", "空几何", "POINT"),

    /**
     * 点过近 - 使用容差判断
     */
    POINT_COINCIDENT("point.coincident", "点过近", "POINT"),

    // ==================== 线要素规则 (LINE) ====================
    /**
     * 线自相交
     */
    LINE_SELF_INTERSECT("line.self_intersect", "线自相交", "LINE"),

    /**
     * 线段重叠 - 完全重叠的线段
     */
    LINE_OVERLAP("line.overlap", "线段重叠", "LINE"),

    /**
     * 线线相交 - 非端点相交
     */
    LINE_INTERSECT("line.intersect", "线线相交", "LINE"),

    /**
     * 几何无效 - 线几何无效
     */
    LINE_INVALID("line.invalid", "几何无效", "LINE"),

    /**
     * 空几何 - NULL或空几何
     */
    GEOM_EMPTY_LINE("line.empty", "空几何", "LINE"),

    /**
     * 线悬挂 - 线端点悬空
     */
    LINE_DANGLE("line.dangle", "线悬挂", "LINE"),

    // ==================== 面要素规则 (POLYGON) ====================
    /**
     * 面重叠
     */
    POLYGON_OVERLAP("polygon.overlap", "面重叠", "POLYGON"),

    /**
     * 面缝隙 - 相邻面之间存在间隙
     */
    POLYGON_GAP("polygon.gap", "面缝隙", "POLYGON"),

    /**
     * 面自相交
     */
    POLYGON_SELF_INTERSECT("polygon.self_intersect", "面自相交", "POLYGON"),

    /**
     * 面积过小 - 小于最小面积阈值
     */
    POLYGON_MIN_AREA("polygon.min_area", "面积过小", "POLYGON"),

    /**
     * 几何无效 - 面几何无效
     */
    POLYGON_INVALID("polygon.invalid", "几何无效", "POLYGON"),

    /**
     * 空几何 - NULL或空几何
     */
    GEOM_EMPTY_POLYGON("polygon.empty", "空几何", "POLYGON"),

    // ==================== 跨图层规则 (CROSS_LAYER) ====================
    /**
     * 不得重叠 - 跨图层重叠检查
     */
    MUST_NOT_OVERLAP("must_not_overlap", "不得重叠", "CROSS_LAYER"),

    /**
     * 必须在内部 - 跨图层包含检查
     */
    MUST_BE_INSIDE("must_be_inside", "必须在内部", "CROSS_LAYER");

    private final String code;
    private final String name;
    private final String geometryType;

    TopologyRuleEnum(String code, String name, String geometryType) {
        this.code = code;
        this.name = name;
        this.geometryType = geometryType;
    }

    /**
     * 根据代码获取枚举
     */
    public static TopologyRuleEnum getByCode(String code) {
        for (TopologyRuleEnum rule : values()) {
            if (rule.getCode().equals(code)) {
                return rule;
            }
        }
        return null;
    }

    /**
     * 是否为点要素规则
     */
    public static boolean isPointRule(String code) {
        TopologyRuleEnum rule = getByCode(code);
        return rule != null && "POINT".equals(rule.getGeometryType());
    }

    /**
     * 是否为线要素规则
     */
    public static boolean isLineRule(String code) {
        TopologyRuleEnum rule = getByCode(code);
        return rule != null && "LINE".equals(rule.getGeometryType());
    }

    /**
     * 是否为面要素规则
     */
    public static boolean isPolygonRule(String code) {
        TopologyRuleEnum rule = getByCode(code);
        return rule != null && "POLYGON".equals(rule.getGeometryType());
    }

    /**
     * 是否为跨图层规则
     */
    public static boolean isCrossLayerRule(String code) {
        TopologyRuleEnum rule = getByCode(code);
        return rule != null && "CROSS_LAYER".equals(rule.getGeometryType());
    }
}
