package com.whulri.geodata.web.controller;

import com.whulri.geodata.common.config.AppSettingConfig;
import com.whulri.geodata.common.dto.FileUploadDto;
import com.whulri.geodata.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文件上传
 */
@RestController
@RequestMapping("/upload")
@Slf4j
public class UploadController {

    @Resource
    private AppSettingConfig appSettingConfig;

    /**
     * 分片上传文件
     *
     * @return
     */
    @PostMapping("/UploadForBreakpoint/AddFiles")
    @Operation(summary = "上传文件", description = "传入文件")
    public Result<?> addFiles(@RequestParam("file") MultipartFile file,
                              @RequestParam("chunk") int chunk,
                              @RequestParam("guid") String guid) {
        if (file.isEmpty()) {
            return Result.fail(500, "文件为空");
        }

        String fileName = guid + "_" + file.getOriginalFilename().replace(".", "_");
        Path filePath = Paths.get(System.getProperty("user.dir"), appSettingConfig.getUploadFilePath(), "files", fileName);

        try {
            // 创建分片存储目录
            Files.createDirectories(filePath);

            // 分片文件存储路径
            Path chunkPath = filePath.resolve(String.valueOf(chunk));
            try (OutputStream os = Files.newOutputStream(chunkPath, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                file.getInputStream().transferTo(os);
            }

            return Result.success("分片上传成功");
        } catch (IOException e) {
            log.error("分片写入失败: chunk={}, err={}", chunk, e.getMessage(), e);
            return Result.fail(500, e.getMessage());
        }
    }

    /**
     * 查询已上传分片号（升序，从 1 开始），供断点续传跳过已传分片
     *
     * @param guid     上传会话标识
     * @param fileName 原始文件名
     * @return 已上传分片号列表
     */
    @GetMapping("/UploadForBreakpoint/Parts")
    @Operation(summary = "已上传分片", description = "返回分片目录中已存在的分片号（升序，从 1 开始）")
    public Result<?> parts(@RequestParam("guid") String guid,
                           @RequestParam("fileName") String fileName) {
        String dirName = guid + "_" + fileName.replace(".", "_");
        Path dir = Paths.get(System.getProperty("user.dir"), appSettingConfig.getUploadFilePath(), "files", dirName);
        List<Integer> parts = new ArrayList<>();
        if (Files.isDirectory(dir)) {
            try (var stream = Files.list(dir)) {
                stream.map(p -> p.getFileName().toString())
                        .filter(n -> !n.isEmpty() && n.chars().allMatch(Character::isDigit))
                        .map(Integer::parseInt)
                        .sorted()
                        .forEach(parts::add);
            } catch (IOException e) {
                return Result.fail(500, e.getMessage());
            }
        }
        return Result.data(parts);
    }

    @PostMapping("/UploadForBreakpoint/FileMerge")
    @Operation(summary = "文件合并", description = "文件合并")
    public Result<?> fileMerge(@RequestParam("fileName") String fileName,
                               @RequestParam("guid") String guid) {
        String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());

//        String fileName = request.getParameter("fileName");
        guid = guid + "_" + fileName.replace(".", "_");
        Path path = Paths.get(System.getProperty("user.dir"), appSettingConfig.getUploadFilePath(), "files", guid);
        Path physicalPath = Paths.get(System.getProperty("user.dir"), appSettingConfig.getUploadFilePath(), "files", fileName); // 文件的物理路径

        // 如果存在则覆盖写入
        try (OutputStream os = Files.newOutputStream(physicalPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            // 创建或清空目标文件
            // 这里排序一定要正确，转成数字后排序（字符串会按1 10 11排序，默认10比2小）
            List<Path> filePaths;
            try (var dirStream = Files.list(path)) {
                filePaths = dirStream
                        .sorted(Comparator.comparingInt(t -> Integer.parseInt(t.getFileName().toString().replaceAll("\\D", ""))))
                        .collect(Collectors.toList());
            }

            for (Path filePath : filePaths) {
                byte[] bytes = Files.readAllBytes(filePath);
                try (OutputStream fs = Files.newOutputStream(physicalPath, StandardOpenOption.APPEND)) {
                    fs.write(bytes);
                }
                Files.delete(filePath);
            }
            Files.delete(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        // COPY文件生成网络路径
        File fi = physicalPath.toFile();
        String fileExtension = getFileExtension(fi.getName());
        String newName = fi.getName(); // 使用原始文件名
        Path uploadDir = Paths.get(System.getProperty("user.dir"), appSettingConfig.getUploadFilePath());
        if (!Files.exists(uploadDir)) {

            try {
                Files.createDirectories(uploadDir);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        }
        Path newFile = uploadDir.resolve(newName);

        try {
            Files.copy(physicalPath, newFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // 返回文件信息
        FileUploadDto uploadDto = new FileUploadDto();
        uploadDto.setFileGuid(guid);
        uploadDto.setExtension(fileExtension);
        uploadDto.setFileSize(String.valueOf(fi.length()));
        uploadDto.setPhysicalName(physicalPath.toString());
        uploadDto.setLogicName(newName);
//        uploadDto.setHttpPath(request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + "/"
//                + newFile.toString().replace(System.getProperty("user.dir"), "").replace("\\", "/"));
        return Result.data(uploadDto);
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int lastDotIndex = fileName.lastIndexOf(".");
        if (lastDotIndex == -1) {
            return "";
        }
        return fileName.substring(lastDotIndex + 1);
    }
}
