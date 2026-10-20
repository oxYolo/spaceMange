package com.whulri.geodata.convert.core;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 三维转换命令构建 SPI。
 *
 * <p>每个工具链（语言）一个独立模块、一个实现类：
 * 模块内只允许出现该工具链的调用细节，禁止跨工具链混写。</p>
 */
public interface ConvertCommandBuilder {

    /** 工具标识（与配置键 geodata.convert.tools.&lt;toolId&gt; 一致），如 3dtiles / py3dtiles / obj2gltf */
    String toolId();

    /** 兼容的源文件后缀（小写，不含点），如 osgb 模块返回 ["osgb"]，点云模块返回 ["las","laz"] */
    Set<String> supportedSrcFormats();

    /** 构建完整命令行（不含 shell，参数列表方式执行，天然跨平台） */
    List<String> buildCommand(ConvertTask task, ConvertToolConfig tool,
                              Path workDir, Path inputFile, Path outputDir);

    /**
     * 转换前从输入数据提取元数据（持久化到业务表）。
     * key 约定：srs / originX / originY / originZ（OSGB 局部原点）。
     * 各工具链自行实现，业务层只做通用键值存储，不感知格式细节。
     */
    default java.util.Map<String, String> extractMetadata(ConvertTask task, Path inputBase) {
        return java.util.Map.of();
    }

    /**
     * 从子进程输出行解析进度百分比（0-100），无法解析返回 null。
     * 默认实现：匹配行内第一个 0~100 的百分数。
     */
    default Integer parseProgress(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        Matcher m = Pattern.compile("(\\d{1,3})\\s*%").matcher(line);
        if (m.find()) {
            int v = Integer.parseInt(m.group(1));
            return (v >= 0 && v <= 100) ? v : null;
        }
        return null;
    }
}
