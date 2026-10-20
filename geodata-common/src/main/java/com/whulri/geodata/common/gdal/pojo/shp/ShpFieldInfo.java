package com.whulri.geodata.common.gdal.pojo.shp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ShpFieldInfo {
	private String fieldName;
	private Object fieldValue;
	private ShpFieldTypeEnum fieldType;
	private int fieldLength = 100; // 默认值
	private int fieldPrecision = 8; // 默认值
}
