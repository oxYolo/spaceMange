package com.whulri.geodata.datatool.vector;

import com.whulri.geodata.common.model.OutObject;
import com.whulri.geodata.datatool.gis.GdalHelp;
import com.whulri.geodata.datatool.gis.Rect;
import com.whulri.geodata.datatool.marshal.BOOL;
import lombok.Data;
import org.gdal.ogr.DataSource;
import org.gdal.ogr.Driver;
import org.gdal.ogr.Feature;
import org.gdal.ogr.FieldDefn;
import org.gdal.ogr.Geometry;
import org.gdal.ogr.Layer;
import org.gdal.ogr.ogr;
import org.gdal.ogr.ogrConstants;
import org.gdal.osr.SpatialReference;
import org.locationtech.jts.geom.Envelope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;

/**
 * shp读取操作类
 */
@Data
public class ShpReader {

    private Driver driver;
    private DataSource dataSource;
    /**
     * layer对象
     */
    private Layer layer;
    /**
     * 数据源路径，一般是shp文件所在目录路径
     */
    private String shpPath;
    private boolean isOpen;
    /**
     * The spatial reference ID (CRS)
     */
    private int srId = -1;
    private BOOL datalayer = BOOL.UNINT;
    /**
     * 获得图层几何类型
     */
    private int geoType = ogrConstants.wkbUnknown;

    private List<FeatAttrs> attObjList = null;
    private List<LayerField> attList = null;
    private List<Feature> featureList = null;

    /**
     * geo字段名
     */
    public String getGeoField() {
        return layer.GetGeometryColumn();
    }

    /**
     * 数据源中图层数量
     */
    public int getLayerCount() {
        return dataSource.GetLayerCount();
    }

    /**
     * 打开的图层是否有要素数据
     */
    public boolean isFeatureDataLayer() {
        if (datalayer != BOOL.UNINT) {
            return (datalayer == BOOL.TRUE);
        }

        datalayer = BOOL.FALSE;

        layer.ResetReading();

        int numFeatures = (int) layer.GetFeatureCount(1);
        if (numFeatures <= 0) {
            return false;
        }
        Feature feature = layer.GetNextFeature();
        if (feature == null) {
            return false;
        }
        Geometry geom = feature.GetGeometryRef();
        if (geom == null) {
            return false;
        }

        if (geom.GetGeometryType() != ogrConstants.wkbNone) {
            datalayer = BOOL.TRUE;
            feature.delete();
            return true;
        }

        feature.delete();

        return false;
    }

    /**
     * 获得图层的几何类型字符串描述
     */
    public String getGeoTypeString() {
        return String.valueOf(getGeoType());
    }

    public int getGeoType() {
        if (geoType != ogrConstants.wkbUnknown) {
            return geoType;
        }

        geoType = ogrConstants.wkbNone;
        layer.ResetReading();

        int numFeatures = (int) layer.GetFeatureCount(1);
        if (numFeatures <= 0) {
            return geoType;
        }
        Feature feature = layer.GetNextFeature();
        if (feature == null) {
            return geoType;
        }
        Geometry geom = feature.GetGeometryRef();
        if (geom == null) {
            return geoType;
        }

        geoType = geom.GetGeometryType();

        feature.delete();

        return geoType;
    }

    /**
     * 几何类型，点、线、面或None
     */
    public GeometryBigType getGeoBigType() {
        switch (this.getGeoTypeString()) {
            case "wkbPolygon":
            case "wkbPolygon25D":
            case "wkbMultiPolygon":
            case "wkbMultiPolygon25D":
                return GeometryBigType.Polygon;
            case "wkbLineString":
            case "wkbLineString25D":
            case "wkbMultiLineString":
            case "wkbMultiLineString25D":
                return GeometryBigType.Line;
            case "wkbPoint":
            case "wkbPoint25D":
            case "wkbMultiPoint":
            case "wkbMultiPoint25D":
                return GeometryBigType.Point;
            default:
                return GeometryBigType.None;
        }
    }

    /**
     * 图层的空间参考系
     */
    public SpatialReference getSpatialRef() {
        return layer.GetSpatialRef();
    }

