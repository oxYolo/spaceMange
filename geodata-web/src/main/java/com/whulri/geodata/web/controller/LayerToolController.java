package com.whulri.geodata.web.controller;

import com.alibaba.nacos.api.utils.StringUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whulri.geodata.common.dto.layerTool.FormulaItemDTO;
import com.whulri.geodata.common.dto.layerTool.LayerToolDto;
import com.whulri.geodata.common.dto.layerTool.OverlayCalculationDTO;
import com.whulri.geodata.common.dto.layerTool.ResamplingDTO;
import com.whulri.geodata.common.entity.LayerContentEntity;
import com.whulri.geodata.common.entity.ToolHisEntity;
import com.whulri.geodata.common.result.Result;
import com.whulri.geodata.datamanage.service.ILayerContentService;
import com.whulri.geodata.datamanage.service.IToolHisService;
import io.swagger.v3.oas.annotations.Operation;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconstConstants;
import com.whulri.geodata.common.bridge.AuthUtil;

import com.whulri.geodata.common.bridge.Func;
import cn.hutool.core.util.StrUtil;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import com.alibaba.fastjson.JSON;
import java.util.List;

@RestController
@RequestMapping("layerTool")
public class LayerToolController {

    @Autowired
    private ILayerContentService layerContentService;

    @Autowired
    private IToolHisService toolHisService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.layer-tool.exchange:LayerToolExchange}")
    private String exchange;

    @Value("${rabbitmq.layer-tool.routing:LayerToolRouting}")
    private String routingKey;

    /**
     * 获取图层名称：优先使用前端传的 layerName，否则使用默认命名
     */
    private String resolveLayerName(String prefix, LayerContentEntity layer, String layerNameFromDto) {
        if (StrUtil.isNotBlank(layerNameFromDto)) {
            return layerNameFromDto;
        }
        return prefix + layer.getLayerName();
    }

    @Operation(summary = "历史记录", tags = "")
    @GetMapping("/getList")
    public Result getList(@RequestParam Integer pageSize,
                          @RequestParam Integer pageIndex,
                          @RequestParam(required = false) String origin) {
        IPage<ToolHisEntity> page = new Page<>(pageIndex, pageSize);
        LambdaQueryWrapper<ToolHisEntity> qw = new LambdaQueryWrapper<>();
        qw.eq(Func.isNotEmpty(origin),ToolHisEntity::getOrigin,origin)
        .orderByDesc(ToolHisEntity::getCreateDT);
        IPage<ToolHisEntity> resultPage = toolHisService.page(page, qw);
        return Result.data(resultPage);
    }

    @Operation(summary = "删除记录", tags = "")
    @GetMapping("/delete")
    public Result delete(String id) {
        return Result.data(toolHisService.removeById(id));
    }

    @Operation(summary = "数据存储", tags = "")
    @GetMapping("/storageLayer")
    public Result storageLayer(@RequestParam String id, @RequestParam String targetId) {
        ToolHisEntity toolHis = toolHisService.getById(id);
        if(Func.isNotEmpty(toolHis)){
            // 更新历史表状态为存储状态
            toolHis.setStorage("1");
            toolHisService.updateById(toolHis);
            // 更新图层表 将数据改为显示状态，并设置目标目录
            String layerId = toolHis.getLayerId();
            LayerContentEntity layer = layerContentService.getById(layerId);
            layer.setIsShow(1);
            layer.setLayerType(targetId);
            layerContentService.updateById(layer);
            return Result.data("存储成功");
        }
        return Result.data("数据获取失败");
    }

    @Operation(summary = "图层裁剪", tags = "")
    @PostMapping("/cutLayer")
    public Result cutLayer(@RequestBody LayerToolDto layerToolDto) {
        // 获取原始图层
        LayerContentEntity layer = layerContentService.getById(layerToolDto.getLayerId());
        if(layer == null){
            return null;
        }
        // 1. 先创建任务记录（状态为处理中）
        ToolHisEntity toolHis = new ToolHisEntity();
        toolHis.setLayerName(resolveLayerName("裁剪", layer, layerToolDto.getLayerName()));
        toolHis.setCreateBy(AuthUtil.getNickName());
        toolHis.setStatus("0");
        toolHis.setOrigin("1");
        toolHis.setStorage("0");
        toolHis.setDetailJson(layerToolDto.getDetailJson());
        toolHis.setCreateDT(java.time.LocalDateTime.now());
        toolHisService.save(toolHis);

        // 2. 设置任务ID到DTO
        layerToolDto.setTaskId(toolHis.getId());
        layerToolDto.setTaskType("1");

        // 3. 发送 MQ 消息到后台服务
        rabbitTemplate.convertAndSend(exchange, routingKey, JSON.toJSONString(layerToolDto));

        // 4. 返回任务ID
        return Result.data(toolHis.getId());
    }

    @Operation(summary = "重采样", tags = "")
    @PostMapping("/resample")
    public Result resample(@RequestBody LayerToolDto layerToolDto){
        // 获取原始图层
        LayerContentEntity layer = layerContentService.getById(layerToolDto.getLayerId());
        if(layer == null){
            return null;
        }
        // 1. 先创建任务记录（状态为处理中）
        ToolHisEntity toolHis = new ToolHisEntity();
        toolHis.setLayerName(resolveLayerName("重采样", layer, layerToolDto.getLayerName()));
        toolHis.setCreateBy(AuthUtil.getNickName());
        toolHis.setStatus("0");
        toolHis.setOrigin("2");
        toolHis.setStorage("0");
        toolHis.setDetailJson(layerToolDto.getDetailJson());
        toolHis.setCreateDT(java.time.LocalDateTime.now());
        toolHisService.save(toolHis);

        // 2. 设置任务ID到DTO
        layerToolDto.setTaskId(toolHis.getId());
        layerToolDto.setTaskType("2");

        // 3. 发送 MQ 消息到后台服务
        rabbitTemplate.convertAndSend(exchange, routingKey, JSON.toJSONString(layerToolDto));
        // 4. 返回任务ID
        return Result.data(toolHis.getId());
    }

    @Operation(summary = "缓冲区", tags = "")
    @PostMapping("/bufferZone")
    public Result bufferZone(@RequestBody LayerToolDto layerToolDto) {
        // 获取原始图层
        LayerContentEntity layer = layerContentService.getById(layerToolDto.getLayerId());
        if (layer == null) {
            return Result.fail("图层不存在");
        }
        // 1. 先创建任务记录（状态为处理中）
        ToolHisEntity toolHis = new ToolHisEntity();
        toolHis.setLayerName(resolveLayerName("缓冲区", layer, layerToolDto.getLayerName()));
        toolHis.setCreateBy(AuthUtil.getNickName());
        toolHis.setStatus("0");
        toolHis.setOrigin("7");
        toolHis.setStorage("0");
        toolHis.setDetailJson(layerToolDto.getDetailJson());
        toolHis.setCreateDT(java.time.LocalDateTime.now());
        toolHisService.save(toolHis);

        // 2. 设置任务ID到DTO
        layerToolDto.setTaskId(toolHis.getId());
        layerToolDto.setTaskType("7");

        // 3. 发送 MQ 消息到后台服务
        rabbitTemplate.convertAndSend(exchange, routingKey, JSON.toJSONString(layerToolDto));

        // 4. 返回任务ID
        return Result.data(toolHis.getId());
    }

    @Operation(summary = "获取栅格数据信息", tags = "")
    @GetMapping("/getTifInfo")
    public Result getTifInfo(@RequestParam String layerId) {
        // 1. 获取图层
        LayerContentEntity layer = layerContentService.getById(layerId);
        if (layer == null) {
            return Result.fail("图层不存在");
        }

        // 2. 获取文件路径
        String filePath = layer.getUploadFileName();
        if (StringUtils.isBlank(filePath)) {
            return Result.fail("文件路径为空");
        }

        try {
            // 3. 初始化 GDAL
            gdal.AllRegister();

            // 4. 打开文件
            Dataset dataset = gdal.Open(filePath, gdalconstConstants.GA_ReadOnly);
            if (dataset == null) {
                return Result.fail("无法打开文件: " + filePath);
            }

            // 5. 读取元数据
            int xSize = dataset.GetRasterXSize();
            int ySize = dataset.GetRasterYSize();
            double[] geoTransform = dataset.GetGeoTransform();
            double xCell = Math.abs(geoTransform[1]) * 111319.5; // X方向分辨率（转米）
            double yCell = Math.abs(geoTransform[5]) * 111319.5; // Y方向分辨率（转米）

            dataset.delete();

            // 6. 构建返回结果
            ResamplingDTO resamplingDto = new ResamplingDTO();
            resamplingDto.setXsize(xSize);
            resamplingDto.setYsize(ySize);
            resamplingDto.setXCell(xCell);
            resamplingDto.setYCell(yCell);

            return Result.data(resamplingDto);
        } catch (Exception e) {
            return Result.fail("读取TIF信息失败: " + e.getMessage());
        }
    }

    @Operation(summary = "栅格矢量化", tags = "")
    @PostMapping("/tifToShp")
    public Result tifToShp(@RequestBody LayerToolDto layerToolDto){
        // 获取原始图层
        LayerContentEntity layer = layerContentService.getById(layerToolDto.getLayerId());
        if(layer == null){
            return null;
        }
        // 1. 先创建任务记录（状态为处理中）
        ToolHisEntity toolHis = new ToolHisEntity();
        toolHis.setLayerName(resolveLayerName("栅格矢量化", layer, layerToolDto.getLayerName()));
        toolHis.setCreateBy(AuthUtil.getNickName());
        toolHis.setStatus("0");
        toolHis.setOrigin("3");
        toolHis.setStorage("0");
        toolHis.setDetailJson(layerToolDto.getDetailJson());
        toolHis.setCreateDT(java.time.LocalDateTime.now());
        toolHis.setUploadFileType("1");
        toolHisService.save(toolHis);

        // 2. 设置任务ID到DTO
        layerToolDto.setTaskId(toolHis.getId());
        layerToolDto.setTaskType("3");

        // 3. 发送 MQ 消息到后台服务
        rabbitTemplate.convertAndSend(exchange, routingKey, JSON.toJSONString(layerToolDto));
        // 4. 返回任务ID
        return Result.data(toolHis.getId());
    }

    @Operation(summary = "矢量栅格化", tags = "")
    @PostMapping("/shpToTif")
    public Result shpToTif(@RequestBody LayerToolDto layerToolDto){
        // 获取原始图层
        LayerContentEntity layer = layerContentService.getById(layerToolDto.getLayerId());
        if(layer == null){
            return null;
        }
        // 1. 先创建任务记录（状态为处理中）
        ToolHisEntity toolHis = new ToolHisEntity();
        toolHis.setLayerName(resolveLayerName("矢量栅格化", layer, layerToolDto.getLayerName()));
        toolHis.setCreateBy(AuthUtil.getNickName());
        toolHis.setStatus("0");
        toolHis.setOrigin("4");
        toolHis.setStorage("0");
        toolHis.setDetailJson(layerToolDto.getDetailJson());
        toolHis.setCreateDT(java.time.LocalDateTime.now());
        toolHis.setUploadFileType("2");
        toolHisService.save(toolHis);

        // 2. 设置任务ID到DTO
        layerToolDto.setTaskId(toolHis.getId());
        layerToolDto.setTaskType("4");

        // 3. 发送 MQ 消息到后台服务
        rabbitTemplate.convertAndSend(exchange, routingKey, JSON.toJSONString(layerToolDto));
        // 4. 返回任务ID
        return Result.data(toolHis.getId());
    }

    @Operation(summary = "重分类", tags = "")
    @PostMapping("/reclassification")
    public Result reclassification(@RequestBody LayerToolDto layerToolDto){
        // 获取原始图层
        LayerContentEntity layer = layerContentService.getById(layerToolDto.getLayerId());
        if(layer == null){
            return null;
        }
        // 1. 先创建任务记录（状态为处理中）
        ToolHisEntity toolHis = new ToolHisEntity();
        toolHis.setLayerName(resolveLayerName("重分类", layer, layerToolDto.getLayerName()));
        toolHis.setCreateBy(AuthUtil.getNickName());
        toolHis.setStatus("0");
        toolHis.setOrigin("5");
        toolHis.setStorage("0");
        toolHis.setDetailJson(layerToolDto.getDetailJson());
        toolHis.setCreateDT(java.time.LocalDateTime.now());
        toolHis.setUploadFileType("2");
        toolHisService.save(toolHis);

        // 2. 设置任务ID到DTO
        layerToolDto.setTaskId(toolHis.getId());
        layerToolDto.setTaskType("5");

        // 3. 发送 MQ 消息到后台服务
        rabbitTemplate.convertAndSend(exchange, routingKey, JSON.toJSONString(layerToolDto));
        // 4. 返回任务ID
        return Result.data(toolHis.getId());
    }

    @Operation(summary = "擦除", tags = "")
    @PostMapping("/eraseVectorLayer")
    public Result eraseVectorLayer(@RequestBody LayerToolDto layerToolDto){
        // 获取原始图层
        LayerContentEntity layer = layerContentService.getById(layerToolDto.getLayerId());
        if(layer == null){
            return null;
        }
        // 1. 先创建任务记录（状态为处理中）
        ToolHisEntity toolHis = new ToolHisEntity();
        toolHis.setLayerName(resolveLayerName("擦除", layer, layerToolDto.getLayerName()));
        toolHis.setCreateBy(AuthUtil.getNickName());
        toolHis.setStatus("0");
        toolHis.setOrigin("6");
        toolHis.setStorage("0");
        toolHis.setDetailJson(layerToolDto.getDetailJson());
        toolHis.setCreateDT(java.time.LocalDateTime.now());
        toolHis.setUploadFileType("2");
        toolHisService.save(toolHis);

        // 2. 设置任务ID到DTO
        layerToolDto.setTaskId(toolHis.getId());
        layerToolDto.setTaskType("6");

        // 3. 发送 MQ 消息到后台服务
        rabbitTemplate.convertAndSend(exchange, routingKey, JSON.toJSONString(layerToolDto));
        // 4. 返回任务ID
        return Result.data(toolHis.getId());
    }

    @Operation(summary = "栅格叠加计算", tags = "")
    @PostMapping("/overlayCalculation")
    public Result overlayCalculation(@RequestBody LayerToolDto layerToolDto){
        OverlayCalculationDTO overlayDto = layerToolDto.getOverlayCalculationDto();
        List<FormulaItemDTO> formulaList = overlayDto.getFormulaList();
        if (formulaList == null || formulaList.isEmpty()) {
            return Result.fail("公式列表不能为空");
        }
        // 获取第一个layer类型的元素作为原始图层
        String layerId = null;
        for (FormulaItemDTO item : formulaList) {
            if ("layer".equals(item.getDataType())) {
                layerId = item.getValue();
                break;
            }
        }
        if (layerId == null) {
            return Result.fail("公式列表中未找到图层");
        }
        // 获取原始图层
        LayerContentEntity layer = layerContentService.getById(layerId);
        if(layer == null){
            return Result.fail("图层不存在");
        }
        // 1. 先创建任务记录（状态为处理中）
        ToolHisEntity toolHis = new ToolHisEntity();
        toolHis.setLayerName(resolveLayerName("栅格叠加计算", layer, layerToolDto.getLayerName()));
        toolHis.setCreateBy(AuthUtil.getNickName());
        toolHis.setStatus("0");
        toolHis.setOrigin("9");
        toolHis.setStorage("0");
        toolHis.setDetailJson(layerToolDto.getDetailJson());
        toolHis.setCreateDT(java.time.LocalDateTime.now());
        toolHis.setUploadFileType("2");
        toolHisService.save(toolHis);

        // 2. 设置任务ID到DTO
        layerToolDto.setTaskId(toolHis.getId());
        layerToolDto.setTaskType("9");

        // 3. 发送 MQ 消息到后台服务
        rabbitTemplate.convertAndSend(exchange, routingKey, JSON.toJSONString(layerToolDto));
        // 4. 返回任务ID
        return Result.data(toolHis.getId());
    }

    @Operation(summary = "矢量交并补", tags = "")
    @PostMapping("/intersectionAndUnion")
    public Result intersectionAndUnion(@RequestBody LayerToolDto layerToolDto) {
        // 1. 校验 layerIds 参数
        if (layerToolDto.getIntersectionAndUnionDto() == null
                || layerToolDto.getIntersectionAndUnionDto().getLayerIds() == null
                || layerToolDto.getIntersectionAndUnionDto().getLayerIds().size() != 2) {
            return Result.fail("需要提供两个图层ID");
        }
        if (StringUtils.isBlank(layerToolDto.getIntersectionAndUnionDto().getOperationType())) {
            return Result.fail("需要指定操作类型");
        }

        // 2. 获取两个图层并校验存在性
        String layerId1 = layerToolDto.getIntersectionAndUnionDto().getLayerIds().get(0);
        String layerId2 = layerToolDto.getIntersectionAndUnionDto().getLayerIds().get(1);
        LayerContentEntity layer1 = layerContentService.getById(layerId1);
        LayerContentEntity layer2 = layerContentService.getById(layerId2);
        if (layer1 == null || layer2 == null) {
            return Result.fail("图层不存在");
        }

        // 3. 校验类型一致性
        if (!layer1.getDataType().equals(layer2.getDataType())) {
            return Result.fail("不支持不同维度图层的交并补操作");
        }

        // 4. 根据操作类型设置前缀
        String opType = layerToolDto.getIntersectionAndUnionDto().getOperationType();
        String opPrefix;
        switch (opType) {
            case "intersection": opPrefix = "交集"; break;
            case "union": opPrefix = "并集"; break;
            case "symDifference": opPrefix = "补集"; break;
            default: opPrefix = "交并补";
        }

        // 5. 创建任务记录（状态为处理中）
        ToolHisEntity toolHis = new ToolHisEntity();
        toolHis.setLayerName(resolveLayerName(opPrefix, layer1, layerToolDto.getLayerName()));
        toolHis.setCreateBy(AuthUtil.getNickName());
        toolHis.setStatus("0");
        toolHis.setOrigin("8");
        toolHis.setStorage("0");
        toolHis.setDetailJson(layerToolDto.getDetailJson());
        toolHis.setCreateDT(java.time.LocalDateTime.now());
        toolHisService.save(toolHis);

        // 5. 设置任务ID和taskType到DTO
        layerToolDto.setTaskId(toolHis.getId());
        layerToolDto.setTaskType("8");

        // 6. 发送 MQ 消息到后台服务
        rabbitTemplate.convertAndSend(exchange, routingKey, JSON.toJSONString(layerToolDto));

        // 7. 返回任务ID
        return Result.data(toolHis.getId());
    }

}
