package com.whulri.geodata.datarelease.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.whulri.geodata.basecore.mapper.*;
import com.whulri.geodata.basecore.service.IOperateLogService;
import com.whulri.geodata.basecore.service.ISystemUserService;
import com.whulri.geodata.common.config.AppSettingConfig;
import com.whulri.geodata.common.constant.DictionaryIds;
import com.whulri.geodata.common.dto.dataStyle.*;
import com.whulri.geodata.common.dto.layerContent.LayerContentOutputDto;
import com.whulri.geodata.common.dto.systemUser.SystemUserDto;
import com.whulri.geodata.common.entity.*;
import com.whulri.geodata.common.gdal.handler.GeoServer;
import com.whulri.geodata.common.gdal.pojo.Style;
import com.whulri.geodata.common.result.ListResultOutput;
import com.whulri.geodata.common.util.*;
import com.whulri.geodata.common.util.style.*;
import com.whulri.geodata.common.vo.PagedResultOutput;
import com.whulri.geodata.datarelease.service.IDataStyleService;
import com.whulri.gdal.model.TiffileInfo;
import lombok.extern.slf4j.Slf4j;
import org.gdal.gdal.Band;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconstConstants;
import com.whulri.geodata.common.bridge.BeanUtil;
import com.whulri.geodata.common.bridge.Func;
import com.whulri.geodata.common.bridge.UserContextUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
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
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class DataStyleServiceImpl extends ServiceImpl<DataStyleMapper, DataStyleEntity> implements IDataStyleService {

    @Autowired
    private DataStyleMapper dataStyleMapper;

    @Autowired
    private DataTypeMapper dataTypeMapper;

    @Autowired
    private ISystemUserService systemUserService;

    @Autowired
    private LayerFieldMapper layerFieldMapper;

    @Autowired
    private LayerMapper layerMapper;

    @Autowired
    private GeoServer geoServer;

    @Autowired
    private AppSettingConfig appSettingConfig;

    @Autowired
    private IOperateLogService operateLogService;

    @Autowired
    private UtilityMessageConvert utilityMessageConvert;

    @Autowired
    private DynamicTableMapper dynamicTableMapper;

    @Resource
    private DataInformationMapper dataInformationMapper;

    @Resource
    private MetadataMapper metadataMapper;

    @Override
    public ListResultOutput<DataStyleDto> getAllList(String name) {
        try {
            LambdaQueryWrapper<DataStyleEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
            if (Func.isNotEmpty(name)) {
                lambdaQueryWrapper.like(DataStyleEntity::getStyleName, name);
            }
            List<DataStyleEntity> dataStyleEntities = dataStyleMapper.selectList(lambdaQueryWrapper);
            List<DataStyleDto> copy = BeanUtil.copy(dataStyleEntities, DataStyleDto.class);
            return new ListResultOutput<>(copy);
        } catch (Exception e) {
            log.error("根据样式名称模糊查找所有样式失败！！！");
            return new ListResultOutput<>();
        }
    }

    @Override
    public boolean isExists(String name) {
        try {
            DataStyleEntity dataStyleEntity = new DataStyleEntity();
            if (Func.isEmpty(name)) {
                dataStyleEntity = dataStyleMapper.selectOne(new QueryWrapper<>());
            } else {
                dataStyleEntity = dataStyleMapper.selectOne(new LambdaQueryWrapper<DataStyleEntity>().eq(DataStyleEntity::getStyleName, name));
            }
            return dataStyleEntity != null;
        } catch (Exception e) {
            log.error("判断样式是否存在异常！！！");
            return false;
        }
    }

    @Override
    public PagedResultOutput<DataStyleDto> getAllListPage(DataStyleInputDto input, int pageSize, int pageIndex) {
        String name = input.getStyleName(), type, dataType;

        if (Func.isNotBlank(input.getStyleType())) {
            type = getMultiChildTypeByType(input.getStyleType());
        } else {
            type = "";
        }
        if (Func.isNotEmpty(input.getStyleDataType())) {
            dataType = input.getStyleDataType();
        } else {
            dataType = "";
        }
        input.setCreateBy(UserContextUtil.resolveUserCode(input.getCreateBy()));
        List<DataStyleEntity> layerContent = getDataByUserCodeAsync(input.getCreateBy());
        assert layerContent != null;
        if (Func.isNotEmpty(name)) {
            layerContent = layerContent.stream()
                    .filter(s -> Func.isNotEmpty(s.getStyleName()) && s.getStyleName().contains(name))
                    .collect(Collectors.toList());
        }
        if (Func.isNotEmpty(type)) {
            layerContent = layerContent.stream()
                    .filter(s -> Func.isNotEmpty(s.getStyleType()) && type.contains(s.getStyleType()))
                    .collect(Collectors.toList());
        }
        if (Func.isNotEmpty(dataType)) {
            layerContent = layerContent.stream()
                    .filter(s -> Func.isNotEmpty(s.getStyleDataType()) && dataType.contains(s.getStyleDataType()))
                    .collect(Collectors.toList());
        }

        List<DataTypeEntity> listStyleType = dataTypeMapper.selectList(new LambdaQueryWrapper<>());

        Map<String, String> typeMap = listStyleType.stream()
                .filter(t -> t.getId() != null) // 防止 null key
                .collect(Collectors.toMap(
                        DataTypeEntity::getId,      // key: id
                        DataTypeEntity::getTypeName, // value: typeName
                        (existing, replacement) -> existing // 处理重复 id（保留第一个）
                ));

        List<DataStyleDto> query = layerContent.stream()
                .map(d -> {
                    // 创建 DTO
                    DataStyleDto dto = new DataStyleDto();
                    dto.setId(d.getId());
                    dto.setStyleName(d.getStyleName());
                    dto.setStyleType(d.getStyleType());
                    // 关键：左连接逻辑
                    // 如果 d.styleType 在 typeMap 中存在 → 取 typeName；否则 ""
                    String typeName = typeMap.get(d.getStyleType());
                    dto.setStyleTypeName(typeName != null ? typeName : "");
                    dto.setStyleContent(d.getStyleContent());
                    dto.setStyleDesc(d.getStyleDesc());
                    dto.setCreateDT(d.getCreateDT());
                    dto.setCreateBy(d.getCreateBy());
                    dto.setStyleConfigType(d.getStyleConfigType());
                    dto.setStyleDataType(d.getStyleDataType());
                    dto.setStyleDefaultLayer(d.getStyleDefaultLayer());
                    dto.setStyleRenderColorBand(d.getStyleRenderColorBand());
                    dto.setStyleRenderField(d.getStyleRenderField());
                    dto.setStyleRenderRule(d.getStyleRenderRule());
                    d.setSymbol(d.getSymbol());
                    d.setStyleRenderFieldName(d.getStyleRenderFieldName());
                    return dto;
                })
                .collect(Collectors.toList());

        int count = query.size();
        List<DataStyleDto> result = query.stream()
                .skip((long) (pageIndex - 1) * pageSize)
                .limit(pageSize)
                .collect(Collectors.toList());
        return new PagedResultOutput<>(count, result);
    }

    @Override
    public DataStyleOutputDto getDetailById(String id) {
        try {
            DataStyleEntity dataStyleEntity = dataStyleMapper.selectById(id);
            if (dataStyleEntity == null) {
                return null;
            }
            DataStyleOutputDto result = BeanUtil.copy(dataStyleEntity, DataStyleOutputDto.class);
            if ("1".equals(result.getStyleConfigType())) {
                // 历史数据可能无 styleRenderRule / styleContent：跳过解析而非整体失败
                if (Func.isNotBlank(dataStyleEntity.getStyleRenderRule()) || Func.isNotBlank(dataStyleEntity.getStyleContent())) {
                    try {
                        List<StyleInfo> list = getStyleInfo(result.getStyleContent(), dataStyleEntity.getStyleRenderRule());
                        if (Func.isNotEmpty(list)) {
                            result.setStyleInfo(list.get(0));
                            if (list.size() > 1) {
                                result.setStyleInfoDefault(list.get(1));
                            }
                        }
                    } catch (Exception e) {
                        log.warn("样式规则解析失败（返回基础信息）: id={}, {}", id, e.getMessage());
                    }
                }
            }
            if ("2".equals(result.getStyleConfigType())) {
                String layerid = result.getStyleDefaultLayer();
                String fieldid = result.getStyleRenderField();
                try {
                    LayerFieldEntity entity = (Func.isNotBlank(layerid) && Func.isNotBlank(fieldid))
                            ? layerFieldMapper.selectOne(new LambdaQueryWrapper<LayerFieldEntity>()
                                    .eq(LayerFieldEntity::getLayerID, layerid)
                                    .eq(LayerFieldEntity::getId, fieldid))
                            : null;
                    if (entity != null) {
                        result.setStyleRenderFieldName(entity.getAttributeName());
                        result.setRuleDatas(getRuleDatas(result.getStyleContent(), entity.getAttributeName(), dataStyleEntity.getSymbol()));
                    } else {
                        result.setRuleDatas(getRuleDatas(result.getStyleContent(), null, dataStyleEntity.getSymbol()));
                    }
                    LayerContentEntity layerContentEntity = Func.isNotBlank(layerid)
                            ? layerMapper.selectOne(new LambdaQueryWrapper<LayerContentEntity>().eq(LayerContentEntity::getId, layerid))
                            : null;
                    if (layerContentEntity != null) {
                        result.setStyleDefaultLayerName(layerContentEntity.getLayerName());
                        result.setLayerContent(BeanUtil.copy(layerContentEntity, LayerContentOutputDto.class));
                    }
                } catch (Exception e) {
                    log.warn("样式规则解析失败（返回基础信息）: id={}, {}", id, e.getMessage());
                }
            }

            return result;
        } catch (Exception e) {
            log.error("根据样式编号查询样式详细信息异常！！！");
            return null;
        }
    }

    @Override
    public DataStyleOutputDto getDetailByLayerID(String layerID) {
        try {
            DataStyleEntity query = dataStyleMapper.selectOne(new LambdaQueryWrapper<DataStyleEntity>()
                    .eq(DataStyleEntity::getStyleDefaultLayer, layerID));
            if (query == null) {
                // 该图层未绑定样式：返回 null 便于前端区分「无样式」与「空对象」
                return null;
            }
            DataStyleOutputDto result = BeanUtil.copy(query, DataStyleOutputDto.class);
            if ("1".equals(result.getStyleConfigType())) {
                // 历史数据可能无 styleRenderRule / styleContent：跳过解析而非整体失败
                if (Func.isNotBlank(query.getStyleRenderRule()) || Func.isNotBlank(query.getStyleContent())) {
                    try {
                        List<StyleInfo> list = getStyleInfo(result.getStyleContent(), query.getStyleRenderRule());
                        if (Func.isNotEmpty(list)) {
                            result.setStyleInfo(list.get(0));
                            if (list.size() > 1) {
                                result.setStyleInfoDefault(list.get(1));
                            }
                        }
                    } catch (Exception e) {
                        log.warn("图层样式规则解析失败（返回基础信息）: layerID={}, {}", layerID, e.getMessage());
                    }
                }
            }

            if (Objects.equals(result.getStyleConfigType(), "2")) {
                try {
                    result.setRuleDatas(JSONObject.parseArray(query.getSymbol(), RuleData.class));
                } catch (Exception e) {
                    log.warn("图层样式规则解析失败（返回基础信息）: layerID={}, {}", layerID, e.getMessage());
                    result.setRuleDatas(new ArrayList<>());
                }
            }

            if (Objects.equals(result.getStyleConfigType(), "4")) {// 栅格数据
                try {
                    result.setTifStyles(JSONObject.parseArray(result.getSymbol(), TifStyle.class));
                } catch (Exception e) {
                    log.warn("栅格样式解析失败（返回基础信息）: layerID={}, {}", layerID, e.getMessage());
                    result.setTifStyles(new ArrayList<>());
                }
            }
            if (Objects.equals(result.getStyleConfigType(), "5") && Func.isNotBlank(query.getSymbol())) {
                // 三波段透明色回填（save 时写入 symbol：{"tifThreeColor":"RRGGBB"}）
                try {
                    Map<String, Object> symMap = JsonUtils.fromJson(query.getSymbol(), Map.class);
                    if (symMap != null && symMap.get("tifThreeColor") != null) {
                        result.setTifThreeColor(String.valueOf(symMap.get("tifThreeColor")));
                    }
                } catch (Exception ignore) {
                    // symbol 非 JSON 时忽略
                }
            }
            return result;
        } catch (Exception e) {
            log.error("根据图层编号查询图层的样式信息异常!!!");
            return null;
        }
    }

    @Override
    public DataStyleDto insert(DataStyleInputDto input) {
        if (Func.isEmpty(input.getId())) {
            input.setId(UUID.randomUUID().toString());
        }

        String styleContent = input.getStyleContent();
        if ("1".equals(input.getStyleConfigType()) && input.getStyleInfo() != null) {
//            // 解析后生成xml内容值
//            styleContent = GetSld(input.getStyleInfo(), input.getStyleDataType());
            StyleInfo sInfoDefault = new StyleInfo();

            if (input.getStyleInfoDefault() != null && input.getStyleInfoDefault().getIsDefaultRule()) {
                sInfoDefault = input.getStyleInfoDefault();
            }
            // 解析后生成xml内容值
            styleContent = GetSld(input.getStyleInfo(), input.getStyleDataType(), sInfoDefault);
        }
        if ("2".equals(input.getStyleConfigType()) && input.getRuleDatas() != null) {
            styleContent = GetSld(input.getRuleDatas(), input.getStyleRenderFieldName(), input.getStyleDataType());
        }

        if ("3".equals(input.getStyleConfigType()) && input.getTifStyles() != null) {
            styleContent = GetTifSld(input.getTifStyles(), input.getStyleDefaultLayer(), input.getStyleDefaultLayer());
        }

        input.setStyleContent(styleContent);

        DataStyleEntity entity = new DataStyleEntity(
                input.getId(),
                input.getStyleName(),
                input.getStyleType(),
                input.getStyleContent(),
                input.getStyleDesc(),
                LocalDateTime.now(),
                input.getCreateBy(),
                input.getStyleDataType(),
                input.getStyleConfigType(),
                input.getStyleDefaultLayer(),
                input.getStyleRenderField(),
                input.getStyleRenderFieldName(),
                input.getStyleRenderColorBand(),
                input.getStyleRenderRule(),
                input.getSymbol()
        );

        try {
            if (Func.isEmpty(entity.getStyleRenderRule()) && input.getStyleInfo() != null) {
                StyleInfo si = JsonUtils.fromJson(JSONObject.toJSONString(input.getStyleInfo()), StyleInfo.class);
                entity.setStyleRenderRule(JSONObject.toJSONString(si));
            }

            if (Func.isEmpty(entity.getSymbol()) && input.getRuleDatas() != null) {
//                List<RuleData> ruleDatas = mapper.readValue(JSONObject.toJSONString(input.getRuleDatas()), new TypeReference<List<RuleData>>() {});
                entity.setSymbol(JSONObject.toJSONString(input.getRuleDatas()));
            }
            insertStyle(input.getStyleDefaultLayer(), input.getStyleName(), input.getStyleContent());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        try {
            dataStyleMapper.insert(entity);
            DataStyleDto result = BeanUtil.copy(entity, DataStyleDto.class);

            operateLogService.writeOperateLog(input.getId(), input.getCreateBy(), 1003, 1101, 1201, 1511, "(" + input.getStyleName() + ")");
            return result;
        } catch (Exception e) {
            operateLogService.writeOperateLog(input.getId(), input.getCreateBy(), 1003, 1101, 1202, 1512, "(" + input.getStyleName() + ")");
            throw new RuntimeException(e);
        }
    }

    @Override
    public DataStyleDto update(DataStyleInputDto input) {
        try {
            // 既有样式记录：用于「本次未提交样式明细时保留原值」，避免仅改元信息把 SLD / 规则打空
            DataStyleEntity oldStyle = input.getId() == null ? null : dataStyleMapper.selectById(input.getId());
            String styleContent = input.getStyleContent();
            String styleRenderRule = input.getStyleRenderRule();
            String symbol = input.getSymbol();
            // 几何类型兜底为「面」：避免历史数据 styleDataType 为空时 GetSld 内 type.equals  NPE
            String geomType = Func.isNotBlank(input.getStyleDataType()) ? input.getStyleDataType() : DictionaryIds.GEOM_POLYGON;
            if ("1".equals(input.getStyleConfigType())) {
                // 单一符号：提交了样式参数则重新生成 SLD，否则保留原值
                if (input.getStyleInfo() != null) {
                    StyleInfo sInfoDefault = new StyleInfo();
                    if (input.getStyleInfoDefault() != null && input.getStyleInfoDefault().getIsDefaultRule()) {
                        sInfoDefault = input.getStyleInfoDefault();
                    }
                    styleContent = GetSld(input.getStyleInfo(), geomType, sInfoDefault);
                    styleRenderRule = JSONObject.toJSONString(input.getStyleInfo());
                } else if (oldStyle != null) {
                    if (styleContent == null) styleContent = oldStyle.getStyleContent();
                    if (styleRenderRule == null) styleRenderRule = oldStyle.getStyleRenderRule();
                }
            } else if ("2".equals(input.getStyleConfigType())) {
                // 分级设色 / 唯一值：提交了规则明细则重新生成 SLD，否则保留原 SLD 与 symbol
                String fieldName = input.getStyleRenderFieldName();
                if (Func.isBlank(fieldName) && oldStyle != null) fieldName = oldStyle.getStyleRenderFieldName();
                if (input.getRuleDatas() != null && !input.getRuleDatas().isEmpty() && Func.isNotBlank(fieldName)) {
                    styleContent = GetSld(input.getRuleDatas(), fieldName.toLowerCase(), geomType);
                    symbol = JSONObject.toJSONString(input.getRuleDatas());
                } else if (oldStyle != null) {
                    if (styleContent == null) styleContent = oldStyle.getStyleContent();
                    if (symbol == null) symbol = oldStyle.getSymbol();
                }
            } else if ("3".equals(input.getStyleConfigType())) {
                // 栅格色带 SLD：前端传 URL 编码文本；为空时保留原值（原实现直接 decode(null) 会 NPE）
                if (Func.isNotBlank(input.getStyleContent())) {
                    styleContent = URLDecoder.decode(input.getStyleContent(), StandardCharsets.UTF_8);
                } else if (oldStyle != null) {
                    styleContent = oldStyle.getStyleContent();
                }
            } else if (oldStyle != null) {
                if (styleContent == null) styleContent = oldStyle.getStyleContent();
                if (symbol == null) symbol = oldStyle.getSymbol();
            }
            if (styleRenderRule == null && oldStyle != null) styleRenderRule = oldStyle.getStyleRenderRule();
            if ("4".equals(input.getStyleConfigType())) {//单波段tif
                LayerContentEntity byId = layerMapper.selectById(input.getLayerId());
                String layerAttrTable = byId.getLayerAttrTable();//图层名称

                LambdaQueryWrapper<MetadataEntity> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(MetadataEntity::getDataId,input.getLayerId());//新加的图层数据id要查询
                MetadataEntity metadataEntity = metadataMapper.selectOne(wrapper);
                styleContent = GetTifSld2(input.getTifStyles(), layerAttrTable, true, metadataEntity.getNoData().doubleValue(), metadataEntity.getMaxPixel().doubleValue());

                LambdaQueryWrapper<DataStyleEntity> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.eq(DataStyleEntity::getStyleDefaultLayer,input.getLayerId());
                DataStyleEntity dataStyleEntity = dataStyleMapper.selectOne(queryWrapper);
                GeoServerUtil.bindStyleToLayer("http://"+appSettingConfig.getGeoServerIP()+":"+appSettingConfig.getGeoServerPort()+"/geoserver",
                        appSettingConfig.getGeoServerUser(),
                        appSettingConfig.getGeoServerPwd(),
                        appSettingConfig.getGeoWorkspace(),
                        layerAttrTable,
                        layerAttrTable,
                        styleContent);
                if (Func.isNotEmpty(dataStyleEntity)){//不为空就修改样式
                    dataStyleEntity.setStyleType(input.getType());
                    dataStyleEntity.setStyleName(Func.isNotBlank(input.getStyleName()) ? input.getStyleName() : dataStyleEntity.getStyleName());
                    dataStyleEntity.setStyleContent(styleContent);
                    dataStyleEntity.setStyleRenderRule(input.getStyleRenderRule());//或者在这input.getStyleInfo()
                    dataStyleEntity.setSymbol(JSONObject.toJSONString(input.getTifStyles()));
                    dataStyleMapper.updateById(dataStyleEntity);
                }else {//为空就新增样式
                    DataStyleEntity entity = new DataStyleEntity();
                    entity.setId(UUID.randomUUID().toString());
                    entity.setStyleName(Func.isNotBlank(input.getStyleName()) ? input.getStyleName() : layerAttrTable);
                    entity.setStyleType(input.getType());
                    entity.setStyleContent(styleContent);
                    entity.setCreateDT(LocalDateTime.now());
                    entity.setStyleConfigType(input.getStyleConfigType());
                    entity.setStyleDefaultLayer(input.getLayerId());
                    entity.setStyleRenderRule(input.getStyleRenderRule());//或者在这input.getStyleInfo()
                    entity.setSymbol(JSONObject.toJSONString(input.getTifStyles()));
                    dataStyleMapper.insert(entity);
                }
                return null;
            }
            if ("5".equals(input.getStyleConfigType())){//三波段tif样式发布
                LayerContentEntity byId = layerMapper.selectById(input.getLayerId());
                String layerAttrTable = byId.getLayerAttrTable();//图层名称
                GeoServerUtil.setTiffTransparency("http://"+appSettingConfig.getGeoServerIP()+":"+appSettingConfig.getGeoServerPort()+"/geoserver",
                        appSettingConfig.getGeoServerUser(),
                        appSettingConfig.getGeoServerPwd(),
                        appSettingConfig.getGeoWorkspace(),
                        layerAttrTable,input.getTifThreeColor());
                // 透明色落库到 symbol，便于编辑回填（getDetailById configType=5 解析）
                String threeColorSymbol = JSONObject.toJSONString(
                        Map.of("tifThreeColor", input.getTifThreeColor() == null ? "" : input.getTifThreeColor()));
                LambdaQueryWrapper<DataStyleEntity> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.eq(DataStyleEntity::getStyleDefaultLayer,input.getLayerId());
                DataStyleEntity dataStyleEntity = dataStyleMapper.selectOne(queryWrapper);
                if (Func.isNotEmpty(dataStyleEntity)){//不为空就修改样式
                    dataStyleEntity.setStyleType(input.getType());
                    dataStyleEntity.setStyleName(Func.isNotBlank(input.getStyleName()) ? input.getStyleName() : dataStyleEntity.getStyleName());
                    dataStyleEntity.setStyleContent(styleContent);
                    dataStyleEntity.setStyleRenderRule(input.getStyleRenderRule());//或者在这input.getStyleInfo()
                    dataStyleEntity.setSymbol(threeColorSymbol);
                    dataStyleMapper.updateById(dataStyleEntity);
                }else {//为空就新增样式
                    DataStyleEntity entity = new DataStyleEntity();
                    entity.setId(UUID.randomUUID().toString());
                    entity.setStyleName(Func.isNotBlank(input.getStyleName()) ? input.getStyleName() : layerAttrTable);
                    entity.setStyleType(input.getType());
                    entity.setStyleContent(styleContent);
                    entity.setCreateDT(LocalDateTime.now());
                    entity.setStyleConfigType(input.getStyleConfigType());
                    entity.setStyleDefaultLayer(input.getLayerId());
                    entity.setStyleRenderRule(input.getStyleRenderRule());//或者在这input.getStyleInfo()
                    entity.setSymbol(threeColorSymbol);
                    dataStyleMapper.insert(entity);
                }
                return null;
            }
            if ("6".equals(input.getStyleConfigType())){//多波段tif样式发布
                LayerContentEntity byId = layerMapper.selectById(input.getLayerId());
                String layerAttrTable = byId.getLayerAttrTable();//图层名称
                styleContent = GetMultiBandTifSld(input.getTifStyles(), layerAttrTable,false,0.0,0.0,input.getBandIndex());
                GeoServerUtil.bindStyleToLayer("http://"+appSettingConfig.getGeoServerIP()+":"+appSettingConfig.getGeoServerPort()+"/geoserver",
                        appSettingConfig.getGeoServerUser(),
                        appSettingConfig.getGeoServerPwd(),
                        appSettingConfig.getGeoWorkspace(),
                        layerAttrTable,
                        layerAttrTable,
                        styleContent);
                LambdaQueryWrapper<DataStyleEntity> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.eq(DataStyleEntity::getStyleDefaultLayer,input.getLayerId());
                DataStyleEntity dataStyleEntity = dataStyleMapper.selectOne(queryWrapper);
                if (Func.isNotEmpty(dataStyleEntity)){//不为空就修改样式
                    dataStyleEntity.setStyleType(input.getType());
                    dataStyleEntity.setStyleName(Func.isNotBlank(input.getStyleName()) ? input.getStyleName() : dataStyleEntity.getStyleName());
                    dataStyleEntity.setStyleContent(styleContent);
                    dataStyleEntity.setStyleRenderRule(input.getStyleRenderRule());//或者在这input.getStyleInfo()
                    dataStyleEntity.setSymbol(JSONObject.toJSONString(input.getTifStyles()));
                    dataStyleMapper.updateById(dataStyleEntity);
                }else {//为空就新增样式
                    DataStyleEntity entity = new DataStyleEntity();
                    entity.setId(UUID.randomUUID().toString());
                    entity.setStyleName(Func.isNotBlank(input.getStyleName()) ? input.getStyleName() : layerAttrTable);
                    entity.setStyleType(input.getType());
                    entity.setStyleContent(styleContent);
                    entity.setCreateDT(LocalDateTime.now());
                    entity.setStyleConfigType(input.getStyleConfigType());
                    entity.setStyleDefaultLayer(input.getLayerId());
                    entity.setStyleRenderRule(input.getStyleRenderRule());//或者在这input.getStyleInfo()
                    entity.setSymbol(JSONObject.toJSONString(input.getTifStyles()));
                    dataStyleMapper.insert(entity);
                }
                return null;
            }

//            if ("3".equals(input.getStyleConfigType()) && input.getTifStyles() != null) {
//                styleContent = GetTifSld(input.getTifStyles(), input.getStyleDefaultLayer(), input.getStyleDefaultLayer());
//            }

            input.setStyleContent(styleContent);
            DataStyleEntity entity = new DataStyleEntity(
                    input.getId(),
                    input.getStyleName(),
                    input.getStyleType(),
                    styleContent,
                    input.getStyleDesc(),
                    LocalDateTime.now(),
                    input.getCreateBy(),
                    input.getStyleDataType(),
                    input.getStyleConfigType(),
                    input.getStyleDefaultLayer(),
                    input.getStyleRenderField(),
                    input.getStyleRenderFieldName(),
                    input.getStyleRenderColorBand(),
                    styleRenderRule,
                    symbol
            );

            dataStyleMapper.updateById(entity);
            // 显式解绑：styleDefaultLayer 传空串时清空图层绑定（updateById 的 NOT_NULL 策略不会写 null）
            if (input.getStyleDefaultLayer() != null && input.getStyleDefaultLayer().isEmpty()) {
                dataStyleMapper.update(null, new LambdaUpdateWrapper<DataStyleEntity>()
                        .eq(DataStyleEntity::getId, input.getId())
                        .set(DataStyleEntity::getStyleDefaultLayer, null));
            }
            DataStyleDto result = BeanUtil.copy(entity, DataStyleDto.class);

//            insertStyle(result.getStyleDefaultLayer(), result.getStyleName(), result.getStyleContent());
            if (Func.isNotBlank(styleContent)) {
                GeoServerUtil.bindStyleToLayer("http://"+appSettingConfig.getGeoServerIP()+":"+appSettingConfig.getGeoServerPort()+"/geoserver",
                        appSettingConfig.getGeoServerUser(),
                        appSettingConfig.getGeoServerPwd(),
                        appSettingConfig.getGeoWorkspace(),
                        result.getStyleName(),
                        result.getStyleName(),
                        styleContent);
            }
            operateLogService.writeOperateLog(input.getId(), input.getCreateBy(), 1003, 1102, 1201, 1521, "(" + input.getStyleName() + ")");
            return result;
        } catch (Exception e) {
            operateLogService.writeOperateLog(input.getId(), input.getCreateBy(), 1003, 1102, 1202, 1522, "(" + input.getStyleName() + ")");
            throw new RuntimeException(e);
        }
    }

    @Override
    public Map<String, Object> getTifMaxMin(String id,Integer bandIndex) {
        Map<String, Object> map = new HashMap<>();
        if (Func.isNotEmpty(bandIndex)){
            LambdaQueryWrapper<MetadataEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(MetadataEntity::getDataId,id);//新加的图层数据id要查询
            MetadataEntity metadataEntity = metadataMapper.selectOne(wrapper);
            //进一法保留三位小数
            String max = metadataEntity.getMaxPixel().setScale(3, RoundingMode.UP).stripTrailingZeros().toPlainString();
            map.put("max",max);
            //去尾法保留三位小数
            String min = metadataEntity.getMinPixel().setScale(3, RoundingMode.DOWN).stripTrailingZeros().toPlainString();
            map.put("min",min);
        }else {
            LayerContentEntity byId = layerMapper.selectById(id);
            String uploadFileName = byId.getUploadFileName();
            gdal.AllRegister();
            Dataset dataset = gdal.Open(uploadFileName, gdalconstConstants.GA_ReadOnly);
            TiffileInfo _tiffileInfo = new TiffileInfo();
            _tiffileInfo.setXSize(dataset.getRasterXSize());
            _tiffileInfo.setYSize(dataset.getRasterYSize());
            _tiffileInfo.setRasterCount(dataset.getRasterCount());
            _tiffileInfo.setProjection(dataset.GetProjection());
            _tiffileInfo.setGeoTransform(dataset.GetGeoTransform());
            Band band = dataset.GetRasterBand(Func.isNotEmpty(bandIndex)?bandIndex:_tiffileInfo.getRasterCount());
            Double[] nodataArr = new Double[1];
            band.GetNoDataValue(nodataArr);
            double nodata = nodataArr[0];
            double[] _result = new double[_tiffileInfo.GetGridCount()];
            band.ReadRaster(0, 0, _tiffileInfo.getXSize(), _tiffileInfo.getYSize(), _result);
            java.util.OptionalDouble maxOpt = Arrays.stream(_result)
                .filter(v -> !Double.isNaN(v) && v != nodata)
                .max();
            java.util.OptionalDouble minOpt = Arrays.stream(_result)
                .filter(v -> !Double.isNaN(v) && v != nodata)
                .min();
            BigDecimal maxb = new BigDecimal(String.valueOf(maxOpt.orElse(0)));
            String maxs = maxb.setScale(3, RoundingMode.UP).stripTrailingZeros().toPlainString();
            map.put("max",maxs);
            BigDecimal minb = new BigDecimal(String.valueOf(minOpt.orElse(0)));
            String mins = minb.setScale(3, RoundingMode.DOWN).stripTrailingZeros().toPlainString();
            map.put("min",mins);
        }
        return map;
    }

    @Override
    public List<Map<String, Double>> getTifJenksBreaks(String id,Integer lvNum,Integer bandIndex) {
        LayerContentEntity byId = layerMapper.selectById(id);
        String uploadFileName = byId.getUploadFileName();
        LambdaQueryWrapper<MetadataEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MetadataEntity::getDataId,id);//新加的图层数据id要查询
        MetadataEntity metadataEntity = metadataMapper.selectOne(wrapper);
        gdal.AllRegister();
        Dataset dataset = gdal.Open(uploadFileName, gdalconstConstants.GA_ReadOnly);
        TiffileInfo _tiffileInfo = new TiffileInfo();
        _tiffileInfo.setXSize(dataset.getRasterXSize());
        _tiffileInfo.setYSize(dataset.getRasterYSize());
        _tiffileInfo.setRasterCount(dataset.getRasterCount());
        _tiffileInfo.setProjection(dataset.GetProjection());
        _tiffileInfo.setGeoTransform(dataset.GetGeoTransform());
        Band band = dataset.GetRasterBand(Func.isNotEmpty(bandIndex)?bandIndex:_tiffileInfo.getRasterCount());
        double[] _result = new double[_tiffileInfo.GetGridCount()];
        band.ReadRaster(0, 0, _tiffileInfo.getXSize(), _tiffileInfo.getYSize(), _result);
        band.delete();
        dataset.delete();
        return JenksBreaksUtil.calculateJenksBreaks(_result, metadataEntity.getNoData().doubleValue(), lvNum);
    }

//    /**
//     * 核心方法：自然断点法划分TIF数据等级范围
//     * @param tifData 从TIF读取的原始double数组
//     * @param noData TIF的无效值（需过滤）
//     * @param numClasses 期望的等级数
//     * @return 每个等级的数值范围列表（数量=等级数）
//     * @throws IllegalArgumentException 等级数非法/无有效数据
//     */
//    public static List<Map<String,Double>> calculateJenksBreaks(double[] tifData, double noData, int numClasses) {
//        // 1. 数据预处理：过滤nodata + 去重 + 排序
//        List<Double> validData = Arrays.stream(tifData)
//                .filter(num -> !Double.isNaN(num) && num != noData) // 过滤nodata和NaN
//                .distinct() // 去重
//                .boxed()
//                .sorted() // 升序排序
//                .collect(Collectors.toList());
//        // 2. 边界校验
//        if (validData.isEmpty()) {
//            throw new IllegalArgumentException("无有效数据（全部为nodata或空数组）");
//        }
//        if (numClasses <= 0) {
//            throw new IllegalArgumentException("等级数必须大于0");
//        }
//        // 若有效数据量 < 等级数，调整等级数为有效数据的唯一值数量
//        int actualClasses = Math.min(numClasses, validData.size());
//        if (actualClasses == 1) {
//            // 只有1个等级：范围为全部数据的min-max
//            HashMap<String, Double> map = new HashMap<>();
//            double min = validData.get(0);
//            double max = validData.get(validData.size() - 1);
//            map.put("min",min);
//            map.put("max",max);
//            return Collections.singletonList(map);
//        }
//        // 3. 转换为数组供Jenks算法使用
//        double[] dataArray = validData.stream().mapToDouble(Double::doubleValue).toArray();
//        // 4. 执行Jenks算法计算断点
//        double[] breaks = jenks(dataArray, actualClasses);
//        // 5. 根据断点划分每个等级的范围
//        List<Map<String,Double>> ranges = new ArrayList<>();
//        for (int i = 0; i < actualClasses; i++) {
//            HashMap<String, Double> map = new HashMap<>();
//            double min = (i == 0) ? dataArray[0] : breaks[i]; // 第一级min为数据最小值
//            double max = breaks[i + 1]; // 每级max为下一个断点
//            map.put("min",min);
//            map.put("max",max);
//            ranges.add(map);
//        }
//        return ranges;
//    }
//
//    /**
//     * 核心算法：Jenks自然断点实现（最小组内平方和）
//     * @param data 有序、去重、无无效值的数组
//     * @param numClasses 等级数
//     * @return 断点数组（长度=numClasses+1，包含最小值和最大值）
//     */
//    private static double[] jenks(double[] data, int numClasses) {
//        int n = data.length;
//        // 初始化矩阵：mat1[i][j] = 前i个数据分为j类的最小组内平方和
//        // mat2[i][j] = 前i个数据分为j类时的最优断点位置
//        double[][] mat1 = new double[n + 1][numClasses + 1];
//        int[][] mat2 = new int[n + 1][numClasses + 1];
//        // 初始化对角线（单个数据分为1类，平方和为0）
//        for (int i = 1; i <= numClasses; i++) {
//            mat1[1][i] = 0.0;
//            mat2[1][i] = 1;
//        }
//        for (int i = 1; i <= n; i++) {
//            mat1[i][1] = getVariance(data, 0, i - 1); // 前i个数据分为1类的方差
//            mat2[i][1] = 1;
//        }
//        // 动态规划计算最小组内平方和
//        for (int j = 2; j <= numClasses; j++) {
//            for (int i = j; i <= n; i++) {
//                mat1[i][j] = Double.MAX_VALUE;
//                // 遍历所有可能的断点位置
//                for (int l = j - 1; l < i; l++) {
//                    double currentVariance = mat1[l][j - 1] + getVariance(data, l, i - 1);
//                    // 保留最小平方和的断点
//                    if (currentVariance < mat1[i][j]) {
//                        mat1[i][j] = currentVariance;
//                        mat2[i][j] = l + 1; // 转换为1-based索引
//                    }
//                }
//            }
//        }
//        // 回溯获取断点
//        int k = n;
//        int[] breakIndices = new int[numClasses + 1];
//        breakIndices[numClasses] = n - 1; // 最后一个断点为数据最后一位索引
//        for (int j = numClasses; j > 1; j--) {
//            k = mat2[k][j] - 1;
//            breakIndices[j - 1] = k;
//        }
//        breakIndices[0] = 0; // 第一个断点为数据第一位索引
//        // 转换为数值断点
//        double[] breaks = new double[numClasses + 1];
//        for (int i = 0; i < breakIndices.length; i++) {
//            breaks[i] = data[breakIndices[i]];
//        }
//        return breaks;
//    }
//
//    /**
//     * 辅助方法：计算数组指定区间[start, end]的方差（组内平方和）
//     * @param data 有序数组
//     * @param start 起始索引（包含）
//     * @param end 结束索引（包含）
//     * @return 区间方差
//     */
//    private static double getVariance(double[] data, int start, int end) {
//        if (start == end) return 0.0;
//        int count = end - start + 1;
//        double sum = 0.0, sumSq = 0.0;
//        for (int i = start; i <= end; i++) {
//            sum += data[i];
//            sumSq += data[i] * data[i];
//        }
//        // 方差公式：(平方和/数量) - (均值)^2，再乘以数量得到组内平方和
//        double mean = sum / count;
//        return sumSq - sum * mean;
//    }

    @Override
    public List<Double> getTifValues(String id,Integer bandIndex) {
        LayerContentEntity byId = layerMapper.selectById(id);
        String uploadFileName = byId.getUploadFileName();
        LambdaQueryWrapper<MetadataEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MetadataEntity::getDataId,id);//新加的图层数据id要查询
        MetadataEntity metadataEntity = metadataMapper.selectOne(wrapper);
        gdal.AllRegister();
        Dataset dataset = gdal.Open(uploadFileName, gdalconstConstants.GA_ReadOnly);
        TiffileInfo _tiffileInfo = new TiffileInfo();
        _tiffileInfo.setXSize(dataset.getRasterXSize());
        _tiffileInfo.setYSize(dataset.getRasterYSize());
        _tiffileInfo.setRasterCount(dataset.getRasterCount());
        _tiffileInfo.setProjection(dataset.GetProjection());
        _tiffileInfo.setGeoTransform(dataset.GetGeoTransform());
        Band band = dataset.GetRasterBand(Func.isNotEmpty(bandIndex)?bandIndex:_tiffileInfo.getRasterCount());
        double[] _result = new double[_tiffileInfo.GetGridCount()];
        band.ReadRaster(0, 0, _tiffileInfo.getXSize(), _tiffileInfo.getYSize(), _result);
        List<Double> filteredUniqueList = null;
        if (Func.isNotEmpty(bandIndex)&&bandIndex>1){
            filteredUniqueList = Arrays.stream(_result)
                    // 对剩余元素去重
                    .distinct()
                    // 将基本类型double转为包装类Double（List只能存对象）
                    .boxed()
                    // 直接收集为List<Double>（核心修改点）
                    .collect(Collectors.toList());
        }else {
            filteredUniqueList = Arrays.stream(_result)
                    // 过滤掉9999的元素
                    .filter(num -> num != metadataEntity.getNoData().doubleValue())
                    // 对剩余元素去重
                    .distinct()
                    // 将基本类型double转为包装类Double（List只能存对象）
                    .boxed()
                    // 直接收集为List<Double>（核心修改点）
                    .collect(Collectors.toList());
        }
        _result = null;
        band.delete();
        dataset.delete();
        Collections.sort(filteredUniqueList);
        return filteredUniqueList;
    }

    @Override
    public boolean delete(String id, String user) {
        boolean bFlag = false;
        try {
            DataStyleEntity query = dataStyleMapper.selectById(id);
            DataStyleDto result = BeanUtil.copy(query, DataStyleDto.class);

            dataStyleMapper.deleteById(id);

            List<Style> styles = geoServer.getStyles(appSettingConfig.getGeoWorkspace());
            if (styles != null && styles.stream().anyMatch(s -> s.getName().equals(result.getStyleName()))) {
                geoServer.DeleteStyle(result.getStyleName(), appSettingConfig.getGeoWorkspace());
            }

            LayerContentEntity entity = layerMapper.selectOne(new LambdaQueryWrapper<LayerContentEntity>()
                    .eq(LayerContentEntity::getLayerDefaultStyle, id));
            if (entity != null) {
                entity.setLayerDefaultStyle(null);
                layerMapper.update(entity, new QueryWrapper<>());
            }
            operateLogService.writeOperateLog(id, user, 1003, 1105, 1201, 1531, null);
            bFlag = true;
        } catch (Exception e) {
            operateLogService.writeOperateLog(id, user, 1003, 1105, 1202, 1532, null);
        }
        return bFlag;
    }

    @Override
    public boolean deleteById(DataStyleDeleteDto dto) {
        boolean bFlag = false;
        try {
            DataStyleEntity query = dataStyleMapper.selectById(dto.getStyleId());
            DataStyleDto result = BeanUtil.copy(query, DataStyleDto.class);

            dataStyleMapper.deleteById(dto.getStyleId());

            List<Style> styles = geoServer.getStyles(appSettingConfig.getGeoWorkspace());
            if (styles != null && styles.stream().anyMatch(s -> s.getName().equals(result.getStyleName()))) {
                geoServer.DeleteStyle(result.getStyleName(), appSettingConfig.getGeoWorkspace());
            }

            LayerContentEntity entity = layerMapper.selectOne(new LambdaQueryWrapper<LayerContentEntity>()
                    .eq(LayerContentEntity::getLayerDefaultStyle, dto.getStyleId()));
            if (entity != null) {
                entity.setLayerDefaultStyle(null);
                layerMapper.update(entity, new QueryWrapper<>());
            }
            operateLogService.writeOperateLog(dto.getStyleId(), dto.getUserAccount(), 1003, 1105, 1201, 1531, null);
            bFlag = true;
        } catch (Exception e) {
            operateLogService.writeOperateLog(dto.getStyleId(), dto.getUserAccount(), 1003, 1105, 1202, 1532, null);
        }
        return bFlag;
    }

    @Override
    public ListResultOutput<ColorRampInfo> getRandomColorRamps() {
        try {
            ColorRampPlus colorRampPlus = new ColorRampPlus();
            List<ColorRampInfo> randomColorRamps = colorRampPlus.getRandomColorRamps(colorRampPlus.getRandomBestColorNum());
            return new ListResultOutput<>(randomColorRamps);
        } catch (Exception e) {
            log.error("获取所有随机颜色带异常！！！");
            throw new RuntimeException(e);
        }
    }

    @Override
    public ColorRampInfo getRandomColorRampsByName(String colorName, int count) {
        try {
            ColorRampPlus colorRampPlus = new ColorRampPlus();
            return colorRampPlus.getRandomColorRamp(colorName, count);
        } catch (Exception e) {
            log.error("根据颜色名称获取随机颜色带异常！！！");
            throw new RuntimeException(e);
        }
    }

    @Override
    public ListResultOutput<ColorRampInfo> getLinearColorRamps() {
        try {
            List<ColorRampInfo> colorRamps = ColorRampPlusStatic.getColorRampPlus().getLinearColorRamps(ColorRampPlusStatic.getColorRampPlus().getLinearBestColorNum());
            return new ListResultOutput<>(colorRamps);
        } catch (Exception e) {
            log.error("获取所有渐变颜色带异常！！！");
            throw new RuntimeException(e);
        }
    }

    @Override
    public ColorRampInfo getLinearColorRampsByName(String colorName, int count) {
        try {
            return ColorRampPlusStatic.getColorRampPlus().getLinearColorRamp(colorName, count);
        } catch (Exception e) {
            log.error("根据颜色名称获取渐变颜色带！！！");
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<String> getAllFolders() {
        List<String> list = new ArrayList<>();
        // todo 此处源代码被注释掉
        //list.Add(UtilityMessageConvert.Get("点图片"));
        //list.Add(UtilityMessageConvert.Get("面图片"));
        try {
            Path style = Paths.get(System.getProperty("user.dir"), "Style");
            // 创建基础目录（若不存在，递归创建父目录）
            Files.createDirectories(style);

            // 遍历子目录列表并创建
            for (String str : list) {
                Path folderPath = style.resolve(str);  // 组合子目录路径
                Files.createDirectories(folderPath);  // 创建子目录（若不存在）
            }

            // 获取基础目录下的所有直接子目录（非递归）
            List<Path> listFolder;
            try (Stream<Path> stream = Files.list(style)) {
                listFolder = stream
                        .filter(Files::isDirectory)
                        .collect(Collectors.toList());
            } catch (IOException e) {
                throw new RuntimeException("获取子目录失败: " + e.getMessage(), e);
            }

            // 处理路径，获取子目录名称（相对于基础目录）
            List<String> listF = new ArrayList<>();
            for (Path folder : listFolder) {
                // 获取子目录的名称（最后一个路径元素）
                String folderName = folder.getFileName().toString();
                listF.add(folderName);
            }
            return listF;
        } catch (IOException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    @Override
    public boolean addFolder(String folderName) {
        try {
            Path path = Paths.get(System.getProperty("user.dir"), "Style");
            Path folder = Paths.get(path.toString(), folderName);
            if (!Files.exists(folder)) {
                Files.createDirectories(folder);
                return true;
            }
        } catch (Exception e) {
            log.error("新增样式文件夹异常！！！");
        }
        return false;
    }

    @Override
    public boolean updateFolder(String oldName, String newName) {
        try {
            String path = Paths.get(System.getProperty("user.dir"), "Style").toString();
            Path folder = Paths.get(path, oldName);
            Path nFolder = Paths.get(path, newName);
            if (Files.exists(folder)) {
                Files.move(folder, nFolder);
            }
            return true;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean deleteFolder(String folderName) {
        try {
            String path = Paths.get(System.getProperty("user.dir"), "Style").toString();
            Path folder = Paths.get(path, folderName);
            if (Files.exists(folder)) {
                Files.delete(folder);
            }
            return true;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return false;
        }
    }

    @Override
    public List<DataStyleOutputImgFileInfo> getAllFiles(String folderName) {
        // todo 未获取url
        List<DataStyleOutputImgFileInfo> list = new ArrayList<>();
        try {
            Path path = Paths.get(System.getProperty("user.dir"), "Style");
            path = Paths.get(path.toString(), folderName);

            if (Files.exists(path) && Files.isDirectory(path)) {
                // 获取目录的父目录和当前目录名
                String parentDirName = path.getParent().getFileName().toString();
                String currentDirName = path.getFileName().toString();

                // 遍历目录中的所有文件（不递归）
                Files.list(path).forEach(file -> {
                    if (Files.isRegularFile(file)) {
                        String fileName = file.getFileName().toString();

                        // 构造 URL 路径
                        String httpPath = String.join("/",
                                parentDirName,
                                currentDirName,
                                fileName);

                        // 创建对象并加入列表
                        DataStyleOutputImgFileInfo info = new DataStyleOutputImgFileInfo();
                        info.setName(fileName);
                        info.setPath(httpPath);

                        list.add(info);
                    }
                });
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return list;
    }

    @Override
    public boolean updateFile(String folderName, String oldName, String newName) {
        try {
            Path path = Paths.get(System.getProperty("user.dir"), "Style");
            path = Paths.get(path.toString(), folderName);
            Path folder = Paths.get(path.toString(), oldName);
            Path nFolder = Paths.get(path.toString(), newName);
            if (Files.exists(folder) && !Files.exists(nFolder)) {
                Files.move(folder, nFolder);
                return true;
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return false;
    }

    @Override
    public boolean deleteFile(String folderName, String fileName) {
        try {
            Path path = Paths.get(System.getProperty("user.dir"), "Style");
            Path folder = Paths.get(path.toString(), folderName);

            if (Files.exists(folder)) {
                Path filePath = Paths.get(folder.toString(), fileName);
                if (Files.exists(filePath)) {
                    Files.delete(filePath);
                    return true;
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return false;
    }

    @Override
    public List<Map<String, Object>> getDataAttribute(String layerId, String layerAttr) {
        try {
            List<Map<String, Object>> list = queryAttribute(layerId, layerAttr);
            return list;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getDataAttributesPage(String layerId, String layerAttr, String colorName, String style, int PageSize, int PageIndex) {
        try {
            DataTable dataTable = new DataTable();
            DataTable dt = queryAttribute(layerId, layerAttr, colorName, style);

            if (dt != null && dt.getRowCount() > 0) {
                int skip = (PageIndex - 1) * PageSize; // 计算跳过的行数（与C#逻辑一致）
                dataTable = dt.paginate(skip, PageSize);// 调用分页方法
            }

            Map<String, Object> result = new HashMap<>();
            result.put("Count", dataTable.getRowCount());       // 总记录数
            result.put("DataTableJson", dataTable);            // 分页后的DataTable

            return new Gson().toJson(result);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getDataAttributesSort(String layerId, String layerAttr, String colorName, String style, int PageSize, int PageIndex) {
        try {
            DataTable dataTable = new DataTable();
            DataTable dt = queryAttributeSort(layerId, layerAttr, colorName, style);

            if (dt != null && dt.getRowCount() > 0) {
                int skip = (PageIndex - 1) * PageSize; // 计算跳过的行数（与C#逻辑一致）
                dataTable = dt.paginate(skip, PageSize);// 调用分页方法
            }

            // 3. 构造返回结果（总记录数+分页数据）
            Map<String, Object> result = new HashMap<>();
            result.put("Count", dataTable.getRowCount());       // 总记录数
            result.put("DataTableJson", dataTable);            // 分页后的DataTable
            return new Gson().toJson(result);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getDataAttributes(String layerId, String layerAttr, String colorName, String style) {
        try {
            DataTable dataTable = queryAttribute(layerId, layerAttr, colorName, style);
            Map<String, Object> result = new HashMap<>();
            result.put("table", dataTable);
            return new Gson().toJson(result);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Object getDataAttributesById(String styleId) {
        try {
            Map<String, PolygonStyle> dicValues = new HashMap<>();
            DataTable dt = null;
            DataStyleEntity entity = dataStyleMapper.selectById(styleId);
            if (entity != null) {
                dt = queryAttribute(entity.getStyleDefaultLayer(), entity.getStyleRenderField(), entity.getStyleRenderColorBand(), entity.getStyleRenderRule());
                if (dt != null && Func.isNotBlank(entity.getStyleContent())) {
                    StyledLayerDescriptor sld = SLDSerialize.deserialize(entity.getStyleContent(), StyledLayerDescriptor.class);

                    List<Rule> rules = getRules(sld);
                    if (!rules.isEmpty()) {
                        for (Rule rule : rules) {
                            if (rule.getFilter() != null) {
                                PropertyIsEqualTo eq = rule.getFilter().getPropertyIsEqualTo();
                                if (eq != null && entity.getStyleRenderField().equals(eq.getPropertyName())) {
                                    String value = eq.getLiteral();
                                    PolygonStyle style = getColorString(rule);
                                    if (style != null && !dicValues.containsKey(value)) {
                                        dicValues.put(value, style);
                                    }
                                }
//                                if (rule.getFilter().getPropertyIsEqualTo() != null
//                                        && rule.getFilter().getPropertyIsEqualTo().getPropertyName().equals(entity.getStyleRenderField())) {
//                                    String value = rule.getFilter().getPropertyIsEqualTo().getLiteral();
//                                    PolygonStyle polygonStyle = getColorString(rule);
//                                    if (polygonStyle != null && !dicValues.containsKey(value)) {
//                                        dicValues.put(value, polygonStyle);
//                                    }
//                                }
                            }
                        }
                    }

                    if (!dicValues.isEmpty()) {
                        for (Object[] row : dt.getDataRows()) {
                            String value = row[1].toString();
                            String style = row[0].toString();

                            if (dicValues.containsKey(value)) {
                                row[0] = JSONObject.toJSONString(dicValues.get(value));
                            }
                        }
                    }
                }
            }
            if (dt == null) {
                dt = new DataTable();
            }
            HashMap<Object, Object> map = new HashMap<>();
            map.put("table", dt);
            return new Gson().toJson(map);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getXmlContent(DataStyleInputDto input) {
        String styleContent = input.getStyleContent();
        if ("1".equals(input.getStyleConfigType()) && input.getStyleInfo() != null) {
            styleContent = GetSld(input.getStyleInfo(), input.getStyleDataType());
        }
        if ("2".equals(input.getStyleConfigType()) && input.getRuleDatas() != null) {
            styleContent = GetSld(input.getRuleDatas(), input.getStyleRenderFieldName(), input.getStyleDataType());
        }
        return FormatXml(styleContent);
    }

    @Override
    public void insertGeoServerStyle(DataStyleInputDto input) {
        if (Func.isEmpty(input.getStyleContent())) {
            return;
        }
        Path path = Paths.get(System.getProperty("user.dir"), "Style");
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create Style directory: " + path, e);
            }
        }
        Path filePath = Paths.get(path.toString(), input.getStyleName() + ".sld");
        try {
            Files.write(filePath, input.getStyleContent().getBytes(Charset.forName("GB2312")));
        } catch (IOException e) {
            throw new RuntimeException("Failed to write file: " + filePath, e);
        }
        List<Style> styles = geoServer.getStyles(appSettingConfig.getGeoWorkspace());
        if (styles != null && styles.stream().anyMatch(s -> s.getName().equals(input.getStyleName()))) {
            geoServer.modifyStyle(input.getStyleName(), filePath.toString(), appSettingConfig.getGeoWorkspace());
        } else {
            geoServer.addStyle(input.getStyleName(), filePath.getFileName().toString(), filePath.toString(), appSettingConfig.getGeoWorkspace());
        }
    }

    @Override
    public void insertStyle(String id, String styleName, String styleContent) {
        if (Func.isEmpty(styleContent)) {
            return;
        }
        Path path = Paths.get(System.getProperty("user.dir"), "Style");
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create Style directory: " + path, e);
            }
        }
        Path filePath = Paths.get(path.toString(), styleName + ".sld");
        try {
            LayerContentEntity entity = layerMapper.selectById(id);
            String encoding = "GBK";
            if (entity != null) {
                if (Func.isNotEmpty(entity.getFileencoding())){
                    encoding = entity.getFileencoding();
                }else {
                    encoding = detectShpEncoding(entity.getUploadFileName());
                }
            }
            if (encoding.equals("GBK")) {
                log.info("<gbk===styleContent:{}", styleContent);
                Charset gbk = Charset.forName("GBK");
                log.info("GBK:{}", gbk);
                Files.write(filePath, styleContent.getBytes(Charset.forName("GBK")));
            } else if (encoding.equals("UTF-8")) {
                log.info("<UTF-8===styleContent:{}", styleContent);
                Charset utf8 = StandardCharsets.UTF_8;
                log.info("UTF-8:{}", utf8);
                Files.write(filePath, styleContent.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write file: " + filePath, e);
        }
        List<Style> styles = geoServer.getStyles(appSettingConfig.getGeoWorkspace());
        if (styles != null && styles.stream().anyMatch(s -> s.getName().equals(styleName))) {
            geoServer.modifyStyle(styleName, filePath.toString(), appSettingConfig.getGeoWorkspace());
        } else {
            geoServer.addStyle(styleName, filePath.getFileName().toString(), filePath.toString(), appSettingConfig.getGeoWorkspace());
        }
    }

    private String detectShpEncoding(String shpPath) {
        // 1. 优先检查 .cpg 文件（Shapefile 官方编码声明）
        String cpgPath = shpPath.replaceAll("\\.shp$", ".cpg");
        File cpgFile = new File(cpgPath);
        if (cpgFile.exists()) {
            try {
                String enc = Files.readString(cpgFile.toPath(), StandardCharsets.ISO_8859_1)
                        .trim()
                        .toUpperCase();
                // 标准化常见编码名
                if (enc.equals("UTF8") || enc.equals("UTF-8")) return "UTF-8";
                if (enc.equals("GBK") || enc.equals("GB2312") || enc.equals("ANSI 936")) return "GBK";
                return enc; // 其他编码直接返回
            } catch (Exception e) {
                log.warn("无法读取 .cpg 文件: {}", cpgPath, e);
            }
        }

        // 2. 无 .cpg 时，默认使用 GBK
        return "GBK";
    }


    @Override
    public DataStyleOutputDto parsingSld(String id) {
        try {
            List<RuleData> temp = new ArrayList<>();
            List<String> dc = null;
            DataStyleEntity entity = dataStyleMapper.selectById(id);
            DataStyleDto tc = BeanUtil.copy(entity, DataStyleDto.class);

            if (tc != null) {
                temp = getRuleDatas(tc.getStyleContent(), tc.getStyleRenderField(), null);
                if (StrUtil.isNotBlank(tc.getSymbol())) {
                    dc = List.of(tc.getSymbol().split(","));
                }
            }

            DataStyleOutputDto bk = new DataStyleOutputDto();
            bk.setSymbolRulesdata(dc);
            bk.setSymbol(tc.getSymbol());
            bk.setStyleType(tc.getStyleType());
            bk.setStyleConfigType(tc.getStyleConfigType());
            bk.setStyleContent(tc.getStyleContent());
            bk.setRuleDatas(temp);
            bk.setStyleName(tc.getStyleName());
            bk.setStyleDefaultLayerName(tc.getStyleDefaultLayer());
            bk.setId(tc.getId());
            bk.setStyleDesc(tc.getStyleDesc());
            bk.setStyleRenderColorBand(tc.getStyleRenderColorBand());
            bk.setStyleDataType(tc.getStyleDataType());
            bk.setStyleRenderField(tc.getStyleRenderField());
            bk.setStyleTypeName(tc.getStyleTypeName());
            bk.setCreateBy(tc.getCreateBy());
            bk.setCreateDT(tc.getCreateDT());
            bk.setStyleDefaultLayer(tc.getStyleDefaultLayer());
            return bk;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<RuleData> parsethefile(String path) {
        try {
            File file = new File(path);
            if (file.exists()) {
                // 读取文件内容并清理空白字符
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                String cleanedContent = content.replaceAll("\\s+", "");

                // 调用处理方法获取规则数据
                return getRuleDatas(cleanedContent, null, null);
            } else {
                return null;
            }
        } catch (IOException e) {
            log.error("读取文件异常！");
        } catch (Exception e) {
            log.error("处理过程中发生异常！");
        }
        return null;
    }


    private String FormatXml(String sUnformattedXml) {
        try {
            // 1. 加载XML字符串为Document对象
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(sUnformattedXml)));

            // 2. 配置Transformer（格式化参数）
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();

            // 设置输出属性：缩进、制表符、编码
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");       // 启用缩进
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "1");  // 缩进空格数（部分实现可能需要）
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");    // 编码
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");  // 保留XML声明

            // 3. 将Document转换为格式化后的字符串
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));

            return writer.toString();

        } catch (Exception e) {
            // 异常处理（可根据需求记录日志或抛出自定义异常）
            throw new RuntimeException("XML格式化失败: " + e.getMessage(), e);
        }
    }


    private DataTable queryAttributeSort(String layerId, String layerAttr, String colorName, String style) {
        DataTable dt = null;

        LayerContentEntity layerContent = layerMapper.selectById(layerId);
        LayerFieldEntity layerField = layerFieldMapper.selectOne(new LambdaQueryWrapper<LayerFieldEntity>()
                .eq(LayerFieldEntity::getLayerID, layerId)
                .eq(LayerFieldEntity::getAttributeName, layerAttr));
        if (layerContent != null && layerField != null && StrUtil.isNotBlank(layerContent.getLayerAttrTable())) {
            try {
                // 3. 构造SQL查询（PostgreSQL语法，字段用双引号）
                String sql = String.format(
                        "SELECT CAST(\"%s\" AS CHAR) AS symbol, \"%s\" AS value, \"%s\" AS note, COUNT(*) AS count " +
                                "FROM %s GROUP BY \"%s\"",
                        layerAttr, layerAttr, layerAttr, layerContent.getLayerAttrTable(), layerAttr
                );
                // 4. 执行SQL查询
                List<Map<String, Object>> queryResult = dynamicTableMapper.dynamicExecute(sql);

                // 5. 转换为DataTable
                if (!queryResult.isEmpty()) {
                    // 定义列名和类型
                    String[] columnNames = {"符号", "值", "注记", "个数"};
                    Class<?>[] columnTypes = {String.class, String.class, String.class, Integer.class};

                    // 初始化DataTable
                    dt = new DataTable(columnNames, 0);
                    dt.setColumnTypes(columnTypes);
                    dt.sortByColumn("值");

                    // 填充数据
                    for (Map<String, Object> row : queryResult) {
                        Object[] data = new Object[columnNames.length];
                        for (int i = 0; i < columnNames.length; i++) {
                            data[i] = row.get(columnNames[i]);
                        }
                        dt.addRow(data);
                    }

                    // 6. 处理颜色和样式
                    if (dt.getRowCount() > 0) {
                        // 设置第一列为字符串类型（最大长度无限制）
                        dt.setColumnType(0, String.class);
                        dt.setColumnMaxLength(0, Integer.MAX_VALUE);

                        PolygonStyle pStyle = new PolygonStyle();
                        Gson gson = new Gson();
                        if (StrUtil.isNotBlank(style)) {
                            // 兼容 PascalCase（Gson @SerializedName）与 camelCase 两种入参；
                            // 空串时保持默认样式（原实现 gson.fromJson("") 返回 null 会 NPE）
                            try {
                                pStyle = JsonUtils.fromJson(style, PolygonStyle.class);
                            } catch (Exception e) {
                                pStyle = gson.fromJson(style, PolygonStyle.class);
                            }
                            if (pStyle == null) pStyle = new PolygonStyle();
                        }

                        // 获取颜色条带
                        ColorRampInfo colorRamp = ColorRampPlusStatic.getColorRampPlus().getRampInfo(colorName, dt.getRowCount());

                        // 应用颜色到每行
                        if (colorRamp != null) {
                            for (int i = 0; i < dt.getRowCount(); i++) {
                                int colorIndex = i % colorRamp.getCount();
                                Color color = colorRamp.getColorList().get(colorIndex);
                                pStyle.setPolygonColor(String.format("%d,%d,%d", color.getRed(), color.getGreen(), color.getBlue()));
                                pStyle.setOutlineColor(pStyle.getPolygonColor());

                                // 序列化样式到JSON并设置到第一列
                                String styleJson = gson.toJson(pStyle);
                                dt.setValueAt(styleJson, i, 0);
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                log.error("查询属性失败: {}", ex.getMessage(), ex);
            }

        }
        return dt;
    }

    private List<Map<String, Object>> queryAttribute(String layerId, String layerAttr) {
        LayerContentEntity layerContent = layerMapper.selectById(layerId);
        LayerFieldEntity layerField = layerFieldMapper.selectOne(new LambdaQueryWrapper<LayerFieldEntity>()
                .eq(LayerFieldEntity::getLayerID, layerId)
                .eq(LayerFieldEntity::getAttributeName, layerAttr));
        if (layerContent != null && layerField != null && Func.isNotBlank(layerContent.getLayerAttrTable())) {
            try {
                // 3. 构造SQL查询（PostgreSQL语法，字段用双引号）
                layerAttr = layerAttr.toLowerCase();
                String sql = String.format(
                        "SELECT \"%s\" AS symbol, \"%s\" AS value, \"%s\" AS note, COUNT(*) AS count " +
                                "FROM %s GROUP BY \"%s\"",
                        layerAttr, layerAttr, layerAttr, layerContent.getLayerAttrTable(), layerAttr
                );
                // 4. 执行SQL查询
                List<Map<String, Object>> queryResult = dynamicTableMapper.dynamicExecute(sql);
                return queryResult;
            } catch (Exception ex) {
                log.error("查询属性失败: {}", ex.getMessage(), ex);
            }
        }
        return null;
    }


    private DataTable queryAttribute(String layerId, String layerAttr, String colorName, String style) {
        DataTable dt = null;

        LayerContentEntity layerContent = layerMapper.selectById(layerId);
        LayerFieldEntity layerField = layerFieldMapper.selectOne(new LambdaQueryWrapper<LayerFieldEntity>()
                .eq(LayerFieldEntity::getLayerID, layerId)
                .eq(LayerFieldEntity::getAttributeName, layerAttr));
        if (layerContent != null && layerField != null && Func.isNotBlank(layerContent.getLayerAttrTable())) {
            try {
                // 3. 构造SQL查询（PostgreSQL语法，字段用双引号）
                String sql = String.format(
                        "SELECT CAST(\"%s\" AS CHAR) AS symbol, \"%s\" AS value, \"%s\" AS note, COUNT(*) AS count " +
                                "FROM %s GROUP BY \"%s\"",
                        layerAttr, layerAttr, layerAttr, layerContent.getLayerAttrTable(), layerAttr
                );
                // 4. 执行SQL查询
                List<Map<String, Object>> queryResult = dynamicTableMapper.dynamicExecute(sql);

                // 5. 转换为DataTable
                if (!queryResult.isEmpty()) {
                    // 定义列名和类型
                    String[] columnNames = {"符号", "值", "标记", "个数"};
                    Class<?>[] columnTypes = {String.class, String.class, String.class, Integer.class};

                    // 初始化DataTable
                    dt = new DataTable(columnNames, 0);
                    dt.setColumnTypes(columnTypes);

                    // 填充数据
                    for (Map<String, Object> row : queryResult) {
                        Object[] data = new Object[columnNames.length];
                        for (int i = 0; i < columnNames.length; i++) {
                            data[i] = row.get(columnNames[i]);
                        }
                        dt.addRow(data);
                    }

                    // 6. 处理颜色和样式
                    if (dt.getRowCount() > 0) {
                        // 设置第一列为字符串类型（最大长度无限制）
                        dt.setColumnType(0, String.class);
                        dt.setColumnMaxLength(0, Integer.MAX_VALUE);

                        PolygonStyle pStyle = new PolygonStyle();
                        Gson gson = new Gson();
                        if (StrUtil.isNotBlank(style)) {
                            // 兼容 PascalCase（Gson @SerializedName）与 camelCase 两种入参；
                            // 空串时保持默认样式（原实现 gson.fromJson("") 返回 null 会 NPE）
                            try {
                                pStyle = JsonUtils.fromJson(style, PolygonStyle.class);
                            } catch (Exception e) {
                                pStyle = gson.fromJson(style, PolygonStyle.class);
                            }
                            if (pStyle == null) pStyle = new PolygonStyle();
                        }

                        // 获取颜色条带
                        ColorRampInfo colorRamp = ColorRampPlusStatic.getColorRampPlus().getRampInfo(colorName, dt.getRowCount());

                        // 应用颜色到每行
                        if (colorRamp != null) {
                            for (int i = 0; i < dt.getRowCount(); i++) {
                                int colorIndex = i % colorRamp.getCount();
                                Color color = colorRamp.getColorList().get(colorIndex);
                                pStyle.setPolygonColor(String.format("%d,%d,%d", color.getRed(), color.getGreen(), color.getBlue()));
                                pStyle.setOutlineColor(pStyle.getPolygonColor());

                                // 序列化样式到JSON并设置到第一列
                                String styleJson = gson.toJson(pStyle);
                                dt.setValueAt(styleJson, i, 0);
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                log.error("查询属性失败: {}", ex.getMessage(), ex);
            }
        }
        return dt;
    }

    private String GetSld(List<RuleData> ruleDatas, String columnName, String type) {
        StyledLayerDescriptor sld = new StyledLayerDescriptor();
        UserLayer ul = new UserLayer();
        UserStyle us = new UserStyle();
        FeatureTypeStyle fts = new FeatureTypeStyle();
        fts.setFeatureTypeName("Feature");
        fts.setSemanticTypeIdentifiers(Arrays.asList("generic:geometry", "simple"));

        List<Rule> ruleList = new ArrayList<>();

        int i = 0;
        for (RuleData rData : ruleDatas) {
            Rule rule = new Rule();
            rule.setName("rule" + (i++));
            rule.setCount(rData.getCount());

//            Gson gson = new Gson();
//            PolygonStyle pstyle = gson.fromJson(rData.getPolygonStyle(), PolygonStyle.class);
            PolygonStyle pstyle = JSONObject.parseObject(rData.getPolygonStyle(), PolygonStyle.class);
//            PolygonStyle pstyle = JsonUtils.fromJson(rData.getPolygonStyle(), PolygonStyle.class);
            if (pstyle != null) {
                if (type.equals(DictionaryIds.GEOM_POINT)) {
                    Symbolizer.PointSymbolizer ps = GetPointSymbolizer(pstyle);
                    rule.setPointSymbolizer(ps);
                } else if (type.equals(DictionaryIds.GEOM_LINE)) {
                    if (Func.isNotEmpty(pstyle.getLineType()) && pstyle.getLineType() == 4) {
                        Symbolizer.LineSymbolizer ls2 = GetDashLine(pstyle);
                        rule.setLineSymbolizer(List.of(ls2));
                    } else {
                        Symbolizer.LineSymbolizer ls = GetLineSymbolizer(pstyle);
                        rule.setLineSymbolizer(List.of(ls));
                    }
                } else {
                    Symbolizer.PolygonSymbolizer ps = GetPolygonSymbolizer(pstyle);
                    rule.setPolygonSymbolizer(ps);
                }
            }
            if (Func.isNotBlank(rData.getTitle())) {
                rule.setTitle(rData.getTitle());
            }
            if (Func.isNotBlank(rData.getValue())) {
                String propName = null;
                if (Func.isNotBlank(columnName)) {
                    propName = columnName;
                } else if (Func.isNotBlank(rData.getTitle())) {
                    propName = rData.getTitle();
                }
                
                if (Func.isNotBlank(propName)) {
                    Filter filter = new Filter();
                    String operator = rData.getOperator();
                    
//                    // 处理转义字符
//                    if (operator != null) {
//                        operator = operator.replace("&gt;", ">").replace("&lt;", "<");
//                    }
                    
                    if ("EQ".equals(operator) || Func.isBlank(operator)) {//=
                        // 默认使用等于操作符
                        filter.setPropertyIsEqualTo(new PropertyIsEqualTo(propName, rData.getValue()));
                    } else if ("GT".equals(operator)) {//>
                        PropertyIsGreaterThan greaterThan = new PropertyIsGreaterThan();
                        Function greaterThanFunction = new Function();
                        greaterThanFunction.setName("parseDouble");
                        greaterThanFunction.setPropertyName(propName);
                        greaterThan.setFunction(greaterThanFunction);
                        greaterThan.setLiteral(rData.getValue());
                        filter.setPropertyIsGreaterThan(greaterThan);
                    } else if ("GTE".equals(operator)) {//>=
                        PropertyIsGreaterThanOrEqualTo greaterThanOrEqualTo = new PropertyIsGreaterThanOrEqualTo();
                        Function greaterThanOrEqualToFunction = new Function();
                        greaterThanOrEqualToFunction.setName("parseDouble");
                        greaterThanOrEqualToFunction.setPropertyName(propName);
                        greaterThanOrEqualTo.setFunction(greaterThanOrEqualToFunction);
                        greaterThanOrEqualTo.setLiteral(rData.getValue());
                        filter.setPropertyIsGreaterThanOrEqualTo(greaterThanOrEqualTo);
                    } else if ("LT".equals(operator)) {//<
                        PropertyIsLessThan lessThan = new PropertyIsLessThan();
                        Function lessThanFunction = new Function();
                        lessThanFunction.setName("parseDouble");
                        lessThanFunction.setPropertyName(propName);
                        lessThan.setFunction(lessThanFunction);
                        lessThan.setLiteral(rData.getValue());
                        filter.setPropertyIsLessThan(lessThan);
                    } else if ("LTE".equals(operator)) {//<=
                        PropertyIsLessThanOrEqualTo lessThanOrEq = new PropertyIsLessThanOrEqualTo();
                        Function lessThanOrEqFunction = new Function();
                        lessThanOrEqFunction.setName("parseDouble");
                        lessThanOrEqFunction.setPropertyName(propName);
                        lessThanOrEq.setFunction(lessThanOrEqFunction);
                        lessThanOrEq.setLiteral(rData.getValue());
                        filter.setPropertyIsLessThanOrEqualTo(lessThanOrEq);
                    } else if ("范围值".equals(operator)) {
                        // 范围过滤，前开后闭 (valueOne, value]
                        if (StrUtil.isNotBlank(rData.getValueOne()) && StrUtil.isNotBlank(rData.getValue())) {
                            // 创建 And 条件
                            And and = new And();
                            
                            // 大于 valueOne
                            PropertyIsGreaterThan greaterThan = new PropertyIsGreaterThan();
                            Function greaterThanFunction = new Function();
                            greaterThanFunction.setName("parseDouble");
                            greaterThanFunction.setPropertyName(propName);
                            greaterThan.setFunction(greaterThanFunction);
                            greaterThan.setLiteral(rData.getValueOne());
                            and.setPropertyIsGreaterThan(greaterThan);
                            
                            // 小于等于 value
                            PropertyIsLessThanOrEqualTo lessThanOrEq = new PropertyIsLessThanOrEqualTo();
                            Function lessThanOrEqFunction = new Function();
                            lessThanOrEqFunction.setName("parseDouble");
                            lessThanOrEqFunction.setPropertyName(propName);
                            lessThanOrEq.setFunction(lessThanOrEqFunction);
                            lessThanOrEq.setLiteral(rData.getValue());
                            and.setPropertyIsLessThanOrEqualTo(lessThanOrEq);
                            
                            filter.setAnd(and);
                        }
                    }
                    
                    rule.setFilter(filter);
                }
            }
//            String value = rData.getValue();
//            String propName = null;
//
//            if (Func.isNotBlank(value)) {
//                if (Func.isNotBlank(columnName)) {
//                    propName = columnName;
//                } else if (Func.isNotBlank(rData.getTitle())) {
//                    propName = rData.getTitle();
//                }
//            }
//
//            if (propName != null) {
//                Filter filter = new Filter();
//                filter.setPropertyIsEqualTo(new PropertyIsEqualTo(propName, value));
//                rule.setFilter(filter);
//            }
            ruleList.add(rule);
        }
        fts.setRules(ruleList);
        us.setFeatureTypeStyles(List.of(fts));
        ul.setUserStyles(List.of(us));
        sld.setUserLayers(List.of(ul));
        return SLDSerialize.serialize(sld);
    }

    private String GetTifSld2(List<TifStyle> tifStyles, String layername,boolean isHaveNoData,Double nodata,double maxVue){
        StyledLayerDescriptorTif styled = new StyledLayerDescriptorTif();
        NamedLayer namedLayer = new NamedLayer();
        namedLayer.setName(layername);

        UserStyle userStyle = new UserStyle();
        userStyle.setName(layername);
        userStyle.setTitle(layername);
        userStyle.setAbstractDesc(layername);

        FeatureTypeStyle featureTypeStyle = new FeatureTypeStyle();
        ArrayList<Rule> ruleList = new ArrayList<>();
        Rule rule = new Rule();
        Symbolizer.RasterSymbolizer raster = new Symbolizer.RasterSymbolizer();
        Symbolizer.ColorMap colorMap = new Symbolizer.ColorMap();
        ArrayList<Symbolizer.ColorMapEntry> colorMapEntries = new ArrayList<>();

        if (StrUtil.isNotBlank(tifStyles.get(0).getFillWay())) {
            colorMap.setType(tifStyles.get(0).getFillWay());
        }
        for (TifStyle tifStyle : tifStyles) {
            if (StrUtil.isNotBlank(tifStyle.getQuantityOne()) && StrUtil.isNotBlank(tifStyle.getQuantity())) {
                // 处理范围样式
                // 生成一个 ColorMapEntry，设置为结束值
                Symbolizer.ColorMapEntry colorMapEntry = new Symbolizer.ColorMapEntry();
                colorMapEntry.setColor("#FFFFFF");
                colorMapEntry.setLabel(tifStyle.getLabel());
                colorMapEntry.setOpacity("0");
                colorMapEntry.setQuantity(tifStyle.getQuantity());
                if (StrUtil.isNotBlank(tifStyle.getColor())) {
                    colorMapEntry.setColor(ColorRGBtoHx16(tifStyle.getColor()));
                }
                if (StrUtil.isNotBlank(tifStyle.getOpacity())) {
                    colorMapEntry.setOpacity(tifStyle.getOpacity());
                }
                colorMapEntries.add(colorMapEntry);
            } else {
                // 传统方式，只生成一个 ColorMapEntry
                Symbolizer.ColorMapEntry colorMapEntry = new Symbolizer.ColorMapEntry();
                colorMapEntry.setColor("#FFFFFF");
                colorMapEntry.setLabel("label");
                colorMapEntry.setOpacity("0");
                colorMapEntry.setQuantity("0");
                if (StrUtil.isNotBlank(tifStyle.getColor())) {
                    colorMapEntry.setColor(ColorRGBtoHx16(tifStyle.getColor()));
                }
                if (StrUtil.isNotBlank(tifStyle.getLabel())) {
                    colorMapEntry.setLabel(tifStyle.getLabel());
                }
                if (StrUtil.isNotBlank(tifStyle.getOpacity())) {
                    colorMapEntry.setOpacity(tifStyle.getOpacity());
                }
                if (StrUtil.isNotBlank(tifStyle.getQuantity())) {
                    colorMapEntry.setQuantity(tifStyle.getQuantity());
                }
                colorMapEntries.add(colorMapEntry);
            }
        }
        
        // 确保最小值也有对应的颜色
        // 检查是否有 quantityOne 小于所有 quantity 的情况
        if (!colorMapEntries.isEmpty() && !tifStyles.isEmpty()) {
            // 找到最小的 quantityOne
            String minQuantityOne = null;
            for (TifStyle tifStyle : tifStyles) {
                if (StrUtil.isNotBlank(tifStyle.getQuantityOne())) {
                    if (minQuantityOne == null || Double.parseDouble(tifStyle.getQuantityOne()) < Double.parseDouble(minQuantityOne)) {
                        minQuantityOne = tifStyle.getQuantityOne();
                    }
                }
            }
            
            // 如果存在最小的 quantityOne，并且没有比它更小的 ColorMapEntry，则添加一个
            if (minQuantityOne != null) {
                double minValue = Double.parseDouble(minQuantityOne);
                boolean hasSmallerEntry = false;
                for (Symbolizer.ColorMapEntry entry : colorMapEntries) {
                    if (StrUtil.isNotBlank(entry.getQuantity())) {
                        try {
                            if (Double.parseDouble(entry.getQuantity()) < minValue) {
                                hasSmallerEntry = true;
                                break;
                            }
                        } catch (NumberFormatException e) {
                            // 忽略非数字的 quantity
                        }
                    }
                }
                
                // 如果没有比 minValue 更小的 ColorMapEntry，添加一个
                if (!hasSmallerEntry) {
                    // 找到对应的 TifStyle
                    TifStyle firstStyle = null;
                    for (TifStyle tifStyle : tifStyles) {
                        if (tifStyle.getQuantityOne() != null && tifStyle.getQuantityOne().equals(minQuantityOne)) {
                            firstStyle = tifStyle;
                            break;
                        }
                    }
                    
                    if (firstStyle != null) {
                        Symbolizer.ColorMapEntry minEntry = new Symbolizer.ColorMapEntry();
                        minEntry.setColor("#FFFFFF");
                        minEntry.setLabel(firstStyle.getLabel());
                        minEntry.setOpacity("0");
                        minEntry.setQuantity(minQuantityOne);
                        if (StrUtil.isNotBlank(firstStyle.getColor())) {
                            minEntry.setColor(ColorRGBtoHx16(firstStyle.getColor()));
                        }
                        if (StrUtil.isNotBlank(firstStyle.getOpacity())) {
                            minEntry.setOpacity(firstStyle.getOpacity());
                        }
                        // 添加到最前面，确保它是第一个
                        colorMapEntries.add(0, minEntry);
                    }
                }
            }
        }
        if (isHaveNoData) {
            if (nodata > 0) {
                Symbolizer.ColorMapEntry colorMapEntry = new Symbolizer.ColorMapEntry();
                colorMapEntry.setColor("#000000");
                colorMapEntry.setLabel("nodata");
                colorMapEntry.setOpacity("0.0");
                colorMapEntry.setQuantity(nodata.toString());
                colorMapEntries.add(colorMapEntry);
            } else if (nodata < maxVue) {
                Symbolizer.ColorMapEntry colorMapEntry = new Symbolizer.ColorMapEntry();
                colorMapEntry.setColor("#000000");
                colorMapEntry.setLabel("nodata");
                colorMapEntry.setOpacity("0.0");
                colorMapEntry.setQuantity(nodata.toString());
                colorMapEntries.add(0, colorMapEntry);
            } else if (nodata != 0) {
                Symbolizer.ColorMapEntry colorMapEntry = new Symbolizer.ColorMapEntry();
                colorMapEntry.setColor("#000000");
                colorMapEntry.setLabel("nodata");
                colorMapEntry.setOpacity("0.0");
                colorMapEntry.setQuantity(nodata.toString());
                colorMapEntries.add(1, colorMapEntry);
            }
        }
        colorMap.setColorMapEntry(colorMapEntries);
        raster.setOpacity("1.0");
        raster.setColorMap(colorMap);
        rule.setRasterSymbolizer(raster);
        ruleList.add(rule);
        featureTypeStyle.setRules(ruleList);
        userStyle.setFeatureTypeStyles(new ArrayList<>(Arrays.asList(featureTypeStyle)));
        namedLayer.setUserStyles(new ArrayList<>(Arrays.asList(userStyle)));
        styled.setNamedLayers(new ArrayList<>(Arrays.asList(namedLayer)));
        return SLDSerialize.serialize(styled);
    }

    /**
     * 生成多波段TIFF样式（选择特定波段）
     * @param tifStyles 样式配置
     * @param layername 图层名称
     * @param isHaveNoData 是否有NoData值
     * @param nodata NoData值
     * @param maxVue 最大值
     * @param bandIndex 波段索引（从1开始）
     * @return SLD样式字符串
     */
    private String GetMultiBandTifSld(List<TifStyle> tifStyles, String layername, boolean isHaveNoData, Double nodata, double maxVue, int bandIndex) {
        StyledLayerDescriptorTif styled = new StyledLayerDescriptorTif();
        NamedLayer namedLayer = new NamedLayer();
        namedLayer.setName(layername);

        UserStyle userStyle = new UserStyle();
        userStyle.setName(layername);
        userStyle.setTitle(layername);
        userStyle.setAbstractDesc(layername);

        FeatureTypeStyle featureTypeStyle = new FeatureTypeStyle();
        ArrayList<Rule> ruleList = new ArrayList<>();
        Rule rule = new Rule();
        Symbolizer.RasterSymbolizer raster = new Symbolizer.RasterSymbolizer();
        
        // 设置波段选择
        Symbolizer.ChannelSelection channelSelection = new Symbolizer.ChannelSelection();
        Symbolizer.GrayChannel grayChannel = new Symbolizer.GrayChannel();
        grayChannel.setSourceChannelName(String.valueOf(bandIndex));
        channelSelection.setGrayChannel(grayChannel);
        raster.setChannelSelection(channelSelection);
        
        // 添加 ContrastEnhancement
        Symbolizer.ContrastEnhancement contrastEnhancement = new Symbolizer.ContrastEnhancement();
        raster.setContrastEnhancement(contrastEnhancement);

        // 设置颜色映射
        Symbolizer.ColorMap colorMap = new Symbolizer.ColorMap();
        ArrayList<Symbolizer.ColorMapEntry> colorMapEntries = new ArrayList<>();

        if (StrUtil.isNotBlank(tifStyles.get(0).getFillWay())) {
            colorMap.setType(tifStyles.get(0).getFillWay());
        }
        for (TifStyle tifStyle : tifStyles) {
            if (StrUtil.isNotBlank(tifStyle.getQuantityOne()) && StrUtil.isNotBlank(tifStyle.getQuantity())) {
                // 处理范围样式
                // 生成一个 ColorMapEntry，设置为结束值
                Symbolizer.ColorMapEntry colorMapEntry = new Symbolizer.ColorMapEntry();
                colorMapEntry.setColor("#FFFFFF");
                colorMapEntry.setLabel(tifStyle.getLabel());
                colorMapEntry.setOpacity("0");
                colorMapEntry.setQuantity(tifStyle.getQuantity());
                if (StrUtil.isNotBlank(tifStyle.getColor())) {
                    colorMapEntry.setColor(ColorRGBtoHx16(tifStyle.getColor()));
                }
                if (StrUtil.isNotBlank(tifStyle.getOpacity())) {
                    colorMapEntry.setOpacity(tifStyle.getOpacity());
                }
                colorMapEntries.add(colorMapEntry);
            } else {
                // 传统方式，只生成一个 ColorMapEntry
                Symbolizer.ColorMapEntry colorMapEntry = new Symbolizer.ColorMapEntry();
                colorMapEntry.setColor("#FFFFFF");
                colorMapEntry.setLabel("label");
                colorMapEntry.setOpacity("0");
                colorMapEntry.setQuantity("0");
                if (StrUtil.isNotBlank(tifStyle.getColor())) {
                    colorMapEntry.setColor(ColorRGBtoHx16(tifStyle.getColor()));
                }
                if (StrUtil.isNotBlank(tifStyle.getLabel())) {
                    colorMapEntry.setLabel(tifStyle.getLabel());
                }
                if (StrUtil.isNotBlank(tifStyle.getOpacity())) {
                    colorMapEntry.setOpacity(tifStyle.getOpacity());
                }
                if (StrUtil.isNotBlank(tifStyle.getQuantity())) {
                    colorMapEntry.setQuantity(tifStyle.getQuantity());
                }
                colorMapEntries.add(colorMapEntry);
            }
        }
        
        // 确保最小值也有对应的颜色
        // 检查是否有 quantityOne 小于所有 quantity 的情况
        if (!colorMapEntries.isEmpty() && !tifStyles.isEmpty()) {
            // 找到最小的 quantityOne
            String minQuantityOne = null;
            for (TifStyle tifStyle : tifStyles) {
                if (StrUtil.isNotBlank(tifStyle.getQuantityOne())) {
                    if (minQuantityOne == null || Double.parseDouble(tifStyle.getQuantityOne()) < Double.parseDouble(minQuantityOne)) {
                        minQuantityOne = tifStyle.getQuantityOne();
                    }
                }
            }
            
            // 如果存在最小的 quantityOne，并且没有比它更小的 ColorMapEntry，则添加一个
            if (minQuantityOne != null) {
                double minValue = Double.parseDouble(minQuantityOne);
                boolean hasSmallerEntry = false;
                for (Symbolizer.ColorMapEntry entry : colorMapEntries) {
                    if (StrUtil.isNotBlank(entry.getQuantity())) {
                        try {
                            if (Double.parseDouble(entry.getQuantity()) < minValue) {
                                hasSmallerEntry = true;
                                break;
                            }
                        } catch (NumberFormatException e) {
                            // 忽略非数字的 quantity
                        }
                    }
                }
                
                // 如果没有比 minValue 更小的 ColorMapEntry，添加一个
                if (!hasSmallerEntry) {
                    // 找到对应的 TifStyle
                    TifStyle firstStyle = null;
                    for (TifStyle tifStyle : tifStyles) {
                        if (tifStyle.getQuantityOne() != null && tifStyle.getQuantityOne().equals(minQuantityOne)) {
                            firstStyle = tifStyle;
                            break;
                        }
                    }
                    
                    if (firstStyle != null) {
                        Symbolizer.ColorMapEntry minEntry = new Symbolizer.ColorMapEntry();
                        minEntry.setColor("#FFFFFF");
                        minEntry.setLabel(firstStyle.getLabel());
                        minEntry.setOpacity("0");
                        minEntry.setQuantity(minQuantityOne);
                        if (StrUtil.isNotBlank(firstStyle.getColor())) {
                            minEntry.setColor(ColorRGBtoHx16(firstStyle.getColor()));
                        }
                        if (StrUtil.isNotBlank(firstStyle.getOpacity())) {
                            minEntry.setOpacity(firstStyle.getOpacity());
                        }
                        // 添加到最前面，确保它是第一个
                        colorMapEntries.add(0, minEntry);
                    }
                }
            }
        }
        if (isHaveNoData) {
            if (nodata > 0) {
                Symbolizer.ColorMapEntry colorMapEntry = new Symbolizer.ColorMapEntry();
                colorMapEntry.setColor("#000000");
                colorMapEntry.setLabel("nodata");
                colorMapEntry.setOpacity("0.0");
                colorMapEntry.setQuantity(nodata.toString());
                colorMapEntries.add(colorMapEntry);
            } else if (nodata < maxVue) {
                Symbolizer.ColorMapEntry colorMapEntry = new Symbolizer.ColorMapEntry();
                colorMapEntry.setColor("#000000");
                colorMapEntry.setLabel("nodata");
                colorMapEntry.setOpacity("0.0");
                colorMapEntry.setQuantity(nodata.toString());
                colorMapEntries.add(0, colorMapEntry);
            } else if (nodata != 0) {
                Symbolizer.ColorMapEntry colorMapEntry = new Symbolizer.ColorMapEntry();
                colorMapEntry.setColor("#000000");
                colorMapEntry.setLabel("nodata");
                colorMapEntry.setOpacity("0.0");
                colorMapEntry.setQuantity(nodata.toString());
                colorMapEntries.add(1, colorMapEntry);
            }
        }
        colorMap.setColorMapEntry(colorMapEntries);
        raster.setOpacity("1.0");
        raster.setColorMap(colorMap);
        rule.setRasterSymbolizer(raster);
        ruleList.add(rule);
        featureTypeStyle.setRules(ruleList);
        userStyle.setFeatureTypeStyles(new ArrayList<>(Arrays.asList(featureTypeStyle)));
        namedLayer.setUserStyles(new ArrayList<>(Arrays.asList(userStyle)));
        styled.setNamedLayers(new ArrayList<>(Arrays.asList(namedLayer)));
        return SLDSerialize.serialize(styled);
    }

    private String GetTifSld(List<TifStyle> tifStyles, String layername, String layerid) {
        MetadataEntity meta = null;
        DataInformationEntity dataInfo = dataInformationMapper.selectById(layerid);
        if (dataInfo != null) {
            meta = metadataMapper.selectOne(new LambdaQueryWrapper<MetadataEntity>()
                    .eq(MetadataEntity::getDataId, dataInfo.getId()));
        }

        StyledLayerDescriptorTif styled = new StyledLayerDescriptorTif();
        NamedLayer namedLayer = new NamedLayer();
        namedLayer.setName(layername);

        UserStyle userStyle = new UserStyle();
        userStyle.setName(layername);
        userStyle.setTitle(layername);
        userStyle.setAbstractDesc(layername);

        FeatureTypeStyle featureTypeStyle = new FeatureTypeStyle();
        ArrayList<Rule> ruleList = new ArrayList<>();
        Rule rule = new Rule();
        Symbolizer.RasterSymbolizer raster = new Symbolizer.RasterSymbolizer();
        Symbolizer.ColorMap colorMap = new Symbolizer.ColorMap();
        ArrayList<Symbolizer.ColorMapEntry> colorMapEntries = new ArrayList<>();

        if (StrUtil.isNotBlank(tifStyles.get(0).getFillWay())) {
            colorMap.setType(tifStyles.get(0).getFillWay());
        }
        for (TifStyle tifStyle : tifStyles) {
            Symbolizer.ColorMapEntry colorMapEntry = new Symbolizer.ColorMapEntry();
            colorMapEntry.setColor("#FFFFFF");
            colorMapEntry.setLabel("label");
            colorMapEntry.setOpacity("0");
            colorMapEntry.setQuantity("0");
            if (StrUtil.isNotBlank(tifStyle.getColor())) {
                colorMapEntry.setColor(tifStyle.getColor());
            }
            if (StrUtil.isNotBlank(tifStyle.getLabel())) {
                colorMapEntry.setLabel(tifStyle.getLabel());
            }
            if (StrUtil.isNotBlank(tifStyle.getOpacity())) {
                colorMapEntry.setOpacity(tifStyle.getOpacity());
            }
            if (StrUtil.isNotBlank(tifStyle.getQuantity())) {
                colorMapEntry.setQuantity(tifStyle.getQuantity());
            }
            colorMapEntries.add(colorMapEntry);
        }

        if (meta != null && meta.getNoData() != null) {
            if (meta.getNoData().compareTo(meta.getMaxVertical()) > 0) {
                Symbolizer.ColorMapEntry colorMapEntry = new Symbolizer.ColorMapEntry();
                colorMapEntry.setColor("#000000");
                colorMapEntry.setLabel("nodata");
                colorMapEntry.setOpacity("0.0");
                colorMapEntry.setQuantity(meta.getNoData().toString());
                colorMapEntries.add(colorMapEntry);
            } else if (meta.getNoData().compareTo(meta.getMaxVertical()) < 0) {
                Symbolizer.ColorMapEntry colorMapEntry = new Symbolizer.ColorMapEntry();
                colorMapEntry.setColor("#000000");
                colorMapEntry.setLabel("nodata");
                colorMapEntry.setOpacity("0.0");
                colorMapEntry.setQuantity(meta.getNoData().toString());
                colorMapEntries.add(0, colorMapEntry);
            } else if (meta.getNoData().compareTo(new BigDecimal(0)) != 0) {
                Symbolizer.ColorMapEntry colorMapEntry = new Symbolizer.ColorMapEntry();
                colorMapEntry.setColor("#000000");
                colorMapEntry.setLabel("nodata");
                colorMapEntry.setOpacity("0.0");
                colorMapEntry.setQuantity(meta.getNoData().toString());
                colorMapEntries.add(1, colorMapEntry);
            }
        }

        colorMap.setColorMapEntry(colorMapEntries);
        raster.setOpacity("1.0");
        raster.setColorMap(colorMap);
        rule.setRasterSymbolizer(raster);
        ruleList.add(rule);
        featureTypeStyle.setRules(ruleList);
        userStyle.setFeatureTypeStyles(new ArrayList<>(List.of(featureTypeStyle)));
        namedLayer.setUserStyles(new ArrayList<>(List.of(userStyle)));
        styled.setNamedLayers(new ArrayList<>(List.of(namedLayer)));
        return SLDSerialize.serialize(styled);
    }


    private String GetSld(StyleInfo sInfo, String type, StyleInfo sInfoDefault) {
        StyledLayerDescriptor sld = new StyledLayerDescriptor();
        UserLayer ul = new UserLayer();
        UserStyle us = new UserStyle();
        FeatureTypeStyle fts = new FeatureTypeStyle();
        fts.setFeatureTypeName("Feature");
        fts.setSemanticTypeIdentifiers(Arrays.asList("generic:geometry", "simple"));

        Rule rule = new Rule();
        Rule ruleDefault = new Rule();
        if (Func.isNotBlank(sInfo.getMaxScaleDenominator()))
            rule.setMaxScaleDenominator(sInfo.getMaxScaleDenominator());
        if (Func.isNotBlank(sInfo.getMinScaleDenominator()))
            rule.setMinScaleDenominator(sInfo.getMinScaleDenominator());


        if (type.equals(DictionaryIds.GEOM_POINT)) {
            Symbolizer.PointSymbolizer ps = GetPointSymbolizer(sInfo);
            rule.setPointSymbolizer(ps);
        } else if (type.equals(DictionaryIds.GEOM_LINE)) {

            if (sInfo.getLineType() == 4) {//线型（实线1，虚线2, 点线3， 虚点线 4）
                Symbolizer.LineSymbolizer ls2 = GetDashLine(sInfo);
                rule.setLineSymbolizer(List.of(ls2));
            } else {
                Symbolizer.LineSymbolizer ls = GetLineSymbolizer(sInfo);
                rule.setLineSymbolizer(List.of(ls));
            }
        } else {
            Symbolizer.PolygonSymbolizer ps = GetPolygonSymbolizer(sInfo);
            rule.setPolygonSymbolizer(ps);
        }

        if (Func.isNotEmpty(sInfo.getIsMark()) && sInfo.getIsMark()) {
            Symbolizer.TextSymbolizer symbolizer = GetFont(sInfo);
            rule.setTextSymbolizer(symbolizer);
        }

        ArrayList<Rule> rules = new ArrayList<>();
        rules.add(rule);
        fts.setRules(rules);

        //添加默认规则
        AddDefaultRule(sInfoDefault, fts, ruleDefault, type);

        ArrayList<FeatureTypeStyle> featureTypeStyles = new ArrayList<>();
        featureTypeStyles.add(fts);
        us.setFeatureTypeStyles(featureTypeStyles);

        ul.setUserStyles(new ArrayList<>(List.of(us)));
        sld.setUserLayers(new ArrayList<>(List.of(ul)));

        return SLDSerialize.serialize(sld);
    }

    private void AddDefaultRule(StyleInfo sInfoDefault, FeatureTypeStyle fts, Rule ruleDefault, String type) {

        if (sInfoDefault != null && sInfoDefault.getIsDefaultRule()) {
            if (type.equals(DictionaryIds.GEOM_POINT)) {

            } else if (DictionaryIds.GEOM_LINE.equals(type)) {
                Symbolizer.LineSymbolizer ls = GetLineSymbolizer(sInfoDefault);
                ArrayList<Symbolizer.LineSymbolizer> lineSymbolizers = new ArrayList<>();
                lineSymbolizers.add(ls);
                ruleDefault.setLineSymbolizer(lineSymbolizers);
            } else {
                Symbolizer.PolygonSymbolizer psDefault = GetPolygonSymbolizer(sInfoDefault);
                ArrayList<Symbolizer.PolygonSymbolizer> polygonSymbolizers = new ArrayList<>();
                polygonSymbolizers.add(psDefault);
                ruleDefault.setPolygonSymbolizer(psDefault);
            }
        }
    }

    private Symbolizer.TextSymbolizer GetFont(PolygonStyle sInfo) {
        Symbolizer.TextSymbolizer symbolizer = new Symbolizer.TextSymbolizer();
        Symbolizer.Font font = new Symbolizer.Font();
        Symbolizer.Label label = new Symbolizer.Label();
        if (StrUtil.isNotBlank(sInfo.getMarkName())) {
            label.setPropertyName(sInfo.getMarkName());
        }
        ArrayList<Symbolizer.CssParameter> cssParameters = new ArrayList<>();
        if (StrUtil.isNotBlank(sInfo.getMarkFontName())) {
            cssParameters.add(new Symbolizer.CssParameter(
                    "font-family",
                    sInfo.getMarkFontName()
            ));
        }
        cssParameters.add(new Symbolizer.CssParameter("font-size", sInfo.getMarkFontSize().toString()));
        cssParameters.add(new Symbolizer.CssParameter("font-style", "normal"));
        cssParameters.add(new Symbolizer.CssParameter("font-weight", "normal"));
        font.setCssParameters(cssParameters);

        Symbolizer.LabelPlacement labelPlacement = new Symbolizer.LabelPlacement();
        Symbolizer.PointPlacement pointPlacement = new Symbolizer.PointPlacement();
        Symbolizer.Displacement displacement = new Symbolizer.Displacement();
        if (StrUtil.isNotBlank(sInfo.getDisplacementX()) && StrUtil.isNotBlank(sInfo.getDisplacementY())) {
            displacement.setDisplacementX(sInfo.getDisplacementX());
            displacement.setDisplacementY(sInfo.getDisplacementY());
        }
        pointPlacement.setDisplacement(displacement);
        labelPlacement.setPointPlacement(pointPlacement);
        symbolizer.setLabel(label);
        symbolizer.setFont(font);
        symbolizer.setLabelPlacement(labelPlacement);
        Symbolizer.Fill fill = new Symbolizer.Fill();
        ArrayList<Symbolizer.CssParameter> cssParameters1 = new ArrayList<>();
        if (StrUtil.isNotBlank(sInfo.getFillColor())) {
            cssParameters1.add(new Symbolizer.CssParameter("fill", ColorRGBtoHx16(sInfo.getFillColor())));
            fill.setCssParameters(cssParameters1);
            symbolizer.setFill(fill);
        }
        return symbolizer;

    }

    private String GetSld(StyleInfo sInfo, String type) {
        StyledLayerDescriptor sld = new StyledLayerDescriptor();
        UserLayer ul = new UserLayer();
        UserStyle us = new UserStyle();
        FeatureTypeStyle fts = new FeatureTypeStyle();
        fts.setFeatureTypeName("Feature");
        fts.setSemanticTypeIdentifiers(Arrays.asList("generic:geometry", "simple"));

        Rule rule = new Rule();
//        Rule ruleDefault = new Rule();
        if (Func.isNotBlank(sInfo.getMaxScaleDenominator())) {
            rule.setMaxScaleDenominator(sInfo.getMaxScaleDenominator());
        }
        if (StrUtil.isNotBlank(sInfo.getMinScaleDenominator())) {
            rule.setMinScaleDenominator(sInfo.getMinScaleDenominator());
        }

        if (type.equals(DictionaryIds.GEOM_POINT)) {
            Symbolizer.PointSymbolizer ps = GetPointSymbolizer(sInfo);
            rule.setPointSymbolizer(ps);
        } else if (type.equals(DictionaryIds.GEOM_LINE)) {

            if (sInfo.getLineType() == 4) {
                Symbolizer.LineSymbolizer ls2 = GetDashLine(sInfo);
                rule.setLineSymbolizer(List.of(ls2));
            } else {
                Symbolizer.LineSymbolizer ls = GetLineSymbolizer(sInfo);
                rule.setLineSymbolizer(List.of(ls));
            }
        } else {
            Symbolizer.PolygonSymbolizer ps = GetPolygonSymbolizer(sInfo);
            rule.setPolygonSymbolizer(ps);
        }


        List<Rule> rules = new ArrayList<>();
        rules.add(rule);
        fts.setRules(rules);

        List<FeatureTypeStyle> featureTypeStyles = new ArrayList<>();
        featureTypeStyles.add(fts);
        us.setFeatureTypeStyles(featureTypeStyles);

        ul.setUserStyles(new ArrayList<>(List.of(us)));
        sld.setUserLayers(new ArrayList<>(List.of(ul)));

        return SLDSerialize.serialize(sld);
    }

    private Symbolizer.LineSymbolizer GetLineSymbolizer(PolygonStyle sInfo) {
        Symbolizer.LineSymbolizer ls = new Symbolizer.LineSymbolizer();
        Symbolizer.Stroke stroke = new Symbolizer.Stroke();
        ls.setStroke(GetStroke(sInfo, stroke));
        return ls;
    }

    private Symbolizer.LineSymbolizer GetDashLine(PolygonStyle sInfo) {
        Symbolizer.LineSymbolizer ls2 = new Symbolizer.LineSymbolizer();
        Symbolizer.Stroke stroke = new Symbolizer.Stroke();
        List<Symbolizer.CssParameter> cssParameters = new ArrayList<>();

        if (Func.isNotBlank(sInfo.getOutlineColor())) {
            Symbolizer.CssParameter cssParameter = new Symbolizer.CssParameter();
            cssParameter.setName("stroke");
            cssParameter.setValue(ColorRGBtoHx16(sInfo.getOutlineColor()));
            cssParameters.add(cssParameter);
        }
        if (sInfo.getLineTransparency() != null) {
            Symbolizer.CssParameter cssParameter = new Symbolizer.CssParameter();
            cssParameter.setName("stroke-opacity");
            cssParameter.setValue(sInfo.getLineTransparency().toString());
            cssParameters.add(cssParameter);
        }
        if (sInfo.getLineWidth() != null) {
            Symbolizer.CssParameter cssParameter = new Symbolizer.CssParameter();
            cssParameter.setName("stroke-width");
            cssParameter.setValue(sInfo.getLineWidth().toString());
            cssParameters.add(cssParameter);
        }
        Symbolizer.CssParameter cssParameter = new Symbolizer.CssParameter();
        cssParameter.setName("stroke-dasharray");
        cssParameter.setValue("10 10");
        cssParameters.add(cssParameter);

        stroke.setCssParameters(cssParameters);
        ls2.setStroke(stroke);
        return ls2;
    }


    private Symbolizer.PointSymbolizer GetPointSymbolizer(PolygonStyle sInfo) {
        Symbolizer.PointSymbolizer ps = new Symbolizer.PointSymbolizer();
        Symbolizer.Graphic g = new Symbolizer.Graphic();
        if (Func.isNotEmpty(sInfo.getIsIcon()) && sInfo.getIsIcon()) {
            g = GetGraphic(sInfo, g);
        } else if (Func.isNotEmpty(sInfo.getIsFont()) && sInfo.getIsFont()) {
            g = GetFontGraphic(sInfo, g);
        } else {
            Symbolizer.Mark mark = new Symbolizer.Mark();
            mark.setWellKnownName(sInfo.getPointType());
            Symbolizer.Fill fill = new Symbolizer.Fill();

            fill = GetFill(sInfo, fill);

            if (Func.isNotBlank(sInfo.getIconSize())) {
                g.setSize(sInfo.getIconSize());
            }
            if (Func.isNotBlank(sInfo.getIconRotation()))
                g.setRotation(sInfo.getIconRotation());

            mark.setFill(fill);
            g.setMark(mark);
        }
        ps.setGraphic(g);
        return ps;
    }


    private Symbolizer.PolygonSymbolizer GetPolygonSymbolizer(PolygonStyle sInfo) {
        Symbolizer.PolygonSymbolizer ps = new Symbolizer.PolygonSymbolizer();

        if (Func.isNotEmpty(sInfo.getIsOutline()) && sInfo.getIsOutline()) {
            Symbolizer.Stroke stroke = new Symbolizer.Stroke();
            stroke = GetStroke(sInfo, stroke);
            ps.setStroke(stroke);
        }

        Symbolizer.Fill fill = new Symbolizer.Fill();
        if (Func.isNotEmpty(sInfo.getIsFill()) && sInfo.getIsFill()) {
            ps.setFill(GetFill(sInfo, fill));
        }
        if (Func.isNotEmpty(sInfo.getIsIcon()) && sInfo.getIsIcon()) {
            Symbolizer.GraphicFill gf = new Symbolizer.GraphicFill();
            Symbolizer.Graphic g = new Symbolizer.Graphic();
            gf.setGraphic(GetGraphic(sInfo, g));
            fill.setGraphicFill(gf);
            ps.setFill(fill);
        }
        if (Func.isNotEmpty(sInfo.getIsFont()) && sInfo.getIsFont()) {
            Symbolizer.GraphicFill gf = new Symbolizer.GraphicFill();
            Symbolizer.Graphic g = new Symbolizer.Graphic();

            gf.setGraphic(GetFontGraphic(sInfo, g));
            fill.setGraphicFill(gf);
            ps.setFill(fill);
        }
        return ps;
    }

    private Symbolizer.Graphic GetFontGraphic(PolygonStyle sInfo, Symbolizer.Graphic g) {
        Symbolizer.Stroke st = new Symbolizer.Stroke();
        if (Func.isNotBlank(sInfo.getFontSize())) {
            g.setSize(sInfo.getFontSize());
        }
        if (Func.isNotBlank(sInfo.getFontRotation())) {
            g.setRotation(sInfo.getFontRotation());
        }
        if (Func.isNotBlank(sInfo.getFontName()) && Func.isNotBlank(sInfo.getCharUnicode())) {
            Symbolizer.Mark mark = new Symbolizer.Mark();
            mark.setWellKnownName(String.format("ttf://{%s}#0x{%s}", sInfo.getFontName(), sInfo.getCharUnicode()));
            if (Func.isNotBlank(sInfo.getFontColor())) {
                Symbolizer.CssParameter cssParameter = new Symbolizer.CssParameter();
                cssParameter.setName("fill");
                cssParameter.setValue(ColorRGBtoHx16(sInfo.getFontColor()));
                g.getMark().getFill().getCssParameters().add(cssParameter);
            }
            g.getMark().setStroke(st);
        }
        return g;
    }


    private Symbolizer.Graphic GetGraphic(PolygonStyle sInfo, Symbolizer.Graphic g) {
        if (Func.isNotBlank(sInfo.getIconSize())) {
            g.setSize(sInfo.getIconSize());
        }
        if (Func.isNotBlank(sInfo.getIconRotation())) {
            g.setRotation(sInfo.getIconRotation());
        }
        if (Func.isNotBlank(sInfo.getIconPath())) {
            Symbolizer.ExternalGraphic eg = new Symbolizer.ExternalGraphic();
            Symbolizer.OnlineResource onlineResource = new Symbolizer.OnlineResource();
            onlineResource.setHref(sInfo.getIconPath());
            eg.setOnlineResource(onlineResource);
            eg.setFormat("image/png");
            g.setExternalGraphic(eg);
        }
        return g;
    }


    private Symbolizer.Fill GetFill(PolygonStyle sInfo, Symbolizer.Fill fill) {
        ArrayList<Symbolizer.CssParameter> cssParameters = new ArrayList<>();
        if (StrUtil.isNotBlank(sInfo.getPolygonColor())) {
            Symbolizer.CssParameter cssParameter = new Symbolizer.CssParameter();
            cssParameter.setName("fill");
            cssParameter.setValue(ColorRGBtoHx16(sInfo.getPolygonColor()));
            cssParameters.add(cssParameter);
            fill.setCssParameters(cssParameters);
        }

        if (sInfo.getFillTransparency() != null) {
            Symbolizer.CssParameter cssParameter = new Symbolizer.CssParameter();
            cssParameter.setName("fill-opacity");
            cssParameter.setValue(sInfo.getFillTransparency().toString());
            cssParameters.add(cssParameter);
            fill.setCssParameters(cssParameters);
        }
        return fill;

    }


    private Symbolizer.Stroke GetStroke(PolygonStyle sInfo, Symbolizer.Stroke stroke) {
        if (Func.isEmpty(sInfo.getLineType())) {
            return new Symbolizer.Stroke();
        }
        switch (sInfo.getLineType()) {
            case 3://点线
                return GetPointStroke(sInfo, stroke);
            case 4://虚点线
                return GetPointStrokeO(sInfo, stroke);
            default:
                return GetDashStroke(sInfo, stroke);
        }
    }

    private Symbolizer.Stroke GetDashStroke(PolygonStyle sInfo, Symbolizer.Stroke stroke) {
        ArrayList<Symbolizer.CssParameter> cssParameters = new ArrayList<>();

        if (StrUtil.isNotBlank(sInfo.getOutlineColor())) {
            if (sInfo.getOutlineColor().startsWith("#")) {
                cssParameters.add(new Symbolizer.CssParameter("stroke", sInfo.getOutlineColor()));
            } else {
                cssParameters.add(new Symbolizer.CssParameter("stroke", ColorRGBtoHx16(sInfo.getOutlineColor())));
            }
        }

        if (sInfo.getLineTransparency() != null) {
            cssParameters.add(new Symbolizer.CssParameter("stroke-opacity", sInfo.getLineTransparency().toString()));
        }

        if (sInfo.getLineWidth() != null) {
            cssParameters.add(new Symbolizer.CssParameter("stroke-width", sInfo.getLineWidth().toString()));
        }
        if (sInfo.getLineType() != 1) {
            switch (sInfo.getLineType()) {
                case 2:
                    cssParameters.add(new Symbolizer.CssParameter("stroke-dasharray", "5 2"));
                    break;
                case 4:
                    cssParameters.add(new Symbolizer.CssParameter("stroke-dasharray", "10 10"));
                    break;
            }
        }
        stroke.setCssParameters(cssParameters);
        return stroke;
    }

    private Symbolizer.Stroke GetPointStrokeO(PolygonStyle sInfo, Symbolizer.Stroke stroke) {
        Symbolizer.GraphicStroke gs = new Symbolizer.GraphicStroke();
        Symbolizer.Graphic g = new Symbolizer.Graphic();

        String lineColor = "#FFFFFF";
        if (StrUtil.isNotBlank(sInfo.getOutlineColor()))
            lineColor = ColorRGBtoHx16(sInfo.getOutlineColor());

        String lineWidth = "1";
        if (sInfo.getLineWidth() != null) {
            lineWidth = sInfo.getLineWidth().toString();
        }

        Symbolizer.Mark mark = new Symbolizer.Mark();
        mark.setWellKnownName("circle");

        Symbolizer.Fill fill = new Symbolizer.Fill();
        fill.setCssParameters(List.of(
                new Symbolizer.CssParameter("fill", lineColor)
        ));
        mark.setFill(fill);

        Symbolizer.Stroke stroke1 = new Symbolizer.Stroke();
        stroke1.setCssParameters(Arrays.asList(
                new Symbolizer.CssParameter("stroke", lineColor),
                new Symbolizer.CssParameter("stroke-width", lineWidth)
        ));
        mark.setStroke(stroke1);

        g.setSize("5");
        g.setMark(mark);
        gs.setGraphic(g);
        stroke.setGraphicStroke(gs);
        ArrayList<Symbolizer.CssParameter> cssParameters = new ArrayList<>();
        cssParameters.add(new Symbolizer.CssParameter("stroke-dasharray", "5 15"));
        cssParameters.add(new Symbolizer.CssParameter("stroke-dashoffset", "7.5"));
        if (sInfo.getLineTransparency() != null) {
            cssParameters.add(new Symbolizer.CssParameter("stroke-opacity", sInfo.getLineTransparency().toString()));
        }
        stroke.setCssParameters(cssParameters);
        return stroke;
    }


    private Symbolizer.Stroke GetPointStroke(PolygonStyle sInfo, Symbolizer.Stroke stroke) {
        Symbolizer.GraphicStroke gs = new Symbolizer.GraphicStroke();
        Symbolizer.Graphic g = new Symbolizer.Graphic();

        String lineColor = "#FFFFFF";
        if (Func.isNotBlank(sInfo.getOutlineColor()))
            lineColor = ColorRGBtoHx16(sInfo.getOutlineColor());

        String lineWidth = "1";
        if (sInfo.getLineWidth() != null) {
            lineWidth = sInfo.getLineWidth().toString();
        }

        Symbolizer.Mark mark = new Symbolizer.Mark();
        mark.setWellKnownName("circle");

        Symbolizer.Fill fill = new Symbolizer.Fill();
        fill.setCssParameters(List.of(
                new Symbolizer.CssParameter("fill", lineColor)
        ));
        mark.setFill(fill);

        Symbolizer.Stroke stroke1 = new Symbolizer.Stroke();
        stroke1.setCssParameters(Arrays.asList(
                new Symbolizer.CssParameter("stroke", lineColor),
                new Symbolizer.CssParameter("stroke-width", lineWidth)
        ));
        mark.setStroke(stroke1);

        g.setSize("4");
        g.setMark(mark);
        gs.setGraphic(g);
        stroke.setGraphicStroke(gs);
        ArrayList<Symbolizer.CssParameter> cssParameters = new ArrayList<>();
        cssParameters.add(new Symbolizer.CssParameter("stroke-dasharray", "4 6"));
        if (sInfo.getLineTransparency() != null) {
            cssParameters.add(new Symbolizer.CssParameter("stroke-opacity", sInfo.getLineTransparency().toString()));
        }
        stroke.setCssParameters(cssParameters);
        return stroke;
    }

    private String ColorRGBtoHx16(String str) {
        if (str.startsWith("#")) {
            return str;
        }

        try {
            str = str.trim().toLowerCase();

            if (str.contains("rgb") && str.contains(",")) {
                str = str.substring(str.indexOf("(") + 1, str.indexOf(")"));
                String[] values = str.split(",");
                int r = Integer.parseInt(values[0].trim());
                int g = Integer.parseInt(values[1].trim());
                int b = Integer.parseInt(values[2].trim());
                return ToHexColor(r, g, b);
            } else if (str.contains(",") && !str.contains("(") && !str.contains(")")) {
                String[] values = str.split(",");
                return ToHexColor(Integer.parseInt(values[0]), Integer.parseInt(values[1]), Integer.parseInt(values[2]));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return "";
    }

    private String ToHexColor(int r, int g, int b) {
        String R = Integer.toHexString(r);
        if (R.equals("0")) {
            R = "00";
        }
        if (r < 16 && r > 0) {
            R = "0" + R;
        }

        String G = Integer.toHexString(g);
        if (G.equals("0")) {
            G = "00";
        }
        if (g < 16 && g > 0) {
            G = "0" + G;
        }

        String B = Integer.toHexString(b);
        if (B.equals("0")) {
            B = "00";
        }
        if (b < 16 && b > 0) {
            B = "0" + B;
        }

        return "#" + R + G + B;
    }


    private List<RuleData> getRuleDatas(String content, String columnName, String Symbol) {
        List<RuleData> rd = null;
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
        if (Symbol != null && !Symbol.trim().isEmpty()) {
            try {
                rd = mapper.readValue(Symbol, new TypeReference<List<RuleData>>() {
                });
            } catch (Exception e) {
                log.warn("解析样式规则 JSON 失败，按无规则处理: {}", e.getMessage(), e);
                rd = null; // or Collections.emptyList()
            }
        }
        List<RuleData> ruleList = new ArrayList<>();
        StyledLayerDescriptor sld = SLDSerialize.deserialize(content, StyledLayerDescriptor.class);
        List<Rule> rules = getRules(sld);
        if (Func.isNotEmpty(rules)) {
            int i = 0;
            for (Rule rule : rules) {
                PolygonStyle pstyle = getColorString(rule);
                if (rd != null) {
                    PolygonStyle dh = null;
                    try {
                        dh = mapper.readValue(rd.get(i).getPolygonStyle(), PolygonStyle.class);
                    } catch (JsonProcessingException e) {
                        log.error("解析异常！！！");
                    }
//                    PolygonStyle dh = gson.fromJson(rd.get(i).getPolygonStyle(), PolygonStyle.class);
                    if (StrUtil.isNotBlank(dh.getIconPath())) {
                        pstyle.setIconPath(dh.getIconPath());
                    }
                    if (Func.isNotEmpty(dh.getIsOutline()) && dh.getIsOutline()) {
                        pstyle.setIsOutline(true);
                        pstyle.setLineType(dh.getLineType());
                        pstyle.setLineWidth(dh.getLineWidth());
                        pstyle.setLineTransparency(dh.getLineTransparency());
//                        pstyle.setOutlineColor(dh.getOutlineColor());
                    }
                    if (Func.isNotEmpty(dh.getFillTransparency())) {
                        pstyle.setFillTransparency(dh.getFillTransparency());
                    }
                    if (Func.isNotEmpty(dh.getLineType())) {
                        pstyle.setLineType(dh.getLineType());
                    }
                    if (Func.isNotEmpty(dh.getIsFont()) && dh.getIsFont()) {
                        pstyle.setIsFill(false);
                        pstyle.setFillColor(dh.getFillColor());
                    }
                }
                if (pstyle != null) {
                    if (rule.getPointSymbolizer() != null) {
                        pstyle.setType("1");
                    } else if (rule.getLineSymbolizer() != null && !rule.getLineSymbolizer().isEmpty()) {
                        pstyle.setType("2");
                    } else if (rule.getPolygonSymbolizer() != null) {
                        pstyle.setType("3");
                    } else {
                        pstyle.setType("4");
                        RuleData ruleData = new RuleData();
                        ruleData.setPolygonStyle(JSONObject.toJSONString(pstyle));
                        ruleData.setTitle(content);
                        return ruleList;
                    }
                    RuleData ruleData = new RuleData();
                    ruleData.setPolygonStyle(JSONObject.toJSONString(pstyle));

                    if (rule.getFilter() != null) {
                        if (Func.isNotEmpty(rule.getFilter().getPropertyIsEqualTo())) {
                            ruleData.setValue(rule.getFilter().getPropertyIsEqualTo().getLiteral());
                            ruleData.setTitle(rule.getFilter().getPropertyIsEqualTo().getPropertyName());
                        } else if (rule.getFilter().getAnd() != null) {
                            // 处理 And 条件（包括范围过滤）
                            if (rule.getFilter().getAnd().getPropertyIsGreaterThan() != null && rule.getFilter().getAnd().getPropertyIsLessThanOrEqualTo() != null) {
                                // 范围过滤
                                ruleData.setValueOne(rule.getFilter().getAnd().getPropertyIsGreaterThan().getLiteral());
                                ruleData.setValue(rule.getFilter().getAnd().getPropertyIsLessThanOrEqualTo().getLiteral());
                                ruleData.setTitle(rule.getFilter().getAnd().getPropertyIsGreaterThan().getPropertyName());
                                ruleData.setOperator("范围值");
                            } else if (Func.isNotEmpty(rule.getFilter().getAnd().getPropertyIsEqualTo())) {
                                // 保留原有逻辑：处理 And 条件中的 PropertyIsEqualTo
                                ruleData.setValue(rule.getFilter().getAnd().getPropertyIsEqualTo().getLiteral());
                                ruleData.setTitle(rule.getFilter().getAnd().getPropertyIsEqualTo().getPropertyName());
                            }
                        } else if (rule.getFilter().getNot() != null && Func.isNotEmpty(rule.getFilter().getNot().getPropertyIsEqualTo())) {
                            ruleData.setValue(rule.getFilter().getNot().getPropertyIsEqualTo().getLiteral());
                            ruleData.setTitle(rule.getFilter().getNot().getPropertyIsEqualTo().getPropertyName());
                        } else if (rule.getFilter().getOr() != null && Func.isNotEmpty(rule.getFilter().getOr().getPropertyIsEqualTo())) {
                            ruleData.setValue(rule.getFilter().getOr().getPropertyIsEqualTo().getLiteral());
                            ruleData.setTitle(rule.getFilter().getOr().getPropertyIsEqualTo().getPropertyName());
                        }
                    }
                    ruleData.setCount(rule.getCount());
                    ruleList.add(ruleData);
                    i++;
                }
            }
        }
        return ruleList;
    }


    private List<StyleInfo> getStyleInfo(String content, String StyleRenderRule) {
//        Gson gson = new Gson();
//        StyleInfo si = gson.fromJson(StyleRenderRule, StyleInfo.class);
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
            StyleInfo si = mapper.readValue(StyleRenderRule, StyleInfo.class);

            StyledLayerDescriptor sld = SLDSerialize.deserialize(content, StyledLayerDescriptor.class);
            List<Rule> rules = getRules(sld);

            List<StyleInfo> styleInfos = new ArrayList<>();
            if (Func.isNotEmpty(rules)) {
                for (Rule item : rules) {
                    StyleInfo styleInfo = new StyleInfo();

                    if (Func.isNotBlank(item.getMaxScaleDenominator())) {
                        styleInfo.setMaxScaleDenominator(item.getMaxScaleDenominator());
                    }
                    if (Func.isNotBlank(item.getMinScaleDenominator())) {
                        styleInfo.setMinScaleDenominator(item.getMinScaleDenominator());
                    }
                    PolygonStyle pstyle = getColorString(item);
                    if (item.getPointSymbolizer() != null && item.getPointSymbolizer().getGraphic() != null) {
                        styleInfo.setType("1");
                        pstyle.setIconPath(si.getIconPath());
                        styleInfo.setPolygonColor(si.getPolygonColor());
                        styleInfo.setPointType(si.getPointType());
                        styleInfo.setFillTransparency(si.getFillTransparency());
                        if (item.getPointSymbolizer().getGraphic().getExternalGraphic() != null
                                && item.getPointSymbolizer().getGraphic().getExternalGraphic().getOnlineResource() != null) {
                            styleInfo.setIconPath(item.getPointSymbolizer().getGraphic().getExternalGraphic().getOnlineResource().getHref());
                        }
                        styleInfo.setIconSize(item.getPointSymbolizer().getGraphic().getSize());
                        styleInfo.setIconRotation(item.getPointSymbolizer().getGraphic().getRotation());
                    }

                    if (item.getLineSymbolizer() != null) {
                        styleInfo.setType("2");
//                        if (si.getIsOutline()) {
//                            styleInfo.setLineType(si.getLineType());
//                            styleInfo.setLineTransparency(si.getLineTransparency());
//                            styleInfo.setLineWidth(si.getLineWidth());
//                            styleInfo.setOutlineColor(si.getOutlineColor());
//                        }
                        styleInfo.setLineType(Func.isNotEmpty(si.getLineType())?si.getLineType():1);
                        styleInfo.setLineTransparency(Func.isNotEmpty(si.getLineTransparency())?si.getLineTransparency():1);
                        styleInfo.setLineWidth(Func.isNotEmpty(si.getLineWidth())?si.getLineWidth():1);
                        styleInfo.setOutlineColor(si.getOutlineColor());
                    }
                    if (item.getPolygonSymbolizer() != null || item.getTextSymbolizer() != null) {
                        styleInfo.setType("3");
                        styleInfo.setLineWidth(pstyle.getLineWidth());
                        styleInfo.setLineTransparency(pstyle.getLineTransparency());

                        styleInfo.setOutlineColor(pstyle.getOutlineColor());
                        styleInfo.setPolygonColor(pstyle.getPolygonColor());
                        styleInfo.setFillTransparency(pstyle.getFillTransparency());

                        styleInfo.setIconPath(si.getIconPath());

                        styleInfo.setWellKnownNameIcon(pstyle.getWellKnownNameIcon());
                        styleInfo.setStrokeIcon(pstyle.getStrokeIcon());
                        styleInfo.setStrokeWidthIcon(pstyle.getStrokeWidthIcon());
                        styleInfo.setIconRotation(pstyle.getIconRotation());
                        styleInfo.setIconSize(pstyle.getIconSize());
                        styleInfo.setIsDefaultRule(pstyle.getIsDefaultRule());
                        styleInfo.setIsIcon(pstyle.getIsIcon());

                        styleInfo.setLineWidth(si.getLineWidth());
                        styleInfo.setIsOutline(si.getIsOutline());
                        styleInfo.setOutlineColor(si.getOutlineColor());
                        styleInfo.setLineType(si.getLineType());
                        styleInfo.setFillTransparency(si.getFillTransparency());
                        styleInfo.setLineTransparency(si.getLineTransparency());
                        styleInfo.setFillColor(si.getFillColor());
                        styleInfo.setIsFill(si.getIsFill());
                        styleInfo.setPolygonColor(si.getPolygonColor());
                    }

                    if (Func.isNotEmpty(si.getIsFont()) && si.getIsFont()) {
                        styleInfo.setIsFill(false);
                        styleInfo.setFontColor(si.getFontColor());
                    }
                    styleInfos.add(styleInfo);
                }
                return styleInfos;
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    private PolygonStyle getColorString(Rule rule) {
        PolygonStyle ps = new PolygonStyle();
        ArrayList<Symbolizer.CssParameter> list = new ArrayList<>();
        if (rule != null) {
            if (rule.getPointSymbolizer() != null && rule.getPointSymbolizer().getGraphic() != null) {
                if (rule.getPointSymbolizer().getGraphic().getExternalGraphic() != null) {
                    ps.setIsIcon(true);
                    ps.setIconPath(rule.getPointSymbolizer().getGraphic().getExternalGraphic().getOnlineResource().getHref());
                }
                if (StrUtil.isNotBlank(rule.getPointSymbolizer().getGraphic().getSize())) {
                    ps.setIconSize(rule.getPointSymbolizer().getGraphic().getSize());
                }
                if (StrUtil.isNotBlank(rule.getPointSymbolizer().getGraphic().getRotation())) {
                    ps.setIconRotation(rule.getPointSymbolizer().getGraphic().getRotation());
                }
                if (rule.getPointSymbolizer().getGraphic().getMark() != null) {
                    if (StrUtil.isNotBlank(rule.getPointSymbolizer().getGraphic().getMark().getWellKnownName())) {
                        String temp = rule.getPointSymbolizer().getGraphic().getMark().getWellKnownName();
                        if (temp.contains("ttf://")) {
                            ps.setIsFont(true);
                            ps.setFontName(GetStr(temp, "ttf://", "#"));
                            ps.setCharUnicode(temp.substring(temp.indexOf("#") + 3));

                            if (StrUtil.isNotBlank(rule.getPointSymbolizer().getGraphic().getSize())) {
                                ps.setFontSize(rule.getPointSymbolizer().getGraphic().getSize());
                            }
                            if (StrUtil.isNotBlank(rule.getPointSymbolizer().getGraphic().getRotation())) {
                                ps.setIconRotation(rule.getPointSymbolizer().getGraphic().getRotation());
                            }
                        } else {
                            ps.setPointType(rule.getPointSymbolizer().getGraphic().getMark().getWellKnownName());
                        }
                    }
                    if (rule.getPointSymbolizer().getGraphic().getMark().getFill() != null) {
                        ps = InitColor(ps, rule.getPointSymbolizer().getGraphic().getMark().getFill().getCssParameters(), "0");
                    }

                    if (rule.getPointSymbolizer().getGraphic().getMark().getStroke() != null) {
                        ps = InitColor(ps, rule.getPointSymbolizer().getGraphic().getMark().getStroke().getCssParameters(), "1");
                    }
                }
            }

            if (rule.getLineSymbolizer() != null
                    && !rule.getLineSymbolizer().isEmpty()
                    && rule.getLineSymbolizer().get(0).getStroke() != null) {
                ps = InitColor(ps, rule.getLineSymbolizer().get(0).getStroke().getCssParameters(), "1");

            }

            if (rule.getPolygonSymbolizer() != null) {
                if (rule.getPolygonSymbolizer().getStroke() != null) {
                    ps = InitColor(ps, rule.getPolygonSymbolizer().getStroke().getCssParameters(), "1");
                }
                if (rule.getPolygonSymbolizer().getFill() != null) {
                    if (rule.getPolygonSymbolizer().getFill().getCssParameters() != null) {
                        ps = InitColor(ps, rule.getPolygonSymbolizer().getFill().getCssParameters(), "0");
                    }

                    if (rule.getPolygonSymbolizer().getFill().getGraphicFill() != null) {
                        if (rule.getPolygonSymbolizer().getFill().getGraphicFill().getGraphic() != null) {
                            ps.setIconSize(rule.getPolygonSymbolizer().getFill().getGraphicFill().getGraphic().getSize());
                            ps.setIconRotation(rule.getPolygonSymbolizer().getFill().getGraphicFill().getGraphic().getRotation());

                            if (rule.getPolygonSymbolizer().getFill().getGraphicFill().getGraphic().getMark() != null
                                    && rule.getPolygonSymbolizer().getFill().getGraphicFill().getGraphic().getMark().getStroke() != null
                                    && rule.getPolygonSymbolizer().getFill().getGraphicFill().getGraphic().getMark().getStroke().getCssParameters() != null) {

                                String temp = rule.getPolygonSymbolizer().getFill().getGraphicFill().getGraphic().getMark().getWellKnownName();
                                ps.setWellKnownNameIcon(temp);

                                for (int i = 0; i < rule.getPolygonSymbolizer().getFill().getGraphicFill().getGraphic().getMark().getStroke().getCssParameters().size(); i++) {
                                    ps.setIsDefaultRule(true);
                                    ps.setIsIcon(true);
                                    Symbolizer.CssParameter cssItem = rule.getPolygonSymbolizer().getFill().getGraphicFill().getGraphic().getMark().getStroke().getCssParameters().get(i);

                                    if (cssItem.getName().equals("stroke")) {
                                        ps.setStrokeIcon(cssItem.getValue());
                                    }
                                    if (cssItem.getName().equals("stroke-width")) {
                                        ps.setStrokeWidthIcon(cssItem.getValue());
                                    }
                                }
                            }
                        } else if (rule.getPolygonSymbolizer().getFill().getGraphicFill().getGraphic().getMark() != null) {
                            String temp = rule.getPolygonSymbolizer().getFill().getGraphicFill().getGraphic().getMark().getWellKnownName();
                            ps.setWellKnownNameIcon(temp);
                            if (temp.contains("ttf://")) {
                                ps.setIsFont(true);
                                ps.setFontName(GetStr(temp, "ttf://", "#"));
                                ps.setCharUnicode(temp.substring(temp.indexOf("#") + 3));

                                if (StrUtil.isNotBlank(rule.getPolygonSymbolizer().getFill().getGraphicFill().getGraphic().getSize())) {
                                    ps.setFontSize(rule.getPolygonSymbolizer().getFill().getGraphicFill().getGraphic().getSize());
                                }
                                if (StrUtil.isNotBlank(rule.getPolygonSymbolizer().getFill().getGraphicFill().getGraphic().getRotation())) {
                                    ps.setFontRotation(rule.getPolygonSymbolizer().getFill().getGraphicFill().getGraphic().getRotation());
                                }
                            }
                        } else {
                            ps.setIsFont(true);
                            ps.setIconPath(rule.getPolygonSymbolizer().getFill().getGraphicFill().getGraphic().getExternalGraphic().getOnlineResource().getHref());
                            ps.setIconSize(rule.getPolygonSymbolizer().getFill().getGraphicFill().getGraphic().getSize());
                            ps.setIconRotation(rule.getPolygonSymbolizer().getFill().getGraphicFill().getGraphic().getRotation());
                        }
                    }
                }
            }
        }
        return ps;

    }

    private PolygonStyle InitColor(PolygonStyle pstyle, List<Symbolizer.CssParameter> list, String type) {
        if (Func.isNotEmpty(list)) {
            if (type.equals("1")) {
                String stroke = GetValue(list, "stroke");
                String strokeo = GetValue(list, "stroke-opacity");
                String strokew = GetValue(list, "stroke-width");
                String stroked = GetValue(list, "stroke-dasharray");
                pstyle.setIsOutline(true);

                if (StrUtil.isNotBlank(strokeo)) {
                    pstyle.setLineTransparency(OpacityToByte(strokeo));
                }
                if (StrUtil.isNotBlank(stroke)) {
                    pstyle.setOutlineColor(ColorHx16toRGB(stroke));
                }
                double d;
                if (StrUtil.isNotBlank(strokew)) {
                    pstyle.setLineWidth(OpacityToByte(strokew));
                }

                if (StrUtil.isNotBlank(stroked)) {
                    assert stroked != null;
                    if (stroked.contains(" ")) {
                        String[] temp = stroked.split(" ");
                        d = OpacityToByte(stroked);
                        if (d == 10) {
                            pstyle.setLineType(4);
                        }
                        if (d == 4) {
                            pstyle.setLineType(3);
                        }
                        if (d == 5) {
                            pstyle.setLineType(2);
                        }
                    }
                }
            } else {
                String fill = GetValue(list, "fill");
                String fillo = GetValue(list, "fill-opacity");
                pstyle.setIsFill(true);
                if (StrUtil.isNotBlank(fillo)) {
                    pstyle.setFillTransparency(OpacityToByte(fillo));
                }
                if (StrUtil.isNotBlank(fill)) {
                    pstyle.setPolygonColor(ColorHx16toRGB(fill));
                }
            }
        }
        return pstyle;
    }


    /**
     * [颜色：16进制转成RGB]
     *
     * @param strHxColor
     * @return
     */
    public String ColorHx16toRGB(String strHxColor) {
        if (strHxColor == null || strHxColor.length() < 7 || !strHxColor.startsWith("#")) {
            return "";
        }

        try {
            // 提取 RGB 各通道的十六进制字符串
            String rStr = strHxColor.substring(1, 3); // R: 1~2位
            String gStr = strHxColor.substring(3, 5); // G: 3~4位
            String bStr = strHxColor.substring(5, 7); // B: 5~6位

            // 十六进制转十进制
            int r = Integer.parseInt(rStr, 16);
            int g = Integer.parseInt(gStr, 16);
            int b = Integer.parseInt(bStr, 16);

            return String.format("rgb(%d, %d, %d)", r, g, b);
        } catch (NumberFormatException ex) {
            return "";
        }
    }

    private double OpacityToByte(String value) {
        double f = 1.0;
        if (value != null && !value.isEmpty()) {
            try {
                f = Double.parseDouble(value);
            } catch (NumberFormatException ignored) {
                // 转换失败，保持 f = 1.0
            }
        }
        return f;
    }


    private List<Rule> getRules(StyledLayerDescriptor sld) {
        ArrayList<Rule> rules = new ArrayList<>();
        if (sld != null) {
            ArrayList<UserStyle> usList = new ArrayList<>();
            ArrayList<FeatureTypeStyle> fstList = new ArrayList<>();
            if (sld.getNamedLayers() != null) {
                for (NamedLayer nl : sld.getNamedLayers()) {
                    if (Func.isNotEmpty(nl.getUserStyles())) {
                        usList.addAll(nl.getUserStyles());
                    }
                }
            }

            if (sld.getUserLayers() != null) {
                for (UserLayer ul : sld.getUserLayers()) {
                    if (Func.isNotEmpty(ul.getUserStyles())) {
                        usList.addAll(ul.getUserStyles());
                    }
                }
            }

            for (UserStyle us : usList) {
                if (Func.isNotEmpty(us.getFeatureTypeStyles())) {
                    for (FeatureTypeStyle fts : us.getFeatureTypeStyles()) {
                        if (Func.isNotEmpty(fts.getRules())) {
                            rules.addAll(fts.getRules());
                        }
                    }
                }
            }
        }
        return rules;

    }


    /**
     * 根据当前登录人所在行政区划，获取对应行政区划下所有人员信息,在返回对应人员信息相关数据
     *
     * @param userCode
     * @return
     */
    private List<DataStyleEntity> getDataByUserCodeAsync(String userCode) {
        try {
            userCode = UserContextUtil.resolveUserCode(userCode);
            List<DataStyleEntity> query = null;
            if (userCode.equalsIgnoreCase(ConstHelper.CONST_SYSTEMNAME)) {
                query = dataStyleMapper.selectList(new LambdaQueryWrapper<DataStyleEntity>());
            } else {
                List<SystemUserDto> userQuery = systemUserService.getUserDataByUserCodeAsync(userCode);
                if (userQuery != null && !userQuery.isEmpty()) {
                    List<String> collect = userQuery.stream()
                            .map(SystemUserEntity::getUserCode)
                            .collect(Collectors.toList());
                    if (Func.isNotEmpty(collect)) {
                        query = dataStyleMapper.selectList(new LambdaQueryWrapper<DataStyleEntity>()
                                .in(DataStyleEntity::getCreateBy, collect));
                    }
                }
                if (query == null) {
                    // sdms_user 无该用户记录（如未初始化）时兜底：按传入用户过滤本人数据
                    query = dataStyleMapper.selectList(new LambdaQueryWrapper<DataStyleEntity>()
                            .eq(DataStyleEntity::getCreateBy, userCode));
                }
            }
            return query;
        } catch (Exception e) {
            return null;
        }
    }


    public String getMultiChildTypeByType(String typeId) {
        try {
            String multiTypeID = typeId + ",";
            List<DataTypeEntity> dataTypeEntities = dataTypeMapper.selectList(
                    new LambdaQueryWrapper<DataTypeEntity>().eq(DataTypeEntity::getParentID, typeId));

            if (Func.isNotEmpty(dataTypeEntities)) {
                for (DataTypeEntity dataTypeEntity : dataTypeEntities) {
                    multiTypeID += dataTypeEntity.getId() + ",";
                }
            }

            multiTypeID = multiTypeID.substring(0, multiTypeID.length() - 1);
            return multiTypeID;
        } catch (Exception e) {
            return typeId;
        }
    }

    private String GetStr(String TxtStr, String FirstStr, String SecondStr) {
        if (FirstStr.indexOf(SecondStr, 0) != 1) {
            return "";
        }
        int firstSite = TxtStr.indexOf(FirstStr, 0);
        int secondSite = TxtStr.indexOf(SecondStr, firstSite + 1);
        if (firstSite == -1 && secondSite == -1)
            return "";
        return TxtStr.substring(firstSite + FirstStr.length(), secondSite - firstSite - FirstStr.length());
    }

    private String GetValue(List<Symbolizer.CssParameter> list, String name) {
        Symbolizer.CssParameter cp = list.stream()
                .filter(c -> StrUtil.isNotBlank(c.getName())
                        && c.getName().toLowerCase().equals(name))
                .findFirst()
                .orElse(null);
        if (cp != null) {
            return cp.getValue();
        }
        return null;
    }

}
