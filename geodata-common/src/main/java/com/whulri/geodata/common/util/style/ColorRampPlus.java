package com.whulri.geodata.common.util.style;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ColorRampPlus {
	private static final Logger LOGGER = Logger.getLogger(ColorRampPlus.class.getName());
	private final String configPath;  // Excel配置文件路径
	private List<ColorRampInfo> linearColorRamps = new ArrayList<>();  // 渐变色条带（seq/div）
	private List<ColorRampInfo> randomColorRamps = new ArrayList<>();   // 随机色条带（qual）

	// 默认构造函数（优先工作目录 Data/ColorBrewer.xls，缺失时回退 classpath:/Data/ColorBrewer.xls）
	public ColorRampPlus() {
		String workDirPath = System.getProperty("user.dir") + File.separator + "Data" + File.separator + "ColorBrewer.xls";
		if (new File(workDirPath).exists()) {
			this.configPath = workDirPath;
		} else {
			// jar 内资源（geodata-web/src/main/resources/Data/ColorBrewer.xls）
			this.configPath = "classpath:/Data/ColorBrewer.xls";
		}
		loadColorRamps();
	}

	// 自定义路径的构造函数
	public ColorRampPlus(String configPath) {
		this.configPath = configPath;
		loadColorRamps();
	}

	// 打开配置文件流：支持文件路径与 classpath: 前缀
	private InputStream openConfigStream() throws IOException {
		if (configPath.startsWith("classpath:")) {
			InputStream in = ColorRampPlus.class.getResourceAsStream(configPath.substring("classpath:".length()));
			if (in == null) {
				throw new IOException("classpath 资源不存在: " + configPath);
			}
			return in;
		}
		return new FileInputStream(new File(configPath));
	}

	// 核心：从Excel加载颜色条带数据
	private void loadColorRamps() {
		try (InputStream fis = openConfigStream();
			 Workbook workbook = new HSSFWorkbook(fis)) {

			Sheet sheet = workbook.getSheetAt(0);  // 读取第一个Sheet（[ColorBrewer$]）
			// 探测表头 R/G/B 列位置（原系统 ColorBrewer.xls 为 0..8 列：ColorName/NumOfColors/Type/CritVal/ColorNum/ColorLetter/R/G/B；兼容 3/4/5 简化布局）
			int rIdx = 3, gIdx = 4, bIdx = 5;
			Row headerRow = sheet.getRow(0);
			if (headerRow != null) {
				for (int c = 0; c < headerRow.getLastCellNum(); c++) {
					String hv = getStringCellValue(headerRow.getCell(c));
					if ("R".equalsIgnoreCase(hv)) rIdx = c;
					else if ("G".equalsIgnoreCase(hv)) gIdx = c;
					else if ("B".equalsIgnoreCase(hv)) bIdx = c;
				}
			}
			int lastRowNum = sheet.getLastRowNum();

			for (int i = 0; i <= lastRowNum; i++) {
				Row row = sheet.getRow(i);
				if (row == null) continue;

				// 解析颜色名称、数量、类型（处理可能的空单元格）
				String colorName = getStringCellValue(row.getCell(0));
				String numOfColorStr = getStringCellValue(row.getCell(1));
				String type = getStringCellValue(row.getCell(2));

				// 校验必要字段
				if (colorName == null || numOfColorStr == null || type == null) continue;
				if (!numOfColorStr.matches("\\d+") || Integer.parseInt(numOfColorStr) <= 0) continue;

				int num = Integer.parseInt(numOfColorStr);
				ColorRampInfo info = new ColorRampInfo();
				info.setColorName(colorName);

				// 读取颜色值（R/G/B列，跳过无效行）
				boolean valid = true;
				for (int j = 0; j < num; j++) {
					Row colorRow = sheet.getRow(i + j);
					if (colorRow == null) {
						valid = false;
						break;
					}

					String rStr = getStringCellValue(colorRow.getCell(rIdx));
					String gStr = getStringCellValue(colorRow.getCell(gIdx));
					String bStr = getStringCellValue(colorRow.getCell(bIdx));

					if (rStr == null || gStr == null || bStr == null) {
						valid = false;
						break;
					}

					try {
						int r = Integer.parseInt(rStr);
						int g = Integer.parseInt(gStr);
						int b = Integer.parseInt(bStr);
						if (r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255) {
							valid = false;
							break;
						}
						info.getColorList().add(new Color(r, g, b));
					} catch (NumberFormatException e) {
						valid = false;
						break;
					}
				}

				if (!valid) {
					info.getColorList().clear();  // 清除无效颜色
					continue;
				}

				// 按类型分类存储
				if ("seq".equalsIgnoreCase(type) || "div".equalsIgnoreCase(type)) {
					linearColorRamps.add(info);
				} else if ("qual".equalsIgnoreCase(type)) {
					randomColorRamps.add(info);
				}

				i += num - 1;  // 跳过已处理的颜色行
			}
		} catch (IOException e) {
			LOGGER.severe("加载颜色配置文件失败: " + e.getMessage());
			throw new RuntimeException("颜色配置文件加载失败，请检查文件路径", e);
		}
	}

	// 辅助：获取单元格字符串值（处理空单元格和数字类型）
	private String getStringCellValue(Cell cell) {
		if (cell == null) return null;
		switch (cell.getCellType()) {
			case STRING:
				return cell.getStringCellValue();
			case NUMERIC:
				return String.valueOf((int) cell.getNumericCellValue());  // 数字转整数字符串
			default:
				return null;
		}
	}

	// 获取渐变色条带列表（按颜色数量过滤）
	public List<ColorRampInfo> getLinearColorRamps(int colNum) {
		return linearColorRamps.stream()
			.filter(ramp -> ramp.getCount() == colNum)
			.collect(Collectors.toList());
	}

	// 获取随机色条带列表（按颜色数量过滤）
	public List<ColorRampInfo> getRandomColorRamps(int colNum) {
		return randomColorRamps.stream()
			.filter(ramp -> ramp.getCount() == colNum)
			.collect(Collectors.toList());
	}

	// 渐变色最大颜色数量
	public int getLinearMaxColorNum() {
		return linearColorRamps.stream()
			.mapToInt(ColorRampInfo::getCount)
			.max()
			.orElse(0);
	}

	// 渐变色最小颜色数量
	public int getLinearMinColorNum() {
		return linearColorRamps.stream()
			.mapToInt(ColorRampInfo::getCount)
			.min()
			.orElse(0);
	}

	// 随机色最大颜色数量
	public int getRandomMaxColorNum() {
		return randomColorRamps.stream()
			.mapToInt(ColorRampInfo::getCount)
			.max()
			.orElse(0);
	}

	// 随机色最小颜色数量
	public int getRandomMinColorNum() {
		return randomColorRamps.stream()
			.mapToInt(ColorRampInfo::getCount)
			.min()
			.orElse(0);
	}

	// 渐变色最佳颜色数量（出现次数最多的数量）
	public int getLinearBestColorNum() {
		return linearColorRamps.stream()
			.collect(Collectors.groupingBy(
				ColorRampInfo::getCount,
				Collectors.counting()
			))
			.entrySet().stream()
			.max(Map.Entry.comparingByValue())
			.map(Map.Entry::getKey)
			.orElse(0);
	}

	// 随机色最佳颜色数量
	public int getRandomBestColorNum() {
		return randomColorRamps.stream()
			.collect(Collectors.groupingBy(
				ColorRampInfo::getCount,
				Collectors.counting()
			))
			.entrySet().stream()
			.max(Map.Entry.comparingByValue())
			.map(Map.Entry::getKey)
			.orElse(0);
	}

	// 获取指定名称和数量的渐变色条带（自动修正数量范围）
	public ColorRampInfo getLinearColorRamp(String colorName, int num) {
		int max = getLinearMaxColorNum();
		int min = getLinearMinColorNum();
		int targetNum = Math.min(Math.max(num, min), max);  // 限制在[min, max]

		return linearColorRamps.stream()
			.filter(ramp -> ramp.getColorName().equals(colorName) && ramp.getCount() == targetNum)
			.findFirst()
			.orElse(null);
	}

	// 获取指定名称和数量的随机色条带（自动修正数量范围）
	public ColorRampInfo getRandomColorRamp(String colorName, int num) {
		int max = getRandomMaxColorNum();
		int min = getRandomMinColorNum();
		int targetNum = Math.min(Math.max(num, min), max);

		return randomColorRamps.stream()
			.filter(ramp -> ramp.getColorName().equals(colorName) && ramp.getCount() == targetNum)
			.findFirst()
			.orElse(null);
	}

	// 通用获取颜色条带（优先渐变色）
	public ColorRampInfo getRampInfo(String colorName, int num) {
		ColorRampInfo ramp = getLinearColorRamp(colorName, num);
		return ramp != null ? ramp : getRandomColorRamp(colorName, num);
	}

	// 示例用法（可选）
	public static void main(String[] args) {
		ColorRampPlus colorRamp = new ColorRampPlus();
		System.out.println("渐变色最大数量: " + colorRamp.getLinearMaxColorNum());
		System.out.println("随机色最佳数量: " + colorRamp.getRandomBestColorNum());

		ColorRampInfo ramp = colorRamp.getLinearColorRamp("Blues", 5);
		if (ramp != null) {
			System.out.println("获取到Blues 5色条带，颜色数量: " + ramp.getCount());
		}
	}
}
