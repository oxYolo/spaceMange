package com.whulri.geodata.common.util.style;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import java.util.List;

/**
 * 规则
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@XmlAccessorType(XmlAccessType.FIELD)
public class Rule {
    @XmlElement(name = "Name",namespace = SLDNamespaces.SLD)
    private String name;

    @XmlElement(name = "Title",namespace = SLDNamespaces.SLD)
    private String title;

    // 注意：'abstract' 是 Java 关键字，XML 中仍为 <sld:Abstract>
    @XmlElement(name = "Abstract", namespace = SLDNamespaces.SLD)
    private String _abstract;

    @XmlElement(name = "MinScaleDenominator",namespace = SLDNamespaces.SLD)
    private String minScaleDenominator;

    @XmlElement(name = "MaxScaleDenominator",namespace = SLDNamespaces.SLD)
    private String maxScaleDenominator;

    // Filter 属于 OGC 命名空间
    @XmlElement(name = "Filter",namespace = SLDNamespaces.OGC)
    private Filter filter;

    // 各类符号器（Symbolizer）
    @XmlElement(name = "PointSymbolizer",namespace = SLDNamespaces.SLD)
    private Symbolizer.PointSymbolizer pointSymbolizer;

    @XmlElement(name = "LineSymbolizer", namespace = SLDNamespaces.SLD)
    private List<Symbolizer.LineSymbolizer> lineSymbolizer;

    @XmlElement(namespace = SLDNamespaces.SLD)
    private Symbolizer.PolygonSymbolizer polygonSymbolizer;

    @XmlElement(name = "PolygonSymbolizer",namespace = SLDNamespaces.SLD)
    private Symbolizer.TextSymbolizer textSymbolizer;

    @XmlElement(name = "RasterSymbolizer", namespace = SLDNamespaces.SLD)
    private Symbolizer.RasterSymbolizer rasterSymbolizer;

    // 非标准扩展字段：<sld:Count>1</sld:Count>
    @XmlElement(name = "Count",namespace = SLDNamespaces.SLD)
    private String count;
}
