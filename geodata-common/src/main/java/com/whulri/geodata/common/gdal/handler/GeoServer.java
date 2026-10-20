package com.whulri.geodata.common.gdal.handler;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whulri.geodata.common.config.AppSettingConfig;
import com.whulri.geodata.common.constant.GeoServerConstants;
import com.whulri.geodata.common.dto.layerField.LayerFieldDto;
import com.whulri.geodata.common.gdal.pojo.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import com.whulri.geodata.common.bridge.Func;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import jakarta.annotation.Resource;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Data
@Component
@Slf4j
@AllArgsConstructor
@NoArgsConstructor
public class GeoServer {

    private static String baseUrl;
    private static String restUrl;

//    @Resource
//    private GeoServerConfig geoserverConfig;

    @Resource
    private AppSettingConfig appSettingConfig;

    public String GEOSERVER_USERNAME = "admin";
    public String GEOSERVER_PASSWORD = "geoserver";
    private Map<Method, String> _RequestMethodLookup = new HashMap<Method, String>() {{
        put(Method.GET, "GET");
        put(Method.PUT, "PUT");
        put(Method.DELETE, "DELETE");
        put(Method.POST, "POST");
    }};
    private Map<ContentType, String> _ContentTypeLookup = new HashMap<ContentType, String>() {{
        put(ContentType.Html, "text/html");
        put(ContentType.Xml, "text/xml");
        put(ContentType.SldFile, "application/vnd.ogc.sld+xml");
        put(ContentType.Zip, "application/zip");
    }};
    private Map<AcceptType, String> _AcceptTypeLookup = new HashMap<AcceptType, String>() {{
        put(AcceptType.Xml, "text/xml");
        put(AcceptType.Html, "text/html");
        put(AcceptType.Byte, "application/octet-stream");
    }};
    private Map<SrsType, String> _SrsLookup = new HashMap<SrsType, String>() {{
        put(SrsType.Epsg4326, "EPSG:4326");
        put(SrsType.EPSG404000, "EPSG:404000");

    }};
    public Map<TaskState, String> taskStateLookup = new HashMap<TaskState, String>() {{
        put(TaskState.Running, "running");
        put(TaskState.Pending, "pending");
        put(TaskState.All, "all");
    }};

    private String serverIp;
    private String serverPort;

    public GeoServer(String serverIP, String serverPort) {
        this.serverIp = serverIP;
        this.serverPort = serverPort;
    }

    public GeoServer(String serverIP, String serverPort, String serverUser, String serverPwd) {
        this.serverIp = serverIP;
        this.serverPort = serverPort;
        GEOSERVER_USERNAME = serverUser;
        GEOSERVER_PASSWORD = serverPwd;
    }

    public void updateStyle(String styleName, String styleContent) {
        if (styleContent == null || styleContent.isEmpty()) {
            return;
        }

        // 获取样式目录路径（Java中使用Path处理文件路径）
        Path styleDir = Paths.get(System.getProperty("user.dir"), "Style");

        // 创建目录（如果不存在）
        if (!Files.exists(styleDir)) {
            try {
                Files.createDirectories(styleDir);
            } catch (Exception e) {
                // 处理目录创建异常
                throw new RuntimeException("创建样式目录失败", e);
            }
        }

        // 构造SLD文件路径
        Path filePath = styleDir.resolve(styleName + ".sld");

        // 写入SLD文件（使用GB2312编码）
        try {
            Files.write(filePath, styleContent.getBytes(Charset.forName("GB2312")));
        } catch (Exception e) {
            throw new RuntimeException("写入SLD文件失败", e);
        }
        String geoWorkspace = appSettingConfig.getGeoWorkspace();
        // 获取当前工作空间的样式列表
        List<Style> styles = getStyles(appSettingConfig.getGeoWorkspace());

        // 检查样式是否存在（Java 8+ 使用stream().anyMatch()替代List.Exists）
        boolean styleExists = styles.stream().anyMatch(style -> style.getName().equals(styleName));

        if (styleExists) {
            // 修改已存在的样式
            modifyStyle(styleName, filePath.toString(), geoWorkspace);
        } else {
            // 添加新样式（Path.getFileName()获取文件名）
            String fileName = filePath.getFileName().toString();
            addStyle(styleName, fileName, filePath.toString(), geoWorkspace);
        }
    }

    /**
     * 判断工作区是否存在
     *
     * @param name 工作去名称标识符
     * @return Boolean
     */
    public Boolean isExistWorkSpace(String name) throws Exception {
        List<WorkSpace> list = getWorkSpaces();
        for (WorkSpace item : list) {
            if (item.getName().equals(name))
                return true;
        }
        return false;
    }

    /**
     * @return
     * @throws Exception
     */
    public List<WorkSpace> getWorkSpaces() {
        String requestString = getRestServiceUrl() + "/workspaces";
        WorkSpacesResponse workSpaceResponse = new WorkSpacesResponse();
        JSONObject status = null;
        try {
            status = sendRestRequest(requestString, Method.GET, WorkSpacesResponse.class, workSpaceResponse, ContentType.Json, AcceptType.Json);
        } catch (Exception ex) {
            throw new RuntimeException("获取所有工作区异常！", ex);
        }

        if (status.getBoolean("status")) {
//            List<WorkSpace> workSpaces = status.getJSONObject("payLoad").toJavaObject(WorkSpacesResponse.class).getWorkSpaces();
            JSONArray jsonArray = status.getJSONObject("payLoad").getJSONArray("workSpaces");
            List<WorkSpace> workSpaces = jsonArray.toJavaList(WorkSpace.class);
            if (workSpaces != null) {
                for (WorkSpace workSpace : workSpaces) {
                    workSpace.setGeoServer(this);
                }
                return workSpaces;
            } else {
                return new ArrayList<>();
            }
        } else {
            return new ArrayList<>();
        }
    }

    /**
     * 获取默认工作区
     *
     * @return WorkSpace
     * @throws Exception
     */
    public WorkSpace getDefaultWorkSpace() throws Exception {
        String requestString = getRestServiceUrl() + "/workspaces/default";
        DefaultWorkSpaceResponse defaultWorkSpace = null;
        JSONObject status = null;

        try {
            status = sendRestRequest(requestString, Method.GET, DefaultWorkSpaceResponse.class, defaultWorkSpace, ContentType.Json, AcceptType.Json);
        } catch (Exception ex) {
            throw new Exception("获取默认工作区异常！", ex);
        }

        WorkSpace workSpace = (defaultWorkSpace instanceof DefaultWorkSpaceResponse && status.get("status").toString().equals("true")) ? defaultWorkSpace.WorkSpace : null;
        if (workSpace != null) {
            workSpace.setGeoServer(this);
        }
        return workSpace;
    }

    /**
     * 添加工作区
     *
     * @param workSpaceName 工作区名称
     * @return boolean
     * @throws Exception
     */
    public boolean addWorkSpace(String workSpaceName) {
        String requestUrl = getRestServiceUrl() + "/workspaces";
        Object newWorkspace = new WorkSpaceRequest(workSpaceName);
        JSONObject status;
        try {
            status = sendRestRequest(requestUrl, Method.POST, WorkSpaceRequest.class, newWorkspace, ContentType.Json, AcceptType.Json);
//            status = sendRestRequest(requestUrl, Method.POST, String.class, workSpaceName, ContentType.Json, AcceptType.Json);
        } catch (Exception ex) {
            throw new RuntimeException("添加工作区失败，可能是工作区名称已存在引起的！", ex);
        }
        if (status.get("status").toString().equals("true")) {
            return true;
        } else {
            return false;
        }

    }
    /// <param name="workSpaceName">工作区名称</param>
/*    public Boolean addWorkSpace(String workSpaceName) throws Exception {
        String requestUrl = getRestServiceUrl() + "/workspaces";
       Object newWorkspace = new WorkSpaceRequest(workSpaceName);

        String status = "";

        try
        {
            status = SendRestRequest(requestUrl, RequestMethod.Post, WorkSpaceRequest), ref newWorkspace, ContentType.Json, AcceptType.Json);
            if (status == "Created")
            {
                return true;
            }
            return false;
        }
        catch (Exception ex)
        {
            throw new Exception("添加工作区失败，可能是工作区名称已存在引起的！", ex);
        }
    }*/


    /**
     * 删除工作区
     *
     * @param workSpaceName
     * @return
     * @throws Exception
     */
    public boolean deleteWorkSpace(String workSpaceName) throws Exception {
        String requestUrl = getRestServiceUrl() + "/workspaces/" + workSpaceName + "?recurse=true";
        JSONObject workspaceRequest = null;
        JSONObject status;
        try {
            status = sendRestRequest(requestUrl, Method.DELETE, WorkSpaceRequest.class, workspaceRequest, ContentType.Json, AcceptType.Json);
        } catch (Exception ex) {
            throw new Exception("删除工作区失败！", ex);
        }
        if (status.get("status").toString().equals("true")) {
            return true;
        } else {
            return false;
        }
    }
/*    public Boolean deleteWorkSpace(String workSpaceName)
    {
        String requestUrl = getRestServiceUrl() + "/workspaces/" + workSpaceName + "?recurse=true";
       Object workspaceRequest = null;

        String status = "";

        try
        {
            status = SendRestRequest(requestUrl, RequestMethod.Delete, WorkSpaceRequest), ref workspaceRequest, ContentType.Json, AcceptType.Json);
            if (status == "OK")
            {
                return true;
            }
            return false;
        }
        catch (Exception ex)
        {
            throw new Exception("删除工作区失败！", ex);
        }
    }*/
    /*<<<<<<--工作区代码结束-->>>>>> */

    /*<<<<<<--栅格数据代码开始-->>>>>> */

    /**
     * 确定覆盖数据是否存在
     *
     * @param workSpace
     * @param coverStoreName
     * @return
     * @throws Exception
     */
    public boolean IsExistCoverageStore(String workSpace, String coverStoreName) throws Exception {
        List<CoverageStore> list = getCoverageStores(workSpace);
        for (CoverageStore item : list) {
            if (item.Name.equals(coverStoreName)) return true;
        }
        return false;
    }
 /*   public Boolean IsExistCoverageStore(String workSpace, String coverStore)
    {
        var list = GetCoverageStores(workSpace);
        foreach (var item in list)
        {
            if (item.Name == coverStore)
                return true;
        }
        return false;
    }*/

    public List<CoverageStore> getCoverageStores(String workSpace) throws Exception {
        if (workSpace == null) {
            workSpace = appSettingConfig.getGeoWorkspace();
        }
        String requestUrl = getRestServiceUrl() + "/workspaces/" + workSpace + "/coveragestores";
        Object dataStoresResponse = null;
        JSONObject status = null;

        try {
            status = sendRestRequest(requestUrl, Method.GET, CoverageStoreResponse.class, dataStoresResponse, ContentType.Json, AcceptType.Json);
        } catch (Exception ex) {
            throw new Exception("获取数据存储异常！", ex);
        }

        if (dataStoresResponse instanceof CoverageStoreResponse && status.get("status").toString().equals("true")) {
            CoverageStoreResponse response = (CoverageStoreResponse) dataStoresResponse;

          /*  for (CoverageStore store : response.CoverageStores) {

            }*/
            return response.CoverageStores;
        } else {
            return new ArrayList<CoverageStore>();
        }
    }

//    public String putCoverageStoresLocal(String tifPath) {
//        String workSpace = geoserverConfig.getWorkSpace();
//        File file1 = new File(tifPath);
//        String layerName = file1.getName();
//        String name = layerName.substring(0, layerName.indexOf("."));
//        String replace = geoserverConfig.getUrl() + "/" + workSpace + "/gwc/service/wmts";
//        Path destPath = Paths.get(geoserverConfig.getDataPath(), layerName);
//
//        try {
//            tiffileReader tiffileReader = new tiffileReader(tifPath);
//            byte[][] tifData = tiffileReader.ReadAsByte(0, 0);
//            TiffileInfo tiffileInfo = tiffileReader.get_tiffileInfo();
//            if (tiffileInfo.getRasterCount() > 3) {
//                tiffileWriter tiffileWriter = new tiffileWriter(destPath.toString(), tiffileInfo.getXSize(), tiffileInfo.getYSize(), 3, DataType.Byte, tiffileInfo.getGeoTransform(), tiffileInfo.getProjection());
//                tiffileWriter.WriteAsByte(tifData, 0, 0);
//            } else {
//                FileUtil.copy(Paths.get(tifPath), destPath, StandardCopyOption.REPLACE_EXISTING);
//            }
//        } catch (Exception e) {
//            FileUtil.copy(Paths.get(tifPath), destPath, StandardCopyOption.REPLACE_EXISTING);
//            log.error("putCoverageStoresLocal:", e);
//        }
//        String a = Paths.get("data/temp", layerName).toString().replace("\\", "/");
//        try {
//            Boolean aBoolean = putCoverageStoresLocal(workSpace, name, a);
//            Boolean bBoolean = putStoreTitleLocal(workSpace, name, name, name, "EPSG:4326", null, 0);
//            if (aBoolean && bBoolean) {
//                log.info("发布成功");
//            } else {
//                log.info("有可能发布文件已存在");
//            }
//            ConverageRequest converageRequest = getConverageInfo(workSpace, name, name);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        return replace + "," + name;
//    }

