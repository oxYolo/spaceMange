package com.whulri.geodata.convert.osgb;

import com.whulri.geodata.convert.core.ConvertTask;
import com.whulri.geodata.convert.core.ConvertToolConfig;
import com.whulri.geodata.convert.core.TemplateCommandBuilder;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Set;

/**
 * C++ 工具链：OSGB → 3D Tiles（fanvanzh/3dtiles 原生二进制，Windows/Linux 双平台）。
 *
 * <p>本模块只允许出现 3dtiles（C++ 原生二进制）的调用细节，其他工具链一律不放。</p>
 *
 * <p>默认命令模板（可在配置中覆盖）：</p>
 * <pre>{bin} -f osgb -i {input} -o {output} {extra}</pre>
 */
@Component
public class Osgb3dtilesCommandBuilder extends TemplateCommandBuilder {

    public static final String TOOL_ID = "3dtiles";

    @Override
    public String toolId() {
        return TOOL_ID;
    }

    @Override
    public Set<String> supportedSrcFormats() {
        return Set.of("osgb");
    }

    @Override
    protected Path resolveInput(ConvertTask task, Path workDir, Path inputFile) {
        // 3dtiles 需要的是含 metadata.xml 的 OSGB 根目录（而非 zip 文件本身）
        return OsgbMetadataReader.locateSourceRoot(inputFile);
    }

    @Override
    public java.util.Map<String, String> extractMetadata(ConvertTask task, Path inputBase) {
        Path root = OsgbMetadataReader.locateSourceRoot(inputBase);
        OsgbMetadata meta = OsgbMetadataReader.read(root);
        java.util.Map<String, String> m = new java.util.HashMap<>();
        if (meta.getSrs() != null && !meta.getSrs().isBlank()) {
            m.put("srs", meta.getSrs());
        }
        if (meta.getOriginX() != null) {
            m.put("originX", String.valueOf(meta.getOriginX()));
        }
        if (meta.getOriginY() != null) {
            m.put("originY", String.valueOf(meta.getOriginY()));
        }
        if (meta.getOriginZ() != null) {
            m.put("originZ", String.valueOf(meta.getOriginZ()));
        }
        return m;
    }

    @Override
    protected Path resolveOutput(ConvertTask task, Path workDir, Path outputDir) {
        return outputDir;
    }
}
