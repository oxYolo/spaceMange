package com.whulri.geodata.datamanage.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.whulri.geodata.common.util.GeoJsonUtil;
import com.whulri.geodata.datamanage.dto.VectorEditDTO;
import com.whulri.geodata.datamanage.service.IVectorEditService;
import com.whulri.geodata.basecore.mapper.DynamicTableMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 矢量编辑服务实现
 */
@Slf4j
@Service
public class VectorEditServiceImpl implements IVectorEditService {

    @Autowired
    private DynamicTableMapper dynamicTableMapper;

    @Override
    public boolean editVectorFeatures(VectorEditDTO dto) {
        if (dto == null || StringUtils.isEmpty(dto.getLayerId())
            || dto.getGeoJsonCollection() == null) {
            log.error("矢量编辑参数不完整");
            return false;
        }

        try {
            // 从 JSONObject 获取 features
            JSONArray features = dto.getGeoJsonCollection().getJSONArray("features");
            if (features == null || features.isEmpty()) {
                log.error("GeoJSON 中没有找到要素");
                return false;
            }

            // 遍历每个要素进行更新
            for (int i = 0; i < features.size(); i++) {
                JSONObject feature = features.getJSONObject(i);
                String featureId = feature.getString("id");
                JSONObject geometry = feature.getJSONObject("geometry");
                JSONObject properties = feature.getJSONObject("properties");

                if (StringUtils.isEmpty(featureId) || geometry == null) {
                    log.warn("跳过无效要素: id={}", featureId);
                    continue;
                }

                // 解析要素ID
                String[] parsed = GeoJsonUtil.parseFeatureId(featureId);
                if (parsed == null) {
                    log.warn("跳过无效要素ID: {}", featureId);
                    continue;
                }
                String tableName = parsed[0];
                String gid = parsed[1];

                // 将 geometry 转为 WKT
                String wkt = GeoJsonUtil.geometryToWkt(geometry);
                if (StringUtils.isEmpty(wkt)) {
                    log.warn("跳过无法转换几何的要素: {}", featureId);
                    continue;
                }

                // 获取该表的 SRID 和几何类型
                int srid = getTableSrid(tableName);
                if (srid <= 0) {
                    srid = 4326; // 默认 WGS84
                }
                String targetGeomType = getColumnGeometryType(tableName);
                int coordDimension = getColumnCoordDimension(tableName);

                // 构建 UPDATE SQL（传入 coordDimension 用于处理 Z/M 维度）
                String sql = buildUpdateSql(tableName, gid, wkt, srid, properties, targetGeomType, coordDimension);
                if (sql == null) {
                    log.warn("跳过无效SQL构建: {}", featureId);
                    continue;
                }

                // 执行更新
                log.info("执行SQL: {}", sql);
                int result = dynamicTableMapper.dynamicUpdateExecute(sql);
                if (result <= 0) {
                    log.warn("更新失败: gid={}", gid);
                }
            }

            return true;
        } catch (Exception e) {
            log.error("矢量编辑失败", e);
            return false;
        }
    }

    /**
     * 获取表的 SRID
     */
    private int getTableSrid(String tableName) {
        try {
            String sql = String.format("SELECT ST_SRID(geom) as srid FROM public.%s LIMIT 1", tableName);
            var result = dynamicTableMapper.dynamicExecute(sql);
            if (result != null && !result.isEmpty()) {
                Object srid = result.get(0).get("srid");
                if (srid != null) {
                    return Integer.parseInt(srid.toString());
                }
            }
        } catch (Exception e) {
            log.warn("获取表 {} 的 SRID 失败: {}", tableName, e.getMessage());
        }
        return 4326;
    }

    /**
     * 获取列的几何类型（通过 geometry_columns）
     */
    private String getColumnGeometryType(String tableName) {
        try {
            // 查询 geometry_columns 获取几何类型
            String sql = String.format(
                "SELECT type FROM geometry_columns WHERE f_table_name = '%s' AND f_geometry_column = 'geom'",
                tableName
            );
            var result = dynamicTableMapper.dynamicExecute(sql);
            if (result != null && !result.isEmpty()) {
                return (String) result.get(0).get("type");
            }
        } catch (Exception e) {
            log.warn("获取表 {} 的几何类型失败: {}", tableName, e.getMessage());
        }
        return null;
    }