    /// <summary>
    /// 发布GeoServer目录下的tif
    /// </summary>
    /// <param name="workSpace">工作区</param>
    /// <param name="coverName">存储名称</param>
    /// <param name="tifPath">tif路径</param>
    /// <returns></returns>
    public Boolean putCoverageStoresLocal(String workSpace, String coverName, String tifPath) throws Exception {
        String requestUrl = getRestServiceUrl() + "/workspaces/" + workSpace + "/coveragestores";
        log.info("requestUrl:{}", requestUrl);
        // 构建 XML 请求体
        StringBuilder xml = new StringBuilder();
        xml.append("<coverageStore>")
                .append("<name>").append(coverName).append("</name>")
                .append("<type>GeoTIFF</type>")
                .append("<enabled>true</enabled>")
                .append("<workspace><name>").append(workSpace).append("</name></workspace>")
                .append("<url>file:").append(tifPath).append("</url>")
                .append("</coverageStore>");
        String xmlBody = xml.toString();

        try {
            // 调用通用 REST 方法
            // 注意：ContentType 必须是 Xml（对应 text/xml），AcceptType 建议用 Xml 或 Text
            Object responseObj = sendRestRequest(
                    requestUrl,
                    Method.POST,
                    String.class,
                    xmlBody,
                    ContentType.Html,
                    AcceptType.Json
            );

            // 解析响应（假设 sendRestRequest 返回包含 status 和 statusCode 的 JSONObject）
            JSONObject response;
            if (responseObj instanceof JSONObject) {
                response = (JSONObject) responseObj;
            } else {
                response = JSONObject.parseObject(responseObj.toString());
            }

            boolean success = response.getBooleanValue("status");
            int statusCode = response.getIntValue("statusCode");

            // GeoServer 成功创建 CoverageStore 返回 HTTP 201 Created
            return success && (statusCode == 201);

        } catch (Exception ex) {
            throw new Exception("获取数据存储异常！", ex);
        }
    }

    public Boolean putStoreTitleLocal(String workSpace, String coveragestoreName, String coverName, String title, String desiredSrs, String bBox, int rasterCount) throws Exception {
        String requestUrl = getRestServiceUrl() + "/workspaces/" + workSpace + "/coveragestores/" + coveragestoreName + "/coverages";

        StringBuilder newDataStore = new StringBuilder();
        newDataStore.append("<coverage>");
        newDataStore.append("<name>").append(coverName).append("</name>");
        newDataStore.append("<nativeName>").append(coverName).append("</nativeName>");
        newDataStore.append("<title>").append(title).append("</title>");
        newDataStore.append("<srs>").append(desiredSrs).append("</srs>");

        String bbox = "";
        if (StrUtil.isEmptyIfStr(bBox)) {
            bbox = String.format(
                    "<nativeBoundingBox><minx>%s</minx><maxx>%s</maxx><miny>%s</miny><maxy>%s</maxy><crs>%s</crs></nativeBoundingBox>" +
                            "<latLonBoundingBox><minx>%s</minx><maxx>%s</maxx><miny>%s</miny><maxy>%s</maxy><crs>%s</crs></latLonBoundingBox>",
                    -1, 0, -1, 0, desiredSrs,
                    -1, 0, -1, 0, desiredSrs
            );
        } else {
            String[] list = bBox.split(",");
            bbox = String.format(
                    "<nativeBoundingBox><minx>%s</minx><maxx>%s</maxx><miny>%s</miny><maxy>%s</maxy><crs>%s</crs></nativeBoundingBox>" +
                            "<latLonBoundingBox><minx>%s</minx><maxx>%s</maxx><miny>%s</miny><maxy>%s</maxy><crs>%s</crs></latLonBoundingBox>",
                    list[0], list[1], list[2], list[3], desiredSrs,
                    list[0], list[1], list[2], list[3], desiredSrs
            );
        }
        newDataStore.append(bbox);
        newDataStore.append("<nativeFormat>GeoTIFF</nativeFormat>");
        newDataStore.append("<parameters>");

        if (rasterCount != 1) {
            newDataStore.append(String.format(
                    "<entry><string>InputTransparentColor</string><string>%s</string></entry>",
                    appSettingConfig.getTiffRemoveBackGroundColor()
            ));
        }
        newDataStore.append("<entry><string>SUGGESTED_TILE_SIZE</string><string>512,512</string></entry>");
        newDataStore.append("</parameters>");
        newDataStore.append("</coverage>");

        String payLoad = newDataStore.toString();
        try {
            Object o = sendRestRequest(requestUrl, Method.POST, String.class, payLoad, ContentType.Html, AcceptType.Json);
            /*status = HttpUtil.post(requestUrl, newDataStore.toString());*/
            JSONObject jsonObject = JSONObject.parseObject(o.toString());
            jsonObject.get("status").toString();
            /* status = HttpUtil.post(requestUrl, newDataStore.toString());*/
            if (jsonObject.get("status").toString().equals("true")) {
                return true;
            }
            return false;
        } catch (Exception ex) {
            throw new Exception("修改数据存储异常！", ex);
        }
    }


    public Boolean putCoverageStore(String workSpace, String coverName, String tifPath) {
        // 使用 configure=all 参数，允许在创建 store 时自动配置 coverage
        String requestUrl = getRestServiceUrl() + "/workspaces/" + workSpace + "/coveragestores/" + coverName + "/" + "file.geotiff?configure=all&coverageName=" + coverName;
        JSONObject status;

        Object newDataStore = tifPath;
        try {
            status = sendRestRequest(requestUrl, Method.PUT, String.class,
                    newDataStore, tifPath.endsWith(".zip") ? ContentType.Zip : ContentType.SldFile, AcceptType.Json);
            if (status.getBoolean("status")) {
                return true;
            }
        } catch (Exception ex) {
            try {
                throw new Exception("添加数据存储异常！", ex);
            } catch (Exception e) {
                log.error("添加数据存储异常", e);
            }
        }
        return false;
    }

    /*public Boolean deleteCoverageStore(String workSpace, String coverName) throws Exception {
        String requestUrl = getRestServiceUrl() + "/workspaces/" + workSpace + "/coveragestores/" + coverName + "?recurse=true";
       Object request = null;

        String status = "";

        try
        {
            status = SendRestRequest(requestUrl, RequestMethod.Delete, Object), ref request, ContentType.Json, AcceptType.Json);
            if (status == "OK")
            {
                return true;
            }
            return false;
        }
        catch (Exception ex)
        {
            throw new Exception("删除数据存储异常！", ex);
        }
    }*/

    public Boolean updateStoreTitle(String workSpace, String coveragestoreName, String coverName, String title, String desiredSrs, int rasterCount) {
        String requestUrl = getRestServiceUrl() + "/workspaces/" + workSpace + "/coveragestores/" + coveragestoreName + "/coverages/" + coverName;
        JSONObject status;

        Object newDataStore = "<coverage>";
        newDataStore += "<title>" + title + "</title>";
        newDataStore += "<enabled>true</enabled>";
        if (!StrUtil.isEmptyIfStr(desiredSrs)) {
            newDataStore += "<srs>" + desiredSrs + "</srs>";
        }
        newDataStore += "<parameters>";
        if (StrUtil.isEmptyIfStr(appSettingConfig.getTiffRemoveBackGroundColor())) {
            //newDataStore += String.Format("<entry><String>InputTransparentColor</String><String>{0}</String></entry>", ConfigHelper.TIFF_RemoveBackGroundColor);
        } else {
            if (rasterCount == 1) {
                //单波段
                //发布样式，修改地图关联样式发布
            } else {
                //3波段
                newDataStore += String.format("<entry><string>InputTransparentColor</string><string>%s</string></entry>", appSettingConfig.getTiffRemoveBackGroundColor());

            }
        }
        newDataStore += "<entry><string>SUGGESTED_TILE_SIZE</string><string>512,512</string></entry>";
        newDataStore += "</parameters>";
        newDataStore += "</coverage>";
        try {
            status = sendRestRequest(requestUrl, Method.PUT, String.class, newDataStore, ContentType.Html, AcceptType.Json);
            return status.getBoolean("status");
        } catch (Exception ex) {
            try {
                throw new Exception("修改数据存储异常！", ex);
            } catch (Exception e) {
                log.error("修改数据存储异常", e);
            }
        }
        return false;
    }

    public ConverageRequest getConverageInfo(String workSpace, String coveragestoreName, String coverName) throws Exception {

        String requestUrl = getRestServiceUrl() + "/workspaces/" + workSpace + "/coveragestores/" + coveragestoreName + "/coverages/" + coverName;

        Object dataStoresResponse = null;
        try {
            Object o = sendRestRequest(requestUrl, Method.GET, String.class, dataStoresResponse, ContentType.Html, AcceptType.Json);
            JSONObject jsonObject = JSONObject.parseObject(o.toString());
            ConverageRequest conver = JSONObject.parseObject(o.toString(), ConverageRequest.class);
            jsonObject.get("status").toString();
            dataStoresResponse = jsonObject.get("payLoad").toString();

            /* status = HttpUtil.post(requestUrl, newDataStore.toString());*/
            if (jsonObject.get("status").toString().equals("true")) {
                if (dataStoresResponse != null) {
                    /* Document document = Jsoup.parse(dataStoresResponse);*/
                    /*  Elements e=document.getElementsByTag("li");*/
                    JSONObject data = JSONObject.parseObject(dataStoresResponse.toString());

                    String coverage = data.get("coverage").toString();
                    JSONObject jsonCoverage = JSONObject.parseObject(coverage);


                    if (jsonCoverage != null && !StrUtil.isEmptyIfStr(jsonCoverage.get("name").toString())) {
                        Coverage coverage1 = new Coverage();
                        coverage1.Name = jsonCoverage.get("name").toString();
                        if (jsonCoverage.get("srs") != null) {
                            coverage1.Srs = jsonCoverage.get("srs").toString();
                        }
                        if (jsonCoverage.get("latLonBoundingBox") != null) {
                            String latLonBoundingBox = jsonCoverage.get("latLonBoundingBox").toString();
                            JSONObject latLon = JSONObject.parseObject(latLonBoundingBox);

                            coverage1.West = Double.valueOf(latLon.get("minx").toString());
                            coverage1.South = Double.valueOf(latLon.get("miny").toString());
                            coverage1.East = Double.valueOf(latLon.get("maxx").toString());
                            coverage1.North = Double.valueOf(latLon.get("maxy").toString());
                        }


                        ConverageRequest converageRequest = new ConverageRequest();
                        converageRequest.setCoverage(coverage1);
                        return converageRequest;
                    }
                }
            }
            return null;
        } catch (Exception ex) {
            throw new Exception("修改数据存储异常！", ex);
        }
    }

/*    public Object publishTif(String tilPath,String layerName){

    }*/


    //endregion

    //region 数据存储
    /// <summary>
    /// 数据存储是否存在
    /// </summary>
    /// <param name="workSpace">工作区名称</param>
    /// <param name="dataStore">数据存储名称</param>
    /// <returns></returns>
    public Boolean isExsitDataStore(String workSpace, String dataStore) {
        try {
            List<DataStore> list = getDataStores(workSpace);
            for (DataStore item : list) {
                if (item.Name.equals(dataStore))
                    return true;
            }
        } catch (Exception e) {
            log.warn("检查数据存储是否存在失败: {}", e.getMessage(), e);
        }
        return false;
    }

    /// <summary>
    /// 获取数据存储
    /// </summary>
    /// <param name="workSpace">工作区名称</param>
    /// <returns></returns>
    public List<DataStore> getDataStores(String workSpace) {
        String requestUrl = getRestServiceUrl() + "/workspaces/" + workSpace + "/datastores";
        DataStoresResponse dataStoresResponse = null;
        Boolean status = false;

        try {
            Object o = sendRestRequest(requestUrl, Method.GET, DataStoresResponse.class, dataStoresResponse, ContentType.Json, AcceptType.Json);
        } catch (Exception ex) {
            throw new RuntimeException("获取数据存储异常！", ex);
        }

        if (dataStoresResponse instanceof DataStoresResponse) {
            for (DataStore store : dataStoresResponse.DataStores) {

            }
            return dataStoresResponse.getDataStores();
        } else {
            return new ArrayList<DataStore>();
        }
    }

    /// <summary>
    /// 添加数据存储
    /// </summary>
    /// <param name="workSpace">工作区名称</param>
    /// <param name="storeName">数据存储名称</param>
    /// <param name="host">数据库主机地址</param>
    /// <param name="port">数据库端口号</param>
    /// <param name="dataBase">数据库名称</param>
    /// <param name="userName">用户名</param>
    /// <param name="userPassword">密码</param>
    public Boolean addDataStore(String workSpace, String storeName, String host, String port, String dataBase, String userName, String userPassword) {
        String requestUrl = getRestServiceUrl() + "/workspaces/" + workSpace + "/datastores";
        //object newDataStore = new DataStoreRequest(workSpace, storeName, host, port, dataBase, userName, userPassword, "postgis");
        StringBuilder parameters = new StringBuilder();
        parameters.append("<host>");
        parameters.append(host);
        parameters.append("</host>");
        parameters.append("<port>");
        parameters.append(port);
        parameters.append("</port>");
        parameters.append("<database>");
        parameters.append(dataBase);
        parameters.append("</database>");
        parameters.append("<schema>public</schema>");
        parameters.append("<user>");
        parameters.append(userName);
        parameters.append("</user>");
        parameters.append("<passwd>");
        parameters.append(userPassword);
        parameters.append("</passwd>");
        parameters.append("<dbtype>postgis</dbtype>");

        String newDataStore = String.format("<dataStore><name>" + storeName + "</name><connectionParameters>%s</connectionParameters></dataStore>", parameters);

        try {
            JSONObject status = sendRestRequest(requestUrl, Method.POST, String.class, newDataStore, ContentType.Html, AcceptType.Json);
            return status.getBoolean("status");
        } catch (Exception ex) {
            throw new RuntimeException("添加数据存储异常！", ex);
        }
    }

