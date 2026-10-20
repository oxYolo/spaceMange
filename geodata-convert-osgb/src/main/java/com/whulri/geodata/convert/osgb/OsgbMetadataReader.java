package com.whulri.geodata.convert.osgb;

import com.whulri.geodata.convert.core.ConvertException;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * OSGB metadata.xml 读取器（纯 JDK DOM 解析，无第三方依赖）。
 *
 * <p>Smart3D/ContextCapture 的 OSGB 目录通常为：</p>
 * <pre>
 *   root/
 *   ├── Data/
 *   │   ├── Tile_+000_+000/Tile_+000_+000_L17.osgb
 *   │   └── ...
 *   └── metadata.xml
 * </pre>
 * metadata.xml 中含 SRS 与 SRSOrigin（局部原点，三维定位必需）。
 */
public final class OsgbMetadataReader {

    private OsgbMetadataReader() {
    }

    /**
     * 在解压目录中定位 OSGB 数据根目录（含 metadata.xml 的目录）。
     * 依次尝试：目录本身 / 一级子目录 / 二级子目录。
     */
    public static Path locateSourceRoot(Path extractedDir) {
        if (Files.isRegularFile(extractedDir.resolve("metadata.xml"))) {
            return extractedDir;
        }
        try (Stream<Path> l1 = Files.list(extractedDir)) {
            Optional<Path> hit = l1.filter(Files::isDirectory)
                    .filter(d -> Files.isRegularFile(d.resolve("metadata.xml")))
                    .findFirst();
            if (hit.isPresent()) {
                return hit.get();
            }
            try (Stream<Path> l2 = Files.list(extractedDir)) {
                for (Path d : l2.filter(Files::isDirectory).sorted(Comparator.naturalOrder()).toList()) {
                    try (Stream<Path> l3 = Files.list(d)) {
                        Optional<Path> hit2 = l3.filter(Files::isDirectory)
                                .filter(x -> Files.isRegularFile(x.resolve("metadata.xml")))
                                .findFirst();
                        if (hit2.isPresent()) {
                            return hit2.get();
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new ConvertException("扫描 OSGB 解压目录失败：" + extractedDir, e);
        }
        throw new ConvertException("解压目录中未找到 metadata.xml，请确认为 Smart3D/ContextCapture 标准目录结构：" + extractedDir);
    }

    /** 解析 metadata.xml（SRS / SRSOrigin） */
    public static OsgbMetadata read(Path osgbRoot) {
        Path meta = osgbRoot.resolve("metadata.xml");
        if (!Files.isRegularFile(meta)) {
            throw new ConvertException("metadata.xml 不存在：" + meta);
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // 安全加固：禁用外部实体
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            Element root = factory.newDocumentBuilder().parse(new File(meta.toUri())).getDocumentElement();

            OsgbMetadata m = new OsgbMetadata();
            m.setSrs(textOfChild(root, "SRS"));
            String origin = textOfChild(root, "SRSOrigin");
            if (origin != null && !origin.isBlank()) {
                String[] parts = origin.trim().split("[,;\\s]+");
                if (parts.length > 0 && !parts[0].isBlank()) {
                    m.setOriginX(Double.parseDouble(parts[0].trim()));
                }
                if (parts.length > 1 && !parts[1].isBlank()) {
                    m.setOriginY(Double.parseDouble(parts[1].trim()));
                }
                if (parts.length > 2 && !parts[2].isBlank()) {
                    m.setOriginZ(Double.parseDouble(parts[2].trim()));
                }
            }
            return m;
        } catch (Exception e) {
            throw new ConvertException("解析 metadata.xml 失败：" + meta + "，" + e.getMessage(), e);
        }
    }

    private static String textOfChild(Element parent, String tagName) {
        var nodes = parent.getElementsByTagName(tagName);
        if (nodes == null || nodes.getLength() == 0) {
            return null;
        }
        String t = nodes.item(0).getTextContent();
        return t == null ? null : t.trim();
    }
}
