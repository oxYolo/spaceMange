package com.whulri.geodata.common.util;

import com.whulri.gdal.model.ColorRamp;
import com.whulri.gdal.model.ColorRampInfo;
import com.whulri.gdal.tif.tiffileReader;
import it.geosolutions.geoserver.rest.GeoServerRESTManager;
import it.geosolutions.geoserver.rest.GeoServerRESTPublisher;
import it.geosolutions.geoserver.rest.encoder.GSLayerEncoder;
import it.geosolutions.geoserver.rest.encoder.coverage.GSCoverageEncoder;
import it.geosolutions.geoserver.rest.encoder.coverage.GSImageMosaicEncoder;
import it.geosolutions.geoserver.rest.encoder.datastore.GSGeoTIFFDatastoreEncoder;
import it.geosolutions.geoserver.rest.encoder.datastore.GSShapefileDatastoreEncoder;
import it.geosolutions.geoserver.rest.manager.GeoServerRESTStyleManager;
import org.gdal.gdal.Band;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconstConstants;
import com.whulri.geodata.common.bridge.Func;
import lombok.extern.slf4j.Slf4j;

import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/**
 * @author zzw
 * @date 2026/3/2 19:02
 */
@Slf4j
public class GeoServerUtil {
    /**
     * 上传tif文件至geoserver
     *
     * @param url         url
     * @param username    username
     * @param password    password
     * @param tifFilePath tif文件路径
     * @param workspace   workspace
     * @param storename   storename
     * @param fileName    tif文件名称
     * @throws MalformedURLException
     * @throws FileNotFoundException
     */
    public static void uploadTiff(String url, String username, String password,
                                  String workspace, String tifFilePath, String storename, String fileName)
            throws MalformedURLException, FileNotFoundException {

        //与GeoServer连接，进行管理
        GeoServerRESTManager manager = new GeoServerRESTManager(new URL(url), username, password);
        GeoServerRESTPublisher publisher = manager.getPublisher();

        //创建工作区
        if (!manager.getReader().existsWorkspace(workspace)) {
            publisher.createWorkspace(workspace);
        }

        //创建数据存储
        if (!manager.getReader().existsCoveragestore(workspace, storename)) {
            GSGeoTIFFDatastoreEncoder gsGeoTIFFDatastoreEncoder = new GSGeoTIFFDatastoreEncoder(storename);
            gsGeoTIFFDatastoreEncoder.setWorkspaceName(workspace);
            gsGeoTIFFDatastoreEncoder.setUrl(new URL("file:" + tifFilePath));
            gsGeoTIFFDatastoreEncoder.setEnabled(true);
            gsGeoTIFFDatastoreEncoder.setType("GeoTIFF");
            manager.getStoreManager().create(workspace, gsGeoTIFFDatastoreEncoder);
        }

        //发布tif图层（如果没有目标数据存储则会先自动创建该命名数据存储再发布）
        boolean bl = manager.getPublisher().publishGeoTIFF(workspace, storename, fileName, new File(tifFilePath));
        GSImageMosaicEncoder encoder = new GSImageMosaicEncoder();
        encoder.setName(storename);
        encoder.setInputTransparentColor("#000000");
        encoder.setOutputTransparentColor("#000000");
        manager.getPublisher().configureCoverage(encoder, workspace, storename);

        //样式代码
		/*GeoServerRESTStyleManager styleManager = manager.getStyleManager();
		if (!styleManager.existsStyle("sst2")) {
			// 向geoserver写入style
			boolean publishStyleInWorkspace = styleManager.publishStyleInWorkspace(workspace, "abc", "sst2");
			System.out.println("publishStyleInWorkspace : " + publishStyleInWorkspace);
		}
		boolean publish = publisher.publishGeoTIFF(workspace,storename, fileName, new File(tifFilePath), "EPSG:4326",
			GSResourceEncoder.ProjectionPolicy.FORCE_DECLARED, "sst1", null);*/
    }

