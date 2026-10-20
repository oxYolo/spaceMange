package com.whulri.geodata.convert.core;

import lombok.Data;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * 单个转换工具的配置（对应配置键 geodata.convert.tools.&lt;toolId&gt;）。
 *
 * <p>跨平台约定：win-bin-path / linux-bin-path 按运行操作系统二选一生效；
 * command-template 中可用的占位符：{bin} 可执行文件、{input} 输入、{output} 输出、{extra} 附加参数。</p>
 */
@Data
public class ConvertToolConfig {

    /** 是否启用 */
    private boolean enabled = true;

    /** Windows 下可执行文件（或解释器）路径 */
    private String winBinPath;

    /** Linux 下可执行文件（或解释器）路径 */
    private String linuxBinPath;

    /**
     * 命令模板。以空格分词（支持双引号包裹含空格的路径），分词后再做占位符替换。
     * 示例："{bin} -f osgb -i {input} -o {output} {extra}"
     */
    private String commandTemplate;

    /** 附加参数（追加到 {extra} 位置，按空格分词） */
    private String extraArgs = "";

    /** 子进程输出字符集（Windows 下部分工具输出 GBK，可按工具覆盖） */
    private String outputCharset = "UTF-8";

    /** 单次转换超时（分钟） */
    private long timeoutMinutes = 240;

    public Charset charset() {
        try {
            return Charset.forName(outputCharset == null || outputCharset.isBlank()
                    ? "UTF-8" : outputCharset);
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }

    public Duration timeout() {
        return Duration.ofMinutes(timeoutMinutes <= 0 ? 240 : timeoutMinutes);
    }

    /** 附加参数分词结果（空则返回空列表） */
    public List<String> extraArgTokens() {
        if (extraArgs == null || extraArgs.isBlank()) {
            return Collections.emptyList();
        }
        String[] arr = extraArgs.trim().split("\\s+");
        return List.of(arr);
    }
}
