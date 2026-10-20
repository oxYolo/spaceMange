package com.whulri.geodata.web.controller;

import com.whulri.geodata.common.dto.dicDataType.DicDataTypeDto;
import com.whulri.geodata.common.result.Result;
import com.whulri.geodata.datamanage.service.IDicDataTypeService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

@RequestMapping("/dicDataType")
@RestController
public class DicDataTypeController {

    @Resource
    private IDicDataTypeService dicDataTypeService;

    /**
     * 获取所有字典分类
     *
     * @return
     */
    @GetMapping("/GetAllList")
    public Result<?> getAllList() {
        return Result.data(dicDataTypeService.getAllList());
    }

    /**
     * 根据字典分类编号获取字典分类详细信息
     *
     * @return
     */
    @GetMapping("/GetDetailById")
    public Result<?> getDetailById(@RequestParam("id") String id) {
        return Result.data(dicDataTypeService.getDetailById(id));
    }

    /**
     * 新增字典分类
     *
     * @return
     */
    @PostMapping("/Insert")
    public Result<?> insert(@RequestBody DicDataTypeDto input) {
        return Result.data(dicDataTypeService.insert(input));
    }

    /**
     * 更新字典分类
     *
     * @param input
     * @return
     */
    @PostMapping("/Update")
    public Result<?> update(@RequestBody DicDataTypeDto input) {
        return Result.data(dicDataTypeService.update(input));
    }

    /**
     * 删除字典分类
     *
     * @param id
     * @return
     */
    @PostMapping("/Delete")
    public Result<?> delete(@RequestParam("id") String id) {
        return Result.status(dicDataTypeService.delete(id));
    }


}
