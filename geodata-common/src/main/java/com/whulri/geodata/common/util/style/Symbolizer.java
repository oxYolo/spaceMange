// Symbolizer.java
package com.whulri.geodata.common.util.style;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import jakarta.xml.bind.annotation.*;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlSeeAlso({
        Symbolizer.PointSymbolizer.class,
        Symbolizer.LineSymbolizer.class,
        Symbolizer.PolygonSymbolizer.class,
        Symbolizer.TextSymbolizer.class,
        Symbolizer.RasterSymbolizer.class,
        Symbolizer.LabelPlacement.class
})
public abstract class Symbolizer {

    // ================== 符号化器子类 ==================

    @XmlType(name = "PointSymbolizer", namespace = SLDNamespaces.SLD)
    @XmlAccessorType(XmlAccessType.FIELD)
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PointSymbolizer extends Symbolizer {
        @XmlElement(name = "Graphic", namespace = SLDNamespaces.SLD)
        private Graphic graphic;
    }

    @XmlType(name = "LineSymbolizer", namespace = SLDNamespaces.SLD)
    @XmlAccessorType(XmlAccessType.FIELD)
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class LineSymbolizer extends Symbolizer {
        @XmlElement(name = "Stroke", namespace = SLDNamespaces.SLD)
        private Stroke stroke;
    }

    @XmlType(name = "PolygonSymbolizer", namespace = SLDNamespaces.SLD)
    @XmlAccessorType(XmlAccessType.FIELD)
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PolygonSymbolizer extends Symbolizer {
        @XmlElement(name = "Stroke", namespace = SLDNamespaces.SLD)
        private Stroke stroke;
        @XmlElement(name = "Graphic", namespace = SLDNamespaces.SLD)
        private Graphic graphic;
        @XmlElement(name = "Fill", namespace = SLDNamespaces.SLD)
        private Fill fill;
    }

    @XmlType(name = "TextSymbolizer", namespace = SLDNamespaces.SLD)
    @XmlAccessorType(XmlAccessType.FIELD)
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class TextSymbolizer extends Symbolizer {
        @XmlElement(name = "Label", namespace = SLDNamespaces.SLD)
        private Label label;
        @XmlElement(name = "Font", namespace = SLDNamespaces.SLD)
        private Font font;
        @XmlElement(name = "LabelPlacement", namespace = SLDNamespaces.SLD)
        private LabelPlacement labelPlacement;
        @XmlElement(name = "Fill", namespace = SLDNamespaces.SLD)
        private Fill fill;
    }

    @XmlType(name = "RasterSymbolizer", namespace = SLDNamespaces.SLD)
    @XmlAccessorType(XmlAccessType.FIELD)
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class RasterSymbolizer extends Symbolizer {
        @XmlElement(name = "Opacity", namespace = SLDNamespaces.SLD)
        private String opacity;

        @XmlElement(name = "ChannelSelection", namespace = SLDNamespaces.SLD)
        private ChannelSelection channelSelection;

        @XmlElement(name = "ColorMap", namespace = SLDNamespaces.SLD)
        private ColorMap colorMap;

        @XmlElement(name = "ContrastEnhancement", namespace = SLDNamespaces.SLD)
        private ContrastEnhancement contrastEnhancement;
    }

    @XmlType(namespace = SLDNamespaces.SLD)
    @XmlAccessorType(XmlAccessType.FIELD)
    @Data
    public static class ChannelSelection {
        @XmlElement(name = "GrayChannel", namespace = SLDNamespaces.SLD)
        private GrayChannel grayChannel;
    }

    @XmlType(namespace = SLDNamespaces.SLD)
    @XmlAccessorType(XmlAccessType.FIELD)
    @Data
    public static class GrayChannel {
        @XmlElement(name = "SourceChannelName", namespace = SLDNamespaces.SLD)
        private String sourceChannelName;
    }

    @XmlType(namespace = SLDNamespaces.SLD)
    @XmlAccessorType(XmlAccessType.FIELD)
    @Data
    public static class ContrastEnhancement {
    }

    @XmlType(name = "LabelPlacement", namespace = SLDNamespaces.SLD)
    @XmlAccessorType(XmlAccessType.FIELD)
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class LabelPlacement extends Symbolizer {
        @XmlElement(name = "PointPlacement", namespace = SLDNamespaces.SLD)
        private PointPlacement pointPlacement;
    }

    // ================== 辅助结构 ==================

    @XmlType(namespace = SLDNamespaces.SLD)
    @XmlAccessorType(XmlAccessType.FIELD)
    @Data
    public static class AnchorPoint {
        @XmlElement(name = "AnchorPointX", namespace = SLDNamespaces.SLD)
        private String anchorPointX;
        @XmlElement(name = "AnchorPointY", namespace = SLDNamespaces.SLD)
        private String anchorPointY;
    }

    @XmlType(namespace = SLDNamespaces.SLD)
    @XmlAccessorType(XmlAccessType.FIELD)
    @Data
    public static class Displacement {
        @XmlElement(name = "DisplacementX", namespace = SLDNamespaces.SLD)
        private String displacementX;
        @XmlElement(name = "DisplacementY", namespace = SLDNamespaces.SLD)
        private String displacementY;
    }