    /**
     * 获取默认样式
     *
     * @param layerType 图层类型
     * @return 默认样式名称
     */
    public String getDefaultStyle(String layerType) {
        switch (layerType) {
            case "点":
            case "POINT":
                return "point";
            case "线":
            case "LINE":
                return "line";
            case "面":
            case "POLYGON":
                return "polygon";
            default:
                return "";
        }
    }

    /// <summary>
    /// 添加矢量数据
    /// </summary>
    /// <param name="workSpace">工作区名称</param>
    /// <param name="storeName">数据存储名称</param>
    /// <param name="type">类型（shapefile）</param>
    /// <param name="shpPaht">shp路径（file:data/shapefiles/）</param>
    /// <returns></returns>
//    public Boolean addDataSHP(String workSpace, String storeName,String type,String shpPaht)
//    {
//        String requestUrl = getRestServiceUrl() + "/workspaces/" + workSpace + "/datastores";
//        String status = "";
//       Object newDataStore = "<dataStore><name>" + storeName + "</name>"+"<connectionParameters>[Parameters]</connectionParameters></dataStore>";
//        StringBuilder parameters = new StringBuilder();
//        parameters.Append("<charset>GB2312</charset>");
//        //parameters.Append("<filetype>shapefile</filetype>");
//
//        //parameters.Append("<filetype>");
//        //parameters.Append(type);
//        //parameters.Append("</filetype>");
//
//        //parameters.Append("<create spatial index>true</create spatial index>");
//        //parameters.Append("<memory mapped buffer>false</memory mapped buffer>");
//        //parameters.Append("<timezone>Asia</timezone>");
//        //parameters.Append("<enable spatial index>true</enable spatial index>");
//        //parameters.Append("<namespace>http://www.infoearth.com</namespace>");
//        //parameters.Append("<cache and reuse memory maps>true</ache and reuse memory maps>");
//        parameters.Append("<url>");
//        parameters.Append(shpPaht);
//        parameters.Append("</url>");
//        //parameters.Append("<fstype>shape</fstype>");
//        newDataStore = (newDataStore as String).Replace("[Parameters]", parameters.ToString());
//        try
//        {
//            status = SendRestRequest(requestUrl, RequestMethod.Post, String), ref newDataStore, ContentType.Html, AcceptType.Json);
//            if (status == "Created")
//            {
//                return true;
//            }
//            return false;
//        }
//        catch (Exception ex)
//        {
//            throw new Exception("添加数据存储异常！", ex);
//        }
//    }

    /**
     * 向GeoServer添加Shapefile数据存储
     *
     * @param workSpace 工作空间名称
     * @param storeName 数据存储名称
     * @param type      类型（如shapefile）
     * @param shpPath   Shapefile路径
     * @return 添加是否成功
     */
    public void addDataSHP(String workSpace, String storeName, String type, String shpPath) throws Exception {

        String requestUrl = getRestServiceUrl() + "/rest/workspaces/" + workSpace + "/datastores";

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();

        // 创建数据存储元素
        Element dataStore = doc.createElement("dataStore");
        doc.appendChild(dataStore);

        // 添加名称子元素
        Element nameElem = doc.createElement("name");
        nameElem.appendChild(doc.createTextNode(storeName));
        dataStore.appendChild(nameElem);

        // 添加连接参数子元素
        Element connectionParameters = doc.createElement("connectionParameters");
        dataStore.appendChild(connectionParameters);

        // 构建连接参数内容
        appendParameter(connectionParameters, "charset", "UTF-8"); // 使用UTF-8编码替代GB2312
        appendParameter(connectionParameters, "url", shpPath);
        // 可以根据需要添加更多参数...

        // 将DOM文档转换为字符串
        String xmlString = convertDocumentToString(doc);

        // 发送POST请求
        JSONObject o = sendRestRequest(requestUrl, Method.POST, String.class, xmlString, ContentType.Html, AcceptType.Json);
        /*status = HttpUtil.post(requestUrl, newDataStore.toString());*/
        JSONObject jsonObject = JSONObject.parseObject(o.toString());
        /* status = HttpUtil.post(requestUrl, newDataStore.toString());*/
        if (!"true".equals(jsonObject.get("status")) || !"Created".equals(jsonObject.get("payLoad"))) {
            throw new RuntimeException(shpPath + "更新失败");
        }

    }

    private void appendParameter(Element parent, String key, String value) {
        Element param = parent.getOwnerDocument().createElement(key);
        param.appendChild(parent.getOwnerDocument().createTextNode(value));
        parent.appendChild(param);
    }

    private String convertDocumentToString(Document doc) throws TransformerException {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        StreamResult result = new StreamResult(new StringWriter());
        DOMSource source = new DOMSource(doc);
        transformer.transform(source, result);
        return result.getWriter().toString();
    }

    /// <summary>
    /// 删除数据存储
    /// </summary>
    /// <param name="workSpace">工作区名称</param>
    /// <param name="dataStore">数据存储名称</param>
    /// <returns></returns>
   /* public Boolean deleteDataStore(String workSpace, String dataStore)
    {
        String requestUrl = getRestServiceUrl() + "/workspaces/" + workSpace + "/datastores/" + dataStore + "?recurse=true";
       Object request = null;

        String status = "";

        try
        {
            status = SendRestRequest(requestUrl, RequestMethod.Delete, Object), ref request, ContentType.Json, AcceptType.Json);
            if (status == "OK")
            {
                return true;
            }
            return false;
        }
        catch (Exception ex)
        {
            throw new Exception("删除数据存储异常！", ex);
        }
    }*/
    //endregion

    //region 图层组
    /// <summary>
    /// 图层组是否存在
    /// </summary>
    /// <param name="workSpace">工作区名称</param>
    /// <param name="layerGroup">图层组名称</param>
    /// <returns></returns>
    public Boolean isExsitLayerGroup(String layerGroup, String workSpace) {
        List<String> list = getLayerGroupNames(workSpace);
        for (String s : list) {
            if (s.equals(layerGroup))
                return true;
        }
        return false;
    }

    /// <summary>
    /// 获取图层组
    /// </summary>
    /// <param name="workSpace">工作区名称</param>
    /// <returns></returns>
    public List<LayerGroup> getLayerGroups(String workSpace) {
        // 构造请求路径
        String layerGroupPath = (workSpace == null || workSpace.isEmpty()) ? "/layergroups" : "/workspaces/" + workSpace + "/layergroups";
        String requestUrl = getRestServiceUrl() + layerGroupPath;

        try {
            // 发送 GET 请求获取图层组列表
            JSONObject jsonObject1 = sendRestRequest(requestUrl, Method.GET, String.class, null, ContentType.Html, AcceptType.Json);
            if (jsonObject1.get("status") == HttpStatus.OK) {
                String responseBody = jsonObject1.get("payload").toString();

                // 反序列化响应为 LayerGroupsResponse 对象
                ObjectMapper objectMapper = new ObjectMapper();
                objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                LayerGroupsResponse layerGroupsResponse = objectMapper.readValue(responseBody, LayerGroupsResponse.class);

                // 遍历每个图层组，发送详细请求
                List<LayerGroup> layerGroupList = new ArrayList<>();
                for (LayerGroup layerGroup : layerGroupsResponse.getLayerGroups()) {
                    String newRequestUrl = requestUrl + "/" + layerGroup.Name;
                    JSONObject jsonObject = sendRestRequest(newRequestUrl, Method.GET, String.class, null, ContentType.Html, AcceptType.Json);

                    if (jsonObject.get("status") == HttpStatus.OK) {
                        String detailResponseBody = jsonObject.get("payload").toString();

                        // 替换 "null" 字符串为 null
                        detailResponseBody = detailResponseBody.replace("\"null\"", "null");

                        // 反序列化为 LayerGroupResponse 对象
                        LayerGroupResponse groupResponse = objectMapper.readValue(detailResponseBody, LayerGroupResponse.class);
                        if (groupResponse != null && groupResponse.getLayerGroup() != null) {
                            layerGroupList.add(groupResponse.getLayerGroup());
                        }
                    }
                }
                return layerGroupList;
            }
        } catch (Exception ex) {
            throw new RuntimeException("获取图层组异常！", ex);
        }
        return new ArrayList<>();
    }

    /// <summary>
    /// 获取图层组
    /// </summary>
    /// <param name="workSpace">工作区名称</param>
    /// <returns></returns>
    public List<String> getLayerGroupNames(String workSpace) {
        workSpace = null;
        String layerGroupPath = StrUtil.isEmptyIfStr(workSpace) ? "/layergroups" : "/workspaces/" + workSpace + "/layergroups";
        String requestUrl = getRestServiceUrl() + layerGroupPath;
        LayerGroupsResponse layerGroups = null;
        Boolean status = false;
        Object temp = null;

        try {
            Object o = sendRestRequest(requestUrl, Method.GET, LayerGroupsResponse.class, layerGroups, ContentType.Json, AcceptType.Json);

            JSONObject jsonObject = JSONObject.parseObject(o.toString());
            jsonObject.get("status").toString();
            /* status = HttpUtil.post(requestUrl, newDataStore.toString());*/
            if (jsonObject.get("status").toString().equals("true")) {
                if (layerGroups instanceof LayerGroupsResponse) {
                    List<String> list = new ArrayList<>();
                    String newRequestUrl = "";
                    for (LayerGroup layerGroup : layerGroups.LayerGroups) {
                        list.add(layerGroup.Name);
                    }
                    return list;
                }
            }
            return new ArrayList<>();
        } catch (Exception ex) {
            throw new RuntimeException("获取图层组异常！", ex);
        }
    }

    public void addLayerGroup(String mapName, List<String> targetLayers, List<String> targetStyles, String desiredSrs, String bBox) {
        if (!isExsitWorkSpace(appSettingConfig.getGeoWorkspace())) {
            addWorkSpace(appSettingConfig.getGeoWorkspace());
        }
        if (isExsitLayerGroup(mapName, appSettingConfig.getGeoWorkspace())) {
            deleteLayerGroup(mapName, appSettingConfig.getGeoWorkspace());
        }
        addLayerGroup(mapName, targetLayers, targetStyles, appSettingConfig.getGeoWorkspace(), desiredSrs, bBox);
    }

    /// <summary>
    /// 添加图层组
    /// </summary>
    /// <param name="layerGroupName">图层组名称</param>
    /// <param name="targetLayers">图层</param>
    /// <param name="targetStyles">样式</param>
    /// <param name="workSpace">工作区名称</param>
    public Boolean addLayerGroup(String layerGroupName, List<String> targetLayers, List<String> targetStyles, String workSpace, String desiredSrs, String bBox) {
        String layerGroupPath = StrUtil.isEmptyIfStr(workSpace) ? "/layergroups" : "/workspaces/" + workSpace + "/layergroups";
        String requestUrl = getRestServiceUrl() + layerGroupPath;

        StringBuilder layers = new StringBuilder();
        for (String targetLayer : targetLayers) {
            layers.append("<layer>");
            layers.append(targetLayer);
            layers.append("</layer>");
        }
        StringBuilder styles = new StringBuilder();
        if (targetStyles != null) {
            for (String targetStyle : targetStyles) {
                styles.append("<style>");
                styles.append(targetStyle);
                styles.append("</style>");
            }
        }

        String bbox = "";
        if (StrUtil.isEmptyIfStr(bBox)) {
            bbox = String.format("<bounds><minx>%s</minx><maxx>%s</maxx><miny>%s</miny><maxy>%s</maxy><crs>%s</crs></bounds>", -1, 0, -1, 0, desiredSrs);
        } else {
            String[] list = bBox.split(",");
            bbox = String.format("<bounds><minx>%s</minx><maxx>%s</maxx><miny>%s</miny><maxy>%s</maxy><crs>%s</crs></bounds>", list[0], list[1], list[2], list[3], desiredSrs);
        }
        String requestPayload = String.format(
                "<layerGroup><name>%s</name><title>%s</title><layers>%s</layers><styles>%s</styles>%s</layerGroup>",
                layerGroupName, layerGroupName, layers, styles, bbox);
        try {
            JSONObject status = sendRestRequest(requestUrl, Method.POST, String.class, requestPayload, ContentType.Html, AcceptType.Json);
            return status.getBoolean("status");
        } catch (Exception ex) {
            throw new RuntimeException("添加图层组异常！", ex);
        }
    }

    /// <summary>
    /// 删除图层组
    /// </summary>
    /// <param name="layerGroup">图层组名称</param>
    /// <param name="workSpace">工作区名称</param>
    public Boolean deleteLayerGroup(String layerGroup, String workSpace) {
        String layerGroupPath = StrUtil.isEmptyIfStr(workSpace) ? "/layergroups/" + layerGroup : "/workspaces/" + workSpace + "/layergroups/" + layerGroup;
        String requestUrl = getRestServiceUrl() + layerGroupPath;
        Object obj = null;
        try {
            JSONObject status = sendRestRequest(requestUrl, Method.DELETE, Object.class, obj, ContentType.Json, AcceptType.Json);
            if (status.getBoolean("status"))
                return true;
            return false;
        } catch (Exception ex) {
            log.error("删除图层组异常", ex);
            throw new RuntimeException("修改图层边界范围异常！", ex);
        }
    }


