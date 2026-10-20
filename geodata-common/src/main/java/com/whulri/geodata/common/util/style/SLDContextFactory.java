// SLDContextFactory.java
package com.whulri.geodata.common.util.style;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import java.lang.reflect.Method;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SLDContextFactory {

    private static final Class<?>[] CLASSES = {
            // 根元素
            StyledLayerDescriptor.class,
            UserLayer.class,
            NamedLayer.class,
            UserStyle.class,
            FeatureTypeStyle.class,
            Rule.class,

            // Symbolizers (内部类!)
            Symbolizer.PointSymbolizer.class,
            Symbolizer.LineSymbolizer.class,
            Symbolizer.PolygonSymbolizer.class,
            Symbolizer.TextSymbolizer.class,
            Symbolizer.RasterSymbolizer.class,
            Symbolizer.LabelPlacement.class,

            // 内部辅助类
            Symbolizer.AnchorPoint.class,
            Symbolizer.Displacement.class,
            Symbolizer.PointPlacement.class,
            Symbolizer.Label.class,
            Symbolizer.Font.class,
            Symbolizer.Fill.class,
            Symbolizer.GraphicFill.class,
            Symbolizer.Stroke.class,
            Symbolizer.GraphicStroke.class,
            Symbolizer.CssParameter.class,
            Symbolizer.Mark.class,
            Symbolizer.ExternalGraphic.class,
            Symbolizer.OnlineResource.class,
            Symbolizer.Graphic.class,
            Symbolizer.ColorMap.class,
            Symbolizer.ColorMapEntry.class,

            // Filters
            Filter.class,
            And.class,
            Or.class,
            Not.class,
            PropertyIsEqualTo.class,
            PropertyIsNotEqualTo.class,
            PropertyIsLessThan.class,
            PropertyIsGreaterThan.class,
            PropertyIsLessThanOrEqualTo.class,
            PropertyIsGreaterThanO.class,
            PropertyIsLike.class,
            PropertyIsNull.class,
            PropertyIsBetween.class,
            BBox.class,
            Envelope.class,
            Function.class
    };

//    public static JAXBContext newInstance() throws JAXBException {
//        try {
//            return JAXBContext.newInstance(CLASSES);
//        } catch (Exception e) {
//            // 捕获并打印具体错误（兼容不同 JDK）
//            if (e instanceof JAXBException && e.getCause() != null) {
//                String causeName = e.getCause().getClass().getName();
//                if (causeName.contains("IllegalAnnotationsException")) {
//                    System.err.println("============================================");
//                    System.err.println("JAXB 非法注解异常详情:");
//                    System.err.println("（请检查以下类是否缺少 @NoArgsConstructor 或 @XmlAccessorType）");
//                    System.err.println("============================================");
//                    e.getCause().printStackTrace(System.err);
//                    System.err.println("============================================");
//                }
//            }
//            throw new JAXBException("Failed to create JAXBContext", e);
//        }
//    }
public static JAXBContext newInstance() throws JAXBException {
    try {
        return JAXBContext.newInstance(CLASSES);
    } catch (Exception e) {
        // 检查是否由 IllegalAnnotationsException 引起
        Throwable cause = e;
        while (cause != null) {
            if ("com.sun.xml.bind.v2.runtime.IllegalAnnotationsException".equals(cause.getClass().getName())) {
                System.err.println("============================================");
                System.err.println("🔍 检测到 JAXB 非法注解异常，正在提取详细错误...");
                System.err.println("============================================");

                try {
                    // 反射调用 getErrors()
                    Method getErrorsMethod = cause.getClass().getMethod("getErrors");
                    @SuppressWarnings("unchecked")
                    List<Object> errors = (List<Object>) getErrorsMethod.invoke(cause);

                    if (errors != null && !errors.isEmpty()) {
                        for (int i = 0; i < errors.size(); i++) {
                            System.err.println((i + 1) + ". " + errors.get(i).toString());
                        }
                    } else {
                        System.err.println("无法获取错误详情（errors 为空）");
                    }
                } catch (Exception ex) {
                    log.warn("反射调用 getErrors() 失败: {}", ex.getMessage(), ex);
                }

                System.err.println("============================================");
                break;
            }
            cause = cause.getCause();
        }
        throw new JAXBException("Failed to create JAXBContext", e);
    }
}
}