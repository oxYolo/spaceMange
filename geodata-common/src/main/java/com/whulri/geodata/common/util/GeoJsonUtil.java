package com.whulri.geodata.common.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.List;

/**
 * GeoJSON 工具类
 */
public class GeoJsonUtil {

    /**
     * 解析要素ID，格式为 "tableName.gid"
     * @param featureId 要素ID，如 "vm16184125.3"
     * @return String[] [0]=表名, [1]=gid
     */
    public static String[] parseFeatureId(String featureId) {
        if (featureId == null || featureId.isEmpty()) {
            return null;
        }
        int lastDotIndex = featureId.lastIndexOf('.');
        if (lastDotIndex <= 0 || lastDotIndex == featureId.length() - 1) {
            return null;
        }
        return new String[]{
            featureId.substring(0, lastDotIndex),
            featureId.substring(lastDotIndex + 1)
        };
    }

    /**
     * 将 GeoJSON geometry 转为 WKT 格式
     * @param geometry GeoJSON geometry 对象
     * @return WKT 字符串
     */
    public static String geometryToWkt(JSONObject geometry) {
        if (geometry == null) {
            return null;
        }
        String type = geometry.getString("type");
        JSONArray coordinates = geometry.getJSONArray("coordinates");

        if (type == null || coordinates == null) {
            return null;
        }

        switch (type.toLowerCase()) {
            case "point":
                return pointToWkt(coordinates);
            case "linestring":
                return lineStringToWkt(coordinates);
            case "polygon":
                return polygonToWkt(coordinates);
            case "multipoint":
                return multipointToWkt(coordinates);
            case "multilinestring":
                return multiLineStringToWkt(coordinates);
            case "multipolygon":
                return multiPolygonToWkt(coordinates);
            default:
                return null;
        }
    }

    /**
     * 将 GeoJSON FeatureCollection 解析为 Feature 列表
     * @param geoJson GeoJSON FeatureCollection 字符串
     * @return Feature 列表
     */
    public static JSONArray parseFeatureCollection(String geoJson) {
        if (geoJson == null || geoJson.isEmpty()) {
            return null;
        }
        JSONObject json = JSON.parseObject(geoJson);
        if (json == null) {
            return null;
        }
        return json.getJSONArray("features");
    }

    private static String pointToWkt(JSONArray coordinates) {
        return "POINT(" + coordToStr(coordinates) + ")";
    }

    private static String lineStringToWkt(JSONArray coordinates) {
        StringBuilder sb = new StringBuilder("LINESTRING(");
        for (int i = 0; i < coordinates.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(coordToStr(coordinates.getJSONArray(i)));
        }
        sb.append(")");
        return sb.toString();
    }

    private static String polygonToWkt(JSONArray coordinates) {
        StringBuilder sb = new StringBuilder("POLYGON(");
        for (int i = 0; i < coordinates.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("(");
            JSONArray ring = coordinates.getJSONArray(i);
            for (int j = 0; j < ring.size(); j++) {
                if (j > 0) sb.append(",");
                sb.append(coordToStr(ring.getJSONArray(j)));
            }
            sb.append(")");
        }
        sb.append(")");
        return sb.toString();
    }

    private static String multipointToWkt(JSONArray coordinates) {
        StringBuilder sb = new StringBuilder("MULTIPOINT(");
        for (int i = 0; i < coordinates.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("(").append(coordToStr(coordinates.getJSONArray(i))).append(")");
        }
        sb.append(")");
        return sb.toString();
    }

    private static String multiLineStringToWkt(JSONArray coordinates) {
        StringBuilder sb = new StringBuilder("MULTILINESTRING(");
        for (int i = 0; i < coordinates.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("(");
            JSONArray line = coordinates.getJSONArray(i);
            for (int j = 0; j < line.size(); j++) {
                if (j > 0) sb.append(",");
                sb.append(coordToStr(line.getJSONArray(j)));
            }
            sb.append(")");
        }
        sb.append(")");
        return sb.toString();
    }

    private static String multiPolygonToWkt(JSONArray coordinates) {
        StringBuilder sb = new StringBuilder("MULTIPOLYGON(");
        for (int i = 0; i < coordinates.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("(");
            JSONArray polygon = coordinates.getJSONArray(i);
            for (int j = 0; j < polygon.size(); j++) {
                if (j > 0) sb.append(",");
                sb.append("(");
                JSONArray ring = polygon.getJSONArray(j);
                for (int k = 0; k < ring.size(); k++) {
                    if (k > 0) sb.append(",");
                    sb.append(coordToStr(ring.getJSONArray(k)));
                }
                sb.append(")");
            }
            sb.append(")");
        }
        sb.append(")");
        return sb.toString();
    }

    private static String coordToStr(JSONArray coord) {
        if (coord.size() >= 3) {
            // 3D coordinates: include Z
            return coord.getDoubleValue(0) + " " + coord.getDoubleValue(1) + " " + coord.getDoubleValue(2);
        }
        return coord.getDoubleValue(0) + " " + coord.getDoubleValue(1);
    }
}
