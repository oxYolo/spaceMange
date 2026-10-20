package com.whulri.geodata.datarelease.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.whulri.geodata.basecore.mapper.*;
import com.whulri.geodata.basecore.service.IOperateLogService;
import com.whulri.geodata.common.config.AppSettingConfig;
import com.whulri.geodata.common.dto.mapRelation.MapRelationDto;
import com.whulri.geodata.common.dto.mapRelation.MapRelationOutputDto;
import com.whulri.geodata.common.dto.mapRelation.MapReleationInputDto;
import com.whulri.geodata.common.dto.mapRelation.StyleInputDto;
import com.whulri.geodata.common.entity.*;
import com.whulri.geodata.common.gdal.handler.GeoServer;
import com.whulri.geodata.common.gdal.pojo.Style;
import com.whulri.geodata.common.gdal.pojo.TaskState;
import com.whulri.geodata.common.result.ListResultOutput;
import com.whulri.geodata.common.util.ThumbnailHelper;
import com.whulri.geodata.common.util.style.*;
import com.whulri.geodata.datarelease.service.IMapRelationService;
import com.whulri.geodata.common.bridge.BeanUtil;
import com.whulri.geodata.common.bridge.Func;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MapRelationServiceImpl extends ServiceImpl<MapRelationMapper, MapRelationEntity> implements IMapRelationService {

    @Autowired
    private MapRelationMapper mapRelationMapper;

    @Autowired
    private MapMapper mapMapper;

    @Autowired
    private LayerMapper layerMapper;

    @Autowired
    private DataStyleMapper dataStyleMapper;

    @Autowired
    private DictDataCodeMapper dictDataCodeMapper;

    @Autowired
    private IOperateLogService operateLogService;

    @Autowired
    private ThumbnailHelper thumbnailHelper;

    @Autowired
    private GeoServer geoServer;

    @Autowired
    private AppSettingConfig appSettingConfig;

    @Override
    public ListResultOutput<MapRelationDto> GetAllListBylayerID(String layerId) {
        try {
            ListResultOutput<MapRelationDto> list = new ListResultOutput<>();
            List<MapRelationEntity> query = mapRelationMapper.selectList(new LambdaQueryWrapper<MapRelationEntity>().eq(MapRelationEntity::getDataConfigID, layerId));

            if (query != null && !query.isEmpty()) {
                List<MapRelationDto> mapRelationDtos = BeanUtil.copy(query, MapRelationDto.class);
                if (!mapRelationDtos.isEmpty()) {
                    for (MapRelationDto mp : mapRelationDtos) {
                        if (Func.isNotEmpty(mp.getMapId())) {
                            MapEntity map = mapMapper.selectById(mp.getMapId());
                            mp.setMapName(map != null ? map.getMapName() : "");
                        }

                        if (Func.isNotEmpty(mp.getDataConfigID())) {
                            LayerContentEntity layer = layerMapper.selectById(mp.getDataConfigID());
                            mp.setDataConfigID(layer != null ? layer.getLayerName() : "");
                        }

                        if (Func.isNotEmpty(mp.getDataStyleID())) {
                            DataStyleEntity dataStyleEntity = dataStyleMapper.selectById(mp.getDataStyleID());
                            mp.setDataConfigID(dataStyleEntity != null ? dataStyleEntity.getStyleName() : "");
                        }
                    }
                }
                list = new ListResultOutput<>(mapRelationDtos);
            }
            return list;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public ListResultOutput<MapRelationDto> getAllList() {
        try {
            List<MapRelationEntity> list = mapRelationMapper.selectList(new LambdaQueryWrapper<>());
            List<MapRelationDto> mapRelationDtos = BeanUtil.copy(list, MapRelationDto.class);
            return new ListResultOutput<>(mapRelationDtos);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public MapRelationOutputDto getDetailById(String id) {
        try {
            MapRelationEntity mapRelationEntity = mapRelationMapper.selectById(id);
            return BeanUtil.copy(mapRelationEntity, MapRelationOutputDto.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean getMapReleationByStyle(String styleID) {
        try {
            List<MapRelationEntity> list = mapRelationMapper.selectList(new LambdaQueryWrapper<MapRelationEntity>()
                    .eq(MapRelationEntity::getDataStyleID, styleID));
            return list != null && !list.isEmpty();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ListResultOutput<MapRelationDto> getAllListByMapId(String mapId) {
        try {
            List<MapRelationEntity> query = mapRelationMapper.selectList(new LambdaQueryWrapper<MapRelationEntity>()
                    .eq(MapRelationEntity::getMapId, mapId)
                    .orderByAsc(MapRelationEntity::getDataSort));
            List<MapRelationDto> mapRelationDtos = BeanUtil.copy(query, MapRelationDto.class);
            ListResultOutput<MapRelationDto> list = new ListResultOutput<>();

            String layerDataTypeID = "1cfe51dd-67a3-11e7-8eb2-005056bb1c7e";
            List<DictDataCodeEntity> listGeomType = dictDataCodeMapper.selectList(new LambdaQueryWrapper<DictDataCodeEntity>()
                    .eq(DictDataCodeEntity::getDataTypeID, layerDataTypeID));

            if (!mapRelationDtos.isEmpty()) {
                for (MapRelationDto mp : mapRelationDtos) {
                    if (Func.isNotEmpty(mp.getDataConfigID())) {
                        LayerContentEntity layer = layerMapper.selectById(mp.getDataConfigID());
                        mp.setDataConfigName(Func.isNotEmpty(layer) ? layer.getLayerName() : "");
                        mp.setIsShow(Func.isNotEmpty(layer) ? (Func.isNotEmpty(layer.getIsShow()) ? layer.getIsShow() : 1) : 0);
                        mp.setDataTypeName(Func.isNotEmpty(layer) ? (Func.isEmpty(layer.getDataType()) ? "" : Objects.requireNonNull(listGeomType.stream()
                                .filter(s -> s.getId().equals(layer.getDataType()))
                                .findFirst().orElse(null)).getCodeName()) : "");
                    }
                    if (Func.isNotEmpty(mp.getDataStyleID())) {
                        String styleId = mp.getDataStyleID();
                        List<DataStyleEntity> style = dataStyleMapper.selectList(new LambdaQueryWrapper<DataStyleEntity>().eq(DataStyleEntity::getId, styleId));
                        if (style != null && !style.isEmpty()) {
                            mp.setDataStyleName(style.get(0).getStyleName());
                        }
                    }
                }
            }
            list.setItem(mapRelationDtos);
            return list;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ListResultOutput<MapRelationDto> getLayersList(String layerId, String mapId) {
        try {
            ListResultOutput<MapRelationDto> list = new ListResultOutput<>();
            List<MapRelationEntity> mapRelations = mapRelationMapper.selectList(new LambdaQueryWrapper<MapRelationEntity>()
                    .eq(MapRelationEntity::getDataConfigID, layerId)
                    .eq(MapRelationEntity::getMapId, mapId));

            if (!mapRelations.isEmpty()) {
                List<MapRelationDto> copy = BeanUtil.copy(mapRelations, MapRelationDto.class);
                if (!copy.isEmpty()) {
                    if (Func.isNotBlank(copy.get(0).getDataConfigID())) {
                        LayerContentEntity layer = layerMapper.selectById(copy.get(0).getDataConfigID());
                        copy.get(0).setLayers(layer);
                        copy.get(0).setDataConfigName(layer.getLayerName());
                    }
                    if (Func.isNotBlank(copy.get(0).getMapId())) {
                        MapEntity map = mapMapper.selectById(copy.get(0).getMapId());
                        copy.get(0).setMapName(map != null ? map.getMapName() : "");
                    }
                    if (Func.isNotBlank(copy.get(0).getDataStyleID())) {
                        DataStyleEntity style = dataStyleMapper.selectById(copy.get(0).getDataStyleID());
                        copy.get(0).setDataStyleName(style != null ? style.getStyleName() : "");
                    }
                }
                list.setItem(copy);
            }
            return list;
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
    }

    @Override
    public MapRelationDto insert(MapReleationInputDto input) {
        try {
            input.setId(UUID.randomUUID().toString());
            MapRelationEntity entity = BeanUtil.copy(input, MapRelationEntity.class);
            mapRelationMapper.insert(entity);
            return BeanUtil.copy(entity, MapRelationDto.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean multiInsert(List<MapReleationInputDto> listInput) {
        try {
            String mapId = "";
            for (MapReleationInputDto input : listInput) {
                MapRelationDto dto = insert(input);
                mapId = dto.getMapId();
            }
            List<String> layers = listInput.stream()
                    .map(MapReleationInputDto::getDataConfigID)
                    .collect(Collectors.toList());
            updateMap(mapId, layers);
            return true;
        } catch (Exception e) {
            log.error(e.getMessage());
            return false;
        }
    }

    @Override
    public MapRelationDto update(MapReleationInputDto input, String user) {
        try {
            MapRelationEntity entity = BeanUtil.copy(input, MapRelationEntity.class);
            mapRelationMapper.updateById(entity);
            MapRelationDto result = BeanUtil.copy(entity, MapRelationDto.class);
            operateLogService.writeOperateLog(input.getMapId(), user, 1002, 1102, 1201, 1431, null);
            return result;
        } catch (Exception e) {
            operateLogService.writeOperateLog(input.getMapId(), user, 1002, 1102, 1202, 1432, null);
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean delete(String id, String user) {
        try {
            mapRelationMapper.deleteById(id);
            operateLogService.writeOperateLog(id, user, 1002, 1105, 1201, 1421, null);
            return true;
        } catch (Exception e) {
            operateLogService.writeOperateLog(id, user, 1002, 1105, 1202, 1422, null);
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean MultiDelete(List<MapReleationInputDto> listInput) {
        try {
            for (MapReleationInputDto map : listInput) {
                mapRelationMapper.delete(new LambdaQueryWrapper<MapRelationEntity>()
                        .eq(MapRelationEntity::getDataConfigID, map.getDataConfigID())
                        .eq(MapRelationEntity::getMapId, map.getMapId()));
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean deleteByMapID(String mapID) {
        try {
            mapRelationMapper.delete(new LambdaQueryWrapper<MapRelationEntity>()
                    .eq(MapRelationEntity::getMapId, mapID));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean publicMap(String mapId) {
        try {
            List<Map<String, Object>> query = mapRelationMapper.selectMapRelationByMapId(mapId);
            List<Map<String, Object>> list = mapRelationMapper.selectMapRelationAndDataStyle();

            List<String> targetLayers = new ArrayList<>();
            List<String> styleLayers = new ArrayList<>();
            String mapName = "";
            for (Map<String, Object> item : query) {
                if (Func.isNotEmpty(item.get("isshow")) && item.get("isshow").equals(1)) {
                    continue;
                }

                Map<String, Object> style = list.stream()
                        .filter(s -> s.get("id").equals(item.get("id")))
                        .findFirst().orElse(null);
                if (style != null) {
                    targetLayers.add(item.get("layerattrtable").toString());
                }
                if (Func.isNotEmpty(style)) {
                    styleLayers.add(style.get("stylename").toString());
                }
//                else {
//                    styleLayers.add("");
//                }
                mapName = item.get("mapenname").toString();
            }
            MapEntity map = mapMapper.selectById(mapId);
            String bBox = map.getMinX() + "," + map.getMaxX() + "," + map.getMinY() + "," + map.getMaxY();

            String geoWorkSpace = appSettingConfig.getGeoWorkspace();
            if (!geoServer.isExistWorkSpace(geoWorkSpace)) {
                geoServer.addWorkSpace(geoWorkSpace);
            }
            if (geoServer.isExsitLayerGroup(mapName, geoWorkSpace)) {
                geoServer.deleteLayerGroup(mapName, geoWorkSpace);
            }
            // todo 存在发布问题
            geoServer.addLayerGroup(mapName, targetLayers, styleLayers, geoWorkSpace, "EPSG:4326", bBox);

            // 生产缩略图
            String strBBox = map.getMinX() + "," + map.getMinY() + "," + map.getMaxX() + "," + map.getMaxY();
            getThumbnial(map.getMapEnName(), strBBox);
            return true;
        } catch (Exception e) {
            log.error(e.getMessage());
            return false;
        }
    }

    @Override
    public boolean changeStyle(String mapid, String layerStr, String styleStr) {
        try {
            List<String> layersId = Arrays.asList(layerStr.split(","));
            List<String> stylesId = Arrays.asList(styleStr.split(","));

            MapEntity map = mapMapper.selectById(mapid);
            if (map != null && layersId.size() == stylesId.size()) {
                List<LayerContentEntity> layerContents = layerMapper.selectList(new LambdaQueryWrapper<>());
                List<Map<String, String>> list1 = layerContents.stream()
                        .filter(x -> layersId.contains(x.getId()))
                        .map(x -> {
                            Map<String, String> map1 = new HashMap<>();
                            map1.put("id", x.getId());
                            map1.put("name", x.getLayerAttrTable());
                            return map1;
                        })
                        .collect(Collectors.toList());

                List<DataStyleEntity> dataStyles = dataStyleMapper.selectList(new QueryWrapper<>());
                List<Map<String, String>> list2 = dataStyles.stream()
                        .filter(x -> stylesId.contains(x.getId()))
                        .map(x -> {
                            Map<String, String> map2 = new HashMap<>();
                            map2.put("id", x.getId());
                            map2.put("name", x.getStyleName());
                            return map2;
                        }).collect(Collectors.toList());

                Map<String, String> dic = new HashMap<>();
                for (int i = 0; i < layersId.size(); i++) {

                    String lid = layersId.get(i);
                    String sid = stylesId.get(i);

                    Map<String, String> l1 = list1.stream()
                            .filter(t -> t.get("id").equals(lid))
                            .findFirst()
                            .orElse(null);
                    Map<String, String> s1 = list2.stream()
                            .filter(t -> t.get("id").equals(sid))
                            .findFirst()
                            .orElse(null);

                    if (l1 != null) {
                        String sName = "";
                        if (s1 != null) {
                            sName = s1.get("name");
                        }
                        if (!dic.containsKey(l1.get("name"))) {
                            dic.put(l1.get("name"), sName);
                        }
                    }
                }

                geoServer.modifyLayerGroup(map.getMapEnName(), new ArrayList<>(dic.keySet()), new ArrayList<>(dic.values()), appSettingConfig.getGeoWorkspace());

                String strBBox = map.getMinX() + "," + map.getMinY() + "," + map.getMaxX() + "," + map.getMaxY();
                getThumbnial(map.getMapEnName(), strBBox);
            }
            return true;
        } catch (Exception e) {
            log.error(e.getMessage());
            return false;
        }
    }

    @Override
    public boolean changeStyleObject(StyleInputDto inputDto) {
        try {
            String mapId = inputDto.getMapId();
            String layerStr = inputDto.getLayerStr();
            String styleStr = inputDto.getStyleStr();

            List<String> layersId = Arrays.asList(layerStr.split(","));
            List<String> stylesId = Arrays.asList(styleStr.split(","));

            MapEntity map = mapMapper.selectById(mapId);
            if (map != null && layersId.size() == stylesId.size()) {
                List<LayerContentEntity> layerContents = layerMapper.selectList(new QueryWrapper<>());
                List<Map<String, String>> list1 = layerContents.stream()
                        .filter(t -> layersId.contains(t.getId()))
                        .map(t -> {
                            Map<String, String> map1 = new HashMap<>();
                            map1.put("id", t.getId());
                            map1.put("name", t.getLayerAttrTable());
                            return map1;
                        })
                        .collect(Collectors.toList());
                List<DataStyleEntity> dataStyles = dataStyleMapper.selectList(new QueryWrapper<>());
                List<Map<String, String>> list2 = dataStyles.stream()
                        .filter(x -> stylesId.contains(x.getId()))
                        .map(x -> {
                            Map<String, String> map2 = new HashMap<>();
                            map2.put("id", x.getId());
                            map2.put("name", x.getStyleName());
                            return map2;
                        }).collect(Collectors.toList());

                HashMap<String, String> dic = new HashMap<>();
                for (int i = 0; i < layersId.size(); i++) {

                    String lid = layersId.get(i);
                    String sid = stylesId.get(i);

                    Map<String, String> l1 = list1.stream()
                            .filter(t -> t.get("id").equals(lid))
                            .findFirst()
                            .orElse(null);
                    Map<String, String> s1 = list2.stream()
                            .filter(t -> t.get("id").equals(sid))
                            .findFirst()
                            .orElse(null);

                    if (l1 != null) {
                        String sName = "";
                        if (s1 != null) {
                            sName = s1.get("name");
                        }
                        if (!dic.containsKey(l1.get("name"))) {
                            dic.put(l1.get("name"), sName);
                        }
                    }

                }
                geoServer.modifyLayerGroup(map.getMapEnName(), new ArrayList<>(dic.keySet()), new ArrayList<>(dic.values()), appSettingConfig.getGeoWorkspace());

                String strBBox = map.getMinX() + "," + map.getMinY() + "," + map.getMaxX() + "," + map.getMaxY();
                getThumbnial(map.getMapEnName(), strBBox);
            }
            return true;
        } catch (Exception e) {
            log.error(e.getMessage());
            return false;
        }
    }

    @Override
    public boolean multiUpdate(String mapId, List<MapReleationInputDto> listInput, String user) {
        try {
            mapRelationMapper.delete(new LambdaQueryWrapper<MapRelationEntity>().eq(MapRelationEntity::getMapId, mapId));

            if (!listInput.isEmpty()) {
                for (MapReleationInputDto input : listInput) {
                    MapRelationDto dto = insert(input);
                }
            }

            List<String> layers = listInput.stream()
                    .map(MapReleationInputDto::getDataConfigID)
                    .collect(Collectors.toList());

            updateMap(mapId, layers);

            operateLogService.writeOperateLog(mapId, user, 1002, 1102, 1201, 1441, null);
            return true;
        } catch (Exception e) {
            operateLogService.writeOperateLog(mapId, user, 1002, 1102, 1202, 1442, null);
            return false;
        }
    }

    @Override
    public boolean refreshMap(String mapId, String user) {
        try {
            List<Map<String, Object>> query = mapRelationMapper.selectMapRelationByMapId(mapId);
            List<Map<String, Object>> list = mapRelationMapper.selectMapRelationAndDataStyle();

            List<String> targetLayers = new ArrayList<>();
            List<String> styleLayers = new ArrayList<>();
            String mapName = "";
            for (Map<String, Object> item : query) {
                targetLayers.add(item.get("layerAttrTable").toString());
                Map<String, Object> style = list.stream()
                        .filter(s -> s.get("id").equals(item.get("id")))
                        .findFirst().orElse(null);
                if (Func.isNotEmpty(style)) {
                    styleLayers.add(style.get("styleName").toString());
                } else {
                    styleLayers.add("");
                }
                mapName = item.get("mapEnName").toString();
            }
            MapEntity map = mapMapper.selectById(mapId);

            String geoWorkSpace = appSettingConfig.getGeoWorkspace();
            if (!geoServer.isExistWorkSpace(geoWorkSpace)) {
                geoServer.addWorkSpace(geoWorkSpace);
            }
            if (geoServer.isExsitLayerGroup(mapName, geoWorkSpace)) {
                geoServer.deleteLayerGroup(mapName, geoWorkSpace);
            }
            geoServer.addLayerGroup(mapName, targetLayers, styleLayers, geoWorkSpace, "EPSG:4326", null);

            terminatingTask(mapName);
            tileMap(mapName, null);

            // 生产缩略图
            String strBBox = map.getMinX() + "," + map.getMinY() + "," + map.getMaxX() + "," + map.getMaxY();
            getThumbnial(map.getMapEnName(), strBBox);

            operateLogService.writeOperateLog(mapId, user, 1002, 1106, 1201, 1451, null);
            return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isExistTilesTask(String mapId) {
        MapEntity query = mapMapper.selectOne(new LambdaQueryWrapper<MapEntity>().eq(MapEntity::getId, mapId));
        if (query != null) {
            return isExistTiles(query.getMapEnName());
        }
        return false;
    }

    @Override
    public com.whulri.geodata.common.dto.serverInterface.TileProgressOutputDto getMapTileProgress(String mapId) {
        MapEntity query = mapMapper.selectOne(new LambdaQueryWrapper<MapEntity>().eq(MapEntity::getId, mapId));
        if (query == null || Func.isBlank(query.getMapEnName())) {
            com.whulri.geodata.common.dto.serverInterface.TileProgressOutputDto idle =
                    new com.whulri.geodata.common.dto.serverInterface.TileProgressOutputDto();
            idle.setDataId(mapId);
            idle.setStatus("IDLE");
            return idle;
        }
        String fullLayerName = appSettingConfig.getGeoWorkspace() + ":" + query.getMapEnName();
        try {
            return com.whulri.geodata.common.dto.serverInterface.TileProgressOutputDto.build(
                    mapId, fullLayerName, geoServer.getTileTasks(fullLayerName));
        } catch (Exception e) {
            log.warn("查询地图组切片进度失败: mapId={}, {}", mapId, e.getMessage());
            com.whulri.geodata.common.dto.serverInterface.TileProgressOutputDto failed =
                    new com.whulri.geodata.common.dto.serverInterface.TileProgressOutputDto();
            failed.setDataId(mapId);
            failed.setLayerName(fullLayerName);
            failed.setStatus("FAILED");
            return failed;
        }
    }

    @Override
    public boolean multiUpdateScale(List<MapReleationInputDto> listInput, String user) {
        try {
            for (MapReleationInputDto input : listInput) {
                if (input.getDataStyleID() == null) {
                    input.setDataStyleID("");
                }
            }

            List<String> temp1 = listInput.stream()
                    .map(MapReleationInputDto::getDataConfigID)
                    .collect(Collectors.toList());
            List<String> temp2 = listInput.stream()
                    .map(MapReleationInputDto::getDataStyleID)
                    .collect(Collectors.toList());

            List<HashMap<String, Object>> list1 = layerMapper.selectList(new QueryWrapper<LayerContentEntity>().in("id", temp1))
                    .stream()
                    .map(item -> {
                        HashMap<String, Object> map = new HashMap<>();
                        map.put("id", item.getId());
                        map.put("name", item.getLayerAttrTable());
                        return map;
                    }).collect(Collectors.toList());
            List<HashMap<String, Object>> list2 = dataStyleMapper.selectList(new QueryWrapper<DataStyleEntity>().in("id", temp2))
                    .stream()
                    .map(item -> {
                        HashMap<String, Object> map = new HashMap<>();
                        map.put("id", item.getId());
                        map.put("name", item.getStyleName());
                        return map;
                    }).collect(Collectors.toList());
            if (list1.size() != listInput.size()) {
                return false;
            }

            String mapId = listInput.get(listInput.size() - 1).getMapId();
            MapEntity map = mapMapper.selectById(mapId);
            for (MapReleationInputDto input : listInput) {
                if (Func.isNotBlank(input.getDataStyleID())) {
                    input.setStyleName(input.getId());
                }
                MapRelationDto dto = update(input, user);
                if (Func.isNotBlank(input.getDataStyleID()) && Func.isNotBlank(input.getStyleName())) {
                    uploadStyle(dto.getDataStyleID(), dto.getMaxScaleDenominator(), dto.getMinScaleDenominator(), dto.getId());
                }
            }
            Map<String, String> dic = new HashMap<>();

            for (int i = listInput.size() - 1; i >= 0; i--) {
                int finalI = i;
                HashMap<String, Object> l1 = list1.stream()
                        .filter(t -> t.get("id").equals(listInput.get(finalI).getDataConfigID()))
                        .findFirst()
                        .orElse(null);
                HashMap<String, Object> s1 = list1.stream()
                        .filter(t -> t.get("id").equals(listInput.get(finalI).getDataStyleID()))
                        .findFirst()
                        .orElse(null);
                if (l1 != null) {
                    String sName = "";
                    if (s1 != null) {
                        sName = listInput.get(finalI).getStyleName();
                    }
                    if (!dic.containsKey(l1.get("name"))) {
                        dic.put(l1.get("name").toString(), sName);
                    }
                }
            }

            geoServer.modifyLayerGroup(map.getMapName(), new ArrayList<>(dic.keySet()), new ArrayList<>(dic.values()), null);

            //生成缩略图
            String strBBox = map.getMinX().toString() + "," + map.getMinY().toString() + "," + map.getMaxX().toString() + "," + map.getMaxY().toString();
            getThumbnial(map.getMapEnName(), strBBox);

            return true;
        } catch (Exception e) {
            log.error(e.getMessage());
            return false;
        }
    }

    void uploadStyle(String styleid, String MaxScaleDenominator, String MinScaleDenominator, String stylename) {
        DataStyleEntity style = dataStyleMapper.selectById(styleid);
        StyledLayerDescriptor sld = SLDSerialize.deserialize(style.getStyleContent(), StyledLayerDescriptor.class);
        List<Rule> rules = GetRules(sld);
        for (Rule rule : rules) {
            if (!Func.isNotBlank(MaxScaleDenominator)) {
                rule.setMaxScaleDenominator(MaxScaleDenominator);
            } else {
                rule.setMaxScaleDenominator(null);
            }
            if (!Func.isNotBlank(MinScaleDenominator)) {
                rule.setMinScaleDenominator(MinScaleDenominator);
            } else {
                rule.setMinScaleDenominator(null);
            }
        }

        StyledLayerDescriptor StyleContent = new StyledLayerDescriptor();
        UserLayer ul = new UserLayer();
        UserStyle us = new UserStyle();
        FeatureTypeStyle fts = new FeatureTypeStyle();
        fts.setRules(rules);
        us.setFeatureTypeStyles(List.of(fts));
        ul.setUserStyles(List.of(us));
        StyleContent.setUserLayers(List.of(ul));
        String serialize = SLDSerialize.serialize(sld);
        updateStyle(stylename, serialize);
    }

    /**
     * 更新样式
     */
    public void updateStyle(String styleName, String styleContent) {
        if (styleContent == null || styleContent.isEmpty()) {
            return;
        }

        try {
            Path stylePath = Paths.get(System.getProperty("user.dir"), "Style");
            if (!Files.exists(stylePath)) {
                Files.createDirectories(stylePath);
            }

            Path styleFile = stylePath.resolve(styleName + ".sld");
            Files.write(styleFile, styleContent.getBytes(StandardCharsets.UTF_8));

            String geoWorkSpace = appSettingConfig.getGeoWorkspace();
            List<Style> styles = geoServer.getStyles(geoWorkSpace);
            List<String> collect = styles.stream().map(Style::getName).collect(Collectors.toList());
            if (collect.contains(styleName)) {
                geoServer.modifyStyle(styleName, styleFile.toString(), geoWorkSpace);
            } else {
                geoServer.addStyle(styleName, styleFile.getFileName().toString(), styleFile.toString(), geoWorkSpace);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to updateByEntity style: " + styleName, e);
        }
    }

    public List<Rule> GetRules(StyledLayerDescriptor sld) {
        List<Rule> rules = new ArrayList<>();
        if (sld != null) {
            List<UserStyle> usList = new ArrayList<>();
            List<FeatureTypeStyle> ftsList = new ArrayList<>();
            if (sld.getNamedLayers() != null) {
                for (NamedLayer nl : sld.getNamedLayers()) {
                    if (Func.isNotEmpty(nl.getUserStyles())) {
                        usList.addAll(nl.getUserStyles());
                    }
                }
            }
            if (sld.getUserLayers() != null) {
                for (UserLayer nl : sld.getUserLayers()) {
                    if (Func.isNotEmpty(nl.getUserStyles())) {
                        usList.addAll(nl.getUserStyles());
                    }
                }
            }

            for (UserStyle us : usList) {
                if (Func.isNotEmpty(us.getFeatureTypeStyles())) {
                    for (FeatureTypeStyle fst : us.getFeatureTypeStyles()) {
                        if (Func.isNotEmpty(fst)) {
                            rules.addAll(fst.getRules());
                        }
                    }
                }
            }
        }
        return rules;
    }

    /**
     * 图层切片
     */
    public boolean tileMap(String mapName, String bbox) {
        String fullMapName = appSettingConfig.getGeoWorkspace() + ":" + mapName;
        List<String> gridSets = geoServer.getLayerGridSet(fullMapName);

        int start = appSettingConfig.getZoomStart();
        int stop = appSettingConfig.getZoomStop();

        String gridSetName = appSettingConfig.getGridSetName();
        for (String gridSet : gridSets) {
            if (Func.isNotEmpty(gridSetName)) {
                String[] split = gridSetName.split(",");
                if (Arrays.asList(split).contains(gridSet)) {
                    geoServer.sendEmptyTilesTask(fullMapName, gridSet, start, stop, 1, bbox);
                    geoServer.sendAllTilesTask(fullMapName, gridSet, start, stop, 1, bbox);
                }
            } else {
                geoServer.sendEmptyTilesTask(fullMapName, gridSet, start, stop, 1, bbox);
                geoServer.sendAllTilesTask(fullMapName, gridSet, start, stop, 1, bbox);
            }
        }
        return true;
    }

    /**
     * 结束所有任务
     */
    public boolean terminatingTask(String mapName) {
        if (isExistTiles(mapName)) {
            String fullMapName = appSettingConfig.getGeoWorkspace() + ":" + mapName;
            return geoServer.sendTerminatingTask(fullMapName, TaskState.All);
        }
        return true;
    }

    /**
     * 查询地图是否有切片任务
     */
    public boolean isExistTiles(String mapName) {
        String fullMapName = appSettingConfig.getGeoWorkspace() + ":" + mapName;
        List<String> tasks = geoServer.getAllTilesTask(fullMapName);
        return tasks != null && !tasks.isEmpty();
    }


    public boolean getThumbnial(String name, String strBBox) {

        ThumbnailHelper tbh = thumbnailHelper;
        String imagePath = tbh.createThumbnail(name, "map", strBBox, "", "");

        if (Func.isBlank(imagePath)) {
            return false;
        } else {
            return true;
        }
    }


    public void updateMap(String mapId, List<String> layers) {
        if (layers != null && !layers.isEmpty()) {
            MapEntity map = mapMapper.selectById(mapId);

            if (map != null) {

                List<LayerContentEntity> list = layerMapper.selectList(new LambdaQueryWrapper<LayerContentEntity>()
                        .in(LayerContentEntity::getId, layers));

                if (list != null && !list.isEmpty()) {
                    Map<String, BigDecimal> bbox = new HashMap<>();
                    for (int i = 0; i < list.size(); i++) {
                        LayerContentEntity layerContent = list.get(i);
                        if (i == 0) {
                            bbox.put("MaxX", layerContent.getMaxX());
                            bbox.put("MinX", layerContent.getMinX());
                            bbox.put("MaxY", layerContent.getMaxY());
                            bbox.put("MinY", layerContent.getMinY());
                        } else {
                            if (layerContent.getMaxX().compareTo(bbox.get("MaxX")) > 0) {
                                bbox.put("MaxX", layerContent.getMaxX());
                            }
                            if (layerContent.getMaxY().compareTo(bbox.get("MaxY")) > 0) {
                                bbox.put("MaxY", layerContent.getMaxY());
                            }
                            if (layerContent.getMinX().compareTo(bbox.get("MinX")) < 0) {
                                bbox.put("MinX", layerContent.getMinX());
                            }
                            if (layerContent.getMinY().compareTo(bbox.get("MinY")) < 0) {
                                bbox.put("MinY", layerContent.getMinY());
                            }
                        }
                    }

                    map.setMaxX(bbox.get("MaxX"));
                    map.setMinX(bbox.get("MinX"));
                    map.setMaxY(bbox.get("MaxY"));
                    map.setMinY(bbox.get("MinY"));

                    mapMapper.updateById(map);
                }
            }
        }
    }


}
