package com.whulri.geodata.web.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.whulri.geodata.common.dto.LayerFieldQuery;
import com.whulri.geodata.common.dto.LayerParameter;
import com.whulri.geodata.common.dto.layerContent.CatalogTreeVO;
import com.whulri.geodata.common.dto.layerContent.LayerContentDto;
import com.whulri.geodata.common.dto.layerContent.LayerContentInputDto;
import com.whulri.geodata.common.dto.layerContent.LayerUpdateDto;
import com.whulri.geodata.common.entity.LayerFieldEntity;
import com.whulri.geodata.common.entity.MapEntity;
import com.whulri.geodata.common.result.ListResultOutput;
import com.whulri.geodata.common.result.PageResultDTO;
import com.whulri.geodata.common.result.Result;
import com.whulri.geodata.common.vo.PagedResultOutput;
import com.whulri.geodata.datamanage.dto.VectorEditDTO;
import com.whulri.geodata.datamanage.service.ILayerContentService;
import com.whulri.geodata.datamanage.service.IVectorEditService;
import com.whulri.geodata.datamanage.util.CompressionUtils;
import com.whulri.geodata.web.config.MinioConfig;
import com.whulri.geodata.web.util.FileUtil;
import io.minio.MinioClient;
import io.minio.ObjectWriteArgs;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import com.whulri.geodata.common.bridge.Func;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Tag(name = "图层")
@RequestMapping("/layerContent")
@RestController
public class LayerContentController {

    @Autowired
    private ILayerContentService layerContentService;

    @Autowired
    private IVectorEditService vectorEditService;

    @Autowired
    private MinioConfig minioConfig;

    @Autowired
    private MinioClient minioClient;


    @Operation(summary = "获取所有图层")
    @GetMapping("/GetAllList")
    public Result<?> GetAllList() {
        return Result.data(layerContentService.getAllList());
    }

    /**
     * 图层统计列表
     *
     * @param userCode 用户编码
     * @return 数据类型统计输出
     */
    @Operation(summary = "图层统计列表")
    @GetMapping("/GetAllCountByDataType")
    public Result<?> getAllCountByDataType(String userCode) {
        return Result.data(layerContentService.getAllCountByDataType(userCode));
    }


    /**
     * 条件分页查询图层信息
     *
     * @param input     查询参数
     * @param pageSize  分页大小
     * @param pageIndex 当前页码
     * @return 分页结果输出
     */
    @Operation(summary = "获取图层分页数据")
    @GetMapping("/GetAllListStatus")
    public Result<?> getAllListStatus(LayerContentInputDto input, int pageSize, int pageIndex) {
        return Result.data(layerContentService.getAllListStatus(input, pageSize, pageIndex));
    }

    /**
     * 图层统计数量
     *
     * @param userCode 用户编码
     * @return 异步返回统计数量
     */
    @Operation(summary = "图层统计数量")
    @GetMapping("/GetAllCount")
    Result<Integer> getAllCount(String userCode) {
        return Result.data(layerContentService.getAllCount(userCode));
    }


    /**
     * 根据查询条件获取所有图层
     *
     * @param input 查询参数
     * @return 图层内容DTO列表结果
     */
    @Operation(summary = "根据查询条件获取所有图层")
    @PostMapping("/GetAllListByName")
    Result<ListResultOutput<LayerContentDto>> getAllListByName(@RequestBody LayerContentInputDto input) {
        return Result.data(layerContentService.getAllListByName(input));
    }

    /**
     * 根据查询条件获取图层分页信息
     *
     * @param input     查询参数
     * @param pageSize  分页大小
     * @param pageIndex 当前页码
     * @return 分页结果输出
     */
    @Operation(summary = "根据查询条件获取图层分页信息")
    @PostMapping("/GetPageListByName")
    Result<PagedResultOutput<LayerContentDto>> getPageListByName(@RequestBody LayerContentInputDto input,
                                                                 @RequestParam(defaultValue = "10") int pageSize,
                                                                 @RequestParam(defaultValue = "1") int pageIndex) {
        return Result.data(layerContentService.getPageListByName(input, pageSize, pageIndex));
    }

    /**
     * 根据地图ID获取所有图层
     *
     * @param mapID 地图ID
     * @return 图层内容DTO列表结果
     */
    @Operation(summary = "根据图层编号获取所有图层")
    @GetMapping("/GetAllListByMapID")
    Result<ListResultOutput<LayerContentDto>> getAllListByMapID(String mapID) {
        return Result.data(layerContentService.getAllListByMapID(mapID));
    }

