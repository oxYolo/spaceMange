package com.whulri.geodata.datamanage.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.whulri.geodata.common.thread.ThreadPool;
import com.whulri.geodata.basecore.mapper.*;
import com.whulri.geodata.basecore.service.IOperateLogService;
import com.whulri.geodata.basecore.service.ISystemUserService;
import com.whulri.geodata.common.config.AppSettingConfig;
import com.whulri.geodata.common.constant.DictionaryIds;
import com.whulri.geodata.common.dto.AttributeObj;
import com.whulri.geodata.common.dto.Coordinate;
import com.whulri.geodata.common.dto.LayerFieldQuery;
import com.whulri.geodata.common.dto.LayerParameter;
import com.whulri.geodata.common.dto.dataStyle.DataStyleDto;
import com.whulri.geodata.common.dto.dataType.GroupKey;
import com.whulri.geodata.common.dto.dataType.LayerTypeGroupResult;
import com.whulri.geodata.common.dto.layerContent.*;
import com.whulri.geodata.common.dto.layerField.LayerFieldDto;
import com.whulri.geodata.common.dto.layerField.LayerFieldListDto;
import com.whulri.geodata.common.dto.systemUser.SystemUserDto;
import com.whulri.geodata.common.dto.tagRelation.TagDto;
import com.whulri.geodata.common.entity.*;
import com.whulri.geodata.common.enums.FieldTypeEnum;
import com.whulri.geodata.common.gdal.handler.GeoServer;
import com.whulri.geodata.common.gdal.pojo.ConverageRequest;
import com.whulri.geodata.common.gdal.pojo.RasterImgInfo;
import com.whulri.geodata.common.result.ListResultOutput;
import com.whulri.geodata.common.result.PageResultDTO;
import com.whulri.geodata.common.util.*;
import com.whulri.geodata.common.vo.AttributeModel;
import com.whulri.geodata.common.vo.PagedResultOutput;
import com.whulri.geodata.datamanage.service.*;
import com.whulri.geodata.datamanage.config.GdalEnvInitializer;
import com.whulri.geodata.datamanage.socket.SignalrClient;
import com.whulri.geodata.datamanage.util.*;
import com.whulri.gdal.config.GDALConfig;
import com.whulri.gdal.coverter.VertorProjection;
import com.whulri.gdal.coverter.gridConverter;
import com.whulri.gdal.model.TiffileInfo;
import com.whulri.gdal.tif.tiffileReader;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.gdal.gdal.Band;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.Driver;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconst;
import org.gdal.ogr.Feature;
import org.gdal.ogr.ogrConstants;
import org.gdal.osr.SpatialReference;
import com.whulri.geodata.common.bridge.BeanUtil;
import com.whulri.geodata.common.bridge.Func;
import com.whulri.geodata.common.bridge.UserContextUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import jakarta.annotation.Resource;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Slf4j
@Service
public class LayerContentServiceImpl extends ServiceImpl<LayerMapper, LayerContentEntity> implements ILayerContentService {

    @Resource
    private LayerMapper layerMapper;

    @Resource
    private MetadataMapper metadataMapper;

    @Resource
    private AppSettingConfig appSettingConfig;

    @Resource
    private ISystemUserService systemUserService;

    @Resource
    private IDataTypeService dataTypeService;

    @Resource
    private ITagRelationService tagRelationService;

    @Resource
    private IDictDataCodeService dictDataCodeService;

    @Resource
    private IDataTagService dataTagService;

    @Resource
    private DataStyleMapper dataStyleMapper;

    @Resource
    private MapRelationMapper mapRelationMapper;

    @Resource
    private ILayerFieldService layerFieldService;

    @Resource
    private LayerFieldMapper layerFieldMapper;

    @Resource
    private DynamicTableMapper dynamicTableMapper;

    @Resource
    private GeoServer geoServer;

    @Resource
    private MapMapper mapMapper;

    @Resource
    private IOperateLogService operateLogService;

    @Resource
    private IDirectoryService directoryService;

    @Resource
    private UtilityMessageConvert utilityMessageConvert;

    @Resource
    private ThumbnailHelper thumbnailHelper;

    @Resource
    private DataTypeMapper dataTypeMapper;

    @Resource
    private DictDataCodeMapper dictDataCodeMapper;

    @Resource
    private ExcuteShpJob excuteShpJob;

    @Resource
    private DataInformationBaseService dataInformationBaseService;

    @Resource
    private PostgresVectorHelper postgresVectorHelper;

    @Resource
    private IToolHisService toolHisService;

    @Resource
    private ILayerLogService layerLogService;

    @Resource
    private SignalrClient signalrClient;

    @Value("${server.servlet.context-path:}")
    private String prefix;

