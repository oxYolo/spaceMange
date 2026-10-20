package com.whulri.geodata.common.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FileHelper {
    public static final int SIZE = 1024;  // 1048576
    public static final double SIZED = 1024.0;
    public static final long SIZE_L = 1024L;

    public static File getFileInfo(String path) {
        return new File(path);
    }

    /**
     * 文件类型枚举
     */
    public enum FileEnum {
        VECTOR(1, "矢量"),
        GRID(2, "网格"),
        TEXT(3, "文本"),
        TIN(4, "三角网"),
        MODEL_3D(5, "立体模型"),
        VIDEO(6, "视频"),
        MATRIX(7, "矩阵"),
        DATA_TABLE(8, "数据表"),
        IMAGE(9, "影像");

        private final int value;
        private final String name;

        FileEnum(int value, String name) {
            this.value = value;
            this.name = name;
        }

        public int getValue() {
            return value;
        }

        public String getName() {
            return name;
        }
    }

    /**
     * 文件编码工具类
     */
    public static class FileEncoding {
        /**
         * 给定文件的路径，读取文件的二进制数据，判断文件的编码类型
         * @param fileName 文件路径
         * @return 文件的编码类型
         */
        public static Charset getType(String fileName) {
            try (FileInputStream fs = new FileInputStream(fileName)) {
                return getType(fs);
            } catch (IOException e) {
                return Charset.defaultCharset();
            }
        }

        /**
         * 通过给定的文件流，判断文件的编码类型
         * @param fs 文件流
         * @return 文件的编码类型
         */
        public static Charset getType(FileInputStream fs) {
            try {
                byte[] unicode = new byte[]{(byte) 0xFF, (byte) 0xFE, 0x41};
                byte[] unicodeBig = new byte[]{(byte) 0xFE, (byte) 0xFF, 0x00};
                byte[] utf8 = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}; // 带BOM

                byte[] bytes = fs.readAllBytes();

                if (isUTF8Bytes(bytes) || (bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF)) {
                    return StandardCharsets.UTF_8;
                } else if (bytes[0] == (byte) 0xFE && bytes[1] == (byte) 0xFF && bytes[2] == 0x00) {
                    return StandardCharsets.UTF_16BE;
                } else if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE && bytes[2] == 0x41) {
                    return StandardCharsets.UTF_16LE;
                }

                return Charset.defaultCharset();
            } catch (IOException e) {
                return Charset.defaultCharset();
            }
        }

        /**
         * 判断是否是不带 BOM 的 UTF8 格式
         * @param data 字节数组
         * @return 是否是UTF8格式
         */
        private static boolean isUTF8Bytes(byte[] data) {
            int charByteCounter = 1; // 计算当前正分析的字符应还有的字节数
            byte curByte; // 当前分析的字节

            for (byte datum : data) {
                curByte = datum;
                if (charByteCounter == 1) {
                    if (curByte >= 0x80) {
                        // 判断当前
                        while (((curByte <<= 1) & 0x80) != 0) {
                            charByteCounter++;
                        }
                        // 标记位首位若为非0 则至少以2个1开始 如:110XXXXX...........1111110X
                        if (charByteCounter == 1 || charByteCounter > 6) {
                            return false;
                        }
                    }
                } else {
                    // 若是UTF-8 此时第一位必须为1
                    if ((curByte & 0xC0) != 0x80) {
                        return false;
                    }
                    charByteCounter--;
                }
            }

            if (charByteCounter > 1) {
                throw new RuntimeException("非预期的byte格式");
            }
            return true;
        }
    }

    public static boolean zipDirectory(String sourceDirPath, String zipFilePath) throws IOException {
        Path zipFile = Files.createFile(Paths.get(zipFilePath));
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            Path sourcePath = Paths.get(sourceDirPath);
            Files.walk(sourcePath)
                .filter(path -> !Files.isDirectory(path))
                .forEach(path -> {
                    ZipEntry zipEntry = new ZipEntry(sourcePath.relativize(path).toString());
                    try {
                        zipOutputStream.putNextEntry(zipEntry);
                        Files.copy(path, zipOutputStream);
                        zipOutputStream.closeEntry();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        }
        return true;
    }

    public static void copyFile(File source, File dest) throws IOException {
        Files.copy(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

	/**
	 * 压缩文件
	 * @param dirToZip
	 * @param zippedFileName
	 * @param compressionLevel
	 * @return
	 */
	public static boolean zipDir(String dirToZip, String zippedFileName, int compressionLevel) {
		try {
			if (!zippedFileName.endsWith(".zip")) {
				zippedFileName += ".zip";
			}
			try (FileOutputStream fos = new FileOutputStream(zippedFileName);
				 ZipOutputStream zipOut = new ZipOutputStream(fos)) {
				zipOut.setLevel(compressionLevel);
				Map<String, FileTime> fileList = getAllFiles(dirToZip);
				int unit = 10 * 1024 * 1024;
				for (Map.Entry<String, FileTime> entry : fileList.entrySet()) {
					File file = new File(entry.getKey());
					try (FileInputStream fis = new FileInputStream(file)) {
						ZipEntry zipEntry = new ZipEntry(file.getName());
						zipEntry.setLastModifiedTime(entry.getValue());
						zipEntry.setSize(file.length());
						zipOut.putNextEntry(zipEntry);
						byte[] buffer = new byte[unit];
						int bytesRead;
						while ((bytesRead = fis.read(buffer)) != -1) {
							zipOut.write(buffer, 0, bytesRead);
						}
						zipOut.closeEntry();
					}
				}
			}
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	/**
	 * 获取所有文件
	 * @param dirPath
	 * @return
	 * @throws IOException
	 */
	public static Map<String, FileTime> getAllFiles(String dirPath) throws IOException {
		Map<String, FileTime> fileMap = new HashMap<>();
		File fileDir = new File(dirPath);
		if (!fileDir.exists() || !fileDir.isDirectory()) {
			throw new IOException("目录：" + fileDir.getAbsolutePath() + " 没有找到!");
		}
		getAllDirFiles(fileDir, fileMap);
		return fileMap;
	}

	/**
	 * 获取一个文件夹下的所有文件夹里的文件
	 * @param dir
	 * @param fileMap
	 * @throws IOException
	 */
	private static void getAllDirFiles(File dir, Map<String, FileTime> fileMap) throws IOException {
		for (File file : Objects.requireNonNull(dir.listFiles(File::isFile))) {
			fileMap.put(file.getAbsolutePath(), Files.getLastModifiedTime(file.toPath()));
		}
		for (File subDir : Objects.requireNonNull(dir.listFiles(File::isDirectory))) {
			getAllDirFiles(subDir, fileMap);
		}
	}
}
