package com.whulri.geodata.common.util.style;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ColorRampInfo {
	private String colorName = "";       // 颜色条带名称
	private List<Color> colorList = new ArrayList<>();  // 颜色列表

	// 颜色个数（对应C#的Count属性）
	public int getCount() {
		return colorList.size();
	}

	// 颜色条带名称（getter/setter）
	public String getColorName() {
		return colorName;
	}

	public void setColorName(String colorName) {
		this.colorName = colorName;
	}

	// 颜色列表（getter/setter）
	public List<Color> getColorList() {
		return colorList;  // 直接返回列表（可根据需要改为不可变视图）
	}

	public void setColorList(List<Color> colorList) {
		if (colorList != null) {
			this.colorList = colorList;
		}
	}

	// 通过索引获取颜色（模拟C#索引器）
	public Color getColor(int index) {
		if (index >= 0 && index < colorList.size()) {
			return colorList.get(index);
		}
		return null;  // 越界返回null（可自定义空颜色）
	}
}
