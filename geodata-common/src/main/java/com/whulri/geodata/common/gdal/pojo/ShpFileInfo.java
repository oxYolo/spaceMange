package com.whulri.geodata.common.gdal.pojo;


import com.whulri.geodata.common.enums.WKBGeometryType;
import com.whulri.geodata.common.gdal.pojo.shp.ShpFeatureInfo;
import lombok.Data;
import org.locationtech.jts.geom.Envelope;

import java.util.ArrayList;
import java.util.List;

@Data
public class ShpFileInfo {
	private String shpFilePath;
	private List<ShpFeatureInfo> featureInfos = new ArrayList<>(); // 假设ShpFeatureInfo是一个自定义类
	private WKBGeometryType shpFileType; // 使用JTS的Geometry类来近似表示wkbGeometryType
	private String spatialReference;
	private String filterWKT;
	private Envelope bbox;
}
