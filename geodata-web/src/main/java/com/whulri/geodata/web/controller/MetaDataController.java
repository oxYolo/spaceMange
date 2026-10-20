package com.whulri.geodata.web.controller;


import com.whulri.geodata.common.dto.metadata.MetaDataInputDto;
import com.whulri.geodata.common.result.Result;
import com.whulri.geodata.datarelease.service.IMetadataService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

@RestController
@RequestMapping("/metadata")
public class MetaDataController {

    @Resource
    private IMetadataService metadataService;

    /**
     * 获取所有元数据
     *
     * @return
     */
    @GetMapping("/GetAllList")
    public Result<?> getAllList() {
        return Result.data(metadataService.getAllList());
    }

    /**
     * 根据元数据编号获取元数据详细信息
     *
     * @return
     */
    @GetMapping("/GetDetailById")
    public Result<?> getDetailById(@RequestParam("id") String id) {
        return Result.data(metadataService.getDetailById(id));
    }

    /**
     * 新增元数据
     *
     * @param input
     * @return
     */
    @PostMapping("Insert")
    public Result<?> insert(@RequestBody MetaDataInputDto input) {
        return Result.data(metadataService.insert(input));
    }

    /**
     * 更新元数据
     *
     * @param input
     * @return
     */
    @PostMapping("Update")
    public Result<?> update(@RequestBody MetaDataInputDto input) {
        return Result.data(metadataService.update(input));
    }

    /**
     * 删除地图元数据
     *
     * @param id
     * @return
     */
    @PostMapping("Delete")
    public Result<?> delete(@RequestParam("id") String id) {
        return Result.status(metadataService.delete(id));
    }

    /**
     * 根据编号删除元数据
     *
     * @param dataId
     * @return
     */
    @PostMapping("DeleteByDataId")
    public Result<?> deleteByDataId(@RequestParam("dataId") String dataId) {
        return Result.status(metadataService.deleteByDataId(dataId));
    }

    /**
     * 根据数据编号获取元数据详细信息
     *
     * @return
     */
    @GetMapping("/GetDetailByDataId")
    public Result<?> getDetailByDataId(@RequestParam("dataId") String dataId) {
        return Result.data(metadataService.getDetailByDataId(dataId));
    }
}
