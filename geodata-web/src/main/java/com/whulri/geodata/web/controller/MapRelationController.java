package com.whulri.geodata.web.controller;

import com.whulri.geodata.common.dto.mapRelation.MapReleationInputDto;
import com.whulri.geodata.common.dto.mapRelation.StyleInputDto;
import com.whulri.geodata.common.result.Result;
import com.whulri.geodata.datarelease.service.IMapRelationService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/mapreleation")
public class MapRelationController {

    @Autowired
    private IMapRelationService mapRelationService;

    @GetMapping("/GetAllListBylayerID")
    @Operation(summary = "根据图层编号获取所有对应关系")
    public Result<?> GetAllListBylayerID(@RequestParam("layerId") String layerId) {
        return Result.data(mapRelationService.GetAllListBylayerID(layerId));
    }

    /**
     * 获取所有地图图层对应关系
     *
     * @return
     */
    @GetMapping("/GetAllList")
    public Result<?> getAllList() {
        return Result.data(mapRelationService.getAllList());
    }

    /**
     * 根据关系编号获取地图图层对应关系详细信息
     *
     * @param id
     * @return
     */
    @GetMapping("/GetDetailById")
    public Result<?> getDetailById(String id) {
        return Result.data(mapRelationService.getDetailById(id));
    }

    /**
     * 判断样式是否被使用
     *
     * @param styleID
     * @return
     */
    @GetMapping("/GetMapReleationByStyle")
    public Result<?> getMapReleationByStyle(String styleID) {
        return Result.status(mapRelationService.getMapReleationByStyle(styleID));
    }

    /**
     * 根据地图编号获取所有对应关系
     *
     * @param mapId
     * @return
     */
    @GetMapping("/GetAllListByMapId")
    public Result<?> getAllListByMapId(String mapId) {
        return Result.data(mapRelationService.getAllListByMapId(mapId));
    }

    /**
     * 根据地图编号获取已勾选图层
     *
     * @param layerId
     * @param mapId
     * @return
     */
    @GetMapping("/GetLayersList")
    public Result<?> getLayersList(String layerId, String mapId) {
        return Result.data(mapRelationService.getLayersList(layerId, mapId));
    }

    /**
     * 新增地图图层对应关系
     *
     * @param inputDto
     * @return
     */
    @PostMapping("/Insert")
    public Result<?> insert(@RequestBody MapReleationInputDto inputDto) {
        return Result.data(mapRelationService.insert(inputDto));
    }

    /**
     * 批量新增地图图层对应关系
     *
     * @param listInput
     * @return
     */
    @PostMapping("/MultiInsert")
    public Result<?> multiInsert(@RequestBody List<MapReleationInputDto> listInput) {
        return Result.status(mapRelationService.multiInsert(listInput));
    }

    /**
     * 更新地图图层对应关系
     *
     * @param input
     * @param user
     * @return
     */
    @PostMapping("/Update")
    public Result<?> update(@RequestBody MapReleationInputDto input,
                            @RequestParam("user") String user) {
        return Result.data(mapRelationService.update(input, user));
    }

    /**
     * 删除地图图层对应关系
     *
     * @param id
     * @param user
     */
    @PostMapping("/Delete")
    public Result<?> delete(@RequestParam("id") String id, @RequestParam("user") String user) {
        return Result.status(mapRelationService.delete(id, user));
    }

    /**
     * 批量删除地图图层对应关系
     *
     * @param listInput
     * @return
     */
    @PostMapping("/MultiDelete")
    public Result<?> multiDelete(@RequestBody List<MapReleationInputDto> listInput) {
        return Result.status(mapRelationService.MultiDelete(listInput));
    }

    /**
     * 删除地图图层对应关系
     *
     * @param mapID
     */
    @PostMapping("/DeleteByMapID")
    public Result<?> deleteByMapID(@RequestParam("mapID") String mapID) {
        return Result.status(mapRelationService.deleteByMapID(mapID));
    }

    /**
     * 发布地图
     *
     * @param mapId
     * @return
     */
    @PostMapping("/PublicMap")
    public Result<?> publicMap(@RequestParam("mapId") String mapId) {
        return Result.status(mapRelationService.publicMap(mapId));
    }

    /**
     * 改变图层组样式
     *
     * @param mapId
     * @param layerStr
     * @param styleStr
     * @return
     */
    @PostMapping("/ChangeStyle")
    public Result<?> changeStyle(@RequestParam("mapid") String mapId,
                                 @RequestParam("layerStr") String layerStr,
                                 @RequestParam("styleStr") String styleStr) {
        return Result.status(mapRelationService.changeStyle(mapId, layerStr, styleStr));
    }

    /**
     * 改变图层组样式
     *
     * @param inputDto
     * @return
     */
    @PostMapping("/ChangeStyleObject")
    public Result<?> changeStyleObject(@RequestBody StyleInputDto inputDto) {
        return Result.status(mapRelationService.changeStyleObject(inputDto));
    }

    /**
     * 改变图层关系
     *
     * @param mapId
     * @param listInput
     * @param user
     * @return
     */
    @PostMapping("/MultiUpdate")
    public Result<?> multiUpdate(@RequestParam("mapId") String mapId,
                                 @RequestBody List<MapReleationInputDto> listInput,
                                 @RequestParam("user") String user) {
        return Result.data(mapRelationService.multiUpdate(mapId, listInput, user));
    }

    /**
     * 刷新地图
     *
     * @param mapId
     * @param user
     * @return
     */
    @PostMapping("/RefreshMap")
    public Result<?> refreshMap(@RequestParam("mapId") String mapId,
                                @RequestParam("user") String user) {
        return Result.status(mapRelationService.refreshMap(mapId, user));
    }


    /**
     * 查询地图是否有切片任务
     *
     * @param mapId
     * @return
     */
    @GetMapping("/IsExistTilesTask")
    public Result<?> isExistTilesTask(@RequestParam("mapId") String mapId) {
        return Result.status(mapRelationService.isExistTilesTask(mapId));
    }

    /**
     * 查询地图组切片进度（基于 GeoServer GWC seed 任务）
     *
     * @param mapId 地图ID
     * @return status: IDLE-无任务 RUNNING-切片中 DONE-已完成 FAILED-查询失败
     */
    @GetMapping("/TileProgress")
    public Result<?> tileProgress(@RequestParam("mapId") String mapId) {
        return Result.data(mapRelationService.getMapTileProgress(mapId));
    }


    /**
     * 批量更新并上传到geoserver
     *
     * @param listInput
     * @param user
     * @return
     */
    @PostMapping("/MultiUpdateScale")
    public Result<?> multiUpdateScale(@RequestBody List<MapReleationInputDto> listInput,
                                      @RequestParam("user") String user) {
        return Result.data(mapRelationService.multiUpdateScale(listInput, user));
    }


}
