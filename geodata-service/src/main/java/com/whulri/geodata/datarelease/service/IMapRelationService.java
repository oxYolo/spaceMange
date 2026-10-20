package com.whulri.geodata.datarelease.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.whulri.geodata.common.dto.mapRelation.MapRelationDto;
import com.whulri.geodata.common.dto.mapRelation.MapRelationOutputDto;
import com.whulri.geodata.common.dto.mapRelation.MapReleationInputDto;
import com.whulri.geodata.common.dto.mapRelation.StyleInputDto;
import com.whulri.geodata.common.entity.MapRelationEntity;
import com.whulri.geodata.common.result.ListResultOutput;

import java.util.List;

public interface IMapRelationService extends IService<MapRelationEntity> {

    ListResultOutput<MapRelationDto> GetAllListBylayerID(String layerId);

    ListResultOutput<MapRelationDto> getAllList();

    MapRelationOutputDto getDetailById(String id);

    boolean getMapReleationByStyle(String styleID);

    ListResultOutput<MapRelationDto> getAllListByMapId(String mapId);

    ListResultOutput<MapRelationDto> getLayersList(String layerId, String mapId);

    MapRelationDto insert(MapReleationInputDto input);

    boolean multiInsert(List<MapReleationInputDto> listInput);

    MapRelationDto update(MapReleationInputDto input, String user);

    boolean delete(String id, String user);

    boolean MultiDelete(List<MapReleationInputDto> listInput);

    boolean deleteByMapID(String mapID);

    boolean publicMap(String mapId);

    boolean changeStyle(String mapid, String layerStr, String styleStr);

    boolean changeStyleObject(StyleInputDto inputDto);

    boolean multiUpdate(String mapId, List<MapReleationInputDto> listInput, String user);

    boolean refreshMap(String mapId, String user);

    boolean isExistTilesTask(String mapId);

    /**
     * 查询地图组切片进度（基于 GeoServer GWC seed 任务）
     *
     * @param mapId 地图ID
     * @return 切片进度；status: IDLE/RUNNING/DONE/FAILED
     */
    com.whulri.geodata.common.dto.serverInterface.TileProgressOutputDto getMapTileProgress(String mapId);

    boolean multiUpdateScale(List<MapReleationInputDto> listInput, String user);

}
