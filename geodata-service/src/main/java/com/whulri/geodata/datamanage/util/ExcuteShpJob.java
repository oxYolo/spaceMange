package com.whulri.geodata.datamanage.util;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.whulri.geodata.basecore.mapper.DataStyleMapper;
import com.whulri.geodata.basecore.mapper.DynamicTableMapper;
import com.whulri.geodata.basecore.mapper.LayerMapper;
import com.whulri.geodata.common.config.AppSettingConfig;
import com.whulri.geodata.common.constant.DictionaryIds;
import com.whulri.geodata.common.dto.dataStyle.StyleInfo;
import com.whulri.geodata.common.entity.DataStyleEntity;
import com.whulri.geodata.common.entity.LayerContentEntity;
import com.whulri.geodata.common.gdal.handler.GeoServer;
import com.whulri.geodata.common.util.GeoServerUtil;
import com.whulri.geodata.common.util.ShpReader;
import com.whulri.geodata.common.util.ThumbnailHelper;
import com.whulri.geodata.common.util.UtilityMessageConvert;
import com.whulri.geodata.datamanage.socket.SignalrClient;
import lombok.extern.slf4j.Slf4j;
import com.whulri.geodata.common.bridge.Func;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@Slf4j
public class ExcuteShpJob implements Runnable {

    @Resource
    private AppSettingConfig appSettingConfig;

    @Resource
    private GeoServer geoServer;

    @Resource
    private DynamicTableMapper dynamicTableMapper;

    @Resource
    private UtilityMessageConvert utilityMessageConvert;

    @Resource
    private ThumbnailHelper thumbnailHelper;

    @Resource
    private DataStyleMapper dataStyleMapper;

    @Resource
    private LayerMapper layerMapper;

    @Resource
    private SignalrClient signalrClient;  // Spring 管理的 bean，用于重新创建连接

    // 新增：用于回调通知的参数
    private SignalrClient connection;
    private String userId;
    private String msgType;
    private String guid;
    private String layerLogId;
    private String layerId;  // 当前处理的图层ID
    private boolean isNewConnection = false;  // 标记是否新创建的连接
    private int targetIsShow = 1;  // 完成后设置的 isShow 值，工具集生成的图层传 0

    public void setCallbackParams(SignalrClient connection, String userId, String msgType, String guid, String layerLogId, int targetIsShow) {
        this.connection = connection;
        this.userId = userId;
        this.msgType = msgType;
        this.guid = guid;
        this.layerLogId = layerLogId;
        this.isNewConnection = false;
        this.targetIsShow = targetIsShow;
    }

    /**
     * 确保连接可用，如果不可用则重新创建
     */
    private void ensureConnection() {
        if (connection == null) {
            // 重新创建连接
            signalrClient.start();
            connection = signalrClient;
            isNewConnection = true;
            log.info("已重新创建SignalR连接用于异步任务");
        }
    }

    /**
     * 发送进度消息到前端
     * @param progress 进度百分比 (0-100)
     * @param message 进度描述
     */
    private void sendProgress(int progress, String message) {
        if (connection != null && userId != null && msgType != null && guid != null) {
            WebSocketUtil.sendMsg(connection, userId, msgType, 9, progress, message, false, false, guid);
        }
    }

    /**
     * 发送错误消息到前端
     * @param message 错误描述
     */
    private void sendError(int progress, String message) {
        if (connection != null && userId != null && msgType != null && guid != null) {
            WebSocketUtil.sendMsg(connection, userId, msgType, 0, progress, message, true, true, guid);
        }
    }


    @Override
    public void run() {
        log.info("ShpFileReadJob running...");
        boolean taskSuccess = true;
        String errorMsg = "";
        SignalrClient workConnection = null;  // 任务专用的连接

        try {
            // 不管外部的 connection，直接创建全新的 SignalR 连接
            workConnection = new SignalrClient();
            workConnection.start();
            log.info("已为异步任务创建新的SignalR连接");

            // 使用 workConnection 替换 connection 供 sendProgress/sendError 使用
            this.connection = workConnection;

            taskSuccess = startReadShpFile();
        } catch (Exception e) {
            taskSuccess = false;
            errorMsg = e.getMessage();
            log.error("ShpFileReadJob 执行异常", e);
        }

        log.info("ShpFileReadJob finished..., success: {}", taskSuccess);

        // 任务完成后，发送WebSocket消息通知前端，并更新layerLog表
        if (connection != null && userId != null && msgType != null && guid != null) {
            if (taskSuccess) {
                WebSocketUtil.sendMsg(connection, userId, msgType, 10, 100, "导入成功", false, true, guid);

                // 更新layerLog表状态为成功
                if (layerLogId != null) {
                    updateLayerLogStatus(layerLogId, "1", null);
                }

                // 更新 isShow显示状态
                if (layerId != null) {
                    LayerContentEntity layer = layerMapper.selectById(layerId);
                    if (layer != null) {
                        layer.setIsShow(targetIsShow);
                        layerMapper.updateById(layer);
                        log.info("图层[{}] isShow 已更新为 {}", layerId, targetIsShow);
                    }
                }
            } else {
                String msg = errorMsg.isEmpty() ? "导入失败" : errorMsg;
                WebSocketUtil.sendMsg(connection, userId, msgType, 0, 100, msg, true, true, guid);

                // 更新layerLog表状态为失败
                if (layerLogId != null) {
                    updateLayerLogStatus(layerLogId, "2", msg);
                }
            }
        }

        // 关闭任务专用的连接，避免资源泄露
        if (workConnection != null) {
            try {
                workConnection.close();
                log.info("已关闭异步任务的SignalR连接");
            } catch (Exception e) {
                log.warn("关闭SignalR连接失败", e);
            }
        }
    }