    /**
     * 修改GeoServer中的图层组
     *
     * @param layerGroup   图层组名称
     * @param targetLayers 目标图层列表
     * @param targetStyles 目标样式列表（可以为空）
     * @param workSpace    工作空间名称（可选）
     * @return 修改是否成功
     */
    public void modifyLayerGroup(String layerGroup, List<String> targetLayers, List<String> targetStyles, String workSpace) throws Exception {

        String layerGroupPath = (workSpace == null || workSpace.isEmpty())
                ? "/layergroups/" + layerGroup
                : "/workspaces/" + workSpace + "/layergroups/" + layerGroup;
        String requestUrl = getRestServiceUrl() + layerGroupPath;

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();

        // 创建图层组元素
        Element layerGroupElem = doc.createElement("layerGroup");
        doc.appendChild(layerGroupElem);

        // 添加名称子元素
        Element nameElem = doc.createElement("name");
        nameElem.appendChild(doc.createTextNode(layerGroup));
        layerGroupElem.appendChild(nameElem);

        // 添加图层层级
        Element layersElem = doc.createElement("layers");
        layerGroupElem.appendChild(layersElem);
        for (String layer : targetLayers) {
            Element layerElem = doc.createElement("layer");
            layerElem.appendChild(doc.createTextNode(layer));
            layersElem.appendChild(layerElem);
        }

        // 添加样式层级（如果存在）
        if (targetStyles != null && !targetStyles.isEmpty()) {
            Element stylesElem = doc.createElement("styles");
            layerGroupElem.appendChild(stylesElem);
            for (String style : targetStyles) {
                Element styleElem = doc.createElement("style");
                styleElem.appendChild(doc.createTextNode(style));
                stylesElem.appendChild(styleElem);
            }
        }

        // 将DOM文档转换为字符串
        String xmlString = convertDocumentToString(doc);

        // 发送PUT请求
        sendRestRequest(requestUrl, Method.PUT, String.class, xmlString, ContentType.Html, AcceptType.Json);


    }


    /// <summary>
    /// 修改图层组
    /// </summary>
    /// <param name="layerGroup">图层组名称</param>
    /// <param name="targetLayers">图层</param>
    /// <param name="workSpace">工作区名称</param>
/*    public Boolean ModifyLayerGroup(String layerGroup, List<String> targetLayers, List<String> targetStyles, String workSpace = null)
    {
        String layerGroupPath = StrUtil.isEmptyIfStr(workSpace) ? "/layergroups/" + layerGroup : "/workspaces/" + workSpace + "/layergroups/" + layerGroup;
        String requestUrl = getRestServiceUrl() + layerGroupPath;

        String status = "";

       Object requestPayload = "<layerGroup><name>" + layerGroup + "</name><layers>[LAYERS]</layers><styles>[STYLES]</styles></layerGroup>";
        StringBuilder layers = new StringBuilder();
        StringBuilder styles = new StringBuilder();
        foreach (String targetLayer in targetLayers)
        {
            layers.Append("<layer>");
            layers.Append(targetLayer);
            layers.Append("</layer>");
        }
        if (targetStyles != null)
        {
            foreach (String targetStyle in targetStyles)
            {
                styles.Append("<style>");
                styles.Append(targetStyle);
                styles.Append("</style>");
            }
        }

        requestPayload = (requestPayload as String).Replace("[LAYERS]", layers.ToString());
        requestPayload = (requestPayload as String).Replace("[STYLES]", styles.ToString());

        try
        {
            status = SendRestRequest(requestUrl, RequestMethod.Put, String), ref requestPayload, ContentType.Html, AcceptType.Json);
            if (status == "OK")
            {
                return true;
            }
            return false;
        }
        catch (Exception ex)
        {
            throw new Exception("修改图层组异常！", ex);
        }
    }*/

    /// <summary>
    /// 获取图层
    /// </summary>
    /// <param name="workSpaceName">工作区名称</param>
    /// <returns></returns>
/*    public List<Layer> GetLayers(String workSpaceName)
    {
        //数据存储列表
        List<DataStore> dataStores = GetDataStores(workSpaceName);
        List<Layer> layers = new ArrayList<Layer>();

        for (DataStore dataStore : dataStores)
        {
            for (Layer layer : GetLayers(workSpaceName, dataStore.Name))
            {
                layers.add(layer);
            }
        }

        return layers;
    }*/

    /// <summary>
    /// 获取图层
    /// </summary>
    /// <param name="workSpaceName">工作区名称</param>
    /// <returns></returns>
/*    public List<String> GetLayerNames(String workSpaceName)
    {
        //数据存储列表
        List<DataStore> dataStores = GetDataStores(workSpaceName);
        IList<String> layers = new List<String>();

        foreach (DataStore dataStore in dataStores)
        {
            foreach (String layer in GetLayerNames(workSpaceName, dataStore.Name))
            {
                layers.Add(layer);
            }
        }

        return layers;
    }

 */
    /// <summary>
    /// 获取图层
    /// </summary>
    /// <param name="workSpace">工作区名称</param>
    /// <param name="dataStore">数据存储名称</param>
    /// <returns></returns>
    public JSONObject getLayers(String workSpace, String dataStore) {
        String requestUrl = getRestServiceUrl() + "/workspaces/" + workSpace + "/datastores/" + dataStore + "/featuretypes";
//        Object layers = null;
//        boolean status = false;
//        Object temp = null;

        try {
            JSONObject js = getLayerInfo(requestUrl);
            if (Func.isNotEmpty(js)) {
                String url = js.getJSONObject("featureTypes").getJSONArray("featureType")
                        .getJSONObject(0).getString("href");
                JSONObject layerInfo = getLayerInfo(url);
                if (layerInfo != null) {
                    return layerInfo;
                }
            }
        } catch (Exception ex) {
            log.error("获取图层异常", ex);
        }
        return null;
    }

    private JSONObject getLayerInfo(String requestUrl) {
        HttpRequest request = HttpUtil.createRequest(Method.GET, requestUrl);
        request.setMethod(Method.GET);
        Map<String, String> userMap = new HashMap<>();
        String encoding = appSettingConfig.getGeoServerUser() + ":" + appSettingConfig.getGeoServerPwd();
        String encCode = "Basic " + Base64.getUrlEncoder().encodeToString(encoding.getBytes());
        userMap.put("Authorization", encCode);
        request.addHeaders(userMap);

        request.contentType(ContentType.Json.toString());
        request.header("Accept", "application/json");
        request.timeout(30 * 60 * 1000);
        request.setReadTimeout(30 * 60 * 1000);

        HttpResponse execute = request.execute();
        String body = execute.body();
        JSONObject js = JSON.parseObject(body);
        execute.close();
        return js;
    }

    /// <summary>
    /// 获取图层
    /// </summary>
    /// <param name="workSpace">工作区名称</param>
    /// <param name="dataStore">数据存储名称</param>
    /// <returns></returns>
/*    public List<String> GetLayerNames(String workSpace, String dataStore)
    {
        String requestUrl = getRestServiceUrl() + "/workspaces/" + workSpace + "/datastores/" + dataStore + "/featuretypes";
       Object layers = null;
        String status = "";
       Object temp = null;

        List<String> list = new List<String>();
        try
        {
            status = SendRestRequest(requestUrl, Method.Get, LayersResponse), ref layers, ContentType.Json, AcceptType.Json);
            if (status == "OK")
            {
                if (layers is LayersResponse)
                {
                    String newRequestUrl = "";
                    foreach (Layer layer in (layers as LayersResponse).Layers)
                    {
                        list.Add(layer.Name);
                    }
                    return list;
                }
            }
            return list;
        }
        catch (Exception ex)
        {
            throw new Exception("获取图层异常！", ex);
        }
    }*/
    /// <summary>
    /// 添加图层
    /// </summary>
    /// <param name="layerName">图层名称</param>
    /// <param name="filedAndType">图层字段名称及类型</param>
    /// <param name="workSpace">工作区名称</param>
    /// <param name="dataStore">数据存储名称</param>
    /// <param name="desiredSrs">编码</param>
 /*   public Boolean AddLayer(String layerName, String title, Dictionary<String, String> filedAndType, String workSpace, String dataStore, String desiredSrs, String bBox = null)
    {
        String requestUrl = getRestServiceUrl() + "/workspaces/" + workSpace + "/datastores/" + dataStore + "/featuretypes";
        String status = "";
        //object newLayer = new LayerRequest(layerName, _SrsLookup[desiredSrs]);
       Object newLayer = "<featureType><name>" + layerName + "</name><nativeName>" + layerName + "</nativeName><title>" + title + "</title>[BBOX]<srs>" + desiredSrs + "</srs><attributes>[Parameters]</attributes></featureType>";
        StringBuilder parameters = new StringBuilder();
        foreach (KeyValuePair<String, String> item in filedAndType)
        {
            parameters.Append("<attribute>");
            parameters.Append("<name>" + item.Key + "</name>");
            parameters.Append("<binding>" + item.Value + "</binding>");
            parameters.Append("</attribute>");
        }
            //region old
        //parameters.Append("<attribute>");
        //parameters.Append("<name>objectid</name>");
        //parameters.Append("<binding>java.lang.Integer</binding>");
        //parameters.Append("</attribute>");
        //parameters.Append("<attribute>");
        //parameters.Append("<name>area</name>");
        //parameters.Append("<binding>java.math.BigDecimal</binding>");
        //parameters.Append("</attribute>");
        //parameters.Append("<attribute>");
        //parameters.Append("<name>perimeter</name>");
        //parameters.Append("<binding>java.math.BigDecimal</binding>");
        //parameters.Append("</attribute>");
        //parameters.Append("<attribute>");
        //parameters.Append("<name>bou12_</name>");
        //parameters.Append("<binding>java.lang.Integer</binding>");
        //parameters.Append("</attribute>");
        //parameters.Append("<attribute>");
        //parameters.Append("<name>bou12_id</name>");
        //parameters.Append("<binding>java.lang.Integer</binding>");
        //parameters.Append("</attribute>");
        //parameters.Append("<attribute>");
        //parameters.Append("<name>code</name>");
        //parameters.Append("<binding>java.lang.Integer</binding>");
        //parameters.Append("</attribute>");
        //parameters.Append("<attribute>");
        //parameters.Append("<name>name</name>");
        //parameters.Append("<binding>java.lang.String</binding>");
        //parameters.Append("</attribute>");
        //parameters.Append("<attribute>");
        //parameters.Append("<name>shape_leng</name>");
        //parameters.Append("<binding>java.math.BigDecimal</binding>");
        //parameters.Append("</attribute>");
        //parameters.Append("<attribute>");
        //parameters.Append("<name>shape_area</name>");
        //parameters.Append("<binding>java.math.BigDecimal</binding>");
        //parameters.Append("</attribute>");
        //parameters.Append("<attribute>");
        //parameters.Append("<name>geom</name>");
        //parameters.Append("<binding>com.vividsolutions.jts.geom.MultiPolygon</binding>");
        //parameters.Append("</attribute>");
            //endregion

            newLayer = (newLayer as String).Replace("[Parameters]", parameters.ToString());
        String bbox = "";
        if (StrUtil.isEmptyIfStr(bBox))
        {
            bbox = String.Format("<nativeBoundingBox><minx>{0}</minx><maxx>{1}</maxx><miny>{2}</miny><maxy>{3}</maxy></nativeBoundingBox><latLonBoundingBox><minx>{0}</minx><maxx>{1}</maxx><miny>{2}</miny><maxy>{3}</maxy><crs>{4}</crs></latLonBoundingBox>", -1, 0, -1, 0, desiredSrs);
        }
        else
        {
            String[] list = bBox.Split(',');
            bbox = String.Format("<nativeBoundingBox><minx>{0}</minx><maxx>{1}</maxx><miny>{2}</miny><maxy>{3}</maxy></nativeBoundingBox><latLonBoundingBox><minx>{0}</minx><maxx>{1}</maxx><miny>{2}</miny><maxy>{3}</maxy><crs>{4}</crs></latLonBoundingBox>", list[0], list[1], list[2], list[3], desiredSrs);
        }
        newLayer = (newLayer as String).Replace("[BBOX]", "");
        try
        {
            //status = SendRestRequest(requestUrl, RequestMethod.Post, LayerRequest), ref newLayer, ContentType.Json, AcceptType.Json);
            status = SendRestRequest(requestUrl, RequestMethod.Post, String), ref newLayer, ContentType.Html, AcceptType.Json);
            if (status == "Created")
            {
                return true;
            }
            return false;
        }
        catch (Exception ex)
        {
            throw new Exception("添加图层异常！", ex);
        }
    }*/
    /// <summary>
    /// 删除图层
    /// </summary>
    /// <param name="workSpace">工作区名称</param>
    /// <param name="dataStore">数据存储名称</param>
    /// <param name="layer">图层名称</param>
    /// <returns></returns>
    public Boolean deleteLayer(String workSpace, String dataStore, String layer) {
        String requestUrl = getRestServiceUrl() + "/workspaces/" + workSpace + "/datastores/" + dataStore + "/featuretypes/" + layer + "?recurse=true";
        Object request = null;

        String status = "";

        try {
            status = String.valueOf(sendRestRequest(requestUrl, Method.DELETE, Object.class, request, ContentType.Json, AcceptType.Json));
            return Objects.equals(status, "OK");
        } catch (Exception ex) {
            throw new RuntimeException("删除图层异常！", ex);
        }
    }

