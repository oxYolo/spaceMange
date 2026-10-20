package com.whulri.geodata.datamanage.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.whulri.geodata.basecore.mapper.LayerMapper;
import com.whulri.geodata.common.config.AppSettingConfig;
import com.whulri.geodata.common.dto.layerContent.LayerContentInputDto;
import com.whulri.geodata.common.dto.serverInterface.ParameterOutputDto;
import com.whulri.geodata.common.dto.serverInterface.ServerInterfaceOutputDto;
import com.whulri.geodata.common.dto.serverInterface.TileProgressOutputDto;
import com.whulri.geodata.common.entity.LayerContentEntity;
import com.whulri.geodata.common.gdal.handler.GeoServer;
import com.whulri.geodata.common.gdal.pojo.LayerGroup;
import com.whulri.geodata.common.util.GeoServerUtil;
import com.whulri.geodata.common.util.UtilityMessageConvert;
import com.whulri.geodata.datamanage.service.IServerInterfaceService;
import lombok.extern.slf4j.Slf4j;
import com.whulri.geodata.common.bridge.BeanUtil;
import com.whulri.geodata.common.bridge.Func;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class ServerInterfaceServiceImpl implements IServerInterfaceService {

    @Resource
    private LayerMapper layerMapper;

    @Resource
    private GeoServer geoServer;

    @Resource
    private AppSettingConfig appSettingConfig;

    @Resource
    private UtilityMessageConvert utilityMessageConvert;


    @Override
    public boolean publishService(LayerContentInputDto dto) {
        try {
            LayerContentEntity layerContentEntity = layerMapper.selectById(dto.getId());
            String geoServerUrl = "http://" + appSettingConfig.getGeoServerIP() + ":" + appSettingConfig.getGeoServerPort() + "/geoserver";
            String username = appSettingConfig.getGeoServerUser();
            String password = appSettingConfig.getGeoServerPwd();
            String workspace = appSettingConfig.getGeoWorkspace();
            String layerName = layerContentEntity.getLayerAttrTable();
            String start = String.valueOf(appSettingConfig.getZoomStart());
            String stop = String.valueOf(appSettingConfig.getZoomStop());
//            GeoServerUtil.GeoSevverLayerCache();
            // 发布类型空值兜底：默认发布 WMS（图层随数据导入即已可用，不触发切片）；
            // 显式指定 WMTS/MVT 时才会触发 GWC 切片任务
            String publishType = Func.isBlank(dto.getPublishType()) ? "WMS" : dto.getPublishType();
            String[] split = publishType.split(",");
            // 异步执行切片发布
            CompletableFuture.runAsync(() -> {
                try {
                    if ("1".equals(layerContentEntity.getUploadFileType())) { // 矢量
                        for (String s : split) {
                            switch (s){
                                case "WMTS":
                                    GeoServerUtil.publishTileService(geoServerUrl, username, password, workspace, layerName, "shp", "wmts", "", start, stop);
                                    break;
                                case "MVT":
                                    GeoServerUtil.publishTileService(geoServerUrl, username, password, workspace, layerName, "shp", "mvt", "", start, stop);
                                    break;
                            }
                        }
                    } else if ("2".equals(layerContentEntity.getUploadFileType())) { // 栅格
                        for (String s : split) {
                            if ("WMTS".equalsIgnoreCase(s)) {
                                GeoServerUtil.publishTileService(geoServerUrl, username, password, workspace, layerName, "tif", "wmts", "", start, stop);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("切片发布失败，回退发布状态: id={}", dto.getId(), e);
                    // 切片任务提交失败时回退发布状态，避免前端显示"已发布"但切片实际未生成
                    try {
                        LayerContentEntity rollback = new LayerContentEntity();
                        rollback.setId(dto.getId());
                        rollback.setPublishStatus("0");
                        layerMapper.updateById(rollback);
                    } catch (Exception ex) {
                        log.error("回退发布状态失败: id={}", dto.getId(), ex);
                    }
                }
            });
//            if ("1".equals(layerContentEntity.getUploadFileType())){//矢量
//                GeoServerUtil.publishTileService("http://"+appSettingConfig.getGeoServerIP()+":"+appSettingConfig.getGeoServerPort()+"/geoserver",
//                        appSettingConfig.getGeoServerUser(),
//                        appSettingConfig.getGeoServerPwd(),
//                        appSettingConfig.getGeoWorkspace(),
//                        layerContentEntity.getLayerAttrTable(),
//                        "shp",
//                        "wmts",
//                        "",
//                        String.valueOf(appSettingConfig.getZoomStart()),
//                        String.valueOf(appSettingConfig.getZoomStop()));
//                GeoServerUtil.publishTileService("http://"+appSettingConfig.getGeoServerIP()+":"+appSettingConfig.getGeoServerPort()+"/geoserver",
//                        appSettingConfig.getGeoServerUser(),
//                        appSettingConfig.getGeoServerPwd(),
//                        appSettingConfig.getGeoWorkspace(),
//                        layerContentEntity.getLayerAttrTable(),
//                        "shp",
//                        "mvt",
//                        "",
//                        String.valueOf(appSettingConfig.getZoomStart()),
//                        String.valueOf(appSettingConfig.getZoomStop()));
//            }else if ("2".equals(layerContentEntity.getUploadFileType())){//栅格
//                GeoServerUtil.publishTileService("http://"+appSettingConfig.getGeoServerIP()+":"+appSettingConfig.getGeoServerPort()+"/geoserver",
//                        appSettingConfig.getGeoServerUser(),
//                        appSettingConfig.getGeoServerPwd(),
//                        appSettingConfig.getGeoWorkspace(),
//                        layerContentEntity.getLayerAttrTable(),
//                        "tif",
//                        "wmts",
//                        "",
//                        String.valueOf(appSettingConfig.getZoomStart()),
//                        String.valueOf(appSettingConfig.getZoomStop()));
//            }
            LayerContentEntity entity = BeanUtil.copy(dto, LayerContentEntity.class);
            // BeanUtil.copy 对 dto.id → entity.Id 的字段名大小写差异会丢失 id，显式回填
            entity.setId(dto.getId());
            entity.setPublishStatus("1");
            layerMapper.updateById(entity);
            return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public TileProgressOutputDto getTileProgress(String dataId) {
        LayerContentEntity entity = layerMapper.selectById(dataId);
        if (entity == null || Func.isBlank(entity.getLayerAttrTable())) {
            TileProgressOutputDto idle = new TileProgressOutputDto();
            idle.setDataId(dataId);
            idle.setStatus("IDLE");
            return idle;
        }
        String fullLayerName = appSettingConfig.getGeoWorkspace() + ":" + entity.getLayerAttrTable();
        try {
            return TileProgressOutputDto.build(dataId, fullLayerName, geoServer.getTileTasks(fullLayerName));
        } catch (Exception e) {
            log.warn("查询切片进度失败: dataId={}, {}", dataId, e.getMessage());
            TileProgressOutputDto failed = new TileProgressOutputDto();
            failed.setDataId(dataId);
            failed.setLayerName(fullLayerName);
            failed.setStatus("FAILED");
            return failed;
        }
    }

    @Override
    public JSONObject getServerInterface(String dataId, String serverType) {
        List<ServerInterfaceOutputDto> list = new ArrayList<>();
        String publishType = "";
        if (Func.isNotEmpty(dataId) && Func.isNotEmpty(serverType)) {
            LayerContentEntity query = layerMapper.selectById(dataId);
            if (query != null) {
                publishType = query.getPublishType();
                JSONObject layers = geoServer.getLayers(appSettingConfig.getGeoWorkspace(), appSettingConfig.getGeoDatastore());
                // layers 为 null：GeoServer 中该工作区暂无图层（未发布/未注册）——返回空服务列表而非抛错
                if (layers != null) {
                    List<String> layerTables = new ArrayList<>();
                    if (serverType.equalsIgnoreCase("WFS")) {
                        layerTables.add(query.getLayerAttrTable());
                    }
                    getListByType(dataId, serverType, query, layers, list);
                }
            }
        }
        JSONObject object = new JSONObject();
        object.put("publishType", publishType);
        object.put("list", list);
        // 返回结果
        return object;
    }

    /**
     * 根据服务类型获取服务信息
     *
     * @param dataId
     * @param type
     * @param layerContentEntity //     * @param layerJs
     *                           //     * @param layerTables
     * @param list
     */
    private List<ServerInterfaceOutputDto> getListByType(String dataId, String type, LayerContentEntity layerContentEntity, JSONObject layers, List<ServerInterfaceOutputDto> list) {
        switch (type.toUpperCase()) {
            case "WMS":
                return getWMSInfo(dataId, layerContentEntity, layers, list);
            case "WMTS":
                return getWMTSInfo(dataId, layerContentEntity, layers, list);
            case "WFS":
                return getWFSInfo(dataId, layerContentEntity, layers, list);
            case "MVT":
                return getMVTInfo(dataId, layerContentEntity, layers, list);
//            case "KML":
//                return getKMLInfo(dataId, layerContentEntity, layerJs, list);
            default:
                throw new IllegalArgumentException("Unsupported type: " + type);
        }
    }

    /**
     * 获取 WMS 信息
     *
     * @param dataId 地图 ID
     * @param query  地图实体对象
     *               //     * @param layerJs 图层组对象
     * @param list   输出结果列表
     */
    public List<ServerInterfaceOutputDto> getWMSInfo(String dataId, LayerContentEntity query, JSONObject layers, List<ServerInterfaceOutputDto> list) {
        String geoWorkSpace = appSettingConfig.getGeoWorkspace();
        // 初始化 ServerInterfaceOutputDto 对象
        ServerInterfaceOutputDto modelServer = new ServerInterfaceOutputDto();
        modelServer.setServerName("GetMap");
        modelServer.setServerDesc(utilityMessageConvert.get("返回图层数据"));
        modelServer.setRequestType("GET");
        modelServer.setServerPath(String.format("http://%s:%s/geoserver[WORKSPACE]/wms", appSettingConfig.getGeoServerIP(), appSettingConfig.getGeoServerPort()));
        modelServer.setServerPath(modelServer.getServerPath().replace("[WORKSPACE]",
                (geoWorkSpace == null || geoWorkSpace.isEmpty()) ? "" : ("/" + geoWorkSpace)));
        modelServer.setServerSort(2);
        modelServer.setServerKeyWord("WMS");

        // 初始化参数列表
        List<ParameterOutputDto> listParameter = new ArrayList<>();

        // 添加 service 参数
        ParameterOutputDto paramService = new ParameterOutputDto();
        paramService.setParameterName("service");
        paramService.setParameterValue("WMS");
        paramService.setParameterDesc(utilityMessageConvert.get("服务类型"));
        paramService.setParameterIsMust(utilityMessageConvert.get("是"));
        paramService.setParameterSort(1);
        listParameter.add(paramService);
        modelServer.setServerPath(modelServer.getServerPath() + "?service=WMS");

        // 添加 version 参数
        ParameterOutputDto paramVersion = new ParameterOutputDto();
        paramVersion.setParameterName("version");
        paramVersion.setParameterValue("1.0.0, 1.1.0, 1.1.1, 1.3.0, 2.0.0");
        paramVersion.setParameterDesc(utilityMessageConvert.get("版本号"));
        paramVersion.setParameterIsMust(utilityMessageConvert.get("是"));
        paramVersion.setParameterSort(2);
        listParameter.add(paramVersion);
        modelServer.setServerPath(modelServer.getServerPath() + "&version=1.1.0");

        // 添加 request 参数
        ParameterOutputDto paramRequest = new ParameterOutputDto();
        paramRequest.setParameterName("request");
        paramRequest.setParameterValue("GetMap");
        paramRequest.setParameterDesc(utilityMessageConvert.get("请求类型"));
        paramRequest.setParameterIsMust(utilityMessageConvert.get("是"));
        paramRequest.setParameterSort(3);
        listParameter.add(paramRequest);
        modelServer.setServerPath(modelServer.getServerPath() + "&request=GetMap");

        // 动态处理 layers 参数
        if (query != null && query.getLayerAttrTable() != null && !query.getLayerAttrTable().isEmpty()) {
            String layersValue = (geoWorkSpace == null || geoWorkSpace.isEmpty())
                    ? query.getLayerAttrTable()
                    : (geoWorkSpace + ":" + query.getLayerAttrTable());

            ParameterOutputDto paramLayers = new ParameterOutputDto();
            paramLayers.setParameterName("layers");
            paramLayers.setParameterValue(layersValue);
            paramLayers.setParameterDesc(utilityMessageConvert.get("图层名称"));
            paramLayers.setParameterIsMust(utilityMessageConvert.get("是"));
            paramLayers.setParameterSort(4);
            listParameter.add(paramLayers);
            modelServer.setServerPath(modelServer.getServerPath() + "&layers=" + layersValue);

            // 动态处理 bbox 和 srs 参数：优先使用图层自身范围（对栅格/矢量均适用，
            // 避免固定取某个 GeoServer featureType 的范围导致预览空白或定位错误）
            JSONObject ftObj = layers.getJSONObject("featureType");
            JSONObject jsonObject = (ftObj == null) ? null : ftObj.getJSONObject("latLonBoundingBox");
            String bboxValue;
            if (query.getMinX() != null && query.getMinY() != null
                    && query.getMaxX() != null && query.getMaxY() != null) {
                bboxValue = String.format("%s,%s,%s,%s",
                        query.getMinX(), query.getMinY(), query.getMaxX(), query.getMaxY());
            } else if (jsonObject != null) {
                bboxValue = String.format("%s,%s,%s,%s",
                        jsonObject.getString("minx"),
                        jsonObject.getString("miny"),
                        jsonObject.getString("maxx"),
                        jsonObject.getString("maxy"));
            } else {
                bboxValue = "";
            }

            ParameterOutputDto paramBbox = new ParameterOutputDto();
            paramBbox.setParameterName("bbox");
            paramBbox.setParameterValue(bboxValue);
            paramBbox.setParameterDesc(utilityMessageConvert.get("边界范围"));
            paramBbox.setParameterIsMust(utilityMessageConvert.get("是"));
            paramBbox.setParameterSort(6);
            listParameter.add(paramBbox);
            modelServer.setServerPath(modelServer.getServerPath() + "&bbox=" + bboxValue);

            String srsValue = (jsonObject != null && jsonObject.getString("crs") != null)
                    ? jsonObject.getString("crs") : "EPSG:4326";
            ParameterOutputDto paramSrs = new ParameterOutputDto();
            paramSrs.setParameterName("srs");
            paramSrs.setParameterValue(srsValue);
            paramSrs.setParameterDesc(utilityMessageConvert.get("坐标参考系"));
            paramSrs.setParameterIsMust(utilityMessageConvert.get("是"));
            paramSrs.setParameterSort(7);
            listParameter.add(paramSrs);
            modelServer.setServerPath(modelServer.getServerPath() + "&srs=" + paramSrs.getParameterValue());

        }

        // 添加 styles 参数
        ParameterOutputDto paramStyles = new ParameterOutputDto();
        paramStyles.setParameterName("styles");
        paramStyles.setParameterValue(utilityMessageConvert.get("空代表默认样式"));
        paramStyles.setParameterDesc(utilityMessageConvert.get("图层样式"));
        paramStyles.setParameterIsMust(utilityMessageConvert.get("是"));
        paramStyles.setParameterSort(5);
        listParameter.add(paramStyles);
        modelServer.setServerPath(modelServer.getServerPath() + "&styles=");

        // 添加 width 参数
        ParameterOutputDto paramWidth = new ParameterOutputDto();
        paramWidth.setParameterName("width");
        paramWidth.setParameterValue(utilityMessageConvert.get("正整数"));
        paramWidth.setParameterDesc(utilityMessageConvert.get("宽度"));
        paramWidth.setParameterIsMust(utilityMessageConvert.get("是"));
        paramWidth.setParameterSort(8);
        listParameter.add(paramWidth);
        modelServer.setServerPath(modelServer.getServerPath() + "&width=768");

        // 添加 height 参数
        ParameterOutputDto paramHeight = new ParameterOutputDto();
        paramHeight.setParameterName("height");
        paramHeight.setParameterValue(utilityMessageConvert.get("正整数"));
        paramHeight.setParameterDesc(utilityMessageConvert.get("高度"));
        paramHeight.setParameterIsMust(utilityMessageConvert.get("是"));
        paramHeight.setParameterSort(9);
        listParameter.add(paramHeight);
        modelServer.setServerPath(modelServer.getServerPath() + "&height=670");

        // 添加 format 参数
        ParameterOutputDto paramFormat = new ParameterOutputDto();
        paramFormat.setParameterName("format");
        paramFormat.setParameterValue("image/png, image/png8, image/jpeg, image/vnd.jpeg-png, image/gif, image/tiff, image/tiff88, image/geotiff, image/geotiff8, image/svg, application/pdf, rss, kml, kmz, application/openlayers, application/json;type=utgrid");
        paramFormat.setParameterDesc(utilityMessageConvert.get("返回格式"));
        paramFormat.setParameterIsMust(utilityMessageConvert.get("是"));
        paramFormat.setParameterSort(10);
        listParameter.add(paramFormat);
        modelServer.setServerPath(modelServer.getServerPath() + "&format=image/png");

        // 添加透明度参数（非必填）
        ParameterOutputDto paramTransparent = new ParameterOutputDto();
        paramTransparent.setParameterName("transparent");
        paramTransparent.setParameterValue("true, false（默认：false）");
        paramTransparent.setParameterDesc(utilityMessageConvert.get("是否透明"));
        paramTransparent.setParameterIsMust(utilityMessageConvert.get("否"));
        paramTransparent.setParameterSort(11);
        listParameter.add(paramTransparent);

        // 添加背景颜色参数（非必填）
        ParameterOutputDto paramBgcolor = new ParameterOutputDto();
        paramBgcolor.setParameterName("bgcolor");
        paramBgcolor.setParameterValue("RRGGBB（默认：FFFFFF）");
        paramBgcolor.setParameterDesc(utilityMessageConvert.get("背景颜色"));
        paramBgcolor.setParameterIsMust(utilityMessageConvert.get("否"));
        paramBgcolor.setParameterSort(12);
        listParameter.add(paramBgcolor);

        // 添加异常文件格式参数（非必填）
        ParameterOutputDto paramExceptions = new ParameterOutputDto();
        paramExceptions.setParameterName("exceptions");
        paramExceptions.setParameterValue("文件格式（默认：application/vnd.ogc.se_xml）");
        paramExceptions.setParameterDesc(utilityMessageConvert.get("异常文件格式"));
        paramExceptions.setParameterIsMust(utilityMessageConvert.get("否"));
        paramExceptions.setParameterSort(13);
        listParameter.add(paramExceptions);

        // 添加时间参数（非必填）
        ParameterOutputDto paramTime = new ParameterOutputDto();
        paramTime.setParameterName("time");
        paramTime.setParameterValue("yyyy-MM-ddThh:mm:ss.SSSZ");
        paramTime.setParameterDesc(utilityMessageConvert.get("日期"));
        paramTime.setParameterIsMust(utilityMessageConvert.get("否"));
        paramTime.setParameterSort(14);
        listParameter.add(paramTime);

        // 添加样式文件地址参数（非必填）
        ParameterOutputDto paramSld = new ParameterOutputDto();
        paramSld.setParameterName("sld");
        paramSld.setParameterValue("URL");
        paramSld.setParameterDesc(utilityMessageConvert.get("样式文件地址"));
        paramSld.setParameterIsMust(utilityMessageConvert.get("否"));
        paramSld.setParameterSort(15);
        listParameter.add(paramSld);

        // 添加样式文件内容参数（非必填）
        ParameterOutputDto paramSldBody = new ParameterOutputDto();
        paramSldBody.setParameterName("sld_body");
        paramSldBody.setParameterValue("XML");
        paramSldBody.setParameterDesc(utilityMessageConvert.get("样式文件内容"));
        paramSldBody.setParameterIsMust(utilityMessageConvert.get("否"));
        paramSldBody.setParameterSort(16);
        listParameter.add(paramSldBody);

        // 设置请求参数并添加到列表
        modelServer.setServerRequestParameters(new ArrayList<>(listParameter));
        list.add(modelServer);
        // 图层属性api
        ServerInterfaceOutputDto layerFieldQuery = getLayerFieldQuery(dataId);
        list.add(layerFieldQuery);
        //图层空间查询api
        ServerInterfaceOutputDto layerSpaceQuery = getLayerSpaceQuery(dataId);
        list.add(layerSpaceQuery);
        //图层空间下载api
        ServerInterfaceOutputDto layerSpaceDownload = getLayerSpaceDownload(dataId);
        list.add(layerSpaceDownload);
        return list;
    }

    /**
     * 获取 WMTS 信息
     *
     * @param dataId 数据ID
     * @param query  地图实体对象
     *               //     * @param queryGroup 图层组对象
     * @param list   输出结果列表
     */
    public List<ServerInterfaceOutputDto> getWMTSInfo(String dataId, LayerContentEntity query, JSONObject layers, List<ServerInterfaceOutputDto> list) {
        ServerInterfaceOutputDto modelServer = new ServerInterfaceOutputDto();
        List<ParameterOutputDto> listParameter = new ArrayList<>();

        // 设置服务基本信息
        modelServer.setServerName("GetTile");
        modelServer.setServerDesc(utilityMessageConvert.get("返回切片数据"));
        modelServer.setRequestType("GET");
        modelServer.setServerPath(String.format("http://%s:%s/geoserver/gwc/service/%s", appSettingConfig.getGeoServerIP(), appSettingConfig.getGeoServerPort(), "wmts"));
        modelServer.setServerSort(1);
        modelServer.setServerKeyWord("WMTS");

        // 添加参数：service
        ParameterOutputDto modelParameter = new ParameterOutputDto();
        modelParameter.setParameterName("service");
        modelParameter.setParameterValue("WMTS");
        modelParameter.setParameterDesc(utilityMessageConvert.get("服务类型"));
        modelParameter.setParameterIsMust(utilityMessageConvert.get("是"));
        modelParameter.setParameterSort(1);
        listParameter.add(modelParameter);
        modelServer.setServerPath(modelServer.getServerPath() + "?service=WMTS");

        // 添加参数：version
        modelParameter = new ParameterOutputDto();
        modelParameter.setParameterName("version");
        modelParameter.setParameterValue("1.0.0, 1.1.0, 1.1.1, 1.3.0, 2.0.0");
        modelParameter.setParameterDesc(utilityMessageConvert.get("版本号"));
        modelParameter.setParameterIsMust(utilityMessageConvert.get("是"));
        modelParameter.setParameterSort(2);
        listParameter.add(modelParameter);
        modelServer.setServerPath(modelServer.getServerPath() + "&version=1.1.0");

        // 添加参数：request
        modelParameter = new ParameterOutputDto();
        modelParameter.setParameterName("request");
        modelParameter.setParameterValue("GetTile");
        modelParameter.setParameterDesc(utilityMessageConvert.get("请求类型"));
        modelParameter.setParameterIsMust(utilityMessageConvert.get("是"));
        modelParameter.setParameterSort(3);
        listParameter.add(modelParameter);
        modelServer.setServerPath(modelServer.getServerPath() + "&request=GetTile");

        String geoWorkSpace = appSettingConfig.getGeoWorkspace();
        // 如果查询对象不为空，添加 layer 参数
        if (query != null && query.getLayerAttrTable() != null && !query.getLayerAttrTable().isEmpty()) {
            modelParameter = new ParameterOutputDto();
            modelParameter.setParameterName("layer");
            modelParameter.setParameterValue(geoWorkSpace == null || geoWorkSpace.isEmpty()
                    ? query.getLayerAttrTable()
                    : geoWorkSpace + ":" + query.getLayerAttrTable());
            modelParameter.setParameterDesc(utilityMessageConvert.get("地图名称"));
            modelParameter.setParameterIsMust(utilityMessageConvert.get("是"));
            modelParameter.setParameterSort(4);
            listParameter.add(modelParameter);
            modelServer.setServerPath(modelServer.getServerPath() + "&layer=" + modelParameter.getParameterValue());

            JSONObject ftObj = layers.getJSONObject("featureType");
            JSONObject jsonObject = (ftObj == null) ? null : ftObj.getJSONObject("latLonBoundingBox");
            double minx = (query.getMinX() != null) ? query.getMinX().doubleValue() : (jsonObject != null ? Double.parseDouble(jsonObject.getString("minx")) : 0d);
            double miny = (query.getMinY() != null) ? query.getMinY().doubleValue() : (jsonObject != null ? Double.parseDouble(jsonObject.getString("miny")) : 0d);
            double maxx = (query.getMaxX() != null) ? query.getMaxX().doubleValue() : (jsonObject != null ? Double.parseDouble(jsonObject.getString("maxx")) : 0d);
            double maxy = (query.getMaxY() != null) ? query.getMaxY().doubleValue() : (jsonObject != null ? Double.parseDouble(jsonObject.getString("maxy")) : 0d);

            // 计算行列号（EPSG:4326）
            int tile4326MinCol = getColFromLongitude(minx, 360 / (2 * Math.pow(2, 0)));
            int tile4326MinRow = getRowFromLatitude(miny, 360 / (1 * Math.pow(2, 0)));
            int tile4326MaxCol = getColFromLongitude(maxx, 360 / (2 * Math.pow(2, 21)));
            int tile4326MaxRow = getRowFromLatitude(maxy, 360 / (1 * Math.pow(2, 21)));

            // 计算行列号（EPSG:900913）
            int tile900913MinCol = getColFromLongitude(minx, 360 / Math.pow(2, 0));
            int tile900913MinRow = getRowFromLatitude(miny, 360 / Math.pow(2, 0));
            int tile900913MaxCol = getColFromLongitude(maxx, 360 / Math.pow(2, 30));
            int tile900913MaxRow = getRowFromLatitude(maxy, 360 / Math.pow(2, 30));


            // 添加参数：tileRow
            modelParameter = new ParameterOutputDto();
            modelParameter.setParameterName("tileRow");
            modelParameter.setParameterValue(
                    tile4326MinRow + "-" + tile4326MaxRow + ", " +
                            tile900913MinRow + "-" + tile900913MaxRow);
            modelParameter.setParameterDesc(utilityMessageConvert.get("所在行"));
            modelParameter.setParameterIsMust(utilityMessageConvert.get("是"));
            modelParameter.setParameterSort(8);
            listParameter.add(modelParameter);
            modelServer.setServerPath(modelServer.getServerPath() + "&tileRow=" + getRowFromLatitude(miny, 360 / Math.pow(2, 7)));

            // 添加参数：tileCol
            modelParameter = new ParameterOutputDto();
            modelParameter.setParameterName("tileCol");
            modelParameter.setParameterValue(
                    tile4326MinCol + "-" + tile4326MaxCol + ", " +
                            tile900913MinCol + "-" + tile900913MaxCol);
            modelParameter.setParameterDesc(utilityMessageConvert.get("所在列"));
            modelParameter.setParameterIsMust(utilityMessageConvert.get("是"));
            modelParameter.setParameterSort(9);
            listParameter.add(modelParameter);
            modelServer.setServerPath(modelServer.getServerPath() + "&tileCol=" + getColFromLongitude(minx, 360 / (2 * Math.pow(2, 7))));

        }

        // 添加参数：tileMatrixSet
        modelParameter = new ParameterOutputDto();
        modelParameter.setParameterName("tileMatrixSet");
        modelParameter.setParameterValue("EPSG:4326, EPSG:900913");
        modelParameter.setParameterDesc(utilityMessageConvert.get("切片方案"));
        modelParameter.setParameterIsMust(utilityMessageConvert.get("是"));
        modelParameter.setParameterSort(5);
        listParameter.add(modelParameter);
        modelServer.setServerPath(modelServer.getServerPath() + "&tileMatrixSet=EPSG:4326");

        // 添加参数：tileMatrix
        modelParameter = new ParameterOutputDto();
        modelParameter.setParameterName("tileMatrix");
        modelParameter.setParameterValue("EPSG:4326:0-EPSG:4326:21, EPSG:900913:0-EPSG:900913:30");
        modelParameter.setParameterDesc(utilityMessageConvert.get("切片等级名称"));
        modelParameter.setParameterIsMust(utilityMessageConvert.get("是"));
        modelParameter.setParameterSort(6);
        listParameter.add(modelParameter);
        modelServer.setServerPath(modelServer.getServerPath() + "&tileMatrix=EPSG:4326:7");

        // 添加参数：format
        modelParameter = new ParameterOutputDto();
        modelParameter.setParameterName("format");
        modelParameter.setParameterValue("image/jpeg, image/png");
        modelParameter.setParameterDesc(utilityMessageConvert.get("返回格式"));
        modelParameter.setParameterIsMust(utilityMessageConvert.get("是"));
        modelParameter.setParameterSort(7);
        listParameter.add(modelParameter);
        modelServer.setServerPath(modelServer.getServerPath() + "&format=image/png");

        // 添加参数：style
        modelParameter = new ParameterOutputDto();
        modelParameter.setParameterName("style");
        modelParameter.setParameterValue("");
        modelParameter.setParameterDesc(utilityMessageConvert.get("样式"));
        modelParameter.setParameterIsMust(utilityMessageConvert.get("否"));
        modelParameter.setParameterSort(9);
        listParameter.add(modelParameter);

        // 将参数列表设置到服务对象中
        modelServer.setServerRequestParameters(listParameter);
        list.add(modelServer);
        // 图层属性api
        ServerInterfaceOutputDto layerFieldQuery = getLayerFieldQuery(dataId);
        list.add(layerFieldQuery);
        //图层空间查询api
        ServerInterfaceOutputDto layerSpaceQuery = getLayerSpaceQuery(dataId);
        list.add(layerSpaceQuery);
        //图层空间下载api
        ServerInterfaceOutputDto layerSpaceDownload = getLayerSpaceDownload(dataId);
        list.add(layerSpaceDownload);
        return list;
    }

    /**
     * 获取 WMTS 信息
     *
     * @param dataId 数据ID
     * @param query  地图实体对象
     *               //     * @param queryGroup 图层组对象
     * @param list   输出结果列表
     */
    public List<ServerInterfaceOutputDto> getMVTInfo(String dataId, LayerContentEntity query, JSONObject layers, List<ServerInterfaceOutputDto> list) {
        ServerInterfaceOutputDto modelServer = new ServerInterfaceOutputDto();
        List<ParameterOutputDto> listParameter = new ArrayList<>();

        // 设置服务基本信息
        modelServer.setServerName("GetTile");
        modelServer.setServerDesc(utilityMessageConvert.get("返回切片数据"));
        modelServer.setRequestType("GET");
        modelServer.setServerPath(String.format("http://%s:%s/geoserver/gwc/service/%s", appSettingConfig.getGeoServerIP(), appSettingConfig.getGeoServerPort(), "wmts"));
        modelServer.setServerSort(1);
        modelServer.setServerKeyWord("WMTS");

        // 添加参数：service
        ParameterOutputDto modelParameter = new ParameterOutputDto();
        modelParameter.setParameterName("SERVICE");
        modelParameter.setParameterValue("WMTS");
        modelParameter.setParameterDesc(utilityMessageConvert.get("服务类型"));
        modelParameter.setParameterIsMust(utilityMessageConvert.get("是"));
        modelParameter.setParameterSort(1);
        listParameter.add(modelParameter);
        modelServer.setServerPath(modelServer.getServerPath() + "?SERVICE=WMTS");

        // 添加参数：version
        modelParameter = new ParameterOutputDto();
        modelParameter.setParameterName("VERSION");
        modelParameter.setParameterValue("1.0.0, 1.1.0, 1.1.1, 1.3.0, 2.0.0");
        modelParameter.setParameterDesc(utilityMessageConvert.get("版本号"));
        modelParameter.setParameterIsMust(utilityMessageConvert.get("是"));
        modelParameter.setParameterSort(2);
        listParameter.add(modelParameter);
        modelServer.setServerPath(modelServer.getServerPath() + "&VERSION=1.1.0");

        // 添加参数：request
        modelParameter = new ParameterOutputDto();
        modelParameter.setParameterName("REQUEST");
        modelParameter.setParameterValue("GetTile");
        modelParameter.setParameterDesc(utilityMessageConvert.get("请求类型"));
        modelParameter.setParameterIsMust(utilityMessageConvert.get("是"));
        modelParameter.setParameterSort(3);
        listParameter.add(modelParameter);
        modelServer.setServerPath(modelServer.getServerPath() + "&REQUEST=GetTile");

        String geoWorkSpace = appSettingConfig.getGeoWorkspace();
        // 如果查询对象不为空，添加 layer 参数
        if (query != null && query.getLayerAttrTable() != null && !query.getLayerAttrTable().isEmpty()) {
            modelParameter = new ParameterOutputDto();
            modelParameter.setParameterName("LAYER");
            modelParameter.setParameterValue(geoWorkSpace == null || geoWorkSpace.isEmpty()
                    ? query.getLayerAttrTable()
                    : geoWorkSpace + ":" + query.getLayerAttrTable());
            modelParameter.setParameterDesc(utilityMessageConvert.get("地图名称"));
            modelParameter.setParameterIsMust(utilityMessageConvert.get("是"));
            modelParameter.setParameterSort(4);
            listParameter.add(modelParameter);
            modelServer.setServerPath(modelServer.getServerPath() + "&LAYER=" + modelParameter.getParameterValue());

            JSONObject ftObj = layers.getJSONObject("featureType");
            JSONObject jsonObject = (ftObj == null) ? null : ftObj.getJSONObject("latLonBoundingBox");
            double minx = (query.getMinX() != null) ? query.getMinX().doubleValue() : (jsonObject != null ? Double.parseDouble(jsonObject.getString("minx")) : 0d);
            double miny = (query.getMinY() != null) ? query.getMinY().doubleValue() : (jsonObject != null ? Double.parseDouble(jsonObject.getString("miny")) : 0d);
            double maxx = (query.getMaxX() != null) ? query.getMaxX().doubleValue() : (jsonObject != null ? Double.parseDouble(jsonObject.getString("maxx")) : 0d);
            double maxy = (query.getMaxY() != null) ? query.getMaxY().doubleValue() : (jsonObject != null ? Double.parseDouble(jsonObject.getString("maxy")) : 0d);

            // 计算行列号（EPSG:4326）
            double tileMinCol = getColFromLongitude(minx, 360 / (2 * Math.pow(2, 0)));
            double tileMinRow = getRowFromLatitude(miny, 360 / (1 * Math.pow(2, 0)));
            double tileMaxCol = getColFromLongitude(maxx, 360 / (2 * Math.pow(2, 14)));
            double tileMaxRow = getRowFromLatitude(maxy, 360 / (1 * Math.pow(2, 14)));


            // 添加参数：tileRow
            modelParameter = new ParameterOutputDto();
            modelParameter.setParameterName("TILEROW");
            modelParameter.setParameterValue(tileMinRow + "-" + tileMaxRow);
            modelParameter.setParameterDesc(utilityMessageConvert.get("所在行"));
            modelParameter.setParameterIsMust(utilityMessageConvert.get("是"));
            modelParameter.setParameterSort(8);
            listParameter.add(modelParameter);
            modelServer.setServerPath(modelServer.getServerPath() + "&TILEROW=" + getRowFromLatitude(miny, 360 / Math.pow(2, 7)));

            // 添加参数：tileCol
            modelParameter = new ParameterOutputDto();
            modelParameter.setParameterName("TILECOL");
            modelParameter.setParameterValue(tileMinCol + "-" + tileMaxCol);
            modelParameter.setParameterDesc(utilityMessageConvert.get("所在列"));
            modelParameter.setParameterIsMust(utilityMessageConvert.get("是"));
            modelParameter.setParameterSort(9);
            listParameter.add(modelParameter);
            modelServer.setServerPath(modelServer.getServerPath() + "&TILECOL=" + getColFromLongitude(minx, 360 / (2 * Math.pow(2, 7))));

        }

        // 添加参数：tileMatrixSet
        modelParameter = new ParameterOutputDto();
        modelParameter.setParameterName("TILEMATRIXSET");
        modelParameter.setParameterValue("EPSG:4326, EPSG:900913");
        modelParameter.setParameterDesc(utilityMessageConvert.get("切片方案"));
        modelParameter.setParameterIsMust(utilityMessageConvert.get("是"));
        modelParameter.setParameterSort(5);
        listParameter.add(modelParameter);
        modelServer.setServerPath(modelServer.getServerPath() + "&TILEMATRIXSET=EPSG:4326");

        // 添加参数：tileMatrix
        modelParameter = new ParameterOutputDto();
        modelParameter.setParameterName("TILEMATRIX");
        modelParameter.setParameterValue("EPSG:4326:0-EPSG:4326:21, EPSG:900913:0-EPSG:900913:30");
        modelParameter.setParameterDesc(utilityMessageConvert.get("切片等级名称"));
        modelParameter.setParameterIsMust(utilityMessageConvert.get("是"));
        modelParameter.setParameterSort(6);
        listParameter.add(modelParameter);
        modelServer.setServerPath(modelServer.getServerPath() + "&TILEMATRIX=EPSG:4326:7");

        // 添加参数：format
        modelParameter = new ParameterOutputDto();
        modelParameter.setParameterName("format");
        modelParameter.setParameterValue("application/vnd.mapbox-vector-tile");
        modelParameter.setParameterDesc(utilityMessageConvert.get("返回格式"));
        modelParameter.setParameterIsMust(utilityMessageConvert.get("是"));
        modelParameter.setParameterSort(7);
        listParameter.add(modelParameter);
        modelServer.setServerPath(modelServer.getServerPath() + "&format=application/vnd.mapbox-vector-tile");

        // 添加参数：style
        modelParameter = new ParameterOutputDto();
        modelParameter.setParameterName("style");
        modelParameter.setParameterValue("");
        modelParameter.setParameterDesc(utilityMessageConvert.get("样式"));
        modelParameter.setParameterIsMust(utilityMessageConvert.get("否"));
        modelParameter.setParameterSort(9);
        listParameter.add(modelParameter);

        // 将参数列表设置到服务对象中
        modelServer.setServerRequestParameters(listParameter);
        list.add(modelServer);
        // 图层属性api
        ServerInterfaceOutputDto layerFieldQuery = getLayerFieldQuery(dataId);
        list.add(layerFieldQuery);
        //图层空间查询api
        ServerInterfaceOutputDto layerSpaceQuery = getLayerSpaceQuery(dataId);
        list.add(layerSpaceQuery);
        //图层空间下载api
        ServerInterfaceOutputDto layerSpaceDownload = getLayerSpaceDownload(dataId);
        list.add(layerSpaceDownload);
        return list;
    }

    /**
     * wfs
     *
     * @param mapId //     * @param queryMap
     *              //     * @param queryGroup
     * @param query
     * @param list
     */
    public List<ServerInterfaceOutputDto> getWFSInfo(String dataId, LayerContentEntity query, JSONObject layers, List<ServerInterfaceOutputDto> list) {
        ServerInterfaceOutputDto modelServer = null;
        List<ParameterOutputDto> listParameter = null;
        ParameterOutputDto modelParameter = null;

        String geoWorkSpace = appSettingConfig.getGeoWorkspace();
        // GetFeature 部分
        modelServer = new ServerInterfaceOutputDto();
        modelServer.setServerName("GetFeature");
        modelServer.setServerDesc(utilityMessageConvert.get("返回图层要素"));
        modelServer.setRequestType("GET");
        modelServer.setServerPath(String.format("http://%s:%s/geoserver[WORKSPACE]/%s", appSettingConfig.getGeoServerIP(), appSettingConfig.getGeoServerPort(), "wfs"));
        modelServer.setServerPath(modelServer.getServerPath().replace("[WORKSPACE]",
                (geoWorkSpace == null || geoWorkSpace.isEmpty()) ? "" : ("/" + geoWorkSpace)));
        modelServer.setServerSort(1);
        modelServer.setServerKeyWord("WFS");

        listParameter = new ArrayList<>();

        // 添加 service 参数
        modelParameter = new ParameterOutputDto();
        modelParameter.setParameterName("service");
        modelParameter.setParameterValue("WFS");
        modelParameter.setParameterDesc(utilityMessageConvert.get("服务类型"));
        modelParameter.setParameterIsMust(utilityMessageConvert.get("是"));
        modelParameter.setParameterSort(1);
        listParameter.add(modelParameter);
        modelServer.setServerPath(modelServer.getServerPath() + "?service=WFS");

        // 添加 version 参数
        modelParameter = new ParameterOutputDto();
        modelParameter.setParameterName("version");
        modelParameter.setParameterValue("1.0.0, 1.1.0, 1.1.1, 1.3.0, 2.0.0");
        modelParameter.setParameterDesc(utilityMessageConvert.get("版本号"));
        modelParameter.setParameterIsMust(utilityMessageConvert.get("是"));
        modelParameter.setParameterSort(2);
        listParameter.add(modelParameter);
        modelServer.setServerPath(modelServer.getServerPath() + "&version=1.1.0");

        // 添加 request 参数
        modelParameter = new ParameterOutputDto();
        modelParameter.setParameterName("request");
        modelParameter.setParameterValue("GetFeature");
        modelParameter.setParameterDesc(utilityMessageConvert.get("请求类型"));
        modelParameter.setParameterIsMust(utilityMessageConvert.get("是"));
        modelParameter.setParameterSort(3);
        listParameter.add(modelParameter);
        modelServer.setServerPath(modelServer.getServerPath() + "&request=GetFeature");

        // 处理 typeNames 参数
        String strLayerNames = "";
        if (query != null) {
            if (geoWorkSpace != null && !geoWorkSpace.isEmpty()) {
                query.setLayerAttrTable(geoWorkSpace + ":" + query.getLayerAttrTable());
            }
            strLayerNames = query.getLayerAttrTable();
        }

        modelParameter = new ParameterOutputDto();
        modelParameter.setParameterName("typeNames");
        modelParameter.setParameterValue(strLayerNames);
        modelParameter.setParameterDesc(utilityMessageConvert.get("图层名称"));
        modelParameter.setParameterIsMust(utilityMessageConvert.get("是"));
        modelParameter.setParameterSort(4);
        listParameter.add(modelParameter);
        if (query != null) {
            modelServer.setServerPath(modelServer.getServerPath() + "&typeNames=" + query.getLayerAttrTable());
        }

        // 动态处理 bbox 和 srsName 参数
        if (query != null && query.getLayerAttrTable() != null && !query.getLayerAttrTable().isEmpty()) {
            if (layers != null) {
                modelParameter = new ParameterOutputDto();
                modelParameter.setParameterName("bbox");

                // 优先使用图层自身范围，兜底使用 GeoServer featureType 范围
                JSONObject ftObj = layers.getJSONObject("featureType");
                JSONObject jsonObject = (ftObj == null) ? null : ftObj.getJSONObject("latLonBoundingBox");
                String bboxVal;
                if (query.getMinX() != null && query.getMinY() != null
                        && query.getMaxX() != null && query.getMaxY() != null) {
                    bboxVal = String.format("%s,%s,%s,%s",
                            query.getMinX(), query.getMinY(), query.getMaxX(), query.getMaxY());
                } else if (jsonObject != null) {
                    bboxVal = String.format("%s,%s,%s,%s",
                            jsonObject.getString("minx"), jsonObject.getString("miny"),
                            jsonObject.getString("maxx"), jsonObject.getString("maxy"));
                } else {
                    bboxVal = "";
                }

                modelParameter.setParameterValue(bboxVal);
                modelParameter.setParameterDesc(utilityMessageConvert.get("边界范围"));
                modelParameter.setParameterIsMust(utilityMessageConvert.get("是"));
                modelParameter.setParameterSort(5);
                listParameter.add(modelParameter);
                modelServer.setServerPath(modelServer.getServerPath() + "&bbox=" + modelParameter.getParameterValue());

                modelParameter = new ParameterOutputDto();
                modelParameter.setParameterName("srsName");
                modelParameter.setParameterValue((jsonObject != null && jsonObject.getString("crs") != null)
                        ? jsonObject.getString("crs") : "EPSG:4326");
                modelParameter.setParameterDesc(utilityMessageConvert.get("坐标参考系"));
                modelParameter.setParameterIsMust(utilityMessageConvert.get("否"));
                modelParameter.setParameterSort(6);
                listParameter.add(modelParameter);
                modelServer.setServerPath(modelServer.getServerPath() + "&srsName=" + modelParameter.getParameterValue());
            }
        }

        // 添加 featureID 参数
        modelParameter = new ParameterOutputDto();
        modelParameter.setParameterName("featureID");
        modelParameter.setParameterValue(utilityMessageConvert.get("要素编号"));
        modelParameter.setParameterDesc(utilityMessageConvert.get("要素编号"));
        modelParameter.setParameterIsMust(utilityMessageConvert.get("否"));
        modelParameter.setParameterSort(7);
        listParameter.add(modelParameter);

        // 添加 count 参数
        modelParameter = new ParameterOutputDto();
        modelParameter.setParameterName("count");
        modelParameter.setParameterValue("");
        modelParameter.setParameterDesc(utilityMessageConvert.get("返回数量") + "(for 2.0.0)");
        modelParameter.setParameterIsMust(utilityMessageConvert.get("否"));
        modelParameter.setParameterSort(8);
        listParameter.add(modelParameter);

        // 添加 maxFeatures 参数
        modelParameter = new ParameterOutputDto();
        modelParameter.setParameterName("maxFeatures");
        modelParameter.setParameterValue("");
        modelParameter.setParameterDesc(utilityMessageConvert.get("返回最大的数量"));
        modelParameter.setParameterIsMust(utilityMessageConvert.get("否"));
        modelParameter.setParameterSort(9);
        listParameter.add(modelParameter);

        // 添加 sortBy 参数
        modelParameter = new ParameterOutputDto();
        modelParameter.setParameterName("sortBy");
        modelParameter.setParameterValue(utilityMessageConvert.get("属性名称"));
        modelParameter.setParameterDesc(utilityMessageConvert.get("排序"));
        modelParameter.setParameterIsMust(utilityMessageConvert.get("否"));
        modelParameter.setParameterSort(10);
        listParameter.add(modelParameter);

        // 添加 propertyName 参数
        modelParameter = new ParameterOutputDto();
        modelParameter.setParameterName("propertyName");
        modelParameter.setParameterValue(utilityMessageConvert.get("属性名称"));
        modelParameter.setParameterDesc(utilityMessageConvert.get("指定返回的属性(多个','隔开)"));
        modelParameter.setParameterIsMust(utilityMessageConvert.get("否"));
        modelParameter.setParameterSort(11);
        listParameter.add(modelParameter);

        // 添加 filter 参数
        modelParameter = new ParameterOutputDto();
        modelParameter.setParameterName("filter");
        modelParameter.setParameterValue("");
        modelParameter.setParameterDesc(utilityMessageConvert.get("过滤条件"));
        modelParameter.setParameterIsMust(utilityMessageConvert.get("否"));
        modelParameter.setParameterSort(12);
        listParameter.add(modelParameter);

        // 设置请求参数并添加到列表
        modelServer.setServerRequestParameters(new ArrayList<>(listParameter));
        list.add(modelServer);
        // 图层属性api
        ServerInterfaceOutputDto layerFieldQuery = getLayerFieldQuery(dataId);
        list.add(layerFieldQuery);
        //图层空间查询api
        ServerInterfaceOutputDto layerSpaceQuery = getLayerSpaceQuery(dataId);
        list.add(layerSpaceQuery);
        //图层空间下载api
        ServerInterfaceOutputDto layerSpaceDownload = getLayerSpaceDownload(dataId);
        list.add(layerSpaceDownload);
        return list;
    }


    /**
     * GetKMLInfo
     *
     * @param mapId
     * @param query
     * @param queryGroup
     * @param list
     */
    public List<ServerInterfaceOutputDto> getKMLInfo(String mapId, LayerContentEntity query, LayerGroup queryGroup, List<ServerInterfaceOutputDto> list) {
        // GetMap 部分
        List<ParameterOutputDto> listParameter = new ArrayList<>();
        ParameterOutputDto modelParameter = null;
        ServerInterfaceOutputDto modelServer = new ServerInterfaceOutputDto();

        String geoWorkSpace = appSettingConfig.getGeoWorkspace();
        modelServer.setServerName("KML");
        modelServer.setServerDesc(utilityMessageConvert.get("返回KML文件"));
        modelServer.setRequestType("GET");
        modelServer.setServerPath(String.format("http://%s:%s/geoserver[WORKSPACE]/%s", appSettingConfig.getGeoServerIP(), appSettingConfig.getGeoServerPort(), "wms"));
        modelServer.setServerPath(modelServer.getServerPath().replace("[WORKSPACE]",
                (geoWorkSpace == null || geoWorkSpace.isEmpty()) ? "" : ("/" + geoWorkSpace)));
        modelServer.setServerSort(1);
        modelServer.setServerKeyWord("KML");

        // 添加 service 参数
        modelParameter = new ParameterOutputDto();
        modelParameter.setParameterName("service");
        modelParameter.setParameterValue("WMS");
        modelParameter.setParameterDesc(utilityMessageConvert.get("服务类型"));
        modelParameter.setParameterIsMust(utilityMessageConvert.get("是"));
        modelParameter.setParameterSort(1);

        listParameter.add(modelParameter);
        modelServer.setServerPath(modelServer.getServerPath() + "?service=WMS");
        // 添加 version 参数
        modelParameter = new ParameterOutputDto();
        modelParameter.setParameterName("version");
        modelParameter.setParameterValue("1.0.0, 1.1.0, 1.1.1, 1.3.0, 2.0.0");
        modelParameter.setParameterDesc(utilityMessageConvert.get("版本号"));
        modelParameter.setParameterIsMust(utilityMessageConvert.get("是"));
        modelParameter.setParameterSort(2);
        listParameter.add(modelParameter);
        modelServer.setServerPath(modelServer.getServerPath() + "&version=1.1.0");

        // 添加 request 参数
        modelParameter = new ParameterOutputDto();
        modelParameter.setParameterName("request");
        modelParameter.setParameterValue("GetMap");
        modelParameter.setParameterDesc(utilityMessageConvert.get("请求类型"));
        modelParameter.setParameterIsMust(utilityMessageConvert.get("是"));
        modelParameter.setParameterSort(3);
        listParameter.add(modelParameter);
        modelServer.setServerPath(modelServer.getServerPath() + "&request=GetMap");

        // 动态处理 layers 参数
        if (query != null && query.getLayerName() != null && !query.getLayerName().isEmpty()) {
            modelParameter = new ParameterOutputDto();
            modelParameter.setParameterName("layers");
            modelParameter.setParameterValue((geoWorkSpace == null || geoWorkSpace.isEmpty())
                    ? query.getLayerName()
                    : (geoWorkSpace + ":" + query.getLayerName()));
            modelParameter.setParameterDesc(utilityMessageConvert.get("图层名称"));
            modelParameter.setParameterIsMust(utilityMessageConvert.get("是"));
            modelParameter.setParameterSort(4);
            listParameter.add(modelParameter);
            modelServer.setServerPath(modelServer.getServerPath() + "&layers=" + modelParameter.getParameterValue());

            // 动态处理 bbox 和 srs 参数
            if (queryGroup != null) {
                modelParameter = new ParameterOutputDto();
                modelParameter.setParameterName("bbox");
                modelParameter.setParameterValue(String.format("%s,%s,%s,%s",
                        queryGroup.getBoundSet().getMinx(),
                        queryGroup.getBoundSet().getMiny(),
                        queryGroup.getBoundSet().getMaxx(),
                        queryGroup.getBoundSet().getMaxy()));
                modelParameter.setParameterDesc(utilityMessageConvert.get("边界范围"));
                modelParameter.setParameterIsMust(utilityMessageConvert.get("是"));
                modelParameter.setParameterSort(6);
                listParameter.add(modelParameter);
                modelServer.setServerPath(modelServer.getServerPath() + "&bbox=" + modelParameter.getParameterValue());

                modelParameter = new ParameterOutputDto();
                modelParameter.setParameterName("srs");
                modelParameter.setParameterValue(queryGroup.getBoundSet().getCrs().toString());
                modelParameter.setParameterDesc(utilityMessageConvert.get("坐标参考系"));
                modelParameter.setParameterIsMust(utilityMessageConvert.get("是"));
                modelParameter.setParameterSort(7);
                listParameter.add(modelParameter);
                modelServer.setServerPath(modelServer.getServerPath() + "&srs=" + modelParameter.getParameterValue());
            }
        }

        // 添加 styles 参数
        modelParameter = new ParameterOutputDto();
        modelParameter.setParameterName("styles");
        modelParameter.setParameterValue(utilityMessageConvert.get("空代表默认样式"));
        modelParameter.setParameterDesc(utilityMessageConvert.get("地图样式"));
        modelParameter.setParameterIsMust(utilityMessageConvert.get("是"));
        modelParameter.setParameterSort(5);
        listParameter.add(modelParameter);
        modelServer.setServerPath(modelServer.getServerPath() + "&styles=");

        // 添加 width 参数
        modelParameter = new ParameterOutputDto();
        modelParameter.setParameterName("width");
        modelParameter.setParameterValue(utilityMessageConvert.get("正整数"));
        modelParameter.setParameterDesc(utilityMessageConvert.get("宽度"));
        modelParameter.setParameterIsMust(utilityMessageConvert.get("是"));
        modelParameter.setParameterSort(8);
        listParameter.add(modelParameter);
        modelServer.setServerPath(modelServer.getServerPath() + "&width=768");

        // 添加 height 参数
        modelParameter = new ParameterOutputDto();
        modelParameter.setParameterName("height");
        modelParameter.setParameterValue(utilityMessageConvert.get("正整数"));
        modelParameter.setParameterDesc(utilityMessageConvert.get("高度"));
        modelParameter.setParameterIsMust(utilityMessageConvert.get("是"));
        modelParameter.setParameterSort(9);
        listParameter.add(modelParameter);
        modelServer.setServerPath(modelServer.getServerPath() + "&height=670");

        // 添加 format 参数
        modelParameter = new ParameterOutputDto();
        modelParameter.setParameterName("format");
        modelParameter.setParameterValue("kml");
        modelParameter.setParameterDesc(utilityMessageConvert.get("返回格式"));
        modelParameter.setParameterIsMust(utilityMessageConvert.get("是"));
        modelParameter.setParameterSort(10);
        listParameter.add(modelParameter);
        modelServer.setServerPath(modelServer.getServerPath() + "&format=kml");

        // 设置请求参数并添加到列表
        modelServer.setServerRequestParameters(new ArrayList<>(listParameter));
        list.add(modelServer);
        return list;
    }

    /**
     * 根据经度计算列号
     *
     * @param longitude  经度
     * @param resolution 分辨率
     * @return 列号
     */
    private int getColFromLongitude(double longitude, double resolution) {
//        return (int) Math.floor((longitude + 180) / resolution);
        double value = Math.abs(-180.0 - longitude) % 360.0;
        return (int) Math.floor(value / resolution);
    }

    /**
     * 根据纬度计算行号
     *
     * @param latitude   纬度
     * @param resolution 分辨率
     * @return 行号
     */
    private int getRowFromLatitude(double latitude, double resolution) {
//        return (int) Math.floor((90 - latitude) / resolution);
        double value = Math.abs(-90.0 - latitude) % 180.0;
        return (int) Math.floor(value / resolution);
    }

    /**
     * 获取图层属性查询api
     *
     * @return
     */
    public ServerInterfaceOutputDto getLayerFieldQuery(String dataId) {
        ServerInterfaceOutputDto modelServer = new ServerInterfaceOutputDto();
        modelServer.setServerName("LayerFieldQuery");
        modelServer.setServerDesc(utilityMessageConvert.get("图层属性查询api"));
        modelServer.setRequestType("GET");
        modelServer.setServerPath(String.format("%s/api/layerContent/GetAttrtableByLayerId", appSettingConfig.getDataResourceIp()));
        modelServer.setServerSort(1);
        modelServer.setServerKeyWord("LayerFieldQuery");

        List<ParameterOutputDto> listParameter = new ArrayList<>();

        ParameterOutputDto modelParameter = new ParameterOutputDto();
        modelParameter.setParameterName("layerId");
        modelParameter.setParameterValue(dataId);
        modelParameter.setParameterDesc(utilityMessageConvert.get("数据id"));
        modelParameter.setParameterIsMust(utilityMessageConvert.get("是"));
        modelParameter.setParameterSort(1);

        listParameter.add(modelParameter);
        modelServer.setServerPath(modelServer.getServerPath() + "?layerId=" + dataId);

        ParameterOutputDto PageIndexParameter = new ParameterOutputDto();
        PageIndexParameter.setParameterName("PageIndex");
        PageIndexParameter.setParameterValue("1");
        PageIndexParameter.setParameterDesc(utilityMessageConvert.get("起始页"));
        PageIndexParameter.setParameterIsMust(utilityMessageConvert.get("是"));
        PageIndexParameter.setParameterSort(2);

        listParameter.add(PageIndexParameter);
        modelServer.setServerPath(modelServer.getServerPath() + "&PageIndex=1");

        ParameterOutputDto PageSizeParameter = new ParameterOutputDto();
        PageSizeParameter.setParameterName("PageSize");
        PageSizeParameter.setParameterValue("10");
        PageSizeParameter.setParameterDesc(utilityMessageConvert.get("每页条数"));
        PageSizeParameter.setParameterIsMust(utilityMessageConvert.get("是"));
        PageSizeParameter.setParameterSort(3);

        listParameter.add(PageSizeParameter);
        modelServer.setServerPath(modelServer.getServerPath() + "&PageSize=10");
        modelServer.setServerRequestParameters(listParameter);
        return modelServer;
    }

    /**
     * 获取图层空间查询api
     *
     * @return
     */
    public ServerInterfaceOutputDto getLayerSpaceQuery(String dataId) {
        ServerInterfaceOutputDto modelServer = null;
        modelServer = new ServerInterfaceOutputDto();
        modelServer.setServerName("LayerSpaceQuery");
        modelServer.setServerDesc(utilityMessageConvert.get("图层空间查询api"));
        modelServer.setRequestType("GET");
        modelServer.setServerPath(String.format("%s/api/layerContent/pageAttrInfoBySwitch", appSettingConfig.getDataResourceIp()));
        modelServer.setServerSort(1);
        modelServer.setServerKeyWord("LayerSpaceQuery");

        List<ParameterOutputDto> listParameter = new ArrayList<>();

        ParameterOutputDto PageIndexParameter = new ParameterOutputDto();
        PageIndexParameter.setParameterName("PageIndex");
        PageIndexParameter.setParameterValue("1");
        PageIndexParameter.setParameterDesc(utilityMessageConvert.get("起始页"));
        PageIndexParameter.setParameterIsMust(utilityMessageConvert.get("是"));
        PageIndexParameter.setParameterSort(1);

        listParameter.add(PageIndexParameter);
        modelServer.setServerPath(modelServer.getServerPath() + "?PageIndex=1");

        ParameterOutputDto PageSizeParameter = new ParameterOutputDto();
        PageSizeParameter.setParameterName("PageSize");
        PageSizeParameter.setParameterValue("10");
        PageSizeParameter.setParameterDesc(utilityMessageConvert.get("每页条数"));
        PageSizeParameter.setParameterIsMust(utilityMessageConvert.get("是"));
        PageSizeParameter.setParameterSort(2);

        listParameter.add(PageSizeParameter);
        modelServer.setServerRequestParameters(listParameter);
        modelServer.setServerPath(modelServer.getServerPath() + "&PageSize=10");

        // requestBody
        List<ParameterOutputDto> bodyRequestParam = getBodyRequestParam(dataId);
        modelServer.setServerRequestBodyParameters(bodyRequestParam);
        return modelServer;
    }

    /**
     * 获取图层空间下载api
     *
     * @return
     */
    public ServerInterfaceOutputDto getLayerSpaceDownload(String dataId) {
        ServerInterfaceOutputDto modelServer = new ServerInterfaceOutputDto();
        modelServer.setServerName("LayerSpaceDownload");
        modelServer.setServerDesc(utilityMessageConvert.get("图层空间下载api"));
        modelServer.setRequestType("GET");
        modelServer.setServerPath(String.format("%s/api/layerContent/downloadAttrInfoBySwitch", appSettingConfig.getDataResourceIp()));
        modelServer.setServerSort(1);
        modelServer.setServerKeyWord("LayerSpaceDownload");


        List<ParameterOutputDto> listParameter = new ArrayList<>();

        ParameterOutputDto PageIndexParameter = new ParameterOutputDto();
        PageIndexParameter.setParameterName("PageIndex");
        PageIndexParameter.setParameterValue("1");
        PageIndexParameter.setParameterDesc(utilityMessageConvert.get("起始页"));
        PageIndexParameter.setParameterIsMust(utilityMessageConvert.get("是"));
        PageIndexParameter.setParameterSort(1);

        listParameter.add(PageIndexParameter);
        modelServer.setServerPath(modelServer.getServerPath() + "?PageIndex=1");

        ParameterOutputDto PageSizeParameter = new ParameterOutputDto();
        PageSizeParameter.setParameterName("PageSize");
        PageSizeParameter.setParameterValue("10");
        PageSizeParameter.setParameterDesc(utilityMessageConvert.get("每页条数"));
        PageSizeParameter.setParameterIsMust(utilityMessageConvert.get("是"));
        PageSizeParameter.setParameterSort(2);

        listParameter.add(PageSizeParameter);
        modelServer.setServerRequestParameters(listParameter);
        modelServer.setServerPath(modelServer.getServerPath() + "&PageSize=10");
        // requestBody
        List<ParameterOutputDto> bodyRequestParam = getBodyRequestParam(dataId);
        modelServer.setServerRequestBodyParameters(bodyRequestParam);
        return modelServer;
    }

    private List<ParameterOutputDto> getBodyRequestParam(String dataId) {
        List<ParameterOutputDto> bodyList = new ArrayList<>();
        ParameterOutputDto layerIdParameter = new ParameterOutputDto();
        layerIdParameter.setParameterName("layerId");
        layerIdParameter.setParameterValue(dataId);
        layerIdParameter.setParameterType("字符串");
        layerIdParameter.setParameterDesc(utilityMessageConvert.get("数据Id"));
        layerIdParameter.setParameterIsMust(utilityMessageConvert.get("是"));
        layerIdParameter.setParameterSort(1);
        bodyList.add(layerIdParameter);

        ParameterOutputDto whereParameter = new ParameterOutputDto();
        whereParameter.setParameterName("where");
        whereParameter.setParameterValue("");
        whereParameter.setParameterType("json对象集合");
        whereParameter.setParameterDesc(utilityMessageConvert.get("查询sql集合"));
        whereParameter.setParameterIsMust(utilityMessageConvert.get("否"));
        whereParameter.setParameterSort(2);
        bodyList.add(whereParameter);

        ParameterOutputDto wktParameter = new ParameterOutputDto();
        wktParameter.setParameterName("wkt");
        wktParameter.setParameterValue("");
        wktParameter.setParameterType("字符串");
        wktParameter.setParameterDesc(utilityMessageConvert.get("wkt"));
        wktParameter.setParameterIsMust(utilityMessageConvert.get("否"));
        wktParameter.setParameterSort(3);
        bodyList.add(wktParameter);

        ParameterOutputDto distanceParameter = new ParameterOutputDto();
        distanceParameter.setParameterName("distance");
        distanceParameter.setParameterValue("");
        distanceParameter.setParameterType("double");
        distanceParameter.setParameterDesc(utilityMessageConvert.get("半径"));
        distanceParameter.setParameterIsMust(utilityMessageConvert.get("否"));
        distanceParameter.setParameterSort(4);
        bodyList.add(distanceParameter);

        ParameterOutputDto lonParameter = new ParameterOutputDto();
        lonParameter.setParameterName("lon");
        lonParameter.setParameterValue("");
        lonParameter.setParameterType("double");
        lonParameter.setParameterDesc(utilityMessageConvert.get("经度"));
        lonParameter.setParameterIsMust(utilityMessageConvert.get("否"));
        lonParameter.setParameterSort(5);
        bodyList.add(lonParameter);

        ParameterOutputDto latParameter = new ParameterOutputDto();
        latParameter.setParameterName("lat");
        latParameter.setParameterValue("");
        latParameter.setParameterType("double");
        latParameter.setParameterDesc(utilityMessageConvert.get("纬度"));
        latParameter.setParameterIsMust(utilityMessageConvert.get("否"));
        latParameter.setParameterSort(6);
        bodyList.add(latParameter);

        return bodyList;
    }


}