    /**
     * 根据ID获取图层详细信息
     *
     * @param id 图层ID
     * @return 异步返回图层内容DTO
     */
    @Operation(summary = "根据图层编号获取图层详细信息")
    @GetMapping("/GetDetailById")
    public Result<?> getDetailById(String id) {
        return Result.data(layerContentService.getDetailById(id));
    }

    /**
     * 根据图层ID获取图层属性分页信息
     *
     * @param layerID   图层ID
     * @param pageSize  分页大小
     * @param pageIndex 当前页码
     * @return 分页属性信息字符串
     */
    @Operation(summary = "根据图层ID获取图层属性分页信息")
    @GetMapping("/GetLayerAttrTabledDetail")
    Result<String> getLayerAttrTabledDetail(String layerID, int pageSize, int pageIndex) {
        return Result.data(layerContentService.getLayerAttrTabledDetail(layerID, pageSize, pageIndex));
    }

    /**
     * 新增图层
     *
     * @param inputLc      图层信息
     * @param filePathName 文件路径名称
     * @param user         用户
     * @param isImport     是否导入
     * @return 异步返回图层内容DTO
     */
    @Operation(summary = "新增图层")
    @PostMapping("/Insert")
    public Result<?> insert(@RequestBody LayerContentInputDto inputLc,
                            @RequestParam String filePathName,
                            @RequestParam String user,
                            @RequestParam String isImport) {
        return Result.data(layerContentService.insert(inputLc, filePathName, user, isImport));
    }


    /**
     * 新增影像图层
     *
     * @param inputLc 图层信息
     * @return 异步返回地图实体
     */
    @Operation(summary = "新增影像图层")
    @PostMapping("/InsertImageLayer")
    Result<MapEntity> insertImageLayer(LayerContentInputDto inputLc) {
        return Result.data(layerContentService.insertImageLayer(inputLc));
    }

    /**
     * 更新图层
     */
    @Operation(summary = "更新图层")
    @PostMapping("/Update")
    public Result<?> update(@RequestBody LayerContentInputDto input) {
        return Result.status(layerContentService.update(input));
    }

    /**
     * 更新图层上传状态为1
     *
     * @param layerID 图层ID
     * @return 异步返回图层内容DTO
     */
    @Operation(summary = "导入 shp 文件数据列表到地图")
    @PostMapping("/UpdateStatus")
    Result<?> updateStatus(String layerID) {
        return Result.data(layerContentService.updateStatus(layerID));
    }


    /**
     * 删除图层信息
     *
     * @param id   图层ID
     * @param user 用户
     * @return 是否成功
     */
    @Operation(summary = "删除图层信息")
    @PostMapping("/Delete")
    Result<Boolean> delete(String id, String user) {
        return Result.data(layerContentService.delete(id, user));
    }


    /**
     * 删除图层及发布信息
     *
     * @param id 图层ID
     * @return 是否成功
     */
    @Operation(summary = "删除图层及发布信息")
    @PostMapping("/DeleteLayer")
    Result<Boolean> deleteLayer(String id) {
        return Result.data(layerContentService.deleteLayer(id));
    }


    /**
     * 获取目录以及图层
     *
     * @param dataType
     * @param publishStatus
     * @param layerName
     * @return
     */
    @GetMapping("/getLayerTree")
    public Result<?> getLayerTree(@RequestParam("dictType") String dictType,
                                  @RequestParam(value = "dataType", required = false) String dataType,
                                  @RequestParam(value = "publishStatus", defaultValue = "") String publishStatus,
                                  @RequestParam(value = "layerName", defaultValue = "") String layerName,
                                  @RequestParam(value = "uploadFileType", defaultValue = "") String uploadFileType,
                                  @RequestParam(value = "maxRaster",required = false) Integer maxRaster,
                                  @RequestParam(value = "isclassify", required = false) Boolean isclassify) {
        List<CatalogTreeVO> catalogTreeVOS = layerContentService.getLayerTree(dictType, dataType, publishStatus, layerName, uploadFileType, maxRaster, isclassify);
        String jsonString = JSON.toJSONString(catalogTreeVOS);
        return Result.data(CompressionUtils.compressString(jsonString));
//        return Result.data(layerContentService.getLayerTree(dataType, publishStatus, layerName));
    }