    /**
     * 删除图层
     *
     * @param layer 图层名称
     * @return true：删除成功，false：删除失败
     */
    public boolean deleteLayer(String layer) {
        String geoWorkSpace = appSettingConfig.getGeoWorkspace();
        String geoDataStore = appSettingConfig.getGeoDatastore();
        try {
            if (isExistLayer(layer, geoWorkSpace, geoDataStore)) {
                return deleteLayer(geoWorkSpace, geoDataStore, layer);
            }
        } catch (Exception ex) {
            return false;
        }
        return true;
    }
    /// <summary>
    /// 修改图层样式
    /// </summary>
    /// <param name="layerName">图层名称</param>
    /// <param name="styles">样式</param>
    /// <returns></returns>
/*    public Boolean ModifyLayerStyle(String layerName, List<String> styles, String defaultStyle = null)
    {
        String requestUrl = getRestServiceUrl() + "/layers/" + layerName;
        String status = "";
       Object newLayer = "<layer><name>" + layerName + "</name><type>VECTOR</type>[DEFAULTSTYLE]<styles class=\"linked-hash-set\">[STYLES]</styles><resource class=\"featureType\"><name>" + layerName + "</name></resource></layer>";
        StringBuilder parameters = new StringBuilder();
        foreach (String item in styles)
        {
            parameters.Append("<style>");
            parameters.Append("<name>" + item + "</name>");
            parameters.Append("</style>");
        }

        newLayer = (newLayer as String).Replace("[STYLES]", parameters.ToString());
        if (!StrUtil.isEmptyIfStr(defaultStyle))
        {
            newLayer = (newLayer as String).Replace("[DEFAULTSTYLE]", "<defaultStyle><name>" + defaultStyle + "</name></defaultStyle>");
        }
        else
        {
            newLayer = (newLayer as String).Replace("[DEFAULTSTYLE]", "");
        }
        try
        {
            status = SendRestRequest(requestUrl, RequestMethod.Put, String), ref newLayer, ContentType.Html, AcceptType.Json);
            if (status == "OK")
            {
                return true;
            }
            return false;
        }
        catch (Exception ex)
        {
            throw new Exception("修改图层样式异常！", ex);
        }
    }*/

    /// <summary>
    /// 修改图层边界范围
    /// </summary>
    /// <param name="layerName">图层名称</param>
    /// <param name="workSpace">工作区名称</param>
    /// <param name="dataStore">数据存储名称</param>
    /// <param name="desiredSrs">参考坐标系</param>
    /// <param name="bBox">边界范围（格式：minX,maxX,minY,maxY）</param>
    /// <returns></returns>

    public boolean modifyLayerBBox(String layerName, String title, String bBox) {
        modifyLayerBBox(layerName, title, appSettingConfig.getGeoWorkspace(), appSettingConfig.getGeoDatastore(), appSettingConfig.getEpsg(), bBox);
        return true;
    }

    public boolean modifyLayerBBox(String layerName, String title, String workSpace, String dataStore, String desiredSrs, String bBox) {
        String requestUrl = getRestServiceUrl() + "/workspaces/" + workSpace + "/datastores/" + dataStore + "/featuretypes/" + layerName;
        String layerXml = "<featureType><name>" + layerName + "</name><nativeName>" + layerName + "</nativeName><title>" + title + "</title>[BBOX]<srs>" + desiredSrs + "</srs></featureType>";
        String bboxXml = "";

        if (bBox == null || bBox.isEmpty()) {
            bboxXml = String.format("<nativeBoundingBox><minx>-1</minx><maxx>0</maxx><miny>-1</miny><maxy>0</maxy></nativeBoundingBox><latLonBoundingBox><minx>-1</minx><maxx>0</maxx><miny>-1</miny><maxy>0</maxy><crs>%s</crs></latLonBoundingBox>", desiredSrs);
        } else {
            //minx miny maxx maxy
            String[] list = bBox.split(",");
            bboxXml = String.format("<nativeBoundingBox>" +
                            "<minx>%s</minx>" +
                            "<maxx>%s</maxx>" +
                            "<miny>%s</miny>" +
                            "<maxy>%s</maxy>" +
                            "</nativeBoundingBox><latLonBoundingBox>" +
                            "<minx>%s</minx>" +
                            "<maxx>%s</maxx>" +
                            "<miny>%s</miny>" +
                            "<maxy>%s</maxy>" +
                            "<crs>%s</crs></latLonBoundingBox>",
                    list[0], list[2], list[1], list[3], list[0], list[2], list[1], list[3], desiredSrs);
        }
        layerXml += "";
        layerXml = layerXml.replace("[BBOX]", bboxXml);

        log.info("修改图层请求路径:{}  layerXml:{}", requestUrl, layerXml);
        try {
            JSONObject status = sendRestRequest(requestUrl, Method.PUT, WorkSpacesResponse.class, layerXml, ContentType.Html, AcceptType.Json);
            log.info("修改图层响应:{}", status);
            return status.getBoolean("status");
        } catch (Exception ex) {
            throw new RuntimeException("修改图层边界范围异常！", ex);
        }
    }
    //endregion

    //region 样式
    /// <summary>
    /// 样式是否存在
    /// </summary>
    /// <param name="workSpace">工作区名称</param>
    /// <param name="styleName">样式名称</param>
    /// <returns></returns>
/*    public Boolean IsExsitStyle(String styleName, String workSpace = null)
    {
        var list = GetStyles(workSpace);
        foreach (var item in list)
        {
            if (item.Name == styleName)
                return true;
        }
        return false;
    }*/

    /**
     * 从GeoServer获取样式列表
     *
     * @param workSpace 工作空间名称（可选）
     * @return 样式流
     */
    public List<Style> getStyles(String workSpace) {

        String stylePath = (workSpace == null || workSpace.isEmpty())
                ? "/styles"
                : "/workspaces/" + workSpace + "/styles";
        String requestUrl = getRestServiceUrl() + stylePath;
        StylesResponse styles = new StylesResponse();
        try {
            JSONObject object = sendRestRequest(requestUrl, Method.GET, String.class, styles, ContentType.Json, AcceptType.Json);
            if (Func.isNotEmpty(object) && object.getBoolean("status")) {
                JSONObject jsonObject = object.getJSONObject("payLoad").getJSONObject("styles");
                if (Func.isNotEmpty(jsonObject)) {
                    List<Style> javaList = jsonObject.getJSONArray("style").toJavaList(Style.class);
                    return javaList;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("获取样式异常！");
        }

        return styles.getStyles();

    }

    /**
     * 修改GeoServer中的样式
     *
     * @param styleName 样式名称
     * @param sldPath   SLD文件绝对路径
     * @param workSpace 工作空间名称（可选）
     * @return 修改是否成功
     */
    public void modifyStyle(String styleName, String sldPath, String workSpace) {
        try {
            String stylePath = (workSpace == null || workSpace.isEmpty())
                    ? "/styles/" + styleName
                    : "/workspaces/" + workSpace + "/styles/" + styleName;
            String requestUrl = getRestServiceUrl() + stylePath;

//            File sldFile = new File(sldPath);
//            if (!sldFile.exists() || !sldFile.isFile()) {
//                throw new IllegalArgumentException("SLD文件不存在或不是有效的文件");
//            }

//            byte[] sldContent = Files.readAllBytes(sldFile.toPath());

            sendRestRequest(requestUrl, Method.PUT, String.class, sldPath, ContentType.SldFile, AcceptType.Json);

        } catch (Exception e) {
            throw new RuntimeException("修改样式异常！", e);
        }
    }

    /**
     * 添加新样式到GeoServer
     *
     * @param styleName 样式名称
     * @param sldName   SLD文件名
     * @param sldPath   SLD文件绝对路径
     * @param workSpace 工作空间名称（可选）
     * @return 是否成功添加样式
     */
    public void addStyle(String styleName, String sldName, String sldPath, String workSpace) {

        // 构建样式路径
        String stylePath = (workSpace == null || workSpace.isEmpty())
                ? "/styles"
                : "/workspaces/" + workSpace + "/styles";
        String requestUrl = getRestServiceUrl() + stylePath;

        // 创建样式 - 发送POST请求
        String createStylePayload = "<style><name>" + styleName + "</name><filename>" + sldName + "</filename></style>";
        JSONObject object = sendRestRequest(requestUrl, Method.POST, String.class, createStylePayload, ContentType.Html, AcceptType.Json);

        if ("true".equalsIgnoreCase(object.get("status").toString())) {
            // 更新样式 - 发送PUT请求（样式名可能含中文，须 URL 编码后拼接）
            requestUrl += "/" + java.net.URLEncoder.encode(styleName, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
            File sldFile = new File(sldPath);
            if (!sldFile.exists() || !sldFile.isFile()) {
                throw new IllegalArgumentException("SLD文件不存在或不是有效的文件");
            }

            JSONObject updataRet = sendRestRequest(requestUrl, Method.PUT, String.class, sldPath, ContentType.SldFile, AcceptType.Json);

            if (!"true".equalsIgnoreCase(updataRet.get("status").toString())) {
                throw new RuntimeException("添加样式异常！");
            }
        }

    }

    /// <summary>
    /// 获取样式
    /// </summary>
    /// <param name="workSpace">工作区名称</param>
    /// <returns></returns>
   /* public List<Style> GetStyles(String workSpace ) throws Exception {
        workSpace=null;
        String stylePath = StrUtil.isEmptyIfStr(workSpace) ? "/styles" : "/workspaces/" + workSpace + "/styles";
        String requestUrl = getRestServiceUrl() + stylePath;
       StylesResponse styles = null;
        String status = "";

        try
        {
            status = SendRestRequest(requestUrl, Method.GET, StylesResponse,  styles, ContentType.Json, AcceptType.Json);
        }
        catch (Exception ex)
        {
            throw new Exception("获取样式异常！", ex);
        }

        if (styles instanceof StylesResponse)
        {
            for (Style style : styles.Styles)
            {

            }
            return styles.Styles;
        }
    }*/
    /// <summary>
    /// 添加样式
    /// </summary>
    /// <param name="styleName">样式名称</param>
    /// <param name="sldName">样式文件名称</param>
    /// <param name="sldPath">样式文件绝对路径</param>
    /// <param name="workSpace">工作区名称</param>
/*    public Boolean AddStyle(String styleName, String sldName, String sldPath, String workSpace = null)
    {
        String stylePath = StrUtil.isEmptyIfStr(workSpace) ? "/styles" : "/workspaces/" + workSpace + "/styles";
        String requestUrl = getRestServiceUrl() + stylePath;
        String status = "";
       Object requestPayload = "<style><name>" + styleName + "</name><filename>" + sldName + "</filename></style>";

        try
        {
            status = SendRestRequest(requestUrl, RequestMethod.Post, String), ref requestPayload, ContentType.Html, AcceptType.Json);
            if (status == "Created")
            {
                requestUrl += "/" + styleName;
                requestPayload = sldPath;

                status = SendRestRequest(requestUrl, RequestMethod.Put, String), ref requestPayload, ContentType.SldFile, AcceptType.Json);
                if (status == "OK")
                {
                    return true;
                }
            }
            return false;
        }
        catch (Exception ex)
        {
            throw new Exception("添加样式异常！", ex);
        }
    }*/

    /// <summary>
    /// 修改样式
    /// </summary>
    /// <param name="styleName">样式名称</param>
    /// <param name="sldPath">样式文件绝对路径</param>
    /// <param name="workSpace">工作区名称</param>
/*    public Boolean ModifyStyle(String styleName, String sldPath, String workSpace = null)
    {
        String stylePath = StrUtil.isEmptyIfStr(workSpace) ? "/styles" : "/workspaces/" + workSpace + "/styles";
        String requestUrl = getRestServiceUrl() + stylePath + "/" + styleName;
        String status = "";
       Object requestPayload = sldPath;

        try
        {
            status = SendRestRequest(requestUrl, RequestMethod.Put, String), ref requestPayload, ContentType.SldFile, AcceptType.Json);
            if (status == "OK")
            {
                return true;
            }
            return false;
        }
        catch (Exception ex)
        {
            throw new Exception("修改样式异常！", ex);
        }
    }*/

    // <summary>
    // 删除样式
    // </summary>
    // <param name="style">样式名称</param>
    // <param name="workSpace">工作区名称</param>
    public Boolean DeleteStyle(String style, String workSpace) {
        String stylePath = Func.isEmpty(workSpace) ? "/styles/" + style : "/workspaces/" + workSpace + "/styles/" + style;
        String requestUrl = getRestServiceUrl() + stylePath;
        Object obj = null;

        try {
            JSONObject object = sendRestRequest(requestUrl, Method.DELETE, Object.class, obj, ContentType.Json, AcceptType.Json);
            return object.getBoolean("status");
        } catch (Exception ex) {
            log.info("删除样式异常！");
            return false;
        }
    }


    //region 切片
    /// <summary>
    /// 图层绑定切片规则
    /// </summary>
    /// <param name="layerName">图层名称（工作区:图层名称）</param>
    /// <param name="gridSetNames">切片规则</param>
    /// <returns></returns>
/*    public Boolean BindLayerGridSet(String layerName, List<String> gridSetNames)
    {
        String requestUrl = BaseServiceUrl + "/gwc/rest/layers/" + layerName + ".xml";
        String status = "";
       Object newGridSet = "<GeoServerLayer><enabled>true</enabled><name>" + layerName + "</name><mimeFormats><String>image/png</String><String>image/jpeg</String></mimeFormats><gridSubsets>[GRIDSET]</gridSubsets><metaWidthHeight><int>4</int><int>4</int></metaWidthHeight><expireCache>0</expireCache><expireClients>0</expireClients><parameterFilters><styleParameterFilter><key>STYLES</key><defaultValue/></styleParameterFilter></parameterFilters><gutter>0</gutter></GeoServerLayer>";

        StringBuilder gridSets = new StringBuilder();

        if (gridSetNames == null || gridSetNames.Count() == 0)
        {
            return false;
        }
        else
        {
            foreach (String gridSetName in gridSetNames)
            {
                gridSets.Append("<gridSubset><gridSetName>");
                gridSets.Append(gridSetName);
                gridSets.Append("</gridSetName></gridSubset>");
            }
        }
        newGridSet = (newGridSet as String).Replace("[GRIDSET]", gridSets.ToString());
        try
        {
            status = SendRestRequest(requestUrl, RequestMethod.Post, String), ref newGridSet, ContentType.Html, AcceptType.Json);
            if (status == "OK")
            {
                return true;
            }
            return false;
        }
        catch (Exception ex)
        {
            throw new Exception("绑定图层切片规则异常！", ex);
        }
    }*/

    /// <summary>
    /// 构造 GeoServer Basic 认证头
    /// </summary>
    private String getBasicAuth() {
        String encoding = appSettingConfig.getGeoServerUser() + ":" + appSettingConfig.getGeoServerPwd();
        return "Basic " + Base64.getEncoder().encodeToString(encoding.getBytes(StandardCharsets.UTF_8));
    }

    /// <summary>
    /// 发送带 Basic 认证的 GET 请求，返回响应体字符串（用于 GWC REST 查询）
    /// </summary>
    private String httpGetString(String requestUrl, String accept) {
        String encCode = getBasicAuth();
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet httpGet = new HttpGet(requestUrl);
            httpGet.setHeader("Authorization", encCode);
            httpGet.setHeader("Accept", accept);
            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == 200) {
                    return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                }
                throw new RuntimeException("请求GeoServer失败: HTTP " + statusCode + ", " + requestUrl);
            }
        } catch (IOException e) {
            throw new RuntimeException("请求GeoServer异常: " + requestUrl, e);
        }
    }