    /**
     * 更新layerLog表状态
     */
    private void updateLayerLogStatus(String layerLogId, String status, String reason) {
        try {
            String strSQL = "update sdms_layer_log set status = '" + status + "'";
            if (reason != null) {
                strSQL += ", reason = '" + reason + "'";
            }
            strSQL += " where id = '" + layerLogId + "'";
            dynamicTableMapper.dynamicUpdateExecute(strSQL);
        } catch (Exception e) {
            log.error("更新layerLog状态失败: {}", layerLogId, e);
        }
    }

    /**
     * 读取SHP文件
     * @return 是否全部成功
     */
    public boolean startReadShpFile() {
        List<Map<String, Object>> dt = GetShpUploadFile();
        List<Map<String, Object>> dtDataType = GetFileType();
        List<Map<String, Object>> dtFieldDict = GetLayerAttributeDict();
        String msg = "";
        boolean allSuccess = true;

        if (!dt.isEmpty()) {
            // 提取 layerId 供成功后更新 isShow 使用
            this.layerId = dt.get(0).get("layerid").toString();

            for (Map<String, Object> map : dt) {
                readShpFileStatus(map.get("id").toString());
            }

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
            for (Map<String, Object> map : dt) {
                LocalDateTime start = LocalDateTime.now();
                JSONObject js = readShpFile(map, dtDataType, dtFieldDict);
                Boolean success = js.getBoolean("status");
                msg = js.getString("message");
                if (!success) {
                    allSuccess = false;
                }
                LocalDateTime end = LocalDateTime.now();
                updataShpFileRead(map.get("id").toString(), success, msg, dtf.format(start), dtf.format(end));
            }

        }
        return allSuccess;
    }

    /**
     * 更新文件读取状态
     *
     * @param id
     * @param status
     * @param msg
     * @param strStart
     * @param strEnd
     */
    private void updataShpFileRead(String id, Boolean status, String msg, String strStart, String strEnd) {
        String strSQL = "update sdms_layer_readlog set ";

        strSQL += "ReadStartDT = '" + strStart + "',ReadEndDT='" + strEnd + "',";

        if (!status) {
            strSQL += " readStatus = '2', message = '" + msg + "'";
        } else {
            strSQL += " readStatus = '1', message = '读取成功' ";
        }

        strSQL += ",MsgStatus = '0' where Id = '" + id + "'";

        dynamicTableMapper.dynamicUpdateExecute(strSQL);
    }

