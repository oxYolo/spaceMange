package com.whulri.geodata.datarelease.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.whulri.geodata.basecore.mapper.*;
import com.whulri.geodata.basecore.service.IOperateLogService;
import com.whulri.geodata.common.config.AppSettingConfig;
import com.whulri.geodata.common.constant.DictionaryIds;
import com.whulri.geodata.common.dto.dataType.DataTypeDto;
import com.whulri.geodata.common.dto.map.MapDto;
import com.whulri.geodata.common.dto.map.MapInputDto;
import com.whulri.geodata.common.dto.map.MapOutputDto;
import com.whulri.geodata.common.entity.*;
import com.whulri.geodata.common.gdal.handler.GeoServer;
import com.whulri.geodata.common.result.ListResultOutput;
import com.whulri.geodata.common.util.ChineseConvert;
import com.whulri.geodata.common.util.ConstHelper;
import com.whulri.geodata.common.util.FileHelper;
import com.whulri.geodata.common.util.ThumbnailHelper;
import com.whulri.geodata.common.vo.PagedResultOutput;
import com.whulri.geodata.datarelease.service.IMapService;
import lombok.extern.slf4j.Slf4j;
import com.whulri.geodata.common.bridge.BeanUtil;
import com.whulri.geodata.common.bridge.Func;
import com.whulri.geodata.common.bridge.UserContextUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class MapServiceImpl extends ServiceImpl<MapMapper, MapEntity> implements IMapService {

    @Resource
    private MapMapper mapMapper;

    @Resource
    private DictDataCodeMapper dictDataCodeMapper;

    @Resource
    private DataTypeMapper dataTypeMapper;

    @Resource
    private TagRelationMapper tagRelationMapper;

    @Resource
    private DataTagMapper dataTagMapper;

    @Resource
    private MapRelationMapper mapRelationMapper;

    @Resource
    private LayerMapper layerMapper;

    @Resource
    private FileInfoMapper fileInfoMapper;

    @Resource
    private ThumbnailHelper thumbnailHelper;

    @Resource
    private IOperateLogService operateLogService;

    @Resource
    private GeoServer geoServer;

    @Resource
    private AppSettingConfig appSettingConfig;

    @Resource
    private LayerFieldMapper layerFieldMapper;

    @Resource
    private DynamicTableMapper dynamicTableMapper;

    @Resource
    private DataStyleMapper dataStyleMapper;


    @Override
    public ListResultOutput<MapDto> getAllList() {
        try {
            List<MapEntity> query = this.list();
            List<MapDto> copy = BeanUtil.copy(query, MapDto.class);
            ArrayList<MapDto> mapDtos = new ArrayList<>();
            if (copy != null && !copy.isEmpty()) {
                for (MapDto mapDto : copy) {
                    if (mapDto.getMapScale() != null) {
                        mapDto.setMapScaleName(getDetailCodeName(mapDto.getMapScale()));
                    }
                    if (mapDto.getSpatialRefence() != null) {
                        mapDto.setSpatialRefenceName(getDetailCodeName(mapDto.getSpatialRefence()));
                    }
                    if (mapDto.getMapType() != null) {
                        try {
                            DataTypeEntity dataTypeEntity = dataTypeMapper.selectById(mapDto.getMapType());
                            mapDto.setMapType(dataTypeEntity.getTypeName());
                        } catch (Exception e) {
                            mapDto.setMapType("");
                        }
                    }
                    mapDto.setMapTag(getMultiTagNameByMapID(mapDto.getId()));
                    mapDto.setImgSrc("/Thumbnail/map/" + mapDto.getMapEnName() + ".png");
                    mapDtos.add(mapDto);
                }
            }
            return new ListResultOutput<>(mapDtos);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ListResultOutput<MapDto> getAllListByName(MapInputDto input) {
        ListResultOutput<MapDto> list;
        try {
            String mapType = input.getMapType();
            String tag = input.getMapTag();
            String name = input.getMapName();
            String mapID = "";
            String type = "";

            if (Func.isNotEmpty(tag)) {
                mapID = getMultiLayerIDByTag(tag);
            }

            if (Func.isNotEmpty(mapType)) {
                type = getMultiChildTypeByType(mapType);
            }

            List<MapEntity> mapUserData = getDataByUserCode(input.getCreateUserId(), name, type, mapID, "");
            List<DataTypeEntity> listMapType = dataTypeMapper.selectList(new LambdaQueryWrapper<>());
            List<DictDataCodeEntity> listMapRefence = dictDataCodeMapper.selectList(new LambdaQueryWrapper<>());
            List<DictDataCodeEntity> listMapScale = dictDataCodeMapper.selectList(new LambdaQueryWrapper<>());

            List<MapDto> result = mapUserData.stream()
                    .map(l -> {
                        MapDto dto = new MapDto();
                        DataTypeEntity dataType = listMapType.stream()
                                .filter(t -> Objects.equals(t.getId(), l.getMapType()))
                                .findFirst()
                                .orElse(null);

                        DictDataCodeEntity refence = listMapRefence.stream()
                                .filter(r -> Objects.equals(r.getCodeValue(), l.getSpatialRefence()))
                                .findFirst()
                                .orElse(null);

                        DictDataCodeEntity scale = listMapScale.stream()
                                .filter(s -> Objects.equals(s.getCodeValue(), l.getMapScale()))
                                .findFirst()
                                .orElse(null);

                        BeanUtils.copyProperties(l, dto);
                        dto.setMapType(dataType == null ? "" : dataType.getTypeName());
                        dto.setMapScaleName(scale == null ? "" : scale.getCodeName());
                        dto.setSpatialRefenceName(refence == null ? "" : refence.getCodeName());
                        dto.setImgSrc("/Thumbnail/map/" + l.getMapEnName() + ".png");
                        dto.setMaxDegLat("");
                        dto.setMinDegLat("");
                        dto.setMinDegLon("");
                        dto.setMaxDegLon("");

                        return dto;
                    })
                    .sorted(Comparator.comparing(MapDto::getCreateDT).reversed())
                    .collect(Collectors.toList());

            return new ListResultOutput<>(result);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public PagedResultOutput<MapDto> getPageListByName(MapInputDto input, int PageSize, int PageIndex) {
        try {
            // 未显式传用户时取当前登录用户，登录态为空回退默认用户 admin（先兜底，避免下方 equals 空指针）
            input.setCreateUserId(UserContextUtil.resolveUserCode(input.getCreateUserId()));
            String mapType = input.getMapType();
            String tag = input.getMapTag();
            String name = input.getMapName();
            String mapID = "";
            String type = "";
            String ttype = input.getType();
            String dType = input.getDictCodeID();

            // 判断是否是管理员
            if (input.getCreateUserId().equals("SYSTEM")) {
                ttype = "0";
            } else {
                ttype = "1";
            }

            if (Func.isNotEmpty(tag)) {
                mapID = getMultiLayerIDByTag(tag);
            }

            type = getMultiChildTypeByType(mapType, ttype, dType, input.getCreateUserId());

            List<MapDto> list = new ArrayList<>();
            List<MapEntity> mapUserData = getDataByUserCode(input.getCreateUserId(), name, type, mapID, ttype);
            List<DataTypeEntity> listMapType = dataTypeMapper.selectList(new LambdaQueryWrapper<>());
            int count = 0;
            if (Func.isNotEmpty(mapUserData)) {
                List<DictDataCodeEntity> listMapRefence = dictDataCodeMapper.selectList(new LambdaQueryWrapper<>());
                List<DictDataCodeEntity> listMapScale = dictDataCodeMapper.selectList(new LambdaQueryWrapper<>());

                // 处理地图数据
                List<MapDto> query = mapUserData.stream()
                        .map(l -> {
                            MapDto dto = new MapDto();
                            DataTypeEntity dataType = listMapType.stream()
                                    .filter(t -> Objects.equals(t.getId(), l.getMapType()))
                                    .findFirst()
                                    .orElse(null);

                            DictDataCodeEntity refence = listMapRefence.stream()
                                    .filter(r -> Objects.equals(r.getCodeValue(), l.getSpatialRefence()))
                                    .findFirst()
                                    .orElse(null);

                            DictDataCodeEntity scale = listMapScale.stream()
                                    .filter(s -> Objects.equals(s.getCodeValue(), l.getMapScale()))
                                    .findFirst()
                                    .orElse(null);

                            BeanUtils.copyProperties(l, dto);
                            dto.setMapType(dataType == null ? "" : dataType.getTypeName());
                            dto.setMapScaleName(scale == null ? "" : scale.getCodeName());
                            dto.setSpatialRefenceName(refence == null ? "" : refence.getCodeName());
                            dto.setImgSrc("/Thumbnail/map/" + l.getMapEnName() + ".png");
                            dto.setDataType("2");

                            return dto;
                        })
                        .sorted(Comparator.comparing(MapDto::getCreateDT).reversed())
                        .collect(Collectors.toList());

                count = query.size();
                List<MapDto> result = query.stream()
                        .skip((long) (PageIndex - 1) * PageSize)
                        .limit(PageSize)
                        .collect(Collectors.toList());

                if (Func.isNotEmpty(result)) {
                    for (MapDto dto : result) {
                        // 设置文件大小
                        dto.setFileSize(Integer.parseInt(getFileSize(dto.getId())));
                        // 设置标签
                        dto.setMapTag(getMultiTagNameByMapID(dto.getId()));
                        // 设置经纬度
                        if (dto.getMaxY() != null) dto.setMaxDegLat(convertBBox(dto.getMaxY().doubleValue()));
                        if (dto.getMinY() != null) dto.setMinDegLat(convertBBox(dto.getMinY().doubleValue()));
                        if (dto.getMaxX() != null) dto.setMaxDegLon(convertBBox(dto.getMaxX().doubleValue()));
                        if (dto.getMinX() != null) dto.setMinDegLon(convertBBox(dto.getMinX().doubleValue()));
                        list.add(dto);
                    }
                }
            }

            // 处理文件数据
            int count1 = 0;
            List<FileInfoEntity> fileUserData = getFileDataByUserCodeAsync(input.getCreateUserId(), name, type, mapID, "");
            if (fileUserData != null) {
                count1 = fileUserData.size();
                List<FileInfoEntity> result = fileUserData.stream()
                        .skip((long) (PageIndex - 1) * PageSize)
                        .limit(PageSize)
                        .collect(Collectors.toList());

                result.forEach(entity -> {
                    MapDto dto = new MapDto();
                    dto.setId(entity.getId());
                    dto.setMapName(entity.getDataName());
                    dto.setMapEnName(entity.getFileName());
                    dto.setMapDesc(entity.getDatasrcource());
                    dto.setMapType(entity.getCatName());
                    dto.setMapPublishAddress(entity.getFileExt());
                    dto.setMapStatus(entity.getFileSize());
                    dto.setMapTag("");
                    dto.setPublishDT(entity.getCreateDate());
                    dto.setCreateUserId(entity.getCreatorId());
                    dto.setCreateUserName(entity.getCreator());
                    dto.setCreateDT(entity.getCreateDate());
                    dto.setDataType("3");
                    list.add(dto);
                });
            }

            // 排序并分页
            List<MapDto> sortedList = list.stream()
                    .sorted(Comparator.comparing(MapDto::getCreateDT).reversed())
                    .collect(Collectors.toList());

            return new PagedResultOutput<MapDto>(count + count1, sortedList);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public MapOutputDto getDetailById(String id) {
        try {
            MapEntity map = mapMapper.selectById(id);
            MapOutputDto result = BeanUtil.copy(map, MapOutputDto.class);

            if (Func.isNotBlank(result.getMapScale())) {
                result.setMapScaleName(getDetailCodeName(result.getMapScale()));
            }

            if (Func.isNotBlank(result.getSpatialRefence())) {
                result.setSpatialRefenceName(getDetailCodeName(result.getSpatialRefence()));
            }

            if (Func.isNotBlank(result.getMapType())) {
                result.setMapTypeCode(result.getMapType());
                result.setMapType(getMapType(result.getMapType()));
            }

            result.setMaxYName(result.getMaxY() != null ? convertBBox(Double.parseDouble(String.valueOf(result.getMaxY()))) : "");
            result.setMinYName(result.getMinY() != null ? convertBBox(Double.parseDouble(String.valueOf(result.getMinY()))) : "");
            result.setMaxXName(result.getMaxX() != null ? convertBBox(Double.parseDouble(String.valueOf(result.getMaxX()))) : "");
            result.setMinXName(result.getMinX() != null ? convertBBox(Double.parseDouble(String.valueOf(result.getMinX()))) : "");

            result.setMapTag(getMultiTagNameByMapID(id));

            return result;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public MapDto insert(MapInputDto input) {
        try {
            input.setId(UUID.randomUUID().toString());
            MapEntity entity = BeanUtil.copy(input, MapEntity.class);

            ChineseConvert chn = new ChineseConvert();
            String mapEnName = chn.getPinyinInitials(entity.getMapName());
            String lastIndex = LocalDateTime.now().format(DateTimeFormatter.ofPattern("ddHHmmss"));
            mapMapper.insert(entity);
            MapDto result = BeanUtil.copy(entity, MapDto.class);
            operateLogService.writeOperateLog(input.getId(), input.getModifyUserId(), 1002, 1101, 1201, 1411, "(" + input.getMapName() + ")");
            return result;
        } catch (Exception e) {
            operateLogService.writeOperateLog(input.getId(), input.getModifyUserId(), 1002, 1101, 1202, 1412, "(" + input.getMapName() + ")");
            throw new RuntimeException(e);
        }
    }

    @Override
    public MapDto update(MapInputDto input) {
        try {
            updateMapBBox(input.getId());

            MapEntity entity = mapMapper.selectById(input.getId());
            entity.setMapPublishAddress(input.getMapPublishAddress());
            entity.setMapStatus(input.getMapStatus());
            entity.setMapDesc(input.getMapDesc());
            entity.setMapType(input.getMapType());
            entity.setMapTag(input.getMapTag());
            entity.setPublishDT(input.getPublishDT());
            entity.setSortCode(input.getSortCode());
            entity.setEnabledMark(input.getEnabledMark());
            entity.setDeleteMark(input.getDeleteMark());
            entity.setCreateUserId(input.getCreateUserId());
            entity.setCreateUserName(input.getCreateUserId());
            entity.setModifyUserId(input.getModifyUserId());
            entity.setModifyUserName(input.getModifyUserName());
            entity.setModifyDate(LocalDateTime.now());
            entity.setMapScale(input.getMapScale());
            entity.setSpatialRefence(input.getSpatialRefence());
            entity.setMapLegend(input.getMapLegend());

            entity.setServiceObject(input.getServiceObject());
            entity.setDataSource(input.getDataSource());
            entity.setDataDesc(input.getDataDesc());
            entity.setDataConfidential(input.getDataConfidential());
            entity.setKeywords(input.getKeywords());
            entity.setDataTypes(input.getDataTypes());
            entity.setDataAccuracy(input.getDataAccuracy());

            mapMapper.updateById(entity);
            MapDto result = BeanUtil.copy(entity, MapDto.class);
            operateLogService.writeOperateLog(input.getId(), input.getCreateUserId(), 1002, 1102, 1201, 1431, "(" + input.getMapName() + ")");
            return result;
        } catch (Exception e) {
            operateLogService.writeOperateLog(input.getId(), input.getCreateUserId(), 1002, 1102, 1202, 1432, "(" + input.getMapName() + ")");
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean updateMapLegend(String mapID, String legendUrlPath) {
        try {
            MapEntity entity = mapMapper.selectById(mapID);
            entity.setMapLegend(legendUrlPath);
            mapMapper.updateById(entity);
            MapDto result = BeanUtil.copy(entity, MapDto.class);
            return true;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public MapDto updateDataType(String mapID, String dataTypeID) {
        try {
            MapEntity entity = mapMapper.selectById(mapID);
            entity.setMapType(dataTypeID);
            mapMapper.updateById(entity);
            return BeanUtil.copy(entity, MapDto.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean updateMapBBox(String mapId) {
        try {
            // 获取地图关联信息
            List<MapRelationEntity> list = mapRelationMapper.selectList(new LambdaQueryWrapper<MapRelationEntity>()
                    .eq(MapRelationEntity::getMapId, mapId));
            MapEntity map = mapMapper.selectById(mapId);

            if (map != null && list != null && !list.isEmpty()) {
                // 计算边界范围
                Map<String, BigDecimal> bbox = new HashMap<>();
                for (int i = 0; i < list.size(); i++) {
                    LayerContentEntity layer = layerMapper.selectById(list.get(i).getDataConfigID());
                    if (layer == null) continue;

                    if (i == 0) {
                        bbox.put("MaxX", layer.getMaxX());
                        bbox.put("MinX", layer.getMinX());
                        bbox.put("MaxY", layer.getMaxY());
                        bbox.put("MinY", layer.getMinY());
                    } else {
                        if (layer.getMaxX().compareTo(bbox.get("MaxX")) > 0) {
                            bbox.put("MaxX", layer.getMaxX());
                        }
                        if (layer.getMaxY().compareTo(bbox.get("MaxY")) > 0) {
                            bbox.put("MaxY", layer.getMaxY());
                        }
                        if (layer.getMinX().compareTo(bbox.get("MinX")) < 0) {
                            bbox.put("MinX", layer.getMinX());
                        }
                        if (layer.getMinY().compareTo(bbox.get("MinY")) < 0) {
                            bbox.put("MinY", layer.getMinY());
                        }
                    }
                }

                // 更新地图边界信息
                map.setMaxX(bbox.get("MaxX"));
                map.setMinX(bbox.get("MinX"));
                map.setMaxY(bbox.get("MaxY"));
                map.setMinY(bbox.get("MinY"));

                mapMapper.updateById(map);

                if (Func.isNotEmpty(appSettingConfig.getIsAutoCache()) && appSettingConfig.getIsAutoCache().equals("1")) {
                    geoServer.terminatingTask(map.getMapEnName());
                    geoServer.tileMap(map.getMapEnName(), null);
                }
                String strBBox = map.getMinX() + "," + map.getMinY() + "," + map.getMaxX() + "," + map.getMaxY();
                // 使用注入的 ThumbnailHelper（手工 new 会导致 @Resource 注入失效）
                String imagePath = thumbnailHelper.createThumbnail(map.getMapEnName(), "map", strBBox, "", "");

                if (Func.isEmpty(imagePath)) {
                    return false;
                } else {
                    return true;
                }
            }
            return true;
        } catch (Exception ex) {
            log.error("更新地图边界范围失败", ex);
            return false;
        }
    }

    @Override
    public boolean delete(String id, String user) {
        try {
            // 删除标签关系数据
            tagRelationMapper.delete(new LambdaQueryWrapper<TagRelationEntity>().eq(TagRelationEntity::getMapID, id));
            // 删除地图关系表
            mapRelationMapper.delete(new LambdaQueryWrapper<MapRelationEntity>().eq(MapRelationEntity::getMapId, id));
            // 删除GeoServer地图
            deleteMap(id);

            mapMapper.deleteById(id);

            operateLogService.writeOperateLog(id, user, 1002, 1105, 1201, 1421, null);
            return true;
        } catch (Exception e) {
            operateLogService.writeOperateLog(id, user, 1002, 1105, 1202, 1422, null);
            return false;
        }
    }

    @Override
    public String getLayerAttrByLayerPt(String layerID, Float lon, Float lat, Float tolerance) {
        String sql1 = "select %s,ST_Asewkt(geom) geom from %s where ST_Contains(geom,ST_PointFromText('POINT(" + lon + " " + lat + ")'))";

        if (tolerance == null) {
            // 从配置文件获取默认容差值
            tolerance = Float.valueOf(appSettingConfig.getGISTolerance());
        }

        String sql2 = "select %s,ST_Asewkt(geom) as geom from %s where st_dwithin(ST_GeomFromText('POINT(" + lon + " " + lat + ")'),geom," + tolerance + ")=true";

        return getLayerAttrByLayerId(layerID, "Pt", sql1, sql2);
    }

    @Override
    public ListResultOutput<DataTypeDto> getTypeInstance(String tagid) {
        try {
            List<TagRelationEntity> tagRelationEntities = tagRelationMapper.selectList(new LambdaQueryWrapper<TagRelationEntity>().eq(TagRelationEntity::getDataTagID, tagid));
            List<String> temp = tagRelationEntities.stream()
                    .map(TagRelationEntity::getMapID)
                    .collect(Collectors.toList());

            List<MapEntity> mapEntities = mapMapper.selectList(new LambdaQueryWrapper<MapEntity>().in(MapEntity::getId, temp));
            List<String> mapType = mapEntities.stream()
                    .map(MapEntity::getMapType)
                    .collect(Collectors.toList());

            List<DataTypeEntity> dataTypeEntities = dataTypeMapper.selectList(new LambdaQueryWrapper<DataTypeEntity>().in(DataTypeEntity::getId, mapType));
            List<DataTypeDto> copy = BeanUtil.copy(dataTypeEntities, DataTypeDto.class);
            return new ListResultOutput<DataTypeDto>(copy);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ListResultOutput<MapDto> getMaps(String tagid, String typeid) {
        try {
            List<MapDto> result = null;
            List<MapDto> tagidmap = new ArrayList<>();
            List<MapDto> typemap = new ArrayList<>();

            if (Func.isNotEmpty(tagid)) {
                List<TagRelationEntity> tagRelationEntities = tagRelationMapper.selectList(new LambdaQueryWrapper<TagRelationEntity>()
                        .eq(TagRelationEntity::getDataTagID, tagid));
                List<String> temp = tagRelationEntities.stream()
                        .map(TagRelationEntity::getMapID)
                        .collect(Collectors.toList());

                List<MapEntity> mapEntities = mapMapper.selectList(new LambdaQueryWrapper<MapEntity>().in(MapEntity::getId, temp));
                tagidmap = BeanUtil.copy(mapEntities, MapDto.class);
            }
            if (Func.isNotEmpty(typeid)) {
                List<MapEntity> mapEntities = mapMapper.selectList(new LambdaQueryWrapper<MapEntity>().eq(MapEntity::getMapType, typeid));
                typemap = BeanUtil.copy(mapEntities, MapDto.class);
            }
            result = Stream.concat(tagidmap.stream(), typemap.stream())
                    .distinct()
                    .collect(Collectors.toList());

            return new ListResultOutput<>(result);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<DataStyleEntity> getDataStyle(String MapId) {
        try {
            MapEntity map = mapMapper.selectById(MapId);
            List<MapRelationEntity> list = mapRelationMapper.selectList(new LambdaQueryWrapper<MapRelationEntity>().eq(MapRelationEntity::getMapId, MapId));
            List<String> collect = list.stream().map(MapRelationEntity::getDataStyleID).collect(Collectors.toList());
            return dataStyleMapper.selectList(new LambdaQueryWrapper<DataStyleEntity>()
                    .in(DataStyleEntity::getId, collect)
                    .or()
                    .eq(DataStyleEntity::getStyleName, map.getMapName()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Map<String, Object>> getAllDataToTable(String username) {
        try {
            List<Map<String, Object>> result = new ArrayList<>();
            List<String> td = new ArrayList<>();
            List<MapEntity> mapEntities = mapMapper.selectList(new LambdaQueryWrapper<MapEntity>().eq(MapEntity::getCreateUserId, username)
                    .or()
                    .eq(MapEntity::getCreateUserId, "SYSTEM")
                    .or()
                    .eq(MapEntity::getDataConfidential, "47c52dbe-455e-4ab8-812a-b14bddc5ae38"));
            List<DataTypeEntity> dataTypeEntities = dataTypeMapper.selectList(new LambdaQueryWrapper<DataTypeEntity>()
                    .eq(DataTypeEntity::getDictCodeID, DictionaryIds.CATALOG_DATA_CATEGORY));
            List<MapRelationEntity> mapRelationEntities = mapRelationMapper.selectList(new LambdaQueryWrapper<MapRelationEntity>());
            List<LayerContentEntity> layerContentEntities = layerMapper.selectList(new LambdaQueryWrapper<LayerContentEntity>());
            td = getdatatype(dataTypeEntities);
            if (Func.isNotEmpty(td)) {
                int k = 1;
                for (int j = 0; j < td.size(); j++) {
                    Map<String, Object> dat = new HashMap<>();
                    List<String> tc = new ArrayList<>();
                    int i = 1;
                    for (MapEntity m : mapEntities) {
                        if (getDataName(m.getMapType(), td, dataTypeEntities).equals(td.get(j))) {
                            JSONObject data = new JSONObject();
                            data.put("serialnum", String.valueOf(k));
                            data.put("typenumber", String.valueOf(j + 1));
                            data.put("datanum", (j + 1) + "-" + i);
                            data.put("mapname", m.getMapName());
                            List<String> finalTd = td;
                            int finalJ = j;
                            data.put("datatype", Objects.requireNonNull(dataTypeEntities.stream().filter(s -> s.getId().equals(finalTd.get(finalJ))).findFirst().orElse(null)).getTypeName());
                            data.put("dataaccuracy", m.getDataAccuracy());
                            data.put("dataformat", GetMapformat(m.getId(), mapRelationEntities, layerContentEntities));
                            data.put("rank", getdatatypes(td.get(finalJ), m.getMapType(), dataTypeEntities));
                            data.put("datasource", m.getDataSource());
                            data.put("mapdesc", m.getMapDesc());
                            tc.add(data.toString());
                            i++;
                            k++;
                        }
                    }
                    dat.put("items", tc);
                    dat.put("counts", tc.size());
                    result.add(dat);
                }
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getdatatypes(String td, String mapType, List<DataTypeEntity> list) {
        List<DataTypeEntity> collect = list.stream().filter(s -> s.getParentID().equals(td)).collect(Collectors.toList());
        String result = "";
        for (DataTypeEntity t : collect) {
            result = matchings(t, list, mapType);
            if (Func.isNotEmpty(result)) {
                return result;
            }
        }
        return result;
    }

    public String matchings(DataTypeEntity t,
                            List<DataTypeEntity> all,
                            String mapTypeId) {
        if (t == null || mapTypeId == null || all == null) {
            return null;
        }

        // 预构建 id -> node 的映射（O(n) 预处理，后续 O(1) 查询）
        Map<String, DataTypeEntity> nodeMap = all.stream()
                .filter(node -> node.getId() != null)
                .collect(Collectors.toMap(DataTypeEntity::getId, Function.identity()));

        String currentId = mapTypeId;
        while (currentId != null && !currentId.isEmpty()) {
            if (currentId.equals(t.getId())) {
                return t.getTypeName(); // 找到匹配，立即返回
            }

            DataTypeEntity currentNode = nodeMap.get(currentId);
            if (currentNode != null) {
                currentId = currentNode.getParentID(); // 向上走
            } else {
                break; // 节点不存在，退出
            }
        }

        return null; // 未找到匹配
    }


    private String GetMapformat(String id, List<MapRelationEntity> mapRelations, List<LayerContentEntity> layers) {
        List<MapRelationEntity> relations = mapRelations.stream()
                .filter(s -> id.contains(s.getMapId()))
                .collect(Collectors.toList());

        List<String> collect = relations.stream()
                .map(MapRelationEntity::getDataConfigID)
                .collect(Collectors.toList());

        List<LayerContentEntity> layerList = layers.stream()
                .filter(s -> collect.contains(s.getId()))
                .collect(Collectors.toList());

        if (!layerList.isEmpty()) {
            for (LayerContentEntity layer : layerList) {
                if ("1".equals(layer.getUploadFileType())) {
                    return "矢量/shp";
                } else if ("2".equals(layer.getUploadFileType())) {
                    return "栅格/tif";
                }
            }
        }
        return "";
    }


    public String getDataName(String id, List<String> targetIds, List<DataTypeEntity> allNodes) {
        // 1. 边界检查
        if (id == null || id.isEmpty() || targetIds == null || allNodes == null) {
            return "";
        }

        // 2. 构建 ID -> 节点 的映射（O(n) 预处理，后续 O(1) 查询）
        Map<String, DataTypeEntity> nodeMap = new HashMap<>();
        for (DataTypeEntity node : allNodes) {
            if (node.getId() != null) {
                nodeMap.put(node.getId(), node);
            }
        }

        // 3. 将目标列表转为 Set（O(1) 查找）
        Set<String> targetSet = new HashSet<>(targetIds);

        // 4. 从下往上遍历祖先链
        String currentId = id;
        while (currentId != null && !currentId.isEmpty()) {
            // 4.1 如果当前 ID 在目标列表中 → 立即返回！
            if (targetSet.contains(currentId)) {
                return currentId;
            }

            // 4.2 向上移动到父节点
            DataTypeEntity currentNode = nodeMap.get(currentId);
            if (currentNode != null) {
                currentId = currentNode.getParentID(); // 可能为 null 或 ""
            } else {
                break; // 当前节点不存在，退出
            }
        }
        // 5. 未找到匹配
        return "";
    }


    private List<String> getdatatype(List<DataTypeEntity> td) {
        List<DataTypeEntity> temp = td.stream()
                .filter(s -> s.getParentID().equals("0"))
                .sorted(Comparator.comparing(DataTypeEntity::getCodeSort))
                .collect(Collectors.toList());
        if (Func.isNotEmpty(temp)) {
            return temp.stream().map(DataTypeEntity::getId).collect(Collectors.toList());
        }
        return null;
    }

    /**
     * 框选查询结果
     *
     * @param layerId
     * @param queryType
     * @param sql1
     * @param sql2
     * @return
     */
    private String getLayerAttrByLayerId(String layerId, String queryType, String sql1, String sql2) {
        try {
            boolean success = false;
            StringBuilder result = new StringBuilder("[");

            String[] layerList = layerId.split(",");
            if (layerList.length > 0) {
                for (String item : layerList) {
                    // Get layer information
                    String layerName = "", layerAttrTable = "", layerDataType = "";
                    List<Map<String, String>> query = layerMapper.selectLayerInfo(item);

                    if (query != null) {
                        if (!query.isEmpty()) {
                            for (Map<String, String> map : query) {
                                layerName = map.get("layerName");
                                layerAttrTable = map.get("layerAttrTable");
                                layerDataType = map.get("codeValue");
                            }
                        }
                    }

                    List<LayerFieldEntity> fields = layerFieldMapper.selectList(new LambdaQueryWrapper<LayerFieldEntity>()
                            .eq(LayerFieldEntity::getLayerID, item));

                    StringBuilder fieldNames = new StringBuilder("\"sid\"");
                    StringBuilder chineseNames = new StringBuilder();

                    for (LayerFieldEntity field : fields) {
                        fieldNames.append(",\"").append(field.getAttributeName()).append("\"");
                        if (field.getChineseName() != null && !field.getChineseName().isEmpty()) {
                            if (chineseNames.length() > 0) {
                                chineseNames.append("#");
                            }
                            chineseNames.append(field.getChineseName());
                        }
                    }

                    // 构建SQL查询
                    String tableName = "public." + layerAttrTable;
                    String sql = "POLYGON".equalsIgnoreCase(layerDataType) && queryType.equals("Pt")
                            ? String.format(sql1, fieldNames.toString(), tableName)
                            : String.format(sql2, fieldNames.toString(), tableName);

                    // 执行查询
                    List<Map<String, Object>> data = dynamicTableMapper.dynamicExecute(sql);

                    // 转换列名
                    for (LayerFieldEntity field : fields) {
                        if (field.getChineseName() != null && !field.getChineseName().isEmpty()) {
                            for (Map<String, Object> row : data) {
                                Object value = row.remove(field.getAttributeName());
                                if (value != null) {
                                    row.put(field.getChineseName(), value);
                                }
                            }
                        }
                    }
                    String mapMaskField = appSettingConfig.getMapMaskField();
                    String[] split = mapMaskField.split(",");
                    for (String s : split) {
                        for (Map<String, Object> row : data) {
                            try {
                                row.remove(s);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }

                    List<Map<String, Object>> processedData = new ArrayList<>();
                    for (Map<String, Object> row : data) {
                        Map<String, Object> newRow = new LinkedHashMap<>(); // 保持顺序

                        // 重命名 + 过滤
                        for (Map.Entry<String, Object> entry : row.entrySet()) {
                            String key = entry.getKey();
                            Object value = entry.getValue();

                            // 跳过含 "_"、以 "F" 开头、以 "1" 结尾的字段
                            if (key.contains("_") || key.startsWith("F") || key.endsWith("1")) {
                                continue;
                            }
                            // todo
                            // 重命名为中文
//                            String finalKey = enToCnMap.getOrDefault(key, key);
//                            newRow.put(finalKey, value);
                        }
                        processedData.add(newRow);
                    }

                    if (processedData.isEmpty()) continue;


                    // 构建JSON响应
                    if (!data.isEmpty()) {
                        success = true;
                        result.append("{")
                                .append("\"layer\":\"").append(layerAttrTable).append("\",")
                                .append("\"layerName\":\"").append(layerName).append("\",")
                                .append("\"layerId\":\"").append(item).append("\",")
                                .append("\"attr\":\"").append(chineseNames).append("\",")
                                .append("\"data\":").append(JSON.toJSONString(data))
                                .append("},");
                    }
                }


                if (success) {
                    result.setLength(result.length() - 1);
                }
            }
            result.append("]");

            return success ? result.toString() : "";

        } catch (Exception ex) {
            log.error("获取图层属性失败!!!");
            return "[]";
        }
    }


    private boolean deleteMap(String mapId) {
        MapEntity map = mapMapper.selectById(mapId);
        if (map != null) {
            try {
                // 清空缓存切片
                String mapName = appSettingConfig.getGeoWorkspace() + ":" + map.getMapName();
                List<String> list = geoServer.getLayerGridSet(mapName);
                int start = 0, stop = 14;
                for (String s : list) {
                    if (Func.isNotEmpty(appSettingConfig.getGridSetName())) {
                        Set<String> gridSet = Arrays.stream(appSettingConfig.getGridSetName().split(","))
                                .map(String::trim)
                                .map(String::toLowerCase)
                                .collect(Collectors.toSet());
                        if (gridSet.contains(s)) {
                            geoServer.sendEmptyTilesTask(mapName, s, appSettingConfig.getZoomStart(), appSettingConfig.getZoomStop(), 1, null);
                        }
                    } else {
                        geoServer.sendEmptyTilesTask(mapName, s, appSettingConfig.getZoomStart(), appSettingConfig.getZoomStop(), 1, null);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            if (geoServer.isExsitLayerGroup(map.getMapEnName(), appSettingConfig.getGeoWorkspace())) {
                return geoServer.deleteLayerGroup(map.getMapEnName(), appSettingConfig.getGeoWorkspace());
            } else {
                return true;
            }
        }
        return true;
    }


    private String getMapType(String id) {
        try {
            DataTypeEntity entity = dataTypeMapper.selectById(id);
            if (entity != null && entity.getId() != null) {
                return entity.getTypeName();
            }
            return "";
        } catch (Exception ex) {
            return "";
        }
    }

    private List<FileInfoEntity> getFileDataByUserCodeAsync(String userCode, String name, String mapType, String
            tag, String ttype) {
        try {
            userCode = UserContextUtil.resolveUserCode(userCode);
            List<FileInfoEntity> query;
            if (Func.isNotEmpty(ttype) && ttype.equals("1")) {
                query = fileInfoMapper.selectList(new LambdaQueryWrapper<FileInfoEntity>()
                        .in(FileInfoEntity::getCreatorId, userCode)
                        .orderByDesc(FileInfoEntity::getCreateDate));
            } else {
                query = fileInfoMapper.selectList(new LambdaQueryWrapper<FileInfoEntity>()
                        .in(FileInfoEntity::getCreatorId, ConstHelper.CONST_SYSTEMNAME)
                        .orderByDesc(FileInfoEntity::getCreateDate));
            }

            if (Func.isNotEmpty(query)) {
                if (Func.isNotEmpty(name)) {
                    query = query.stream()
                            .filter(s -> Func.isNotEmpty(s.getFileName()) && s.getFileName().contains(name))
                            .collect(Collectors.toList());
                }
                if (Func.isNotEmpty(mapType)) {
                    query = query.stream()
                            .filter(s -> Func.isNotEmpty(s.getCatId()) && mapType.contains(s.getCatId()))
                            .collect(Collectors.toList());
                }
                if (Func.isNotEmpty(tag)) {
                    query = query.stream()
                            .filter(s -> Func.isNotEmpty(s.getId()) && tag.contains(s.getId()))
                            .collect(Collectors.toList());
                }
            }
            return query;
        } catch (Exception e) {
            return null;
        }
    }


    private String convertBBox(double bbox) {
        if (Double.isNaN(bbox)) {
            return "";
        }
        try {
            double d = Math.abs(bbox);
            double m = (60 * (d - Math.floor(d)));
            double s = (60 * (m - Math.floor(m)));
            return String.format("%d°%d'%d\"", (int) d * (int) Math.signum(bbox), (int) m, Math.round(s));
        } catch (Exception ex) {
            return "";
        }
    }

    private String getFileSize(String mapId) {
        try {
            Long ret = 0L;
            List<MapRelationEntity> mapRelations = mapRelationMapper.selectList(new LambdaQueryWrapper<MapRelationEntity>()
                    .eq(MapRelationEntity::getMapId, mapId));

            if (Func.isEmpty(mapRelations)) {
                return "0";
            }

            List<String> temp = mapRelations.stream()
                    .map(MapRelationEntity::getDataConfigID)
                    .collect(Collectors.toList());

            List<LayerContentEntity> layerContents = layerMapper.selectList(new LambdaQueryWrapper<LayerContentEntity>()
                    .in(LayerContentEntity::getId, temp));

            // Calculate total size
            for (LayerContentEntity layer : layerContents) {
                if (layer.getFileSize() != null) {
                    ret += layer.getFileSize();
                } else if (Func.isNotEmpty(layer.getUploadFileName())) {
                    String[] files = layer.getUploadFileName().split("\\*");
                    for (String file : files) {
                        if (Func.isEmpty(file)) continue;

                        File f = new File(file);
                        String ext = getFileExtension(file).toLowerCase();

                        if (f.exists() && ".tif".equals(ext)) {
                            ret += (int) (f.length() / FileHelper.SIZE);
                        } else if (f.exists() && isShapeFile(ext)) {
                            String fileName = getFileNameWithoutExtension(file);
                            String directory = getDirectoryName(file);

                            List<String> paths = Arrays.asList(
                                    directory + File.separator + fileName + ".shp",
                                    directory + File.separator + fileName + ".prj",
                                    directory + File.separator + fileName + ".shx",
                                    directory + File.separator + fileName + ".dbf"
                            );

                            for (String path : paths) {
                                File shapeFile = new File(path);
                                if (shapeFile.exists()) {
                                    ret += (int) (shapeFile.length() / FileHelper.SIZE);
                                }
                            }
                        }
                    }
                }
            }

            return String.valueOf(ret);
        } catch (Exception ex) {
            return "0";
        }
    }

    private boolean isShapeFile(String extension) {
        return ".shp".equals(extension) || ".prj".equals(extension) ||
                ".shx".equals(extension) || ".dbf".equals(extension);
    }

    private String getFileExtension(String path) {
        int lastDot = path.lastIndexOf('.');
        return lastDot > -1 ? path.substring(lastDot) : "";
    }

    private String getFileNameWithoutExtension(String path) {
        String fileName = new File(path).getName();
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > -1 ? fileName.substring(0, lastDot) : fileName;
    }

    private String getDirectoryName(String path) {
        return new File(path).getParent();
    }


    private List<MapEntity> getDataByUserCode(String userCode, String name, String mapType, String tag, String
            ttype) {
        try {
            userCode = UserContextUtil.resolveUserCode(userCode);

            List<MapEntity> query;
            if (Func.isNotEmpty(ttype) && "1".equals(ttype)) {
                query = mapMapper.selectList(new LambdaQueryWrapper<MapEntity>()
                        .in(MapEntity::getCreateUserId, userCode)
                        .or().in(MapEntity::getCreateUserId, ConstHelper.CONST_SYSTEMNAME)
                        .or().eq(MapEntity::getDataConfidential, "47c52dbe-455e-4ab8-812a-b14bddc5ae38"));
            } else {
                query = mapMapper.selectList(new LambdaQueryWrapper<MapEntity>()
                        .in(MapEntity::getCreateUserId, ConstHelper.CONST_SYSTEMNAME)
                        .or().eq(MapEntity::getDataConfidential, "47c52dbe-455e-4ab8-812a-b14bddc5ae38"));
            }

            if (Func.isNotEmpty(query)) {
                if (Func.isNotEmpty(name)) {
                    query = query.stream()
                            .filter(s -> Func.isNotEmpty(s.getMapName()) && s.getMapName().contains(name))
                            .collect(Collectors.toList());
                }
                if (Func.isNotEmpty(mapType)) {
                    query = query.stream()
                            .filter(s -> Func.isNotEmpty(s.getMapType()) && mapType.contains(s.getMapType()))
                            .collect(Collectors.toList());
                }
                if (Func.isNotEmpty(tag)) {
                    query = query.stream()
                            .filter(s -> Func.isNotEmpty(s.getId()) && tag.contains(s.getId()))
                            .collect(Collectors.toList());
                }
            }
            return query;
        } catch (Exception ex) {
            return null;
        }
    }


    private String getMultiChildTypeByType(String typeId, String ttype, String dictCode, String user) {
        try {
            if (Func.isEmpty(typeId) && Func.isEmpty(dictCode)) {
                return "";
            }

            StringBuilder multiTypeId = new StringBuilder();
            if (Func.isNotEmpty(typeId)) {
                multiTypeId.append(typeId).append(",");
            }

            List<DataTypeEntity> query = new ArrayList<>();
            if (Func.isNotEmpty(typeId)) {
                List<DataTypeEntity> types = dataTypeMapper.selectList(new LambdaQueryWrapper<>());
                getChildNode(types, typeId, query);
            } else if (Func.isNotEmpty(dictCode)) {
                if (Func.isNotEmpty(ttype)) {
                    if ("0".equals(ttype)) {
                        query = dataTypeMapper.selectList(new LambdaQueryWrapper<DataTypeEntity>()
                                .eq(DataTypeEntity::getDictCodeID, dictCode)
                                .eq(DataTypeEntity::getCreateUser, ConstHelper.CONST_SYSTEMNAME));
                    } else {
                        query = dataTypeMapper.selectList(new LambdaQueryWrapper<DataTypeEntity>()
                                .eq(DataTypeEntity::getDictCodeID, dictCode)
                                .eq(DataTypeEntity::getCreateUser, user));
                    }
                } else {
                    query = dataTypeMapper.selectList(new LambdaQueryWrapper<DataTypeEntity>()
                            .eq(DataTypeEntity::getDictCodeID, dictCode));
                }
            }

            if (Func.isNotEmpty(query)) {
                for (DataTypeEntity item : query) {
                    multiTypeId.append(item.getId()).append(",");
                }
            }

            if (multiTypeId.length() > 0) {
                return multiTypeId.substring(0, multiTypeId.length() - 1);
            }
            return multiTypeId.toString();
        } catch (Exception ex) {
            return typeId;
        }
    }


    private void getChildNode(List<DataTypeEntity> list, String parentId, List<DataTypeEntity> result) {
        if (Func.isNotEmpty(parentId)) {
            List<DataTypeEntity> children = list.stream()
                    .filter(p -> Objects.equals(p.getParentID(), parentId))
                    .collect(Collectors.toList());

            if (!children.isEmpty()) {
                for (DataTypeEntity child : children) {
                    result.add(child);
                    getChildNode(list, child.getId(), result);
                }
            }
        }
    }

    private String getMultiChildTypeByType(String typeId) {
        return getMultiChildTypeByType(typeId, "", "", ConstHelper.CONST_SYSTEMNAME);
    }


    private String getMultiLayerIDByTag(String tagId) {
        try {
            StringBuilder mapId = new StringBuilder();
            List<TagRelationEntity> query = tagRelationMapper.selectList(new LambdaQueryWrapper<TagRelationEntity>()
                    .eq(TagRelationEntity::getDataTagID, tagId));

            if (Func.isNotEmpty(query)) {
                for (TagRelationEntity item : query) {
                    mapId.append(item.getMapID()).append(",");
                }
            }

            if (mapId.length() > 0) {
                return mapId.substring(0, mapId.length() - 1);
            }
            return "";
        } catch (Exception ex) {
            return "";
        }
    }


    private String getMultiTagNameByMapID(String mapLayerID) {
        try {
            StringBuilder tagName = new StringBuilder();
            List<TagRelationEntity> query = tagRelationMapper.selectList(new LambdaQueryWrapper<TagRelationEntity>().eq(TagRelationEntity::getMapID, mapLayerID));

            if (Func.isNotEmpty(query)) {
                for (TagRelationEntity item : query) {
                    DataTagEntity tag = dataTagMapper.selectById(item.getDataTagID());
                    if (tag != null) {
                        tagName.append(tag.getTagName()).append(",");
                    }
                }
            }

            if (tagName.length() > 0) {
                return tagName.substring(0, tagName.length() - 1);
            }
            return "";
        } catch (Exception ex) {
            return "";
        }
    }

    private String getDetailCodeName(String id) {
        try {
            DictDataCodeEntity entity = dictDataCodeMapper.selectById(id);
            if (entity != null && entity.getCodeName() != null) {
                return entity.getCodeName();
            }
            return "";
        } catch (Exception ex) {
            return "";
        }
    }
}