    /**
     * 获取列的坐标维度（通过 geometry_columns）
     * @return 2=XY, 3=XYZ, 4=XYZM
     */
    private int getColumnCoordDimension(String tableName) {
        try {
            String sql = String.format(
                "SELECT coord_dimension FROM geometry_columns WHERE f_table_name = '%s' AND f_geometry_column = 'geom'",
                tableName
            );
            var result = dynamicTableMapper.dynamicExecute(sql);
            if (result != null && !result.isEmpty()) {
                Object dim = result.get(0).get("coord_dimension");
                if (dim != null) {
                    return Integer.parseInt(dim.toString());
                }
            }
        } catch (Exception e) {
            log.warn("获取表 {} 的坐标维度失败: {}", tableName, e.getMessage());
        }
        return 2; // 默认 XY
    }

    /**
     * 构建 UPDATE SQL
     * @param tableName 表名
     * @param gid 要素ID
     * @param wkt WKT几何字符串
     * @param srid 坐标系
     * @param properties 属性
     * @param targetGeomType 目标列的几何类型（Point/LineString/Polygon/MultiPoint/MultiLineString/MultiPolygon）
     * @param coordDimension 坐标维度（2=XY, 3=XYZ, 4=XYZM）
     */
    private String buildUpdateSql(String tableName, String gid, String wkt, int srid, JSONObject properties, String targetGeomType, int coordDimension) {
        try {
            StringBuilder sql = new StringBuilder();
            sql.append("UPDATE public.").append(tableName).append(" SET ");

            // 根据目标列类型决定是否需要包装几何
            String geomSql = buildGeomSql(wkt, srid, targetGeomType, tableName, gid, coordDimension);
            sql.append("geom = ").append(geomSql);

            sql.append(" WHERE gid = ").append(gid);

            return sql.toString();
        } catch (Exception e) {
            log.error("构建 UPDATE SQL 失败", e);
            return null;
        }
    }

    /**
     * 根据目标列几何类型构建几何 SQL，自动适配单几何和多几何
     * @param wkt WKT字符串
     * @param srid 坐标系
     * @param targetGeomType 目标类型（Point/LineString/Polygon/MultiPoint/MultiLineString/MultiPolygon）
     * @param tableName 表名（用于获取原几何的 M 值）
     * @param gid 要素ID
     * @param coordDimension 目标坐标维度（2=XY, 3=XYZ, 4=XYZM）
     */
    private String buildGeomSql(String wkt, int srid, String targetGeomType, String tableName, String gid, int coordDimension) {
        String baseGeom = String.format("ST_GeomFromText('%s', %d)", wkt, srid);

        if (targetGeomType == null || targetGeomType.isEmpty()) {
            // 没有目标类型信息，直接返回
            return baseGeom;
        }

        // 统一转为小写比较
        String targetLower = targetGeomType.toUpperCase();
        boolean isMultiTarget = targetLower.startsWith("MULTI");
        boolean isMultiSource = wkt.toUpperCase().startsWith("MULTI");

        // 处理 Z/M 维度
        baseGeom = handleZM(baseGeom, wkt, tableName, gid, srid, coordDimension);

        if (isMultiTarget && !isMultiSource) {
            // 目标需要多几何，但传入是单几何，需要包装
            if (targetLower.equals("MULTIPOLYGON") || targetLower.equals("MULTILINESTRING") || targetLower.equals("MULTIPOINT")) {
                return "ST_Multi(" + baseGeom + ")";
            }
        } else if (!isMultiTarget && isMultiSource) {
            // 目标需要单几何，但传入是多几何，取第一个
            if (targetLower.equals("POLYGON")) {
                return "ST_GeometryN(" + baseGeom + ", 1)";
            } else if (targetLower.equals("LINESTRING")) {
                return "ST_GeometryN(" + baseGeom + ", 1)";
            } else if (targetLower.equals("POINT")) {
                return "ST_GeometryN(" + baseGeom + ", 1)";
            }
        }

        // 类型匹配，直接返回
        return baseGeom;
    }