    /**
     * 读取文件
     *
     * @param dr
     * @param dtDataType
     * @param dtFieldDict
     * @return
     */
    private JSONObject readShpFile(Map<String, Object> dr, List<Map<String, Object>> dtDataType, List<Map<String, Object>> dtFieldDict) {
        String layerId = dr.get("layerid").toString(),
                filePath = dr.get("shpfilename").toString(),
                tableName1 = dr.get("layerattrtable").toString(),
                tableName2 = dr.get("layerspatialtable").toString(),
                layerName = dr.get("layername").toString(),
                folderName = (dr.get("foldername") != null) ? dr.get("foldername").toString().replace("#", "\\") : "";
        boolean status = false;
        String message = "";
        JSONObject jsonObject = new JSONObject();

        JSONObject fileExist = isFileExist(filePath, dtDataType, folderName);
        filePath = fileExist.getString("filePath");
        Boolean isExist = fileExist.getBoolean("isExist");
        if (isExist) {
            ShpReader shpReader = new ShpReader(filePath);
            try {
                // 检查矢量文件的有效性
                if (!shpReader.isValidDataSource()) {
                    status = false;
                    message = utilityMessageConvert.get("上传文件解析异常(上传的矢量文件无效)");
                } else {
                    String tableName = "";
                    String layerType = "";
                    List<Map<String, Object>> dtAttr = getLayerAttribute(layerId);

                    // 建表
                    Map<String, String> attr = shpReader.getAttributeType();
                    List<String> shpAttr = new ArrayList<>();
                    Map<String, String> hashTable = new HashMap<>();

                    for (Map<String, Object> map : dtAttr) {
                        String name = map.get("attributename").toString();
                        if (attr.containsKey(name)) {
                            shpAttr.add(name);
                            hashTable.put(name, attr.get(name));
                        }
                    }

                    if (shpAttr.size() <= 0 || shpAttr.size() != dtAttr.size() || attr.size() != shpAttr.size()) {
                        status = false;
                        message = utilityMessageConvert.get("上传文件解析异常(属性无匹配或只部分匹配)");
                    } else {
                        /**
                         * 数据入库
                         */
                        sendProgress(92, "正在导入矢量数据到数据库...");
                        Map<String, Double> bbox = shpReader.getLayerBBox();
                        int srid = shpReader.getSrid();
                        boolean success = importToDataBase(filePath, tableName1, 4326);

                        if (success) {
                            // 动态获取 Shapefile 的实际几何类型，而不是用字典里的固定值
                            String actualGeomType = shpReader.getGeomType();
                            log.info("Shapefile 实际几何类型: {}", actualGeomType);
                            layerType = convertGeomTypeForPublish(actualGeomType);
                            log.info("转换后的发布类型: {}", layerType);

                            /**
                             *  发布 geoserver
                             */
                            sendProgress(95, "正在发布GeoServer图层...");
                            JSONObject object = publishGeoServer(tableName1, layerName, message, layerId, layerType);
                            success = object.getBoolean("status");
                            message = object.getString("message");

                            if (success) {
                                // 更新状态和坐标
                                String strUpdateSQL = "update sdms_layer set UploadStatus = '1'";
                                String geomTypeId = resolveGeomTypeId(actualGeomType);
                                if (geomTypeId != null) {
                                    // 按实际几何类型回填 datatype（点/线/面字典 id），供目录树分组与类型筛选使用
                                    strUpdateSQL += ",datatype = '" + geomTypeId + "'";
                                }
                                String strBBox = "";
                                if (!bbox.isEmpty()) {
                                    BigDecimal currentMinX = toBigDecimal(bbox.get("MinX"));
                                    BigDecimal newMinX = toBigDecimal(dr.get("minx"));
                                    BigDecimal lastMinX = newMinX == null ? new BigDecimal("79228162514264337593543950335") : newMinX;
                                    BigDecimal MinX = currentMinX.min(lastMinX);

                                    BigDecimal currentMaxX = toBigDecimal(bbox.get("MaxX"));
                                    BigDecimal newMaxX = toBigDecimal(dr.get("maxx"));
                                    BigDecimal lastMaxX = newMaxX == null ? new BigDecimal("-79228162514264337593543950335") : newMaxX;
                                    BigDecimal MaxX = currentMaxX.min(lastMaxX);

                                    BigDecimal currentMinY = toBigDecimal(bbox.get("MinY"));
                                    BigDecimal newMinY = toBigDecimal(dr.get("miny"));
                                    BigDecimal lastMinY = newMinY == null ? new BigDecimal("79228162514264337593543950335") : newMinY;
                                    BigDecimal MinY = currentMinY.min(lastMinY);

                                    BigDecimal currentMaxY = toBigDecimal(bbox.get("MaxY"));
                                    BigDecimal newMaxY = toBigDecimal(dr.get("maxy"));
                                    BigDecimal lastMaxY = newMaxY == null ? new BigDecimal("-79228162514264337593543950335") : newMaxY;
                                    BigDecimal MaxY = currentMaxY.min(lastMaxY);

                                    strUpdateSQL += ",minx = " + MinX + ",miny=" + MinY + ",maxx=" + MaxX + ",maxy=" + MaxY;
                                    strBBox = MinX + "," + MinY + "," + MaxX + "," + MaxY;

                                    if (success && Func.isNotEmpty(dr.get("minx"))) {
                                        String bboxStr = String.format("%s, %s, %s, %s", MinX, MinY, MaxX, MaxY);
                                        success = geoServer.modifyLayerBBox(tableName1, layerName, bboxStr);
                                    }
                                }
                                if (success) {
                                    strUpdateSQL += " where Id='" + dr.get("layerid").toString() + "'";
                                    dynamicTableMapper.dynamicUpdateExecute(strUpdateSQL);

                                    // 生成缩略图
                                    sendProgress(97, "正在生成缩略图...");
                                    String imagePath = thumbnailHelper.createThumbnail(tableName1, "layer", strBBox, "", "");

                                    sendProgress(98, "正在更新地图缩略图...");
                                    updateMapThumbnail(layerId);

                                    if (Func.isEmpty(imagePath)) {
                                        status = false;
                                        message = utilityMessageConvert.get("上传文件解析异常(下载缩略图)");
                                    } else {
                                        status = true;
                                        message = utilityMessageConvert.get("上传文件解析数据保存成功");
                                    }

                                } else {
                                    status = false;
                                    message = utilityMessageConvert.get("上传文件解析异常(GeoServer地图发布异常)");
                                }
                            } else {
                                status = false;
                                message = utilityMessageConvert.get("上传文件解析异常(GeoServer地图发布异常)");
                            }
                        } else {
                            status = false;
                            message = utilityMessageConvert.get("上传文件解析异常(MySQL:请查看SHP文件属性值类型或长度与系统建置是否匹配)");
                        }
                    }
                }
            } catch (Exception e) {
                status = false;
                message = utilityMessageConvert.get("上传文件解析异常");
            } finally {
                shpReader.close();
            }
        } else {
            status = false;
            message = utilityMessageConvert.get("上传文件解析异常(上传必要文件不存在或不完整)");
        }
        jsonObject.put("status", status);
        jsonObject.put("message", message);
        return jsonObject;
    }

    /**
     * 地图缩略图下载
     *
     * @param layerId
     */
    private boolean updateMapThumbnail(String layerId) {
        try {
            List<Map<String, Object>> dt = GetMapByRelLayer(layerId);
            if (!dt.isEmpty()) {
                String strBBox = "";
                for (Map<String, Object> map : dt) {
                    strBBox = map.get("minx").toString() + "," + map.get("mixy").toString() + "," + map.get("maxx").toString() + "," + map.get("maxy").toString();
                    String imagePath = thumbnailHelper.createThumbnail(map.get("MapEnName").toString(), "map", strBBox, "", "");
                }
            }
            return true;
        } catch (Exception e) {
            log.error(e.getMessage());
            return false;
        }
    }

    // 安全转换 Object → BigDecimal
    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;

        if (value instanceof BigDecimal) return (BigDecimal) value;

        if (value instanceof Number) {
            // 避免 double 精度问题：先转字符串
            return new BigDecimal(value.toString());
        }