    /// <summary>
    /// 获取图层所有绑定的切片规则
    /// </summary>
    /// <param name="layerName">图层名称（工作区:图层名称）</param>
    public List<String> getLayerGridSet(String layerName) {
        String requestUrl = getBaseUrl() + "/gwc/rest/layers/" + layerName + ".xml";

        String gridSetResponse;
        try {
            gridSetResponse = httpGetString(requestUrl, "application/xml");
        } catch (Exception ex) {
            throw new RuntimeException("获取图层切片规则异常！", ex);
        }
        if (Func.isBlank(gridSetResponse)) {
            return new ArrayList<>();
        }
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            InputSource is = new InputSource(new StringReader(gridSetResponse));
            Document doc = dBuilder.parse(is);

            NodeList nodeList = doc.getElementsByTagName("gridSubset");
            List<String> gridSets = new ArrayList<>(nodeList.getLength());
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                if (!node.hasChildNodes()) {
                    continue;
                }
                NodeList children = node.getChildNodes();
                for (int j = 0; j < children.getLength(); j++) {
                    Node child = children.item(j);
                    if ("gridSetName".equals(child.getNodeName()) && child.getFirstChild() != null) {
                        gridSets.add(child.getFirstChild().getNodeValue().trim());
                    }
                }
            }
            return gridSets;
        } catch (Exception e) {
            throw new RuntimeException("解析图层切片规则异常！", e);
        }
    }

    /// <summary>
    /// 获取图层所有的切片任务编号
    /// </summary>
    /// <param name="layerName">图层名称（工作区:图层名称）</param>
    /// <returns></returns>
    public List<String> getAllTilesTask(String layerName) {
        List<long[]> tasks = getTileTasks(layerName);
        List<String> taskIds = new ArrayList<>(tasks.size());
        for (long[] row : tasks) {
            taskIds.add(row.length > 3 ? String.valueOf(row[3]) : "-");
        }
        return taskIds;
    }

    /// <summary>
    /// 获取图层当前所有切片任务（运行中与挂起的 seed/truncate/reseed 任务）
    /// GWC 返回 {"long-array-array":[[...],...]}，每行含义：
    /// [tiles processed, total tiles, est. remaining seconds, Task ID, Task status]
    /// Task status: -1=ABORTED, 0=PENDING, 1=RUNNING, 2=DONE
    /// </summary>
    /// <param name="layerName">图层名称（工作区:图层名称）</param>
    /// <returns>每行原始 long 数组；无活动任务时返回空列表</returns>
    public List<long[]> getTileTasks(String layerName) {
        String requestUrl = getBaseUrl() + "/gwc/rest/seed/" + layerName + ".json";
        try {
            String payload = httpGetString(requestUrl, "application/json");
            if (Func.isBlank(payload)) {
                return new ArrayList<>();
            }
            ObjectMapper mapper = new ObjectMapper();
            JsonNode array = mapper.readTree(payload).path("long-array-array");
            List<long[]> tasks = new ArrayList<>();
            for (JsonNode row : array) {
                long[] values = new long[row.size()];
                for (int i = 0; i < row.size(); i++) {
                    values[i] = row.get(i).asLong();
                }
                tasks.add(values);
            }
            return tasks;
        } catch (IOException e) {
            throw new RuntimeException("查询切片任务异常！", e);
        }
    }


    /// <summary>
    /// 发送重新切片任务
    /// </summary>
    /// <param name="layerName">图层名称（工作区:图层名称）</param>
    /// <param name="gridSetName">切片规则</param>
    /// <param name="zoomStart">切片开始等级</param>
    /// <param name="zoomStop">切片结束等级</param>
    /// <param name="threadCount">任务进程数量</param>
    /// <param name="bBox">切片范围（格式：minX,minY,maxX,maxY）</param>
    /// <returns></returns>
    public boolean sendAllTilesTask(String layerName, String gridSetName, int zoomStart, int zoomStop, int threadCount, String bBox) {
        String requestUrl = getBaseUrl() + "/gwc/rest/seed/" + layerName + ".xml";
        String status = "";
        String newTask = "<seedRequest><name>" + layerName + "</name><gridSetId>" + gridSetName + "</gridSetId><zoomStart>" + zoomStart + "</zoomStart><zoomStop>" + zoomStop + "</zoomStop><type>reseed</type><threadCount>" + threadCount + "</threadCount>[BBOX]</seedRequest>";

        String strBBox = "";
        if (bBox != null && !bBox.trim().isEmpty()) {
            String[] arrBBox = bBox.split(",");
            if (arrBBox.length == 4) {
                strBBox = String.format("<bounds><coords><double>%s</double><double>%s</double><double>%s</double><double>%s</double></coords></bounds>", arrBBox[0], arrBBox[1], arrBBox[2], arrBBox[3]);
            }
        }
        newTask = newTask.replace("[BBOX]", strBBox);
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(requestUrl);
            httpPost.setHeader("Content-Type", "application/xml");
            httpPost.setHeader("Authorization", getBasicAuth());
            httpPost.setEntity(new StringEntity(newTask));

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == 200) {
                    status = EntityUtils.toString(response.getEntity());
                    return "OK".equals(status);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("发送重新切片任务异常！", e);
        }
        return false;
    }

    /// <summary>
    /// 发送缺失切片任务
    /// </summary>
    /// <param name="layerName">图层名称（工作区:图层名称）</param>
    /// <param name="gridSetName">切片规则</param>
    /// <param name="zoomStart">切片开始等级</param>
    /// <param name="zoomStop">切片结束等级</param>
    /// <param name="threadCount">任务进程数量</param>
    /// <param name="bBox">切片范围（格式：minX,minY,maxX,maxY）</param>
    /// <returns></returns>
/*    public Boolean SendMissTilesTask(String layerName, String gridSetName, int zoomStart, int zoomStop, int threadCount = 1, String bBox = null)
    {
        String requestUrl = BaseServiceUrl + "/gwc/rest/seed/" + layerName + ".xml";
        String status = "";
       Object newTask = "<seedRequest><name>" + layerName + "</name>[BBOX]<gridSetId>" + gridSetName + "</gridSetId><zoomStart>" + zoomStart + "</zoomStart><zoomStop>" + zoomStop + "</zoomStop><type>seed</type><threadCount>" + threadCount + "</threadCount></seedRequest>";
        String strBBox = "";
        if (!StrUtil.isEmptyIfStr(bBox))
        {
            String[] arrBBox = bBox.Split(',');
            if (arrBBox.Count() == 4)
            {
                strBBox = String.Format("<bounds><coords><double>{0}</double><double>{1}</double><double>{2}</double><double>{3}</double></coords></bounds>", arrBBox[0], arrBBox[1], arrBBox[2], arrBBox[3]);
            }
        }
        newTask = (newTask as String).Replace("[BBOX]", strBBox);
        try
        {
            status = SendRestRequest(requestUrl, RequestMethod.Post, String), ref newTask, ContentType.Html, AcceptType.Json);
            if (status == "OK")
            {
                return true;
            }
            return false;
        }
        catch (Exception ex)
        {
            throw new Exception("发送缺失切片任务异常！", ex);
        }
    }*/

    /// <summary>
    /// 发送清空切片任务
    /// </summary>
    /// <param name="layerName">图层名称（工作区:图层名称）</param>
    /// <param name="gridSetName">切片规则</param>
    /// <param name="zoomStart">切片开始等级</param>
    /// <param name="zoomStop">切片结束等级</param>
    /// <param name="threadCount">任务进程数量</param>
    /// <param name="bBox">切片范围（格式：minX,minY,maxX,maxY）</param>
    /// <returns></returns>
    public boolean sendEmptyTilesTask(String layerName, String gridSetName, int zoomStart, int zoomStop, int threadCount, String bBox) {
        String requestUrl = getBaseUrl() + "/gwc/rest/seed/" + layerName + ".xml";
        String status = "";
        String newTask = "<seedRequest><name>" + layerName + "</name><gridSetId>" + gridSetName + "</gridSetId><zoomStart>" + zoomStart + "</zoomStart><zoomStop>" + zoomStop + "</zoomStop><type>truncate</type><threadCount>" + threadCount + "</threadCount>[BBOX]</seedRequest>";

        String strBBox = "";
        if (bBox != null && !bBox.trim().isEmpty()) {
            String[] arrBBox = bBox.split(",");
            if (arrBBox.length == 4) {
                strBBox = String.format("<bounds><coords><double>%s</double><double>%s</double><double>%s</double><double>%s</double></coords></bounds>", arrBBox[0], arrBBox[1], arrBBox[2], arrBBox[3]);
            }
        }
        newTask = newTask.replace("[BBOX]", strBBox);

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(requestUrl);
            httpPost.setHeader("Content-Type", "application/xml");
            httpPost.setHeader("Authorization", getBasicAuth());
            httpPost.setEntity(new StringEntity(newTask));

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == 200) {
                    status = EntityUtils.toString(response.getEntity());
                    return "OK".equals(status);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("发送清空切片任务异常！", e);
        }

        return false;
    }

    /// <summary>
    /// 结束切片任务
    /// </summary>
    /// <param name="layerName">图层名称（工作区:图层名称）</param>
    /// <param name="taskState">任务状态</param>
    /// <returns></returns>
    public Boolean sendTerminatingTask(String layerName, TaskState taskState) {
        String requestUrl = getBaseUrl() + "/gwc/rest/seed/" + layerName;
        Object newTask = String.format("kill_all=%s", taskStateLookup.get(taskState));
        try {
            JSONObject status = sendRestRequest(requestUrl, Method.POST, String.class, newTask, ContentType.Json, AcceptType.Json);
            return status.getBoolean("status");
        } catch (Exception ex) {
            throw new RuntimeException("结束切片任务异常！", ex);
        }
    }

    /**
     * 结束所有任务
     *
     * @param mapName 地图名称
     * @return 是否成功
     */
    public boolean terminatingTask(String mapName) {
        if (isExistTilesTask(mapName)) {
            mapName = appSettingConfig.getGeoWorkspace() + ":" + mapName;
            return sendTerminatingTask(mapName, TaskState.All);
        }
        return true;
    }

    /**
     * 图层绑定切片规则
     *
     * @param mapName 图层名称（工作区:图层名称）
     * @param bbox    边界框
     * @return 操作结果
     */
    public boolean tileMap(String mapName, String bbox) {
        mapName = appSettingConfig.getGeoWorkspace() + ":" + mapName;
        List<String> list = getLayerGridSet(mapName);
        String _gridSetName = appSettingConfig.getGridSetName();
        int start = 0;
        int stop = 14;
        try {

            for (String l : list) {
                if (_gridSetName != null && !_gridSetName.isEmpty()) {
                    String[] listgrid = _gridSetName.split(",");
                    if (Arrays.asList(listgrid).contains(l)) {
                        sendEmptyTilesTask(mapName, l, start, stop, 1, bbox);
                        sendAllTilesTask(mapName, l, start, stop, 1, bbox);
                    }
                } else {
                    sendEmptyTilesTask(mapName, l, start, stop, 1, bbox);
                    sendAllTilesTask(mapName, l, start, stop, 1, bbox);
                }
            }
        } catch (NumberFormatException e) {
            // 处理转换异常
        }
        return false;
    }

    /**
     * 检查地图是否有切片任务
     *
     * @param mapName 地图名称
     * @return 如果存在切片任务返回true，否则返回false
     */
    public boolean isExistTilesTask(String mapName) {
        mapName = appSettingConfig.getGeoWorkspace() + ":" + mapName;
        List<String> list = getAllTilesTask(mapName);
        return list != null && !list.isEmpty();
    }

    //region WMTS
    /// <summary>
    /// 获取WMTS图片
    /// </summary>
    /// <param name="requestUrl"></param>
    /// <returns></returns>
/*    public byte[] GetWMTSData(String requestUrl)
    {
       Object payload = null;
        GetRestResponse(requestUrl, RequestMethod.Get, String), ref payload, ContentType.Html, AcceptType.Byte);
        if (payload != null)
        {
            return payload as byte[];
        }
        return null;
    }*/
    //endregion

    /*  public String BaseServiceUrl
        {
            get
            {
                return GeoServerConstants.BaseUrl.Replace(GeoServerConstants.ServerIp, ServerIP).Replace(GeoServerConstants.ServerPort, ServerPort);
            }
        }
    */
    /// <summary>
    /// 获取Rest服务地址
    /// </summary>
    public String getBaseUrl() {
        return "http://" + appSettingConfig.getGeoServerIP() + ":" + appSettingConfig.getGeoServerPort() + "/geoserver";
    }

    public String getRestServiceUrl() {
        return "http://" + appSettingConfig.getGeoServerIP() + ":" + appSettingConfig.getGeoServerPort() + "/geoserver/rest";
    }

