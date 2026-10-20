package com.whulri.geodata.common.io;

import java.io.File;

/**
 * 路径工具（反编译自 dev-common-1.1.8，签名与原实现保持一致）
 */
public class Path {

    private Path() {
    }

    public static String getFileName(String path) {
        int lastSeparator = Path.getLastSeparator(path);
        int end = path.length();
        if (lastSeparator != -1 && end != -1 && lastSeparator <= end) {
            return path.substring(lastSeparator + 1, end);
        }
        return path;
    }

    public static String getFileNameWithoutExtension(String path) {
        int end;
        int lastSeparator = Path.getLastSeparator(path);
        if (lastSeparator <= (end = path.lastIndexOf("."))) {
            return path.substring(lastSeparator + 1, end);
        }
        return null;
    }

    public static String changeExtension(String path, String newExt) {
        int end;
        int lastSeparator = path.lastIndexOf(File.separator);
        if (lastSeparator <= (end = path.lastIndexOf("."))) {
            return path.substring(0, end) + newExt;
        }
        return null;
    }

    public static String getExtension(String path) {
        int index = path.lastIndexOf(".");
        if (index == -1) {
            return "";
        }
        return path.substring(index + 1);
    }

    public static String getDirectoryName(String path) {
        int lastSeparator = Path.getLastSeparator(path);
        if (-1 == lastSeparator) {
            return "";
        }
        return path.substring(0, lastSeparator);
    }

    public static String combine(String... paths) {
        if (paths.length == 1) {
            return paths[0];
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < paths.length; i++) {
            String path = paths[i];
            result.append(path);
            if (i == paths.length - 1) {
                continue;
            }
            result.append(File.separator);
        }
        return result.toString();
    }

    private static int getLastSeparator(String path) {
        int index1 = path.lastIndexOf("/");
        int index2 = path.lastIndexOf("\\");
        return Math.max(index1, index2);
    }
}