        if (value instanceof String) {
            String str = ((String) value).trim();
            if (str.isEmpty()) return null;
            try {
                return new BigDecimal(str);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        return null; // 不支持的类型
    }

    /**
     * 判断上传文件是否完整
     *
     * @param filePath
     * @param dtDataType
     * @param folderName
     * @return
     */
    public JSONObject isFileExist(String filePath, List<Map<String, Object>> dtDataType, String folderName) {
        String name = filePath.substring(0, filePath.lastIndexOf("."));
        String fileFullName = Paths.get(System.getProperty("user.dir"), appSettingConfig.getUploadFilePath()).toString();
        String filePhysicPath = Paths.get(fileFullName, folderName, Paths.get(filePath).getFileName().toString()).toString();

        boolean isExist = false;
        JSONObject js = new JSONObject();
        js.put("filePath", filePhysicPath);

        if (!dtDataType.isEmpty()) {
            for (Map<String, Object> map : dtDataType) {
                String fileName = name + "." + map.get("codevalue");
                String uploadFilePath = "";
                if (!fileName.contains(fileFullName)) {
                    uploadFilePath = Paths.get(fileFullName, folderName, Paths.get(fileName).getFileName().toString()).toString();
                }
                if (!Files.exists(Path.of(uploadFilePath))) {
                    js.put("isExist", isExist);
                    return js;
                }
            }
            isExist = true;
            js.put("isExist", isExist);
            return js;
        } else {
            js.put("isExist", isExist);
            return js;
        }
    }

    public void readShpFileStatus(String id) {
        String strSQL = "update sdms_layer_readlog set readStatus = '3'";
        strSQL += " where Id = '" + id + "'";
        dynamicTableMapper.dynamicUpdateExecute(strSQL);
    }

    /**
     * 图层属性字典
     *
     * @return
     */
    public List<Map<String, Object>> GetLayerAttributeDict() {
        String strSQL = "select a.* from public.sdms_layerfielddict a ";
        List<Map<String, Object>> list = dynamicTableMapper.dynamicExecute(strSQL);
        return list;
    }

    /**
     * 获取文件类型
     *
     * @return
     */
    public List<Map<String, Object>> GetFileType() {
        String strSQL = "select a.* from public.sdms_dictdatacode a where a.DataTypeID = 'b9ef9c7c-67b3-11e7-8eb2-005056bb1c7e' ";
        List<Map<String, Object>> list = dynamicTableMapper.dynamicExecute(strSQL);
        return list;
    }

    /**
     * 取待解析的SHP文件
     *
     * @return
     */
    public List<Map<String, Object>> GetShpUploadFile() {
        String strSQL = "select a.LayerID,a.ShpFileName,b.LayerAttrTable,b.LayerSpatialTable,b.MinX,b.MinY,b.MaxX,b.MaxY,b.UploadStatus,c.CodeName,a.Id,a.CreateBy,b.LayerName,a.FolderName from public.sdms_layer_readlog a,public.sdms_layer b,sdms_dictdatacode c where a.LayerID = b.Id and b.DataType = c.Id and a.ReadStatus = '0' ORDER BY a.CreateDT DESC LIMIT 1";
        List<Map<String, Object>> list = dynamicTableMapper.dynamicExecute(strSQL);
        return list;
    }

    public List<Map<String, Object>> GetMapByRelLayer(String layerID) {
        String strSQL = "select b.MapEnName,b.MinX,b.MinY,b.MaxX,b.MaxY from public.sdms_map_releation a,public.sdms_map b where a.MapID = b.Id and a.DataConfigID = '" + layerID + "' ";
        return dynamicTableMapper.dynamicExecute(strSQL);
        //return mysql.ExecuteQuery(strSQL);
    }

    public List<Map<String, Object>> getLayerAttribute(String layerID) {
        String sql = "SELECT a.*, b.codevalue AS attributetypename " +
                "FROM sdms_layerfield a " +
                "JOIN sdms_dictdatacode b ON a.attributetype = b.id " +
                "WHERE a.layerid = '" + layerID + "'";
        return dynamicTableMapper.dynamicExecute(sql);
    }

    /**
     * 发布图层到 GeoServer
     *
     * @param tableName  表名（GeoServer 中的图层名）
     * @param layerName  图层显示名称
     * @param messageRef 错误消息引用
     * @param layerID    图层 ID（用于查询属性）
     * @param layerType  几何类型（如 "POINT", "LINESTRING", "POLYGON" 等）
     * @return 是否发布成功
     */
    private JSONObject publishGeoServer(String tableName,
                                        String layerName,
                                        String messageRef,
                                        String layerID,
                                        String layerType) {
        boolean status = false;
        boolean success = true;
        JSONObject object = new JSONObject();

        try {
            // 检查并创建工作空间
            if (!geoServer.isExistWorkSpace(appSettingConfig.getGeoWorkspace())) {
                geoServer.addWorkSpace(appSettingConfig.getGeoWorkspace());
            }

            // 检查并创建 PostGIS 数据存储
            if (!geoServer.isExsitDataStore(appSettingConfig.getGeoWorkspace(), appSettingConfig.getGeoDatastore())) {
                geoServer.addDataStore(
                        appSettingConfig.getGeoWorkspace(),
                        appSettingConfig.getGeoDatastore(),
                        appSettingConfig.getPostGisHost(),
                        appSettingConfig.getPostGisPort(),
                        appSettingConfig.getPostGisDB(),
                        appSettingConfig.getPostGisUser(),
                        appSettingConfig.getPostGisPwd()
                );
            }

            // 获取图层字段及类型
            Map<String, String> fieldAndType = new HashMap<>();

            List<Map<String, Object>> attributeList = getLayerAttribute(layerID);
            for (Map<String, Object> row : attributeList) {
                String attrName = row.get("attributename").toString().toLowerCase();
                String attrTypeName = (String) row.get("attributetypename");
                fieldAndType.put(attrName, getGeoFieldType(attrTypeName));
            }

            // 添加固定字段
//            fieldAndType.put("gid", getGeoFieldType("VARCHAR"));
            fieldAndType.put("geom", getGeoFieldType(layerType));

            // 如果图层不存在，则创建
            if (!geoServer.isExistLayer(tableName, appSettingConfig.getGeoWorkspace(), appSettingConfig.getGeoDatastore())) {
                success = geoServer.addLayer(
                        tableName,
                        layerName,
                        fieldAndType,
                        appSettingConfig.getGeoWorkspace(),
                        appSettingConfig.getGeoDatastore(),
                        appSettingConfig.getEpsg(),
                        null
                );

                if (success) {
                    log.info("样式入库！！！");
                    sendProgress(96, "正在处理图层样式...");
                    String styleType = getDefaultStyle(layerType);
                    // 判断是否已经有了style
                    LambdaQueryWrapper<DataStyleEntity> queryWrapper = new LambdaQueryWrapper<>();
                    queryWrapper.eq(DataStyleEntity::getStyleDefaultLayer, layerID);
                    DataStyleEntity styleEntity = dataStyleMapper.selectOne(queryWrapper);
                    // 如果已经有style按照style上传到geoserver
                    if (styleEntity != null) {
                        styleEntity.setStyleName(tableName);
                        dataStyleMapper.updateById(styleEntity);
                        if (Func.isNotEmpty(styleEntity.getStyleContent())) {
                            sendProgress(96, "正在上传自定义样式到GeoServer...");
                            success = geoServer.modifyLayerStyle(
                                    tableName,
                                    Collections.emptyList(),
                                    tableName
                            );
                            GeoServerUtil.bindStyleToLayer("http://"+appSettingConfig.getGeoServerIP()+":"+appSettingConfig.getGeoServerPort()+"/geoserver",
                                    appSettingConfig.getGeoServerUser(),
                                    appSettingConfig.getGeoServerPwd(),
                                    appSettingConfig.getGeoWorkspace(),
                                    tableName,
                                    tableName,
                                    styleEntity.getStyleContent());
                        }
                    }else {
                        // 否则走默认样式
                        DataStyleEntity entity = new DataStyleEntity();
                        entity.setId(UUID.randomUUID().toString());
                        entity.setStyleName(tableName);
                        entity.setStyleDefaultLayer(layerID);
                        
                        entity.setCreateDT(LocalDateTime.now());
                        entity.setStyleConfigType("1");
                        if (Func.isNotEmpty(styleType)) {
                        if (styleType.equals("point")) {
                            StyleInfo styleInfo = new StyleInfo();
                            styleInfo.setIconSize("10");
                            styleInfo.setFillTransparency(1.0);
                            styleInfo.setLineTransparency(1.0);
                            styleInfo.setPointType("square");
                            styleInfo.setPolygonColor(hexToRgb("#FF0000"));
                            String jsonString = JSONObject.toJSONString(styleInfo);
                            entity.setStyleRenderRule(jsonString);
                            entity.setStyleDataType(DictionaryIds.GEOM_POINT);
                            entity.setStyleContent("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                    "<StyledLayerDescriptor version=\"1.0.0\" \n" +
                                    " xsi:schemaLocation=\"http://www.opengis.net/sld StyledLayerDescriptor.xsd\" \n" +
                                    " xmlns=\"http://www.opengis.net/sld\" \n" +
                                    " xmlns:ogc=\"http://www.opengis.net/ogc\" \n" +
                                    " xmlns:xlink=\"http://www.w3.org/1999/xlink\" \n" +
                                    " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                                    "  <!-- a Named Layer is the basic building block of an SLD document -->\n" +
                                    "  <NamedLayer>\n" +
                                    "    <Name>default_point</Name>\n" +
                                    "    <UserStyle>\n" +
                                    "    <!-- Styles can have names, titles and abstracts -->\n" +
                                    "      <Title>Red Square Point</Title>\n" +
                                    "      <Abstract>A sample style that draws a red square point</Abstract>\n" +
                                    "      <!-- FeatureTypeStyles describe how to render different features -->\n" +
                                    "      <!-- A FeatureTypeStyle for rendering points -->\n" +
                                    "      <FeatureTypeStyle>\n" +
                                    "        <Rule>\n" +
                                    "          <Name>rule1</Name>\n" +
                                    "          <Title>Red Square Point</Title>\n" +
                                    "          <Abstract>A 6 pixel square with a red fill and no stroke</Abstract>\n" +
                                    "            <PointSymbolizer>\n" +
                                    "              <Graphic>\n" +
                                    "                <Mark>\n" +
                                    "                  <WellKnownName>square</WellKnownName>\n" +
                                    "                  <Fill>\n" +
                                    "                    <CssParameter name=\"fill\">#FF0000</CssParameter>\n" +
                                    "                  </Fill>\n" +
                                    "                </Mark>\n" +
                                    "              <Size>6</Size>\n" +
                                    "            </Graphic>\n" +
                                    "          </PointSymbolizer>\n" +
                                    "        </Rule>\n" +
                                    "      </FeatureTypeStyle>\n" +
                                    "    </UserStyle>\n" +
                                    "  </NamedLayer>\n" +
                                    "</StyledLayerDescriptor>\n" +
                                    "\n");
                        } else if (styleType.equals("line")) {
                            StyleInfo styleInfo = new StyleInfo();
//                            styleInfo.setIsOutline(true);
                            styleInfo.setLineWidth(1.0);
                            styleInfo.setLineType(1);
                            styleInfo.setOutlineColor(hexToRgb("#0000FF"));
                            String jsonString = JSONObject.toJSONString(styleInfo);
                            entity.setStyleRenderRule(jsonString);

                            entity.setStyleDataType(DictionaryIds.GEOM_LINE);
                            entity.setStyleContent("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                    "<StyledLayerDescriptor version=\"1.0.0\" \n" +
                                    " xsi:schemaLocation=\"http://www.opengis.net/sld StyledLayerDescriptor.xsd\" \n" +
                                    " xmlns=\"http://www.opengis.net/sld\" \n" +
                                    " xmlns:ogc=\"http://www.opengis.net/ogc\" \n" +
                                    " xmlns:xlink=\"http://www.w3.org/1999/xlink\" \n" +
                                    " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                                    "  <!-- a Named Layer is the basic building block of an SLD document -->\n" +
                                    "  <NamedLayer>\n" +
                                    "    <Name>default_line</Name>\n" +
                                    "    <UserStyle>\n" +
                                    "    <!-- Styles can have names, titles and abstracts -->\n" +
                                    "      <Title>Blue Line</Title>\n" +
                                    "      <Abstract>A sample style that draws a line</Abstract>\n" +
                                    "      <!-- FeatureTypeStyles describe how to render different features -->\n" +
                                    "      <!-- A FeatureTypeStyle for rendering lines -->\n" +
                                    "      <FeatureTypeStyle>\n" +
                                    "        <Rule>\n" +
                                    "          <Name>rule1</Name>\n" +
                                    "          <Title>Blue Line</Title>\n" +
                                    "          <Abstract>A solid blue line with a 1 pixel width</Abstract>\n" +
                                    "          <LineSymbolizer>\n" +
                                    "            <Stroke>\n" +
                                    "              <CssParameter name=\"stroke\">#0000FF</CssParameter>\n" +
                                    "            </Stroke>\n" +
                                    "          </LineSymbolizer>\n" +
                                    "        </Rule>\n" +
                                    "      </FeatureTypeStyle>\n" +
                                    "    </UserStyle>\n" +
                                    "  </NamedLayer>\n" +
                                    "</StyledLayerDescriptor>\n" +
                                    "\n");
                        } else if (styleType.equals("polygon")) {
                            StyleInfo styleInfo = new StyleInfo();
                            styleInfo.setPolygonColor(hexToRgb("#AAAAAA"));
                            styleInfo.setOutlineColor("#000000");
                            styleInfo.setLineWidth(1.0);
                            styleInfo.setFillTransparency(1.0);
                            styleInfo.setLineTransparency(1.0);
                            styleInfo.setLineType(1);
                            styleInfo.setIsFill(true);
                            styleInfo.setIsOutline(true);
                            String jsonString = JSONObject.toJSONString(styleInfo);
                            entity.setStyleRenderRule(jsonString);

                            entity.setStyleDataType(DictionaryIds.GEOM_POLYGON);
                            entity.setStyleContent("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                    "<StyledLayerDescriptor version=\"1.0.0\" \n" +
                                    " xsi:schemaLocation=\"http://www.opengis.net/sld StyledLayerDescriptor.xsd\" \n" +
                                    " xmlns=\"http://www.opengis.net/sld\" \n" +
                                    " xmlns:ogc=\"http://www.opengis.net/ogc\" \n" +
                                    " xmlns:xlink=\"http://www.w3.org/1999/xlink\" \n" +
                                    " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                                    "  <!-- a Named Layer is the basic building block of an SLD document -->\n" +
                                    "  <NamedLayer>\n" +
                                    "    <Name>default_polygon</Name>\n" +
                                    "    <UserStyle>\n" +
                                    "    <!-- Styles can have names, titles and abstracts -->\n" +
                                    "      <Title>Default Polygon</Title>\n" +
                                    "      <Abstract>A sample style that draws a polygon</Abstract>\n" +
                                    "      <!-- FeatureTypeStyles describe how to render different features -->\n" +
                                    "      <!-- A FeatureTypeStyle for rendering polygons -->\n" +
                                    "      <FeatureTypeStyle>\n" +
                                    "        <Rule>\n" +
                                    "          <Name>rule1</Name>\n" +
                                    "          <Title>Gray Polygon with Black Outline</Title>\n" +
                                    "          <Abstract>A polygon with a gray fill and a 1 pixel black outline</Abstract>\n" +
                                    "          <PolygonSymbolizer>\n" +
                                    "            <Fill>\n" +
                                    "              <CssParameter name=\"fill\">#AAAAAA</CssParameter>\n" +
                                    "            </Fill>\n" +
                                    "            <Stroke>\n" +
                                    "              <CssParameter name=\"stroke\">#000000</CssParameter>\n" +
                                    "              <CssParameter name=\"stroke-width\">1</CssParameter>\n" +
                                    "            </Stroke>\n" +
                                    "          </PolygonSymbolizer>\n" +
                                    "        </Rule>\n" +
                                    "      </FeatureTypeStyle>\n" +
                                    "    </UserStyle>\n" +
                                    "  </NamedLayer>\n" +
                                    "</StyledLayerDescriptor>\n");
                        }
                        } else {
                            entity.setStyleContent("");
                        }
                    
                        // 将样式同步到GeoServer并设置为图层默认样式
                        try {
                        if (Func.isNotEmpty(entity.getStyleContent())) {
                            success = geoServer.modifyLayerStyle(
                                    tableName,
                                    Collections.emptyList(),
                                    entity.getStyleName()
                            );
                            geoServer.updateStyle(entity.getStyleName(), entity.getStyleContent());
                        }
                        } catch (Exception e) {
                            log.error("默认样式同步到GeoServer失败，但不影响图层发布。styleName={}, layer={}", entity.getStyleName(), tableName, e);
                        }
                    
                        dataStyleMapper.insert(entity);
                    }
                    
                
                } else {
                    status = false;
                    messageRef = utilityMessageConvert.get("上传文件解析异常(GeoServer地图发布异常)");
                }
            }

            if (!success) {
                status = false;
                messageRef = utilityMessageConvert.get("上传文件解析异常(GeoServer地图发布异常)");
            } else {
                status = true;
            }

        } catch (Exception ex) {
            // 记录异常
            log.error("GeoServer 发布失败", ex);
            object.put("status", status);
            object.put("message", messageRef);
            return object;
        }
        object.put("status", status);
        object.put("messageRef", messageRef);
        return object;
    }

    /**
     * 将十六进制颜色字符串转换为 "rgb(r, g, b)" 格式
     * 支持: "#ff8000", "ff8000", "#f80", "f80"
     *
     * @param hexColor 十六进制颜色字符串（可带 #）
     * @return 格式如 "rgb(255, 128, 0)"
     * @throws IllegalArgumentException 如果输入无效
     */
    public String hexToRgb(String hexColor) {
        if (hexColor == null) {
            throw new IllegalArgumentException("Color string cannot be null");
        }

        String hex = hexColor.trim();

        // 移除开头的 #
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }

        // 检查长度
        if (hex.length() != 3 && hex.length() != 6) {
            throw new IllegalArgumentException("Invalid hex color length: " + hexColor);
        }

        // 处理 3 位简写 (e.g., "f80" -> "ff8800")
        if (hex.length() == 3) {
            StringBuilder expanded = new StringBuilder();
            for (char c : hex.toCharArray()) {
                expanded.append(c).append(c);
            }
            hex = expanded.toString();
        }

        // 解析 R、G、B
        try {
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            return String.format("rgb(%d, %d, %d)", r, g, b);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid hex characters in: " + hexColor, e);
        }
    }