    @Override
    public List<LayerContentDto> getAllList() {
        try {
            List<LayerContentEntity> list = this.list();
            return BeanUtil.copy(list, LayerContentDto.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public DataTypeCountOutput getAllCountByDataType(String userCode) {
        try {
            userCode = UserContextUtil.resolveUserCode(userCode);

            List<LayerContentEntity> layerList = null;

            if (ConstHelper.CONST_SYSTEMNAME.equalsIgnoreCase(userCode)) {
                // 超级用户
                layerList = this.list();
            } else {
                List<SystemUserDto> userQuery = systemUserService.getUserDataByUserCodeAsync(userCode);
                if (userQuery != null && !userQuery.isEmpty()) {
                    List<String> userids = userQuery.stream()
                            .map(SystemUserDto::getUserCode)
                            .collect(Collectors.toList());
                    if (!userids.isEmpty()) {
                        layerList = baseMapper.selectList(new LambdaQueryWrapper<LayerContentEntity>()
                                .in(LayerContentEntity::getCreateBy, userids));
                    }
                }
                if (layerList == null) {
                    // sdms_user 无该用户记录（如未初始化）时兜底：按传入用户过滤本人数据
                    layerList = baseMapper.selectList(new LambdaQueryWrapper<LayerContentEntity>()
                            .eq(LayerContentEntity::getCreateBy, userCode));
                }
            }

            List<DataTypeEntity> allDataTypes = dataTypeService.list();

            Map<String, DataTypeEntity> dataTypeMap = allDataTypes.stream()
                    .collect(Collectors.toMap(DataTypeEntity::getId, Function.identity()));

            assert layerList != null;
            List<LayerTypeGroupResult> result = layerList.stream()
                    .map(layer -> {
                        DataTypeEntity child = dataTypeMap.get(layer.getLayerType());
                        if (child == null) {
                            return null;
                        }

                        DataTypeEntity parent = Optional.ofNullable(child.getParentID())
                                .map(dataTypeMap::get)
                                .orElse(null);

                        String effectiveId = (parent != null) ? parent.getId() : child.getId();
                        String effectiveName = (parent != null) ? parent.getTypeName() : child.getTypeName();

                        return new GroupKey(effectiveId, effectiveName);
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.groupingBy(
                            key -> key,
                            Collectors.counting()
                    ))
                    .entrySet().stream()
                    .map(entry -> new LayerTypeGroupResult(
                            entry.getKey().getId(),
                            entry.getKey().getName(),
                            entry.getValue().intValue()
                    ))
                    .collect(Collectors.toList());

            DataTypeCountOutput ret = new DataTypeCountOutput();

            ret.setData(new ArrayList<>());
            ret.setColors(new ArrayList<>());
            ret.setNames(new ArrayList<>());

            for (LayerTypeGroupResult item : result) {
                DataTypeCountDto dto = new DataTypeCountDto();
                dto.setId(item.getDataType());
                dto.setName(item.getDataTypeName());
                dto.setValue(item.getDataCount());

                ret.getData().add(dto);
                ret.getNames().add(item.getDataTypeName());
            }

            return ret;
        } catch (Exception ex) {
            log.error("获取图层统计失败", ex);
            return null;
        }
    }

    @Override
    public PagedResultOutput<LayerContentDto> getAllListStatus(LayerContentInputDto input, int pageSize, int pageIndex) {
        try {
            String layerType = input.getLayerType();
            String layerTag = input.getLayerTag();
            String layerName = input.getLayerName();
            String mapID = input.getLayerDesc();

            String listLayer = "", type = "", tag = "";

            List<LayerContentEntity> list = new ArrayList<>();

            if (Func.isNotEmpty(layerType)) {
                type = getMultiChildTypeByType(layerType);
            }

            if (Func.isNotEmpty(layerTag)) {
                tag = getMultiLayerIDByTag(layerTag);
            }

            input.setCreateBy(UserContextUtil.resolveUserCode(input.getCreateBy()));

            List<LayerContentEntity> layerContentEntity = getDataByUserCodeAsync(input.getCreateBy(), layerName, type, tag, "", ConstHelper.UPLOAD_SUCCESS);

            List<DataTypeEntity> listLayerType = dataTypeService.list();
            List<DictDataCodeEntity> listlayerRefence = dictDataCodeService.list();
            List<DictDataCodeEntity> listDataType = dictDataCodeService.list();

            assert layerContentEntity != null;
            List<LayerContentDto> query = layerContentEntity.stream().map(l -> {
                        DataTypeEntity de = listLayerType.stream()
                                .filter(t -> t.getId().equals(l.getLayerType()))
                                .findFirst().orElse(null);

                        DictDataCodeEntity re = listlayerRefence.stream()
                                .filter(r -> r.getId().equals(l.getLayerRefence()))
                                .findFirst().orElse(null);

                        DictDataCodeEntity ldt = listDataType.stream()
                                .filter(dt -> dt.getId().equals(l.getDataType()))
                                .findFirst().orElse(null);

                        LayerContentDto dto = new LayerContentDto();
                        dto.setId(l.getId());
                        dto.setLayerName(l.getLayerName());
                        dto.setDataType(l.getDataType());
                        dto.setDataTypeName(ldt == null ? "" : ldt.getCodeName());
                        dto.setLayerBBox(l.getLayerBBox());
                        dto.setLayerType(l.getLayerType());
                        dto.setLayerTypeName(de == null ? "" : de.getTypeName());
                        dto.setLayerTag(l.getLayerTag());
                        dto.setLayerDesc(l.getLayerDesc());
                        dto.setLayerAttrTable(l.getLayerAttrTable());
                        dto.setLayerSpatialTable(l.getLayerSpatialTable());
                        dto.setLayerRefence(l.getLayerRefence());
                        dto.setLayerRefenceName(re == null ? "" : re.getCodeName());
                        dto.setCreateDT(l.getCreateDT());
                        dto.setMaxY(l.getMaxY());
                        dto.setMinY(l.getMinY());
                        dto.setMinX(l.getMinX());
                        dto.setMaxX(l.getMaxX());
                        dto.setUploadStatus(l.getUploadStatus());
                        dto.setCreateBy(l.getCreateBy());
                        dto.setLayerDefaultStyle(l.getLayerDefaultStyle());
                        dto.setPublishStatus(l.getPublishStatus());
                        dto.setPublishType(l.getPublishType());
                        return dto;
                    }).sorted(Comparator.comparing(LayerContentDto::getCreateDT).reversed())
                    .collect(Collectors.toList());


            int count = query.size();
            Pageable pageable = PageRequest.of(pageIndex - 1, pageSize);
            Page<LayerContentDto> page = new PageImpl<>(query, pageable, count);

            List<LayerContentDto> result = page.getContent();

            if (!result.isEmpty()) {
                for (LayerContentDto item : result) {
                    item.setLayerTag(getMultiTagNameByMapID(item.getId()));
                    if (Func.isEmpty(item.getLayerDefaultStyle())) {
                        item.setLayerDefaultStyleName(getDefaultStyleName(item.getLayerDefaultStyle()));
                    }
                }
            }
            return new PagedResultOutput<>(count, result);
        } catch (Exception ex) {
            throw new RuntimeException(ex.getMessage());
        }
    }

    @Override
    public Integer getAllCount(String userCode) {
        try {
            userCode = UserContextUtil.resolveUserCode(userCode);

            List<LayerContentEntity> layerList = null;

            if (ConstHelper.CONST_SYSTEMNAME.equalsIgnoreCase(userCode)) {
                // 超级用户
                layerList = list();
            } else {
                List<SystemUserDto> userQuery = systemUserService.getUserDataByUserCodeAsync(userCode);
                if (userQuery != null && !userQuery.isEmpty()) {
                    List<String> userids = userQuery.stream()
                            .map(SystemUserEntity::getUserCode)
                            .collect(Collectors.toList());
                    if (!userids.isEmpty()) {
                        layerList = baseMapper.selectList(new LambdaQueryWrapper<LayerContentEntity>().
                                in(LayerContentEntity::getCreateBy, userids));
                    }
                }
                if (layerList == null) {
                    // sdms_user 无该用户记录（如未初始化）时兜底：按传入用户过滤本人数据
                    layerList = baseMapper.selectList(new LambdaQueryWrapper<LayerContentEntity>()
                            .eq(LayerContentEntity::getCreateBy, userCode));
                }
            }

            return layerList != null ? layerList.size() : 0;
        } catch (Exception ex) {
            return 0;
        }
    }

    @Override
    public ListResultOutput<LayerContentDto> getAllListByName(LayerContentInputDto input) {
        try {
            String layerType = input.getLayerType();
            String layerTag = input.getLayerTag();
            String layerName = input.getLayerName();
            String type = "", tag = "";

            if (Func.isNotEmpty(layerType)) {
                type = getMultiChildTypeByType(layerType);
            }

            if (Func.isNotEmpty(layerTag)) {
                tag = getMultiLayerIDByTag(layerTag);
            }

            input.setCreateBy(UserContextUtil.resolveUserCode(input.getCreateBy()));

            List<LayerContentEntity> layerContentEntity = getDataByUserCodeAsync(input.getCreateBy(), layerName, type, tag, "", "");

            String publishStatus = input.getPublishStatus();
            if (Func.isNotEmpty(publishStatus) && layerContentEntity != null) {
                layerContentEntity = layerContentEntity.stream()
                        .filter(s -> Func.isNotEmpty(s.getPublishStatus()) && s.getPublishStatus().equals(publishStatus))
                        .collect(Collectors.toList());
            }

            List<DataTypeEntity> listLayerType = dataTypeService.list();
            List<DictDataCodeEntity> listlayerRefence = dictDataCodeService.list();
            List<DictDataCodeEntity> listDataType = dictDataCodeService.list();

            assert layerContentEntity != null;
            List<LayerContentDto> result = layerContentEntity.stream().map(l -> {
                        DataTypeEntity de = listLayerType.stream().filter(t -> t.getId().equals(l.getLayerType())).findFirst().orElse(null);

                        DictDataCodeEntity re = listlayerRefence.stream().filter(r -> r.getId().equals(l.getLayerRefence())).findFirst().orElse(null);

                        DictDataCodeEntity ldt = listDataType.stream().filter(dt -> dt.getId().equals(l.getDataType())).findFirst().orElse(null);
                        LayerContentDto dto = new LayerContentDto();
                        dto.setId(l.getId());
                        dto.setLayerName(l.getLayerName());
                        dto.setDataType(l.getDataType());
                        dto.setDataTypeName(ldt == null ? "" : ldt.getCodeName());
                        dto.setLayerBBox(l.getLayerBBox());
                        dto.setLayerType(l.getLayerType());
                        dto.setLayerTypeName(de == null ? "" : de.getTypeName());
                        dto.setLayerTag(l.getLayerTag());
                        dto.setLayerDesc(l.getLayerDesc());
                        dto.setLayerAttrTable(l.getLayerAttrTable());
                        dto.setLayerSpatialTable(l.getLayerSpatialTable());
                        dto.setLayerRefence(l.getLayerRefence());
                        dto.setLayerRefenceName(re == null ? "" : re.getCodeName());
                        dto.setCreateDT(l.getCreateDT());
                        dto.setMaxY(l.getMaxY());
                        dto.setMinY(l.getMinY());
                        dto.setMinX(l.getMinX());
                        dto.setMaxX(l.getMaxX());
                        dto.setUploadStatus(l.getUploadStatus());
                        dto.setCreateBy(l.getCreateBy());
                        dto.setLayerDefaultStyle(l.getLayerDefaultStyle());
                        dto.setImgSrc("/Thumbnail/layer/" + l.getLayerAttrTable() + ".png");
                        dto.setPublishStatus(l.getPublishStatus());
                        dto.setPublishType(l.getPublishType());
                        return dto;
                    }).collect(Collectors.toList())
                    .stream()
                    .sorted(Comparator.comparing(LayerContentDto::getCreateDT, Comparator.nullsLast(Comparator.reverseOrder())))
                    .collect(Collectors.toList());

            return new ListResultOutput<>(result);
        } catch (Exception ex) {
            throw new RuntimeException(ex.getMessage());
        }
    }

    @Override
    public PagedResultOutput<LayerContentDto> getPageListByName(LayerContentInputDto input, int pageSize, int pageIndex) {
        try {
            String layerType = input.getLayerType();
            String layerTag = input.getLayerTag();
            String layerName = input.getLayerName();
            String dataType = input.getDataType();
            String createBy = input.getCreateBy();
            // 未显式传用户时取当前登录用户，登录态为空回退默认用户 admin（与前端 userStore 一致）
            createBy = UserContextUtil.resolveUserCode(createBy);

            String type = Func.isNotEmpty(layerType) ? getMultiChildTypeByType(layerType) : "";
            String tag = Func.isNotEmpty(layerTag) ? getMultiLayerIDByTag(layerTag) : "";

            List<LayerContentEntity> layerContentEntity = getDataByUserCodeAsync(createBy, layerName, type, tag, dataType, "");
            if (layerContentEntity == null) {
                layerContentEntity = Collections.emptyList();
            }
            String publishStatus = input.getPublishStatus();
            if (Func.isNotEmpty(publishStatus)) {
                layerContentEntity = layerContentEntity.stream()
                        .filter(s -> Func.isNotEmpty(s.getPublishStatus()) && s.getPublishStatus().equals(publishStatus))
                        .collect(Collectors.toList());
            }

            List<DataTypeEntity> listLayerType = dataTypeService.list();
            List<DictDataCodeEntity> listLayerRefence = dictDataCodeService.list();
            List<DictDataCodeEntity> listDataType = dictDataCodeService.list();

            Map<String, DataTypeEntity> typeMap = listLayerType.stream()
                    .collect(Collectors.toMap(DataTypeEntity::getId, t -> t, (a, b) -> a));

            Map<String, DictDataCodeEntity> refMap = listLayerRefence.stream()
                    .collect(Collectors.toMap(DictDataCodeEntity::getId, r -> r, (a, b) -> a));

            Map<String, DictDataCodeEntity> dataTypeMap = listDataType.stream()
                    .collect(Collectors.toMap(DictDataCodeEntity::getId, d -> d, (a, b) -> a));

            // === 转换 DTO ===
            List<LayerContentDto> fullList = layerContentEntity.stream().map(l -> {
                        LayerContentDto dto = new LayerContentDto();
                        dto.setId(l.getId());
                        dto.setLayerName(l.getLayerName());
                        dto.setDataType(l.getDataType());
                        dto.setDataTypeName(dataTypeMap.getOrDefault(l.getDataType(), new DictDataCodeEntity()).getCodeName());
                        dto.setLayerBBox(l.getLayerBBox());
                        dto.setLayerType(l.getLayerType());
                        dto.setLayerTypeName(typeMap.getOrDefault(l.getLayerType(), new DataTypeEntity()).getTypeName());
                        dto.setLayerTag(l.getLayerTag());
                        dto.setLayerDesc(l.getLayerDesc());
                        dto.setLayerAttrTable(l.getLayerAttrTable());
                        dto.setLayerSpatialTable(l.getLayerSpatialTable());
                        dto.setLayerRefence(l.getLayerRefence());
                        dto.setLayerRefenceName(refMap.getOrDefault(l.getLayerRefence(), new DictDataCodeEntity()).getCodeName());
                        dto.setCreateDT(l.getCreateDT());
                        dto.setMaxY(l.getMaxY());
                        dto.setMinY(l.getMinY());
                        dto.setMinX(l.getMinX());
                        dto.setMaxX(l.getMaxX());
                        dto.setUploadStatus(l.getUploadStatus());
                        dto.setCreateBy(l.getCreateBy());
                        dto.setLayerDefaultStyle(l.getLayerDefaultStyle());
                        dto.setImgSrc("/Thumbnail/layer/" + l.getLayerAttrTable() + ".png");
                        dto.setPublishStatus(l.getPublishStatus());
                        dto.setPublishType(l.getPublishType());
                        return dto;
                    }).sorted(Comparator.comparing(LayerContentDto::getCreateDT, Comparator.nullsLast(Comparator.reverseOrder())))
                    .collect(Collectors.toList());

            int count = fullList.size();
            List<LayerContentDto> collect = fullList.stream()
                    .skip((long) (pageIndex - 1) * pageSize)
                    .limit(pageSize)
                    .collect(Collectors.toList());

            if (!collect.isEmpty()) {
                for (LayerContentDto item : collect) {
                    item.setLayerTag(getMultiTagNameByMapID(item.getId()));
                    if (Func.isEmpty(item.getLayerDefaultStyle())) {
                        item.setLayerDefaultStyleName(getDefaultStyleName(item.getLayerDefaultStyle()));
                    }
                }
            }
            return new PagedResultOutput<>(count, collect);
        } catch (Exception ex) {
            throw new RuntimeException(ex.getMessage());
        }
    }

    @Override
    public ListResultOutput<LayerContentDto> getAllListByMapID(String mapId) {
        try {
            List<LayerContentDto> layerContentEntityDtos = new ArrayList<>();

            List<MapRelationEntity> mapRelations = mapRelationMapper
                    .selectList(new LambdaQueryWrapper<MapRelationEntity>()
                            .eq(MapRelationEntity::getMapId, mapId));

            if (mapRelations != null && !mapRelations.isEmpty()) {
                for (MapRelationEntity item : mapRelations) {
                    LayerContentEntity layer = baseMapper.selectById(item.getDataConfigID());
                    layerContentEntityDtos.add(BeanUtil.copy(layer, LayerContentDto.class));
                }
            }

            return new ListResultOutput<>(layerContentEntityDtos);
        } catch (Exception ex) {
            throw new RuntimeException(ex.getMessage());
        }
    }

    @Override
    public LayerContentDto getDetailById(String id) {
        LayerContentEntity query = this.getById(id);
        LayerContentDto result = BeanUtil.copy(query, LayerContentDto.class);
        if (result != null) {
            try {
                result.setLayerTag(getMultiTagNameByMapID(result.getId()));
                DictDataCodeEntity dictCode = dictDataCodeService.getById(result.getDataType());
                result.setDataTypeName(dictCode.getCodeName());
                result.setLayerDefaultStyleName(getDefaultStyleName(result.getLayerDefaultStyle()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return result;
    }

    @Override
    public String getLayerAttrTabledDetail(String layerID, int pageSize, int pageIndex) {
        String layerField = "";
        String querylayerField = "";
        String strJson_result = "";
        String strJson_dataTable = "";

        try {
            LayerContentEntity layer = baseMapper.selectById(layerID);
            List<LayerFieldEntity> layerAttr = layerFieldService.list(new LambdaQueryWrapper<LayerFieldEntity>()
                    .eq(LayerFieldEntity::getLayerID, layerID)
                    .orderByAsc(LayerFieldEntity::getAttributeSort));

            for (LayerFieldEntity item : layerAttr) {
                querylayerField += "\"" + item.getAttributeName().toLowerCase() + "\",";
                layerField += item.getAttributeName().toLowerCase() + ",";
            }

            if (!layerField.isEmpty()) {
                layerField = layerField.replaceAll(",+$", "");
                querylayerField = querylayerField.replaceAll(",+$", "");

                int fromNum = (pageIndex - 1) * pageSize;
                String sql = "select " + querylayerField + " from " + layer.getLayerAttrTable() + " ORDER BY SID DESC limit " + pageSize + " OFFSET " + fromNum;

                if (pageSize == 0) {
                    sql = "select " + querylayerField + " from " + layer.getLayerAttrTable();
                }

                List<Map<String, Object>> result = dynamicTableMapper.dynamicExecute(sql);
                strJson_dataTable = listMapToJson("data", result);

                // 回传记录总数，便于前端分页展示（count 失败不影响主流程）
                long totalCount = 0L;
                try {
                    List<Map<String, Object>> cntRes = dynamicTableMapper.dynamicExecute(
                            "select count(*) as cnt from " + layer.getLayerAttrTable());
                    if (cntRes != null && !cntRes.isEmpty() && cntRes.get(0).get("cnt") != null) {
                        totalCount = Long.parseLong(String.valueOf(cntRes.get(0).get("cnt")));
                    }
                } catch (Exception ignore) {
                    // count 查询失败时按 0 处理
                }

                if (strJson_dataTable.contains("\\")) {
                    strJson_dataTable = strJson_dataTable.replace("\\", "/");
                }

                Map<String, Object> resultMap = new HashMap<>();
                resultMap.put("LayerField", layerField);
                resultMap.put("DataTableJson", strJson_dataTable);
                resultMap.put("TotalCount", totalCount);
                strJson_result = JsonUtil.toJson(resultMap);
            }
        } catch (Exception ex) {
            log.error("getLayerAttrTabledDetail", ex);
        }

        return strJson_result;
    }

    @Override
    public LayerContentDto insert(LayerContentInputDto inputLc, String filePathName, String user,String isImport) {
        // 首先创建一条导入记录
        LayerLogEntity layerLogEntity = new LayerLogEntity();
        layerLogEntity.setStatus("0");
        layerLogEntity.setLayerId(inputLc.getId());
        layerLogEntity.setLayerName(inputLc.getLayerName());
        layerLogEntity.setUploadFileType(inputLc.getUploadFileType());
        layerLogEntity.setCreateBy(user);
        layerLogEntity.setCreateDT(LocalDateTime.now());
        layerLogService.save(layerLogEntity);

        // signalr相关
        SignalrClient connection = signalrClient;
        connection.start();
        String userId = user;
        String guid = inputLc.getId();
        String msgType = "layerInsertLog";

        LayerContentDto layerInput = null;
        try {
            WebSocketUtil.sendMsg(connection, userId, msgType, 1, 5, "开始导入数据...", false, false, guid);

            // 处理文件路径
            if (Func.isNotEmpty(filePathName) && !filePathName.equals("undefined")) {
                String fileFullNames = Paths.get(System.getProperty("user.dir"), appSettingConfig.getUploadFilePath()).toString();
                inputLc.setUploadFileName(Paths.get(fileFullNames, Paths.get(filePathName).getFileName().toString()).toString());
            }

            // 创建图层数据
            WebSocketUtil.sendMsg(connection, userId, msgType, 2, 10, "正在创建图层...", false, false, guid);
            layerInput = insertLayerData(inputLc, connection, userId, msgType, guid);

            // 初始化元数据（公共）
            initMetadata(layerInput, user, connection, userId, msgType, guid);

            // 矢量数据导入（矢量独有）
            if ("1".equals(isImport) && "1".equals(inputLc.getUploadFileType())
                    && Func.isNotEmpty(inputLc.getUploadFileName())) {
                importVectorData(layerInput, filePathName, user, connection, userId, msgType, guid, layerLogEntity.getId());
            }

            // 更新显示状态
            if (layerInput != null) {
                // 栅格数据：同步处理完成，发送成功消息 更新显示状态（矢量数据由ExcuteShpJob异步处理）
                if ("2".equals(inputLc.getUploadFileType())) {
                    LayerContentEntity layerContent = this.getById(layerInput.getId());
                    if (layerContent != null) {
                        // 如果来源明确要求 isShow=0（工具集生成的图层），则保持隐藏；否则显示
                        Integer targetIsShow = (inputLc.getIsShow() != null && inputLc.getIsShow() == 0) ? 0 : 1;
                        layerContent.setIsShow(targetIsShow);
                        this.updateById(layerContent);
                    }
                    layerLogEntity.setStatus("1");
                    layerLogService.updateById(layerLogEntity);
                    WebSocketUtil.sendMsg(connection, userId, msgType, 9, 100, "导入成功", false, true, guid);
                }
            }
        } catch (Exception e) {
            log.error("图层导入失败", e);
            WebSocketUtil.sendMsg(connection, userId, msgType, 0, 100, "导入失败: " + e.getMessage(), true, true, guid);

            layerLogEntity.setStatus("2");
            String reason = e.getMessage();
            if (reason != null && reason.contains(": ")) {
                reason = reason.substring(0, reason.indexOf(":"));
            } else if (reason == null) {
                reason = "未知错误";
            }
            layerLogEntity.setReason(reason);
            layerLogService.updateById(layerLogEntity);
            return null;
        } finally {
            // 确保连接一定关闭，防止资源泄露
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception ex) {
                    log.warn("关闭WebSocket连接失败", ex);
                }
            }
        }
        return layerInput;
    }

    /**
     * 初始化元数据（公共方法）
     */
    private void initMetadata(LayerContentDto layerInput, String user, SignalrClient connection, String userId, String msgType, String guid) {
        if (layerInput == null) {
            return;
        }
        WebSocketUtil.sendMsg(connection, userId, msgType, 6, 70, "正在初始化元数据...", false, false, guid);
        try {
            MetadataEntity metadata = new MetadataEntity();
            metadata.setId(UUID.randomUUID().toString());
            metadata.setDataId(layerInput.getId());
            metadata.setMetadataName(layerInput.getLayerName());
            metadata.setDataType(layerInput.getDataType());

            // 使用原始空间参考（如果存在），否则使用转换后的或默认的
            if (Func.isNotEmpty(layerInput.getOriginLayerRefence())) {
                metadata.setSpatialReferenceSystem(layerInput.getOriginLayerRefence());
            } else {
                metadata.setSpatialReferenceSystem(layerInput.getLayerRefence());
            }

            metadata.setCreateDT(LocalDateTime.now());
            metadata.setCreateBy(user);
            metadata.setDataContentDec(layerInput.getLayerDesc());

            // BBox - 优先使用原始坐标（如果存在）
            if (layerInput.getOriginMaxX() != null && layerInput.getOriginMinX() != null
                    && layerInput.getOriginMaxY() != null && layerInput.getOriginMinY() != null) {
                metadata.setEast(layerInput.getOriginMaxX());
                metadata.setWest(layerInput.getOriginMinX());
                metadata.setSouth(layerInput.getOriginMinY());
                metadata.setNorth(layerInput.getOriginMaxY());
            } else {
                metadata.setEast(layerInput.getMaxX());
                metadata.setWest(layerInput.getMinX());
                metadata.setSouth(layerInput.getMinY());
                metadata.setNorth(layerInput.getMaxY());
            }

            // 栅格特有属性处理
            if ("2".equals(layerInput.getUploadFileType()) && Func.isNotEmpty(layerInput.getResultData())) {
                String[] resultData = layerInput.getResultData().split(",");
                if (resultData.length >= 4) {
                    try {
                        metadata.setMinPixel(new BigDecimal(resultData[1]));
                        metadata.setMaxPixel(new BigDecimal(resultData[2]));
                        metadata.setNoData(new BigDecimal(resultData[3]));
                    } catch (Exception e) {
                        log.warn("解析栅格元数据数值失败", e);
                    }
                }
            }
            metadataMapper.insert(metadata);
        } catch (Exception e) {
            log.error("初始化元数据失败: " + layerInput.getId(), e);
            throw new RuntimeException("初始化元数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 导入矢量数据（矢量独有方法）
     */
    private void importVectorData(LayerContentDto layerInput, String filePathName, String user,
                                  SignalrClient connection, String userId, String msgType, String guid, String layerLogId) {
        if (user == null || user.isEmpty()) {
            user = "System";
        }

        // 处理属性字段
        WebSocketUtil.sendMsg(connection, userId, msgType, 7, 80, "正在处理矢量属性字段...", false, false, guid);
        try {
            LayerFieldListDto layerFieldListDto = layerFieldService.getLayerFieldByFileName(filePathName);
            String layerId = "";
            List<LayerFieldDto> listInput = layerFieldListDto.getLayerField();

            for (LayerFieldDto input : listInput) {
                LayerFieldEntity d = new LayerFieldEntity();
                d.setId(input.getId());
                d.setLayerID(layerInput.getId());
                d.setAttributeName(input.getAttributeName());
                d.setAttributeDesc(input.getAttributeDesc());
                d.setAttributeType(input.getAttributeType());
                d.setAttributeLength(input.getAttributeLength());
                d.setAttributePrecision(input.getAttributePrecision());
                d.setAttributeInputCtrl(input.getAttributeInputCtrl());
                d.setAttributeInputMax(input.getAttributeInputMax());
                d.setAttributeInputMin(input.getAttributeInputMin());
                d.setAttributeDefault(input.getAttributeDefault());
                d.setAttributeIsNull(input.getAttributeIsNull());
                d.setAttributeInputFormat(input.getAttributeInputFormat());
                d.setAttributeUnit(input.getAttributeUnit());
                d.setAttributeCalComp(input.getAttributeCalComp());
                d.setAttributeDataSource(input.getAttributeDataSource());
                d.setAttributeDataType(input.getAttributeDataType());
                d.setAttributeValueLink(input.getAttributeValueLink());
                d.setRemark(input.getRemark());
                d.setAttributeSort(input.getAttributeSort());
                d.setChineseName(input.getChineseName());
                d.setCreateDT(LocalDateTime.now());

                layerFieldService.save(d);
                layerId = d.getLayerID();
            }

            // 添加标签关联
            String dataTypeID = "a2faae61-6acd-11e7-87f3-005056bb1c7e";
            TagDto args = new TagDto();
            args.setTagName("");
            args.setDataID(dataTypeID);
            args.setMapLayerID(layerInput.getId());
            tagRelationService.MultiInsert(args);
        } catch (Exception e) {
            throw new RuntimeException("处理属性字段失败: " + e.getMessage(), e);
        }

        // 导入SHP数据
        List<LayerFieldEntity> list = layerFieldService.list(new LambdaQueryWrapper<LayerFieldEntity>().eq(LayerFieldEntity::getLayerID, layerInput.getId()));
        LayerContentEntity layer = baseMapper.selectById(layerInput.getId());
        List<DictDataCodeEntity> listDataType = dictDataCodeService.list(new LambdaQueryWrapper<DictDataCodeEntity>().eq(DictDataCodeEntity::getDataTypeID, "73160096-67a5-11e7-8eb2-005056bb1c7e"));

        WebSocketUtil.sendMsg(connection, userId, msgType, 8, 90, "正在导入SHP数据...", false, false, guid);
        try {
            importShpFileData(layerInput.getId(), filePathName, user, list, layer, listDataType, true, connection, userId, msgType, guid, layerLogId,layerInput);
        } catch (Exception e) {
            throw new RuntimeException("导入SHP数据失败: " + e.getMessage(), e);
        }
    }

    @Override
    public MapEntity insertImageLayer(LayerContentInputDto inputLc) {

        try {
            LayerContentDto layerEntity = insertLayerData(inputLc);
            if (layerEntity == null) {
                return null;
            }

            // 保存地图数据
            String mapId = UUID.randomUUID().toString();
            String mapType = layerEntity.getLayerType();
            MapEntity entityMap = new MapEntity();
            entityMap.setId(mapId);
            entityMap.setMapName(layerEntity.getLayerName());
            entityMap.setMapType(mapType);
            entityMap.setCreateDT(LocalDateTime.now());
            entityMap.setCreateUserId(inputLc.getCreateBy());
            entityMap.setCreateUserName(inputLc.getCreateByName());
            entityMap.setMapDesc(inputLc.getLayerDesc());
            entityMap.setSpatialRefence(appSettingConfig.getSpatialReference());

            // 数据源相关属性
            entityMap.setDataSource(inputLc.getDataSource());
            entityMap.setDataDesc(inputLc.getDataDesc());
            entityMap.setDataConfidential(inputLc.getDataConfidential());
            entityMap.setKeywords(inputLc.getKeywords());
            entityMap.setServiceObject(inputLc.getServiceObject());
            entityMap.setDataTypes(inputLc.getDataTypes());
            entityMap.setDataAccuracy(inputLc.getDataAccuracy());

            // 生成英文名称
            ChineseConvert chn = new ChineseConvert();
            String mapEnName = chn.getPinyinInitials(entityMap.getMapName());
            String lastIndex = LocalDateTime.now().getDayOfMonth() + ""
                    + LocalDateTime.now().getHour()
                    + LocalDateTime.now().getMinute()
                    + LocalDateTime.now().getSecond();
            entityMap.setMapEnName(mapEnName + lastIndex);

            try {
                mapMapper.insert(entityMap);
            } catch (Exception ex) {
                log.error("插入地图数据异常", ex);
            }

            // 保存地图图层关系
            String releationId = UUID.randomUUID().toString();
            MapRelationEntity entityMapReleation = new MapRelationEntity();
            entityMapReleation.setId(releationId);
            entityMapReleation.setMapId(mapId);
            entityMapReleation.setDataConfigID(layerEntity.getId());
            entityMapReleation.setDataStyleID(mapId);
            entityMapReleation.setDataSort(1);
            entityMapReleation.setConfigDT(LocalDateTime.now());
            entityMapReleation.setModifyDT(LocalDateTime.now());
            mapRelationMapper.insert(entityMapReleation);

            // 更新地图数据
            List<String> layers = new ArrayList<>();
            layers.add(layerEntity.getId());

            Map<String, BigDecimal> bbox = new HashMap<>();
            bbox.put("MaxX", layerEntity.getMaxX());
            bbox.put("MinX", layerEntity.getMinX());
            bbox.put("MaxY", layerEntity.getMaxY());
            bbox.put("MinY", layerEntity.getMinY());

            entityMap.setMaxX(bbox.get("MaxX"));
            entityMap.setMinX(bbox.get("MinX"));
            entityMap.setMaxY(bbox.get("MaxY"));
            entityMap.setMinY(bbox.get("MinY"));

            try {
                mapMapper.updateById(entityMap);
            } catch (Exception e) {
                log.error("更新地图范围异常", e);
            }

            // 发布地图相关逻辑
            List<LayerContentDto> layerList = new ArrayList<>();
            layerList.add(layerEntity);

            String desiredSrs = appSettingConfig.getEpsg();
            if (desiredSrs == null) {
                desiredSrs = "EPSG:4326";
            }

            List<String> targetLayers = new ArrayList<>();
            List<String> styleLayers = new ArrayList<>();

            DataStyleDto dst = null;
            if (layerEntity.getRasterCount()==1 && inputLc.getLayerDefaultStyle() == null) {//todo  目前只有单波段的样式设置
                List<String> resultData = List.of(layerEntity.getResultData().split(","));
                double dmin = Double.parseDouble(resultData.get(1));
                double dmax = Double.parseDouble(resultData.get(2));
                double dnoData = Double.parseDouble(resultData.get(3));
                String noDataValue = changeDataToD(resultData.get(3));

                String val;
                if ("error".equals(noDataValue)) {
                    val = "<ColorMapEntry color=\"#000000\" quantity=\"" + changeDataToD(resultData.get(1)) + "\" label=\"values\" />" +
                            "<ColorMapEntry color=\"#FFFFFF\" quantity=\"" + changeDataToD(resultData.get(2)) + "\" label=\"values\" />";
                } else {
                    if (dnoData > dmax) {
                        val = "<ColorMapEntry color=\"#000000\" quantity=\"" + changeDataToD(resultData.get(1)) + "\" label=\"values\" />" +
                                "<ColorMapEntry color=\"#FFFFFF\" quantity=\"" + changeDataToD(resultData.get(2)) + "\" label=\"values\" />" +
                                "<ColorMapEntry color=\"#000000\" quantity=\"" + changeDataToD(resultData.get(3)) + "\" label=\"nodata\" opacity=\"0.0\" />";
                    } else if (dnoData < dmin) {
                        val = "<ColorMapEntry color=\"#000000\" quantity=\"" + changeDataToD(resultData.get(3)) + "\" label=\"nodata\" opacity=\"0.0\" />" +
                                "<ColorMapEntry color=\"#000000\" quantity=\"" + changeDataToD(resultData.get(1)) + "\" label=\"values\" />" +
                                "<ColorMapEntry color=\"#FFFFFF\" quantity=\"" + changeDataToD(resultData.get(2)) + "\" label=\"values\" />";
                    } else {
                        val = "<ColorMapEntry color=\"#000000\" quantity=\"" + changeDataToD(resultData.get(1)) + "\" label=\"values\" />" +
                                "<ColorMapEntry color=\"#000000\" quantity=\"" + changeDataToD(resultData.get(3)) + "\" label=\"nodata\" opacity=\"0.0\" />" +
                                "<ColorMapEntry color=\"#FFFFFF\" quantity=\"" + changeDataToD(resultData.get(2)) + "\" label=\"values\" />";
                    }
                }

                DataStyleEntity dataStyle = new DataStyleEntity();
                dataStyle.setId(mapId);
                dataStyle.setStyleName(layerEntity.getLayerName());
                dataStyle.setStyleContent("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                        "<StyledLayerDescriptor version=\"1.0.0\"" +
                        " xmlns=\"http://www.opengis.net/sld\" xmlns:ogc=\"http://www.opengis.net/ogc\"" +
                        " xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"" +
                        " xsi:schemaLocation=\"http://www.opengis.net/sld StyledLayerDescriptor.xsd\">" +
                        "  <NamedLayer>" +
                        "    <Name>" + entityMap.getMapEnName() + "</Name>" +
                        "    <UserStyle>" +
                        "      <Name>" + entityMap.getMapEnName() + "</Name>" +
                        "      <Title>" + entityMap.getMapEnName() + "</Title>" +
                        "      <Abstract>" + entityMap.getMapEnName() + "</Abstract>" +
                        "      <FeatureTypeStyle>" +
                        "        <Rule>" +
                        "          <RasterSymbolizer>" +
                        "            <Opacity>1.0</Opacity>" +
                        "            <ColorMap>" + val + "</ColorMap>" +
                        "          </RasterSymbolizer>" +
                        "        </Rule>" +
                        "      </FeatureTypeStyle>" +
                        "    </UserStyle>" +
                        "  </NamedLayer>" +
                        "</StyledLayerDescriptor>");
                dataStyle.setStyleContent(formatXml(dataStyle.getStyleContent()));
                dataStyle.setStyleDataType(DictionaryIds.GEOM_RASTER);
                dataStyle.setStyleConfigType("3");
//                dataStyleService.save(dataStyle);
                dataStyleMapper.insert(dataStyle);
                boolean c = updateLayer(layerEntity.getId(), dataStyle.getId());
            }

            // 发布图层组
            for (LayerContentDto layer : layerList) {
                targetLayers.add(layer.getLayerAttrTable());
            }
            String mapName = entityMap.getMapEnName();

            if (dst != null) {
                styleLayers.add(dst.getStyleName());
            } else {
                String name = inputLc.getLayerDefaultStyle() != null ? inputLc.getLayerDefaultStyle() : "";
                styleLayers.add(name);
            }

            String bBox = null;
            if (entityMap.getMinX() != null && entityMap.getMaxX() != null
                    && entityMap.getMinY() != null && entityMap.getMaxY() != null) {
                bBox = entityMap.getMinX() + "," + entityMap.getMaxX() + ","
                        + entityMap.getMinY() + "," + entityMap.getMaxY();
            }

            geoServer.addLayerGroup(mapName, targetLayers, styleLayers, desiredSrs, bBox);

            // 自动缓存处理
//            String isAutoCache = appSettingConfig.getIsAutoCache();
            String isAutoCache = "0";
            if ("1".equals(isAutoCache)) {
                geoServer.terminatingTask(mapName);
                geoServer.tileMap(entityMap.getMapEnName(), bBox);
            }

            // 生成缩略图
            String strBBox = null;
            if (entityMap.getMinX() != null && entityMap.getMaxX() != null
                    && entityMap.getMinY() != null && entityMap.getMaxY() != null) {
                strBBox = entityMap.getMinX() + "," + entityMap.getMinY() + ","
                        + entityMap.getMaxX() + "," + entityMap.getMaxY();
            }
            getThumbnial(entityMap.getMapEnName(), strBBox);

            return entityMap;
        } catch (Exception ex) {
            log.error("新增图层异常", ex);
            throw new RuntimeException(ex);
        }
    }

    @Override
    public boolean update(LayerContentInputDto input) {
        try {
            LayerContentEntity layerContentEntity = BeanUtil.copy(input, LayerContentEntity.class);
            if(Func.isNotEmpty(input.getParentId())){
                layerContentEntity.setLayerType(input.getParentId());
            }
            baseMapper.updateById(layerContentEntity);
            // 显式解绑默认样式：layerDefaultStyle 传空串时清空（updateById 的 NOT_NULL 策略不会写 null）
            if (input.getLayerDefaultStyle() != null && input.getLayerDefaultStyle().isEmpty()) {
                baseMapper.update(null, new LambdaUpdateWrapper<LayerContentEntity>()
                        .eq(LayerContentEntity::getId, input.getId())
                        .set(LayerContentEntity::getLayerDefaultStyle, null));
            }
        } catch (Exception e) {
            log.error("更新图层失败！");
            return false;
        }
        return true;
    }

    @Override
    public LayerContentDto updateStatus(String layerID) {
        try {
            // 异步查找实体（假设findById返回CompletableFuture）
            LayerContentEntity entity = baseMapper.selectById(layerID);

            if (entity != null) {
                entity.setUploadStatus("1");
            }

            // 异步更新实体（假设update返回CompletableFuture）
            baseMapper.updateById(entity);

            return BeanUtil.copy(entity, LayerContentDto.class);
        } catch (Exception ex) {
            throw new RuntimeException(ex.getMessage());
        }
    }

    @Override
    public boolean delete(String id, String user) {
        try {
            // 删除标签关系数据
            tagRelationService.remove(new LambdaQueryWrapper<TagRelationEntity>().eq(TagRelationEntity::getMapID, id));
            // 删除配置表数据
            layerFieldService.remove(new LambdaQueryWrapper<LayerFieldEntity>().eq(LayerFieldEntity::getLayerID, id));

            operateLogService.writeOperateLog(id, user, 1001, 1105, 1201, 1341, null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public boolean deleteLayer(String id) {
        try {
            // 获取图层详情
            LayerContentDto entity = getDetailById(id);
            if (entity != null) {
                String layerTable = entity.getLayerAttrTable();
                String layerSpatial = entity.getLayerSpatialTable();
                String strSql = "drop table " + layerTable + ";drop table " + layerSpatial;
                // 执行SQL（此处保留原代码逻辑，实际应用中应替换为真实实现）

                String strPostSQL = String.format("DROP TABLE IF EXISTS \"%s\" CASCADE", entity.getLayerAttrTable());
                try {
                    dynamicTableMapper.dynamicUpdateExecute(strPostSQL);
                } catch (Exception e) {
                    log.warn("Failed to drop table {}, may need manual cleanup", entity.getLayerAttrTable(), e);
                    throw new RuntimeException("数据库表删除失败", e);
                }
                geoServer.deleteLayer(entity.getLayerAttrTable());
                // 删除图层内容
                baseMapper.deleteById(id);
            }
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    @Override
    public List<CatalogTreeVO> getLayerTree(String dictType, String dataType, String publishStatus, String layerName, String uploadFileType, Integer maxRaster, Boolean isclassify) {
        // 1. 查询所有匹配 dataTypeId 的目录
        List<DataTypeEntity> catalogs = dataTypeMapper.selectList(
                new LambdaQueryWrapper<DataTypeEntity>()
                        .eq(DataTypeEntity::getDictCodeID, dictType)
                        .orderByAsc(DataTypeEntity::getParentID, DataTypeEntity::getId)
        );
        if (catalogs.isEmpty()) return Collections.emptyList();

        // 2. 批量查询关联的图层（layerType = 目录.id）
        Set<String> catalogIds = catalogs.stream()
                .map(DataTypeEntity::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        LambdaQueryWrapper<LayerContentEntity> queryWrapper = new LambdaQueryWrapper<LayerContentEntity>()
                .in(!catalogIds.isEmpty(), LayerContentEntity::getLayerType, catalogIds)
                .like(!layerName.isEmpty(), LayerContentEntity::getLayerName, layerName)
                .eq(Func.isNotBlank(uploadFileType), LayerContentEntity::getUploadFileType, uploadFileType)
                .le(Func.isNotEmpty(maxRaster), LayerContentEntity::getRasterCount, maxRaster)
                .eq(!publishStatus.isEmpty(), LayerContentEntity::getPublishStatus, publishStatus)
                .eq(Func.isNotEmpty(dataType),LayerContentEntity::getDataType,dataType)
                .eq(LayerContentEntity::getIsShow, 1);

        // 添加 isclassify 条件：筛选分类栅格数据（tifVariety < 50 或 tifVariety IS NULL）
        if (Boolean.TRUE.equals(isclassify)) {
            queryWrapper.and(wrapper -> wrapper
                .lt(LayerContentEntity::getTifVariety, 50)
                .or()
                .isNull(LayerContentEntity::getTifVariety)
            );
        } else if (Boolean.FALSE.equals(isclassify)) {
            queryWrapper.and(wrapper -> wrapper.ge(LayerContentEntity::getTifVariety, 50));
        }

        List<LayerContentEntity> layers = layerMapper.selectList(queryWrapper);

        // 3. 按目录ID分组图层
        Map<String, List<LayerContentEntity>> layerMap = layers.stream()
                .collect(Collectors.groupingBy(LayerContentEntity::getLayerType));

        // 4. 构建 ID -> 目录实体映射
        Map<String, DataTypeEntity> catalogMap = catalogs.stream()
                .collect(Collectors.toMap(DataTypeEntity::getId, c -> c));

        // 5. 计算每个节点的 totalLayerCount（记忆化递归）
        Map<String, Integer> totalLayerCountMap = new HashMap<>();
        for (DataTypeEntity catalog : catalogs) {
            calculateTotalLayers(catalog.getId(), catalogs, layerMap, totalLayerCountMap);
        }

        // 6. 递归构建树（从顶层 parentId="0" 开始）
        return buildTree(catalogs, layerMap, totalLayerCountMap, "0", !layerName.isEmpty());
    }

    @Override
    public List<CatalogTreeVO> getLayerTreeByType(String dataTypeId, String publishStatus, String layerName) {
        try {
            // 几何类型字典（点/线/面/影像）
            List<DictDataCodeEntity> dictDataCodeEntities = dictDataCodeMapper.selectList(new LambdaQueryWrapper<DictDataCodeEntity>()
                    .eq(DictDataCodeEntity::getDataTypeID, "1cfe51dd-67a3-11e7-8eb2-005056bb1c7e"));

            // 目录子树（含自身）：入参 dataTypeId 为 sdms_datatype 目录 id
            List<DataTypeEntity> catalogs = dataTypeService.list();
            Set<String> catalogIds = new HashSet<>();
            collectCatalogSubtree(catalogs, dataTypeId, catalogIds);
            if (catalogIds.isEmpty()) {
                return new ArrayList<>();
            }

            List<LayerContentEntity> layerContentEntities = layerMapper.selectList(new LambdaQueryWrapper<LayerContentEntity>()
                    .in(LayerContentEntity::getLayerType, catalogIds)
                    .like(!layerName.isEmpty(), LayerContentEntity::getLayerName, layerName)
                    .eq(!publishStatus.isEmpty(), LayerContentEntity::getPublishStatus, publishStatus)
                    .eq(LayerContentEntity::getIsShow,1));


            List<CatalogTreeVO> list = new ArrayList<>();
            // 矢量
            CatalogTreeVO treeVO = new CatalogTreeVO();
            List<CatalogTreeVO> catalogTreeVOS = new ArrayList<>();
            treeVO.setId("0");
            treeVO.setName("矢量");
            int count = 0;

            // 栅格
            CatalogTreeVO imageTreeVO = new CatalogTreeVO();

            for (DictDataCodeEntity dictDataCode : dictDataCodeEntities) {
                if ("点".equals(dictDataCode.getCodeName())) {
                    CatalogTreeVO catalogTreeVO = new CatalogTreeVO();
                    catalogTreeVO.setId(dictDataCode.getId());
                    catalogTreeVO.setName("点");

                    List<LayerContentEntity> point = layerContentEntities.stream()
                            .filter(l -> Func.isNotEmpty(l.getDataType()) && l.getDataType().equals(dictDataCode.getId()))
                            .collect(Collectors.toList());
                    List<CatalogTreeVO> collect = point.stream()
                            .map(s -> {
                                CatalogTreeVO vo = new CatalogTreeVO();
                                vo.setId(s.getId());
                                vo.setName(s.getLayerName());
                                vo.setLayerContent(s);
                                return vo;
                            }).collect(Collectors.toList());
                    catalogTreeVO.setTotalLayerCount(point.size());
                    catalogTreeVO.setChildren(collect);
                    catalogTreeVOS.add(catalogTreeVO);
                    count += point.size();

                } else if ("线".equals(dictDataCode.getCodeName())) {
                    CatalogTreeVO catalogTreeVO = new CatalogTreeVO();
                    catalogTreeVO.setId(dictDataCode.getId());
                    catalogTreeVO.setName("线");

                    List<LayerContentEntity> line = layerContentEntities.stream()
                            .filter(l -> Func.isNotEmpty(l.getDataType()) && l.getDataType().equals(dictDataCode.getId()))
                            .collect(Collectors.toList());
                    List<CatalogTreeVO> collect = line.stream()
                            .map(s -> {
                                CatalogTreeVO vo = new CatalogTreeVO();
                                vo.setId(s.getId());
                                vo.setName(s.getLayerName());
                                vo.setLayerContent(s);
                                return vo;
                            }).collect(Collectors.toList());
                    catalogTreeVO.setTotalLayerCount(line.size());
                    catalogTreeVO.setChildren(collect);
                    catalogTreeVOS.add(catalogTreeVO);
                    count += line.size();

                } else if ("面".equals(dictDataCode.getCodeName())) {
                    CatalogTreeVO catalogTreeVO = new CatalogTreeVO();
                    catalogTreeVO.setId(dictDataCode.getId());
                    catalogTreeVO.setName("面");

                    List<LayerContentEntity> polygon = layerContentEntities.stream()
                            .filter(l -> Func.isNotEmpty(l.getDataType()) && l.getDataType().equals(dictDataCode.getId()))
                            .collect(Collectors.toList());
                    List<CatalogTreeVO> collect = polygon.stream()
                            .map(s -> {
                                CatalogTreeVO vo = new CatalogTreeVO();
                                vo.setId(s.getId());
                                vo.setName(s.getLayerName());
                                vo.setLayerContent(s);
                                return vo;
                            }).collect(Collectors.toList());
                    catalogTreeVO.setTotalLayerCount(polygon.size());
                    catalogTreeVO.setChildren(collect);
                    catalogTreeVOS.add(catalogTreeVO);
                    count += polygon.size();

                } else if ("影像".equals(dictDataCode.getCodeName())) {
                    imageTreeVO.setId(dictDataCode.getId());
                    imageTreeVO.setName("栅格");

                    List<LayerContentEntity> image = layerContentEntities.stream()
                            .filter(l -> Func.isNotEmpty(l.getDataType()) && l.getDataType().equals(dictDataCode.getId()))
                            .collect(Collectors.toList());
                    List<CatalogTreeVO> collect = image.stream()
                            .map(s -> {
                                CatalogTreeVO vo = new CatalogTreeVO();
                                vo.setId(s.getId());
                                vo.setName(s.getLayerName());
                                vo.setLayerContent(s);
                                return vo;
                            }).collect(Collectors.toList());
                    imageTreeVO.setTotalLayerCount(image.size());
                    imageTreeVO.setChildren(collect);
                }
            }
            treeVO.setTotalLayerCount(count);
            treeVO.setChildren(catalogTreeVOS);

            list.add(treeVO);
            list.add(imageTreeVO);
            return list;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 收集目录子树 id 集合（含自身，BFS）
     */
    private void collectCatalogSubtree(List<DataTypeEntity> catalogs, String rootId, Set<String> out) {
        if (Func.isEmpty(rootId)) {
            return;
        }
        Deque<String> queue = new ArrayDeque<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            String id = queue.poll();
            if (!out.add(id)) {
                continue;
            }
            for (DataTypeEntity c : catalogs) {
                if (Objects.equals(c.getParentID(), id)) {
                    queue.add(c.getId());
                }
            }
        }
    }

    /**
     * 递归构建树形结构
     */
    private List<CatalogTreeVO> buildTree(List<DataTypeEntity> catalogs,
                                          Map<String, List<LayerContentEntity>> layerMap,
                                          Map<String, Integer> totalLayerCountMap,
                                          String parentId,
                                          boolean filter) {
        return catalogs.stream()
                .filter(c -> Objects.equals(c.getParentID(), parentId))
                .filter(c -> !filter || totalLayerCountMap.getOrDefault(c.getId(), 0) > 0)
                .map(catalog -> {
                    CatalogTreeVO node = new CatalogTreeVO();
                    node.setId(catalog.getId());
                    node.setDataTypeId(catalog.getDictCodeID());
                    node.setParentId(catalog.getParentID());
                    node.setName(catalog.getTypeName());
                    // 设置直接图层
                    List<LayerContentEntity> directLayers = layerMap.getOrDefault(catalog.getId(), Collections.emptyList())
                            .stream()
                            .sorted(Comparator.comparing(LayerContentEntity::getLayerSort, Comparator.nullsLast(Integer::compareTo)))
                            .collect(Collectors.toList());

                    List<CatalogTreeVO> list = buildTree(catalogs, layerMap, totalLayerCountMap, catalog.getId(), filter);
                    for (LayerContentEntity directLayer : directLayers) {
                        CatalogTreeVO catalogTreeVO = new CatalogTreeVO();
                        catalogTreeVO.setId(directLayer.getId());
                        catalogTreeVO.setName(directLayer.getLayerName());
                        catalogTreeVO.setParentId(directLayer.getLayerType());
                        catalogTreeVO.setLayerContent(directLayer);
                        catalogTreeVO.setSort(directLayer.getLayerSort());
                        list.add(catalogTreeVO);
                    }
                    // 重新排序，确保目录和图层按照sort字段混合排序
                    list.sort(Comparator.comparing(CatalogTreeVO::getSort, Comparator.nullsLast(Integer::compareTo)));
                    node.setChildren(list);

//                    node.setDirectLayers(directLayers);
                    node.setDirectLayerCount(directLayers.size());

                    // 设置总图层数（自身+子孙）
                    node.setTotalLayerCount(totalLayerCountMap.getOrDefault(catalog.getId(), 0));

                    // 排序
                    node.setSort(catalog.getCodeSort());

                    return node;
                })
                .sorted(Comparator.comparing(CatalogTreeVO::getSort, Comparator.nullsLast(Integer::compareTo)))
                .collect(Collectors.toList());
    }

    /**
     * 递归计算节点的总图层数（自身 + 所有子孙）
     */
    private int calculateTotalLayers(String nodeId,
                                     List<DataTypeEntity> catalogs,
                                     Map<String, List<LayerContentEntity>> directLayerMap,
                                     Map<String, Integer> memo) {
        // 记忆化避免重复计算
        if (memo.containsKey(nodeId)) {
            return memo.get(nodeId);
        }

        // 1. 当前节点直接图层数
        int count = directLayerMap.getOrDefault(nodeId, Collections.emptyList()).size();

        // 2. 找到所有直接子目录
        List<DataTypeEntity> children = catalogs.stream()
                .filter(c -> Objects.equals(c.getParentID(), nodeId))
                .collect(Collectors.toList());

        // 3. 递归累加子目录的总图层数
        for (DataTypeEntity child : children) {
            count += calculateTotalLayers(child.getId(), catalogs, directLayerMap, memo);
        }

        memo.put(nodeId, count);
        return count;
    }


    private LayerImportValidDto importShpFileData(String layerID, String filePath, String user, List<LayerFieldEntity> list, LayerContentEntity layer, List<DictDataCodeEntity> listDataType, boolean importFlag, SignalrClient connection, String userId, String msgType, String guid, String layerLogId,LayerContentDto inputLc) {

        LayerImportValidDto liv = new LayerImportValidDto();
        liv.setStatus(true);
        boolean success = true;
        String fileName = Paths.get(filePath).getFileName().toString();

        if (isFileExist(filePath, liv)) {
            try {
                String path = Paths.get(System.getProperty("user.dir"), appSettingConfig.getUploadFilePath(), filePath).toString();
                ShpReader shpReader = new ShpReader(path);

                if (!shpReader.isValidDataSource()) {
                    liv.setStatus(false);
                    liv.setMessage(utilityMessageConvert.get("上传文件解析异常(上传的矢量文件无效)"));
                    operateLogService.writeOperateLog(layerID, user, 1001, 1103, 1202, 1323, null);
                } else {
                    String csSrc = shpReader.getSridWkt();
                    String spatialReference = appSettingConfig.getSpatialReference();
                    String tableName = "";
                    if (layer.getId() != null) {
                        tableName = layer.getLayerAttrTable();
                    }

                    List<String> layerAttr = list.stream()
                            .map(LayerFieldEntity::getAttributeName)
                            .collect(Collectors.toList());

                    Map<String, String> attr = shpReader.getAttributeType();
                    List<String> shpAttr = new ArrayList<>();
                    Map<String, String> hashTable = new HashMap<>();

                    for (Map.Entry<String, String> item : attr.entrySet()) {
                        if (layerAttr.contains(item.getKey())) {
                            shpAttr.add(item.getKey());
                            hashTable.put(item.getKey(), item.getValue());
                        }
                    }

//                        GeoServerHelper geoServerHelper = new GeoServerHelper();

                    if (shpAttr.isEmpty() || shpAttr.size() != layerAttr.size() || attr.size() != layerAttr.size()) {
                        liv.setStatus(false);
                        liv.setMessage(utilityMessageConvert.get("上传文件解析异常(属性无匹配或只部分匹配)"));

                        operateLogService.writeOperateLog(layerID, user, 1001, 1103, 1202, 1325, null);
                        throw new RuntimeException("上传文件解析异常(属性无匹配或只部分匹配)");
                    } else {
                        List<AttributeModel> lstAttr = shpReader.getOneFeatureAttributeModel(0);
                        for (AttributeModel item : lstAttr) {
                            String dataType = item.getAttributeType().toString();
                            item.setAttributePrecision(item.getAttributePrecision());

                            switch (dataType) {
                                case "OFTDate":
                                case "OFTDateTime":
                                    item.setAttributeTypeName(utilityMessageConvert.get("时间型"));
                                    break;
                                case "OFTInteger":
                                    item.setAttributeTypeName(utilityMessageConvert.get("短整型"));
                                    break;
                                case "OFTIntegerList":
                                    item.setAttributeTypeName(utilityMessageConvert.get("长整型"));
                                    break;
                                case "OFTInt64":
                                    item.setAttributeTypeName(utilityMessageConvert.get("长整型"));
                                    break;
                                case "OFTReal":
                                    if (item.getAttributeWidth() <= 13) {
                                        item.setAttributeTypeName(utilityMessageConvert.get("单浮点型"));
                                    } else {
                                        item.setAttributeTypeName(utilityMessageConvert.get("双浮点型"));
                                    }
                                    break;
                                case "OFTString":
                                    item.setAttributeTypeName(utilityMessageConvert.get("字符型"));
                                    break;
                                case "OFTTime":
                                    item.setAttributeTypeName(utilityMessageConvert.get("时间型"));
                                    break;
                                default:
                                    item.setAttributeTypeName(utilityMessageConvert.get("字符型"));
                                    break;
                            }

                            Optional<DictDataCodeEntity> dicEntity = listDataType.stream()
                                    .filter(entity -> entity.getCodeName().equals(item.getAttributeTypeName()))
                                    .findFirst();

                            String attributeType = dicEntity.map(DictDataCodeEntity::getId).orElse("");

                            try {
                                LayerFieldEntity layerField = list.stream()
                                        .filter(entity -> entity.getAttributeName().equals(item.getAttributeName()))
                                        .findFirst()
                                        .orElse(null);

                                if (Func.isNotEmpty(layerField)) {
                                    if (!layerField.getAttributeLength().equalsIgnoreCase(String.valueOf(item.getAttributeWidth()))
                                            || !layerField.getAttributeType().equalsIgnoreCase(attributeType)
                                            || !layerField.getAttributePrecision().equalsIgnoreCase(String.valueOf(item.getAttributePrecision()))) {
                                        success = false;
                                        liv.setStatus(false);
                                        liv.setMessage(utilityMessageConvert.get("图层属性信息匹配异常"));

                                        operateLogService.writeOperateLog(layerID, user, 1001, 1103, 1202, 1327, null);
                                    }
                                }
                            } catch (Exception ex) {
                                success = false;
                                liv.setStatus(false);
                                liv.setMessage(utilityMessageConvert.get("图层属性信息匹配异常"));
                                operateLogService.writeOperateLog(layerID, user, 1001, 1103, 1202, 1328, null);
                            }
                        }

                        if (success) {
                            String logID = UUID.randomUUID().toString();
                            String strSQL = "insert into sdms_layer_readlog(Id,LayerID,ShpFileName,ReadStatus,CreateDT,CreateBy) values('" + logID + "','" + layerID + "','" + fileName + "','0','" + LocalDateTime.now() + "','" + user + "');";
                            dynamicTableMapper.dynamicInsertExecute(strSQL);

                            /**
                             * 入库，发布geoserver
                             */
                            if (connection != null) WebSocketUtil.sendMsg(connection, userId, msgType, 9, 91, "shp文件上传到geoserver", false, false, guid);
                            // 设置回调参数，任务完成后会更新layerLog和发送WebSocket消息
                            int targetIsShow = (inputLc.getIsShow() != null && inputLc.getIsShow() == 0) ? 0 : 1;
                            excuteShpJob.setCallbackParams(connection, userId, msgType, guid, layerLogId, targetIsShow);
                            ThreadPool.execute(excuteShpJob);

                            // 入库
//                            shpImporter(path, tableName);
                            // 发布
//                            publishGeoServer(tableName, layer.getLayerName(), )

                            liv.setStatus(true);
                            liv.setMessage(utilityMessageConvert.get("上传文件成功"));
                        }
                    }
                }
            } catch (Exception ex) {
                liv.setStatus(false);
                liv.setMessage(utilityMessageConvert.get("上传文件解析异常"));
                operateLogService.writeOperateLog(layerID, user, 1001, 1103, 1202, 1322, null);
                log.error("Error during file upload and registration: ", ex);
                throw new RuntimeException(ex.getMessage());
            }
        } else {
            liv.setStatus(false);
            liv.setMessage(utilityMessageConvert.get("上传文件解析异常(上传必要文件不存在或不完整)"));
            operateLogService.writeOperateLog(layerID, user, 1001, 1103, 1202, 1326, null);
        }
        return liv;
    }


    private String buildCreateTableSql(String tableName, String geomType, List<AttributeModel> attrs, int srid) {
        StringBuilder sb = new StringBuilder("CREATE TABLE IF NOT EXISTS ");
        sb.append(tableName).append(" (");
        sb.append("id SERIAL PRIMARY KEY, ");
        sb.append("geom GEOMETRY(").append(geomType).append(", ").append(srid).append(")");

        for (AttributeModel attr : attrs) {
            String name = escapeFieldName(attr.getAttributeName().toLowerCase());
            String type = convertOgrTypeToPg(
                    attr.getAttributeType().getValue(),
                    attr.getAttributeWidth()
            );
            sb.append(", \"").append(name).append("\" ").append(type);
        }
        sb.append(")");
        return sb.toString();
    }

    private List<Map<String, Object>> prepareRecords(List<Feature> features, List<AttributeModel> attrs) {
        List<Map<String, Object>> records = new ArrayList<>();
        for (Feature f : features) {
            if (f == null) continue;
            var geom = f.GetGeometryRef();
            if (geom == null) continue;

            String wkt = geom.ExportToWkt();
            if (wkt == null || wkt.trim().isEmpty()) continue;

            Map<String, Object> record = new HashMap<>();
            record.put("wkt", wkt); // 专供 geom 使用

            for (AttributeModel attr : attrs) {
                String name = attr.getAttributeName();
                String value = f.GetFieldAsString(name);
                record.put(name.toLowerCase(), (value == null || value.trim().isEmpty()) ? null : value.trim());
            }

            records.add(record);
        }
        return records;
    }

    private String escapeFieldName(String name) {
        return name.replaceAll("\"", "");
    }

    private String convertOgrTypeToPg(int ogrType, int width) {
        switch (ogrType) {
            case ogrConstants.OFTInteger:
                return "INTEGER";
            case ogrConstants.OFTReal:
                return "DOUBLE PRECISION"; // PG 中 REAL 精度低，一般用 DOUBLE
            case ogrConstants.OFTString:
                // Shapefile 中 width=0 很常见，直接用 TEXT 避免问题
                return "TEXT";
            case ogrConstants.OFTDate:
                return "DATE";
            case ogrConstants.OFTDateTime:
                return "TIMESTAMP";
            case ogrConstants.OFTTime:
                return "TIME";
            default:
                return "TEXT";
        }
    }

    /**
     * 判断上传文件是否完整
     *
     * @param filePath 文件路径
     * @param kvd      验证结果DTO
     * @return 文件是否存在
     */
    public boolean isFileExist(String filePath, LayerImportValidDto kvd) {
        String name = filePath.substring(0, filePath.lastIndexOf("."));

        String fileFullName = Paths.get(System.getProperty("user.dir"), appSettingConfig.getUploadFilePath()).toString();

        kvd.setFilePath(Paths.get(fileFullName, new File(filePath).getName()).toString());
        String strSQL = "SELECT * FROM public.sdms_dictdatacode where datatypeid='b9ef9c7c-67b3-11e7-8eb2-005056bb1c7e'";

        List<Map<String, Object>> list = dynamicTableMapper.dynamicExecute(strSQL);

        try {
            if (list != null && !list.isEmpty()) {
                for (Map<String, Object> stringObjectMap : list) {
                    String fileName = name + "." + stringObjectMap.get("codevalue");
                    String uploadFilePath;

                    if (!fileName.contains(fileFullName)) {
                        uploadFilePath = Paths.get(fileFullName, new File(fileName).getName()).toString();
                    } else {
                        uploadFilePath = fileName;
                    }

                    if (!new File(uploadFilePath).exists()) {
                        kvd.setStatus(false);
                        kvd.setMessage(fileName + " 不存在！");
                        System.out.println(uploadFilePath);
                        return false;
                    }
                }
                return true;
            } else {
                log.info(strSQL + "执行结果为空!");
                kvd.setStatus(false);
                kvd.setMessage(strSQL + "执行结果为空!");
                return false;
            }
        } catch (Exception e) {
            log.error("isFileExist出现异常", e);
            return false;
        }
    }


    private boolean getThumbnial(String name, String bBox) {

        ThumbnailHelper tbh = thumbnailHelper;
        String imagePath = tbh.createThumbnail(name, "map", bBox, "", "");

        return !Func.isEmpty(imagePath);

    }

    private String formatXml(String unformattedXml) {
        try {
            // 解析XML字符串为Document对象
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new java.io.StringReader(unformattedXml)));

            // 配置Transformer用于格式化输出
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");  // 启用缩进
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "1");  // 缩进层级（对应C#的Indentation=1）
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");  // 指定输出方法为XML

            // 使用StringWriter接收格式化后的内容
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));

            return writer.toString();
        } catch (Exception ex) {
            // 异常处理（与C#逻辑一致，返回原始未格式化内容或抛出异常）
            return unformattedXml;
        }
    }


    /**
     * 更改图层样式
     *
     * @param layerId 图层id
     * @param styleId 样式id
     * @return 是否成功
     */
    public boolean updateLayer(String layerId, String styleId) {

        String layerType = "";
        String layerName = "";

        // 查询样式（假设使用Spring Data JPA，返回Optional对象）
//        DataStyleEntity style = dataStyleService.getById(styleId);
        DataStyleEntity style = dataStyleMapper.selectById(styleId);
        // 查询图层
        LayerContentEntity layer = baseMapper.selectById(layerId);

        // 处理样式名称空值
        if (style != null && style.getStyleName() == null) {
            style.setStyleName("");
        }

        // 获取图层属性表名
        if (layer != null) {
            layerName = layer.getLayerAttrTable();
        }

        // 查询数据类型字典（假设layer不为null时访问dataType）
        if (layer != null) {
            DictDataCodeEntity dic = dictDataCodeService.getById(layer.getDataType());
            if (dic != null) {
                layerType = dic.getCodeName();
            }
        }

        return geoServer.publicLayer(layerName, (layer != null) ? layer.getLayerName() : "", layerType, (style != null) ? style.getStyleName() : "");
    }


    private String changeDataToD(String strData) {
        try {
            String result = "";
            if (strData.contains("E")) {
                // 使用BigDecimal解析科学计数法字符串（自动处理E表示）
                BigDecimal dData = new BigDecimal(strData);
                result = dData.toPlainString(); // 转换为非科学计数法的字符串表示
            } else {
                result = strData;
            }
            return result;
        } catch (Exception ex) {
            return "error";
        }
    }


    private LayerContentDto insertLayerData(LayerContentInputDto input) {
        return insertLayerData(input, null, null, null, null);
    }

    /**
     * 新增图层私有方法
     *
     * @param input
     * @return
     */
    private LayerContentDto insertLayerData(LayerContentInputDto input, SignalrClient connection, String userId, String msgType, String guid) {
        try {
            // 通用处理：生成ID和表名
            String name = "";
            if(Func.isEmpty(input.getId())){
                input.setId(UUID.randomUUID().toString());
            }

            if (input.getLayerName() != null && !input.getLayerName().isEmpty()) {
                ChineseConvert chn = new ChineseConvert();
                name = chn.getPinyinInitials(input.getLayerName());
                String lastIndex = String.valueOf(LocalDateTime.now().getDayOfMonth()) + LocalDateTime.now().getHour() + LocalDateTime.now().getMinute() + LocalDateTime.now().getSecond();

                int nameLength = (30 - lastIndex.length()) - 1;
                input.setLayerAttrTable((input.getUploadFileType().equals("1") ? "v" : "g") + (name.length() > nameLength ? name.substring(0, nameLength) : name) + lastIndex);
                input.setLayerSpatialTable((input.getUploadFileType().equals("1") ? "v" : "g") + (name.length() > (nameLength - 7) ? name.substring(0, (nameLength - 7)) : name) + lastIndex + "_s");
            }

            // 通用处理：获取文件编码和大小
            String fileencoding = "GBK";
            if (Func.isNotEmpty(input.getUploadFileName())) {
                List<String> files = Arrays.stream(input.getUploadFileName().split("\\*"))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
                for (String file : files) {
                    Path filePath = Paths.get(file);
                    if (Files.exists(filePath)) {
                        String extension = file.substring(file.lastIndexOf(".")).toLowerCase();
                        if (".tif".equals(extension)) {
                            try {
                                long fileSizeInBytes = Files.size(filePath);
                                int fileSizeInKB = (int) (fileSizeInBytes / 1024);
                                input.setFileSize(fileSizeInKB);
                            } catch (IOException e) {
                                log.warn("无法获取文件大小: " + file, e);
                            }
                        } else if (".shp".equals(extension)) {
                            try {
                                int fileSizeKB = getFileSize(file);
                                input.setFileSize(fileSizeKB);
                            } catch (Exception e) {
                                log.warn("无法获取 shp 文件大小: " + file, e);
                            }
                        }
                    }
                }
                if (input.getUploadFileName().endsWith(".shp")){
                    String[] split = input.getUploadFileName().split("\\.");
                    String s = split[0] + ".cpg";
                    Path filePath = Paths.get(s);
                    if (Files.exists(filePath)) {
                        fileencoding = Files.readString(filePath, StandardCharsets.US_ASCII).trim();//赋值编码
                    }
                }
            }

            // 根据数据类型分发处理
            if (input.getUploadFileType().equals("1")) {
                // 矢量数据处理
                return insertVectorLayerData(input, connection, userId, msgType, guid, fileencoding);
            } else if (input.getUploadFileType().equals("2")) {
                // 栅格数据处理
                return insertRasterLayerData(input, connection, userId, msgType, guid, fileencoding);
            }

            return null;
        } catch (Exception e) {
            log.error("图层处理异常", e);
            if (connection != null) WebSocketUtil.sendMsg(connection, userId, msgType, 0, 100, "图层处理异常: " + e.getMessage(), true, true, guid);
            throw new RuntimeException(e);
        }
    }

    /**
     * 构建基础图层实体（通用属性）
     */
    private LayerContentEntity buildBaseLayerEntity(LayerContentInputDto input, String fileencoding) {
        LayerContentEntity entity = new LayerContentEntity();
        entity.setId(input.getId());
        entity.setLayerName(input.getLayerName());
        entity.setDataType(input.getDataType());
        entity.setLayerBBox(input.getLayerBBox());
        entity.setLayerType(input.getLayerType());
        entity.setLayerTag(input.getLayerTag());
        entity.setLayerDesc(input.getLayerDesc());
        entity.setLayerAttrTable(input.getLayerAttrTable());
        entity.setLayerSpatialTable(input.getLayerSpatialTable());
        entity.setLayerRefence(input.getLayerRefence());
        entity.setMaxX(input.getMaxX());
        entity.setMinX(input.getMinX());
        entity.setMaxY(input.getMaxY());
        entity.setMinY(input.getMinY());
        entity.setCreateBy(input.getCreateBy());
        entity.setCreateDT(LocalDateTime.now());
        entity.setUploadFileType(input.getUploadFileType());
        entity.setUploadFileName(input.getUploadFileName());
        entity.setFileSize(input.getFileSize());
        entity.setPublishStatus(Func.isNotEmpty(input.getPublishStatus()) ? input.getPublishStatus() : "0");
        // 设置 layerSort：若传入值为空，则取当前最大值+1
        if (input.getLayerSort() != null) {
            entity.setLayerSort(input.getLayerSort());
        } else {
            Map<String, Object> maxSortMap = this.getMap(
                new QueryWrapper<LayerContentEntity>()
                    .select("MAX(layersort) as maxlayersort")
            );
            Integer maxSort = maxSortMap == null ? null : (Integer) maxSortMap.get("maxlayersort");
            entity.setLayerSort(maxSort == null ? 1 : maxSort + 1);
        }
        entity.setFileencoding(fileencoding);
        entity.setIsShow(input.getIsShow());
        return entity;
    }

    /**
     * 处理矢量数据（uploadFileType=1）
     */
    private LayerContentDto insertVectorLayerData(LayerContentInputDto input, SignalrClient connection, String userId, String msgType, String guid, String fileencoding) {
        try {
            LayerContentEntity entity = buildBaseLayerEntity(input, fileencoding);

            if (new File(entity.getUploadFileName()).exists()) {
                String shpFileName = entity.getUploadFileName();
                ShpReader shpReader = new ShpReader(shpFileName);

                // 增加坐标系判断与转换逻辑
                Map<String, Double> tempBbox = shpReader.getLayerBBox();
                boolean isProjected = false;
                if (tempBbox != null && !tempBbox.isEmpty()) {
                    double minX = tempBbox.get("MinX");
                    double maxX = tempBbox.get("MaxX");
                    // 简单判断：如果坐标值超过180，认为是投影坐标系（米）
                    if (Math.abs(minX) > 180 || Math.abs(maxX) > 180) {
                        isProjected = true;
                    }
                }

                if (isProjected) {
                    if (connection != null) WebSocketUtil.sendMsg(connection, userId, msgType, 3, 30, "正在进行坐标转换...", false, false, guid);
                    String originWkt = shpReader.getSridWkt();
                    entity.setOriginLayerRefence(originWkt);

                    Map<String, Double> originBbox = shpReader.getLayerBBox();
                    if (!originBbox.isEmpty()) {
                        entity.setOriginMinX(BigDecimal.valueOf(originBbox.get("MinX")));
                        entity.setOriginMaxX(BigDecimal.valueOf(originBbox.get("MaxX")));
                        entity.setOriginMinY(BigDecimal.valueOf(originBbox.get("MinY")));
                        entity.setOriginMaxY(BigDecimal.valueOf(originBbox.get("MaxY")));
                    }

                    shpReader.close(); // 关闭文件以便重命名

                    File shpFile = new File(shpFileName);
                    String parentDir = shpFile.getParent();
                    String fileName = shpFile.getName();
                    String baseName = fileName.substring(0, fileName.lastIndexOf("."));

                    // 重命名相关文件
                    File dir = new File(parentDir);
                    File[] files = dir.listFiles((d, shpName) -> shpName.startsWith(baseName + "."));
                    if (files != null) {
                        for (File f : files) {
                            String ext = f.getName().substring(f.getName().lastIndexOf("."));
                            File newFile = new File(dir, "new_" + baseName + ext);
                            f.renameTo(newFile);
                        }
                    }

                    String sourceShpPath = Paths.get(parentDir, "new_" + baseName + ".shp").toString();
                    String targetShpPath = shpFileName; // 转换回原文件名
                    String proj = "GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\",SPHEROID[\"WGS 84\",6378137,298.257223563,AUTHORITY[\"EPSG\",\"7030\"]],AUTHORITY[\"EPSG\",\"6326\"]],PRIMEM[\"Greenwich\",0,AUTHORITY[\"EPSG\",\"8901\"]],UNIT[\"degree\",0.0174532925199433,AUTHORITY[\"EPSG\",\"9122\"]],AUTHORITY[\"EPSG\",\"4326\"]]";

                    // 在坐标系转换之前，如果是UTF-8格式 先把编码统一转成 GBK
                    String cpgPath = sourceShpPath.replace(".shp", ".cpg");
                    String sourceEnc = "";
                    if (Files.exists(Paths.get(cpgPath))) {
                        sourceEnc = Files.readString(Paths.get(cpgPath)).trim();
                    }
                    if ("UTF-8".equalsIgnoreCase(sourceEnc)) {
                        ShpFileUtil.convertShpEncoding(sourceShpPath, sourceEnc, "GBK");
                    }

                    VertorProjection.ChangeVertorSrs(sourceShpPath, targetShpPath, proj, "GBK");
                    // 将原始文件的 cpg 复制到新文件（GDAL 坐标系转换后不生成 cpg）
                    String sourceCpg = sourceShpPath.replace(".shp", ".cpg");
                    String targetCpg = targetShpPath.replace(".shp", ".cpg");
                    if (Files.exists(Paths.get(sourceCpg))) {
                        Files.copy(Paths.get(sourceCpg), Paths.get(targetCpg), StandardCopyOption.REPLACE_EXISTING);
                    }
                    try {
                        // 重新读取转换后的文件
                        shpReader = new ShpReader(shpFileName);
                    } catch (Exception e){
                        log.error("矢量数据坐标转换失败: " + shpFileName + e);
                        if (connection != null) WebSocketUtil.sendMsg(connection, userId, msgType, 0, 100, "矢量数据坐标转换失败: " + e.getMessage(), true, true, guid);
                    }
                }

                String csSrc = shpReader.getSridWkt();

                int startindex = csSrc.indexOf("GEOGCS[\"") + 8;
                int endindex = csSrc.indexOf(",DATUM") - 1;

                if (endindex > startindex) {
                    entity.setLayerRefence(csSrc.substring(startindex, endindex));
                } else {
                    entity.setLayerRefence(appSettingConfig.getSpatialReference());
                }

                Map<String, Double> bbox = shpReader.getLayerBBox();
                if (!bbox.isEmpty()) {
                    entity.setMinX(BigDecimal.valueOf(bbox.get("MinX")));
                    entity.setMaxX(BigDecimal.valueOf(bbox.get("MaxX")));
                    entity.setMinY(BigDecimal.valueOf(bbox.get("MinY")));
                    entity.setMaxY(BigDecimal.valueOf(bbox.get("MaxY")));
                }
                shpReader.close();
            }
            if (connection != null) WebSocketUtil.sendMsg(connection, userId, msgType, 5, 60, "正在保存图层信息...", false, false, guid);
            int query = baseMapper.insert(entity);
            operateLogService.writeOperateLog(input.getId(), input.getCreateBy(), 1001, 1101, 1201, 1313, "(" + input.getLayerName() + ")");
            return BeanUtil.copy(entity, LayerContentDto.class);

        } catch (Exception e) {
            log.error("矢量图层处理失败", e);
            if (connection != null) WebSocketUtil.sendMsg(connection, userId, msgType, 0, 100, "矢量图层处理失败: " + e.getMessage(), true, true, guid);
            throw new RuntimeException(e);
        }
    }

    /**
     * 处理栅格数据（uploadFileType=2）
     */
    private LayerContentDto insertRasterLayerData(LayerContentInputDto input, SignalrClient connection, String userId, String msgType, String guid, String fileencoding) {
        try {
            LayerContentEntity entity = buildBaseLayerEntity(input, fileencoding);

            if (connection != null) WebSocketUtil.sendMsg(connection, userId, msgType, 2, 15, "正在处理栅格数据...", false, false, guid);
            String dataType = DictionaryIds.GEOM_RASTER;
            entity.setDataType(dataType);

            String GeoServerIp = appSettingConfig.getGeoServerIP();
            String GeoServerPort = appSettingConfig.getGeoServerPort();
            String GeoWorkSpace = appSettingConfig.getGeoWorkspace();
            String UploadFilePath = Paths.get(System.getProperty("user.dir"), appSettingConfig.getUploadFilePath()).toString();

            String zipFilePath = Paths.get(UploadFilePath, input.getLayerAttrTable() + ".zip").toString();

            // 坐标处理
            String[] split = input.getUploadFileName().split("\\*");
            for (String item : split) {
                if (item.substring(item.lastIndexOf(".")).equalsIgnoreCase(".tif")) {
                    automaticToTif(item);
                }
            }

            if (!Files.exists(Paths.get(UploadFilePath, input.getLayerAttrTable()))) {
                Files.createDirectories(Paths.get(UploadFilePath, input.getLayerAttrTable()));
            }

            String tifPath = "";
            for (String fullPath : input.getUploadFileName().split("\\*")) {
                // 提取纯文件名
                String fileName = Paths.get(fullPath).getFileName().toString();
                String newFileName = input.getLayerAttrTable() + fileName.substring(fileName.lastIndexOf("."));
                Path sourcePath = Paths.get(fullPath);
                Path targetDir = Paths.get(UploadFilePath, input.getLayerAttrTable());
                Path targetPath = targetDir.resolve(newFileName);

                if (Files.exists(sourcePath)) {
                    Files.createDirectories(targetDir);
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);

                    if (fileName.toLowerCase().endsWith(".tif") || fileName.toLowerCase().endsWith(".geotiff")) {
                        tifPath = sourcePath.toString();
                    }
                }
            }

            int rasterCount = 0;
            double[] dd = new double[2];
            double d1 = 0;

            if (new File(tifPath).exists()) {
                try {
                    try {
                        GdalEnvInitializer.ensureConfigured();
                    } catch (Exception gdalCfgEx) {
                        // GDAL 环境配置失败时降级（不影响读取尺寸/波段/投影元信息）
                        log.warn("GDAL 环境配置失败（降级继续）: {}", gdalCfgEx.getMessage());
                    }
                    RasterImgInfo imgInfo = new RasterImgInfo();
                    boolean isok = imgInfo.open(tifPath, true);

                    if (isok) {
                        double xLeft = imgInfo.getXLeft();
                        double xRight = imgInfo.getXRight();
                        boolean isProjected = (Math.abs(xLeft) > 180 || Math.abs(xRight) > 180);

                        if (isProjected) {
                            if (connection != null) WebSocketUtil.sendMsg(connection, userId, msgType, 3, 30, "正在进行坐标转换...", false, false, guid);
                            entity.setOriginLayerRefence(imgInfo.getData().GetProjectionRef());
                            entity.setOriginMinX(BigDecimal.valueOf(imgInfo.getXLeft()));
                            entity.setOriginMaxX(BigDecimal.valueOf(imgInfo.getXRight()));
                            entity.setOriginMinY(BigDecimal.valueOf(imgInfo.getYBottom()));
                            entity.setOriginMaxY(BigDecimal.valueOf(imgInfo.getYTop()));

                            imgInfo.close();

                            File oldFile = new File(tifPath);
                            String parentDir = oldFile.getParent();
                            String fileName = oldFile.getName();
                            String newFileName = "new_" + fileName;
                            File newFile = new File(parentDir, newFileName);
                            oldFile.renameTo(newFile);

                            String wgs84Wkt = "GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\",SPHEROID[\"WGS 84\",6378137,298.257223563,AUTHORITY[\"EPSG\",\"7030\"]],AUTHORITY[\"EPSG\",\"6326\"]],PRIMEM[\"Greenwich\",0,AUTHORITY[\"EPSG\",\"8901\"]],UNIT[\"degree\",0.0174532925199433,AUTHORITY[\"EPSG\",\"9122\"]],AUTHORITY[\"EPSG\",\"4326\"]]";

                            RasterProjection.ChangeRasterSrs(newFile.getPath(), tifPath, wgs84Wkt);
                            try {
                                imgInfo = new RasterImgInfo();
                                isok = imgInfo.open(tifPath, true);
                            } catch (Exception e){
                                log.error("栅格数据坐标转换失败: " + tifPath);
                                if (connection != null) WebSocketUtil.sendMsg(connection, userId, msgType, 0, 100, "栅格数据坐标转换失败: " + e.getMessage(), true, true, guid);
                            }
                        }
                    }

                    if (isok) {
                        rasterCount = imgInfo.getData().getRasterCount();
                        Band band = imgInfo.getData().GetRasterBand(1);
                        band.ComputeRasterMinMax(dd, 1);
                        Double[] hasNoData = new Double[1];
                        band.GetNoDataValue(hasNoData);
                        if(Func.isNotEmpty(hasNoData) && Func.isNotEmpty(hasNoData[0])){
                            d1 = hasNoData[0];
                        }

                        // 统计不同像素值的数量
                        int tifVariety = countUniquePixelValues(tifPath);
                        entity.setTifVariety(tifVariety);

                        // 设置栅格数据类型
                        int rasterDataType = band.getDataType();
                        entity.setRasterDatatype(getDataTypeName(rasterDataType));

                        entity.setMinX(BigDecimal.valueOf(imgInfo.getXLeft()));
                        entity.setMinY(BigDecimal.valueOf(imgInfo.getYBottom()));
                        entity.setMaxX(BigDecimal.valueOf(imgInfo.getXRight()));
                        entity.setMaxY(BigDecimal.valueOf(imgInfo.getYTop()));

                        String csSrc = imgInfo.getCoordsystem().getWKT();
                        int startindex = csSrc.indexOf("GEOGCS[\"") + 8;
                        int endindex = csSrc.indexOf(",DATUM") - 1;
                        String Refence = csSrc.substring(startindex, endindex);
                        entity.setLayerRefence(Refence);
                    }

                    imgInfo.close();
                } catch (Exception ex) {
                    throw ex;
                }
            } else {
                throw new Exception();
            }

            String bboxStr2 = String.format("%s,%s,%s,%s", entity.getMinX(), entity.getMaxX(), entity.getMinY(), entity.getMaxY());
            String type = appSettingConfig.getGeoServerPublishType();

            // GeoServer发布
            if (connection != null) WebSocketUtil.sendMsg(connection, userId, msgType, 4, 40, "正在发布GeoServer图层...", false, false, guid);
            try {
                if (type.equals("1")) {
                    String gisDataPath = "";
                    if (!Files.exists(Paths.get(gisDataPath))) {
                        Files.createDirectories(Paths.get(gisDataPath));
                    }
                    Path gisFolderPath = Paths.get(gisDataPath, GeoWorkSpace, input.getLayerAttrTable());
                    Path gisFilePath = Paths.get(String.valueOf(gisFolderPath), input.getLayerAttrTable() + tifPath.substring(tifPath.lastIndexOf(".")));
                    if (!Files.exists(gisFolderPath)) {
                        Files.createDirectories(gisFolderPath);
                    }
                    Files.copy(Path.of(tifPath), gisFilePath);
                    String gisDataFilePath = Paths.get("data", GeoWorkSpace, input.getLayerAttrTable(), gisFilePath.getFileName().toString()).toString();
                    gisDataFilePath = gisDataFilePath.replace("\\", "/");
                    String epsgC = appSettingConfig.getEpsg();

                    geoServer.putCoverageStoresLocal(GeoWorkSpace, input.getLayerAttrTable(), gisDataFilePath);
                    boolean b = geoServer.putStoreTitleLocal(GeoWorkSpace, input.getLayerAttrTable(), input.getLayerAttrTable(), input.getLayerAttrTable(), epsgC, bboxStr2, rasterCount);
                    if (!b) {
                        throw new Exception("发布数据失败！");
                    }
                } else {
                    boolean success = ZipHelper.zipDir(Paths.get(UploadFilePath, input.getLayerAttrTable()).toString(), zipFilePath, 9);
                    if (success) {
                        geoServer.putCoverageStore(GeoWorkSpace, input.getLayerAttrTable(), zipFilePath);
                        String epsgC = appSettingConfig.getEpsg();
                        geoServer.updateStoreTitle(GeoWorkSpace, input.getLayerAttrTable(), input.getLayerAttrTable(), input.getLayerAttrTable(), epsgC, rasterCount);
                    } else {
                        throw new Exception("文件压缩失败");
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("发布GeoServer图层失败: " + e.getMessage(), e);
            }

            // 样式配置
            if (connection != null) WebSocketUtil.sendMsg(connection, userId, msgType, 5, 50, "正在配置图层样式...", false, false, guid);
            try {
                LambdaQueryWrapper<DataStyleEntity> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.eq(DataStyleEntity::getStyleDefaultLayer, input.getId());
                DataStyleEntity styleEntity = dataStyleMapper.selectOne(queryWrapper);
                if (styleEntity != null) {
                    styleEntity.setStyleName(input.getLayerAttrTable());
                    dataStyleMapper.updateById(styleEntity);

                    if (Func.isNotEmpty(styleEntity.getStyleContent())) {
                        GeoServerUtil.bindStyleToLayer("http://" + appSettingConfig.getGeoServerIP() + ":" + appSettingConfig.getGeoServerPort() + "/geoserver",
                                appSettingConfig.getGeoServerUser(),
                                appSettingConfig.getGeoServerPwd(),
                                appSettingConfig.getGeoWorkspace(),
                                input.getLayerAttrTable(),
                                input.getLayerAttrTable(),
                                styleEntity.getStyleContent());
                    }
                }
            } catch (Exception e) {
                log.warn("配置图层样式失败: " + e.getMessage());
            }

            // 获取SRS并生成缩略图
            String srs = "";
            ConverageRequest data = geoServer.getConverageInfo(GeoWorkSpace, input.getLayerAttrTable(), input.getLayerAttrTable());
            if (data != null) {
                srs = data.getCoverage().getSrs();
            }
            if (connection != null) WebSocketUtil.sendMsg(connection, userId, msgType, 5, 55, "正在生成缩略图...", false, false, guid);
            try {
                String bboxStr = String.format("%s,%s,%s,%s", entity.getMinX(), entity.getMinY(), entity.getMaxX(), entity.getMaxY());
                thumbnailHelper.createThumbnail(input.getLayerAttrTable(), "layer", bboxStr, input.getUploadFileType(), srs);
            } catch (Exception e) {
                log.warn("生成缩略图失败: " + e.getMessage());
            }

            // 保存到数据库
            if (connection != null) WebSocketUtil.sendMsg(connection, userId, msgType, 5, 60, "正在保存图层信息...", false, false, guid);
            entity.setUploadStatus("1");
            entity.setRasterCount(rasterCount);
            layerMapper.insert(entity);
            LayerContentDto result = BeanUtil.copy(entity, LayerContentDto.class);
            result.setResultData(rasterCount + "," + dd[0] + "," + dd[1] + "," + d1);
            operateLogService.writeOperateLog(input.getId(), input.getCreateBy(), 1001, 1101, 1201, 1315, "(" + input.getLayerName() + ")");
            return result;

        } catch (Exception e) {
            log.error("栅格图层处理失败", e);
            if (connection != null) WebSocketUtil.sendMsg(connection, userId, msgType, 0, 100, "栅格图层处理失败: " + e.getMessage(), true, true, guid);
            throw new RuntimeException(e);
        }
    }

    /**
     * 影响坐标转换
     *
     * @param filePath
     * @return
     */
    public String automaticToTif(String filePath) {
        String wkt = null;
        try {
            // 获取上传目录（当前工作目录下的 UploadFile）
            Path uploadDir = Paths.get(System.getProperty("user.dir"), appSettingConfig.getUploadFilePath());
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir); // 自动创建目录（若不存在）
            }
            String ret = "";
            Path sourcePath = Paths.get(filePath);

            if (Files.exists(sourcePath)) {
                try (RasterImgInfo imgInfo = new RasterImgInfo()) {
                    if (imgInfo.open(filePath, true))
                        wkt = imgInfo.getCoordsystem().getWKT();
                }

                if (wkt == null || !wkt.contains("PROJCS")) {
                    return "";
                }

                // 调用坐标转换方法（需自定义实现）
                String temp = coordTransToTif(filePath, wkt);
                if (temp == null || temp.isEmpty()) {
                    return "";
                }

                Path tempPath = Paths.get(temp);
                // 复制转换后的文件到目标目录
                Path targetPath = uploadDir.resolve(tempPath.getFileName());
                if (Files.exists(tempPath)) {
                    Files.copy(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    ret = targetPath.toString();
                }
                // 清理临时目录（递归删除）
                Path tempDir = tempPath.getParent();
                try {
                    if (Files.exists(tempDir)) {
                        FileServiceHelper.deleteDirectory(tempDir); // 需确保目录内文件已 unlock
                    }
                } catch (Exception e) {
                    // 记录日志，但不要中断主流程
                    log.error("清理临时目录失败: {}, {}", tempDir, e.getMessage());
                }
            }
            return ret;
        } catch (Exception ex) {
            throw new RuntimeException("影像坐标转换失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * TIF文件坐标转换（Java转换版本）
     *
     * @param file           文件路径
     * @param coordsystemWKT 坐标系WKT字符串
     * @return 转换后的TIF文件路径
     */
    public String coordTransToTif(String file, String coordsystemWKT) {
        // 检查文件扩展名是否为.tif
        String ext = Paths.get(file).getFileName().toString();
        int dotIndex = ext.lastIndexOf('.');
        if (dotIndex == -1 || !ext.substring(dotIndex).equalsIgnoreCase(".tif")) {
            return "";
        }

        Path srcFile = Paths.get(file);

        // 初始化GDAL（需确保GDAL Java绑定库已正确配置）
        gdal.AllRegister();
        try {
            // 提取文件名（不带扩展名）
            String name = ext.substring(0, dotIndex);

            // 创建目标目录（格式：原目录\CoordTransform\文件名）
            Path newFolder = Paths.get(srcFile.getParent().toString(), "CoordTransform", name);
            try {
                if (!Files.exists(newFolder)) {
                    Files.createDirectories(newFolder);
                }
            } catch (IOException e) {
                throw new RuntimeException("创建目录失败: " + e.getMessage(), e);
            }

            // 构造临时文件路径
            Path tmpFile = newFolder.resolve(name + ".tif");

            // ✅ 核心：使用 GDAL Warp 重投影
            boolean success = warpReproject(file, coordsystemWKT, tmpFile.toString());
            if (!success) {
                return "";
            }

            // 返回生成的 .tif 文件路径
            try (Stream<Path> files = Files.list(newFolder)) {
                Optional<Path> tif = files
                        .filter(p -> {
                            String n = p.getFileName().toString().toLowerCase();
                            return n.endsWith(".tif") || n.endsWith(".tiff");
                        })
                        .findFirst();
                return tif.map(Path::toString).orElse("");
            }
        } catch (Exception e) {
            log.warn("查找目录下 TIFF 文件失败: {}", e.getMessage(), e);
            return "";
        }
    }

    private boolean warpReproject(String srcFile, String targetWkt, String dstFile) {
        Dataset srcDs = null;
        Dataset warped = null;
        Dataset outDs = null;

        try {
            // 1. 打开源文件
            srcDs = gdal.Open(srcFile, gdalconst.GA_ReadOnly);
            if (srcDs == null) {
                log.info("无法打开源文件: {}", gdal.GetLastErrorMsg());
                return false;
            }

            // 2. 获取源坐标系（WKT）
            String srcWkt = srcDs.GetProjection();
            if (srcWkt == null || srcWkt.trim().isEmpty()) {
                log.info("源 TIF 未包含坐标系信息");
                return false;
            }

            // 3. 验证目标 WKT
            SpatialReference targetSrs = new SpatialReference();
            targetSrs.ImportFromWkt(targetWkt);
            if (targetSrs.Validate() != 0) {
                log.error("目标坐标系 WKT 无效");
                return false;
            }


            // 4. 使用 GDAL Warp 重投影（推荐方式）
            warped = gdal.AutoCreateWarpedVRT(
                    srcDs,
                    srcWkt,           // 源 WKT
                    targetWkt,        // 目标 WKT
                    gdalconst.GRA_Bilinear, // 重采样算法
                    0.125            // 最大误差（像素）
            );
            if (warped == null) {
                log.error("重投影失败: {}", gdal.GetLastErrorMsg());
                return false;
            }

            // 5. 保存为 GeoTIFF
            Driver driver = gdal.GetDriverByName("GTiff");
            outDs = driver.CreateCopy(dstFile, warped, 0, (String[]) null);
            if (outDs == null) {
                log.error("保存输出文件失败: {}", gdal.GetLastErrorMsg());
                return false;
            }
            outDs.FlushCache(); // ✅ 强制写入磁盘
            return true;
        } finally {
            // 6. 释放资源（防止内存泄漏）
            if (outDs != null) outDs.delete();      // 释放输出文件句柄
            if (warped != null) warped.delete();    // 释放 VRT
            if (srcDs != null) srcDs.delete();      // 释放源文件句柄 ← 这是锁住的原因！
        }
    }


    public static int getFileSize(String filePath) {
        int size = 0;
        List<String> requiredFiles = new ArrayList<>();
        String name = filePath.substring(0, filePath.lastIndexOf('.'));
        String parentDir = new File(filePath).getParent();

        requiredFiles.add(name + ".shp");
        requiredFiles.add(name + ".prj");
        requiredFiles.add(name + ".dbf");
        requiredFiles.add(name + ".shx");

        for (String file : requiredFiles) {
            File f = new File(file);
            if (f.exists()) {
                double fileSize = (double) f.length() / 1024;
                if (fileSize < 1) {
                    fileSize = 1;
                }
                size += (int) fileSize;
            } else {
                return 0;
            }
        }
        return size;
    }


    /**
     * 空间坐标转换成地理坐标（Java 转换版本）
     *
     * @param fiepaths 文件路径列表
     * @param livs     验证结果列表
     * @return 处理后的文件路径列表
     */
    private List<String> automatic2(List<String> fiepaths, List<LayerImportValidDto> livs) {

        String uploadFilePath = Paths.get(System.getProperty("user.dir"), appSettingConfig.getUploadFilePath()).toString();
        Path dateDir = Paths.get(uploadFilePath);

        List<String> ret = new ArrayList<>();
        String publishAddress = System.getProperty("user.dir");

        for (String fi : fiepaths) {
            LayerImportValidDto liv = new LayerImportValidDto();
            Path filePath = dateDir.resolve(Paths.get(fi).getFileName());
            String f = filePath.toString();

            if (Files.exists(filePath)) {
                String wktReference = "";
                String layerRefence = "";
                AtomicReference<String> layerRefenceRef = new AtomicReference<>(layerRefence); // 替代 C# 的 ref 参数

                if (isCoordTransform(filePath.toString(), layerRefenceRef)) {

                    String tempRef = layerRefenceRef.get().replace("GCS_", "").replace("_", " ");
                    layerRefenceRef.set(tempRef);
                    String temp = Gisfile.coordTransform2(f, layerRefence);
                    if (Func.isEmpty(temp)) {
                        liv.setStatus(false);
                        liv.setMessage(fiepaths + "坐标转换失败！");
                        livs.add(liv);
                    } else {
                        List<Path> df = new ArrayList<>();
                        Path tempPath = Paths.get(temp);
                        String name = FileServiceHelper.getFileNameWithoutExtension(tempPath.toString());
                        Path parentDir = tempPath.getParent();

                        df.add(parentDir.resolve(name + ".shp"));
                        df.add(parentDir.resolve(name + ".prj"));
                        df.add(parentDir.resolve(name + ".dbf"));
                        df.add(parentDir.resolve(name + ".shx"));

                        for (Path t : df) {
                            if (Files.exists(t)) {
                                Path target = dateDir.resolve(t.getFileName());
                                try {
                                    Files.copy(t, target, StandardCopyOption.REPLACE_EXISTING);
                                } catch (IOException e) {
                                    log.error("复制文件失败: {}");
                                }
                            }
                        }

                        // 删除临时目录（使用 Apache Commons IO）
                        try {
                            if (Files.isDirectory(parentDir)) {
                                FileUtils.deleteDirectory(parentDir.toFile());
                            }
                        } catch (IOException e) {
                            log.error("删除临时目录失败: {}");
                        }

                        Path ttf = dateDir.resolve(Paths.get(temp).getFileName());
                        ret.add(ttf.toString().replace(publishAddress, ""));
                    }
                } else {
                    ret.add(fi);
                }
            } else {
                liv.setStatus(false);
                liv.setMessage(fiepaths + "坐标转换失败！");
                livs.add(liv);
            }
        }
        return ret;
    }

    /**
     * 判断是否需要坐标转换
     *
     * @param path         SHP 文件路径
     * @param layerRefence 用于存储提取的地理坐标系信息（类似 C# 的 ref 参数）
     * @return 是否需要转换（true: 需要，false: 不需要）
     */
    public boolean isCoordTransform(String path, AtomicReference<String> layerRefence) {
        // 使用 try-with-resources 自动关闭资源
        try {
            ShpReader shpReader = new ShpReader(path);
            String csSrc = shpReader.getSridWkt();
            if (csSrc.contains("PROJCS")) { // 检查是否为投影坐标系
                int startIndex = csSrc.indexOf("GEOGCS[\"") + 8; // 定位地理坐标系起始位置
                int endIndex = csSrc.indexOf(",DATUM") - 1; // 定位地理坐标系结束位置
                if (startIndex >= 0 && endIndex > startIndex) {
                    layerRefence.set(csSrc.substring(startIndex, endIndex)); // 提取地理坐标系名称
                    return true;
                }
            }
            return false;
        } catch (Exception ex) {
            // 异常时返回 false（与 C# 逻辑一致）
            return false;
        }
    }

    /**
     * 将List<Map>转换为JSON字符串
     *
     * @param tableName 表名
     * @param dataList  数据列表
     * @return JSON字符串
     */
    public static String listMapToJson(String tableName, List<Map<String, Object>> dataList) {
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{\"").append(tableName).append("\":[");

        if (dataList != null && !dataList.isEmpty()) {
            for (Map<String, Object> row : dataList) {
                jsonBuilder.append("{");
                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    jsonBuilder.append("\"").append(entry.getKey()).append("\":");

                    // 处理值类型
                    Object value = entry.getValue();
                    if (value instanceof String) {
                        String strValue = ((String) value).replace("\"", "'");
                        jsonBuilder.append("\"").append(strValue).append("\"");
                    } else {
                        jsonBuilder.append(JsonUtil.toJson(value));
                    }
                    jsonBuilder.append(",");
                }
                if (!row.isEmpty()) {
                    jsonBuilder.deleteCharAt(jsonBuilder.length() - 1);
                }
                jsonBuilder.append("},");
            }
            jsonBuilder.deleteCharAt(jsonBuilder.length() - 1);
        }

        jsonBuilder.append("]}");
        return jsonBuilder.toString();
    }


    public String getDefaultStyleName(String id) {
//        DataStyleEntity style = dataStyleService.getById(id);
        DataStyleEntity style = dataStyleMapper.selectById(id);
        if (style != null) {
            return style.getStyleName();
        }
        return "";
    }

    public String getMultiTagNameByMapID(String mapLayerID) {
        List<TagRelationEntity> query = tagRelationService.list(new QueryWrapper<TagRelationEntity>().eq("mapid", mapLayerID));
        if (query != null) {
            List<String> tagNames = query.stream().map(item -> {
                DataTagEntity tag = dataTagService.getById(item.getDataTagID());
                return tag != null ? tag.getTagName() : null;
            }).filter(Objects::nonNull).collect(Collectors.toList());

            return String.join(",", tagNames);
        }
        return null;
    }


    private List<LayerContentEntity> getDataByUserCodeAsync(String userCode, String layerName, String
            layerType, String layerTag, String dataType, String uploadStatus) {
        try {
            userCode = UserContextUtil.resolveUserCode(userCode);

            List<LayerContentEntity> query = null;

            if (ConstHelper.CONST_SYSTEMNAME.equalsIgnoreCase(userCode)) {
                // 超级用户
                query = list();
            } else {
                List<SystemUserDto> userQuery = systemUserService.getUserDataByUserCodeAsync(userCode);
                if (userQuery != null && !userQuery.isEmpty()) {
                    List<String> userids = userQuery.stream()
                            .map(SystemUserEntity::getUserCode)
                            .collect(Collectors.toList());
                    if (!userids.isEmpty()) {
                        query = baseMapper.selectList(new LambdaQueryWrapper<LayerContentEntity>().in(LayerContentEntity::getCreateBy, userids));
                    }
                }
                if (query == null) {
                    // sdms_user 无该用户记录（如未初始化）时兜底：按传入用户过滤本人数据
                    query = baseMapper.selectList(new LambdaQueryWrapper<LayerContentEntity>()
                            .eq(LayerContentEntity::getCreateBy, userCode));
                }
            }

            if (query != null && !query.isEmpty()) {
                if (Func.isNotEmpty(uploadStatus)) {
                    query = query.stream().filter(s -> Func.isNotEmpty(s.getUploadStatus()) && s.getUploadStatus().equals(uploadStatus)).collect(Collectors.toList());
                }
                if (Func.isNotEmpty(layerName)) {
                    query = query.stream().filter(s -> Func.isNotEmpty(s.getLayerName()) && s.getLayerName().contains(layerName)).collect(Collectors.toList());
                }
                if (Func.isNotEmpty(layerType)) {
                    query = query.stream().filter(s -> Func.isNotEmpty(s.getLayerType()) && layerType.contains(s.getLayerType())).collect(Collectors.toList());
                }
                if (Func.isNotEmpty(layerTag)) {
                    query = query.stream().filter(s -> Func.isNotEmpty(s.getId()) && layerTag.contains(s.getId())).collect(Collectors.toList());
                }
                if (Func.isNotEmpty(dataType)) {
                    query = query.stream().filter(s -> Func.isNotEmpty(s.getDataType()) && dataType.contains(s.getDataType())).collect(Collectors.toList());
                }
            }
            return query;
        } catch (Exception ex) {
            return null;
        }
    }


    /**
     * 获取标签对应的所有地图或图层ID字符串
     *
     * @param typeID 类型ID
     * @return 所有子类型ID字符串
     */
    public String getMultiChildTypeByType(String typeID) {
        try {
            String multiTypeId = typeID;
            List<DataTypeEntity> collect = dataTypeService.list();
            List<DataTypeEntity> query = collect.stream()
                    .filter(q -> Func.isNotEmpty(q.getParentID()) && q.getParentID().equals(typeID))
                    .collect(Collectors.toList());

            if (!query.isEmpty()) {
                for (DataTypeEntity item : query) {
                    String result = getMultiChildTypeByType(item.getId());
                    if (result != null && !result.trim().isEmpty()) {
                        multiTypeId += "," + result;
                    } else {
                        return typeID;
                    }
                }
            }
            return multiTypeId;
        } catch (Exception ex) {
            return typeID;
        }
    }


    /**
     * 获取标签对应的所有地图或图层ID字符串
     *
     * @param tagID 标签ID
     * @return 图层ID字符串
     */
    public String getMultiLayerIDByTag(String tagID) {
        try {
            StringBuilder layerID = new StringBuilder();
            List<TagRelationEntity> query = tagRelationService.list().stream()
                    .filter(q -> q.getDataTagID().equals(tagID))
                    .collect(Collectors.toList());

            for (TagRelationEntity item : query) {
                layerID.append(item.getMapID()).append(",");
            }

            if (layerID.length() > 0) {
                layerID.deleteCharAt(layerID.length() - 1);
            }

            return layerID.toString();
        } catch (Exception ex) {
            return "";
        }
    }


    public PagedResultOutput<String> getAttrtableByLayerId(String id, int pageSize, int pageIndex) {

        try {
            LayerContentEntity layer = baseMapper.selectById(id);
            if (layer != null) {
                String sql = String.format("SELECT *, ST_AsText(geom) AS geomnew FROM %s WHERE gid IN (SELECT MIN(gid) FROM %s GROUP BY \"Name\") LIMIT %d OFFSET %d", layer.getLayerAttrTable(), layer.getLayerAttrTable(), pageSize, (pageIndex - 1) * pageSize);

                String countSql = String.format("SELECT COUNT(*) as total FROM %s WHERE gid IN (SELECT MIN(gid) FROM %s GROUP BY \"Name\")", layer.getLayerAttrTable(), layer.getLayerAttrTable());

                List<String> result = new ArrayList<>();
                List<Map<String, Object>> rs = dynamicTableMapper.dynamicExecute(sql);
                List<Map<String, Object>> countMap = dynamicTableMapper.dynamicExecute(countSql);

                result.add(JsonUtil.toJson(rs));
                int totalCount = Integer.parseInt(countMap.get(0).get("total").toString());

                return new PagedResultOutput<>(totalCount, result);

            } else {
                return null;
            }
        } catch (Exception ex) {
            log.error("getAttrtableByLayerId异常", ex);
            return null;
        }
    }

    public PagedResultOutput<String> getInfoBylayerIdsAndRectPage(List<String> layerIds, double minLon, double minLat, double maxLon, double maxLat, int pageSize, int pageIndex) {
        try {
            LambdaQueryWrapper<LayerContentEntity> queryWrapper = new LambdaQueryWrapper<LayerContentEntity>()
                    .in(LayerContentEntity::getId, layerIds).ne(LayerContentEntity::getDataType, DictionaryIds.GEOM_RASTER);
            List<LayerContentEntity> layers = baseMapper.selectList(queryWrapper);

            List<String> result = new ArrayList<>();
            StringBuilder cols = new StringBuilder();
            StringBuilder tj = new StringBuilder();
            int totalCount = 0;
            for (LayerContentEntity item : layers) {
                String tableName = item.getLayerAttrTable();
                List<String> columnFromTable = dynamicTableMapper.getColumnFromTable(tableName);
                if (columnFromTable != null && !columnFromTable.isEmpty()) {
                    for (String col : columnFromTable) {
                        if (!"GEOM".equalsIgnoreCase(col)) {
                            cols.append("\"").append(col).append("\" as \"").append(col).append("\",");
                        }
                    }
                }
                String input = cols.toString();
                if (input.endsWith(",")) {
                    input = input.substring(0, input.length() - 1);
                }
                if (input.isEmpty()) {
                    continue;
                }
                String sql = String.format("select %s ,ST_Asewkt(geom) as geomnew from %s where ST_Intersects(ST_MakeEnvelope('%f','%f','%f','%f'),geom) limit %d offset %d", input, item.getLayerAttrTable(), minLon, minLat, maxLon, maxLat, pageSize, (pageIndex - 1) * pageSize);

                String countSql = String.format("select count(*) as total from %s where ST_Intersects(ST_MakeEnvelope('%f','%f','%f','%f'),geom)", item.getLayerAttrTable(), minLon, minLat, maxLon, maxLat);

                List<Map<String, Object>> retList = dynamicTableMapper.dynamicExecute(sql);
                List<Map<String, Object>> retCount = dynamicTableMapper.dynamicExecute(countSql);
                result.add(JsonUtil.toJson(retList));
                totalCount += Integer.parseInt(retCount.get(0).get("total").toString());
                tj.append("|").append(item.getLayerName()).append(": ").append(retCount.get(0).get("total")).append("  ");
            }
            if (tj.toString().endsWith(",")) {
                String tjStr = tj.substring(0, tj.length() - 1);
                result.add(0, tjStr);
            }

            return new PagedResultOutput<>(totalCount, result);
        } catch (Exception ex) {
            // Log the exception appropriately
            log.error("getInfoByLayerIdsAndRectPage异常", ex);
            return null;
        }
    }

    public String getInfoBylayerIdsAndRectDownLoad(List<String> layerIds, double minLon, double minLat, double maxLon, double maxLat) {
        try {
            // 获取配置项
            String publishAddress = appSettingConfig.getBigDataIp();

            // 获取符合条件的图层列表
            QueryWrapper<LayerContentEntity> queryWrapper = new QueryWrapper<>();
            queryWrapper.in("id", layerIds);
            List<LayerContentEntity> layers = CompletableFuture.supplyAsync(() -> baseMapper.selectList(queryWrapper)).join();

            List<String> filePaths = new ArrayList<>();
            for (LayerContentEntity item : layers) {
                // 检查表是否存在
                String isTableSql = "SELECT COUNT(*) FROM pg_class WHERE relname = '" + item.getLayerAttrTable() + "'";
                List<Map<String, Object>> maps = dynamicTableMapper.dynamicExecute(isTableSql);
                String countObj = maps.get(0).get("count").toString();
                if (countObj == null || Integer.parseInt(countObj) == 0) {
                    continue;
                }

                // 构建SQL查询语句
                String strSQL = String.format("SELECT *, ST_AsText(geom) as geomnew FROM %s WHERE ST_Contains(ST_MakeEnvelope(%f,%f,%f,%f), geom)", item.getLayerAttrTable(), minLon, minLat, maxLon, maxLat);
                List<Map<String, Object>> dt = dynamicTableMapper.dynamicExecute(strSQL);


                if (dt != null && !dt.isEmpty()) {
                    // 获取输出SHP文件路径
                    String shpFilePath = getOutputShpFilePath(dt, item, "geomnew");

                    // 转换远程路径到本地路径
                    String localPath = shpFilePath.replace(publishAddress, Paths.get("").toAbsolutePath().toString()).replace("/", File.separator);

                    filePaths.add(localPath);
                }

            }

            // 如果有文件需要压缩，则进行压缩
            if (!filePaths.isEmpty()) {
                return Path.of(publishAddress, FileServiceHelper.zipFileList(filePaths)).toString();
            } else {
                return null;
            }
        } catch (Exception ex) {
            // 记录错误日志
            log.error("getInfoByLayerIdsAndRectDownload异常", ex); // 或者使用日志框架记录错误
            return null;
        }
    }

    /**
     * 根据图层id、圈选查询属性表信息
     *
     * @param layerIds
     * @param lon
     * @param lat
     * @param distance
     * @return
     */
    public String getInfoByLayerIdsAndPtToleranceDownload(List<String> layerIds, double lon, double lat, float distance) {
        try {
            String publishAddress = appSettingConfig.getDataResourceIp();
            List<LayerContentEntity> layers = baseMapper.selectList(new LambdaQueryWrapper<LayerContentEntity>().in(LayerContentEntity::getId, layerIds));

            List<String> filePaths = new ArrayList<>();

            for (LayerContentEntity item : layers) {
                String sql = String.format("SELECT *,ST_AsText(geom) AS geomnew FROM %s WHERE ST_DWithin(geom,ST_GeomFromText('POINT(%f %f)'),%f)=true", item.getLayerAttrTable(), lon, lat, distance / (2 * Math.PI * 6371004) * 360);

                List<Map<String, Object>> dt = dynamicTableMapper.dynamicExecute(sql);
                if (dt != null && !dt.isEmpty()) {
                    String shpFilePath = getOutputShpFilePath(dt, item, "geomnew");
                    String host = publishAddress;
                    String local = shpFilePath.replace(host, System.getProperty("user.dir")).replace("/", "\\");
                    filePaths.add(local);
                }
            }

            if (!filePaths.isEmpty()) {
                return Path.of(publishAddress, FileServiceHelper.zipFileList(filePaths)).toString();
            } else {
                return null;
            }
        } catch (Exception ex) {
            log.error("getInfoByLayerIdsAndPtToleranceDownload错误", ex);
            return null;
        }
    }

    public PagedResultOutput<String> getInfoBylayerIdsAndPolygonPage(String layerIds, List<Coordinate> coordinates, int pageSize, int pageIndex) {
        try {
            // 查询图层数据
            List<LayerContentEntity> layers = baseMapper.selectList(new LambdaQueryWrapper<LayerContentEntity>().in(LayerContentEntity::getId, layerIds));

            List<String> result = new ArrayList<>();
            StringBuilder tj = new StringBuilder();
            String sql;

            // 构建多边形坐标字符串
            StringJoiner polygon = new StringJoiner(",");
            for (Coordinate coord : coordinates) {
                if (coord.getLon() == 0 || coord.getLat() == 0) {
                    continue;
                }
                polygon.add(coord.getLon() + " " + coord.getLat());
            }

            // 自动闭合：如果首尾不等，把第一个点加到末尾
            if (!coordinates.get(0).equals(coordinates.get(coordinates.size() - 1))) {
                coordinates.add(coordinates.get(0));
            }

            for (Coordinate coord : coordinates) {
                polygon.add(coord.getLon() + " " + coord.getLat());
            }


            String polygonStr = polygon.toString();

            List<Map<String, Object>> dts = new ArrayList<>();
            int totalCount = 0;
            if (!polygonStr.isEmpty()) {
                for (LayerContentEntity item : layers) {
                    // 执行空间查询
                    sql = String.format("SELECT *,ST_AsText(geom) AS geomnew FROM %s " + "WHERE ST_Intersects(ST_PolygonFromText('POLYGON((%s))'),geom) " + "LIMIT %d OFFSET %d", item.getLayerAttrTable(), polygonStr, pageSize, (pageIndex - 1) * pageSize);

                    List<Map<String, Object>> dt = dynamicTableMapper.dynamicExecute(sql);

                    // 获取总数
                    String countSql = String.format("SELECT COUNT(*) as count FROM %s WHERE ST_Intersects(ST_PolygonFromText('POLYGON((%s))'),geom)", item.getLayerAttrTable(), polygonStr);
                    List<Map<String, Object>> count = dynamicTableMapper.dynamicExecute(countSql);

                    int layerCount = Integer.parseInt(count.get(0).get("count").toString());
                    tj.append("|").append(item.getLayerName()).append(": ").append(layerCount).append("  ");
                    totalCount += layerCount;
                    dts.addAll(dt);

                }
            }

            result.add(tj.toString());
            result.add(JsonUtil.toJson(dts));
            return new PagedResultOutput<>(totalCount, result);
        } catch (Exception ex) {
            log.error("多边形空间查询失败", ex);
            return null;
        }
    }

    /**
     * 根据图层id、多边形空间查询属性表信息
     *
     * @param layerIds    图层id
     * @param coordinates 坐标
     * @return
     */
    public String getInfoBylayerIdsAndPolygonDownLoad(String layerIds, List<Coordinate> coordinates) {
        try {
            // 获取发布地址
            String publishAddress = appSettingConfig.getDataResourceIp();

            // 查询图层数据
            List<LayerContentEntity> layers = baseMapper.selectList(new LambdaQueryWrapper<LayerContentEntity>().in(LayerContentEntity::getId, layerIds));
            //List<LayerField> fieldEntities = iLayerFieldBaseService.list(new LambdaQueryWrapper<LayerField>().in(LayerField::getLayerId, layerIds));

            List<String> filePaths = new ArrayList<>();
            String sql;

            // 构建多边形坐标字符串
            StringJoiner polygon = new StringJoiner(",");
            for (Coordinate coord : coordinates) {
                polygon.add(coord.getLon() + " " + coord.getLat());
            }
            String polygonStr = polygon.toString();

            if (!polygonStr.isEmpty()) {
                for (LayerContentEntity layer : layers) {
                    // 执行空间查询
                    sql = String.format("SELECT *,ST_AsText(geom) AS geomnew FROM %s " + "WHERE ST_Contains(ST_PolygonFromText('POLYGON((%s))'),geom)", layer.getLayerAttrTable(), polygonStr);

                    List<Map<String, Object>> dt = dynamicTableMapper.dynamicExecute(sql);
                    if (dt != null && !dt.isEmpty()) {
                        // 生成SHP文件
                        String shpFilePath = getOutputShpFilePath(dt, layer, "geomnew");

                        // 转换本地路径
                        String localPath = shpFilePath.replace(publishAddress, System.getProperty("user.dir")).replace("/", File.separator);

                        filePaths.add(localPath);
                    }
                }
            }

            // 压缩文件
            if (!filePaths.isEmpty()) {
                return Path.of(publishAddress, FileServiceHelper.zipFileList(filePaths)).toString();
            }

            return null;
        } catch (Exception ex) {
            log.error("多边形空间查询下载失败", ex);
            return null;
        }
    }


    public PagedResultOutput<String> getInfoByLayerIdsAndPtTolerancePage(List<String> layerIds, double lon, double lat, float distance, int pageSize, int pageIndex) {
        List<Map<String, Object>> result = new ArrayList<>();

        List<LayerContentEntity> layers = baseMapper.selectList(new LambdaQueryWrapper<LayerContentEntity>().in(LayerContentEntity::getId, layerIds).ne(LayerContentEntity::getDataType, DictionaryIds.GEOM_RASTER));


        StringBuilder tj = new StringBuilder();
        int totalCount = 0;
        try {
            for (LayerContentEntity item : layers) {
                String query = String.format("SELECT *, ST_AsText(geom) as geomnew FROM %s WHERE ST_Intersects(geom,st_buffer('POINT(%f %f)',%f))=true limit %d offset %d", item.getLayerAttrTable(), lon, lat, (distance / (2 * Math.PI * 6371004) * 360) * 10, pageSize, (pageIndex - 1) * pageSize); // 假设getLayerAttrTable根据layerId返回表名

                List<Map<String, Object>> dt = dynamicTableMapper.dynamicExecute(query);
                String countQuery = String.format("select count(*) as count from %s where ST_Intersects(geom,st_buffer('POINT(%f %f)',%f))=true", item.getLayerAttrTable(), lon, lat, (distance / (2 * Math.PI * 6371004) * 360) * 10);
                List<Map<String, Object>> count = dynamicTableMapper.dynamicExecute(countQuery);
                Object o = count.get(0).get("count");
                tj.append("|").append(item.getLayerName()).append(": ").append(o).append("  ");
                totalCount += Integer.parseInt(o.toString());

                result.addAll(dt);

            }
        } catch (Exception e) {
            log.error("getInfoByLayerIdsAndPtTolerancePage:异常", e);
            return null;
        }

        List<String> re = new ArrayList<>();
        re.add(tj.toString());
        re.add(JsonUtil.toJson(result));
        return new PagedResultOutput<>(totalCount, re);
    }

    public String getOutputShpFilePath(List<Map<String, Object>> dt, LayerContentEntity layer, String geomName) {
        String shpFilePath = "";
        if (dt == null || dt.isEmpty()) {
            return shpFilePath;
        }

        // 坐标参考
        String layerRefence = layer.getLayerRefence();
        String wktReference = null;
        if (layerRefence != null) {
            // 尝试 1: 原始名称
            wktReference = findReferenceFile(appSettingConfig.getCoordPath(), null, layerRefence);
            if (wktReference == null) {
                // 尝试 2: 替换 GCS_ 和 _ 为空格 (Windows常见)
                String layerRefenceSpace = layerRefence.replace("GCS_", "").replace("_", " ");
                wktReference = findReferenceFile(appSettingConfig.getCoordPath(), null, layerRefenceSpace);
            }
            if (wktReference == null) {
                // 尝试 3: 仅去掉 GCS_ (保留下划线)
                String layerRefenceUnderscore = layerRefence.replace("GCS_", "");
                wktReference = findReferenceFile(appSettingConfig.getCoordPath(), null, layerRefenceUnderscore);
            }
        }
        log.info("获取wktReference结果：" + wktReference);
        // 图层属性查询

        List<AttributeModel> result = layerFieldMapper.getLayerFieldById(layer.getId());

        // 图层属性赋值
        List<String> lstGeom = new ArrayList<>();
        List<AttributeObj> lstAttrObj = new ArrayList<>();
        int fid = 0;
        for (Map<String, Object> dr : dt) {

            if (dr.containsKey(geomName)) {
                lstGeom.add(dr.get(geomName).toString());
            }
            AttributeObj attObj = new AttributeObj();
            Map<String, String> dicAttValue = new HashMap<>();
            int index = 0;
            for (AttributeModel attributeM : result) {
                index++;
                String columnName = attributeM.getAttributeName().toLowerCase();
                if (Func.isNotEmpty(dr.get(columnName))) {
                    String rowValue = dr.get(columnName).toString().trim();
                    attributeM.setAttributeApproxOK(index);
                    attObj.setOId(fid);
                    attributeM.setAttributeType(FieldTypeEnum.valueOf(Utility.dataTypeToGdalType(attributeM.getAttributeTypeName())));
                    dicAttValue.put(columnName, rowValue);
                    fid++;
                }
            }
            attObj.setAttributeValue(dicAttValue);
            lstAttrObj.add(attObj);
        }

        // 文件生成
        Path outFilePath = Paths.get(System.getProperty("user.dir"), appSettingConfig.getDownloadFile(), "shp", "LayerShpFileExport");
        String publishAddress = appSettingConfig.getBigDataIp();
        if (!Files.exists(outFilePath)) {
            try {
                Files.createDirectories(outFilePath);
            } catch (IOException e) {
                log.error("文件创建失败", e);
            }
        }

        String layerTableName = layer.getLayerName();
        DictDataCodeEntity dictDataCodeEntity = dictDataCodeService.getById(layer.getDataType());
        String geoType = dictDataCodeEntity.getCodeValue().toUpperCase();
        geoType = geoType.isEmpty() ? "" : geoType;

        Path layerFilePath = outFilePath.resolve(layerTableName);
        FileServiceHelper.createDir(layerFilePath);
        Path outFileFullname = layerFilePath.resolve(layerTableName + ".shp");

        try {
            Files.deleteIfExists(outFileFullname);
            Files.deleteIfExists(outFileFullname.resolveSibling(layerTableName + ".prj"));
            Files.deleteIfExists(outFileFullname.resolveSibling(layerTableName + ".dbf"));
            Files.deleteIfExists(outFileFullname.resolveSibling(layerTableName + ".shx"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ShpFileWriter shpWriter = new ShpFileWriter(outFileFullname.toString());
        boolean success = shpWriter.doExport(result, Utility.getGeoTypeByString(geoType), lstGeom, lstAttrObj, wktReference);

        if (success) {
            // 如果存在原始坐标系，转换回原始坐标系
            if (Func.isNotEmpty(layer.getOriginLayerRefence())) {
                try {
                    String tempPrefix = "temp_";
                    String baseName = layerTableName;
                    Path parentDir = layerFilePath;

                    // 重命名生成的文件为临时文件
                    File[] generatedFiles = parentDir.toFile().listFiles((dir, name) -> name.startsWith(baseName + "."));
                    if (generatedFiles != null) {
                        for (File f : generatedFiles) {
                            String ext = f.getName().substring(f.getName().lastIndexOf("."));
                            File tempFile = new File(parentDir.toFile(), tempPrefix + baseName + ext);
                            f.renameTo(tempFile);
                        }

                        String sourceShp = parentDir.resolve(tempPrefix + baseName + ".shp").toString();
                        String targetShp = outFileFullname.toString();

                        // 转换坐标系
                        VertorProjection.ChangeVertorSrs(sourceShp, targetShp, layer.getOriginLayerRefence(), "GBK");

                        try {
                            // 删除临时文件
                            File[] tempFiles = parentDir.toFile().listFiles((dir, name) -> name.startsWith(tempPrefix + baseName + "."));
                            if (tempFiles != null) {
                                for (File f : tempFiles) {
                                    f.delete();
                                }
                            }
                        } catch (Exception e){
                            log.error("导出Shapefile时恢复原始坐标系失败: " + layer.getOriginLayerRefence());
                            // 恢复原始文件
                            File[] tempFiles = parentDir.toFile().listFiles((dir, name) -> name.startsWith(tempPrefix + baseName + "."));
                            if (tempFiles != null) {
                                for (File f : tempFiles) {
                                    String ext = f.getName().substring(f.getName().lastIndexOf("."));
                                    File originalFile = new File(parentDir.toFile(), baseName + ext);
                                    f.renameTo(originalFile);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("导出Shapefile坐标转换异常", e);
                }
            }

            boolean zipFilePath = FileServiceHelper.zipDir(layerFilePath.toString(), layerFilePath.toString(), 9);
            if (zipFilePath) {
                FileServiceHelper.deleteDirectory(layerFilePath);
                shpFilePath = layerFilePath + ".zip";
            }
        }

        return shpFilePath;
    }

    @Override
    public PagedResultOutput<String> getLayerAttrTableByWhere(String layerId, String where, int pageSize, int pageIndex) {
        try {
            LayerContentEntity layer = baseMapper.selectById(layerId);
            if (layer == null) {
                // 如果没有找到，尝试从IDataInformationRepository查找
                DataInformationEntity byId = dataInformationBaseService.getById(layerId);
                layer = baseMapper.selectById(byId.getGcjz());
            }

            if (layer != null && where != null && !where.trim().isEmpty()) {
                where = "WHERE 1=1 AND " + where;
                String sqlCount = String.format("SELECT COUNT(*) as total FROM %s %s", layer.getLayerAttrTable(), where);
                String sqlData = String.format("SELECT *, ST_AsText(geom) AS geomNew FROM %s %s LIMIT %d OFFSET %d", layer.getLayerAttrTable(), where, pageSize, (pageIndex - 1) * pageSize);

                List<Map<String, Object>> vds = dynamicTableMapper.dynamicExecute(sqlCount);
                List<Map<String, Object>> dt = dynamicTableMapper.dynamicExecute(sqlData);
                List<String> re = new ArrayList<>();
                re.add(JsonUtil.toJson(dt));

                return new PagedResultOutput<>(Integer.parseInt(vds.get(0).get("total").toString()), re);
            }
        } catch (Exception e) {
            log.error("getLayerAttrTableByWhere异常", e);
        }
        return null;
    }

    /**
     * 查找并读取参考文件。
     * 有风险
     *
     * @param path               要搜索的目录路径
     * @param wktReference       用于存储找到的WKT参考字符串的对象
     * @param spatialRefenceFile 配置中的文件名（不包括扩展名）
     */
    public String findReferenceFile(String path, String wktReference, String spatialRefenceFile) {
        // 优先使用传入的 wktReference
        if (wktReference != null) {
            return wktReference;
        }

        // 检查路径是否有效目录
        File folder = new File(path);
        if (!folder.exists() || !folder.isDirectory()) {
            return null;
        }

        String fileName = spatialRefenceFile + ".prj";

        // 1. 先在当前目录查找 .prj 文件
        File[] files = folder.listFiles((dir, name) -> name.equalsIgnoreCase(fileName));
        if (files != null) {
            for (File file : files) {
                try {
                    String content = readFile(file.getAbsolutePath());
                    if (content != null && content.toUpperCase().contains("GEOGCS")) {
                        return content; // 找到有效内容，立即返回
                    }
                } catch (Exception e) {
                    // 可选：记录日志，而不是直接抛异常（避免一个文件读错导致整个搜索失败）
                    // log.warn("Failed to read PRJ file: " + file, e);
                    throw new RuntimeException("Failed to read PRJ file: " + file, e);
                }
            }
        }

        // 2. 当前目录未找到，递归子目录
        File[] subDirs = folder.listFiles(File::isDirectory);
        if (subDirs != null) {
            for (File subDir : subDirs) {
                String result = findReferenceFile(subDir.getAbsolutePath(), null, spatialRefenceFile);
                if (result != null) {
                    return result; // 找到就立即返回，不再继续
                }
            }
        }

        // 3. 所有地方都没找到
        return null;

    }

    /**
     * 读取文件的第一行内容。
     *
     * @param fileName 文件路径
     * @return 文件的第一行内容，如果发生异常或文件为空则返回空字符串
     */
    public String readFile(String fileName) {
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(fileName))) {
            return reader.readLine(); // 直接读取第一行
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 新增图层数据
     *
     * @param layerParameter
     * @return
     */
    public boolean insertAttrtable(LayerParameter layerParameter) {
        try {
            if (StringUtils.isBlank(layerParameter.getAttrtable())) {
                log.info("InsertAttrtable：Attrtable 是空");
            } else {
                log.info("InsertAttrtable：Attrtable 有值");
            }

            String id = layerParameter.getId();
            String attrtable = layerParameter.getAttrtable();

            List<String> insertSql = new ArrayList<>();
            boolean executeResult = false;

            // 查询图层和字段信息
            LayerContentEntity layer = baseMapper.selectById(id);
            List<LayerFieldEntity> layerFields = layerFieldService.list(new LambdaQueryWrapper<LayerFieldEntity>().eq(LayerFieldEntity::getLayerID, layer.getId()).orderByAsc((LayerFieldEntity::getAttributeSort)));

            // 转换JSON为DataTable
            List<String> temp = JsonUtil.listFromJson(attrtable, new TypeToken<>() {
            });

            // 检查必须字段
            if (temp.contains("geom")) {
                return false;
            }

            // 检查字段匹配
            if (layerFields.stream().noneMatch(f -> temp.contains(f.getAttributeName()))) {
                return false;
            }

            // 获取当前最大sid
            String maxSidSql = String.format("select Max(sid) as max from %s", layer.getLayerAttrTable());
            List<Map<String, Object>> maxSidObj = dynamicTableMapper.dynamicExecute(maxSidSql);
            int maxSid = (maxSidObj == null) ? 0 : Integer.parseInt(maxSidObj.get(0).get("max").toString());

            // 处理每一行数据
            for (int i = 0; i < temp.size(); i++) {

                JsonElement jsonElement = JsonParser.parseString(temp.get(i));
                JsonObject row = jsonElement.getAsJsonObject();
                String geom = row.get("geom").getAsString();
                if (StringUtils.isBlank(geom)) {
                    continue;
                }

                maxSid++;
                StringBuilder sqlBuilder = new StringBuilder();
                StringBuilder valuesBuilder = new StringBuilder();

                // 构建INSERT语句
                sqlBuilder.append("INSERT INTO ").append(layer.getLayerAttrTable()).append("(");

                for (LayerFieldEntity field : layerFields) {
                    if (temp.contains(field.getAttributeName())) {
                        sqlBuilder.append("\"").append(field.getAttributeName()).append("\",");
                        valuesBuilder.append(getAttributeValue(field, row.get(field.getAttributeName()).getAsString())).append(",");
                    }
                }

                // 添加几何字段和系统字段
                valuesBuilder.append(String.format("ST_GeomFromText('%s'),%d,'%s'", geom, maxSid, UUID.randomUUID().toString()));

                sqlBuilder.append("geom,sid,gid) VALUES(").append(valuesBuilder).append(");");
                String sql = sqlBuilder.toString();
                log.info("执行SQL：{}", sql);
                insertSql.add(sql);
                executeResult = dynamicTableMapper.dynamicInsertExecute(sql) >= 1;
            }

            // 如果是首次添加数据，更新GeoServer的BBox
            if (maxSid == 0) {

                String bboxStr = "-180,180,-90,90";
                executeResult = geoServer.modifyLayerBBox(layer.getLayerAttrTable(), layer.getLayerName(), bboxStr);
            }

            return executeResult;
        } catch (Exception ex) {
            log.error("新增图层数据失败", ex);
            return false;
        }
    }

    // 辅助方法：获取字段值
    private String getAttributeValue(LayerFieldEntity field, String value) {
        // 根据字段类型处理值的格式
        if (field.getAttributeType().equalsIgnoreCase("string")) {
            return "'" + value.replace("'", "''") + "'";
        }

        return value;
    }

    public boolean updateAttrtable(LayerParameter layerParameter) {
        try {
            String id = layerParameter.getId();
            String attrtable = layerParameter.getAttrtable();
            boolean executeResult = false;

            // 查询图层和字段信息
            LayerContentEntity layer = baseMapper.selectById(id);
            if (layer != null) {
                List<LayerFieldEntity> layerFields = layerFieldService.list(new LambdaQueryWrapper<LayerFieldEntity>().eq(LayerFieldEntity::getLayerID, layer.getId()).orderByAsc((LayerFieldEntity::getAttributeSort)));

                log.info("attrtable:{}", attrtable);
                // 转换JSON为DataTable
                List<Map<String, Object>> dataTable = jsonToListOfMaps(attrtable);
                List<String> columns = getColumnsByDataTable(dataTable);

                log.info("dataTable:{} columns:{}", dataTable, columns);
                // 检查字段匹配
                if (columns.isEmpty() || !columns.contains("gid")) {
                    log.error("字段不匹配");
                    return false;
                }

                // 获取SRID
                String srid = "4326";
                if (columns.contains("geom")) {
                    try {
                        String tableName = layer.getLayerAttrTable();
                        String schemaName = "public";
                        if (tableName.contains(".")) {
                            String[] parts = tableName.split("\\.");
                            schemaName = parts[0].replaceAll("\"", "");
                            tableName = parts[1].replaceAll("\"", "");
                        }
                        String querySridSql = String.format("SELECT Find_SRID('%s', '%s', 'geom') as srid", schemaName, tableName);
                        List<Map<String, Object>> sridData = postgresVectorHelper.getData(querySridSql);
                        if (sridData != null && !sridData.isEmpty()) {
                            Object sridObj = sridData.get(0).get("srid");
                            if (sridObj != null) {
                                srid = sridObj.toString();
                            }
                        }
                    } catch (Exception e) {
                        log.warn("获取SRID失败,使用默认值4326", e);
                    }
                }

                // 处理每一行数据
                for (Map<String, Object> row : dataTable) {
                    String sid = row.get("gid").toString();

                    if (StringUtils.isNotBlank(sid)) {
                        StringBuilder sqlBuilder = new StringBuilder();
                        sqlBuilder.append("UPDATE ").append(layer.getLayerAttrTable()).append(" SET ");

                        // 构建更新字段部分
                        for (LayerFieldEntity field : layerFields) {
                            String fieldName = field.getAttributeName().toLowerCase();
                            Object v = row.get(fieldName);
                            if (columns.contains(fieldName)) {
                                String value = getAttributeValue(field, v.toString());
                                if (v instanceof String) {
                                    sqlBuilder.append("\"").append(fieldName).append("\"= '").append(value).append("',");
                                } else {
                                    sqlBuilder.append("\"").append(fieldName).append("\"=").append(value).append(",");
                                }

                            }
                        }

                        // 处理几何字段
                        if (columns.contains("geom")) {
                            String geomValue = row.get("geomnew").toString();
                            // 替换 C# 风格的特殊浮点值为 0，避免 WKT 解析失败
                            geomValue = geomValue.replaceAll("-?1\\.#INF", "0");
                            geomValue = geomValue.replaceAll("-?1\\.#IND", "0");
                            geomValue = geomValue.replaceAll("-?1\\.#QNAN", "0");
                            sqlBuilder.append("\"geom\"=ST_GeomFromText('").append(geomValue).append("', ").append(srid).append(")");
                        } else {
                            // 移除最后一个逗号
                            sqlBuilder.setLength(sqlBuilder.length() - 1);
                        }

                        // 添加WHERE条件
                        sqlBuilder.append(" WHERE gid='").append(sid).append("'");

                        // 执行SQL
                        executeResult = dynamicTableMapper.dynamicUpdateExecute(sqlBuilder.toString()) > 0;
                    }
                }
                return executeResult;
            }
            return false;
        } catch (Exception ex) {
            log.error("更新属性数据失败", ex);
            return false;
        }
    }

    public boolean updateAttrtableList(List<LayerParameter> layerParameters) {
        try {
            if (layerParameters != null && !layerParameters.isEmpty()) {
                for (LayerParameter layerParameter : layerParameters) {
                    updateAttrtable(layerParameter);
                }
                return true;
            }
            return false;
        } catch (Exception ex) {
            log.error("批量更新图层数据失败", ex);
            return false;
        }
    }

    /**
     * 根据图层id查询图层属性字段
     *
     * @param layerId 图层id
     * @param attributeType 属性类型过滤（Num/Text/null）
     * @return 包含LayerFieldDto的异步列表
     */
    public List<LayerFieldEntity> getFieldByLayerId(String layerId, String attributeType) {

        // 获取图层信息
        LayerContentEntity layer = baseMapper.selectById(layerId);
        String tableName = layer != null ? layer.getLayerAttrTable() : null;

        // 从ILayerFieldRepository查询数据
        List<LayerFieldEntity> fieldList = layerFieldService.list(
                new LambdaQueryWrapper<LayerFieldEntity>().eq(LayerFieldEntity::getLayerID, layerId).orderByAsc(LayerFieldEntity::getAttributeSort)
        );

        if (fieldList.isEmpty()) {
            List<Map<String, Object>> datas = postgresVectorHelper.getData(String.format(
                    "select * from sdms_attributeparameter where datainfoid='%s' and tablename='Business'", layerId));

            if (!datas.isEmpty()) {
                for (Map<String, Object> row : datas) {
                    String attrType = row.get("attributetypes").toString();

                    String dataType = "afd042e0-67a8-11e7-8eb2-005056bb1c7e";
                    String typeName = "Text";
                    if (attrType.contains("numeric") || attrType.contains("Integer")) {
                        dataType = "9ffd11ea-67a8-11e7-8eb2-005056bb1c7e";
                        typeName = "Double";
                    }

                    LayerFieldEntity dto = new LayerFieldEntity();
                    dto.setAttributeDataType(dataType);
                    dto.setAttributeType(typeName);
                    dto.setAttributeName(row.get("attributename").toString());
                    dto.setChineseName(row.get("attributename").toString());
                    dto.setAttributePrecision(row.get("attributedecimal").toString());
                    dto.setAttributeLength(row.get("attributelength").toString());
                    dto.setId(row.get("id").toString());

                    fieldList.add(dto);
                }
            }
        } else {
            // 从IDicDataCodeRepository查询字典数据
            List<DictDataCodeEntity> dicList = dictDataCodeService.list(
                    new LambdaQueryWrapper<DictDataCodeEntity>().eq(
                            DictDataCodeEntity::getDataTypeID, "73160096-67a5-11e7-8eb2-005056bb1c7e"
                    ));

            for (LayerFieldEntity item : fieldList) {
                Optional<DictDataCodeEntity> dc = dicList.stream()
                        .filter(d -> d.getId().equals(item.getAttributeType()))
                        .findFirst();
                dc.ifPresent(d -> item.setAttributeType(d.getCodeValue()));
            }

        }

        // 根据实际数据值推断字段类型
        if (StringUtils.isNotBlank(tableName) && !fieldList.isEmpty()) {
            inferFieldTypesFromData(tableName, fieldList);
        }

        // 按 attributeType 过滤
        if (StringUtils.isNotBlank(attributeType)) {
            if ("Num".equals(attributeType)) {
                fieldList = fieldList.stream()
                        .filter(f -> {
                            String t = f.getAttributeType();
                            return "Long Integer".equals(t) || "Short Integer".equals(t)
                                    || "Float".equals(t) || "Double".equals(t);
                        })
                        .collect(Collectors.toList());
            } else {
                fieldList = fieldList.stream()
                        .filter(f -> attributeType.equals(f.getAttributeType()))
                        .collect(Collectors.toList());
            }
        }

        return fieldList;
    }

    /**
     * 根据实际数据值推断字段类型
     * 遍历每个字段的全部数据，如果所有非空值都能转成Double，则认为它是Num类型
     *
     * @param tableName 表名
     * @param fieldList 字段列表
     */
    private void inferFieldTypesFromData(String tableName, List<LayerFieldEntity> fieldList) {
        for (LayerFieldEntity field : fieldList) {
            String attrName = field.getAttributeName();
            if (attrName == null || attrName.isEmpty() || "geom".equalsIgnoreCase(attrName) || "guid".equalsIgnoreCase(attrName) || "sid".equalsIgnoreCase(attrName)) {
                continue;
            }
            // 转换成小写，因为属性表中字段名都是小写的
            attrName = attrName.toLowerCase();

            try {
                // 查询该列的所有非空值
                String sql = String.format("SELECT \"%s\" FROM public.%s WHERE \"%s\" IS NOT NULL LIMIT 1000",
                        attrName, tableName, attrName);
                List<Map<String, Object>> results = postgresVectorHelper.getData(sql);

                if (results.isEmpty()) {
                    // 全是null，视为Num类型
                    field.setAttributeType("Double");
                    continue;
                }

                boolean allNumeric = true;
                for (Map<String, Object> row : results) {
                    Object value = row.get(attrName);
                    if (value == null) {
                        continue;
                    }
                    String strValue = value.toString().trim();
                    if (strValue.isEmpty()) {
                        continue;
                    }
                    try {
                        Double.parseDouble(strValue);
                    } catch (NumberFormatException e) {
                        allNumeric = false;
                        break;
                    }
                }

                if (allNumeric) {
                    field.setAttributeType("Double");
                } else {
                    field.setAttributeType("Text");
                }
            } catch (Exception e) {
                log.warn("推断字段 {} 类型失败: {}", attrName, e.getMessage());
            }
        }
    }

    /**
     * 验证图层条件是否有效
     *
     * @param layerId 图层ID
     * @param where   查询条件
     * @return 条件是否有效（true：有效，false：无效）
     */
    public boolean validateCondition(String layerId, List<JSONObject> where) {
        LayerContentEntity layer = baseMapper.selectById(layerId); // 假设仓储同步查询

        String querySql = genSQL(where);

        // 若图层不存在，尝试通过数据信息查找关联图层
        if (layer == null) {
            DataInformationEntity dataInfo = dataInformationBaseService.getById(layerId);
            if (dataInfo != null) {
                layer = baseMapper.selectById(dataInfo.getGcjz());
            }
        }

        if (layer != null) {
            // 构造SQL（添加基础条件避免空where）
            String sql = String.format("select * from %s %s", layer.getLayerAttrTable(), querySql);

            try {
                log.info("sql = {}", sql);
                // 执行查询验证SQL有效性（不关心结果，仅验证是否可执行）
                dynamicTableMapper.dynamicExecute(sql);
                return true;
            } catch (Exception ex) {
                log.error("执行sql失败", ex);
                // 捕获SQL执行异常（如语法错误）
                return false;
            }
        }
        return false;
    }

    String genSQL(List<JSONObject> where) {
        StringBuilder querySql = new StringBuilder();
        int index = 0;
        for (JSONObject o : where) {
            if (index != 0) {
                String join = o.getString("join");
                querySql.append(" ").append(join).append(" ");
            } else {
                querySql.append(" WHERE ");
            }
            String field = o.getString("key");
            String symbol = o.getString("symbol");
            if (symbol != null) {
                symbol = symbol.replace("&gt;", ">").replace("&lt;", "<");
            }
            Object value = o.get("value");
            if ("like".equalsIgnoreCase(symbol)) {
                value = "%" + value + "%";
            }
            if (value instanceof String) {
                querySql.append("\"").append(field).append("\"").append(" ").append(symbol).append(" ").append("'").append(value).append("'").append(" ");
            } else {
                querySql.append("\"").append(field).append("\"").append(" ").append(symbol).append(" ").append(value).append(" ");
            }
            index++;
        }

        return querySql.toString();
    }

    public PageResultDTO<Map<String, Object>> pageAttrInfoByWKT(String polygonStr, LayerContentEntity layerContentEntity, int pageSize, int pageIndex) {

        try {
            // 查询图层数据
            String sql;
            int totalCount = 0;
            List<Map<String, Object>> result = new ArrayList<>();

            if (!polygonStr.isEmpty()) {
                // 执行空间查询 - 自动匹配SRID
                // 使用 ST_SetSRID(ST_PolygonFromText('%s'), (SELECT ST_SRID(geom) FROM %s LIMIT 1))
                // 这样可以确保输入的 WKT 几何体被赋予与表中 geom 字段相同的 SRID
                String geomSridSql = String.format("(SELECT ST_SRID(geom) FROM %s LIMIT 1)", layerContentEntity.getLayerAttrTable());
                
                sql = String.format("SELECT *,ST_AsText(geom) AS geomnew FROM %s " + 
                        "WHERE ST_Intersects(ST_SetSRID(ST_PolygonFromText('%s'), %s), geom) ORDER BY gid " +
                        "LIMIT %d OFFSET %d", 
                        layerContentEntity.getLayerAttrTable(), 
                        polygonStr, 
                        geomSridSql,
                        pageSize,
                        (pageIndex - 1) * pageSize);

                result = dynamicTableMapper.dynamicExecute(sql);
                result = formatNumericResult(result);

                // 获取总数 - 同样应用自动匹配SRID逻辑
                String countSql = String.format("SELECT COUNT(*) as count FROM %s " +
                        "WHERE ST_Intersects(ST_SetSRID(ST_PolygonFromText('%s'), %s), geom)", 
                        layerContentEntity.getLayerAttrTable(), 
                        polygonStr,
                        geomSridSql);
                        
                List<Map<String, Object>> count = dynamicTableMapper.dynamicExecute(countSql);

                if (count != null && !count.isEmpty() && count.get(0).get("count") != null) {
                    totalCount = Integer.parseInt(count.get(0).get("count").toString());
                }
            } else {

                // 执行空间查询
                sql = String.format("SELECT *,ST_AsText(geom) AS geomnew FROM %s ORDER BY gid LIMIT %d OFFSET %d", layerContentEntity.getLayerAttrTable(), pageSize, (pageIndex - 1) * pageSize);

                result = dynamicTableMapper.dynamicExecute(sql);
                result = formatNumericResult(result);

                // 获取总数
                String countSql = String.format("SELECT COUNT(*) as count FROM %s ", layerContentEntity.getLayerAttrTable());
                List<Map<String, Object>> count = dynamicTableMapper.dynamicExecute(countSql);

                totalCount = Integer.parseInt(count.get(0).get("count").toString());

            }

            PageResultDTO<Map<String, Object>> ret = new PageResultDTO<>();
            ret.setTotal(totalCount);
            ret.setRecords(result);
            return ret;
        } catch (Exception ex) {
            log.error("多边形空间查询失败", ex);
            return null;
        }
    }

    public boolean deleteAttrtable(String id, String gid) {
        try {
            boolean executeResult = false;
            // 查询图层信息
            LayerContentEntity layer = baseMapper.selectById(id);

            if (layer != null) {
                // 构建删除SQL
                String sql = "DELETE FROM " + layer.getLayerAttrTable() + " WHERE gid='" + gid + "'";
                executeResult = dynamicTableMapper.dynamicUpdateExecute(sql) > 0;
            }
            return executeResult;
        } catch (Exception ex) {
            log.error("删除图层数据失败", ex);
            return false;
        }
    }

    public static List<Map<String, Object>> jsonToListOfMaps(String json) {
        List<Map<String, Object>> resultList = new ArrayList<>();
        try {

            JsonArray jsonArray = JsonParser.parseString(json).getAsJsonArray();

            for (JsonElement element : jsonArray) {
                JsonObject jsonObject = element.getAsJsonObject();
                Map<String, Object> rowMap = new HashMap<>();

                for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                    String key = entry.getKey();
                    JsonElement value = entry.getValue();
                    rowMap.put(key, convertJsonElementToObject(value));
                }

                resultList.add(rowMap);
            }
        } catch (Exception e) {

        }
        return resultList;
    }

    private static Object convertJsonElementToObject(JsonElement value) {
        if (value.isJsonNull()) {
            return null;
        } else if (value.isJsonPrimitive()) {
            JsonPrimitive primitive = value.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            } else if (primitive.isNumber()) {
                String numberStr = primitive.getAsString();
                if (numberStr.contains(".")) {
                    return primitive.getAsDouble();
                } else {
                    return primitive.getAsLong();
                }
            } else if (primitive.isString()) {
                return primitive.getAsString();
            }
        } else if (value.isJsonArray()) {
            return value.getAsJsonArray();
        } else if (value.isJsonObject()) {
            return value.getAsJsonObject();
        }
        return null;
    }

    private List<String> getColumnsByDataTable(List<Map<String, Object>> dataTable) {
        List<String> columns = new ArrayList<>();
        if (dataTable != null && !dataTable.isEmpty()) {
            dataTable.forEach(row -> {
                columns.addAll(row.keySet());
            });
        }
        return columns;
    }

    public PageResultDTO<Map<String, Object>> layerAttrInfoByWhere(List<JSONObject> where, LayerContentEntity layer, int pageSize, int pageIndex) {
        try {

            String sqlCount = String.format("SELECT COUNT(*) as total FROM %s", layer.getLayerAttrTable());
            String sqlData = String.format("SELECT *, ST_AsText(geom) AS geomNew FROM %s ORDER BY gid LIMIT %d OFFSET %d", layer.getLayerAttrTable(), pageSize, (pageIndex - 1) * pageSize);
            if (where != null) {
                String sql = genSQL(where);

                sqlCount = String.format("SELECT COUNT(*) as total FROM %s %s", layer.getLayerAttrTable(), sql);
                sqlData = String.format("SELECT *, ST_AsText(geom) AS geomNew FROM %s %s ORDER BY gid LIMIT %d OFFSET %d", layer.getLayerAttrTable(), sql, pageSize, (pageIndex - 1) * pageSize);
            }
            List<Map<String, Object>> vds = dynamicTableMapper.dynamicExecute(sqlCount);

            List<Map<String, Object>> result = dynamicTableMapper.dynamicExecute(sqlData);
            result = formatNumericResult(result);

            PageResultDTO<Map<String, Object>> ret = new PageResultDTO<>();
            ret.setRecords(result);
            ret.setTotal(Integer.parseInt(vds.get(0).get("total").toString()));
            return ret;
        } catch (Exception e) {
            log.error("getLayerAttrTableByWhere异常", e);
        }
        return null;
    }

    @Override
    public PageResultDTO<Map<String, Object>> pageAttrInfoBySwitch(LayerFieldQuery query, int pageSize, int pageIndex) {

        LayerContentEntity item = baseMapper.selectById(query.getLayerId());

        if (item == null) {
            // 如果没有找到，尝试从IDataInformationRepository查找
            DataInformationEntity byId = dataInformationBaseService.getById(query.getLayerId());
            if (byId == null) {
                throw new RuntimeException("未查询到相关图层信息");
            }
            item = baseMapper.selectById(byId.getGcjz());
            if (item == null) {
                return null;
            }
        }

        PageResultDTO<Map<String, Object>> dto = routeAttrQuery(query, item, pageSize, pageIndex);
        return dto;
    }

    /**
     * 统一查询路由（pageAttrInfoBySwitch / downloadAttrInfoBySwitch 共用）：
     *  - WKT 以 POINT 开头 + distance（米）→ 圆形缓冲查询（圈选 / 缓冲查询）
     *  - 其它 WKT（POLYGON 等）→ ST_Intersects 相交查询（框选 / 多边选）
     *  - where 条件 → 属性条件查询
     *  - lon/lat + distance（度）→ 点容差查询
     *  - 以上均无 → 全量分页
     */
    private PageResultDTO<Map<String, Object>> routeAttrQuery(LayerFieldQuery query, LayerContentEntity item, int pageSize, int pageIndex) {
        String wkt = query.getWkt() == null ? null : query.getWkt().trim();
        if (Func.isNotBlank(wkt)) {
            if (wkt.toUpperCase().startsWith("POINT")) {
                double[] lonlat = parseWktPoint(wkt);
                if (lonlat != null) {
                    return pagePtToleranceMeters(item, lonlat[0], lonlat[1], query.getDistance(), pageSize, pageIndex);
                }
            }
            return pageAttrInfoByWKT(wkt, item, pageSize, pageIndex);
        }
        if (query.getWhere() != null && !query.getWhere().isEmpty()) {
            return layerAttrInfoByWhere(query.getWhere(), item, pageSize, pageIndex);
        }
        if (query.getLon() != null && query.getLat() != null) {
            return pagePtTolerance(item, query.getLon(), query.getLat(), query.getDistance(), pageSize, pageIndex);
        }
        return pageSelectAll(item.getLayerAttrTable(), pageSize, pageIndex);
    }

    /** 解析 POINT(lon lat) WKT → [lon, lat]；失败返回 null */
    private double[] parseWktPoint(String wkt) {
        try {
            int l = wkt.indexOf('(');
            int r = wkt.indexOf(')');
            if (l < 0 || r < 0 || r <= l) return null;
            String[] parts = wkt.substring(l + 1, r).trim().split("\\s+");
            if (parts.length < 2) return null;
            return new double[]{Double.parseDouble(parts[0]), Double.parseDouble(parts[1])};
        } catch (Exception e) {
            return null;
        }
    }

    /** 点 + 距离（米）圆形缓冲查询：ST_DWithin geography（与 pageAttrInfoByPoint 同口径） */
    private PageResultDTO<Map<String, Object>> pagePtToleranceMeters(LayerContentEntity layerContentEntity, Double lon, Double lat, Double distanceMeters, int pageSize, int pageIndex) {
        List<Map<String, Object>> result = new ArrayList<>();
        int totalCount = 0;
        try {
            if (distanceMeters == null || distanceMeters <= 0) {
                distanceMeters = 100d;
            }
            String where = String.format(
                    "ST_DWithin(ST_Force2D(geom)::geography, ST_SetSRID(ST_Point(%f, %f), 4326)::geography, %f)",
                    lon, lat, distanceMeters);
            String query = String.format(
                    "SELECT *, ST_AsText(geom) AS geomnew FROM %s WHERE %s ORDER BY gid LIMIT %d OFFSET %d",
                    layerContentEntity.getLayerAttrTable(), where, pageSize, (pageIndex - 1) * pageSize);
            String countQuery = String.format("SELECT count(*) as count FROM %s WHERE %s",
                    layerContentEntity.getLayerAttrTable(), where);
            result = dynamicTableMapper.dynamicExecute(query);
            result = formatNumericResult(result);
            List<Map<String, Object>> count = dynamicTableMapper.dynamicExecute(countQuery);
            if (count != null && !count.isEmpty() && count.get(0).get("count") != null) {
                totalCount = Integer.parseInt(count.get(0).get("count").toString());
            }
        } catch (Exception e) {
            log.error("pagePtToleranceMeters:异常", e);
            return null;
        }
        PageResultDTO<Map<String, Object>> ret = new PageResultDTO<>();
        ret.setTotal(totalCount);
        ret.setRecords(result);
        return ret;
    }

    private PageResultDTO<Map<String, Object>> pageSelectAll(String layerAttrTable, Integer pageSize, Integer pageIndex) {
        List<Map<String, Object>> result = new ArrayList<>();

        int totalCount = 0;
        try {

            String query = String.format("SELECT *, ST_AsText(geom) as geomnew FROM %s ORDER BY gid limit %d offset %d", layerAttrTable, pageSize, (pageIndex - 1) * pageSize); // 假设getLayerAttrTable根据layerId返回表名
            String countQuery = String.format("select count(*) as count from %s ", layerAttrTable);

            result = dynamicTableMapper.dynamicExecute(query);
            result = formatNumericResult(result);
            List<Map<String, Object>> count = dynamicTableMapper.dynamicExecute(countQuery);
            Object o = count.get(0).get("count");
            totalCount = Integer.parseInt(o.toString());

        } catch (Exception e) {
            log.error("pageSelectAll:异常", e);
            return null;
        }

        PageResultDTO<Map<String, Object>> ret = new PageResultDTO<>();
        ret.setTotal(totalCount);
        ret.setRecords(result);
        return ret;

    }

    public PageResultDTO<Map<String, Object>> pagePtTolerance(LayerContentEntity layerContentEntity, Double lon, Double lat, Double distance, int pageSize, int pageIndex) {
        List<Map<String, Object>> result = new ArrayList<>();

        int totalCount = 0;
        try {

            if (distance == null) {
                distance = 100d;
            }
            String query = String.format(
                    "SELECT *, ST_AsText(geom) AS geomnew " +
                            "FROM %s " +
                            "WHERE ST_DWithin(" +
                            "ST_Force2D(geom), " +
                            "ST_SetSRID(ST_Point(%f, %f), 4326), " + // 显式设置 SRID=4326
                            "%f" +                                   // 距离单位：度
                            ") " +
                            "ORDER BY gid LIMIT %d OFFSET %d",
                    layerContentEntity.getLayerAttrTable(),
                    lon, lat,
                    distance,
                    pageSize,
                    (pageIndex - 1) * pageSize
            );
            // String query = String.format("SELECT *, ST_AsText(geom) as geomnew FROM %s WHERE ST_Intersects(geom,st_buffer('POINT(%f %f)',%f))=true limit %d offset %d", layerContentEntity.getLayerAttrTable(), lon, lat, (distance / (2 * Math.PI * 6371004) * 360) * 10, pageSize, (pageIndex - 1) * pageSize); // 假设getLayerAttrTable根据layerId返回表名
           // String countQuery = String.format("select count(*) as count from %s where ST_Intersects(geom,st_buffer('POINT(%f %f)',%f))=true", layerContentEntity.getLayerAttrTable(), lon, lat, (distance / (2 * Math.PI * 6371004) * 360) * 10);
            String countQuery = String.format(
                    "SELECT count(*) as count " +
                            "FROM %s " +
                            "WHERE ST_DWithin(" +
                            "ST_Force2D(geom), " +
                            "ST_SetSRID(ST_Point(%f, %f), 4326), " + // 显式指定 SRID=4326
                            "%f" +                                   // 距离单位：度
                            ")",
                    layerContentEntity.getLayerAttrTable(),
                    lon, lat,
                    distance
            );
            result = dynamicTableMapper.dynamicExecute(query);
            result = formatNumericResult(result);
            List<Map<String, Object>> count = dynamicTableMapper.dynamicExecute(countQuery);
            Object o = count.get(0).get("count");
            totalCount = Integer.parseInt(o.toString());

        } catch (Exception e) {
            log.error("getInfoByLayerIdsAndPtTolerancePage:异常", e);
            return null;
        }

        PageResultDTO<Map<String, Object>> ret = new PageResultDTO<>();
        ret.setTotal(totalCount);
        ret.setRecords(result);
        return ret;
    }


    @Override
    public String downloadAttrInfoBySwitch(LayerFieldQuery query) {

        LayerContentEntity item = baseMapper.selectById(query.getLayerId());

        if (item == null) {
            // 如果没有找到，尝试从IDataInformationRepository查找
            DataInformationEntity byId = dataInformationBaseService.getById(query.getLayerId());
            item = baseMapper.selectById(byId.getGcjz());
            if (item == null) {
                return null;
            }
        }

        // 获取表中数据条数
        String countQuery = String.format("select count(*) as count from %s ", item.getLayerAttrTable());
        List<Map<String, Object>> count = dynamicTableMapper.dynamicExecute(countQuery);
        Object o = count.get(0).get("count");
        int totalCount = Integer.parseInt(o.toString());

        PageResultDTO<Map<String, Object>> dto = routeAttrQuery(query, item, totalCount, 1);

        List<Map<String, Object>> records = dto.getRecords();

        if (records != null && !records.isEmpty()) {
            // 获取输出SHP文件路径
            String shpFilePath = getOutputShpFilePath(records, item, "geomnew");
            log.info("矢量文件输出路径:{}", shpFilePath);
            return shpFilePath;
        }

        return "";
    }

    @Override
    public String outputShpFileData(String layerID) {
        String shpFilePath = "";
        try {
            // 假设_layerContentRepository是已注入的Repository
            LayerContentEntity layer = baseMapper.selectById(layerID);

            if (layer != null) {
                // 图层属性取值
                String tableName = "public." + layer.getLayerAttrTable();
                String strSQL = String.format("select %s,ST_Asewkt(geom) geom from %s ",
                        getLayerFields(layer.getId()), tableName);

                List<Map<String, Object>> list = dynamicTableMapper.dynamicExecute(strSQL);

                if (list != null && !list.isEmpty()) {
                    shpFilePath = getOutputShpFilePath(list, layer, "geom");
                }

            }
            return shpFilePath;
        } catch (Exception ex) {
            return shpFilePath;
        }
    }

    @Override
    public String outputTifFileData(String layerID) {
        String tiffFilePath = "";
        try {
            LayerContentEntity layer = baseMapper.selectById(layerID);
            if (layer == null) {
                return "";
            }

            LocalDateTime createDT = layer.getCreateDT();
            String date = createDT.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String upload = Paths.get(appSettingConfig.getUploadFilePath()).toString();

            // 查找tif文件
            Path sourceFile = null;
            String fileName = "";
            for (String item : layer.getUploadFileName().split("\\*")) {
                String[] parts = item.split("\\\\|/");
                String name = parts[parts.length - 1];
                if (name.toLowerCase().endsWith(".tif") || name.toLowerCase().endsWith(".tiff")) {
                    fileName = name;
                    Path tempPath = Paths.get(item);
                    if (Files.exists(tempPath)) {
                        sourceFile = tempPath;
                    } else {
                        sourceFile = Paths.get(upload, date, name);
                    }
                    if (Files.exists(sourceFile)) {
                        break;
                    }
                }
            }

            if (sourceFile == null || !Files.exists(sourceFile)) {
                log.error("未找到TIF文件");
                return "";
            }

            // 如果存在原始坐标系，转换回原始坐标系
            if (Func.isNotEmpty(layer.getOriginLayerRefence())) {
                try {
                    // 创建导出目录
                    Path exportDir = Paths.get(System.getProperty("user.dir"), appSettingConfig.getDownloadFile(), "tif", "LayerTifFileExport");
                    if (!Files.exists(exportDir)) {
                        Files.createDirectories(exportDir);
                    }
                    
                    String targetFileName = "converted_" + fileName;
                    Path targetPath = exportDir.resolve(targetFileName);
                    
                    // 转换坐标系
                    // ChangeRasterSrs(Output, Input, WKT)
                    RasterProjection.ChangeRasterSrs(sourceFile.toString(),targetPath.toString(), layer.getOriginLayerRefence());
                    
                    if (Files.exists(targetPath)) {
                        return targetPath.toString();
                    }
                } catch (Exception e) {
                    log.error("导出TIFF坐标转换异常", e);
                }
            }
            
            // 如果不需要转换或转换失败，返回原文件路径
            return sourceFile.toString();

        } catch (Exception ex) {
            log.error("outputTifFileData异常", ex);
            return "";
        }
    }

    @Transactional
    @Override
    public boolean updateList(List<LayerUpdateDto> layerUpdateDtoList) {
        try {
            List<LayerContentEntity> layerContentEntities = layerUpdateDtoList.stream().map(x -> {
                LayerContentEntity entity = new LayerContentEntity();
                entity.setId(x.getId());
                entity.setLayerType(x.getParentId());
                entity.setLayerSort(x.getSort());
                return entity;
            }).collect(Collectors.toList());
            this.updateBatchById(layerContentEntities);
            return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Transactional
    @Override
    public boolean updateBothList(List<LayerUpdateDto> layerUpdateDtoList) {
        // 同时更新图层表和目录表
        try {
            // 更新图层
            List<LayerContentEntity> layerContentEntities = layerUpdateDtoList.stream().filter(item ->{
                if(item.getType().equals("layer")){
                    return true;
                }
                return false;
            }).map(x -> {
                LayerContentEntity entity = new LayerContentEntity();
                entity.setId(x.getId());
                entity.setLayerType(x.getParentId()); 
                entity.setLayerSort(x.getSort());
                return entity;
            }).collect(Collectors.toList());
            
            if (!layerContentEntities.isEmpty()) {
                this.updateBatchById(layerContentEntities);
            }

            // 更新目录
            List<DataTypeEntity> dataTypeEntities = layerUpdateDtoList.stream().filter(item ->{
                if(item.getType().equals("dataType")){
                    return true;
                }
                return false;
            }).map(x -> {
                DataTypeEntity entity = new DataTypeEntity();
                entity.setId(x.getId());
                entity.setParentID(x.getParentId());
                entity.setCodeSort(x.getSort());
                return entity;
            }).collect(Collectors.toList());
            
            if (!dataTypeEntities.isEmpty()) {
                dataTypeService.updateBatchById(dataTypeEntities);
            }
            
            return true;
        } catch (Exception e) {
            log.error("批量更新图层和目录排序异常", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<PageResultDTO<Map<String, Object>>> pageAttrInfoByPoint(LayerFieldQuery queryDto) {

        List<LayerContentEntity> layers = baseMapper.selectList(new LambdaQueryWrapper<LayerContentEntity>().in(LayerContentEntity::getId, queryDto.getLayerIds()).ne(LayerContentEntity::getDataType, DictionaryIds.GEOM_RASTER));
        double lon = queryDto.getLon();
        double lat = queryDto.getLat();
        Double distance = queryDto.getDistance();
        if (distance == null){
            distance = 50d;
        }
        List<PageResultDTO<Map<String, Object>>> retDto = new ArrayList<>();
        try {
            for (LayerContentEntity item : layers) {

               // String query = String.format("SELECT *, ST_AsText(geom) as geomnew FROM %s WHERE ST_Intersects(geom,st_buffer('POINT(%f %f)',%f))=true", item.getLayerAttrTable(), lon, lat, distance / (2 * Math.PI * 6371004) * 360); // 假设getLayerAttrTable根据layerId返回表名
                String query = String.format(
                        "SELECT *, ST_AsText(geom) AS geomnew " +
                                "FROM %s " +
                                "WHERE ST_DWithin(ST_Force2D(geom)::geography, ST_Point(%f, %f)::geography, %f)",
                        item.getLayerAttrTable(),
                        lon, lat,
                        distance
                );
                List<Map<String, Object>> dt = dynamicTableMapper.dynamicExecute(query);
                dt = formatNumericResult(dt);
                PageResultDTO<Map<String, Object>> ret = new PageResultDTO<>();
                ret.setRecords(dt);
                ret.setLayerName(item.getLayerName());
                ret.setLayerId(item.getId());
                retDto.add(ret);
            }
        } catch (Exception e) {
            log.error("getInfoByLayerIdsAndPtTolerancePage:异常", e);
            return null;
        }


        return retDto;
    }

    private String getLayerFields(String layerId) {
        StringBuilder str = new StringBuilder();
        try {
            // 假设_layerContentRepository和_layerFieldRepository是已注入的Repository
            List<LayerContentEntity> layerContents = this.list();
            List<LayerFieldEntity> layerFields = layerFieldService.list();

            // 使用Java Stream API实现LINQ查询
            List<String> attributeNames = layerContents.stream()
                    .filter(a -> a.getId().equals(layerId))
                    .flatMap(a -> layerFields.stream()
                            .filter(b -> b.getLayerID().equals(a.getId()))
                            .map(LayerFieldEntity::getAttributeName))
                    .collect(Collectors.toList());

            if (!attributeNames.isEmpty()) {
                for (String name : attributeNames) {
                    str.append("\"")
                            .append(name)
                            .append("\"")
                            .append(",");
                }
                if (str.length() > 0) {
                    str.setLength(str.length() - 1);
                }
            }
            return str.toString();
        } catch (Exception ex) {
            return "";
        }
    }

    @Override
    public Object getLayerValue(String layerId, String longitude, String latitude) {
        Dataset dataset = null;
        try {
            LayerContentEntity layer = baseMapper.selectById(layerId);
            if (layer == null) {
                return null;
            }
            if (!"2".equals(layer.getUploadFileType())) {
                return null;
            }

            String uploadFileName = layer.getUploadFileName();
            if (Func.isEmpty(uploadFileName)) {
                return null;
            }

            // 处理可能包含多个文件的情况（如 .tif*...）
            String[] split = uploadFileName.split("\\*");
            String tifPath = "";
            for (String s : split) {
                if (s.toLowerCase().endsWith(".tif") || s.toLowerCase().endsWith(".geotiff")) {
                    tifPath = s;
                    break;
                }
            }

            if (Func.isEmpty(tifPath) || !new File(tifPath).exists()) {
                return null;
            }

            // 注册 GDAL
            gdal.AllRegister();
            gdal.SetConfigOption("GDAL_FILENAME_IS_UTF8", "YES");
            dataset = gdal.Open(tifPath, gdalconst.GA_ReadOnly);
            if (dataset == null) {
                return null;
            }

            double[] geoTransform = dataset.GetGeoTransform();
            double lon = Double.parseDouble(longitude);
            double lat = Double.parseDouble(latitude);
            
            // 使用 gridConverter 计算行列号
            int[] pair = gridConverter.LonLat2XY(geoTransform, lon, lat);
            int x = pair[0];
            int y = pair[1];

            int xSize = dataset.getRasterXSize();
            int ySize = dataset.getRasterYSize();

            if (x < 0 || x >= xSize || y < 0 || y >= ySize) {
                return null;
            }

            int bandCount = dataset.getRasterCount();
            if (bandCount == 1) {
                Band band = dataset.GetRasterBand(1);
                double[] val = new double[1];
                band.ReadRaster(x, y, 1, 1, val);
                if(Func.isNotEmpty(val[0])){
                    return val[0];
                }else {
                    return "";
                }
            } else {
                List<String> values = new ArrayList<>();
                for (int i = 1; i <= bandCount; i++) {
                    Band band = dataset.GetRasterBand(i);
                    double[] val = new double[1];
                    band.ReadRaster(x, y, 1, 1, val);
                    values.add("band" + i + ": " + val[0]);
                }
                if(Func.isNotEmpty(values)){
                    return values.stream().map(String::valueOf).collect(Collectors.joining(", "));
                }else {
                    return "";
                }
            }
        } catch (Exception e) {
            log.error("获取图层值失败", e);
            return null;
        } finally {
            if (dataset != null) {
                dataset.delete();
            }
        }
    }

    /**
     * 处理返回结果中的数值格式化，将科学计数法转为普通数字
     * 解决 PostgreSQL numeric 类型返回 0E-8 等科学计数法的问题
     */
    private List<Map<String, Object>> formatNumericResult(List<Map<String, Object>> result) {
        if (result == null) return result;
        for (Map<String, Object> row : result) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Number) {
                    entry.setValue(formatNumber((Number) value));
                } else if (value instanceof String) {
                    // 检查是否包含科学计数法格式的数字字符串
                    String str = (String) value;
                    if (str.matches("(?i).*[0-9]e[+-].*")) {
                        try {
                            double d = Double.parseDouble(str);
                            entry.setValue(formatNumber(d));
                        } catch (NumberFormatException e) {
                            // 忽略无法解析的字符串
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * 格式化数字，将科学计数法转为普通表示
     */
    private Object formatNumber(Number num) {
        double d = num.doubleValue();
        if (d == 0) return 0;
        if (Math.abs(d) < 0.0001 && d != 0) {
            // 科学计数法表示的非常小的数，转为 0
            return 0;
        }
        // 使用 BigDecimal 转为普通数字格式
        String plain = BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
        // 如果转换后是科学计数法（如 1e-8），手动转换
        if (plain.matches("[0-9.]+e[+-]?[0-9]+")) {
            BigDecimal bd = new BigDecimal(plain);
            plain = bd.toPlainString();
        }
        // 返回数字而不是字符串
        try {
            if (plain.contains(".")) {
                return Double.parseDouble(plain);
            } else {
                return Long.parseLong(plain);
            }
        } catch (NumberFormatException e) {
            return plain;
        }
    }

    /**
     * 统计栅格中不同像素值的数量
     * @param tifPath TIF文件路径
     * @return 不同值的数量
     */
    private int countUniquePixelValues(String tifPath) {
        try {
            tiffileReader tifReader = new tiffileReader(tifPath);
            TiffileInfo tiffileInfo = tifReader.get_tiffileInfo();
            Double noData = tifReader.GetNoData();
            double[] vdoubles = tifReader.ReadAsDouble(0, 0);

            Map<Double, Integer> tifMap = new java.util.HashMap<>();
            int xSize = tiffileInfo.getXSize();
            int ySize = tiffileInfo.getYSize();

            // 获取重分类tif各个分类的出现次数
            for (int y = 0; y < ySize; y++) {
                for (int x = 0; x < xSize; x++) {
                    double vdouble = vdoubles[x + y * xSize];
                    if (noData == null || Math.abs(vdouble - noData) > 0.0001) {
                        // 并非noData值
                        Integer count = tifMap.get(vdouble);
                        if (count != null) {
                            tifMap.put(vdouble, count + 1);
                        } else {
                            tifMap.put(vdouble, 1);
                        }
                    }
                }
            }

            return tifMap.size();
        } catch (Exception e) {
            log.warn("统计栅格不同值失败: " + e.getMessage());
            return 0;
        }
    }

    /**
     * 将GDAL数据类型常量转换为可读字符串
     */
    private String getDataTypeName(int gdalDataType) {
        switch (gdalDataType) {
            case 1: return "Byte";
            case 2: return "UInt16";
            case 3: return "Int16";
            case 4: return "UInt32";
            case 5: return "Int32";
            case 6: return "Float32";
            case 7: return "Float64";
            default: return "Unknown";
        }
    }

}
