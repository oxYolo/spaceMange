package com.whulri.geodata.common.enums;

import lombok.Getter;

@Getter
public enum WKBGeometryType {

	WKBUnknown(0),
	WKBPoint(1),
	WKBLineString(2),
	WKBPolygon(3),
	WKBMultiPoint(4),
	WKBMultiLineString(5),
	WKBMultiPolygon(6),
	WKBGeometryCollection(7),
	WKBNone(100),
	WKBLinearRing(101),
	WKBPointZ(1001),
	WKBLineStringZ(1002),
	WKBPolygonZ(1003),
	WKBMultiPointZ(1004),
	WKBMultiLineStringZ(1005),
	WKBMultiPolygonZ(1006),
	WKBGeometryCollectionZ(1007),
	WKBPointM(2001),
	WKBLineStringM(2002),
	WKBPolygonM(2003),
	WKBMultiPointM(2004),
	WKBMultiLineStringM(2005),
	WKBMultiPolygonM(2006),
	WKBGeometryCollectionM(2007),
	WKBPointZM(3001),
	WKBLineStringZM(3002),
	WKBPolygonZM(3003),
	WKBMultiPointZM(3004),
	WKBMultiLineStringZM(3005),
	WKBMultiPolygonZM(3006),
	WKBGeometryCollectionZM(3007),
	WKBPoint25D(-2147483647),
	WKBLineString25D(-2147483646),
	WKBPolygon25D(-2147483645),
	WKBMultiPoint25D(-2147483644),
	WKBMultiLineString25D(-2147483643),
	WKBMultiPolygon25D(-2147483642),
	WKBGeometryCollection25D(-2147483641);

    /**
     * -- GETTER --
     *  获取枚举值
     *
     * @return 枚举对应的整数值
     */
    private final int value;

    WKBGeometryType(int value) {
        this.value = value;
    }

    /**
     * 根据整数值获取对应的枚举对象
     *
     * @param value 枚举对应的整数值
     * @return 对应的枚举对象，如果找不到则抛出 IllegalArgumentException
     */
    public static WKBGeometryType fromValue(int value) {
        for (WKBGeometryType geometryType : values()) {
            if (geometryType.getValue() == value) {
                return geometryType;
            }
        }
        throw new IllegalArgumentException("未知的几何类型值: " + value);
    }
}