    /**
     * 根据几何类型获取 GeoServer 默认样式名称
     *
     * @param layerType 几何类型（支持中英文，如 "点"、"Point"、"线"、"Line" 等）
     * @return 对应的默认样式名，如 "point"、"line"、"polygon"；若不匹配则返回空字符串
     */
    private String getDefaultStyle(String layerType) {
        if (layerType == null) {
            return "";
        }

        // 统一转为大写以简化比较（也可用 equalsIgnoreCase，但 switch 不支持）
        String type = layerType.trim().toUpperCase();

        switch (type) {
            case "点":
            case "多点":
            case "POINT":
            case "MULTIPOINT":
                return "point";

            case "线":
            case "LINE":
            case "LINESTRING": // 兼容更完整的 WKT 类型名
            case "MULTILINESTRING":
                return "line";

            case "面":
            case "POLYGON":
            case "MULTIPOLYGON": // 可选：多面也用 polygon 样式
                return "polygon";

            default:
                return "";
        }
    }

    /**
     * 几何类型（GDAL 字符串）→ sdms_layer.datatype 字典 id（1cfe51dd 组：点/线/面）
     *
     * @param gdalGeomType GDAL 返回的几何类型字符串
     * @return 字典 id；无法识别时返回 null（不改写 datatype）
     */
    private String resolveGeomTypeId(String gdalGeomType) {
        if (gdalGeomType == null) {
            return null;
        }
        String type = gdalGeomType.toUpperCase();
        if (type.contains("POINT")) {
            return DictionaryIds.GEOM_POINT;
        }
        if (type.contains("LINE")) {
            return DictionaryIds.GEOM_LINE;
        }
        if (type.contains("POLYGON")) {
            return DictionaryIds.GEOM_POLYGON;
        }
        return null;
    }