    // 根据条件分页获取图层属性表
    @Operation(summary = "根据条件分页获取图层属性表")
    @GetMapping("/GetLayerAttrTableByWhere")
    Result<PagedResultOutput<String>> getLayerAttrTableByWhere(String layerId, String where, int pageSize, int pageIndex) {
        return Result.data(layerContentService.getLayerAttrTableByWhere(layerId, where, pageSize, pageIndex));
    }

//    @Operation(summary = "根据条件分页获取图层属性表")
//    @GetMapping("/layerAttrInfoByWhere")
//    PagedResultOutput<String> layerAttrInfoByWhere(@RequestBody List<JSONObject> where,String layerId, int pageSize, int pageIndex) {
//        return layerContentService.layerAttrInfoByWhere(where,layerId, pageSize, pageIndex);
//    }

    // 根据图层ID分页获取属性表
    @Operation(summary = "根据图层ID分页获取属性表")
    @GetMapping("/GetAttrtableByLayerId")
    Result<PagedResultOutput<String>> getAttrtableByLayerId(String layerId, int pageSize, int pageIndex) {
        return Result.data(layerContentService.getAttrtableByLayerId(layerId, pageSize, pageIndex));
    }

    @Operation(summary = "空间查询 支持圆, 多边形，框选，缓冲区,SQL拼接查询")
    @PostMapping("/pageAttrInfoBySwitch")
    Result<PageResultDTO<Map<String,Object>>> pageAttrInfoBySwitch(@RequestBody LayerFieldQuery query, int pageSize, int pageIndex) {
        return Result.data(layerContentService.pageAttrInfoBySwitch(query, pageSize, pageIndex));
    }

    @Operation(summary = "空间查询 点选查询")
    @PostMapping("/pageAttrInfoByPoint")
    Result<List<PageResultDTO<Map<String,Object>>>> pageAttrInfoByPoint(@RequestBody LayerFieldQuery query) {
        return Result.data(layerContentService.pageAttrInfoByPoint(query));
    }

