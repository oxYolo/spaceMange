package com.whulri.geodata.datatool.gis;

import com.whulri.geodata.common.Environment;
import com.whulri.geodata.common.callback.ExeOutputCallback;
import com.whulri.geodata.common.io.Path;
import com.whulri.geodata.common.model.Point3D;
import com.whulri.geodata.common.utils.Marshal;
import org.gdal.osr.CoordinateTransformation;
import org.gdal.osr.SpatialReference;
import org.gdal.osr.osr;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class CoordTransform {
//    public static final String WGS84_OGC_WKT = osr.SRS_WKT_WGS84_LAT_LONG;

    /**
     * 坐标转换
     *
     * @param srcSR 原坐标系
     * @param dstSR 目标坐标系
     * @param point 需要转换的点
     * @return
     */
    public static Point3D transform(SpatialReference srcSR, SpatialReference dstSR, Point3D point) {
        GdalHelp.proj7Config();

        //proj6以后，输入输出的XY轴顺序会遵循wkt中定义的顺序，设置该策略后lon->X,lat->Y
        srcSR.SetAxisMappingStrategy(osr.OAMS_TRADITIONAL_GIS_ORDER);
        dstSR.SetAxisMappingStrategy(osr.OAMS_TRADITIONAL_GIS_ORDER);

        CoordinateTransformation ct = new CoordinateTransformation(srcSR, dstSR);
        var transformPT = ct.TransformPoint(point.getX(), point.getY(), point.getZ());
        return new Point3D(transformPT[0], transformPT[1], transformPT[2]);
    }

    /**
     * 批量坐标转换
     *
     * @param srcSR
     * @param dstSR
     * @param list
     * @return
     */
    public static List<Point3D> transformPoints(SpatialReference srcSR, SpatialReference dstSR, List<Point3D> list) {
        GdalHelp.proj7Config();

        //proj6以后，输入输出的XY轴顺序会遵循wkt中定义的顺序，设置该策略后lon->X,lat->Y
        srcSR.SetAxisMappingStrategy(osr.OAMS_TRADITIONAL_GIS_ORDER);
        dstSR.SetAxisMappingStrategy(osr.OAMS_TRADITIONAL_GIS_ORDER);

        CoordinateTransformation ct = new CoordinateTransformation(srcSR, dstSR);

        List<Point3D> transList = new ArrayList<>();
        list.forEach(l -> {
            var temp = ct.TransformPoint(l.getX(), l.getY(), l.getZ());
            Point3D tranPt = new Point3D(temp[0], temp[1], temp[2]);
            transList.add(tranPt);
        });
        return transList;
    }

    /**
     * 经纬坐标转UTM投影坐标
     *
     * @param geoPoint 经纬高
     * @return 投影坐标
     */
    public static Point3D wgs84ToUTM(Point3D geoPoint) {
        //计算度带
        int zone = (int) geoPoint.getX() / 6 + 31;
        int north = geoPoint.getY() > 0 ? 1 : 0;

        SpatialReference srcSR = new SpatialReference();
        srcSR.SetWellKnownGeogCS("WGS84");
        SpatialReference dstSR = new SpatialReference();
        dstSR.SetUTM(zone, north);

        return transform(srcSR, dstSR, geoPoint);
    }

    /**
     * UTM投影坐标转经纬坐标
     *
     * @param point 投影坐标
     * @param utm   utm字符串 TUM zone 48N
     * @return 经纬高
     */
    public static Point3D utmToWGS84(Point3D point, String utm) {
        String[] str = utm.split(" ");
        int zone = Integer.parseInt(str[2].substring(0, str[2].length() - 1));
        int north = str[2].endsWith("N") ? 1 : 0;

        SpatialReference srcSR = new SpatialReference();
        srcSR.SetUTM(zone, north);
        SpatialReference dstSR = new SpatialReference();
        dstSR.SetWellKnownGeogCS("WGS84");

        return transform(srcSR, dstSR, point);
    }

    /**
     * 转WGS84坐标
     *
     * @param point 需要转换的点
     * @param wkt   原坐标系wkt
     * @return
     */
    public static Point3D toWGS84(Point3D point, String wkt) {
        SpatialReference srcSR = new SpatialReference(wkt);
        SpatialReference dstSR = new SpatialReference();
        dstSR.SetWellKnownGeogCS("WGS84");

        return transform(srcSR, dstSR, point);
    }

    /// <summary>
    /// 影像投影坐标转地理坐标系
    /// </summary>
    /// <param name="cs"></param>
    /// <param name="projpath"></param>
    /// <param name="geopath"></param>
    /// <param name="errCallback">错误信息回调</param>
    /// <param name="infoCallback">正常信息回调函数</param>
    /// <returns></returns>
    static public boolean projToGeo(CoordSysType.CoordSystem cs, String projpath, String geopath) {
        return projToGeo(cs, projpath, geopath, "GTiff");
    }

    /// <summary>
    /// 影像投影坐标转地理坐标系
    /// </summary>
    /// <param name="cs"></param>
    /// <param name="projpath"></param>
    /// <param name="geopath"></param>
    /// <param name="outputformat">输出文件格式，使用gdal支持的短格式名，如GTiff/HDF4Image</param>
    /// <param name="errCallback">错误信息回调</param>
    /// <param name="infoCallback">正常信息回调函数</param>
    /// <returns></returns>
    static public boolean projToGeo(CoordSysType.CoordSystem cs, String projpath, String geopath, String outputformat) {

        //设置插件目录和数据目录
        String bindir = Path.combine(Environment.getHomeDir(), "apps");
//        String datadir = GdalHelp.getGdalDataDir();
//        String plugindir = GdalHelp.getGdalPluginDir();

        String arg = String.format("-dstnodata 255 -multi -overwrite --config GDAL_FILENAME_IS_UTF8 NO "
                        + " --config NUM_THREADS ALL_CPUS  -of %s "
                        + " -s_srs %s -t_srs %s \"%s\" \"%s\"",
                outputformat, cs.getWKT(), cs.getGeoCoordSystem(), projpath, geopath);
        String exePath = Path.combine(bindir, "gdalwarp.exe");
        var result = Marshal.runWithHiddenMode(exePath, arg, new ExeOutputCallback() {
            @Override
            public void infoOutput(InputStream is) {
                Marshal.outLog(is, true);
            }

            @Override
            public void errorOutput(InputStream es) {
                Marshal.outLog(es, true);
            }
        });
        return result;
    }
}