    /**
     * 将 GDAL 返回的几何类型转换为 GeoServer 发布时需要的类型
     * GDAL 返回: POINT, MULTIPOINT, LINESTRING, MULTILINESTRING, POLYGON, MULTIPOLYGON 等
     * 发布需要: Point, MultiPoint, MultiLineString, MultiPolygon 等（统一用 Multi* 类型）
     *
     * @param gdalGeomType GDAL 返回的几何类型字符串
     * @return 转换后的类型，用于 GeoServer 发布
     */
    private String convertGeomTypeForPublish(String gdalGeomType) {
        if (gdalGeomType == null) {
            return "Point";
        }

        String type = gdalGeomType.toUpperCase();

        // 点类型：单个点或多个点都转为 MultiPoint
        if (type.contains("POINT")) {
            return "MultiPoint";
        }

        // 线类型：单个线或多个线都转为 MultiLineString
        if (type.contains("LINESTRING") || type.contains("LINE")) {
            return "MultiLineString";
        }

        // 面类型：单个面或多个面都转为 MultiPolygon
        if (type.contains("POLYGON")) {
            return "MultiPolygon";
        }

        // 其他类型默认当作几何集合处理
        if (type.contains("GEOMETRYCOLLECTION")) {
            return "GeometryCollection";
        }

        // 未知的几何类型，默认当作点处理
        log.warn("未知的几何类型: {}，默认使用 Point", gdalGeomType);
        return "Point";
    }