    /**
     * 是否大地地理坐标系
     */
    public boolean isGeoCoordinateSystem() {
        SpatialReference spatialRef = layer.GetSpatialRef();
        if (spatialRef != null) {
            String projText = getSpatialRef().GetAttrValue("PROJCS", 0);
            if (projText == null || projText.isEmpty()) {
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    /**
     * 是否是投影坐标系
     */
    public boolean isProjected() {
        SpatialReference spatialRef = layer.GetSpatialRef();
        if (spatialRef != null) {
            String projText = getSpatialRef().GetAttrValue("PROJCS", 0);
            if (projText == null || projText.isEmpty()) {
                return false;
            } else {
                return true;
            }
        } else {
            return false;
        }
    }


    /**
     * 打开源数据指定层名的Layer
     */
    public ShpReader(String filePath, String layerName) {
        driver = ShpHelper.getShpDriver();

        setShpPath(filePath);

        open(layerName);
    }

    /**
     * 打开源数据指定层号的Layer
     */
    public ShpReader(String filePath, int layerNum) {
        driver = ShpHelper.getShpDriver();

        setShpPath(filePath);

        open(layerNum);
    }

    /**
     * 返回指定层号的Layer名称
     *
     * @param name 返回图层名称
     */
    public ShpReader(String filePath, int layerNum, OutObject<String> name) {
        this(filePath, layerNum);
        name.value = layer.GetName();
    }

    /**
     * 打开源数据的第一个图层
     */
    public ShpReader(String filePath) {
        this(filePath, 0);
    }

    /**
     * 返回源数据第一图层的名称
     *
     * @param name 返回图层名称
     */
    public ShpReader(String filePath, OutObject<String> name) {
        this(filePath, 0, name);
    }

    /**
     * 判断数据是否是有效数据
     */
    public boolean isValidDataSource() {
        layer.ResetReading();
        int featureCount = (int) layer.GetFeatureCount(1);
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

        if (pGeom.GetGeometryType() == ogrConstants.wkbNone || pGeom.GetGeometryType() == ogrConstants.wkbUnknown) {
            pFeature.delete();
            return false;
        }

        pFeature.delete();

        return true;
    }

    /**
     * 返回数据的数据类型
     */
    public String getGeomType() {
        String geomType = getGeoTypeString();
        return geomType.substring(3).toUpperCase();
    }

    /**
     * 得到数据空间参考的wkt字符串
     */
    public String getSrIdWkt() {
        String wkt = "";
        SpatialReference pSpatialReference = layer.GetSpatialRef();
        if (pSpatialReference != null) {
            OutObject<String> tempOutWkt = new OutObject<>();
            String[] strings = {tempOutWkt.value};
            pSpatialReference.ExportToWkt(strings);
            wkt = tempOutWkt.value;
        }
        return wkt;
    }

    /**
     * 得到shp文件的维度
     */
    public int getDeminsion() {
        int deminsion = -1;
        Feature pFeature = layer.GetFeature(0);
        Geometry pGeom = pFeature.GetGeometryRef();
        deminsion = pGeom.GetCoordinateDimension();
        return deminsion;
    }

    /**
     * 得到shp文件属性名和属性类型的字典表
     */
    public HashMap<String, String> getAttrList() {
        HashMap<String, String> attr = new HashMap<>();

        try {
            layer.ResetReading();

            Feature pFeature = layer.GetFeature(0);
            int pFieldCount = pFeature.GetFieldCount();

            for (int i = 0; i < pFieldCount; i++) {
                FieldDefn field = pFeature.GetFieldDefnRef(i);

                attr.put(field.GetName().strip(), String.valueOf(field.GetFieldType()).strip());
            }
        } catch (java.lang.Exception e) {
            //
        }

        return attr;
    }

    /**
     * 返回源数据feature数
     */
    public int getFeatureCount() {
        return (int) layer.GetFeatureCount(1);
    }

    /**
     * Gets the connection ID of the datasource
     */
    public String getConnectionID() {
        return String.format("Data Source=%s;Layer%s", dataSource.getName(), layer.GetName());
    }

    private void open(String layerName) {
        if (isOpen) {
            close();
        }

        dataSource = driver.Open(shpPath, 1);
        layer = dataSource.GetLayerByName(layerName);

        isOpen = true;
    }

    private void open(int layerNum) {
        if (isOpen) {
            close();
        }

//        ogrDataSource = driver.Open(ShpHelp.getGdalFileName(datasource), 1);
        dataSource = driver.Open(shpPath, 1);

        if (dataSource != null) {
            layer = dataSource.GetLayerByIndex(layerNum);

            isOpen = true;
        }
    }

    /**
     * Closes the datasource
     */
    public void close() {
        if (isOpen) {
            layer.delete();
            layer = null;

            dataSource.delete();
            dataSource = null;

            isOpen = false;
        }
    }

    /**
     * Returns true if the datasource is currently open
     */
    public boolean isOpen() {
        return isOpen;
    }

    /**
     * 获取要素列表
     */
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
        } catch (java.lang.Exception e) {
            return null;
        }
    }

    /**
     * 返回所有要素的属性信息
     *
     * @return 属性对象列表
     * 大shp文件导致内存溢出时会返回null
     */
    public List<FeatAttrs> getAllFeatAttrs() {
        try {
            if (attObjList != null) {
                return attObjList;
            }

            attObjList = new ArrayList<>();

            layer.ResetReading();

            Feature pFeature = null;
            while ((pFeature = layer.GetNextFeature()) != null) {
                FeatAttrs aObj = new FeatAttrs();
                HashMap<String, String> dic = new HashMap<>();

                int fieldCount = pFeature.GetFieldCount();
                for (int i = 0; i < fieldCount; i++) {
                    dic.put(pFeature.GetFieldDefnRef(i).GetName().strip(), pFeature.GetFieldAsString(i).strip());
                }

                aObj.setOid((int) pFeature.GetFID());
                aObj.setAttrValue(dic);
                aObj.setRefFeature(pFeature);

                attObjList.add(aObj);
            }

            return attObjList;
        } catch (java.lang.Exception e) {
            return null;
        }
    }

    /**
     * 获取某个属性字段的唯一值列表及对应的个数
     *
     * @param features 字段列表
     * @param fldName  属性字段名称
     */
    public Hashtable getDistinctFieldValues(List<FeatAttrs> features, String fldName) {
        try {
            if (features == null || features.size() < 1) {
                return null;
            }

            Hashtable table = new Hashtable();

            for (int i = 0; i < features.size(); i++) {
                Object val = features.get(i).getAttrValue().get(fldName);

                if (table.containsKey(val) == false) {
                    table.put(val, 1);
                } else {
                    table.put(val, (int) table.get(val) + 1);
                }
            }

            return table;
        } catch (java.lang.Exception e) {
            return null;
        }
    }

    /**
     * 获取某个属性字段的唯一值列表
     *
     * @param fldName 属性字段名称
     * @return 返回唯一值列表
     */
    public List<String> getDistinctFieldValues(String fldName) {
        List<String> vals = new ArrayList<>();

        layer.ResetReading();

        Feature pFeature = layer.GetNextFeature();
        if (pFeature == null) {
            return vals;
        }

        int index = pFeature.GetFieldIndex(fldName); //字段索引

        if (index == -1) {
            throw new RuntimeException("字段未找到" + fldName);
        }

        do {
            vals.add(pFeature.GetFieldAsString(index));
            pFeature.delete();
        } while ((pFeature = layer.GetNextFeature()) != null);

        Collections.sort(vals);
        vals = ShpHelper.unique(vals);

        return vals;
    }

    /**
     * 获取某个属性字段的所有值
     *
     * @param fldName 属性字段名称
     * @return 返回值列表
     */
    public List<String> getFieldValues(String fldName) {
        List<Integer> fids = null;
        OutObject<List<Integer>> tempOutFids = new OutObject<>();
        var tempVar = getFieldValues(fldName, tempOutFids);
        fids = tempOutFids.value;
        return tempVar;
    }

    /**
     * 获取某个属性字段的所有值
     *
     * @param fldName 属性字段名称
     * @param fids    对应的FID列表，和返回值列表元素顺序对应
     * @return 返回值列表
     */
    public List<String> getFieldValues(String fldName, OutObject<List<Integer>> fids) {
        List<String> values = new ArrayList<>();
        fids.value = new ArrayList<>();

        layer.ResetReading();

        Feature pFeature = layer.GetNextFeature();
        if (pFeature == null) {
            return values;
        }

        int index = pFeature.GetFieldIndex(fldName); //字段索引

        if (index == -1) {
            pFeature.delete();
            throw new RuntimeException("字段未找到" + fldName);
        }

        do {
            values.add(pFeature.GetFieldAsString(index));
            fids.value.add((int) pFeature.GetFID());
            pFeature.delete();
        } while ((pFeature = layer.GetNextFeature()) != null);

        return values;
    }

    /**
     * 返回属性表字段
     *
     * @return 属性表字段列表
     */
    public List<LayerField> getAttributes() {
        try {
            if (attList != null) {
                return attList;
            }

            attList = new ArrayList<>();

            layer.ResetReading();

            Feature pFeature = null;
            if ((pFeature = layer.GetNextFeature()) != null) {
                int fieldCount = pFeature.GetFieldCount();
                for (int i = 0; i < fieldCount; i++) {
                    LayerField attr = new LayerField();

                    FieldDefn field = pFeature.GetFieldDefnRef(i);

                    attr.setFieldName(field.GetName().strip());
                    attr.setFieldType(String.valueOf(field.GetFieldType()));

                    attList.add(attr);
                }

                pFeature.delete();
            }

            return attList;
        } catch (java.lang.Exception e) {
            return null;
        }
    }

    /**
     * 查找具有某个特定字段值的要素列表
     *
     * @param fldName 要查找的字段名
     * @param val     要查找的字段值
     * @return 返回符合条件的要素FID列表
     */
    public List<Integer> getFeatHasVal(String fldName, String val) {
        List<Integer> indexs = new ArrayList<>();

        layer.ResetReading();
        Feature pFeature = layer.GetNextFeature();
        if (pFeature == null) {
            return indexs;
        }

        int index = pFeature.GetFieldIndex(fldName); //字段索引

        if (index == -1) {
            pFeature.delete();
            throw new RuntimeException("字段未找到" + fldName);
        }

        do {
            if (val.equals(pFeature.GetFieldAsString(index))) {
                indexs.add((int) pFeature.GetFID());
            }

            pFeature.delete();
        } while ((pFeature = layer.GetNextFeature()) != null);

        return indexs;
    }

    /**
     * 获取shp里面第一个面的外接矩形
     *
     * @param cutShp 路径
     */
    public static Rect getFirstGeoExtent(String cutShp) {
        Driver pDriver = ShpHelper.getShpDriver();
        DataSource cutDs = pDriver.Open(cutShp, 0);
        Layer cutLyr = cutDs.GetLayerByIndex(0);
        if (cutLyr.GetFeatureCount(0) == 0) {
            throw new IllegalStateException("layer is empty");
        }

        Feature cutFea = cutLyr.GetFeature(0);
        Geometry cutGeo = cutFea.GetGeometryRef();
        Envelope env = new Envelope();
        double[] arr = {env.getMinX(), env.getMaxX(), env.getMinY(), env.getMaxY()};
        cutGeo.GetEnvelope(arr);

        Rect rc = new Rect();
        rc.minx = env.getMinX();
        rc.maxx = env.getMaxX();
        rc.miny = env.getMinY();
        rc.maxy = env.getMaxY();

        cutGeo.delete();
        cutFea.delete();
        cutLyr.delete();
        cutDs.delete();

        return rc;
    }

    /**
     * 获取shp里面第一个几何
     *
     * @param cutShp 路径
     * @return 返回第一个几何，由调用方Dispose
     */
    public static Geometry getFirstGeo(String cutShp) {
        GdalHelp.initGdal();

        Driver pDriver = ogr.GetDriverByName("ESRI Shapefile");
        DataSource cutDs = pDriver.Open(cutShp, 0);
        Layer cutLyr = cutDs.GetLayerByIndex(0);
        if (cutLyr.GetFeatureCount(0) == 0) {
            return null;
        }

        Feature cutFea = cutLyr.GetFeature(0);
        Geometry cutGeo = cutFea.GetGeometryRef();
        Geometry result = cutGeo.Clone();

        cutGeo.delete();
        cutFea.delete();
        cutLyr.delete();
        cutDs.delete();

        return result;
    }
}