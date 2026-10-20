package com.whulri.geodata.convert.bim;

import com.whulri.geodata.convert.core.ConvertTask;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Set;

/**
 * Node 工具链：OBJ → glb（obj2gltf，npm i -g obj2gltf 或 npx）。
 *
 * <p>默认命令模板（可在配置中覆盖）：</p>
 * <pre>{bin} --yes obj2gltf -i {input} -o {output}</pre>
 * <p>{bin} 建议配 npx（Windows 为 npx.cmd 的完整路径或裸 npx；Linux 为 npx）；
 * {output} 为具体 glb 文件路径。</p>
 */
@Component
public class Obj2GltfCommandBuilder extends AbstractModelFileCommandBuilder {

    public static final String TOOL_ID = "obj2gltf";

    @Override
    public String toolId() {
        return TOOL_ID;
    }

    @Override
    public Set<String> supportedSrcFormats() {
        return Set.of("obj");
    }

    @Override
    protected Path resolveInput(ConvertTask task, Path workDir, Path inputFile) {
        return locateSource(task, inputFile, "obj");
    }

    @Override
    protected Path resolveOutput(ConvertTask task, Path workDir, Path outputDir) {
        // obj2gltf 的 -o 指向具体 glb 文件
        String base = task.getName() == null || task.getName().isBlank()
                ? "model" : task.getName();
        return outputDir.resolve(safeFileName(base) + ".glb");
    }

    private String safeFileName(String name) {
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        return cleaned.length() > 120 ? cleaned.substring(0, 120) : cleaned;
    }
}