    /**
     * 将字段类型名称转换为 GeoServer 所需的 Java 全限定类名（FQCN）
     *
     * @param fieldType 字段类型（如 "VARCHAR", "Point", "面" 等）
     * @return 对应的 Java 类型全名
     */
    private String getGeoFieldType(String fieldType) {
        if (fieldType == null) {
            return "java.lang.String";
        }

        // 使用 equalsIgnoreCase 避免创建新字符串
        if (fieldType.equalsIgnoreCase("LONG INTEGER") ||
                fieldType.equalsIgnoreCase("SHORT INTEGER") ||
                fieldType.equalsIgnoreCase("INT") ||
                fieldType.equalsIgnoreCase("BIGINT")) {
            return "java.lang.Integer";
        }

        if (fieldType.equalsIgnoreCase("DOUBLE")) {
            return "java.lang.Double";
        }

        if (fieldType.equalsIgnoreCase("FLOAT") ||
                fieldType.equalsIgnoreCase("NUMERIC")) {
            return "java.math.BigDecimal";
        }

        if (fieldType.equalsIgnoreCase("TEXT") ||
                fieldType.equalsIgnoreCase("VARCHAR")) {
            return "java.lang.String";
        }

        if (fieldType.equalsIgnoreCase("DATETIME") ||
                fieldType.equalsIgnoreCase("DATE")) {
            return "java.sql.Date";
        }

        // 几何类型（支持中英文）
        if (fieldType.equalsIgnoreCase("点") || fieldType.equalsIgnoreCase("Point")) {
            return "com.vividsolutions.jts.geom.Point";
        }

        if (fieldType.equalsIgnoreCase("线") || fieldType.equalsIgnoreCase("Line") || fieldType.equalsIgnoreCase("LineString")) {
            return "com.vividsolutions.jts.geom.LineString";
        }

        if (fieldType.equalsIgnoreCase("面") || fieldType.equalsIgnoreCase("Polygon")) {
            return "com.vividsolutions.jts.geom.Polygon";
        }

        if (fieldType.equalsIgnoreCase("多点") || fieldType.equalsIgnoreCase("MultiPoint")) {
            return "com.vividsolutions.jts.geom.MultiPoint";
        }

        if (fieldType.equalsIgnoreCase("多线") || fieldType.equalsIgnoreCase("MultiLineString")) {
            return "com.vividsolutions.jts.geom.MultiLineString";
        }

        if (fieldType.equalsIgnoreCase("多面") || fieldType.equalsIgnoreCase("MultiPolygon")) {
            return "com.vividsolutions.jts.geom.MultiPolygon";
        }

        // 默认回退
        return "java.lang.String";
    }

