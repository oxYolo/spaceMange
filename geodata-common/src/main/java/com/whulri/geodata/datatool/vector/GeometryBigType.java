package com.whulri.geodata.datatool.vector;

/**
 * 空间几何类型，点、线、面，未知
 */
public enum GeometryBigType {
    /**
     * 点
     */
    Point,

    /**
     * 线
     */
    Line,

    /**
     * 面
     */
    Polygon,

    /**
     * 无几何
     */
    None;

    public static final int SIZE = java.lang.Integer.SIZE;

    public int getValue() {
        return this.ordinal();
    }

    public static GeometryBigType forValue(int value) {
        return values()[value];
    }
}