    /**
     * 处理 Z/M 维度：当 GeoJSON 坐标维度不足时，用 0 代替 M 值
     * PostGIS 2.3.2 不存在 ST_SetM 函数，且原几何 M 值可能是 -Infinity
     * 因此简单方案：当缺少 M 时，直接用 0 代替
     */
    private String handleZM(String baseGeom, String wkt, String tableName, String gid, int srid, int coordDimension) {
        // 计算 WKT 中的坐标维度
        int wktCoordCount = countWktCoordDimensions(wkt);
        if (wktCoordCount >= coordDimension) {
            // WKT 维度足够，不需要处理
            return baseGeom;
        }

        // 如果目标需要 4 维 (XYZM) 但 WKT 只有 2-3 维
        if (coordDimension == 4 && wktCoordCount < 4) {
            // 获取 WKT 几何类型前缀
            String geomType = extractGeomType(wkt);
            if (geomType == null) {
                return baseGeom;
            }

            // 在 Java 代码中处理坐标，添加 M=0
            String zmWkt = buildZMWkt(wkt, geomType);
            if (zmWkt == null) {
                return baseGeom;
            }

            // 拼接成 EWKT 格式
            return String.format("ST_GeomFromEWKT('SRID=%d;%s')", srid, zmWkt);
        }

        return baseGeom;
    }

    /**
     * 构建 ZM 格式的 WKT，在每个坐标后添加 M=0
     */
    private String buildZMWkt(String wkt, String geomType) {
        // 找到类型后面的坐标部分
        int typeLen = geomType.length();
        String afterType = wkt.substring(typeLen).trim(); // 跳过类型，得到 ((...))
        if (!afterType.startsWith("(")) return null;

        // 在坐标部分递归添加 M=0
        String coordsPart = addMToCoords(afterType);
        return geomType + " " + coordsPart;
    }