//	public String getRestServiceUrl() {
//		return GeoServerContants.RestUrl.replace(GeoServerContants.ServerIp, ServerIP).replace(GeoServerContants.ServerPort, ServerPort);
//	}

    /*    public String getRestServiceUrl()
    {
        get
        {
            return GeoServerConstants.RestUrl.Replace(GeoServerConstants.ServerIp, ServerIP).Replace(GeoServerConstants.ServerPort, ServerPort);
        }
    }*/
    /// <summary>
    /// 验证GeoServer端口
    /// </summary>
    public Boolean IsValid;

    public Boolean getIsValid() {
        return true;
    }
    /*    public Boolean IsValid
    {
        get
        {
            //IPGlobalProperties ipGlobal = IPGlobalProperties.GetIPGlobalProperties();
            //IPEndPoint[] tcpCon = ipGlobal.GetActiveTcpListeners();

            //foreach (IPEndPoint ipEnd in tcpCon)
            //{
            //    if (ipEnd.Port.ToString() == ServerPort)
            //    {
            //        return true;
            //    }
            //}
            return true;
        }
    }*/
    /// <summary>
    /// JSON转字符串
    /// </summary>
/*    public String GetJsonString(Object objToConvert)
    {
        String retVal = "";
        if (objToConvert != null)
        {
            Type objType = objToConvert.GetType();
            DataContractJsonSerializer serializer = new DataContractJsonSerializer(objType);

            using (MemoryStream ms = new MemoryStream())
            {
                serializer.WriteObject(ms, objToConvert);
                ms.Position = 0;
                using (StreamReader sr = new StreamReader(ms))
                {
                    retVal = sr.ReadToEnd();
                }
            }
        }
        return retVal;
    }*/

  /*  public String XmlSerialize<T>(T obj)
    {
        String xmlString = "";
        XmlSerializer xmlSerializer = new XmlSerializer(T));
        using (MemoryStream ms = new MemoryStream())
        {
            xmlSerializer.Serialize(ms, obj);
            xmlString = Encoding.UTF8.GetString(ms.ToArray());
        }
        return xmlString;
    }*/

    /// <summary>
    /// XML String 反序列化成对象
    /// </summary>