    /**
     * 上传tif文件至geoserver
     *
     * @param url         url
     * @param username    username
     * @param password    password
     * @param tifFilePath tif文件路径
     * @param workspace   workspace
     * @param storename   storename
     * @param fileName    tif文件名称
     * @throws MalformedURLException
     * @throws FileNotFoundException
     */
    public static void uploadTiffWhite(String url, String username, String password,
                                       String workspace, String tifFilePath, String storename, String fileName)
            throws MalformedURLException, FileNotFoundException {

        //与GeoServer连接，进行管理
        GeoServerRESTManager manager = new GeoServerRESTManager(new URL(url), username, password);
        GeoServerRESTPublisher publisher = manager.getPublisher();

        //创建工作区
        if (!manager.getReader().existsWorkspace(workspace)) {
            publisher.createWorkspace(workspace);
        }

        //创建数据存储
        if (!manager.getReader().existsCoveragestore(workspace, storename)) {
            GSGeoTIFFDatastoreEncoder gsGeoTIFFDatastoreEncoder = new GSGeoTIFFDatastoreEncoder(storename);
            gsGeoTIFFDatastoreEncoder.setWorkspaceName(workspace);
            gsGeoTIFFDatastoreEncoder.setUrl(new URL("file:" + tifFilePath));
            gsGeoTIFFDatastoreEncoder.setEnabled(true);
            gsGeoTIFFDatastoreEncoder.setType("GeoTIFF");
            manager.getStoreManager().create(workspace, gsGeoTIFFDatastoreEncoder);
        }

        //发布tif图层（如果没有目标数据存储则会先自动创建该命名数据存储再发布）
        manager.getPublisher().publishGeoTIFF(workspace, storename, fileName, new File(tifFilePath));
        GSImageMosaicEncoder encoder = new GSImageMosaicEncoder();
        encoder.setName(storename);
        encoder.setInputTransparentColor("#ffffff");//设置透明度
        encoder.setOutputTransparentColor("#ffffff");
        manager.getPublisher().configureCoverage(encoder, workspace, storename);
        //样式代码
		/*GeoServerRESTStyleManager styleManager = manager.getStyleManager();
		if (!styleManager.existsStyle("sst2")) {
			// 向geoserver写入style
			boolean publishStyleInWorkspace = styleManager.publishStyleInWorkspace(workspace, "abc", "sst2");
			System.out.println("publishStyleInWorkspace : " + publishStyleInWorkspace);
		}
		boolean publish = publisher.publishGeoTIFF(workspace,storename, fileName, new File(tifFilePath), "EPSG:4326",
			GSResourceEncoder.ProjectionPolicy.FORCE_DECLARED, "sst1", null);*/
    }

    /**
     * 上传tif文件至geoserver(单波段tif带样式)
     *
     * @param url         url
     * @param username    username
     * @param password    password
     * @param tifFilePath tif文件路径
     * @param workspace   workspace
     * @param storename   storename
     * @param fileName    tif文件名称
     * @throws MalformedURLException
     * @throws FileNotFoundException
     */
    public static void uploadTiffWithStyle(String url, String username, String password,
                                           String workspace, String tifFilePath, String storename, String fileName)
            throws MalformedURLException, FileNotFoundException {

        //与GeoServer连接，进行管理
        GeoServerRESTManager manager = new GeoServerRESTManager(new URL(url), username, password);
        GeoServerRESTPublisher publisher = manager.getPublisher();

        //创建工作区
        if (!manager.getReader().existsWorkspace(workspace)) {
            publisher.createWorkspace(workspace);
        }

        //创建数据存储
        if (!manager.getReader().existsCoveragestore(workspace, storename)) {
            GSGeoTIFFDatastoreEncoder gsGeoTIFFDatastoreEncoder = new GSGeoTIFFDatastoreEncoder(storename);
            gsGeoTIFFDatastoreEncoder.setWorkspaceName(workspace);
            gsGeoTIFFDatastoreEncoder.setUrl(new URL("file:" + tifFilePath));
            gsGeoTIFFDatastoreEncoder.setEnabled(true);
            gsGeoTIFFDatastoreEncoder.setType("GeoTIFF");
            manager.getStoreManager().create(workspace, gsGeoTIFFDatastoreEncoder);
        }
        boolean ispublish = manager.getPublisher().publishGeoTIFF(workspace, storename, fileName, new File(tifFilePath));
        GSCoverageEncoder encoder = new GSCoverageEncoder();
        encoder.setName(storename);
        encoder.setSRS("EPSG:4326");
        boolean isC = manager.getPublisher().configureCoverage(encoder, workspace, storename);
        GSLayerEncoder layerEncoder = new GSLayerEncoder();
        layerEncoder.setDefaultStyle("raster");
        layerEncoder.setEnabled(true);
        boolean isS = manager.getPublisher().configureLayer(workspace, fileName, layerEncoder);
    }

