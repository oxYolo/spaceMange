package com.whulri.geodata.convert.bim;

import com.whulri.geodata.convert.core.ConvertFileUtils;
import com.whulri.geodata.convert.core.ConvertTask;
import com.whulri.geodata.convert.core.TemplateCommandBuilder;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 单体/BIM 工具链公共骨架：从解压目录中定位唯一源模型文件。
 *
 * <p>本模块内只允许出现 Node 工具链（obj2gltf / FBX2glTF）与 IfcConvert 的调用细节。</p>
 */
public abstract class AbstractModelFileCommandBuilder extends TemplateCommandBuilder {

    /** 在输入（目录或文件）中定位源模型文件 */
    protected Path locateSource(ConvertTask task, Path inputFile, String... exts) {
        if (inputFile == null) {
            throw new IllegalArgumentException("inputFile is null");
        }
        if (Files.isDirectory(inputFile)) {
            Path hit = ConvertFileUtils.findFirstFile(inputFile, exts);
            if (hit == null) {
                throw new com.whulri.geodata.convert.core.ConvertException(
                        "解压目录中未找到 ." + String.join("/.", exts) + " 模型文件：" + inputFile);
            }
            return hit;
        }
        return inputFile;
    }
}
