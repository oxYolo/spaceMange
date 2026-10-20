package com.whulri.geodata.web.controller;

import com.whulri.geodata.common.result.ListResultOutput;
import com.whulri.geodata.common.result.Result;
import com.whulri.geodata.common.dto.dataTag.DataTagDto;
import com.whulri.geodata.datamanage.service.IDataTagService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "DataTag数据标签信息")
@RestController
@RequestMapping("dataTag")
@AllArgsConstructor
public class DataTagController {

    @Autowired
    private IDataTagService dataTagService;

    @Operation(summary = "获取所有标签")
    @GetMapping("/GetAllList")
    public Result<?> getAllList() {
        List<DataTagDto> allList = dataTagService.getAllList();
        return Result.data(new ListResultOutput<>(allList));
    }

    @Operation(summary = "根据标签类型查询标签")
    @GetMapping("/GetAllListByDataType")
    public Result<ListResultOutput<DataTagDto>> getAllListByDataType(String dataType) {
        List<DataTagDto> allListByDataType = dataTagService.getAllListByDataType(dataType);
        return Result.data(new ListResultOutput<>(allListByDataType));
    }

    @Operation(summary = "根据标签编号查询所有标签")
    @GetMapping("/GetDetailById")
    public Result<?> getDetailById(@RequestParam String id) {
        DataTagDto detailById = dataTagService.getDetailById(id);
        return Result.data(detailById);
    }

    @Operation(summary = "新增标签")
    @PostMapping("/Insert")
    public Result<?> insert(@RequestBody DataTagDto dto) {
        DataTagDto insert = dataTagService.insert(dto);
        return Result.data(insert);
    }

    @Operation(summary = "更新标签")
    @PostMapping("/Update")
    public Result<?> update(@RequestBody DataTagDto dto) {
        DataTagDto update = dataTagService.update(dto);
        return Result.data(update);
    }


    @Operation(summary = "根据标签编号删除标签")
    @PostMapping("/Delete")
    public Result<?> Delete(@RequestParam String id) {
        return Result.status(dataTagService.delete(id));
    }
}