    /*
     tcys色带样式固定  1 - 红黄绿渐变 2 - 蓝绿红渐变
     */
    public static ColorRampInfo Custom(String tifPath, String sd) {

        tiffileReader tifReader = null;

        try {
            tifReader = new tiffileReader(tifPath);
        } catch (Exception var13) {
            log.warn("读取 TIFF 信息失败: path={}, err={}", tifPath, var13.getMessage(), var13);
        }

        if (tifReader.get_tiffileInfo().getRasterCount() > 1) {
            tifReader.delete();
            return null;
        } else {
            double[] maxminDatas = tifReader.GetMaximum();
            tifReader.delete();
            if (maxminDatas != null && maxminDatas.length >= 2) {
                double min = maxminDatas[0];
                double max = maxminDatas[1];
                if (min - max == 0) {
                    min = min - 1;
                    max = max + 1;
                }
                double step = (max - min) / 2.0;
                java.util.List<Color> NormalColor = new LinkedList();
                if ("2".equals(sd)) {
                    NormalColor.add(Color.blue);
                    NormalColor.add(Color.green);
                    NormalColor.add(Color.red);
                } else {
                    NormalColor.add(Color.red);
                    NormalColor.add(Color.yellow);
                    NormalColor.add(Color.green);
                }

                ColorRampInfo result = new ColorRampInfo();
                result.setColorRamps(new ArrayList());

                for (int i = 0; i < 3; ++i) {
                    ColorRamp colorRamp2 = new ColorRamp();
                    colorRamp2.setRangeLow(min + (double) i * step);
                    colorRamp2.setRangeColor((Color) NormalColor.get(i));
                    colorRamp2.setRangeValue(i + 1);
                    List<ColorRamp> colorRamps = result.getColorRamps();
                    colorRamps.add(colorRamp2);
                }

                return result;
            } else {
                return null;
            }
        }
    }

