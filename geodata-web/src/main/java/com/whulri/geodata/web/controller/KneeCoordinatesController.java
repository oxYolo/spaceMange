package com.whulri.geodata.web.controller;

import com.whulri.geodata.common.result.Result;
import com.whulri.geodata.common.util.ShpHelper;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.whulri.geodata.common.bridge.Func;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "上传坐标相关")
public class KneeCoordinatesController {

    @PostMapping("/Kneecoordinates/UploadShpFiles")
    public Result<?> uploadShpFiles(@RequestParam("file") List<MultipartFile> files,
                                    @RequestParam(value = "folderId", required = false) String folderId) {
        try {
            // 检查是否有文件上传
            if (files.isEmpty()) {
                return Result.fail("请选择需要上传的文件");
            }

            Thread.sleep(500); // 延迟500毫秒

            // 创建目录并保存文件
            Path directory;
            if (Func.isEmpty(folderId)) {
                directory = Paths.get(System.getProperty("user.dir"), "UploadFile");
            } else {
                directory = Paths.get(System.getProperty("user.dir"), "UploadFile", folderId);
            }

            if (!Files.exists(directory)) {
                Files.createDirectories(directory);
            }
            for (MultipartFile file : files) {
                Path filePath = directory.resolve(Objects.requireNonNull(file.getOriginalFilename()));
                Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            }

            return Result.success("ok");
        } catch (Exception ex) {
            log.error("上传失败", ex);
            return Result.fail("error"); // 这里应该返回错误信息，而不是"ok"
        }
    }

    @PostMapping("/Kneecoordinates/GetShpFileWKT")
    public Result<?> getShpFileWKT(@RequestParam("folderId") String folderId) throws IOException {
        List<File> fileList = new ArrayList<>();
        Path directory = Paths.get(System.getProperty("user.dir"), "UploadFile", folderId);

        if (!Files.exists(directory)) {
            return Result.fail("文件未上传");
        }

        getAllFileByDirectory(directory.toString(), fileList);
        List<String> filePaths = fileList.stream().map(File::getAbsolutePath).collect(Collectors.toList());

        if (filePaths.isEmpty()) {
            return Result.data("文件未上传");
        }

        List<String> shpFiles = filePaths.stream()
                .filter(path -> path.toLowerCase().endsWith(".shp"))
                .collect(Collectors.toList());

        String allWKTs = ShpHelper.GetWKT(shpFiles); // 假设ShpHelper是一个处理SHP文件的工具类

        return Result.data(allWKTs);
    }

    private void getAllFileByDirectory(String directory, List<File> returnValue) {
        File dir = new File(directory);
        File[] files = dir.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    returnValue.add(file);
                } else if (file.isDirectory()) {
                    getAllFileByDirectory(file.getAbsolutePath(), returnValue);
                }
            }
        }
    }
}
