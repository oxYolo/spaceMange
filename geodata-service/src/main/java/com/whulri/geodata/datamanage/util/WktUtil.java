package com.whulri.geodata.datamanage.util;

import java.util.ArrayList;
import java.util.List;

public class WktUtil {

    // WGS84椭球体参数
    private static final double EARTH_RADIUS_KM = 6371.0088;//地球半径 Km
    private static final double DEG_TO_RAD = Math.PI / 180.0;
    private static final double RAD_TO_DEG = 180.0 / Math.PI;

    public static String createCircleWKT(double centerLon, double centerLat,
                                         double radiusKm, int segments, int precision) {
        // 参数校验
        validateCoordinates(centerLon, centerLat);
        if (radiusKm <= 0) {
            throw new IllegalArgumentException("半径必须大于0: " + radiusKm);
        }
        if (segments < 8) {
            segments = 36; // 默认36段
        }
        precision = Math.max(2, Math.min(10, precision)); // 限制2-10位精度

        // 生成圆形点
        List<double[]> circlePoints = generateGeoCirclePoints(
                centerLon, centerLat, radiusKm, segments);

        // 构建WKT
        return buildWKTPolygon(circlePoints, precision);
    }

    /**
     * 坐标验证
     */
    private static void validateCoordinates(double lon, double lat) {
        if (lon < -180 || lon > 180) {
            throw new IllegalArgumentException("经度范围错误 [-180, 180]: " + lon);
        }
        if (lat < -90 || lat > 90) {
            throw new IllegalArgumentException("纬度范围错误 [-90, 90]: " + lat);
        }
    }

    /**
     * 生成地理圆形点集
     */
    private static List<double[]> generateGeoCirclePoints(double lon, double lat,
                                                          double radiusKm, int segments) {
        List<double[]> points = new ArrayList<>(segments + 1);

        // 中心点转换为弧度
        double lonRad = lon * DEG_TO_RAD;
        double latRad = lat * DEG_TO_RAD;

        // 角距离
        double angularDistance = radiusKm / EARTH_RADIUS_KM;
        double cosAngularDistance = Math.cos(angularDistance);
        double sinAngularDistance = Math.sin(angularDistance);

        // 预计算中心点的三角函数
        double cosLat = Math.cos(latRad);
        double sinLat = Math.sin(latRad);

        // 生成圆上的点
        double angleStep = 2 * Math.PI / segments;

        for (int i = 0; i <= segments; i++) {
            double bearing = i * angleStep;

            // 计算目标点
            double cosBearing = Math.cos(bearing);
            double sinBearing = Math.sin(bearing);

            // 目标点纬度
            double destLatRad = Math.asin(
                    sinLat * cosAngularDistance +
                            cosLat * sinAngularDistance * cosBearing
            );

            // 目标点经度
            double deltaLon = Math.atan2(
                    sinBearing * sinAngularDistance * cosLat,
                    cosAngularDistance - sinLat * Math.sin(destLatRad)
            );
            double destLonRad = lonRad + deltaLon;

            // 转换为度并规范化
            double destLat = destLatRad * RAD_TO_DEG;
            double destLon = normalizeLongitude(destLonRad * RAD_TO_DEG);

            points.add(new double[]{destLon, destLat});
        }

        return points;
    }

    /**
     * 规范化经度到[-180, 180]
     */
    private static double normalizeLongitude(double lon) {
        lon = lon % 360;
        if (lon > 180) {
            lon -= 360;
        } else if (lon < -180) {
            lon += 360;
        }
        return lon;
    }

    /**
     * 构建WKT POLYGON字符串
     */
    private static String buildWKTPolygon(List<double[]> points, int precision) {
        StringBuilder wkt = new StringBuilder("POLYGON((");

        String format = "%." + precision + "f %." + precision + "f";

        for (int i = 0; i < points.size(); i++) {
            double[] point = points.get(i);
            wkt.append(String.format(format, point[0], point[1]));

            if (i < points.size() - 1) {
                wkt.append(", ");
            }
        }

        wkt.append("))");
        return wkt.toString();
    }


}
