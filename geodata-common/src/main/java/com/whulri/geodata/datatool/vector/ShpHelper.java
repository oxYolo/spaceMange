package com.whulri.geodata.datatool.vector;

import cn.hutool.core.io.FileUtil;
import com.whulri.geodata.common.model.Point3D;
import com.whulri.geodata.common.thread.ExceptionProcesser;
import com.whulri.geodata.datatool.gis.CoordTransform;
import com.whulri.geodata.datatool.gis.GdalHelp;
import lombok.extern.slf4j.Slf4j;
import org.gdal.gdal.Band;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.gdal;
import org.gdal.ogr.Driver;
import org.gdal.ogr.Layer;
import org.gdal.ogr.ogr;
import org.gdal.osr.SpatialReference;
import org.gdal.osr.osr;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * shp文件辅助类
 */
@Slf4j
public class ShpHelper {

    /**
     * GDAL读写shp的驱动,会调用InitShp
     */
    public static Driver getShpDriver() {
        GdalHelp.initGdal();

        //调用对Shape文件读写的Driver接口
        return ogr.GetDriverByName("ESRI Shapefile");
    }

    /**
     * 范围转6元素
     *
     * @param extent
     * @param xSize
     * @param ySize
     * @return
     */
    public static double[] extent2GeoTransform(double[] extent, int xSize, int ySize) {
        double[] geoTransform = new double[6];
        geoTransform[0] = extent[0];
        geoTransform[1] = (extent[1] - extent[0]) / xSize;
        geoTransform[2] = 0;
        geoTransform[3] = extent[3];
        geoTransform[4] = 0;
        geoTransform[5] = (extent[2] - extent[3]) / ySize;
        return geoTransform;
    }

    public static void shpToRaster(Dataset dstDs, Layer layer) {
        shpToRaster(dstDs, layer, -9999);
    }

    /**
     * 矢量数据转栅格数据
     *
     * @param dstDs     栅格数据
     * @param layer     矢量数据图层
     * @param noData    无效值，不传则为-9999
     */
    public static void shpToRaster(Dataset dstDs, Layer layer, double noData) {
        try {

            double[] layerExtent = transformToWGS84(layer);
            //计算GeoTransform
            int rasterX = dstDs.GetRasterXSize();
            int rasterY = dstDs.getRasterYSize();
            double[] geoTransform = extent2GeoTransform(layerExtent, rasterX, rasterY);

            dstDs.SetProjection(osr.SRS_WKT_WGS84_LAT_LONG);
            dstDs.SetGeoTransform(geoTransform);
            Band dstBand = dstDs.GetRasterBand(1);
            dstBand.SetNoDataValue(noData);
            dstBand.FlushCache();

            gdal.RasterizeLayer(dstDs, new int[]{1}, layer);
        } catch (Exception e) {
            log.error("shpToRaster failed+" + ExceptionProcesser.getInfo(e));
        }
    }

    private static double[] transformToWGS84(Layer layer) {
        double[] layerExtent = layer.GetExtent();

        String wkt = "";
        SpatialReference spatialReference = layer.GetSpatialRef();
        if (null != spatialReference) {
            wkt = spatialReference.ExportToWkt();
        }

        //判断是不是WGS84坐标
        if (layerExtent[0] >= -180 && layerExtent[0] <= 180 && layerExtent[2] >= -90 && layerExtent[2] <= 90) {
            if (wkt.startsWith("\"GEOGCS[\\\"WGS 84\\\",")) {
                return layerExtent;
            }
        }
        Point3D ltPt = new Point3D(layerExtent[0], layerExtent[3], 0);
        Point3D rbPt = new Point3D(layerExtent[1], layerExtent[2], 0);
        var ltPtTran = CoordTransform.toWGS84(ltPt, wkt);
        var rbPtTran = CoordTransform.toWGS84(rbPt, wkt);

        layerExtent[0] = ltPtTran.getX();
        layerExtent[1] = rbPtTran.getX();
        layerExtent[2] = rbPtTran.getY();
        layerExtent[3] = ltPtTran.getY();

        return layerExtent;
    }

    /**
     * 删除指定shp文件，包括相关文件
     *
     * @param folder    shp所在目录路径
     * @param layerName 图层名，不带参数
     */
    public static boolean removeShpFile(String folder, String layerName) {
        String temp = Paths.get(folder).resolve(layerName).toString();

        String dbf = temp + ".dbf";
        String prj = temp + ".prj";
        String shp = temp + ".shp";
        String shx = temp + ".shx";

        if (!FileUtil.exist(shp)) return true;

        try {
            FileUtil.del(shp);
            FileUtil.del(dbf);
            FileUtil.del(shx);
            FileUtil.del(prj);

            return true;
        } catch (RuntimeException ex) {
            System.out.println("删除文件失败:" + ex.getMessage());
            return false;
        }
    }

    /**
     * 删除指定shp文件，包括相关文件
     *
     * @param shpFilePath 要删除的shp文件的完整路径
     */
    public static boolean removeShpFile(String shpFilePath) {
        if (!(new File(shpFilePath)).isFile()) {
            return true;
        }
        String s = Paths.get(shpFilePath).getFileName().toString();
        String substring = s.substring(0, s.lastIndexOf("."));
        return removeShpFile((new File(shpFilePath)).getParent(), substring);
    }

    /**
     * 将默认gbk编码的文件名、文件路径转成gdal要求的u8编码
     */
    public static String getU8FileName(String gbkFilename) {
        String gbk = new String(gbkFilename.getBytes(), Charset.forName("GBK"));
        byte[] utf = gbk.getBytes(StandardCharsets.UTF_8);
        return new String(utf, StandardCharsets.UTF_8);
    }

    /**
     * 直接设置shp文件的投影信息
     */
    public static boolean setSpatialRef(String shpFilepath, String wkt) {
        try {
            //写prj,shp文件的投影信息记录在.prj文件里
            String prjfile = replaceExtension(shpFilepath, ".prj");

            FileOutputStream w = new FileOutputStream(prjfile);
            byte[] buffer = wkt.getBytes(StandardCharsets.UTF_8);
            w.write(buffer, 0, buffer.length);
            w.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String replaceExtension(String oldFilename, String newExtent) {
        int dotidx = oldFilename.lastIndexOf('.');
        if (dotidx == -1) {
            return oldFilename;
        }
        return oldFilename.substring(0, dotidx) + "." + newExtent.replaceAll("\\.+", "");
    }

    /**
     * 消除对.net4.0里Linq的依赖,列表值唯一化
     *
     * @param list 值必须是已序的
     */
    public static <T extends Comparable> List<T> unique(List<T> list) {
        if (list == null || list.isEmpty()) {
            return list;
        }

        ArrayList<T> result = new ArrayList<T>();

        T v = list.get(0);
        result.add(v);
        for (T t : list) {
            if (t.compareTo(v) != 0) {
                result.add(t);
                v = t;
            }
        }

        return result;
    }
}