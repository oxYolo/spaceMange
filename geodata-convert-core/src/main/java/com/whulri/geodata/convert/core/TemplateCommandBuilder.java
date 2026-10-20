package com.whulri.geodata.convert.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于命令模板的构建器骨架（各工具链模块继承使用）。
 *
 * <p>组装流程：双引号感知分词 → 逐 token 替换占位符（{bin}/{input}/{output}/{extra}），
 * 含空格的 Windows 路径不会被错误切分，参数以列表方式交给 ProcessBuilder，不经 shell。</p>
 */
public abstract class TemplateCommandBuilder implements ConvertCommandBuilder {

    @Override
    public List<String> buildCommand(ConvertTask task, ConvertToolConfig tool,
                                     Path workDir, Path inputFile, Path outputDir) {
        String template = tool == null ? null : tool.getCommandTemplate();
        List<String> tokens = tokenize(template);
        if (tokens.isEmpty()) {
            throw new ConvertException(String.format(
                    "转换工具 [%s] 未配置命令模板（geodata.convert.tools.%s.command-template）",
                    toolId(), toolId()));
        }
        String bin = Platform.resolveBinary(toolId(), tool.getWinBinPath(), tool.getLinuxBinPath());
        Path input = resolveInput(task, workDir, inputFile);
        Path output = resolveOutput(task, workDir, outputDir);

        List<String> command = new ArrayList<>();
        for (String t : tokens) {
            if ("{extra}".equals(t)) {
                command.addAll(tool.extraArgTokens());
            } else if ("{srs-args}".equals(t)) {
                // 源数据坐标系参数（py3dtiles 语法，模板按需引用）：
                //   --srs_in <EPSG数字>            输入坐标系（只认数字，"EPSG:4326" 须截取）
                //   --srs_out 4978                 输出统一为 ECEF（3D Tiles 规范要求，缺省时 transform
                //                                  会写成输入坐标原值——经纬度度数，Cesium 无法定位）
                //   --pyproj-always-xy             EPSG:4326 在 pyproj 默认轴序为 (纬,经)，必须强制 X=经度
                // 未登记 srs 时整体省略。其它工具链如需坐标系参数，另加占位符，勿复用本 token。
                if (task.getSrs() != null && !task.getSrs().isBlank()) {
                    String raw = task.getSrs().trim();
                    String num = raw.contains(":") ? raw.substring(raw.lastIndexOf(':') + 1) : raw;
                    command.add("--srs_in");
                    command.add(num);
                    command.add("--srs_out");
                    command.add("4978");
                    command.add("--pyproj-always-xy");
                }
            } else {
                command.add(replacePlaceholder(t, bin, input.toString(), output.toString()));
            }
        }
        return command;
    }

    /** 输入路径，默认直接使用解压/落盘后的输入；工具链可覆盖（如扫描特定后缀文件） */
    protected Path resolveInput(ConvertTask task, Path workDir, Path inputFile) {
        return inputFile;
    }

    /** 输出路径，默认为输出目录；产出单文件的工具链可覆盖为具体文件路径 */
    protected Path resolveOutput(ConvertTask task, Path workDir, Path outputDir) {
        return outputDir;
    }

    /** 双引号感知分词 */
    protected List<String> tokenize(String template) {
        List<String> tokens = new ArrayList<>();
        if (template == null || template.isBlank()) {
            return tokens;
        }
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (char c : template.toCharArray()) {
            if (c == '"') {
                inQuote = !inQuote;
            } else if (c == ' ' && !inQuote) {
                if (cur.length() > 0) {
                    tokens.add(cur.toString());
                    cur.setLength(0);
                }
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) {
            tokens.add(cur.toString());
        }
        return tokens;
    }

    protected String replacePlaceholder(String token, String bin, String input, String output) {
        return token
                .replace("{bin}", bin)
                .replace("{input}", input)
                .replace("{output}", output);
    }
}
