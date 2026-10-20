package com.whulri.geodata.common.util;

import cn.hutool.core.util.ZipUtil;
import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;
import com.whulri.geodata.common.bridge.Func;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 该类中部分方法可以使用hutool的zipUtil替代
 */
@Slf4j
public class ZipHelper {

    private String sourceFolderPath;

    public ZipHelper(String sourceFolderPath) {
        this.sourceFolderPath = sourceFolderPath;
    }

    public ZipHelper() {
    }

    /**
     * 压缩目录
     *
     * @param zipFilePath
     * @throws IOException
     */
    public void zipFolder(String zipFilePath) {

        Path sourcePath = Paths.get(sourceFolderPath);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(Paths.get(zipFilePath)))) {
            Files.walk(sourcePath).forEach(path -> {
                try {
                    String relativePath = sourcePath.relativize(path).toString().replace("\\", "/");
                    ZipEntry zipEntry = new ZipEntry(relativePath);
                    zos.putNextEntry(zipEntry);
                    Files.copy(path, zos);
                    zos.closeEntry();
                } catch (IOException e) {
                    log.error("压缩目录失败", e);
                }
            });
        } catch (Exception e) {
            log.error("读取目录失败", e);
            throw new RuntimeException("压缩文件失败");
        }
    }

    /**
     * 压缩指定目录下的所有文件到一个ZIP文件。
     *
     * @param dirToZip         要压缩的目录路径
     * @param zipedFileName    输出的ZIP文件名
     * @param compressionLevel 压缩级别，默认为9
     * @return 操作是否成功
     */
    public static boolean zipDir(String dirToZip, String zipedFileName, int compressionLevel) {
        try {
            if (!zipedFileName.endsWith(".zip")) {
                zipedFileName += ".zip";
            }
            File zip = ZipUtil.zip(dirToZip, zipedFileName);
            log.info("文件:{}压缩成功\n 至{}", dirToZip, zipedFileName);
            return true;
        } catch (Exception e) {
            log.error("压缩指定目录下的所有文件到一个ZIP文件 失败", e);
            return false;
        }
    }


    public static Map<String, Date> getAllFiles(String dir) throws IOException {
        File fileDir = new File(dir);
        if (!fileDir.exists()) {
            throw new FileNotFoundException("Directory: " + fileDir.getAbsolutePath() + " not found!");
        }
        Map<String, Date> filesList = new Hashtable<>();
        addFilesToList(fileDir, filesList);
        return filesList;
    }

    private static void addFilesToList(File dir, Map<String, Date> filesList) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    addFilesToList(file, filesList);
                } else {
                    filesList.put(file.getAbsolutePath(), new Date(file.lastModified()));
                }
            }
        }
    }

    public boolean unzip(String zipFilePath, String unzipDir) {
        try {
            if (zipFilePath.isEmpty()) {
                throw new IllegalArgumentException("Zip file path cannot be empty!");
            }
            if (!new File(zipFilePath).exists()) {
                throw new FileNotFoundException("Zip file does not exist!");
            }
            if (unzipDir.isEmpty()) {
                unzipDir = zipFilePath.substring(0, zipFilePath.lastIndexOf("."));
            }
            if (!unzipDir.endsWith("/")) {
                unzipDir += "/";
            }
            if (!new File(unzipDir).exists()) {
                new File(unzipDir).mkdirs();
            }
            try (ZipArchiveInputStream zipIn = new ZipArchiveInputStream(Files.newInputStream(Paths.get(zipFilePath)))) {
                ZipArchiveEntry entry;
                while ((entry = zipIn.getNextZipEntry()) != null) {
                    String dirName = new File(entry.getName()).getParent();
                    String fileName = new File(entry.getName()).getName();
                    String filePath = unzipDir;
                    if (dirName != null && !dirName.isEmpty()) {
                        filePath += dirName;
                        new File(filePath).mkdirs();
                    }
                    if (!fileName.isEmpty()) {
                        try (FileOutputStream fos = new FileOutputStream(filePath + "/" + fileName)) {
                            IOUtils.copy(zipIn, fos);
                        }
                    }
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 解压zip格式的文件
     *
     * @param zipFilePath 压缩文件路径
     * @param unZipDir    解压文件存放路径,为空时默认与压缩文件同一级目录下，跟压缩文件同名的文件夹
     * @return 解压是否成功
     */
    public static boolean unZip(String zipFilePath, String unZipDir) {
        try {
            if (zipFilePath == null || zipFilePath.isEmpty()) {
                throw new IllegalArgumentException("压缩文件不能为空！");
            }
            if (!new File(zipFilePath).exists()) {
                throw new IllegalArgumentException("压缩文件不存在！");
            }

            // 如果解压目录为空，使用压缩文件同级目录下同名文件夹
            if (unZipDir == null || unZipDir.isEmpty()) {
                String fileName = new File(zipFilePath).getName();
                String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
                unZipDir = zipFilePath.replace(fileName, baseName);
            }

            // 确保目录以分隔符结尾
            if (!unZipDir.endsWith(File.separator)) {
                unZipDir += File.separator;
            }

            // 创建目标目录
            File destDir = new File(unZipDir);
            if (!destDir.exists()) {
                destDir.mkdirs();
            }

            // 使用zip4j解压
            new ZipFile(zipFilePath).extractAll(unZipDir);
            return true;
        } catch (ZipException e) {
            log.warn("解压失败: file={}, err={}", zipFilePath, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 压缩单个文件
     *
     * @param filePath         被压缩的文件名称(包含文件路径)，文件的全路径
     * @param zipedFileName    压缩后的文件名称(包含文件路径)，保存的文件名称
     * @param compressionLevel 压缩率0（无压缩）到 9（压缩率最高）
     */
    public void zipFile(String filePath, String zipedFileName, CompressionLevel compressionLevel) throws FileNotFoundException {

        // 检查文件是否存在
        File file = new File(filePath);
        if (!file.exists()) {
            throw new FileNotFoundException("文件：" + filePath + "没有找到！");
        }

        // 如果压缩后名字为空就默认使用源文件名称作为压缩文件名称
        if (zipedFileName == null || zipedFileName.isEmpty()) {
            zipedFileName = filePath.substring(0, filePath.lastIndexOf(File.separator)) + File.separator + file.getName() + ".zip";
        }

        // 如果压缩后的文件名称后缀名不是zip，就加上zip
        if (!zipedFileName.endsWith(".zip")) {
            zipedFileName += ".zip";
        }

        // 创建目录如果不存在
        File zipDir = new File(zipedFileName).getParentFile();
        if (zipDir != null && !zipDir.exists()) {
            boolean mkdirFlag = zipDir.mkdirs();
            log.debug("文件目录:{}不存在 创建标识:{}", zipedFileName, mkdirFlag);
        }

        // 使用Zip4j创建ZipFile实例
        try (ZipFile zipFile = new ZipFile(zipedFileName)) {

            ZipParameters parameters = new ZipParameters();
            // 设置压缩级别
            parameters.setCompressionMethod(CompressionMethod.DEFLATE);
            // 设置压缩级别
            parameters.setCompressionLevel(compressionLevel);
            // 执行压缩操作
            zipFile.addFile(file, parameters);

            log.info("文件{}压缩成功", filePath);
        } catch (Exception e) {
            log.error("文件{}压缩失败", filePath, e);
        }
    }

    /**
     * 压缩多个目录或文件
     *
     * @param folderOrFileList 待压缩的目录或文件列表
     * @param zipFile          压缩后文件的路径
     * @param pwd              密码
     * @return 操作是否成功
     */
    public boolean zipManyFileNoPassword(List<String> folderOrFileList, String zipFile, String pwd) {
        boolean res = true;

        try {

            for (String fileOrDir : folderOrFileList) {
                Path path = Paths.get(fileOrDir);

                // 判断是否为目录
                if (Files.isDirectory(path)) {
                    res = zipFileDirectory(fileOrDir, zipFile, pwd);
                } else {
                    // 文件
                    res = zipFileWithStream(fileOrDir, zipFile, pwd);
                }

                if (!res) {
                    break; // 如果压缩过程中出现错误，立即停止
                }
            }

        } catch (Exception e) {
            res = false;
            log.error("压缩文件失败", e); // 异常处理，这里简单地打印堆栈跟踪
        }

        return res;
    }

    public static boolean zipManyFilesOrDirectories(List<String> folderOrFileList, String zippedFile, String password) {
        ZipParameters parameters = new ZipParameters();
        parameters.setCompressionLevel(CompressionLevel.NORMAL);
        if (password != null && !password.isEmpty()) {
            parameters.setEncryptFiles(true);
            parameters.setEncryptionMethod(EncryptionMethod.ZIP_STANDARD); // ZIP_STANDARD is compatible with most tools
        }

        try {
            if (password == null || password.isEmpty()) {
                // No password set
                try (ZipFile zipFile = new ZipFile(zippedFile)) {
                    addFilesToZip(zipFile, folderOrFileList, parameters);
                }
            } else {
                // Password is set
                try (ZipFile zipFile = new ZipFile(zippedFile, password.toCharArray())) {
                    addFilesToZip(zipFile, folderOrFileList, parameters);
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void addFilesToZip(ZipFile zipFile, List<String> folderOrFileList, ZipParameters parameters) throws ZipException {
        for (String fileOrDir : folderOrFileList) {
            File file = new File(fileOrDir);
            if (file.isDirectory()) {
                zipFile.addFolder(file, parameters);
            } else {
                zipFile.addFile(file, parameters);
            }
        }
    }

    /**
     * 使用带压缩流压缩单个文件
     *
     * @param fileToZip 要进行压缩的文件名
     * @param zipFile   压缩路径
     * @param password  密码
     * @return 操作是否成功
     */
    private boolean zipFileWithStream(String fileToZip, String zipFile, String password) {
        File file = new File(fileToZip);

        // 如果文件不存在，则抛出异常
        if (!file.exists()) {
            log.error("指定要压缩的文件: {} 不存在!", fileToZip);
        }

        // 创建一个ZipParameters对象，这是用来存储压缩参数的
        ZipParameters params = new ZipParameters();
        params.setCompressionLevel(CompressionLevel.HIGHER);

        // 创建ZipFile对象，指定输出ZIP文件的路径
        try (ZipFile zipFileObj = new ZipFile(zipFile)) {


            if (Func.isNotEmpty(password)) {
                params.setEncryptFiles(true);
                params.setEncryptionMethod(EncryptionMethod.NONE);
                zipFileObj.setPassword(password.toCharArray());
            }

            if (file.isDirectory()) {
                zipFileObj.addFolder(file, params);
            } else {
                zipFileObj.addFile(file, params);
            }
            log.info("文件{}已成功压缩并加密", fileToZip);
            return true;
        } catch (Exception e) {
            log.error("文件{}压缩或加密失败", fileToZip, e);
            return false;
        }
    }

    /**
     * 递归压缩文件夹方法
     *
     * @param folderToZip 要压缩的文件夹路径
     * @return 如果压缩成功返回true，否则返回false
     */
    public static boolean zipFileDirectory(String folderToZip, String zipFile, String password) {
        Path folderPath = Paths.get(folderToZip);
        ZipParameters params = new ZipParameters();
        params.setCompressionLevel(CompressionLevel.HIGHER);
        try (ZipFile zip = new ZipFile(zipFile);
             Stream<Path> walk = Files.walk(folderPath)
        ) {
            walk.filter(Files::isRegularFile)
                    .forEach(filePath -> {
                        try {
                            ZipEntry entry = new ZipEntry(folderPath.relativize(filePath).toString());
                            entry.setLastModifiedTime(FileTime.from(Instant.now().truncatedTo(ChronoUnit.SECONDS)));
                            entry.setSize(Files.size(filePath));
                            CRC32 crc = new CRC32();
                            crc.update(Files.readAllBytes(filePath));
                            entry.setCrc(crc.getValue());

                            if (Func.isNotEmpty(password)) {
                                params.setEncryptFiles(true);
                                params.setEncryptionMethod(EncryptionMethod.NONE);
                                zip.setPassword(password.toCharArray());
                            }

                            zip.setPassword(password.toCharArray());
                            zip.addFile(filePath.toFile(), params);
                        } catch (Exception e) {
                            log.error("递归处理文件时出现错误: " + e.getMessage());
                        }
                    });

        } catch (Exception e) {
            log.error("ZipFileDirectory出现错误: " + e.getMessage());
            return false;
        }

        return true;
    }

    /**
     * 压缩单个文件
     *
     * @param tifPath 要压缩的文件路径
     * @return 压缩后ZIP文件的路径
     */
    public static String YSZip(String tifPath) {
        // 获取原始文件的路径和文件名（不带扩展名）
        Path originalPath = Paths.get(tifPath);
        String tifName = originalPath.getFileName().toString().replaceAll("[.][^.]+?$", "");
        String tifFilePath = originalPath.getParent().toString();

        // 构建压缩后的文件路径
        String tifZipPath = Paths.get(tifFilePath, tifName + ".zip").toString();

        // 调用压缩方法对单个文件进行压缩
        zipSingleFile(tifPath, tifZipPath);

        // 删除原文件（如果它未被其他进程使用）
        File originalFile = new File(tifPath);
        if (originalFile.exists() && isFileNotUse(tifPath)) {
            originalFile.delete();
        }

        return tifZipPath;
    }

    /**
     * 压缩单个文件到指定的ZIP文件
     *
     * @param filePath 要压缩的文件路径
     * @param zipPath  输出的ZIP文件路径
     */
    private static void zipSingleFile(String filePath, String zipPath) {
        try {
            // 创建源文件对象
            File fileToZip = new File(filePath);
            // 创建目标ZIP文件对象
            File zipFile = new File(zipPath);

            // 创建ZipParameters对象并设置参数
            ZipParameters parameters = new ZipParameters();
            parameters.setCompressionMethod(CompressionMethod.DEFLATE); // 使用DEFLATE算法
            parameters.setCompressionLevel(CompressionLevel.NORMAL); // 设置压缩级别

            // 创建一个新的ZIP文件或向已存在的ZIP文件添加内容
            ZipFile zip = new ZipFile(zipFile);

            // 添加文件到ZIP
            zip.addFile(fileToZip, parameters);

            System.out.println("文件压缩成功：" + zipFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("zipSingleFile出现异常", e);
        }
    }

    /**
     * 压缩多个文件
     *
     * @param tifPathStr 要压缩的文件路径
     * @return 压缩后ZIP文件的路径
     */
    public static String YSDZip(String tifPathStr) {
        Path tifPath = Paths.get(tifPathStr);
        Path path = tifPath.getParent();
        String fileName = tifPath.getFileName().toString().replaceAll("[.][^.]+?$", "");

        // 创建一个文件列表，包含要压缩的所有相关文件
        List<String> fileList = new ArrayList<String>() {{
            add(path.resolve(fileName + ".shp").toString());
            add(path.resolve(fileName + ".shx").toString());
            add(path.resolve(fileName + ".prj").toString());
            add(path.resolve(fileName + ".dbf").toString());
        }};

        String zipFilePath = path.resolve(fileName + ".zip").toString();

        // 使用ZipHelper类的静态方法来压缩文件列表中的所有文件
        // 注意：Java中没有直接等价于C#的ZipHelper类，这里使用Apache Commons Compress库实现
        zipFiles(fileList, zipFilePath);

        // 删除已压缩的原文件，前提是它们未被其他进程使用
        for (String filePath : fileList) {
            File file = new File(filePath);
            if (file.exists() && isFileNotUse(filePath)) {
                file.delete();
            }
        }

        return zipFilePath;
    }

    private static void zipFiles(List<String> filePaths, String zipFilePath) {
        try {
            // 创建目标ZIP文件对象
            File zipFile = new File(zipFilePath);

            // 创建ZipParameters对象并设置参数
            ZipParameters parameters = new ZipParameters();
            parameters.setCompressionMethod(CompressionMethod.DEFLATE); // 使用DEFLATE算法
            parameters.setCompressionLevel(CompressionLevel.NORMAL); // 设置压缩级别

            // 创建一个新的ZIP文件或向已存在的ZIP文件添加内容
            ZipFile zip = new ZipFile(zipFile);

            for (String filePath : filePaths) {
                File fileToAdd = new File(filePath);
                if (fileToAdd.exists() && fileToAdd.isFile()) {
                    zip.addFile(fileToAdd, parameters);
                } else {
                    System.out.println("忽略非文件或不存在的文件：" + filePath);
                }
            }

            System.out.println("文件压缩成功：" + zipFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("zipFiles出现异常", e);
        }
    }

    /**
     * 判断文件是否被占用
     *
     * @param fileName 文件名全路径
     * @return 是否被占用（true 表示未被占用，false 表示被占用）
     */
    public static boolean isFileNotUse(String fileName) {
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "rw");
             FileChannel fc = raf.getChannel()) {

            // 尝试获取非阻塞的独占锁
            try {
                FileLock lock = fc.tryLock();
                if (lock == null) {
                    // 未能获取锁，文件被占用
                    return false;
                } else {
                    // 成功获取锁，文件未被占用
                    lock.release(); // 释放锁
                    return true;
                }
            } catch (OverlappingFileLockException e) {
                // 文件已经被其他地方锁定
                return false;
            }
        } catch (Exception e) {
            log.error("判断文件是否被占用时出现异常", e);
            return false; // 出现异常，可能文件被占用
        }
    }

    /**
     * 压缩指定的文件夹及其所有子文件。
     *
     * @param sourceDirectory 源文件夹路径
     * @param outputFilePath  输出ZIP文件路径
     * @throws IOException 如果发生IO异常
     */
//    public static void compressDirectory(String sourceDirectory, String outputFilePath) throws IOException {
//        Path sourcePath = Paths.get(sourceDirectory);
//        if (!Files.isDirectory(sourcePath)) {
//            throw new IllegalArgumentException("The specified path is not a directory.");
//        }
//
//        try (ZipArchiveOutputStream zipOut = new ZipArchiveOutputStream(new FileOutputStream(outputFilePath))) {
//            compressDirectoryRecursive(zipOut, sourcePath, "");
//        }
//    }

    /**
     * 递归压缩文件夹及其所有子文件。
     *
     * @param zipOut     输出流
     * @param sourcePath 源文件夹路径
     * @param parent     相对于父目录的路径
     * @throws IOException 如果发生IO异常
     */
//    private static void compressDirectoryRecursive(ZipArchiveOutputStream zipOut, Path sourcePath, String parent) throws IOException {
//        File[] files = sourcePath.toFile().listFiles();
//        if (files == null) {
//            throw new IOException("Failed to list contents of " + sourcePath);
//        }
//
//        for (File fileEntry : files) {
//            if (fileEntry.isDirectory()) {
//                // 如果是目录，则递归调用
//                compressDirectoryRecursive(zipOut, fileEntry.toPath(), parent + fileEntry.getName() + "/");
//            } else {
//                // 如果是文件，则添加到ZIP输出流中
//                addFileToZip(zipOut, fileEntry.toPath(), parent, zipOut);
//            }
//        }
//    }

    /**
     * 将单个文件添加到ZIP输出流中。
     *
     * @param zipOut   ZIP输出流
     * @param filePath 文件路径
     * @param parent   相对于父目录的路径
     * @param zipOut   已经打开的ZIP输出流
     * @throws IOException 如果发生IO异常
     */
//    private static void addFileToZip(ZipArchiveOutputStream zipOut, Path filePath, String parent, ZipArchiveOutputStream zipOutStream) throws IOException {
//        ZipArchiveEntry zipEntry = new ZipArchiveEntry(filePath.toFile(), parent + filePath.getFileName());
//        zipOutStream.putArchiveEntry(zipEntry);
//        try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
//            IOUtils.copy(fis, zipOutStream);
//        }
//        zipOutStream.closeArchiveEntry();
//    }

}