    /**
     * 在坐标字符串的每个坐标后添加 M=0
     * 例如：((1 2 3,4 5 6)) -> ((1 2 3 0,4 5 6 0))
     */
    private String addMToCoords(String str) {
        // 先替换 C# 风格的特殊浮点值（负无穷、正无穷、NaN）为 0
        // 这些值出现在 M 坐标位置时会破坏 WKT 解析
        str = str.replaceAll("-?1\\.#INF", "0");
        str = str.replaceAll("-?1\\.#IND", "0");
        str = str.replaceAll("-?1\\.#QNAN", "0");

        StringBuilder result = new StringBuilder();
        int parenDepth = 0;
        int i = 0;

        while (i < str.length()) {
            char c = str.charAt(i);

            if (c == '(') {
                parenDepth++;
                result.append(c);
                i++;
            } else if (c == ')') {
                parenDepth--;
                result.append(c);
                i++;
            } else if (c == ',' && parenDepth == 1) {
                // 环之间的分隔符，不处理
                result.append(c);
                i++;
            } else if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                // 空白字符，原样保留
                result.append(c);
                i++;
            } else if (Character.isDigit(c) || c == '.' || c == '-' || c == '+') {
                // 开始一个数字，解析整个坐标值
                int start = i;
                // 跳过数字
                while (i < str.length() && (Character.isDigit(str.charAt(i)) || str.charAt(i) == '.' || str.charAt(i) == 'e' || str.charAt(i) == 'E' || str.charAt(i) == '-' || str.charAt(i) == '+')) {
                    i++;
                }
                // i 现在指向第一个非数字字符
                // 但数字后面可能跟的是空格和更多数字（坐标值）
                // 我们需要读取 X Y Z 三个坐标值，然后在最后添加 M=0

                // 读取 X
                String x = str.substring(start, i).trim();
                if (x.isEmpty()) {
                    result.append(str.charAt(i++));
                    continue;
                }

                // 跳过空格，读取 Y
                while (i < str.length() && Character.isWhitespace(str.charAt(i))) i++;
                int yStart = i;
                while (i < str.length() && (Character.isDigit(str.charAt(i)) || str.charAt(i) == '.' || str.charAt(i) == 'e' || str.charAt(i) == 'E' || str.charAt(i) == '-' || str.charAt(i) == '+')) {
                    i++;
                }
                String y = str.substring(yStart, i).trim();

                // 跳过空格，读取 Z（如果有）
                while (i < str.length() && Character.isWhitespace(str.charAt(i))) i++;
                String z = null;
                if (i < str.length() && (Character.isDigit(str.charAt(i)) || str.charAt(i) == '-' || str.charAt(i) == '+')) {
                    int zStart = i;
                    while (i < str.length() && (Character.isDigit(str.charAt(i)) || str.charAt(i) == '.' || str.charAt(i) == 'e' || str.charAt(i) == 'E' || str.charAt(i) == '-' || str.charAt(i) == '+')) {
                        i++;
                    }
                    z = str.substring(zStart, i).trim();
                }

                // 构建输出：X Y [Z] M
                result.append(x).append(" ").append(y);
                if (z != null && !z.isEmpty()) {
                    result.append(" ").append(z);
                }
                result.append(" 0"); // 添加 M=0
            } else {
                result.append(c);
                i++;
            }
        }
        return result.toString();
    }

    /**
     * 从 WKT 字符串中提取几何类型
     * @return POINT, MULTIPOINT, POLYGON, MULTIPOLYGON, LINESTRING, MULTILINESTRING 等
     */
    private String extractGeomType(String wkt) {
        if (wkt == null) return null;
        String upper = wkt.toUpperCase().trim();
        if (upper.startsWith("POINT")) return "POINT";
        if (upper.startsWith("MULTIPOINT")) return "MULTIPOINT";
        if (upper.startsWith("POLYGON")) return "POLYGON";
        if (upper.startsWith("MULTIPOLYGON")) return "MULTIPOLYGON";
        if (upper.startsWith("LINESTRING")) return "LINESTRING";
        if (upper.startsWith("MULTILINESTRING")) return "MULTILINESTRING";
        return null;
    }

    /**
     * 从 WKT 字符串中提取坐标值
     * @return [x, y] 或 [x, y, z]
     */
    private double[] extractCoordsFromWkt(String wkt) {
        if (wkt == null) return null;
        try {
            // 匹配 POINT, POINT Z, POINT ZM 等格式
            int openParen = wkt.indexOf('(');
            int closeParen = wkt.lastIndexOf(')');
            if (openParen < 0 || closeParen < 0) return null;

            String coordsStr = wkt.substring(openParen + 1, closeParen);
            // 处理多重几何的括号
            coordsStr = coordsStr.replaceAll("\\)\\(", " ").replaceAll("[()]", " ");
            String[] parts = coordsStr.trim().split("\\s+");

            if (parts.length < 2) return null;

            double x = Double.parseDouble(parts[0]);
            double y = Double.parseDouble(parts[1]);
            if (parts.length >= 3) {
                double z = Double.parseDouble(parts[2]);
                return new double[]{x, y, z};
            }
            return new double[]{x, y};
        } catch (Exception e) {
            log.warn("解析 WKT 坐标失败: {}, error: {}", wkt, e.getMessage());
            return null;
        }
    }

    /**
     * 计算 WKT 字符串中的坐标维度（通过检查坐标数量）
     */
    private int countWktCoordDimensions(String wkt) {
        if (wkt == null) return 2;
        // 检查是否包含 M 后缀 或 ZM 后缀
        String upper = wkt.toUpperCase();
        if (upper.contains(" ZM") || upper.contains("ZM(") || upper.startsWith("POINT ZM") || upper.startsWith("MULTIPOINT ZM")) {
            return 4;
        }
        if (upper.contains(" Z ") || upper.contains("(Z") || upper.startsWith("POINT Z") || upper.startsWith("MULTIPOINT Z")) {
            return 3;
        }
        // 检查坐标中是否有第三个数值（简单检查）
        // 对于 POINT (x y z) 格式
        int openParen = wkt.indexOf('(');
        int closeParen = wkt.lastIndexOf(')');
        if (openParen > 0 && closeParen > openParen) {
            String coords = wkt.substring(openParen + 1, closeParen);
            // 移除多重几何括号等
            coords = coords.replaceAll("\\)\\(", " ").replaceAll("[()]", " ");
            String[] parts = coords.trim().split("\\s+");
            if (parts.length >= 3) {
                return 3; // 可能是 XYZ
            }
        }
        return 2;
    }

}
