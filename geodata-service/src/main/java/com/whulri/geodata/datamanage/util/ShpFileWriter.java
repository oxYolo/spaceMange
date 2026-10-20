package com.whulri.geodata.datamanage.util;


import com.whulri.geodata.common.dto.AttributeObj;
import com.whulri.geodata.common.enums.WKBGeometryType;
import com.whulri.geodata.common.gdal.pojo.ShpFileInfo;
import com.whulri.geodata.common.gdal.pojo.shp.ShpFeatureInfo;
import com.whulri.geodata.common.gdal.pojo.shp.ShpFieldInfo;
import com.whulri.geodata.common.gdal.pojo.shp.ShpFieldTypeEnum;
import com.whulri.geodata.common.vo.AttributeModel;
import lombok.extern.slf4j.Slf4j;
import org.gdal.gdal.gdal;
import org.gdal.ogr.*;
import org.gdal.osr.SpatialReference;
import com.whulri.geodata.common.bridge.Func;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

@Slf4j
public class ShpFileWriter {

	private String _shpFileName;
	private List<AttributeModel> _lstAttribute;
	private int _geoType;
	private List<String> _lstWkt;
	private List<AttributeObj> _lstAttributeObj;

	private ShpFileInfo shpFileInfo;

	public ShpFileWriter(ShpFileInfo shpFileInfo) {
		this.shpFileInfo = shpFileInfo;
	}
	public ShpFileWriter(String shpFileName) {
		this._shpFileName = shpFileName;
	}


	public boolean writeShpFile() {
		try {
			if (shpFileInfo.getShpFilePath() == null || shpFileInfo.getShpFilePath().isEmpty()) {
				return false;
			}
			if (shpFileInfo.getFeatureInfos() == null || shpFileInfo.getFeatureInfos().isEmpty()) {
				return false;
			}


				// 支持中文路径
			gdal.SetConfigOption("SHAPE_ENCODING", "");


			// 获取Shapefile Driver
			Driver driver = ogr.GetDriverByName("ESRI Shapefile");
			if (driver == null) {
				throw new Exception("Driver Error");
			}

			// 创建数据源
			DataSource dataSource = driver.CreateDataSource(shpFileInfo.getShpFilePath(), null);
			if (dataSource == null) {
				throw new Exception("DataSource Creation Error");
			}

			// 定义坐标系
			SpatialReference srs = new SpatialReference(shpFileInfo.getSpatialReference());

			// 创建Layer
			Layer layer = dataSource.CreateLayer(shpFileInfo.getShpFilePath(), srs, shpFileInfo.getShpFileType().ordinal(), null);
			if (layer == null) {
				throw new Exception("Layer Creation Failed");
			}

			if (shpFileInfo.getFilterWKT() != null && !shpFileInfo.getFilterWKT().isEmpty()) {
				Geometry filterGeo = Geometry.CreateFromWkt(shpFileInfo.getFilterWKT());
				layer.SetSpatialFilter(filterGeo);
			}

			List<ShpFieldInfo> shpFieldInfoList = shpFileInfo.getFeatureInfos().get(0).getShpFeatureFields();

			// 创建属性列
			for (ShpFieldInfo fieldInfo : shpFieldInfoList) {
				FieldDefn fieldDefn = new FieldDefn(fieldInfo.getFieldName(),
					getFieldOgrType(fieldInfo.getFieldType()));
				fieldDefn.SetWidth(fieldInfo.getFieldLength());
				if (fieldInfo.getFieldType() == ShpFieldTypeEnum.Double) {
					fieldDefn.SetPrecision(fieldInfo.getFieldPrecision());
				}
				layer.CreateField(fieldDefn, 1);
			}

			// 写入要素
			for (ShpFeatureInfo featureInfo : shpFileInfo.getFeatureInfos()) {
				Feature feature = new Feature(layer.GetLayerDefn());
				Geometry geometry = Geometry.CreateFromWkt(featureInfo.getShpFeatureWKT());
				feature.SetGeometry(geometry);

				for (ShpFieldInfo fieldInfo : featureInfo.getShpFeatureFields()) {
					if (fieldInfo.getFieldValue() == null || fieldInfo.getFieldValue().toString().isEmpty()) {
						continue;
					}

					switch (fieldInfo.getFieldType()) {
						case Double:
							feature.SetField(fieldInfo.getFieldName(), Double.parseDouble(fieldInfo.getFieldValue().toString()));
							break;
						case Int:
							feature.SetField(fieldInfo.getFieldName(), Integer.parseInt(fieldInfo.getFieldValue().toString()));
							break;
						default:
							feature.SetField(fieldInfo.getFieldName(), fieldInfo.getFieldValue().toString());
							break;
					}
				}

				layer.CreateFeature(feature);
				feature.delete();
			}

			// 关闭数据源
			dataSource.FlushCache();
			dataSource.delete();
			return true;
		} catch (Exception e) {
			log.error("writeShpFile错误", e);
			return false;
		}
	}

