package com.whulri.geodata.web.controller;


import com.whulri.geodata.common.dto.dataType.DataTypeUpdateDto;
import com.whulri.geodata.common.result.ListResultOutput;
import com.whulri.geodata.common.result.Result;
import com.whulri.geodata.common.dto.dataType.DataTypeDto;
import com.whulri.geodata.common.dto.dataType.DataTypeInputDto;
import com.whulri.geodata.common.dto.dataType.DataTypeOutputDto;
import com.whulri.geodata.datamanage.service.IDataTypeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("dataType")
@Tag(name = "分类管理模块信息")
public class DataTypeController {

    @Autowired
    private IDataTypeService dataTypeService;

    @Operation(summary = "获取所有分类")
    @GetMapping("/GetAllList")
    public Result<?> getAllList() {
        List<DataTypeDto> allList = dataTypeService.getAllList();
        ListResultOutput<DataTypeDto> dataTypeDtoListResultOutput = new ListResultOutput<>(allList);
        return Result.data(dataTypeDtoListResultOutput);
    }

    @Operation(summary = "获取分类管理(通过图层与地图类型区分)", description = "传入获取分类管理")
    @GetMapping("/GetAllListByDataType")
    public Result<ListResultOutput<DataTypeDto>> getAllListByDataType(@RequestParam String dataType) {
        List<DataTypeDto> allListByDataType = dataTypeService.getAllListByDataType(dataType);
        return Result.data(new ListResultOutput<>(allListByDataType));
    }

    @Operation(summary = "根据分类编号查询分类详细信息", description = "传入类型id")
    @GetMapping("/GetDetailById")
    public Result<?> getDetailById(@RequestParam String id) {
        DataTypeOutputDto detailById = dataTypeService.getDetailById(id);
        return Result.data(detailById);
    }

    @Operation(summary = "根据名称和类型查询所有分类", description = "传入类型名称name，类型编号type")
    @GetMapping("/GetDetailByName")
    public Result<ListResultOutput<DataTypeDto>> getDetailByName(String name, String dataType) {
        List<DataTypeDto> detailByName = dataTypeService.getDetailByName(name, dataType);
        return Result.data(new ListResultOutput<>(detailByName));
    }

    @Operation(summary = "新增分类")
    @PostMapping("/Insert")
    public Result<?> insert(@RequestBody DataTypeInputDto input) {
        DataTypeDto insert = dataTypeService.insert(input);
        return Result.data(insert);
    }

    @Operation(summary = "更新分类")
    @PostMapping("/Update")
    public Result<?> update(@RequestBody DataTypeInputDto input) {
        DataTypeDto dataTypeDto = dataTypeService.update(input);
        return Result.data(dataTypeDto);
    }

    @Operation(summary = "删除分类")
    @PostMapping("/Delete")
    public Result<?> delete(@RequestParam String id) {
        return Result.status(dataTypeService.delete(id));
    }

    /**
     * 批量排序修改
     * @param dataTypeUpdataDtoList
     * @return
     */
    @PostMapping("/updateList")
    public Result<?> updateList(@RequestBody List<DataTypeUpdateDto> dataTypeUpdataDtoList) {
        return Result.status(dataTypeService.updateList(dataTypeUpdataDtoList));
    }

}
