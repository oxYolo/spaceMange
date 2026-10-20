package com.whulri.geodata.web.controller;

import com.whulri.geodata.common.dto.layerField.LayerFieldDto;
import com.whulri.geodata.common.dto.layerField.LayerFieldInputDto;
import com.whulri.geodata.common.dto.layerField.LayerFieldOutputDto;
import com.whulri.geodata.common.result.ListResultOutput;
import com.whulri.geodata.common.result.Result;
import com.whulri.geodata.datamanage.service.ILayerFieldService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("layerField")
public class LayerFieldController {

    @Autowired
    private ILayerFieldService layerFieldService;

    @Operation(summary = "获取所有图层所有属性", tags = "")
    @GetMapping("/GetAllList")
    public Result<ListResultOutput<LayerFieldDto>> getAllList() {
        List<LayerFieldDto> allList = layerFieldService.getAllList();
        return Result.data(new ListResultOutput<>(allList));
    }

    @Operation(summary = "根据图层属性编号获取图层属性信息", tags = "传入id")
    @GetMapping("/GetDetailById")
    public Result<?> GetDetailById(@RequestParam String id) {
        LayerFieldOutputDto layerFieldOutputDto = layerFieldService.GetDetailById(id);
        return Result.data(layerFieldOutputDto);
    }

    @Operation(summary = "根据图层编号获取所有属性信息", tags = "传入layerId")
    @GetMapping("/GetDetailByLayerID")
    public Result<ListResultOutput<LayerFieldDto>> GetDetailByLayerID(@RequestParam("layerID") String layerId) {
        List<LayerFieldDto> layerFieldDtos = layerFieldService.GetDetailByLayerID(layerId);
        return Result.data(new ListResultOutput<>(layerFieldDtos));
    }

    @Operation(summary = "新增图层属性")
    @PostMapping("/Insert")
    public Result<?> Insert(@RequestBody LayerFieldInputDto inputDto) {
        LayerFieldDto dto = layerFieldService.insert(inputDto);
        return Result.data(dto);
    }

    @Operation(summary = "新增图层属性")
    @PostMapping("/InsertSync")
    public Result<?> InsertSync(@RequestBody LayerFieldInputDto inputDto) {
        LayerFieldDto dto = layerFieldService.insertSync(inputDto);
        return Result.data(dto);
    }

    @Operation(summary = "批量新增图层属性")
    @PostMapping("/MultiInsert")
    public Result<?> MultiInsert(@RequestBody List<LayerFieldInputDto> layerFields) {
        boolean b = layerFieldService.MultiInsert(layerFields);
        if (b) {
            return Result.status(true);
        }
        return Result.fail("新增失败");
    }

    @Operation(summary = "更新图层属性")
    @PostMapping("/Update")
    public Result<?> Update(@RequestBody LayerFieldInputDto input) {
        LayerFieldDto layerFieldDto = layerFieldService.update(input);
        return Result.data(layerFieldDto);
    }


    @Operation(summary = "删除图层属性")
    @PostMapping("/Delete")
    public Result<?> delete(String id) {
        boolean delete = layerFieldService.delete(id);
        return Result.status(delete);
    }


    @Operation(summary = "根据图层编号删除所有属性")
    @PostMapping("/DeleteByLayerID")
    public Result<?> deleteByLayerID(@RequestParam("layerID") String layerID) {
        boolean delete = layerFieldService.deleteByLayerID(layerID);
        return Result.status(delete);
    }

}
