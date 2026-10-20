package com.whulri.geodata.convert.bim;

import com.whulri.geodata.convert.core.ConvertTask;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Set;

/**
 * BIM 工具链：IFC → glTF（IfcConvert，IfcOpenShell 原生二进制，Windows/Linux 双平台）。
 *
 * <p>默认命令模板（可在配置中覆盖）：</p>
 * <pre>{bin} {input} {output}</pre>
 * <p>IfcConvert 依据输出扩展名选择导出格式：.glb 输出二进制 glTF。</p>
 */
@Component
public class IfcConvertCommandBuilder extends AbstractModelFileCommandBuilder {

    public static final String TOOL_ID = "ifcconvert";

    @Override
    public String toolId() {
        return TOOL_ID;
    }

    @Override
    public Set<String> supportedSrcFormats() {
        return Set.of("ifc");
    }

    @Override
    protected Path resolveInput(ConvertTask task, Path workDir, Path inputFile) {
        return locateSource(task, inputFile, "ifc");
    }

    @Override
    protected Path resolveOutput(ConvertTask task, Path workDir, Path outputDir) {
        String base = task.getName() == null || task.getName().isBlank()
                ? "model" : task.getName();
        return outputDir.resolve(safeFileName(base) + ".glb");
    }

    private String safeFileName(String name) {
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        return cleaned.length() > 120 ? cleaned.substring(0, 120) : cleaned;
    }
}