	public boolean doExport(List<AttributeModel> lstAttribute, int geometryType, List<String> lstWkt, List<AttributeObj> lstAttributeObj, String srsWkt) {
		_lstAttribute = lstAttribute;
		_geoType = geometryType;
		_lstWkt = lstWkt;
		_lstAttributeObj = lstAttributeObj;
		DataSource poDS = null;
		try {
			// 注册
			String pszDriverName = "ESRI Shapefile";

			// 调用对Shape文件读写的Driver接口
			gdal.SetConfigOption("SHAPE_ENCODING", "GBK");
//			String os = System.getProperty("os.name");
//			if (os != null && os.toLowerCase().startsWith("win")) {
//				gdal.SetConfigOption("SHAPE_ENCODING", "GBK");
//			} else {
//				gdal.SetConfigOption("SHAPE_ENCODING", "UTF-8");
//				gdal.SetConfigOption("GDAL_FILENAME_IS_UTF8", "YES"); //指定路径名U8编码
//			}
			gdal.AllRegister();
			Driver poDriver = ogr.GetDriverByName(pszDriverName);
			if (poDriver == null)
				throw new Exception("Driver Error");

			// 用此Driver创建Shape文件
			poDS = poDriver.CreateDataSource(_shpFileName, null);
			if (poDS == null)
				throw new Exception("DataSource Creation Error");

			// 定义坐标系
			SpatialReference srs = null;
			if (srsWkt != null && !srsWkt.trim().isEmpty()) {
				log.info("srsWkt: " + srsWkt);
				srs = new SpatialReference(srsWkt);
			} else {
				srs = new SpatialReference();
				srs.ImportFromEPSG(4326);
			}

			// 创建层Layer
			Layer poLayer = null;
			// 指定创建图层时使用 UTF-8 编码

			String layerName = new File(_shpFileName).getName().replaceFirst("[.][^.]+$", "");
			poLayer = poDS.CreateLayer(layerName, srs, _geoType, null);

			if (poLayer == null)
				throw new Exception("Layer Creation Failed");

			// 创建属性列（直接使用原始字段名，不转拼音，统一小写）
			for (AttributeModel att : _lstAttribute) {
				String fieldName = att.getAttributeName().toLowerCase();
				int fieldType = ogrConstants.OFTString; // 默认为字符串类型
				if (Func.isNotEmpty(att.getAttributeType()) && att.getAttributeType().getValue() != 0) {
					fieldType = att.getAttributeType().getValue();
				}
				FieldDefn oField = new FieldDefn(fieldName, fieldType);
				oField.SetWidth(att.getAttributeWidth());
				oField.SetPrecision(att.getAttributePrecision());

				poLayer.CreateField(oField, att.getAttributeApproxOK());
			}

			// 遍历每条记录，每条记录创建独立的 Feature 对象（避免状态残留）
			for (int i = 0; i < _lstWkt.size(); i++) {
				Feature feature = new Feature(poLayer.GetLayerDefn());
				if (_lstAttributeObj != null && !_lstAttributeObj.isEmpty()) {
					// 直接使用原始字段名设置属性值（不转拼音）
					for (Map.Entry<String, String> item : _lstAttributeObj.get(i).getAttributeValue().entrySet()) {
						feature.SetField(item.getKey(), item.getValue());
					}
				}
				Geometry geo = Geometry.CreateFromWkt(_lstWkt.get(i));
				feature.SetGeometry(geo);
				poLayer.CreateFeature(feature);
				feature.delete();
				geo.delete();
			}

			// 关闭文件读写
			poDS.delete();

			// 创建 CPG 文件指定编码为 GBK
			String cpgFilePath = _shpFileName.replaceFirst("[.][^.]+$", ".cpg");
			try (FileWriter writer = new FileWriter(cpgFilePath, Charset.forName("GBK"))) {
				writer.write("GBK");
				log.info("已创建 CPG 文件: {}", cpgFilePath);
			} catch (IOException e) {
				log.warn("创建 CPG 文件失败: {}", e.getMessage());
			}

			return true;
		} catch (Exception e) {
			log.error("doExport错误", e);
			return false;
		} finally {
			// 关闭文件读写
			if (poDS != null ){
				poDS.delete();
			}
		}
	}

	private int getFieldOgrType(ShpFieldTypeEnum fieldType) {
		switch (fieldType) {
			case Double:
				return ogrConstants.OFTReal;
			case Int:
				return ogrConstants.OFTInteger;
			default:
				return ogrConstants.OFTString;
		}
	}
}
