package com.whulri.geodata.common.gdal.pojo;

import com.whulri.geodata.datatool.gis.CoordSysType;
import lombok.Data;
import org.gdal.osr.SpatialReference;

@Data
public class CoordSystem {


	private String _descript; // 坐标系原始描述字符串
	private SpatialReference _SR;
	private String _CoodSysName;
	private CoordSysType _type;

	/// <summary>
	/// 坐标系WKT格式定义
	/// </summary>
	public String getWKT() {

		String wkt = "";
		String[] wktArray = new String[]{wkt};
		_SR.ExportToWkt(wktArray, null);
		return wktArray[0];

	}

	/// <summary>
	/// 获得投影坐标对应的地理坐标系定义
	/// </summary>
	/// <returns>1.坐标系自身不是投影坐标系时返回string.Empty
	/// 2.投影系不是bj54/xian80/wgs84时也返回string.Empty</returns>
	public String getGeoCoordSystem()
	{
		if (_type != CoordSysType.CST_PRJ)
		{
			return "";
		}

		String geocs ="";
		String[] geocsArray = new String[]{geocs};
		_SR.CloneGeogCS().ExportToWkt(geocsArray, null);
		return geocs;
	}

	/**
	 * 构造函数
	 *
	 * @param projection 坐标系描述字符串（WKT或PROJ.4格式）
	 */
	public CoordSystem(String projection) {
		_descript = projection;

		try {
			// 初始化SpatialReference对象（GDAL Java绑定）
			_SR = new SpatialReference(projection);

			// 判断坐标系类型
			if (_SR.IsGeographic() == 1 && _SR.IsProjected() == 0 && _SR.IsLocal() == 0) {
				_CoodSysName = _SR.GetAttrValue("GEOGCS", 0);
				_type = CoordSysType.CST_GEO;
			} else if (_SR.IsGeographic() == 0 && _SR.IsProjected() == 1 && _SR.IsLocal() == 0) {
				_CoodSysName = _SR.GetAttrValue("PROJCS", 0);
				_type = CoordSysType.CST_PRJ;
			} else {
				_type = CoordSysType.CST_UNKNOWN;
			}
		} catch (Exception e) {
			throw new IllegalArgumentException("无效的坐标系描述字符串: " + projection, e);
		}
	}


}
