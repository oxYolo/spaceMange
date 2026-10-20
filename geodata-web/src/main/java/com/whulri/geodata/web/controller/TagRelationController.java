package com.whulri.geodata.web.controller;

import com.whulri.geodata.common.dto.dicDataCode.DicDataCodeInputDto;
import com.whulri.geodata.common.dto.tagRelation.TagDto;
import com.whulri.geodata.common.dto.tagRelation.TagReleationInputDto;
import com.whulri.geodata.common.result.Result;
import com.whulri.geodata.datamanage.service.ITagRelationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "数据标签关系")
@RestController
@RequestMapping("/tagReleation")
public class TagRelationController {

    @Autowired
    private ITagRelationService tagRelationService;


    /**
     * 根据地图或图层编号删除标签
     *
     * @param mapID
     * @return
     */
    @PostMapping("/DeleteByMapID")
    public Result<?> DeleteByMapID(@RequestParam String mapID) {
        return Result.status(tagRelationService.DeleteByMapID(mapID));
    }

    /**
     * 获取所有地图或图层标签
     *
     * @return
     */
    @GetMapping("/GetAllList")
    public Result<?> getAllList() {
        return Result.data(tagRelationService.getAllList());
    }

    /**
     * 根据关系编号获取标签详细信息
     *
     * @return
     */
    @GetMapping("/GetDetailById")
    public Result<?> getDetailById(@RequestParam("id") String id) {
        return Result.data(tagRelationService.getDetailById(id));
    }

    /**
     * 根据地图或图层编号获取所有标签描述
     *
     * @return
     */
    @GetMapping("/GetMultiTagNameByMapID")
    public Result<?> getMultiTagNameByMapID(@RequestParam("mapLayerID") String mapLayerID) {
        return Result.data(tagRelationService.getMultiTagNameByMapID(mapLayerID));
    }

    /**
     * 新增地图或图层标签
     *
     * @return
     */
    @PostMapping("/Insert")
    public Result<?> insert(@RequestBody TagReleationInputDto input) {
        return Result.data(tagRelationService.insert(input));
    }

    /**
     * 批量新增地图或图层标签
     *
     * @return
     */
    @PostMapping("/MultiInsert")
    public Result<?> multiInsert(@RequestBody TagDto tagDto) {
        return Result.data(tagRelationService.MultiInsert(tagDto));
    }

    /**
     * 更新地图或图层标签
     *
     * @return
     */
    @PostMapping("/Update")
    public Result<?> update(@RequestBody TagReleationInputDto input) {
        return Result.data(tagRelationService.update(input));
    }

    /**
     * 删除标签
     *
     * @return
     */
    @PostMapping("/Delete")
    public Result<?> delete(@RequestParam("id") String id) {
        return Result.data(tagRelationService.delete(id));
    }
}