/*    public T XmlDeserialize<T>(String xmlString)
    {
        T t = default(T);
        XmlSerializer xmlSerializer = new XmlSerializer(T));
        using (Stream xmlStream = new MemoryStream(Encoding.UTF8.GetBytes(xmlString)))
    {
        using (XmlReader xmlReader = XmlReader.Create(xmlStream))
        {
           Object obj = xmlSerializer.Deserialize(xmlReader);
            t = (T)obj;
        }
    }
    return t;
    }*/
    //endregion

    //region 私有方法
    /// <summary>
    /// 发送请求
    /// </summary>
    /// <param name="requestUrl">请求地址</param>
    /// <param name="method">请求方式</param>
    /// <param name="objectType">请求类型</param>
    /// <param name="payload">返回数据</param>
    /// <param name="contentType">返回类型</param>
    /// <param name="acceptType">接收类型</param>
    /// <returns></returns>
    public JSONObject sendRestRequest(String requestUrl, Method method, Type classType, Object payLoad, ContentType contentType, AcceptType acceptType) {
        boolean statusCode = false;
        if (getIsValid()) {
            HttpRequest request = HttpUtil.createRequest(method, requestUrl);
            request.setMethod(method);
            Map<String, String> userMap = new HashMap<>();
            /*    userMap.put("UserName",GEOSERVER_USERNAME);
                userMap.put("Password",GEOSERVER_PASSWORD);*/
//            ""admin:geoserver"
            String encoding = appSettingConfig.getGeoServerUser() + ":" + appSettingConfig.getGeoServerPwd();
            String encCode = "Basic " + Base64.getUrlEncoder().encodeToString(encoding.getBytes());
            userMap.put("Authorization", encCode);
            request.addHeaders(userMap);
           /*     String url = String.format(request.getUrl());
                request.setUrl(url);*/

            request.contentType(contentType.toString());
            if (acceptType.toString().equals("Json")) {
                request.header("Accept", "application/json");
            }
            request.timeout(30 * 60 * 1000);
            request.setReadTimeout(30 * 60 * 1000);

            boolean b = method.toString().equals("POST") || method.toString().equals("DELETE") || method.toString().equals("PUT");
            if (contentType.equals(ContentType.Json)) {
                if (method == Method.GET) {
                    HttpResponse execute = request.execute();
                    String body = execute.body();
                    payLoad = JSONObject.parseObject(body, classType);
                    statusCode = execute.isOk();
                    execute.close();
                } else if (b) {
                    HttpResponse execute = request.execute();
                    String body = execute.body();
                    log.info("body:" + body);
//                    payLoad = JSONObject.parseObject(execute.body(), classType);
                    statusCode = execute.isOk();
                    log.info("statusCode:" + statusCode);
                    execute.close();
                }

            } else if (contentType == ContentType.Xml) {
                request.contentType("text/xml");
                if (method == Method.GET) {

                }
                if (b) {

                }
            } else if (contentType == ContentType.SldFile || contentType == ContentType.Zip) {
                if (method == Method.PUT) {
                    try {
                        File file = new File(payLoad.toString());
                        if (!file.exists() || !file.isFile()) {
                            throw new IllegalArgumentException("文件不存在: " + payLoad.toString());
                        }

                        URL url = new URL(requestUrl);
                        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                        // 设置请求方法（通常是 PUT 或 POST）
                        connection.setRequestMethod("PUT"); // 或 "POST"
                        connection.setDoOutput(true);       // 允许写入请求体
                        connection.setChunkedStreamingMode(0); // 启用分块流式传输（关键！）
                        connection.setConnectTimeout(30 * 60 * 1000); // 30分钟连接超时
                        connection.setReadTimeout(30 * 60 * 1000);     // 30分钟读取超时

                        // 可选：设置 Content-Type（GeoServer 上传 ZIP 需要 application/zip）
                        if (contentType == ContentType.Zip) {
                            connection.setRequestProperty("Content-Type", GeoServerConstants.ContentType.Zip.getValue());
                        } else if (contentType == ContentType.SldFile) {
                            connection.setRequestProperty("Content-Type", GeoServerConstants.ContentType.SldFile.getValue());
                        }

                        connection.setRequestProperty("Authorization", encCode);

                        // 流式写入文件（核心逻辑）
                        try (OutputStream reqStream = connection.getOutputStream();
                             FileInputStream fileStream = new FileInputStream(file)) {

                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = fileStream.read(buffer)) != -1) {
                                reqStream.write(buffer, 0, bytesRead);
                            }
                        }

                        // 获取响应
                        int sCode = connection.getResponseCode();
                        String statusMessage = connection.getResponseMessage();
                        log.info("HTTP {} {}", sCode, statusMessage);
                        if (sCode == 200 || ("Created").equals(statusMessage)) {
                            statusCode = true;
                        }

//                        // 读取响应体（可选）
//                        try (InputStream responseStream = connection.getInputStream()) {
//                            String responseBody = new String(responseStream.readAllBytes(), StandardCharsets.UTF_8);
//                            log.info("Response: {}", responseBody);
//                        } catch (IOException e) {
//                            // 如果状态码 >= 400，getInputStream() 会抛异常，需读 errorStream
//                            try (InputStream errorStream = connection.getErrorStream()) {
//                                if (errorStream != null) {
//                                    String errorBody = new String(errorStream.readAllBytes(), StandardCharsets.UTF_8);
//                                    log.error("Error Response: {}", errorBody);
//                                }
//                            }
//                        } finally {
//                            connection.disconnect();
//                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            } else {
                request.contentType("text/xml");
                if (method.toString().equals("GET")) {
                    /* request.header("Accept",acceptType.toString());*/

                    HttpResponse response = request.execute();
                    statusCode = response.isOk();
                    /* response.header("Content-Type", "application/json");*/
                    String body = response.body();
                    payLoad = body;
                } else if (b) {
                    request.header("Accept", "");
                    if (payLoad instanceof String) {
                        byte[] bytes = ((String) payLoad).getBytes(StandardCharsets.UTF_8);
                        request.body(bytes);
                    } else {
                        throw new RuntimeException("请求数据类型错误！期望 String，实际类型: " +
                                (payLoad == null ? "null" : payLoad.getClass().getName()));
                    }
                    request.addHeaders(userMap);
                    /*  String post = HttpUtil.post(requestUrl, String.valueOf(userMap));*/
                    HttpResponse execute = request.execute();
                    int status = execute.getStatus();
                    statusCode = execute.isOk();
                    String body = execute.body();
                    log.info("body:{}", body);
                    execute.close();

                }
            }
        }
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("status", statusCode);
        jsonObject.put("payLoad", payLoad);
//        log.info("geoserver响应信息:{}", jsonObject);
        return jsonObject;
    }

    /// <summary>
    /// 发送请求
    /// </summary>
    /// <param name="requestUrl">请求地址</param>
    /// <param name="method">请求方式</param>
    /// <param name="objectType">请求类型</param>
    /// <param name="payload">返回数据</param>
    /// <param name="contentType">返回类型</param>
    /// <param name="acceptType">接收类型</param>
    /// <returns></returns>
    private Boolean getRestResponse(String requestUrl, Method method, Type ObjectType, Object payload, ContentType contentType, AcceptType acceptType) throws IOException {
        try {
            Boolean retVal = false;
            if (IsValid) {
                HttpRequest request = HttpUtil.createRequest(method, requestUrl);
                request.setMethod(method);
                Map<String, String> userMap = new HashMap<>();
                userMap.put("userName", appSettingConfig.getGeoServerUser());
                userMap.put("password", appSettingConfig.getGeoServerPwd());
                request.contentType(_ContentTypeLookup.get(contentType));
                JSONObject serializer = JSONObject.parseObject(ObjectType.toString());
                if (method.toString().equals("GET")) {

                } else if (method.toString().equals("POST")) {
                    serializer.writeJSONString(request.getConnection().getOutputStream(), payload);
                }

                if (acceptType == AcceptType.Byte) {
                    /*  MemoryCacheImageInputStream memoryCacheImageInputStream = new MemoryCacheImageInputStream();*/

                    HttpResponse execute = request.execute();
                    InputStream inputStream = execute.bodyStream();
                    byte[] bytes = new byte[1024 * 1024];
                    byte[] ms = new byte[1024 * 1024 * 5];
                    int read = inputStream.read(bytes);

                    execute.close();
                    payload = bytes;
                    retVal = true;

                } else {
                    HttpResponse execute = request.execute();
                    return retVal = execute.isOk();
                }
            }

            return retVal;
        } catch (Exception e) {
            return false;
        }


    }

    /**
     * 获取图层名称
     */
    public List<String> getLayerNames(String workSpace) {

        List<String> layers = new ArrayList<>();
        List<DataStore> dataStores = getDataStores(workSpace);

        for (DataStore dataStore : dataStores) {
            layers.addAll(getLayerNames(workSpace, dataStore.getName()));
        }

        return layers;
    }

    /**
     * 获取图层名称
     */
    public List<String> getLayerNames(String workSpace, String dataStore) {
        String requestUrl = getRestServiceUrl() + "/workspaces/" + workSpace + "/datastores/" + dataStore + "/featuretypes";
        List<String> layerNames = new ArrayList<>();

        try {
            // 发送 REST 请求，并获取状态
            // 注意: SendRestRequest 方法的实现需要你自己定义或者使用现有的 HTTP 客户端库
            Object layers = null;
            JSONObject jsonObject = sendRestRequest(requestUrl, Method.GET, LayerResponse.class, layers, ContentType.Json, AcceptType.Json);
            if (jsonObject.get("status").toString().equals("true")) {
                // 假设 SendRestRequest 返回的是一个 LayersResponse 对象
                Object payLoad = jsonObject.get("payLoad");
                if (payLoad instanceof LayerResponse) {
                    LayerResponse response = (LayerResponse) payLoad;
                    // 添加每个图层的名称到列表中
                    if (response.getFeatureTypes() != null) {
                        for (Layer layer : response.getFeatureTypes().getFeatureType()) {
                            layerNames.add(layer.getName());
                        }
                    }
                }
            }
            return layerNames;
        } catch (Exception ex) {
            // 如果发生异常，则抛出一个新的异常
            throw new RuntimeException("获取图层时出现异常！", ex);
        }
    }

    /**
     * 图层是否存在
     *
     * @param layer
     * @param workSpace
     * @param dataStore
     * @return
     */
    public boolean isExistLayer(String layer, String workSpace, String dataStore) throws Exception {

        List<String> list = dataStore == null ? getLayerNames(workSpace) : getLayerNames(workSpace, dataStore);
        return list.contains(layer);
    }

    public boolean addLayer(String layerName, String title, Map<String, String> filedAndType, String workSpace, String dataStore, String desiredSrs, String bBox) {
        log.info("filedAndType = {}", JSON.toJSONString(filedAndType));
        log.info("开始添加GeoServer图层:{} 参数:{},{},{},{}", layerName, title, workSpace, dataStore, desiredSrs);
        String requestUrl = getRestServiceUrl() + "/workspaces/" + workSpace + "/datastores/" + dataStore + "/featuretypes";

        // 构建新的图层 XML 字符串
        String newLayer =
                "<featureType>" +
                        "<name>" + layerName + "</name>" +
                        "<nativeName>" + layerName + "</nativeName>" +
                        "<title>" + title + "</title>" +
                        "[BBOX]" +
                        "<srs>" + desiredSrs + "</srs>" +
                        "<attributes>" +
                        "[Parameters]" +
                        "</attributes>" +
                        "</featureType>";

        // 初始化一个 StringBuilder 以构建属性参数部分
        StringBuilder parameters = new StringBuilder();
        for (Map.Entry<String, String> entry : filedAndType.entrySet()) {
            // 每个属性都包含在一个 <attribute> 标签内
            parameters.append("<attribute>")
                    .append("<name>").append(entry.getKey()).append("</name>")
                    .append("<binding>").append(entry.getValue()).append("</binding>")
                    .append("</attribute>");
        }

        // 替换 [PARAMETERS] 占位符
        newLayer = newLayer.replace("[Parameters]", parameters.toString());

        // 如果提供了边界框，则替换 [BBOX] 占位符
        String boundingBox =
                "<nativeBoundingBox>" +
                        "<minx>-1</minx>" +
                        "<maxx>0</maxx>" +
                        "<miny>-1</miny>" +
                        "<maxy>0</maxy>" +
                        "</nativeBoundingBox>" +
                        "<latLonBoundingBox>" +
                        "<minx>-1</minx>" +
                        "<maxx>0</maxx>" +
                        "<miny>-1</miny>" +
                        "<maxy>0</maxy>" +
                        "<crs>" + desiredSrs + "</crs>" +
                        "</latLonBoundingBox>";

        // 替换 [BBOX] 占位符
//        newLayer = newLayer.replace("[BBOX]", boundingBox);
        newLayer = newLayer.replace("[BBOX]", "");

        try {
            // 发送 REST 请求，并获取状态
            // 注意: SendRestRequest 方法的实现需要你自己定义或者使用现有的 HTTP 客户端库
            Object o = sendRestRequest(requestUrl, Method.POST, LayerRequest.class, newLayer, ContentType.Html, AcceptType.Json);
            JSONObject jsonObject = JSONObject.parseObject(o.toString());
            log.info("图层入参:{}", newLayer);
            log.info("添加图层响应:{}", jsonObject);
            return jsonObject.get("status").toString().equals("true");
        } catch (Exception ex) {
            // 如果发生异常，则抛出一个新的异常
            throw new RuntimeException("添加图层时出现异常！", ex);
        }

    }

    public boolean modifyLayerStyle(String layerName, List<String> styles, String defaultStyle) {
        log.info("更新GeoServer图层:{}", layerName);
        String requestUrl = getRestServiceUrl() + "/layers/" + layerName;

        // 构建新的图层 XML 字符串
        String newLayer =
                "<layer>" +
                        "<name>" + layerName + "</name>" +
                        "<type>VECTOR</type>" +
                        "[DEFAULTSTYLE]" +
                        "<styles class=\"linked-hash-set\">" +
                        "[STYLES]" +
                        "</styles>" +
                        "<resource class=\"featureType\">" +
                        "<name>" + layerName + "</name>" +
                        "</resource>" +
                        "</layer>";

        // 初始化一个 StringBuilder 以构建 styles 部分
        StringBuilder parameters = new StringBuilder();
        for (String item : styles) {
            // 每个 style 元素都包含在一个 <style> 标签内
            parameters.append("<style>")
                    .append("<name>").append(item).append("</name>")
                    .append("</style>");
        }

        // 替换 [STYLES] 占位符
        newLayer = newLayer.replace("[STYLES]", parameters.toString());

        // 如果提供了默认样式，则替换 [DEFAULTSTYLE] 占位符
        if (Func.isNotBlank(defaultStyle)) {
            newLayer = newLayer.replace("[DEFAULTSTYLE]",
                    "<defaultStyle>" +
                            "<name>" + defaultStyle + "</name>" +
                            "</defaultStyle>");
        } else {
            // 如果没有提供默认样式，则移除 [DEFAULTSTYLE] 占位符
            newLayer = newLayer.replace("[DEFAULTSTYLE]", "");
        }

        try {
            // 发送 REST 请求，并获取状态
            // 注意: SendRestRequest 方法的实现需要你自己定义或者使用现有的 HTTP 客户端库
            Object o = sendRestRequest(requestUrl, Method.PUT, String.class, newLayer, ContentType.Html, AcceptType.Json);
            JSONObject jsonObject = JSONObject.parseObject(o.toString());
            /* status = HttpUtil.post(requestUrl, newDataStore.toString());*/
            return jsonObject.get("status").toString().equals("true");
        } catch (Exception ex) {
            // 如果发生异常，则抛出一个新的异常
            throw new RuntimeException("修改图层样式时出现异常！", ex);
        }
    }

    /**
     * 添加成WMTS或MVT服务
     *
     * @param type      类型 WMTS或MVT服务
     * @param workspace 工作空间
     * @param layerName 图层名称
     * @param srs       矩阵集
     * @param srsList   矩阵标识集
     * @param size      切片
     * @param format    格式
     * @return
     */
    public boolean addToService(String type, String workspace, String layerName, String srs, String srsList, String size, String format) {

        String url = "";
        String payload = "";
        if (type.equals("MVT")) {
            url = getRestServiceUrl() + "/workspaces/" + workspace + "/vectortiles/layergroups/" + layerName + ".xml";
            payload = getMvtConfig(layerName, srs, size);
        } else if (type.equals("WMTS")) {
            url = getRestServiceUrl() + "/gwc/rest/layers/" + workspace + ":" + layerName + ".xml";
            payload = getWmtsConfig(srs, size, srsList, format);
        }

        Object o = null;
        boolean flag = false;
        try {
            o = sendRestRequest(url, Method.POST, LayerRequest.class, payload, ContentType.Html, AcceptType.Json);
        } catch (Exception e) {
            log.error("注册为{}服务时异常-图层", type, e);
        }

        if (flag) {
            if (type.equals("MVT")) {
                url = getRestServiceUrl() + "/workspaces/" + workspace + "/vectortiles/layergroups/" + layerName + ".xml";
                payload = getMvtConfig(layerName, srs, size);
            } else if (type.equals("WMTS")) {
                url = getRestServiceUrl() + "/gwc/rest/layers/" + workspace + ":" + layerName + ".xml";
                payload = getWmtsConfig(srs, size, srsList, format);
            }

            try {
                o = sendRestRequest(url, Method.POST, LayerRequest.class, payload, ContentType.Html, AcceptType.Json);
            } catch (Exception e) {
                log.error("注册为{}服务时异常-图层组", type, e);
                return false;
            }
        }
        log.info("注册为{}服务响应:{}", type, o);
        if (o == null) {
            return false;
        }
        JSONObject jsonObject = JSONObject.parseObject(o.toString());
        log.info("注册为{}服务响应:{}", type, jsonObject);
        return jsonObject.get("status").toString().equals("true");
    }

    private static final double INITIAL_RESOLUTION = 0.703125;

    public static String generateResolutions(String matrixSet) {
        List<String> levels = Arrays.asList(matrixSet.split(","));
        StringBuilder resolutions = new StringBuilder();

        for (String level : levels) {
            int zoomLevel = Integer.parseInt(level.split(":")[2]);
            double resolution = INITIAL_RESOLUTION / Math.pow(2, zoomLevel);
            resolutions.append("<double>").append(resolution).append("</double>\n");
        }

        return resolutions.toString();
    }

    private String getMvtConfig(String layerName, String srs, String size) {
        return
                "<vectorTileLayer>" +
                        "<name>" + layerName + "</name>" +
                        "<nativeName>" + layerName + "</nativeName>" +
                        "<title>" + layerName + "</title>" +
                        "<srs>" + srs + "</srs>" +
                        "<metaTileGrid>" +
                        "<width>" + size + "</width>" +
                        "<height>" + size + "</height>" +
                        "</metaTileGrid>" +
                        "<gridSubsets>" +
                        "<gridSubset>" +
                        "<gridSetName>" + srs + "</gridSetName>" +
                        "</gridSubset>" +
                        "</gridSubsets>" +
                        "</vectorTileLayer>";
    }

    /**
     * @param srs     矩阵集 EPSG:4326
     * @param size    切片 256
     * @param srsList 矩阵标识集合 EPSG:4326:0,EPSG:4326:1,EPSG:4326:2,EPSG:4326:3,EPSG:4326:4,EPSG:4326:5,EPSG:4326:6,EPSG:4326:7,EPSG:4326:8,EPSG:4326:9,EPSG:4326:10,EPSG:4326:11,EPSG:4326:12,EPSG:4326:13,EPSG:4326:14
     * @param format  格式 image/png
     * @return
     */
    private String getWmtsConfig(String srs, String size, String srsList, String format) {
        return
                "<GeoServerLayer>" +
                        "<enabled>true</enabled>" +
                        "<mimeFormats>" +
                        "<string>" + format + "</string>" +
                        "</mimeFormats>" +
                        "<gridSubsets>" +
                        "<gridSubset>" +
                        "<gridSetName>" + srs + "</gridSetName>" +
                        "</gridSubset>" +
                        "</gridSubsets>" +
                        "<metaWidthHeight>" +
                        "<int>" + size + "</int>" +
                        "<int>" + size + "</int>" +
                        "</metaWidthHeight>" +
                        "<resolutions>" + generateResolutions(srsList) +
                        "</resolutions>" +
                        "</GeoServerLayer>";
    }

    public byte[] getWMTSData(String requestUrl) {
        return null;
    }

    public boolean isExsitWorkSpace(String workSpace) {
        List<WorkSpace> list = getWorkSpaces();
        for (WorkSpace item : list) {
            if (item.getName().equals(workSpace)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 发布图层
     *
     * @param layerName 图层名称
     * @param title     标题
     * @param listDto   字段列表
     * @param layerType 图层类型
     * @param bbox      边界框
     * @return true：发布成功，false：发布失败
     */
    public boolean publicLayer(String layerName, String title, List<LayerFieldDto> listDto, String layerType, String bbox) {
        if (!isExsitWorkSpace(appSettingConfig.getGeoWorkspace())) {
            addWorkSpace(appSettingConfig.getGeoWorkspace());
        }
        if (!isExsitDataStore(appSettingConfig.getGeoWorkspace(), appSettingConfig.getGeoDatastore())) {
            addDataStore(appSettingConfig.getGeoWorkspace(),
                    appSettingConfig.getGeoDatastore(),
                    appSettingConfig.getGeoServerIP(),
                    appSettingConfig.getGeoServerPort(),
                    appSettingConfig.getGeoDb(),
                    appSettingConfig.getGeoServerUser(),
                    appSettingConfig.getGeoServerPwd());
        }

        Map<String, String> filedAndType = new HashMap<>();
        for (LayerFieldDto dto : listDto) {
            filedAndType.put(dto.getAttributeName(), getGeoFiledType(dto.getAttributeTypeName()));
        }

        filedAndType.put("guid", getGeoFiledType("VARCHAR"));
        filedAndType.put("geom", getGeoFiledType(layerType));

        if (!isExsitLayer(layerName, appSettingConfig.getGeoWorkspace(), appSettingConfig.getGeoDatastore())) {
            addLayer(layerName, title, filedAndType, appSettingConfig.getGeoWorkspace(), appSettingConfig.getGeoDatastore(), appSettingConfig.getEpsg(), bbox);
            modifyLayerStyle(layerName, new ArrayList<>(), getDefaultStyle(layerType));
        }
        return true;
    }

    /**
     * 发布图层（带样式指定）
     *
     * @param layerName  图层名称
     * @param title      图层标题
     * @param layerType  图层类型（点/线/面）
     * @param stylesName 目标样式名称
     * @return 是否发布成功
     */
    public boolean publicLayer(String layerName, String title, String layerType, String stylesName) {
        // 获取工作区样式列表（Java 8 Stream转换为List）
        List<Style> styleList = new ArrayList<>(getStyles(appSettingConfig.getGeoWorkspace()));

        // 检查图层存在性 + 样式存在性
        boolean layerExists = isExsitLayer(layerName, appSettingConfig.getGeoWorkspace(), appSettingConfig.getGeoDatastore());
        boolean styleExists = styleList.stream()
                .anyMatch(style -> stylesName.equals(style.getName()));

        if (layerExists && styleExists) {
            // 创建样式列表（单元素列表）
            List<String> targetStyles = Collections.singletonList(stylesName);
            // 修改图层样式（调用GeoServer接口）
            modifyLayerStyle(layerName, targetStyles, getDefaultStyle(layerType));
            return true;
        } else {
            return false;
        }
    }

    /**
     * 检查图层是否存在
     *
     * @param layer     图层名称
     * @param workSpace 工作区名称
     * @param dataStore 数据存储名称
     * @return 如果存在返回true，否则返回false
     */
    public boolean isExsitLayer(String layer, String workSpace, String dataStore) {
        List<String> list = dataStore == null ? getLayerNames(workSpace) : getLayerNames(workSpace, dataStore);
        for (String item : list) {
            if (item.equals(layer)) {
                return true;
            }
        }
        return false;
    }

    public static String getGeoFiledType(String filedType) {
        switch (filedType.toUpperCase()) {
            case "LONG INTEGER":
            case "SHORT INTEGER":
            case "INT":
            case "BIGINT":
                return "java.lang.Integer";

            case "DOUBLE":
                return "java.lang.Double";
            case "FLOAT":
            case "NUMERIC":
                return "java.math.BigDecimal";

            case "TEXT":
            case "VARCHAR":
                return "java.lang.String";

            case "DATETIME":
            case "DATE":
                return "java.util.Date";

            case "点":
            case "Point":
            case "POINT":
                return "com.vividsolutions.jts.geom.Point";

            case "线":
            case "Line":
            case "LINE":
                return "com.vividsolutions.jts.geom.MultiLineString";

            case "面":
            case "Polygon":
            case "POLYGON":
                return "com.vividsolutions.jts.geom.MultiPolygon";

            default:
                break;
        }
        return "java.lang.String";
    }

}

