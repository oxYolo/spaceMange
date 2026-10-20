package com.whulri.geodata.datamanage.util;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;

/**
 * 字符串压缩工具类（Gzip + Base64）
 */
public class CompressionUtils {

    /**
     * 将字符串压缩为 Gzip 并编码为 Base64 字符串
     *
     * @param str 待压缩的原始字符串（支持中文/Emoji）
     * @return Base64 编码的 Gzip 压缩数据
     * @throws IllegalArgumentException 当输入为 null 时抛出
     */
    public static String compressString(String str) {
        if (str == null) {
            throw new IllegalArgumentException("Input string must not be null");
        }

        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
             GZIPOutputStream gzipStream = new GZIPOutputStream(byteStream)) {

            // 1. 字符串 → UTF-8 字节数组
            byte[] inputBytes = str.getBytes(StandardCharsets.UTF_8);

            // 2. Gzip 压缩
            gzipStream.write(inputBytes);
            gzipStream.finish(); // 确保压缩完成（重要！）

            // 3. 获取压缩后的字节数组
            byte[] compressedBytes = byteStream.toByteArray();

            // 4. 转为 Base64 字符串
            return Base64.getEncoder().encodeToString(compressedBytes);

        } catch (Exception e) {
            throw new RuntimeException("Failed to compress string", e);
        }
    }
}
