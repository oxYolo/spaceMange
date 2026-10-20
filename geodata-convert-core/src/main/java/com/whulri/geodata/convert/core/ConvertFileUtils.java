package com.whulri.geodata.convert.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * 转换过程通用文件工具（跨工具链共用，语言无关）
 */
public final class ConvertFileUtils {

    private ConvertFileUtils() {
    }

    /**
     * 在目录中查找第一个匹配指定后缀（小写、不含点）的常规文件，
     * 按「目录层级浅优先、同层级字典序」排序，最深扫描 4 层。
     */
    public static Path findFirstFile(Path dir, String... lowercaseExts) {
        try (Stream<Path> stream = Files.walk(dir, 4)) {
            return stream
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparingInt(p -> p.getNameCount()))
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        for (String ext : lowercaseExts) {
                            if (name.endsWith("." + ext)) {
                                return true;
                            }
                        }
                        return false;
                    })
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            throw new ConvertException("扫描文件失败：" + dir, e);
        }
    }
}
