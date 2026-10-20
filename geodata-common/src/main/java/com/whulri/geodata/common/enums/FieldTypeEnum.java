package com.whulri.geodata.common.enums;

public enum FieldTypeEnum {
    OFTInteger(0),
    OFTIntegerList(1),
    OFTReal(2),
    OFTRealList(3),
    OFTString(4),
    OFTStringList(5),
    OFTWideString(6),
    OFTWideStringList(7),
    OFTBinary(8),
    OFTDate(9),
    OFTTime(10),
    OFTDateTime(11),
    OFTInt64(12);  // 长整型

    private final int value;

    FieldTypeEnum(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    /**
     * 根据 GDAL 的整数值返回对应的枚举
     *
     * @param gdalType GDAL 中的 FieldType 整数值（如 org.gdal.ogr.FieldType.OFTString）
     * @return 对应的枚举，若未知则返回 null
     */
    public static FieldTypeEnum fromGdalValue(int gdalType) {
        for (FieldTypeEnum type : FieldTypeEnum.values()) {
            if (type.value == gdalType) {
                return type;
            }
        }
        return OFTString; // 未知类型默认为字符型
    }

    public String getFriendlyName() {
        return this.name().replace("OFT", "");
    }
}