    @Operation(summary = "shp数据导出")
    @PostMapping("/downloadAttrInfoBySwitch")
    Result<String> downloadAttrInfoBySwitch(@RequestBody LayerFieldQuery query) throws Exception {
        String localPath = layerContentService.downloadAttrInfoBySwitch(query);
        File file = new File(localPath);
        if (file.exists()) {
            MultipartFile multipartFile = FileUtil.fileToMultipartFile(file);
            minioClient = MinioClient.builder().endpoint(minioConfig.getUrl()).credentials(minioConfig.getAccessKey(), minioConfig.getSecretKey()).build();
            ObjectWriteResponse res = minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object("dataManage/"+file.getName())
                    .stream(multipartFile.getInputStream(), multipartFile.getSize(), ObjectWriteArgs.MIN_MULTIPART_SIZE).build());
            // 返回可访问的图片链接
            return Result.data(minioConfig.getUrl() + "/" + minioConfig.getBucket() + "/" +res.object());
        }
        return Result.data(localPath);
    }

    @Operation(summary = "栅格影像导出")
    @GetMapping("/downloadTif")
    Result<String> downloadTif(@RequestParam String layerId) throws Exception {
        String localPath = layerContentService.outputTifFileData(layerId);
        File file = new File(localPath);
        if (file.exists()) {
            MultipartFile multipartFile = FileUtil.fileToMultipartFile(file);
            minioClient = MinioClient.builder().endpoint(minioConfig.getUrl()).credentials(minioConfig.getAccessKey(), minioConfig.getSecretKey()).build();
            ObjectWriteResponse res = minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object("dataManage/"+file.getName())
                    .stream(multipartFile.getInputStream(), multipartFile.getSize(), ObjectWriteArgs.MIN_MULTIPART_SIZE).build());
            // 返回可访问的图片链接
            return Result.data(minioConfig.getUrl() + "/" + minioConfig.getBucket() + "/" +res.object());
        }
        return Result.data(localPath);
    }

    /**
     * 插入图层属性
     *
     * @param layerParamete 图层参数
     * @return 插入是否成功
     */
    @Operation(summary = "插入图层属性")
    @PostMapping("/InsertAttrtable")
    Result<Boolean> insertAttrtable(@RequestBody LayerParameter layerParamete) {
        return Result.data(layerContentService.insertAttrtable(layerParamete));
    }


    /**
     * 删除图层属性
     *
     * @param id   图层ID
     * @param gid 属性GUID
     * @return 删除是否成功
     */
    @Operation(summary = "删除图层属性")
    @PostMapping("/DeleteAttrtable")
    Result<Boolean> deleteAttrtable(String id, String gid) {
        return Result.data(layerContentService.deleteAttrtable(id, gid));
    }

    /**
     * 更新图层属性
     *
     * @param layerParameter 图层参数
     * @return 更新是否成功
     */
    @Operation(summary = "更新图层属性")
    @PostMapping("/UpdateAttrtable")
    Result<Boolean> updateAttrtable(@RequestBody LayerParameter layerParameter) {
        return Result.data(layerContentService.updateAttrtable(layerParameter));
    }

    /**
     * 批量更新图层属性
     *
     * @param layerParameters 图层参数列表
     * @return 更新是否成功
     */
    @Operation(summary = "批量更新图层属性")
    @PostMapping("/UpdateAttrtableList")
    Result<Boolean> updateAttrtableList(@RequestBody List<LayerParameter> layerParameters) {
        return Result.data(layerContentService.updateAttrtableList(layerParameters));
    }

    // 根据图层ID获取字段
    @Operation(summary = "组合条件字段")
    @GetMapping("/GetFieldByLayerId")
    Result<List<LayerFieldEntity>> getFieldByLayerId(String layerId, String attributeType) {
        return Result.data(layerContentService.getFieldByLayerId(layerId, attributeType));
    }

    // 验证条件格式
    @Operation(summary = "验证条件格式")
    @PostMapping("/ValidateCondition")
    Result<Boolean> validateCondition(String layerId, @RequestBody List<JSONObject> where) {
        return Result.data(layerContentService.validateCondition(layerId, where));
    }

    /**
     * 按类型（矢量，栅格等）资源目录树和节点
     *
     * @param dataTypeId
     * @param publishStatus
     * @param layerName
     * @return
     */
    @GetMapping("/getLayerTreeByType")
    public Result<?> getLayerTreeByType(@RequestParam("dataTypeId") String dataTypeId,
                                        @RequestParam(value = "publishStatus", defaultValue = "") String publishStatus,
                                        @RequestParam(value = "layerName", defaultValue = "") String layerName) {
        List<CatalogTreeVO> layerTreeByType = layerContentService.getLayerTreeByType(dataTypeId, publishStatus, layerName);
        return Result.data(CompressionUtils.compressString(JSON.toJSONString(layerTreeByType)));
//        return Result.data(layerContentService.getLayerTreeByType(dataTypeId, publishStatus, layerName));
    }


    /**
     * 输出图层 shp 文件
     *
     * @param layerID 图层ID
     * @return 文件路径
     */
    @GetMapping("/OutputShpFileData")
    public Result<?> outputShpFileData(String layerID) {
        return Result.data(layerContentService.outputShpFileData(layerID));
    }

    /**
     * 输出图层 tiff 文件路径
     *
     * @param layerID 图层ID
     * @return 文件路径字符串
     */
    @GetMapping("/OutputTifFileData")
    public Result<?> outputTifFileData(String layerID) {
        return Result.data(layerContentService.outputTifFileData(layerID));
    }

    /**
     * 批量更新图层排序
     * @param layerUpdateDtoList
     * @return
     */
    @PostMapping("/updateList")
    public Result<?> updateList(@RequestBody List<LayerUpdateDto> layerUpdateDtoList) {
        return Result.status(layerContentService.updateList(layerUpdateDtoList));
    }

    @PostMapping("/updateBothList")
    public Result<?> updateBothList(@RequestBody List<LayerUpdateDto> layerUpdateDtoList) {
        return Result.status(layerContentService.updateBothList(layerUpdateDtoList));
    }

    @GetMapping("/getLayerValue")
    public Result<?> getLayerValue(@RequestParam("layerId") String layerId,
                                   @RequestParam("longitude") String longitude,
                                   @RequestParam("latitude") String latitude) {
        Object layerValue = layerContentService.getLayerValue(layerId, longitude, latitude);
        if(Func.isNotEmpty(layerValue)){
            return Result.data(layerValue);
        }
        return Result.data("");
    }

    /**
     * 编辑矢量要素
     *
     * @param dto 编辑参数（layerId + geoJsonList）
     * @return 是否成功
     */
    @Operation(summary = "编辑矢量要素")
    @PostMapping("/editVectorFeatures")
    public Result<?> editVectorFeatures(@RequestBody VectorEditDTO dto) {
        return Result.status(vectorEditService.editVectorFeatures(dto));
    }

}
