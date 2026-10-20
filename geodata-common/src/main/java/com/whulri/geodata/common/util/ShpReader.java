package com.whulri.geodata.common.util;

import com.whulri.geodata.common.enums.FieldTypeEnum;
import com.whulri.geodata.common.gdal.pojo.SpatialAttributeObj;
import com.whulri.geodata.common.vo.AttributeModel;
import lombok.Data;
import org.gdal.gdal.gdal;
import org.gdal.ogr.*;
import org.gdal.osr.SpatialReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class ShpReader {
    private static final Logger log = LoggerFactory.getLogger(ShpReader.class);
    private DataSource dataSource;
    private String shpFilePath;
    private List<Feature> featureList;


    /**
     * layer对象
     */
    private Layer layer;

    /**
     * 获得图层几何类型
     */
    private int geoType = ogrConstants.wkbUnknown;

    public ShpReader(String filePath) {
//        // 1. 设置 PROJ_LIB（必须在 RegisterAll 之前！）
//        String os = System.getProperty("os.name").toLowerCase();
//        if (os.contains("win")) {
//            System.setProperty("PROJ_LIB", "C:\\Program Files\\Gdal\\release-1916-x64-gdal-3-8-3-mapserver-8-0-1\\proj");
//        } else {
//            System.setProperty("PROJ_LIB", "/usr/share/proj");
//        }
//		dataSource = dataSource.GetDriver().Open(filePath);
//		this.shpFilePath = filePath;
        // 智能编码处理：优先查找同名 .cpg 文件
        String cpgPath = filePath.substring(0, filePath.lastIndexOf(".")) + ".cpg";
        java.io.File cpgFile = new java.io.File(cpgPath);

        if (cpgFile.exists()) {
            // 读取 cpg 文件内容，根据内容设置编码
            try {
                String cpgContent = new String(Files.readAllBytes(cpgFile.toPath())).trim();
                if (cpgContent.toLowerCase().contains("utf-8")) {
                    gdal.SetConfigOption("SHAPE_ENCODING", "UTF-8");
                } else {
                    gdal.SetConfigOption("SHAPE_ENCODING", cpgContent);
                }
            } catch (Exception e) {
                gdal.SetConfigOption("SHAPE_ENCODING", "CP936");
            }
        } else {
            gdal.SetConfigOption("SHAPE_ENCODING", "CP936");
        }
        
        ogr.RegisterAll(); // 确保驱动注册（可选，但推荐）
        this.shpFilePath = filePath;
        this.dataSource = ogr.Open(filePath, 0);
        if (this.dataSource == null) {
            throw new IllegalArgumentException("无法打开 SHP 文件: " + filePath);
        }
        this.layer = dataSource.GetLayerByIndex(0);
        if (this.layer == null) {
            throw new IllegalArgumentException("SHP 文件中没有图层");
        }
    }
    //String correctedName = new String(fieldName.getBytes(StandardCharsets.ISO_8859_1), "GBK");
    public Map<String, String> getAttributeType() {
        Map<String, String> attributeTypes = new HashMap<>();
        for (int i = 0; i < dataSource.GetLayer(0).GetLayerDefn().GetFieldCount(); i++) {
            String fieldName = dataSource.GetLayer(0).GetLayerDefn().GetFieldDefn(i).GetNameRef();
//            // 处理字段名：UTF-8编码下dbf字段名最多10字节，约3个中文，超长需要截断
//            fieldName = truncateFieldName(fieldName);
            try {
                // GDAL 已经处理了编码，直接使用即可
                String correctedName = fieldName;
                // 只有当检测到明显的乱码时（如全是问号或乱码字符），才尝试手动转码，或者完全移除手动转码逻辑
                // String correctedName = new String(fieldName.getBytes(StandardCharsets.ISO_8859_1), "GBK");
                String typeName = dataSource.GetLayer(0).GetLayerDefn().GetFieldDefn(i).GetTypeName();
                attributeTypes.put(correctedName, typeName);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return attributeTypes;
    }

    public List<SpatialAttributeObj> getAllSpatialAttributeObj() {
        try {
            List<SpatialAttributeObj> lstSAObj = new ArrayList<>();
            layer.ResetReading(); // 重置图层读取位置

            Feature pFeature;
            while ((pFeature = layer.GetNextFeature()) != null) {
                SpatialAttributeObj saObj = new SpatialAttributeObj();
                saObj.setOid((int) pFeature.GetFID()); // 获取要素的唯一标识符
                saObj.setSpatialValue(pFeature); // 设置空间值

                HashMap<String, String> dic = new HashMap<>();
                int fieldCount = pFeature.GetFieldCount(); // 获取字段数量
                for (int i = 0; i < fieldCount; i++) {
                    String key = pFeature.GetFieldDefnRef(i).GetName().trim(); // 字段名称
                    String value = pFeature.GetFieldAsString(i).trim(); // 字段值
                    dic.put(key, value);
                }
                saObj.setAttriValue(dic); // 设置属性值

                lstSAObj.add(saObj); // 添加到结果列表
            }
            return lstSAObj;
        } catch (Exception e) {
            log.warn("读取 Shp 空间属性失败: {}", e.getMessage(), e);
            return null;
        }
    }

    public Long getFeatureCount() {
        return dataSource.GetLayer(0).GetFeatureCount();
    }

    public Map<String, String> getOneFeatureAttribute(int index, List<String> attributes) {
        Feature feature = dataSource.GetLayer(0).GetFeature(index);
        Map<String, String> featureAttributes = new HashMap<>();
        for (String attrName : attributes) {
            featureAttributes.put(attrName, feature.GetFieldAsString(attrName));
        }
        return featureAttributes;
    }


    public String getOneFeatureGeomWkt(int index) {
        Feature feature = dataSource.GetLayer(0).GetFeature(index);
        Geometry geometry = feature.GetGeometryRef();
        if (geometry != null && !(geometry instanceof Geometry)) {
            return geometry.ExportToWkt();
        }
        return null;
    }

    public String getGeomType() {
        int geomTypeCode = getGeoType();
        return getSimpleGeometryTypeName(geomTypeCode);
    }

    /**
     * 获得图层的几何类型字符串描述
     */
    public int getGeoType() {
//        if (geoType != ogrConstants.wkbUnknown) {
//            return geoType;
//        }

        layer.ResetReading();

        Feature feature = layer.GetNextFeature();
        if (feature == null) {
            geoType = ogrConstants.wkbNone;
            return geoType;
        }

        try {
            Geometry geom = feature.GetGeometryRef();
            if (geom == null) {
                return geoType;
            }
            geoType = geom.GetGeometryType();
            return geoType;
        } finally {
            feature.delete(); // 确保无论是否发生异常都释放 Feature
        }
    }

    private static String getSimpleGeometryTypeName(int wkbType) {
        switch (wkbType) {
            case ogrConstants.wkbPoint:
            case ogrConstants.wkbPoint25D:
            case ogrConstants.wkbPointM:
//            case ogrConstants.wkbPointZ:
            case ogrConstants.wkbPointZM:
                return "POINT";

            case ogrConstants.wkbLineString:
            case ogrConstants.wkbLineString25D:
            case ogrConstants.wkbLineStringM:
//            case ogrConstants.wkbLineStringZ:
            case ogrConstants.wkbLineStringZM:
                return "LINESTRING";

            case ogrConstants.wkbPolygon:
            case ogrConstants.wkbPolygon25D:
            case ogrConstants.wkbPolygonM:
//            case ogrConstants.wkbpolygon:
            case ogrConstants.wkbPolygonZM:
                return "POLYGON";

            case ogrConstants.wkbMultiPoint:
            case ogrConstants.wkbMultiPoint25D:
                return "MULTIPOINT";

            case ogrConstants.wkbMultiLineString:
            case ogrConstants.wkbMultiLineString25D:
                return "MULTILINESTRING";

            case ogrConstants.wkbMultiPolygon:
            case ogrConstants.wkbMultiPolygon25D:
                return "MULTIPOLYGON";

            case ogrConstants.wkbGeometryCollection:
            case ogrConstants.wkbGeometryCollection25D:
                return "GEOMETRYCOLLECTION";

            default:
                return "UNKNOWN";
        }
    }

    /**
     * 获取一个要素的所有属性名和属性值的字典表
     *
     * @param featureID 要素的 ID 号
     * @return 包含字段名和字段值的 Map
     */
    public Map<String, String> getOneFeatureAttribute(int featureID) {
        Map<String, String> attr = new HashMap<>();
        Feature feature = layer.GetFeature(featureID);
        if (feature == null) {
            return attr; // 返回空的 Map
        }

        int fieldCount = feature.GetFieldCount();
        for (int i = 0; i < fieldCount; i++) {
            String fieldName = feature.GetFieldDefnRef(i).GetName().trim();
            String fieldValue = feature.GetFieldAsString(i).trim();
            attr.put(fieldName, fieldValue);
        }

        return attr;
    }


    /**
     * 获取一个要素的所有属性名和属性值的长度、步长、类型信息
     *
     * @param featureID 要素的 ID 号
     * @return 属性模型列表
     */
    public List<AttributeModel> getOneFeatureAttributeModel(int featureID) {
        List<AttributeModel> lstAttr = new ArrayList<>();
        Feature feature = layer.GetFeature(featureID);
        if (feature == null) {
            return lstAttr; // 返回空的列表
        }

        int fieldCount = feature.GetFieldCount();
        for (int i = 0; i < fieldCount; i++) {
            AttributeModel attr = new AttributeModel();
            String fieldName = feature.GetFieldDefnRef(i).GetName();
//            log.info("Raw field name [{}]: '{}'", i, fieldName);
            try {
//                // 处理字段名：UTF-8编码下dbf字段名最多10字节，约3个中文，超长需要截断
//                String correctedName =  truncateFieldName(fieldName);
                String correctedName =  fieldName;
                // String correctedName = new String(fieldName.getBytes(StandardCharsets.ISO_8859_1), "GBK");
//                log.info("correctedName:{}", correctedName);
                attr.setAttributeName(correctedName);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            attr.setAttributeType(FieldTypeEnum.fromGdalValue(feature.GetFieldDefnRef(i).GetFieldType()));
            attr.setAttributeWidth(Integer.valueOf(String.valueOf(feature.GetFieldDefnRef(i).GetWidth())));
            attr.setAttributePrecision(Integer.valueOf(String.valueOf(feature.GetFieldDefnRef(i).GetPrecision())));

            lstAttr.add(attr);
        }

        return lstAttr;
    }


    public List<Feature> getAllFeatures() {
        try {
            if (featureList == null) {
                featureList = new ArrayList<>();

                layer.ResetReading();

                Feature pFeature = null;
                while ((pFeature = layer.GetNextFeature()) != null) {
                    featureList.add(pFeature);
                }
            }

            return featureList;
        } catch (Exception e) {
            return null;
        }
    }

    // 判断数据是否是有效数据
    public boolean isValidDataSource() {
        layer.ResetReading();
        long featureCount = layer.GetFeatureCount(1);
        if (featureCount <= 0) {
            return false;
        }
        Feature pFeature = layer.GetNextFeature();
        if (pFeature == null) {
            return false;
        }
        Geometry pGeom = pFeature.GetGeometryRef();
        if (pGeom == null) {
            return false;
        }
        int geomType = pGeom.GetGeometryType();
        if (geomType == ogrConstants.wkbUnknown || geomType == ogrConstants.wkbNone) {
            return false; // 无效几何类型
        }
        return true;
    }


    public Map<String, Double> getLayerBBox() {
        Map<String, Double> dic = new HashMap<>();
        try {
            double[] extent = new double[4];
            int result = layer.GetExtent(extent, 1);
            if (result == 0) {
                dic.put("MinX", extent[0]);
                dic.put("MaxX", extent[1]);
                dic.put("MinY", extent[2]);
                dic.put("MaxY", extent[3]);
            } else {
                log.warn("GetExtent failed with code: {}", result);
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return dic;
    }


    /**
     * 得到数据空间参考的wkt字符串
     *
     * @return WKT字符串
     */
    public String getSridWkt() {
        try {
            SpatialReference sr = layer.GetSpatialRef();
            if (sr == null) {
                log.warn("图层无空间参考信息");
                return "";
            }
            return sr.ExportToWkt();
        } catch (Exception e) {
            log.error("导出 WKT 失败", e);
            return "";
        }
    }

    public void close() {
        try {
            if (featureList != null) {
                for (Feature f : featureList) {
                    if (f != null) f.delete();
                }
                featureList.clear();
            }
        } catch (Exception e) {
            log.warn("释放 Feature 失败", e);
        }

        try {
            if (layer != null) layer.delete();
            if (dataSource != null) dataSource.delete();
        } catch (Exception e) {
            log.warn("释放 DataSource/Layer 失败", e);
        }
    }

    public int getSrid() {
        SpatialReference sr = layer.GetSpatialRef();
        if (sr == null) {
            log.warn("图层无空间参考系统");
            return -1;
        }

        try {
            // Step 1: 验证 SRS 是否可导出 WKT
            String wkt;
            try {
                wkt = sr.ExportToWkt();
            } catch (Exception ex) {
                log.error("SRS 无法导出 WKT，可能已损坏", ex);
                return -1;
            }

            // Step 2: 处理 ESRI 格式（Shapefile 常见）
            if (wkt.contains("GCS_") || wkt.contains("D_")) {
                try {
                    sr.MorphFromESRI(); // 转为 OGC 标准格式
                } catch (Exception ex) {
                    log.warn("MorphFromESRI 失败，继续尝试识别", ex);
                }
            }

            // Step 3: 尝试自动识别 EPSG
            int autoResult = sr.AutoIdentifyEPSG(); // 0 = success
            String authCode = sr.GetAuthorityCode(null);

            if (authCode != null && !authCode.trim().isEmpty()) {
                try {
                    int epsg = Integer.parseInt(authCode.trim());
                    log.debug("成功识别 EPSG: {}", epsg);
                    return epsg;
                } catch (NumberFormatException e) {
                    log.warn("EPSG 编码格式无效: '{}'", authCode);
                }
            }

            // Step 4: 识别失败，但可能是已知标准坐标系（fallback）
            if (autoResult != 0) {
                log.debug("AutoIdentifyEPSG 失败 (code={}), 尝试通过 WKT 内容推断", autoResult);
            }

            // Fallback: 检查是否为常见坐标系（无需依赖 PROJ）
            if (wkt.contains("WGS 84") && (wkt.contains("Greenwich") || wkt.contains("degree"))) {
                log.info("检测到 WGS84 特征，假设为 EPSG:4326");
                return 4326;
            }
            if (wkt.contains("WGS_1984_Web_Mercator") || wkt.contains("Popular Visualisation")) {
                log.info("检测到 Web Mercator 特征，假设为 EPSG:3857");
                return 3857;
            }

            log.warn("无法识别 SRS，WKT 片段: {}", wkt.length() > 200 ? wkt.substring(0, 200) + "..." : wkt);
            return -1;

        } catch (Exception e) {
            log.error("解析空间参考系统时发生异常", e);
            return -1;
        }
    }

    /**
     * 处理字段名，截断超长部分
     * dbf字段名最多10字节，UTF-8编码下约3个中文，超长会导致乱码
     */
    private String truncateFieldName(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return fieldName;
        }
        try {
            byte[] bytes = fieldName.getBytes("UTF-8");
            if (bytes.length > 10) {
                // 截断到10字节
                byte[] truncated = new byte[10];
                System.arraycopy(bytes, 0, truncated, 0, 10);
                String result = new String(truncated, "UTF-8");
                // 确保不截断到半个中文字符
                while (result.length() > 0) {
                    try {
                        byte[] test = result.getBytes("UTF-8");
                        if (test.length <= 10) {
                            break;
                        }
                        result = result.substring(0, result.length() - 1);
                    } catch (Exception e) {
                        result = result.substring(0, result.length() - 1);
                    }
                }
                return result;
            }
        } catch (Exception e) {
            // 如果处理失败，返回原字段名
        }
        return fieldName;
    }
}