    private static String CreateTifStyleXml(String layerName, Double nodata, String tifPath, ColorRampInfo info) {
        String colContent = "";
        for (int i = 0; i < info.getColorRamps().size(); i++) {
            ColorRamp cr = info.getColorRamps().get(i);
            Color color = cr.getRangeColor();
            String hexValue = "#" + Integer.toHexString((color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue());
            if (i == info.getColorRamps().size() - 1) {
                //防止开闭关系
                cr.setRangeHigh(cr.getRangeHigh() + 0.1);
            }
            colContent += "<ColorMapEntry color=\"" + hexValue + "\" quantity=\"" + cr.getRangeLow() + "\" label=\"values\" />" + "\n";
        }
        String styleContent = "";
        if (nodata < info.getColorRamps().get(info.getColorRamps().size() - 1).getRangeHigh()) {
            styleContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                    "<StyledLayerDescriptor version=\"1.0.0\"" +
                    " xmlns=\"http://www.opengis.net/sld\" xmlns:ogc=\"http://www.opengis.net/ogc\"" +
                    " xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"" +
                    " xsi:schemaLocation=\"http://www.opengis.net/sld StyledLayerDescriptor.xsd\">" +
                    "<NamedLayer>" +
                    "<Name>" + layerName + "</Name>" +
                    "<UserStyle>" +
                    "<Name>" + layerName + "</Name>" +
                    "<Title>" + layerName + "</Title>" +
                    "<Abstract>" + layerName + "</Abstract>" +
                    "<FeatureTypeStyle>" +
                    "<Rule>" +
                    "<RasterSymbolizer>" +
                    "<Opacity>1.0</Opacity>" +
                    "<ColorMap type=\"ramp\">" +
                    "<ColorMapEntry color=\"#000000\" quantity=\"" + nodata + "\" label=\"nodata\" opacity=\"0.0\" />" +
                    colContent +
                    "</ColorMap>" +
                    "</RasterSymbolizer>" +
                    "</Rule>" +
                    "</FeatureTypeStyle>" +
                    "</UserStyle>" +
                    "</NamedLayer>" +
                    "</StyledLayerDescriptor>";
        } else {
            styleContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                    "<StyledLayerDescriptor version=\"1.0.0\"" +
                    " xmlns=\"http://www.opengis.net/sld\" xmlns:ogc=\"http://www.opengis.net/ogc\"" +
                    " xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"" +
                    " xsi:schemaLocation=\"http://www.opengis.net/sld StyledLayerDescriptor.xsd\">" +
                    "<NamedLayer>" +
                    "<Name>" + layerName + "</Name>" +
                    "<UserStyle>" +
                    "<Name>" + layerName + "</Name>" +
                    "<Title>" + layerName + "</Title>" +
                    "<Abstract>" + layerName + "</Abstract>" +
                    "<FeatureTypeStyle>" +
                    "<Rule>" +
                    "<RasterSymbolizer>" +
                    "<Opacity>1.0</Opacity>" +
                    "<ColorMap type=\"ramp\">" +
                    colContent +
                    "<ColorMapEntry color=\"#000000\" quantity=\"" + nodata + "\" label=\"nodata\" opacity=\"0.0\" />" +
                    "</ColorMap>" +
                    "</RasterSymbolizer>" +
                    "</Rule>" +
                    "</FeatureTypeStyle>" +
                    "</UserStyle>" +
                    "</NamedLayer>" +
                    "</StyledLayerDescriptor>";
        }
        return styleContent;
    }

    public static double GetTifNoData(String tifPath) {
        gdal.AllRegister();
        Dataset _dataset = gdal.Open(tifPath, gdalconstConstants.GA_ReadOnly);
        Double[] val = new Double[1];
        if (Func.isNotEmpty(_dataset)) {
            {
                Band band = _dataset.GetRasterBand(1);
                if (Func.isNotEmpty(band)) {
                    band.GetNoDataValue(val);
                    if (Func.isNotEmpty(val)) {
                        return val[0] == null ? 0 : val[0];
                    }
                }
                _dataset.delete();
            }
        }
        return -9999;
    }

    /**
     * 上传shp文件至geoserver
     *
     * @param url            url
     * @param username       username
     * @param password       password
     * @param shpZipFilePath shp压缩后文件路径
     * @param workspace      workspace
     * @param storename      storename
     * @param fileName       shp压缩后文件路径
     * @throws MalformedURLException
     * @throws FileNotFoundException
     */
    public static void uploadShp(String url, String username, String password, String workspace,
                                 String shpZipFilePath, String storename, String fileName, String styleContent,String charset)
            throws MalformedURLException, FileNotFoundException {

        if(Func.isBlank(charset)){
            charset="GBK";
        }
        //与GeoServer连接，进行管理
        GeoServerRESTManager manager = new GeoServerRESTManager(new URL(url), username, password);
        GeoServerRESTPublisher publisher = manager.getPublisher();

        //创建工作区
        if (!manager.getReader().existsWorkspace(workspace)) {
            publisher.createWorkspace(workspace);
        }
        GeoServerRESTStyleManager styleManager = manager.getStyleManager();
        if (!styleManager.existsStyle(fileName) && Func.isNotBlank(styleContent)) {
            // 向geoserver写入style
            boolean publishStyleInWorkspace = styleManager.publishStyleInWorkspace(workspace, styleContent, fileName);
            System.out.println("publishStyleInWorkspace : " + publishStyleInWorkspace);
        }

        //创建数据存储
        if (!manager.getReader().existsDatastore(workspace, storename)) {
            GSShapefileDatastoreEncoder gsShapefileDatastoreEncoder = new GSShapefileDatastoreEncoder(storename, new URL("file:" + shpZipFilePath));
            gsShapefileDatastoreEncoder.setEnabled(true);
            gsShapefileDatastoreEncoder.setType("Shapefile");
            gsShapefileDatastoreEncoder.setCharset(Charset.forName(charset));
            manager.getStoreManager().create(workspace, gsShapefileDatastoreEncoder);
        }
		/*GSFeatureTypeEncoder gsFeatureTypeEncoder = new GSFeatureTypeEncoder();
		gsFeatureTypeEncoder.setTitle(fileName);
		gsFeatureTypeEncoder.setName(fileName);
		gsFeatureTypeEncoder.setSRS(GeoServerRESTPublisher.DEFAULT_CRS);*/


        //发布shp图层（如果没有目标数据存储则会先自动创建该命名数据存储再发布）
        manager.getPublisher().publishShp(workspace, storename, fileName, new File(shpZipFilePath));
        GSLayerEncoder gsLayerEncoder = new GSLayerEncoder();
        gsLayerEncoder.setDefaultStyle(fileName);
        boolean isS = manager.getPublisher().configureLayer(workspace, fileName, gsLayerEncoder);
    }


    public static void publishStyle(String url, String username, String password, String workspace,
                                    String styleName, String styleContent) {
        //与GeoServer连接，进行管理
        GeoServerRESTManager manager = null;
        try {
            manager = new GeoServerRESTManager(new URL(url), username, password);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        GeoServerRESTStyleManager styleManager = manager.getStyleManager();
        if (!styleManager.existsStyle(workspace,styleName)) {
            // 向geoserver写入style
            boolean publishStyleInWorkspace = styleManager.publishStyleInWorkspace(workspace, styleContent, styleName);
            System.out.println("publishStyleInWorkspace : " + publishStyleInWorkspace);
        } else {
            boolean publishStyleInWorkspace = styleManager.updateStyleInWorkspace(workspace, styleContent, styleName);
            System.out.println("publishStyleInWorkspace : " + publishStyleInWorkspace);
        }
    }

    /**
     * 为指定图层绑定/更新样式
     * 逻辑：1. 先发布/更新样式（与publishStyle逻辑一致）；2. 将样式绑定为图层的默认样式
     *
     * @param url          GeoServer地址（如：http://localhost:8080/geoserver）
     * @param username     GeoServer用户名
     * @param password     GeoServer密码
     * @param workspace    工作区名称
     * @param layerName    图层名称
     * @param styleName    样式名称
     * @param styleContent 样式字符串（SLD/XML格式）
     */
    public static void bindStyleToLayer(String url, String username, String password,
                                        String workspace, String layerName, String styleName, String styleContent) {
        // 1. 校验必填参数
        if (Func.isBlank(url) || Func.isBlank(username) || Func.isBlank(password) ||
                Func.isBlank(workspace) || Func.isBlank(styleName) || Func.isBlank(styleContent)) {
            throw new IllegalArgumentException("必填参数不能为空！");
        }
        try {
            String auth = "Basic " + java.util.Base64.getEncoder()
                    .encodeToString((username + ":" + password).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String encName = java.net.URLEncoder.encode(styleName, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
            String styleBase = url + "/rest/workspaces/" + workspace + "/styles";

            // 2. 存在性检查（UTF-8 编码的样式名，避免中文名 400）
            cn.hutool.http.HttpResponse chk = cn.hutool.http.HttpRequest.get(styleBase + "/" + encName + ".json")
                    .header("Authorization", auth).timeout(30000).execute();
            boolean exists = chk.getStatus() == 200;
            chk.close();

            // 3. 不存在则先建样式元数据，再上传 SLD；存在则直接覆盖
            if (!exists) {
                String createBody = "<style><name>" + styleName + "</name><filename>" + styleName + ".sld</filename></style>";
                cn.hutool.http.HttpResponse cr = cn.hutool.http.HttpRequest.post(styleBase)
                        .header("Authorization", auth).contentType("application/xml")
                        .body(createBody).timeout(30000).execute();
                int cst = cr.getStatus();
                String cbody = cr.body();
                cr.close();
                if (cst != 201 && cst != 200) {
                    throw new RuntimeException("GeoServer 样式创建失败: HTTP " + cst + " " + cbody);
                }
            }
            cn.hutool.http.HttpResponse put = cn.hutool.http.HttpRequest.put(styleBase + "/" + encName + "?raw=true")
                    .header("Authorization", auth).contentType("application/vnd.ogc.sld+xml")
                    .body(styleContent).timeout(60000).execute();
            int pst = put.getStatus();
            put.close();
            if (pst != 200) {
                throw new RuntimeException("GeoServer 样式上传失败: HTTP " + pst);
            }

            // 4. 设置图层默认样式（图层名可能与样式名不同/图层不存在——失败仅告警，不阻断样式更新）
            if (Func.isNotBlank(layerName)) {
                String encLayer = java.net.URLEncoder.encode(layerName, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
                String layerBody = "{\"layer\":{\"defaultStyle\":{\"name\":\"" + styleName + "\"}}}";
                cn.hutool.http.HttpResponse cfg = cn.hutool.http.HttpRequest
                        .put(url + "/rest/workspaces/" + workspace + "/layers/" + encLayer)
                        .header("Authorization", auth).contentType("application/json")
                        .body(layerBody).timeout(30000).execute();
                int lst = cfg.getStatus();
                cfg.close();
                if (lst != 200) {
                    System.err.println("图层默认样式绑定未生效（不影响样式更新）: HTTP " + lst);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("图层样式绑定异常", e);
        }
    }

    /**
     * 发布GeoServer切片服务
     *
     * @param url         GeoServer服务地址
     * @param username    GeoServer用户名
     * @param password    GeoServer密码
     * @param workspace   工作区名称
     * @param layerName   图层名称
     * @param dataType    数据类型 (shp/tif)
     * @param tileType    切片类型 (wmts/mvt)
     * @param gridSetName 网格集名称 (默认EPSG:4326)
     * @param start       缩放级别范围 0
     * @param stop        缩放级别范围 18
     */
    public static void publishTileService(String url, String username, String password,
                                         String workspace, String layerName, String dataType,
                                         String tileType, String gridSetName,String start, String stop) {
        // 1. 校验必填参数
        if (Func.isBlank(url) || Func.isBlank(username) || Func.isBlank(password) ||
                Func.isBlank(workspace) || Func.isBlank(layerName) || Func.isBlank(dataType) ||
                Func.isBlank(tileType)) {
            throw new IllegalArgumentException("必填参数不能为空！");
        }

        // 2. 校验数据类型和切片类型的组合
        if ("shp".equalsIgnoreCase(dataType)) {
            if (!"wmts".equalsIgnoreCase(tileType) && !"mvt".equalsIgnoreCase(tileType)) {
                throw new IllegalArgumentException("SHP数据只支持wmts和mvt切片类型！");
            }
        } else if ("tif".equalsIgnoreCase(dataType)) {
            if (!"wmts".equalsIgnoreCase(tileType)) {
                throw new IllegalArgumentException("TIF数据只支持wmts切片类型！");
            }
        } else {
            throw new IllegalArgumentException("不支持的数据类型，仅支持shp和tif！");
        }

        // 3. 设置默认值
        if (Func.isBlank(gridSetName)) {
            gridSetName = "EPSG:4326";
        }
        if (Func.isBlank(start)||Func.isBlank(stop)) {
            start = "0";
            stop = "15";
        }

        GeoServerRESTManager manager = null;
        try {
            // 4. 初始化GeoServer连接
            manager = new GeoServerRESTManager(new URL(url), username, password);
//            GeoServerRESTPublisher publisher = manager.getPublisher();

            // 5. 检查工作区是否存在
            if (!manager.getReader().existsWorkspace(workspace)) {
                throw new RuntimeException("工作区" + workspace + "不存在！");
            }

            // 6. 检查图层是否存在
            boolean layerExists = false;
            try {
                // 获取工作区中的所有图层
                String layersUrl = url + "/rest/workspaces/" + workspace + "/layers.json";
                HttpURLConnection connection = (HttpURLConnection) new URL(layersUrl).openConnection();
                connection.setRequestMethod("GET");
                
                // 设置认证
                String auth = username + ":" + password;
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
                connection.setRequestProperty("Authorization", "Basic " + encodedAuth);
                connection.setRequestProperty("Accept", "application/json");
                
                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        
                        // 解析JSON响应
                        JSONObject json = JSON.parseObject(response.toString());
                        if (json.containsKey("layers")) {
                            JSONObject layersObj = json.getJSONObject("layers");
                            if (layersObj.containsKey("layer")) {
                                JSONArray layerArray = layersObj.getJSONArray("layer");
                                for (int i = 0; i < layerArray.size(); i++) {
                                    JSONObject layerObj = layerArray.getJSONObject(i);
                                    if (layerObj.containsKey("name") && layerObj.getString("name").equals(layerName)) {
                                        layerExists = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("检查图层存在性失败：" + e.getMessage());
            }
            if (!layerExists) {
                throw new RuntimeException("图层" + layerName + "不存在！");
            }

            // 7. 跳过手动创建切片配置，直接触发切片生成
            // GeoServer 发布图层时会自动创建 GWC 配置，无需手动创建
            System.out.println("跳过手动创建切片配置，直接触发切片生成...");

            // 8. 触发切片生成
            triggerTileGeneration(url, username, password, workspace, layerName, gridSetName, start, stop);

            System.out.println("切片服务发布成功！");

        } catch (MalformedURLException e) {
            System.err.println("GeoServer URL格式错误：" + e.getMessage());
            throw new RuntimeException("GeoServer连接失败", e);
        } catch (Exception e) {
            System.err.println("切片服务发布失败：" + e.getMessage());
            throw new RuntimeException("切片服务发布异常", e);
        }
    }

    /**
     * 触发切片生成
     */
    private static void triggerTileGeneration(String url, String username, String password,
                                              String workspace, String layerName, String gridSetName, String start, String stop) {
        try {
            // 修正 API 路径 - 使用正确的 GWC 种子 API 路径
            String generateUrl = url + "/gwc/rest/seed/" + workspace + ":" + layerName + ".json";
            // 获取图层实际边界
            double[] bounds = getLayerBounds(url, username, password, workspace, layerName);

            // 构建正确的 JSON 请求格式
            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"seedRequest\": {");
            json.append("\"name\": \"").append(workspace).append(":").append(layerName).append("\",");
            
            // 如果获取不到有效边界，则不设置bounds，让GWC自动使用图层的边界
            if (bounds != null) {
                json.append("\"bounds\": {\"minX\": ").append(bounds[0]).append(", \"minY\": ").append(bounds[1]).append(", \"maxX\": ").append(bounds[2]).append(", \"maxY\": ").append(bounds[3]).append("},");
            }
            
            json.append("\"gridSetId\": \"").append(gridSetName).append("\",");
            json.append("\"zoomStart\": ").append(start).append(",");
            json.append("\"zoomStop\": ").append(stop).append(",");
            json.append("\"threadCount\": 4,");
            json.append("\"type\": \"reseed\"");
            System.out.println("使用 reseed 类型进行切片重建，确保使用最新数据");
            json.append("}");
            json.append("}");

            URL endpoint = new URL(generateUrl);
            HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");

            // 设置认证
            String auth = username + ":" + password;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            connection.setRequestProperty("Authorization", "Basic " + encodedAuth);

            System.out.println("发送切片生成请求到：" + generateUrl);
            System.out.println("请求内容：" + json.toString());

            // 写入请求体
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = json.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            System.out.println("切片生成请求响应码：" + responseCode);

            // 打印错误信息（如果有）
            if (responseCode >= 400) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
                    StringBuilder error = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        error.append(line);
                    }
                    System.err.println("切片生成错误信息：" + error.toString());
                }
            } else {
                System.out.println("切片生成任务已提交！");
            }
        } catch (Exception e) {
            log.error("触发切片生成失败: {}", e.getMessage(), e);
        }
    }

    private static double[] getLayerBounds(String url, String username, String password, String workspace, String layerName) {
        try {
            String layerUrl = url + "/rest/workspaces/" + workspace + "/layers/" + layerName + ".json";
            HttpURLConnection connection = (HttpURLConnection) new URL(layerUrl).openConnection();
            connection.setRequestMethod("GET");
            // 设置认证
            String auth = username + ":" + password;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            connection.setRequestProperty("Authorization", "Basic " + encodedAuth);
            connection.setRequestProperty("Accept", "application/json");

            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }

                    // 解析JSON响应
                    JSONObject json = JSON.parseObject(response.toString());
                    if (json.containsKey("layer")) {
                        JSONObject layerObj = json.getJSONObject("layer");
                        if (layerObj.containsKey("resource")) {
                            JSONObject resourceObj = layerObj.getJSONObject("resource");
                            if (resourceObj.containsKey("nativeBoundingBox")) {
                                JSONObject bbox = resourceObj.getJSONObject("nativeBoundingBox");
                                // 检查bbox是否有效
                                if (bbox != null && bbox.containsKey("minx") && bbox.containsKey("maxx")) {
                                    double minX = bbox.getDouble("minx");
                                    double minY = bbox.getDouble("miny");
                                    double maxX = bbox.getDouble("maxx");
                                    double maxY = bbox.getDouble("maxy");
                                    // 检查是否有效数值
                                    if (!Double.isNaN(minX) && !Double.isNaN(maxX) && !Double.isNaN(minY) && !Double.isNaN(maxY)) {
                                        return new double[]{minX, minY, maxX, maxY};
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("获取图层边界失败：" + e.getMessage());
        }
        // 返回null表示无法获取有效边界
        return null;
    }

    /**
     * 为已发布的三波段TIFF服务设置透明度
     * @param url          GeoServer地址
     * @param username     GeoServer用户名
     * @param password     GeoServer密码
     * @param workspace    工作区名称
     * @param layerName    图层名称
     * @param transparentColor 透明颜色（如 "#ffffff"）
     */
    public static void setTiffTransparency(String url, String username, String password,
                                           String workspace, String layerName, String transparentColor) {
        try {
            // 初始化GeoServer连接
            GeoServerRESTManager manager = new GeoServerRESTManager(new URL(url), username, password);
            GeoServerRESTPublisher publisher = manager.getPublisher();

            // 配置覆盖层，设置透明度
            GSImageMosaicEncoder encoder = new GSImageMosaicEncoder();
            encoder.setName(layerName);
            encoder.setInputTransparentColor(transparentColor);
            encoder.setOutputTransparentColor(transparentColor);

            // 应用配置
            boolean configureSuccess = publisher.configureCoverage(encoder, workspace, layerName);
            System.out.println("设置TIFF透明度结果：" + configureSuccess);

        } catch (MalformedURLException e) {
            System.err.println("GeoServer URL格式错误：" + e.getMessage());
            throw new RuntimeException("GeoServer连接失败", e);
        } catch (Exception e) {
            System.err.println("设置TIFF透明度失败：" + e.getMessage());
            throw new RuntimeException("设置TIFF透明度异常", e);
        }
    }
}
