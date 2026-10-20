package com.whulri.geodata.datatool.marshal;

/**
 * 三值布尔型
 */
public enum BOOL {
    /**
     * 未赋值状态
     */
    UNINT(-1),

    /**
     * false
     */
    FALSE(0),

    /**
     * true
     */
    TRUE(1);

    public static final int SIZE = java.lang.Integer.SIZE;

    private int intValue;
    private static java.util.HashMap<Integer, BOOL> mappings;

    private static java.util.HashMap<Integer, BOOL> getMappings() {
        if (mappings == null) {
            synchronized (BOOL.class) {
                if (mappings == null) {
                    mappings = new java.util.HashMap<Integer, BOOL>();
                }
            }
        }
        return mappings;
    }

    private BOOL(int value) {
        intValue = value;
        getMappings().put(value, this);
    }

    public int getValue() {
        return intValue;
    }

    public static BOOL forValue(int value) {
        return getMappings().get(value);
    }
}