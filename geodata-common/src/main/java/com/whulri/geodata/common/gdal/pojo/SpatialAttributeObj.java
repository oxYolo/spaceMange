package com.whulri.geodata.common.gdal.pojo;

import lombok.Data;
import org.gdal.ogr.Feature;

import java.util.HashMap;
@Data
public class SpatialAttributeObj {
	private int oid;
	private Feature spatialValue;
	private HashMap<String, String> attriValue;

	public void close() {
		if (spatialValue != null) {
			spatialValue.delete(); // 释放 OGR Feature 对象的资源
			spatialValue = null;
		}
	}
}
