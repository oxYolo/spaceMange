package com.whulri.geodata.convert.pointcloud;

import com.whulri.geodata.convert.core.ConvertFileUtils;
import com.whulri.geodata.convert.core.ConvertTask;
import com.whulri.geodata.convert.core.TemplateCommandBuilder;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * Python 工具链：点云 LAS/LAZ → 3D Tiles pnts（py3dtiles，pip install py3dtiles）。
 *
 * <p>本模块只允许出现 Python 解释器的调用细节，其他工具链一律不放。</p>
 *
 * <p>默认命令模板（可在配置中覆盖）：</p>
 * <pre>{bin} -m py3dtiles convert {input} --out {output}</pre>
 * <p>{bin} 指向 Python 解释器（Windows 例：D:/envs/py3dtiles/python.exe；Linux 例：/usr/bin/python3）。</p>
 */
@Component
public class Py3dtilesCommandBuilder extends TemplateCommandBuilder {

    public static final String TOOL_ID = "py3dtiles";

    @Override
    public String toolId() {
        return TOOL_ID;
    }

    @Override
    public Set<String> supportedSrcFormats() {
        return Set.of("las", "laz");
    }

    @Override
    protected Path resolveInput(ConvertTask task, Path workDir, Path inputFile) {
        if (inputFile == null) {
            throw new IllegalArgumentException("inputFile is null");
        }
        if (Files.isDirectory(inputFile)) {
            // zip 解压场景：在解压目录中找第一个 .las/.laz 文件
            Path hit = ConvertFileUtils.findFirstFile(inputFile, "las", "laz");
            if (hit == null) {
                throw new com.whulri.geodata.convert.core.ConvertException(
                        "解压目录中未找到 .las/.laz 点云文件：" + inputFile);
            }
            return hit;
        }
        return inputFile;
    }
}
