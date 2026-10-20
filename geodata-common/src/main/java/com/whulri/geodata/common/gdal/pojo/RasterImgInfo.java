package com.whulri.geodata.common.gdal.pojo;

import com.whulri.geodata.datatool.gis.CoordSysType;
import com.whulri.geodata.datatool.gis.GdalHelp;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.gdal;

@Data
@EqualsAndHashCode
public class RasterImgInfo implements AutoCloseable {
	// 地理范围左边界
	private double xLeft;
	// 地理范围右边界
	private double xRight;
	// 地理范围下边界
	private double yBottom;
	// 地理范围上边界
	private double yTop;
	// 图片宽度，单位像素
	private int width;
	// 图片高度，单位像素
	private int height;
	// 横向两像素代表的地理间隔
	private double xCellSpan;
	// 纵向两像素代表的地理间隔
	private double yCellSpan;
	// 图片的数据源
	private Dataset data;
	// 图片的坐标变换矩阵
	private double[] transform = new double[6];
	// 影像坐标系信息
	private CoordSysType.CoordSystem
		coordsystem;

	static {
		GdalHelp.initGdal();
	}

	/**
	 * 打开影像文件
	 * @param imgPath 文件路径
	 * @param readOnly 是否只读模式打开
	 * @return 打开失败返回false
	 */
	public boolean open(String imgPath, boolean readOnly) {
		try {
			GdalHelp.initGdal();
			this.data = gdal.Open(imgPath, readOnly ? 0 : 1);
		} catch (Exception e) {
			return false;
		}

		if (this.data == null) {
			return false;
		}

		this.data.GetGeoTransform(this.transform);
		this.coordsystem = new CoordSysType.CoordSystem(this.data.GetProjectionRef());

		calculateBound();

		return true;
	}

	@Override
	public void close() {
		if (this.data != null) {
			this.data.delete();
			this.data = null;
		}
		this.transform = new double[6];
		System.gc();
	}

	/**
	 * 计算图像范围
	 */
	private void calculateBound() {
		if (this.data == null) {
			throw new NullPointerException("dataset is null");
		}

		this.width = this.data.getRasterXSize();
		this.height = this.data.getRasterYSize();

		this.xLeft = this.transform[0];
		this.yTop = this.transform[3];
		this.xRight = this.transform[0] + this.width * this.transform[1];
		this.yBottom = this.transform[3] + this.height * this.transform[5];

		this.xCellSpan = (this.xRight - this.xLeft) / this.width;
		this.yCellSpan = (this.yTop - this.yBottom) / this.height;
	}
}
