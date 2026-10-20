package com.whulri.geodata.common.util;



import com.whulri.geodata.common.gdal.pojo.CoordSystem;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;

public class CoordTransform {


	/**
	 * 矢量文件坐标系转换（Java 转换版本）
	 * @param csSrc 初始坐标系（WKT/EPSG）
	 * @param csDst 目标坐标系（WKT/EPSG）
	 * @param pathSrc 输入文件路径
	 * @param pathDst 输出文件路径
	 * @param formatDst 输出格式（如 "ESRI Shapefile"）
	 * @param errCallback 错误信息回调
	 * @param infoCallback 正常信息回调
	 * @return 转换是否成功
	 */
	public static boolean ogrtransform(String csSrc, String csDst,
									   String pathSrc, String pathDst,
									   String formatDst,
									   Consumer<String> errCallback,
									   Consumer<String> infoCallback) {
		// 参数校验
		if (errCallback == null) {
			throw new NullPointerException("errCallback");
		}
		if (infoCallback == null) {
			throw new NullPointerException("infoCallback");
		}

		// 获取 GDAL 相关目录（需根据实际环境配置）
		String bindir = "C:\\gdal\\bin"; // 示例 GDAL 二进制目录
		String datadir = "C:\\gdal\\data"; // 示例 GDAL 数据目录
		String plugindir = "C:\\gdal\\plugins"; // 示例 GDAL 插件目录

		// 构建 ogr2ogr 命令参数
		String args = String.format(
			"-overwrite -f \"%s\" -progress -t_srs %s -s_srs %s " +
				"--config GDAL_FILENAME_IS_UTF8 NO --config SHAPE_ENCODING \"\" " +
				"--config GDAL_DRIVER_PATH \"%s\" --config GDAL_DATA \"%s\" " +
				"\"%s\" \"%s\"",
			formatDst, csDst, csSrc, plugindir, datadir, pathDst, pathSrc
		);

		// 执行 ogr2ogr 命令
		ProcessBuilder processBuilder = new ProcessBuilder(
			Path.of(bindir, "ogr2ogr.exe").toString(),
			args
		);

		try {
			Process process = processBuilder.start();
			int exitCode = process.waitFor();

			// 处理输出流（简化示例，实际需读取 stdout/stderr）
			if (exitCode == 0) {
				infoCallback.accept("坐标转换成功，退出码：" + exitCode);
				return true;
			} else {
				errCallback.accept("坐标转换失败，退出码：" + exitCode);
				return false;
			}
		} catch (Exception e) {
			errCallback.accept("执行坐标转换时发生异常：" + e.getMessage());
			return false;
		}
	}

	/**
	 * 影像投影坐标转地理坐标系（Java转换版本）
	 *
	 * @param cs           坐标系对象
	 * @param projPath     投影坐标系影像路径
	 * @param geoPath      地理坐标系输出路径
	 * @param outputFormat 输出格式（如"GTiff"）
	 * @param errCallback  错误信息回调
	 * @param infoCallback 正常信息回调
	 * @return 转换是否成功
	 */
	public static boolean projToGeo(CoordSystem cs, String projPath, String geoPath,
									String outputFormat, Consumer<String> errCallback,
									Consumer<String> infoCallback) {
		if (errCallback == null) {
			throw new IllegalArgumentException("errCallback 不能为null");
		}
		if (infoCallback == null) {
			throw new IllegalArgumentException("infoCallback 不能为null");
		}

		// 设置插件目录和数据目录（需根据实际环境调整）
		Path binDir = Paths.get("E:\\MyProject\\GeoBigData\\DataResource6.0\\bin"); // 替换为实际二进制目录
		Path dataDir = binDir.resolve("data");
		Path pluginDir = binDir.resolve("gdalplugins");

		// 检查插件目录是否存在，不存在则尝试其他路径
		if (!Files.exists(pluginDir)) {
			pluginDir = binDir.resolve("plugins");
			if (!Files.exists(pluginDir)) {
				pluginDir = binDir.resolve("plugin");
			}
		}

		// 构造gdalwarp命令路径
		Path gdalWarpPath = binDir.resolve("gdalwarp.exe");

		// 构造命令参数（注意转义双引号）
		String arg = String.format(
			"-dstnodata 255 -multi -overwrite --config GDAL_FILENAME_IS_UTF8 YES " +
				"--config NUM_THREADS ALL_CPUS -of %s -s_srs \"%s\" -t_srs \"%s\" \"%s\" \"%s\"",
			outputFormat,
			cs.getWKT().replace("\"", "\\\""),
			cs.getGeoCoordSystem().replace("\"", "\\\""),
			projPath,
			geoPath
		);

		// 启动外部进程执行gdalwarp
		ProcessBuilder processBuilder = new ProcessBuilder(gdalWarpPath.toString(), arg);
		processBuilder.redirectErrorStream(true); // 合并错误流和输入流

		try {
			Process process = processBuilder.start();

			// 处理进程输出
			new Thread(() -> {
				try (InputStream is = process.getInputStream();
					 BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
					String line;
					while ((line = reader.readLine()) != null) {
						infoCallback.accept(line);
					}
				} catch (Exception e) {
					errCallback.accept("读取进程输出失败: " + e.getMessage());
				}
			}).start();

			// 等待进程完成
			int exitCode = process.waitFor();
			return exitCode == 0;
		}catch (Exception e) {
			Thread.currentThread().interrupt();
			errCallback.accept("进程被中断: " + e.getMessage());
			return false;
		}
	}

	/**
	 * 矢量文件由一个坐标系转到另一个坐标系
	 * @param csSrc 初始坐标系WKT/EPSG
	 * @param csDst 转换后坐标系WKT/EPSG
	 * @param pathSrc 转换前文件路径
	 * @param pathDst 转换后文件路径
	 * @return 转换是否成功
	 */
	public static boolean ogrtransform(String csSrc, String csDst,
									   String pathSrc, String pathDst) {
		return ogrtransform(csSrc, csDst, pathSrc, pathDst, "ESRI Shapefile",
			(error)->{String s = "";}, (info)->{String s = "";});
	}
}
