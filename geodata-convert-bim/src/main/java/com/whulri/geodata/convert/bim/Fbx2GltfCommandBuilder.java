package com.whulri.geodata.convert.bim;

import com.whulri.geodata.convert.core.ConvertTask;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Set;

/**
 * Node 工具链：FBX → glb（Facebook FBX2glTF 原生二进制）。
 *
 * <p>默认命令模板（可在配置中覆盖）：</p>
 * <pre>{bin} -i {input} -o {output}</pre>
 * <p>FBX2glTF 按输出扩展名自动选择 binary/embed 模式，{output} 以 .glb 结尾即输出二进制 glTF。</p>
 */
@Component
public class Fbx2GltfCommandBuilder extends AbstractModelFileCommandBuilder {

    public static final String TOOL_ID = "fbx2gltf";

    @Override
    public String toolId() {
        return TOOL_ID;
    }

    @Override
    public Set<String> supportedSrcFormats() {
        return Set.of("fbx");
    }

    @Override
    protected Path resolveInput(ConvertTask task, Path workDir, Path inputFile) {
        return locateSource(task, inputFile, "fbx");
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
