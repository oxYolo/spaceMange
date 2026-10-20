package com.whulri.geodata.convert.core;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 跨平台工具：操作系统识别 + 按平台解析转换工具可执行文件路径。
 *
 * <p>约定：ConvertToolConfig 中 win-bin-path / linux-bin-path 分别配置 Windows 与 Linux
 * 下的可执行文件（或解释器）路径，运行时按当前操作系统自动选择，不混用。</p>
 */
public final class Platform {

    private Platform() {
    }

    public static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win");
    }

    public static boolean isLinux() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("linux") || os.contains("unix") || os.contains("aix");
    }

    /**
     * 按当前操作系统解析工具可执行路径；未配置或文件不存在时抛 ConvertException。
     *
     * @param toolId     工具标识（用于报错信息）
     * @param winBinPath Windows 下可执行路径
     * @param linuxBinPath Linux 下可执行路径
     */
    public static String resolveBinary(String toolId, String winBinPath, String linuxBinPath) {
        boolean win = isWindows();
        String raw = win ? winBinPath : linuxBinPath;
        if (raw == null || raw.isBlank()) {
            throw new ConvertException(String.format(
                    "转换工具 [%s] 未配置%s下的可执行路径（%s）",
                    toolId, win ? "Windows" : "Linux", win ? "win-bin-path" : "linux-bin-path"));
        }
        // 允许 PATH 上的裸命令（如 npx / python3）：仅当包含路径分隔符时校验文件存在性
        Path p = Paths.get(raw.trim());
        if (p.getNameCount() > 1 && !Files.isExecutable(p) && !Files.isRegularFile(p)) {
            File f = p.toFile();
            if (!f.exists()) {
                throw new ConvertException(String.format(
                        "转换工具 [%s] 的可执行文件不存在：%s", toolId, p.toAbsolutePath()));
            }
        }
        return p.toAbsolutePath().toString();
    }
}