    @XmlType(namespace = SLDNamespaces.SLD)
    @XmlAccessorType(XmlAccessType.FIELD)
    @Data
    public static class PointPlacement {
        @XmlElement(name = "AnchorPoint", namespace = SLDNamespaces.SLD)
        private AnchorPoint anchorPoint;
        @XmlElement(name = "Displacement", namespace = SLDNamespaces.SLD)
        private Displacement displacement;
    }

    @XmlType(namespace = SLDNamespaces.SLD)
    @XmlAccessorType(XmlAccessType.FIELD)
    @Data
    public static class Label {
        @XmlElement(name = "PropertyName", namespace = SLDNamespaces.OGC)
        private String propertyName;
    }

    @XmlType(namespace = SLDNamespaces.SLD)
    @XmlAccessorType(XmlAccessType.FIELD)
    @Data
    public static class Font {
        @XmlElement(name = "CssParameter", namespace = SLDNamespaces.SLD)
        private List<CssParameter> cssParameters;
    }

    @XmlType(namespace = SLDNamespaces.SLD)
    @XmlAccessorType(XmlAccessType.FIELD)
    @Data
    public static class Fill {
        @XmlElement(name = "GraphicFill", namespace = SLDNamespaces.SLD)
        private GraphicFill graphicFill;
        @XmlElement(name = "CssParameter", namespace = SLDNamespaces.SLD)
        private List<CssParameter> cssParameters;
    }

    @XmlType(namespace = SLDNamespaces.SLD)
    @XmlAccessorType(XmlAccessType.FIELD)
    @Data
    public static class GraphicFill {
        @XmlElement(name = "Graphic", namespace = SLDNamespaces.SLD)
        private Graphic graphic;
    }

    @XmlType(namespace = SLDNamespaces.SLD)
    @XmlAccessorType(XmlAccessType.FIELD)
    @Data
    public static class Stroke {
        @XmlElement(name = "CssParameter", namespace = SLDNamespaces.SLD)
        private List<CssParameter> cssParameters;
        @XmlElement(name = "GraphicStroke", namespace = SLDNamespaces.SLD)
        private GraphicStroke graphicStroke;
    }

    @XmlType(namespace = SLDNamespaces.SLD)
    @XmlAccessorType(XmlAccessType.FIELD)
    @Data
    public static class GraphicStroke {
        @XmlElement(name = "Graphic", namespace = SLDNamespaces.SLD)
        private Graphic graphic;
    }

    @XmlType(namespace = SLDNamespaces.SLD)
    @XmlAccessorType(XmlAccessType.FIELD)
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CssParameter {
        @XmlAttribute(name = "name")
        private String name;
        @XmlValue
        private String value;
    }

    @XmlType(namespace = SLDNamespaces.SLD)
    @XmlAccessorType(XmlAccessType.FIELD)
    @Data
    public static class Mark {
        @XmlElement(name = "WellKnownName", namespace = SLDNamespaces.SLD)
        private String wellKnownName;
        @XmlElement(name = "Fill", namespace = SLDNamespaces.SLD)
        private Fill fill;
        @XmlElement(name = "Stroke", namespace = SLDNamespaces.SLD)
        private Stroke stroke;
        @XmlElement(name = "Size", namespace = SLDNamespaces.SLD)
        private String size;
    }

    @XmlType(namespace = SLDNamespaces.SLD)
    @XmlAccessorType(XmlAccessType.FIELD)
    @Data
    public static class ExternalGraphic {
        @XmlElement(name = "OnlineResource", namespace = SLDNamespaces.SLD)
        private OnlineResource onlineResource;
        @XmlElement(name = "Format", namespace = SLDNamespaces.SLD)
        private String format = "image/png";
    }

    @XmlType(namespace = SLDNamespaces.SLD)
    @XmlAccessorType(XmlAccessType.FIELD)
    @Data
    public static class OnlineResource {
        @XmlAttribute(name = "type", namespace = SLDNamespaces.XLINK)
        private String type = "simple";
        @XmlAttribute(name = "href", namespace = SLDNamespaces.XLINK)
        private String href;
    }

    @XmlType(namespace = SLDNamespaces.SLD)
    @XmlAccessorType(XmlAccessType.FIELD)
    @Data
    public static class Graphic {
        @XmlElement(name = "Mark", namespace = SLDNamespaces.SLD)
        private Mark mark;
        @XmlElement(name = "ExternalGraphic", namespace = SLDNamespaces.SLD)
        private ExternalGraphic externalGraphic;
        @XmlElement(name = "Size", namespace = SLDNamespaces.SLD)
        private String size;
        @XmlElement(name = "Rotation", namespace = SLDNamespaces.SLD)
        private String rotation;
    }

    @XmlType(namespace = SLDNamespaces.SLD)
    @XmlAccessorType(XmlAccessType.FIELD)
    @Data
    public static class ColorMap {
        @XmlAttribute(name = "type")
        private String type;
        @XmlElement(name = "ColorMapEntry", namespace = SLDNamespaces.SLD)
        private List<ColorMapEntry> colorMapEntry;
    }

    @XmlType(namespace = SLDNamespaces.SLD)
    @XmlAccessorType(XmlAccessType.FIELD)
    @Data
    public static class ColorMapEntry {
        @XmlAttribute(name = "color")
        private String color;
        @XmlAttribute(name = "quantity")
        private String quantity;
        @XmlAttribute(name = "label")
        private String label;
        @XmlAttribute(name = "opacity")
        private String opacity;
    }
}