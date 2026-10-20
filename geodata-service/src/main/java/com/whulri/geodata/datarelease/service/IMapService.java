package com.whulri.geodata.datarelease.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.whulri.geodata.common.dto.dataType.DataTypeDto;
import com.whulri.geodata.common.dto.map.MapDto;
import com.whulri.geodata.common.dto.map.MapInputDto;
import com.whulri.geodata.common.dto.map.MapOutputDto;
import com.whulri.geodata.common.entity.DataStyleEntity;
import com.whulri.geodata.common.entity.MapEntity;
import com.whulri.geodata.common.result.ListResultOutput;
import com.whulri.geodata.common.vo.PagedResultOutput;

import java.util.List;
import java.util.Map;

public interface IMapService extends IService<MapEntity> {

    ListResultOutput<MapDto> getAllList();

    ListResultOutput<MapDto> getAllListByName(MapInputDto input);

    PagedResultOutput<MapDto> getPageListByName(MapInputDto input, int PageSize, int PageIndex);

    MapOutputDto getDetailById(String id);

    MapDto insert(MapInputDto dto);

    MapDto update(MapInputDto dto);

    boolean updateMapLegend(String mapID, String legendUrlPath);

    MapDto updateDataType(String mapID, String dataTypeID);

    boolean updateMapBBox(String mapID);

    boolean delete(String id, String user);

    String getLayerAttrByLayerPt(String layerID, Float lon, Float lat, Float tolerance);

    ListResultOutput<DataTypeDto> getTypeInstance(String tagid);

    ListResultOutput<MapDto> getMaps(String tagid, String typeid);

    List<DataStyleEntity> getDataStyle(String MapId);

    List<Map<String, Object>> getAllDataToTable(String username);
}
