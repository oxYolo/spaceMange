package com.whulri.geodata.web.controller;

import com.whulri.geodata.common.dto.map.MapInputDto;
import com.whulri.geodata.common.result.Result;
import com.whulri.geodata.datarelease.service.IMapService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 地图管理模块
 */
@Slf4j
@RestController
@RequestMapping("/map")
public class MapController {

    @Autowired
    private IMapService mapService;

    /**
     * 获取所有列表
     *
     * @return
     */
    @GetMapping("/GetAllList")
    public Result<?> getAllList() {
        return Result.data(mapService.getAllList());
    }

    /**
     * 根据查询条件获取地图信息
     *
     * @return
     */
    @PostMapping("/GetAllListByName")
    public Result<?> getAllListByName(MapInputDto input) {
        return Result.data(mapService.getAllListByName(input));
    }

    /**
     * 根据查询条件获取地图分页信息
     *
     * @param input
     * @param pageSize
     * @param pageIndex
     * @return
     */
    @PostMapping("/GetPageListByName")
    public Result<?> getPageListByName(@RequestBody MapInputDto input,
                                       @RequestParam(defaultValue = "10") int pageSize,
                                       @RequestParam(defaultValue = "1") int pageIndex) {
        return Result.data(mapService.getPageListByName(input, pageSize, pageIndex));
    }

    /**
     * 根据地图编号查询地图详细信息
     *
     * @param id
     * @return
     */
    @GetMapping("/GetDetailById")
    public Result<?> getDetailById(String id) {
        return Result.data(mapService.getDetailById(id));
    }

    /**
     * 新增地图
     *
     * @param input
     * @return
     */
    @PostMapping("/Insert")
    public Result<?> insert(MapInputDto input) {
        return Result.data(mapService.insert(input));
    }

    /**
     * 更新地图
     *
     * @param input
     * @return
     */
    @PostMapping("/Update")
    public Result<?> update(MapInputDto input) {
        return Result.data(mapService.update(input));
    }

    /**
     * 更新地图图例
     *
     * @param mapID
     * @param legendUrlPath
     * @return
     */
    @PostMapping("/UpdateMapLegend")
    public Result<?> updateMapLegend(String mapID, String legendUrlPath) {
        return Result.status(mapService.updateMapLegend(mapID, legendUrlPath));
    }

    /**
     * 更新地图分类
     *
     * @param mapID
     * @param dataTypeID
     * @return
     */
    @PostMapping("/UpdateDataType")
    public Result<?> updateDataType(String mapID, String dataTypeID) {
        return Result.data(mapService.updateDataType(mapID, dataTypeID));
    }

    /**
     * 更新地图边界范围
     *
     * @param mapID
     * @return
     */
    @PostMapping("/UpdateMapBBox")
    public Result<?> updateMapBBox(String mapID) {
        return Result.status(mapService.updateMapBBox(mapID));
    }

    /**
     * 删除地图
     *
     * @param id
     * @param user
     * @return
     */
    @PostMapping("/Delete")
    public Result<?> delete(String id, String user) {
        return Result.status(mapService.delete(id, user));
    }

    /**
     * 根据经纬度查询图层属性信息
     *
     * @param layerID
     * @param lon
     * @param lat
     * @param tolerance
     * @return
     */
    @GetMapping("/GetLayerAttrByLayerPt")
    public Result<?> getLayerAttrByLayerPt(@RequestParam("layerID") String layerID,
                                           @RequestParam("lon") Float lon,
                                           @RequestParam("lat") Float lat,
                                           @RequestParam(value = "tolerance", required = false) Float tolerance) {
        return Result.data(mapService.getLayerAttrByLayerPt(layerID, lon, lat, tolerance));
    }

    /**
     * 根据标签id获取有关地图类型的分类数据表
     *
     * @param tagid
     * @return
     */
    @GetMapping("/GetTypeInstance")
    public Result<?> getTypeInstance(String tagid) {
        return Result.data(mapService.getTypeInstance(tagid));
    }

    /**
     * 根据标签id和类型id查找所有地图
     *
     * @param tagid
     * @param typeid
     * @return
     */
    @GetMapping("/GetMaps")
    public Result<?> getMaps(String tagid, String typeid) {
        return Result.data(mapService.getMaps(tagid, typeid));
    }

    /**
     * 获取地图样式信息
     *
     * @param MapId
     * @return
     */
    @GetMapping("/GetDataStyle")
    public Result<?> getDataStyle(String MapId) {
        return Result.data(mapService.getDataStyle(MapId));
    }

    /**
     * 获取列表
     * @param username
     * @return
     */
    @GetMapping("/GetAllDataToTable")
    public Result<?> getAllDataToTable(String username) {
        return Result.data(mapService.getAllDataToTable(username));
    }


}
