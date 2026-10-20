package com.whulri.geodata.common.gdal.pojo.shp;


import lombok.Data;

import java.util.ArrayList;
import java.util.List;
@Data
public class ShpFeatureInfo {

	// 要素空间信息
	private String shpFeatureWKT;

	// 要素属性集合
	private List<ShpFieldInfo> shpFeatureFields;

	// 构造函数
	public ShpFeatureInfo() {
		this.shpFeatureFields = new ArrayList<>();
	}
}
