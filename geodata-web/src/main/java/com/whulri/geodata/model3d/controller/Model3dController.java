package com.whulri.geodata.model3d.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whulri.geodata.model3d.dto.Model3dPageQuery;
import com.whulri.geodata.model3d.dto.Model3dUploadInitDTO;
import com.whulri.geodata.model3d.entity.Model3dEntity;
import com.whulri.geodata.model3d.service.IModel3dService;
import com.whulri.geodata.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 三维数据管理接口
 */
@Tag(name = "三维数据管理", description = "3D Tiles / OSGB 倾斜摄影 / 点云 / 单体 / BIM 数据上传、转换与发布")
@RestController
@RequestMapping("/model3d")
public class Model3dController {

    @Resource
    private IModel3dService model3dService;

    @PostMapping("/init")
    @Operation(summary = "初始化上传", description = "登记元数据并返回三维数据 id，随后按分片上传")
    public Result<String> init(@RequestBody Model3dUploadInitDTO dto) {
        return Result.data(model3dService.initUpload(dto), "初始化成功");
    }

    @PostMapping("/{id}/part")
    @Operation(summary = "上传分片", description = "partNo 从 1 开始；非最后分片须 ≥5MiB（对象存储 compose 限制）")
    public Result<Void> uploadPart(@PathVariable("id") String id,
                                   @RequestParam("partNo") int partNo,
                                   @RequestParam("file") MultipartFile file) {
        model3dService.uploadPart(id, partNo, file);
        return Result.success("分片上传成功");
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "完成上传", description = "合并分片；现成 3D Tiles 立即发布，其余等待转换")
    public Result<Model3dEntity> complete(@PathVariable("id") String id) {
        return Result.data(model3dService.completeUpload(id), "上传完成");
    }

    @GetMapping("/{id}/parts")
    @Operation(summary = "已上传分片", description = "返回已上传分片号（升序，从 1 开始），供断点续传跳过已传分片")
    public Result<List<Integer>> parts(@PathVariable("id") String id) {
        return Result.data(model3dService.listUploadedParts(id));
    }

    @PostMapping("/{id}/convert")
    @Operation(summary = "下发转换任务", description = "按源格式路由到对应工具链（OSGB→3dtiles / LAS→py3dtiles / OBJ→obj2gltf / FBX→FBX2glTF / IFC→IfcConvert），异步执行")
    public Result<Void> convert(@PathVariable("id") String id) {
        model3dService.requestConvert(id);
        return Result.success("转换任务已下发");
    }

    @GetMapping("/{id}")
    @Operation(summary = "详情", description = "含状态 / 进度 / 包围盒 / 转换失败原因")
    public Result<Model3dEntity> get(@PathVariable("id") String id) {
        return Result.data(model3dService.getById(id));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询", description = "支持名称模糊 / 目录 / 类型 / 状态过滤")
    public Result<Page<Model3dEntity>> page(Model3dPageQuery query) {
        return Result.data(model3dService.page(query));
    }

    @GetMapping("/{id}/publish-url")
    @Operation(summary = "签发加载地址",
            description = "返回成果加载地址。默认走公开只读前缀（整目录可访问，适配 3D Tiles 相对路径子瓦片）；"
                    + "publicRead=false 时退回预签名 URL（仅单文件有效）")
    public Result<String> publishUrl(@PathVariable("id") String id) {
        return Result.data(model3dService.getPublishUrl(id), "签发成功");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除", description = "删除记录并清理对象存储中该数据的全部对象")
    public Result<Void> delete(@PathVariable("id") String id) {
        model3dService.delete(id);
        return Result.success("删除成功");
    }
}
