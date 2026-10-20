package com.whulri.geodata.common.util.style;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class SLDSerialize {

    private static final String GB2312 = "GB2312";
    private static final String UTF8 = "UTF-8";

    /**
     * 序列化Tif对象为XML字符串
     *
     * @param obj 要序列化的对象
     * @return XML字符串
     */
    public static String serializeTif(Object obj) {
        if (obj == null) {
            throw new IllegalArgumentException("obj cannot be null");
        }

        try {
            JAXBContext context = JAXBContext.newInstance(obj.getClass());
            Marshaller marshaller = context.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_ENCODING, GB2312);
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

            // 设置命名空间
            Map<String, String> namespaces = new HashMap<>();
            namespaces.put("ogc", "http://www.opengis.net/ogc");
            namespaces.put("xlink", "http://www.w3.org/1999/xlink");
            namespaces.put("xsi", "http://www.w3.org/2001/XMLSchema-instance");

            // 创建输出流
            StringWriter writer = new StringWriter();
            XMLStreamWriter xmlStreamWriter = XMLOutputFactory.newInstance()
                    .createXMLStreamWriter(writer);

            // 添加命名空间
            for (Map.Entry<String, String> entry : namespaces.entrySet()) {
                xmlStreamWriter.setPrefix(entry.getKey(), entry.getValue());
            }

            marshaller.marshal(obj, xmlStreamWriter);
            return writer.toString();
        } catch (Exception ex) {
            log.error("Failed to serialize Tif object", ex);
            return "";
        }
    }

    /**
     * 序列化StyledLayerDescriptor对象为XML字符串
     *
     * @param obj StyledLayerDescriptor对象
     * @return XML字符串
     */
//    public static String serialize(Object obj) {
//        if (obj == null) {
//            throw new IllegalArgumentException("obj cannot be null");
//        }
//
//        try {
//            JAXBContext context = JAXBContext.newInstance(obj.getClass());
//            Marshaller marshaller = context.createMarshaller();
//            marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8"); // 建议用 UTF-8
//            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
//
//            StringWriter writer = new StringWriter();
//            XMLOutputFactory xof = XMLOutputFactory.newFactory();
//            XMLStreamWriter xmlStreamWriter = xof.createXMLStreamWriter(writer);
//
//            // ✅ 关键修复：设置 DEFAULT NAMESPACE（无前缀）
//            String defaultNs = "http://www.opengis.net/sld";
//            xmlStreamWriter.setDefaultNamespace(defaultNs); // ← 这行最重要！
//
//            // 可选：注册其他命名空间前缀（用于 ogc:, gml: 等）
//            xmlStreamWriter.writeNamespace("ogc", "http://www.opengis.net/ogc");
//            xmlStreamWriter.writeNamespace("gml", "http://www.opengis.net/gml");
//            xmlStreamWriter.writeNamespace("xlink", "http://www.w3.org/1999/xlink");
//
//            // 开始文档（可选，JAXB 会自动写）
//            xmlStreamWriter.writeStartDocument("UTF-8", "1.0");
//
//            // ✅ marshal 到已配置命名空间的 writer
//            marshaller.marshal(obj, xmlStreamWriter);
//
//            xmlStreamWriter.writeEndDocument();
//            xmlStreamWriter.close();
//
//            return writer.toString();
//        } catch (Exception ex) {
//            log.error("Failed to serialize SLD object", ex);
//            return "";
//        }
//    }
    public static String serialize(Object obj) {
        if (obj == null) {
            throw new IllegalArgumentException("obj cannot be null");
        }

        try {
            JAXBContext context = JAXBContext.newInstance(obj.getClass());
            Marshaller marshaller = context.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_ENCODING, UTF8);
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

            // 创建输出流
            StringWriter writer = new StringWriter();
//            XMLStreamWriter xmlStreamWriter = XMLOutputFactory.newInstance()
//                    .createXMLStreamWriter(writer);
//
//            xmlStreamWriter.setDefaultNamespace(SLDNamespaces.SLD);

            marshaller.marshal(obj, writer);
            return writer.toString();
        } catch (Exception ex) {
            log.error("Failed to serialize SLD object", ex);
            return "";
        }
    }

    /**
     * 序列化元数据对象为XML字符串
     *
     * @param obj 要序列化的对象
     * @return XML字符串
     */
    public static String serializeMetadata(Object obj) {
        if (obj == null) {
            throw new IllegalArgumentException("obj cannot be null");
        }

        try {
            JAXBContext context = JAXBContext.newInstance(obj.getClass());
            Marshaller marshaller = context.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_ENCODING, UTF8);
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

            StringWriter writer = new StringWriter();
            marshaller.marshal(obj, writer);
            return writer.toString();
        } catch (Exception ex) {
            log.error("Failed to serialize metadata object", ex);
            return "";
        }
    }

    /**
     * 从XML字符串反序列化对象
     *
     * @param sldXml XML字符串
     * @return 反序列化后的对象
     */
    public static Object deserialize(String sldXml) {
        if (sldXml == null || sldXml.trim().isEmpty()) {
            throw new IllegalArgumentException("sldXml cannot be null or empty");
        }

        try {
            JAXBContext context = JAXBContext.newInstance(StyledLayerDescriptor.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();

            // 使用GB2312编码读取XML
            byte[] bytes = sldXml.getBytes(Charset.forName(GB2312));
            ByteArrayInputStream stream = new ByteArrayInputStream(bytes);

            return unmarshaller.unmarshal(stream);
        } catch (Exception ex) {
            log.error("Failed to deserialize XML", ex);
            return null;
        }
    }

    /**
     * 从XML字符串反序列化为指定类型的对象
     *
     * @param type 目标类型
     * @param xml  XML字符串
     * @return 反序列化后的对象
     */
    @SuppressWarnings("unchecked")
    public static <T> T deserialize(String xml, Class<T> type) {
        if (xml == null || xml.trim().isEmpty()) {
            throw new IllegalArgumentException("xml cannot be null or empty");
        }

        try {
            // 使用我们自定义的、包含所有类的上下文（关键！）
            JAXBContext context = SLDContextFactory.newInstance();
            Unmarshaller unmarshaller = context.createUnmarshaller();

            // 使用 StringReader 直接读取字符串（无需指定编码，String 已是 Unicode）
            try (StringReader reader = new StringReader(xml)) {
                @SuppressWarnings("unchecked")
                T result = (T) unmarshaller.unmarshal(reader);
                return result;
            }

        } catch (Exception ex) {
            log.error("Failed to deserialize XML", ex);

            // 👇 关键：如果是 JAXB 初始化错误，尝试触发详细错误打印
            if (ex.getCause() != null &&
                    ex.getCause().getClass().getName().contains("IllegalAnnotationsException")) {
                // 强制重新创建上下文以触发错误详情（因为我们已在 SLDContextFactory 中加了打印逻辑）
                try {
                    SLDContextFactory.newInstance(); // 这会触发详细的错误输出
                } catch (Exception debugEx) {
                    log.error("Detailed JAXB error info triggered:", debugEx);
                }
            }

            throw new RuntimeException("Failed to deserialize XML to " + type.getSimpleName(), ex);
        }
    }
}