    /**
     * shp2pgsql工具导数据入库
     *
     * @param shpPath
     * @param tableName
     * @param srid
     * @return
     */
    private boolean importToDataBase(String shpPath, String tableName, int srid) {
//        String BIN_DIR = "C:\\Program Files\\PostgreSQL\\13\\bin";
        String PSQL_BIN_DIR = appSettingConfig.getPsqlBinDir();
        String SHP_BIN_DIR = appSettingConfig.getShp2pgsqlBinDir();
        String shp2pgsqlExe = SHP_BIN_DIR + File.separator + "shp2pgsql";
        String psqlExe = PSQL_BIN_DIR + File.separator + "psql";
        String lowPeoperty = System.getProperty("os.name").toLowerCase();
        if (lowPeoperty.contains("win")) {
            shp2pgsqlExe += ".exe";
            psqlExe += ".exe";
        }


        String schema = "public";
//        int srid = 4326;                            // 坐标系
//        String encoding = "GBK";                    // 中文 Shapefile 编码
        String encoding = detectShpEncoding(shpPath);
        log.info("检测到 Shapefile 编码: {}", encoding);

        String host = appSettingConfig.getPostGisHost();         // 远程数据库地址
        int port = Integer.parseInt(appSettingConfig.getPostGisPort());
        String database = appSettingConfig.getPostGisDB();
        String user = appSettingConfig.getPostGisUser();
        String password = appSettingConfig.getPostGisPwd();

        try {
            //shp2pgsql -s 4490 -a -W GBK 文件名.shp 命名空间名.表名 | psql -h localhost -U postgres -d 数据库名-p 5432
            // 构建完整命令：shp2pgsql | psql
            String fullTable = schema + "." + tableName;
            String command = String.format(
                    "\"%s\" -s %d -c -I -W %s \"%s\" %s | \"%s\" -h %s -p %d -U %s -d %s -w",
                    shp2pgsqlExe, srid, encoding, shpPath, fullTable,
                    psqlExe, host, port, user, database
            );
//            String command = "shp2pgsql -s 4326 -c -I -W GBK D:\\workplace\\DMSPTo3\\DataManagementAndServicePlatform\\Web\\dmsp-parent\\dmsp-web\\UploadFile\\test_point.shp public.vtest_insert12164666 | psql -h 192.168.1.179 -U postgres -d postgers -p 5432";

            ProcessBuilder pb;
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                pb = new ProcessBuilder("cmd", "/c", command);
            } else {
                pb = new ProcessBuilder("/bin/bash", "-c", command);
            }

            // 设置密码环境变量
            pb.environment().put("PGPASSWORD", password);
            pb.redirectErrorStream(true); // 合并错误和标准输出

            Process process = pb.start();

            // 实时打印输出（用于调试）
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[LOG] " + line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                log.info("\n✅ 导入成功！");
                return true;
            } else {
                log.error("\n❌ 导入失败，退出码: {}", exitCode);
                return false;
            }

        } catch (Exception e) {
            log.error("shp 导入任务执行异常: {}", e.getMessage(), e);
        }
        return true;
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

}
