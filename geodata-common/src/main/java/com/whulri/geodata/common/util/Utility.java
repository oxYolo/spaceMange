package com.whulri.geodata.common.util;

import com.whulri.geodata.common.enums.WKBGeometryType;

public class Utility {

    /**
     * 将 SQL 数据库类型转换为 GDAL 的数据类型
     *
     * @param sqlType SQL 数据类型
     * @return GDAL 数据类型
     */
    public static String sqlTypeToGdalType(String sqlType) {
        String fieldType = "OFTBinary"; // 默认值
        switch (sqlType) {
            case "binary(50)":
                fieldType = "OFTBinary";
                break;
            case "date":
                fieldType = "OFTDate";
                break;
            case "datetime":
                fieldType = "OFTDateTime";
                break;
            case "int":
                fieldType = "OFTInteger";
                break;
            case "real":
                fieldType = "OFTReal";
                break;
            case "nvarchar":
                fieldType = "OFTString";
                break;
        }
        return fieldType;
    }

    /**
     * 将数据类型转换为 GDAL 类型
     *
     * @param dataType 数据类型
     * @return GDAL 数据类型
     */
    public static String dataTypeToGdalType(String dataType) {
        String fieldType = "OFTBinary"; // 默认值
        switch (dataType) {
            case "Short Integer":
            case "Long Integer":
                fieldType = "OFTInteger";
                break;
            case "Date":
                fieldType = "OFTDateTime";
                break;
            case "Float":
            case "Double":
                fieldType = "OFTReal";
                break;
            case "Text":
                fieldType = "OFTString";
                break;
            default:
                fieldType = "OFTString";
                break;
        }
        return fieldType;
    }

    /**
     * 将 GDAL 的数据类型转换为 SQL 数据库类型
     *
     * @param gdalType GDAL 数据类型
     * @return SQL 数据类型
     */
    public static String gdalTypeToSqlType(String gdalType) {
        String sqlType = "";
        switch (gdalType) {
            case "OFTBinary":
                sqlType = "binary(50)";
                break;
            case "OFTDate":
                sqlType = "date";
                break;
            case "OFTDateTime":
                sqlType = "datetime";
                break;
            case "OFTInteger":
                sqlType = "int";
                break;
            case "OFTIntegerList":
                sqlType = "";
                break;
            case "OFTReal":
                sqlType = "real";
                break;
            case "OFTRealList":
                sqlType = "";
                break;
            case "OFTString":
                sqlType = "nvarchar(max)";
                break;
            case "OFTStringList":
                sqlType = "";
                break;
            case "OFTTime":
                sqlType = "";
                break;
            case "OFTWideString":
                sqlType = "";
                break;
            case "OFTWideStringList":
                sqlType = "";
                break;
        }
        return sqlType;
    }

	/**
	 * 将 GDAL 的数据类型转换为 NpSQL 数据库类型
	 *
	 * @param gdalType GDAL 数据类型
	 * @return NpSQL 数据类型
	 */
	public static String gdalTypeToNpSqlType(String gdalType) {
		String sqlType = "";
		switch (gdalType) {
			case "OFTBinary":
				sqlType = "bit";
				break;
			case "OFTDate":
			case "OFTDateTime":
				sqlType = "date";
				break;
			case "OFTInteger":
				sqlType = "int";
				break;
			case "OFTReal":
				sqlType = "real";
				break;
			case "OFTString":
				sqlType = "text";
				break;
			case "OFTIntegerList":
			case "OFTRealList":
			case "OFTStringList":
			case "OFTTime":
			case "OFTWideString":
			case "OFTWideStringList":
				sqlType = "";
				break;
		}
		return sqlType;
	}

    /**
     * 根据数据库中的存储字段，获得空间数据的存储类型
     *
     * @param geoType 空间数据类型字符串
     * @return 空间数据类型
     */
    public static int getGeoTypeByString(String geoType) {
        if (geoType == null) {
            return WKBGeometryType.WKBNone.getValue();
        }

        String type = geoType.toUpperCase();

        // 处理面类型 - 统一使用 MultiPolygon 以支持所有情况
        if (type.contains("POLYGON")) {
            return WKBGeometryType.WKBMultiPolygon.getValue();
        }

        // 处理点类型 - 需要判断是 POINT 还是 MULTIPOINT
        if (type.contains("POINT")) {
            // 如果明确指定为 MULTIPOINT，则返回 MULTIPOINT
            if (type.contains("MULTIPOINT")) {
                return WKBGeometryType.WKBMultiPoint.getValue();
            }
            // 如果只是 POINT，返回 POINT 而不是 MULTIPOINT
            else {
                return WKBGeometryType.WKBPoint.getValue();
            }
        }

        // 处理线类型 - 统一使用 MultiLineString 以支持所有情况
        if (type.contains("LINESTRING") || type.contains("LINE")) {
            return WKBGeometryType.WKBMultiLineString.getValue();
        }

        // 其他情况
        return WKBGeometryType.WKBNone.getValue();
    }

    /**
     * 获取 WKT 空间参考
     *
     * @param spatialReference 空间参考编号
     * @return WKT 空间参考字符串
     */
    public static String wktSpatialReference(String spatialReference) {
        switch (spatialReference) {
            case "1":
                return "GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\",SPHEROID[\"WGS 84\",6378137,298.257223563,AUTHORITY[\"EPSG\",\"7030\"]],AUTHORITY[\"EPSG\",\"6326\"]],PRIMEM[\"Greenwich\",0.0,AUTHORITY[\"EPSG\",\"8901\"]],UNIT[\"degree\",0.0174532925199433,AUTHORITY[\"EPSG\",\"912\"]],AUTHORITY[\"EPSG\",\"4326\"]]";
            case "2":
            case "3":
            case "4":
            default:
                return "";
        }

	}
}
