package com.whulri.geodata.datamanage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.whulri.geodata.basecore.mapper.DictDataCodeMapper;
import com.whulri.geodata.basecore.mapper.DynamicTableMapper;
import com.whulri.geodata.basecore.mapper.LayerFieldMapper;
import com.whulri.geodata.basecore.mapper.LayerMapper;
import com.whulri.geodata.common.config.AppSettingConfig;
import com.whulri.geodata.common.dto.layerField.LayerFieldDto;
import com.whulri.geodata.common.dto.layerField.LayerFieldInputDto;
import com.whulri.geodata.common.dto.layerField.LayerFieldListDto;
import com.whulri.geodata.common.dto.layerField.LayerFieldOutputDto;
import com.whulri.geodata.common.entity.DictDataCodeEntity;
import com.whulri.geodata.common.entity.LayerContentEntity;
import com.whulri.geodata.common.entity.LayerFieldEntity;
import com.whulri.geodata.common.result.ListResultOutput;
import com.whulri.geodata.common.util.PostgresVectorHelper;
import com.whulri.geodata.common.util.ShpReader;
import com.whulri.geodata.common.util.UtilityMessageConvert;
import com.whulri.geodata.common.vo.AttributeModel;
import com.whulri.geodata.datamanage.service.IDictDataCodeService;
import com.whulri.geodata.datamanage.service.ILayerFieldService;
import com.whulri.geodata.common.bridge.BeanUtil;
import com.whulri.geodata.common.bridge.Func;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.io.File;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LayerFieldServiceImpl extends ServiceImpl<LayerFieldMapper, LayerFieldEntity> implements ILayerFieldService {

    @Resource
    private AppSettingConfig appSettingConfig;

    @Resource
    private IDictDataCodeService dictDataCodeService;

    @Resource
    private LayerMapper layerMapper;

    @Resource
    private DictDataCodeMapper dictDataCodeMapper;

    @Resource
    private DynamicTableMapper dynamicTableMapper;

    @Resource
    private UtilityMessageConvert utilityMessageConvert;


    @Override
    public LayerFieldListDto getLayerFieldByFileName(String shpFileName) {

        boolean status = false;
        String message = "";
        List<LayerFieldDto> listDTO = new ArrayList<>();
        String dataTypes = "";

        // 图层类型查询的ID,点线面
        String layerDataTypeID = "1cfe51dd-67a3-11e7-8eb2-005056bb1c7e";
        List<DictDataCodeEntity> listGeomType = dictDataCodeService.list(new LambdaQueryWrapper<DictDataCodeEntity>()
                .eq(DictDataCodeEntity::getDataTypeID, layerDataTypeID));

        ListResultOutput<DictDataCodeEntity> b = isFileExist(shpFileName);
        if (!b.item.isEmpty()) {
            try {
                String fileFullName = Paths.get(System.getProperty("user.dir"), appSettingConfig.getUploadFilePath()).toString();
                shpFileName = Paths.get(fileFullName, Paths.get(shpFileName).getFileName().toString()).toString();

                ShpReader shpReader = new ShpReader(shpFileName);
                dataTypes = getGeomType(shpReader.getGeomType().toUpperCase(), listGeomType);

                if (!shpReader.isValidDataSource()) {
                    status = true;
                    message = utilityMessageConvert.get("上传文件解析异常(上传的矢量文件无效)");
                } else {
                    String csSrc = shpReader.getSridWkt();
                    String spatialReference = appSettingConfig.getSpatialReference();
                    if (false) {
                        status = true;
                        message = utilityMessageConvert.get("上传文件解析异常(与系统默认坐标系不匹配)");
                    } else {
                        List<DictDataCodeEntity> listDataType = dictDataCodeService.list(
                                new LambdaQueryWrapper<DictDataCodeEntity>()
                                        .eq(DictDataCodeEntity::getDataTypeID, "73160096-67a5-11e7-8eb2-005056bb1c7e"));

                        Map<String, String> attr = shpReader.getAttributeType();
                        List<AttributeModel> lstattr = shpReader.getOneFeatureAttributeModel(0);
                        int i = 0;
                        for (AttributeModel item : lstattr) {
                            LayerFieldDto dto = new LayerFieldDto();
                            dto.setAttributeName(item.getAttributeName());
                            String dataType = item.getAttributeType().toString();
                            dto.setAttributeLength(item.getAttributeWidth().toString());
                            dto.setAttributePrecision(item.getAttributePrecision().toString());
//                            dto.setAttributePrecision(item.getAttributePrecision().toString().equals("11") ? "6" : item.getAttributePrecision().toString());
                            dto.setChineseName(item.getAttributeName());
                            dto.setAttributeSort(i);
                            i++;
                            switch (dataType) {
                                case "OFTDate":
                                    dto.setAttributeTypeName(utilityMessageConvert.get("时间型"));
                                    break;
                                case "OFTDateTime":
                                    dto.setAttributeTypeName(utilityMessageConvert.get("时间型"));
                                    break;
                                case "OFTInteger":
                                    dto.setAttributeTypeName(utilityMessageConvert.get("短整型"));
                                    break;
                                case "OFTIntegerList":
                                    dto.setAttributeTypeName(utilityMessageConvert.get("长整型"));
                                    break;
                                case "OFTInt64":
                                    dto.setAttributeTypeName(utilityMessageConvert.get("长整型"));
                                    break;
                                case "OFTReal":
                                    if (item.getAttributeWidth() <= 13) {
                                        dto.setAttributeTypeName(utilityMessageConvert.get("单浮点型"));
                                    } else {
                                        dto.setAttributeTypeName(utilityMessageConvert.get("双浮点型"));
                                    }
                                    break;
                                case "OFTString":
                                    dto.setAttributeTypeName(utilityMessageConvert.get("字符型"));
                                    break;
                                case "OFTTime":
                                    dto.setAttributeTypeName(utilityMessageConvert.get("时间型"));
                                    break;
                                default:
                                    dto.setAttributeTypeName(utilityMessageConvert.get("字符型"));
                                    break;
                            }

                            // 使用lambda表达式替代C#的匿名委托
                            DictDataCodeEntity dicEntity = listDataType.stream()
                                    .filter(entity -> entity.getCodeName().equals(dto.getAttributeTypeName()))
                                    .findFirst()
                                    .orElse(null);

                            if (dicEntity != null) {
                                dto.setAttributeType(dicEntity.getId());
                            }

                            listDTO.add(dto);
                        }
                    }
                }
            } catch (Exception ex) {
                status = true;
                message = utilityMessageConvert.get("上传文件解析异常(上传的矢量文件无效)");
            }
        } else {
            status = true;
            message = utilityMessageConvert.get("上传文件解析异常(上传必要文件不存在或不完整)");
        }

        LayerFieldListDto lstDTO = new LayerFieldListDto();
        lstDTO.setLayerField(listDTO);
        lstDTO.setMessage(message);
        lstDTO.setError(status);
        lstDTO.setDataType(dataTypes);
        return lstDTO;
    }

    @Override
    public boolean createTable(String layerID) {
        try {
            boolean bFlag = false;
            // 获取图层内容
            LayerContentEntity layerContent = layerMapper.selectById(layerID);

            String strSql1 = "";
            String strSql2 = "";
            ListResultOutput<LayerFieldDto> query = getDetailByLayerID(layerID);

            if (query.getItem() != null && !query.getItem().isEmpty()) {
                // 获取数据库配置（假设使用 Spring Environment 读取配置）
                String config = appSettingConfig.getDatabase();
                int startIndex = config.lastIndexOf(":");
                String dataBase = config.substring(0, config.lastIndexOf(":"));
                String dataBaseType = config.substring(startIndex + 1);

                // 根据数据库类型生成 SQL
                switch (dataBase) {
                    case "MySQL":
                        strSql1 = mySqlTableCreateSql(query, dataBase, dataBaseType);
                        break;
                    default:
                        // 其他数据库类型扩展位置
                        break;
                }

                // 执行 SQL 创建表
                if (Func.isNotBlank(strSql1) && layerContent != null) {
                    String strPostSQL = postGISTableCreateSql(query, layerContent.getLayerAttrTable());
//                    PostgresVectorHelper pvh = new PostgresVectorHelper();
//                    bFlag = pvh.executeSQL(strPostSQL, layerContent.getLayerAttrTable());
                    dynamicTableMapper.dynamicUpdateExecute(strPostSQL);
                }
            }
            return bFlag;


        } catch (Exception ex) {
            log.error(ex.getMessage());
        }

        return false;
    }

    @Override
    public List<LayerFieldDto> getAllList() {
        try {
            List<LayerFieldEntity> query = this.list();
            return BeanUtil.copy(query, LayerFieldDto.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public LayerFieldOutputDto GetDetailById(String id) {
        try {
            LayerFieldEntity query = this.getById(id);
            return BeanUtil.copy(query, LayerFieldOutputDto.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<LayerFieldDto> GetDetailByLayerID(String layerID) {
        try {
            LambdaQueryWrapper<LayerFieldEntity> queryWrapper = new LambdaQueryWrapper<LayerFieldEntity>()
                    .eq(LayerFieldEntity::getLayerID, layerID)
                    .orderByAsc(LayerFieldEntity::getAttributeSort);
            List<LayerFieldEntity> list = this.list(queryWrapper);
            List<LayerFieldDto> layerFields = BeanUtil.copy(list, LayerFieldDto.class);

            List<String> attributeTypes = layerFields.stream()
                    .map(LayerFieldEntity::getAttributeType)
                    .collect(Collectors.toList());
            String config = appSettingConfig.getDatabase();
            int startIndex = config.lastIndexOf(":");
            String dataBaseType = config.substring(config.lastIndexOf(":") + 1);
            List<DictDataCodeEntity> dicDataCodeEntitys = dictDataCodeService.list().stream()
                    .filter(q -> dataBaseType.equals(q.getDataTypeID())
                            && attributeTypes.contains(q.getKeywords()))
                    .collect(Collectors.toList());

            layerFields.forEach(t -> {
                dicDataCodeEntitys.stream()
                        .filter(m -> Objects.equals(m.getKeywords(), t.getAttributeType()))
                        .findFirst()
                        .ifPresent(entity -> {
                            if (entity.getCodeValue() != null) {
                                t.setAttributeTypeName(entity.getCodeValue());
                                t.setCodeName(entity.getCodeName());
//                                t.setAttributeName(t.getAttributeName().toLowerCase());
                            }
                        });
            });
            return layerFields;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public LayerFieldDto insert(LayerFieldInputDto input) {
        try {
            input.setId(UUID.randomUUID().toString());
            LayerFieldEntity layerField = BeanUtil.copy(input, LayerFieldEntity.class);
            assert layerField != null;
            layerField.setCreateDT(LocalDateTime.now());
            boolean save = this.save(layerField);
            return BeanUtil.copy(layerField, LayerFieldDto.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public LayerFieldDto insertSync(LayerFieldInputDto input) {
        try {
            input.setId(UUID.randomUUID().toString());
            PostgresVectorHelper helper = new PostgresVectorHelper();
            if (input.getProperties().getDataSource() != null && input.getProperties().getOriginalToChange() != null) {
                LayerContentEntity temp = layerMapper.selectById(input.getLayerID());
                StringBuilder kl = new StringBuilder();
                for (String s : input.getProperties().getDataSource()) {
                    kl.append(s).append(input.getProperties().getSymol());
                }
                String klStr = kl.substring(0, kl.toString().length() - 1);
                String sqlInsert = String.format("ALTER TABLE %s ADD COLUMN \"%s\" VARCHAR(200) NULL",
                        temp.getLayerAttrTable(), klStr);
                helper.executeSQL(sqlInsert);

                for (String s : input.getProperties().getOriginalToChange()) {
                    String[] tempc = s.split("|");
                    if (tempc.length > 2) {
                        tempc[1] = "";
                    }
                    String sqlReplace = String.format("UPDATE %s SET \"%s\" = REPLACE(\"%s\", '%s', '%s')",
                            temp.getLayerAttrTable(), klStr, klStr, tempc[0], tempc[1]);
                    helper.executeSQL(sqlReplace);
                }
                input.setNewFields(klStr);
            } else {
                LayerContentEntity temp = layerMapper.selectById(input.getLayerID());
                String sqlInsert = String.format("ALTER TABLE %s ADD COLUMN \"%s\" VARCHAR(50) NULL",
                        temp.getLayerAttrTable(), input.getAttributeName());
                helper.executeSQL(sqlInsert);
            }
            LayerFieldEntity entity = BeanUtil.copy(input, LayerFieldEntity.class);
            assert entity != null;
            entity.setCreateDT(LocalDateTime.now());
            boolean save = this.save(entity);
            if (save) {
                return BeanUtil.copy(entity, LayerFieldDto.class);
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean MultiInsert(List<LayerFieldInputDto> listInput) {
        try {
            String layerId = "";

            List<LayerFieldEntity> layerFieldEntities = BeanUtil.copy(listInput, LayerFieldEntity.class);
            return saveBatch(layerFieldEntities);
        } catch (Exception e) {
            throw new RuntimeException("批量插入图层字段失败");
        }
    }

    @Override
    public LayerFieldDto update(LayerFieldInputDto input) {
        try {
            if (input.getProperties() != null && input.getProperties().getDataSource() != null
                    && input.getProperties().getOriginalToChange() != null
                    && !input.getProperties().getOriginalToChange().isEmpty()) {
                LayerContentEntity temp = layerMapper.selectById(input.getLayerID());
                PostgresVectorHelper helper = new PostgresVectorHelper();
                for (String s : input.getProperties().getDataSource()) {
                    for (String t : input.getProperties().getOriginalToChange()) {
                        String[] tempc = t.split("\\|", 2);
                        String oldValue = tempc[0].trim();
                        String newValue = (tempc.length > 1) ? tempc[1].trim() : "";

                        String sqlReplace = String.format("UPDATE %s SET \"%s\" = REPLACE(\"%s\", '%s', '%s')",
                                temp.getLayerAttrTable(), s, s, oldValue, newValue);
//                        helper.executeSQL(sqlReplace);
                        dynamicTableMapper.dynamicUpdateExecute(sqlReplace);
                    }
                }
            }
            LayerFieldEntity entity = BeanUtil.copy(input, LayerFieldEntity.class);
            boolean query = this.updateById(entity);
            if (query) {
                return BeanUtil.copy(entity, LayerFieldDto.class);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    @Override
    public boolean delete(String id) {
        return this.removeById(id);
    }

    @Override
    public boolean deleteByLayerID(String layerID) {
        boolean b = false;
        try {
            b = this.remove(new LambdaQueryWrapper<LayerFieldEntity>().eq(LayerFieldEntity::getLayerID, layerID));
        } catch (Exception e) {
            log.error("根据图层编号删除属性失败！");
            return false;
        }
        return b;
    }

    public String postGISTableCreateSql(ListResultOutput<LayerFieldDto> listDto, String tableName) {
        // 从配置获取数据库信息（假设使用自定义配置工具类）
        String config = appSettingConfig.getDatabase();
        int startIndex = config.lastIndexOf(":");
        String dataBaseType = config.substring(config.lastIndexOf(":") + 1);

        // 使用 StringBuilder 拼接 SQL（比 String 拼接更高效）
        StringBuilder strSql = new StringBuilder();
        strSql.append(String.format("CREATE TABLE public.%s(sid SERIAL primary key,guid varchar,", tableName));

        for (LayerFieldDto item : listDto.getItem()) {
            String attrTypeName = item.getAttributeTypeName();
            if (!Func.isBlank(attrTypeName)) {
                // 调整数据类型映射（与 C# 逻辑一致）
                String adjustedType;
                switch (attrTypeName.toLowerCase()) {
                    case "double":
                    case "float":
                        adjustedType = "double precision";
                        break;
                    case "int":
                    case "long integer":
                    case "short integer":
                        adjustedType = "integer";
                        break;
                    case "datetime":
                        adjustedType = "date";
                        break;
                    default:
                        adjustedType = attrTypeName;
                }
                strSql.append(String.format("\"%s\" %s,", item.getAttributeName(), adjustedType));
            }
        }

        // 移除末尾多余的逗号
        if (strSql.length() > 0 && strSql.charAt(strSql.length() - 1) == ',') {
            strSql.deleteCharAt(strSql.length() - 1);
        }

        // 添加几何字段并闭合表定义
        strSql.append(",geom geometry)");
        return strSql.toString();
    }


    public String mySqlTableCreateSql(ListResultOutput<LayerFieldDto> listDto, String dataBase, String dataBaseType) {
        StringBuilder strSql = new StringBuilder();

        strSql.append("CREATE TABLE `@tableName1` (");
        strSql.append("`sid` varchar(36) NOT NULL, ");
        for (LayerFieldDto item : listDto.getItem()) {
            String attrType = item.getAttributeType();
            // var entity = _IDicDataCodeRepository.GetAll().Where(q => q.DataTypeID == dataBaseType && q.Keywords == attrType).FirstOrDefault();
            if (!Func.isBlank(item.getAttributeTypeName())) {
                strSql.append(sqlColumnType(dataBase, item.getCodeName(), item.getAttributeTypeName(), item)).append(",");
            }
        }
        // 移除末尾多余的逗号
        if (strSql.charAt(strSql.length() - 1) == ',') {
            strSql.setLength(strSql.length() - 1);
        }
        strSql.append(", PRIMARY KEY (`sid`)) ENGINE=InnoDB DEFAULT CHARSET=utf8;");

        strSql.append("CREATE TABLE `@tableName2` (");
        strSql.append("`sid` varchar(36) NOT NULL, ");
        strSql.append("`DataID` varchar(36) NOT NULL, ");
        strSql.append("`geom` GEOMETRY NOT NULL");
        strSql.append(", PRIMARY KEY (`sid`)) ENGINE=InnoDB DEFAULT CHARSET=utf8;");

        return strSql.toString();
    }

    public String sqlColumnType(String dataBase, String columnType, String columnCode, LayerFieldDto dto) {
        if (utilityMessageConvert.get("字符型").equals(columnType)) {
            if ("MySQL".equals(dataBase)) {
                return "`" + dto.getAttributeName() + "` " + columnCode + "(" + dto.getAttributeLength() + ") " + dto.getAttributeDefault() + dto.getAttributeIsNull();
            } else {
                return "";
            }
        } else if (utilityMessageConvert.get("短整型").equals(columnType)) {
            if ("MySQL".equals(dataBase)) {
                return "`" + dto.getAttributeName() + "` " + columnCode + "(11)";
            } else {
                return "";
            }
        } else if (utilityMessageConvert.get("长整型").equals(columnType)) {
            if ("MySQL".equals(dataBase)) {
                return "`" + dto.getAttributeName() + "` " + columnCode + "(11)";
            } else {
                return "";
            }
        } else if (utilityMessageConvert.get("单浮点型").equals(columnType)) {
            if ("MySQL".equals(dataBase)) {
                return "`" + dto.getAttributeName() + "` " + columnCode + "(" + dto.getAttributeLength() + "," + dto.getAttributePrecision() + ")";
            } else {
                return "";
            }
        } else if (utilityMessageConvert.get("双浮点型").equals(columnType)) {
            if ("MySQL".equals(dataBase)) {
                return "`" + dto.getAttributeName() + "` " + columnCode + "(" + dto.getAttributeLength() + "," + dto.getAttributePrecision() + ")";
            } else {
                return "";
            }
        } else if (utilityMessageConvert.get("时间型").equals(columnType)) {
            if ("MySQL".equals(dataBase)) {
                return "`" + dto.getAttributeName() + "` " + columnCode;
            } else {
                return "";
            }
        }
        return "";
    }


    /**
     * 根据图层ID获取字段详情（Java转换版本）
     *
     * @param layerID 图层ID
     * @return 字段详情列表
     */
    public ListResultOutput<LayerFieldDto> getDetailByLayerID(String layerID) {
        try {
            // 1. 查询图层字段列表并按排序字段排序
            List<LayerFieldEntity> entities = baseMapper.selectList(new LambdaQueryWrapper<LayerFieldEntity>()
                    .eq(LayerFieldEntity::getLayerID, layerID));

            // 2. 转换为DTO列表
            List<LayerFieldDto> layerFields = entities.stream()
                    .map(v -> BeanUtil.copyProperties(v, LayerFieldDto.class))
                    .collect(Collectors.toList());

            // 3. 获取数据库配置（假设配置格式为 "数据库类型:数据库参数"）
            String config = appSettingConfig.getDatabase();
            int lastColonIndex = config.lastIndexOf(":");
            String dataBaseType = config.substring(lastColonIndex + 1);

            // 4. 查询数据字典（获取字段类型映射）
            List<String> attributeTypes = layerFields.stream()
                    .map(LayerFieldDto::getAttributeType)
                    .collect(Collectors.toList());

            List<DictDataCodeEntity> dicDataCodeEntities = dictDataCodeMapper.selectList(new LambdaQueryWrapper<DictDataCodeEntity>()
                    .eq(DictDataCodeEntity::getDataTypeID, dataBaseType)
                    .in(DictDataCodeEntity::getKeywords, attributeTypes)
            );

            // 5. 映射字段类型名称和编码
            layerFields.forEach(dto -> {
                dicDataCodeEntities.stream()
                        .filter(entity -> entity.getKeywords().equals(dto.getAttributeType()))
                        .findFirst()
                        .ifPresent(entity -> {
                            dto.setAttributeTypeName(entity.getCodeValue());
                            dto.setCodeName(entity.getCodeName());
                        });
            });

            return new ListResultOutput<>(layerFields);
        } catch (Exception ex) {
            throw new RuntimeException(ex.getMessage());
        }
    }


    /**
     * 匹配字典中的几何类型
     *
     * @param geomType     几何类型字符串
     * @param listGeomType 几何类型字典列表
     * @return 匹配的几何类型ID
     */
    public static String getGeomType(String geomType, List<DictDataCodeEntity> listGeomType) {
        String geomTypeID = "";
        if (geomType != null && !geomType.isEmpty()) {
            for (DictDataCodeEntity item : listGeomType) {
                if (geomType.toLowerCase().contains(item.getCodeValue().toLowerCase())) {
                    geomTypeID = item.getId();
                }
            }
        }
        return geomTypeID;
    }

    private ListResultOutput<DictDataCodeEntity> isFileExist(String filePath) {
        String name = filePath.substring(0, filePath.lastIndexOf("."));
        String fileFullName = Paths.get(System.getProperty("user.dir"), appSettingConfig.getUploadFilePath()).toString();
        List<DictDataCodeEntity> list = dictDataCodeService.list(new LambdaQueryWrapper<DictDataCodeEntity>()
                .eq(DictDataCodeEntity::getDataTypeID, "73160096-67a5-11e7-8eb2-005056bb1c7e"));

        try {
            if (!list.isEmpty()) {
                for (DictDataCodeEntity item : list) {
                    String fileName = name + "." + item.getCodeValue();
                    String uploadFilePath = "";
                    if (!fileName.contains(fileFullName)) {
                        uploadFilePath = Paths.get(fileFullName, new File(fileName).getName()).toString();
                    }
                    if (!new File(uploadFilePath).exists()) {
                    }
                }

            }
            return new ListResultOutput<>(list);
        } catch (Exception ex) {
            return new ListResultOutput<>();
        }
    }


}
