package com.whulri.geodata.common.util;

import org.gdal.gdal.gdal;
import org.gdal.ogr.ogr;
import com.whulri.geodata.common.bridge.Func;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static com.whulri.geodata.common.util.CoordTransform.ogrtransform;


public class Gisfile {


	/**
	 * 查找坐标系参考文件（Java 转换版本）
	 * @param path 搜索路径
	 * @param wktReference 用于存储找到的 WKT 坐标系信息（类似 C# 的 ref 参数）
	 * @param spatialRefenceFile 要查找的坐标系文件名前缀（不带 .prj 后缀）
	 */
	public static String findReferenceFile(String path, String wktReference, String spatialRefenceFile) {
		// 若已找到 WKT 信息，直接返回
		if (Func.isNotEmpty(wktReference)) {
			return "";
		}
		AtomicReference<String> wktReferenceFinal = new AtomicReference<>("");
		Path searchPath = Paths.get(path);
		String targetFileName = spatialRefenceFile + ".prj";

		try (Stream<Path> files = Files.list(searchPath)) {

			// 遍历当前目录下的文件
			files.forEach(file -> {
				if (Files.isRegularFile(file) && file.getFileName().toString().equals(targetFileName)) {
					try {
						String content = Files.readString(file);
						if (content.toUpperCase().contains("GEOGCS")) {
							wktReferenceFinal.set(content); // 找到有效 WKT 信息
						} else {
							wktReferenceFinal.set(""); // 无效 WKT 信息，置空
						}
					} catch (Exception e) {
						// 读取文件失败，保持原状态
					}
				}
			});

		} catch (Exception e) {
			// 目录遍历失败，保持原状态
			return "";
		}

		// 若未找到，递归搜索子目录
		if (Func.isEmpty(wktReference)) {
			try (Stream<Path> directories = Files.list(searchPath)) {
				directories.filter(Files::isDirectory).forEach(dir -> {
					findReferenceFile(dir.toString(), wktReference, spatialRefenceFile);
				});
			} catch (Exception e) {
				// 子目录遍历失败，保持原状态
			}
		}
		return wktReferenceFinal.get();
	}

	/**
	 * 矢量转换成默认坐标系（Java 转换版本）
	 * @param files 文件路径
	 * @return 转换后的 .shp 文件路径
	 */
	public static String coordTransform2(String files,String coordPath) {
		// 检查文件扩展名是否为 .shp
		String ext = FileServiceHelper.getFileExtension(files);
		if (!ext.equalsIgnoreCase(".shp")) {
			return "";
		}

		// 获取配置路径并查找参考文件
		String wktReference = "";
		findReferenceFile(coordPath, wktReference,null);

		if (Func.isBlank(wktReference)) {
			return "";
		}

		// 初始化 GDAL
		gdal.AllRegister();
		ogr.RegisterAll();

		// 读取源文件坐标系
		ShpReader shpReader2 = new ShpReader(files);// 需实现获取 WKT 坐标系的方法
		String str2 = shpReader2.getSridWkt();
		// 创建新目录
		Path srcPath = Paths.get(files);
		String name = FileServiceHelper.getFileNameWithoutExtension(srcPath.toString());
		Path newFolder = srcPath.getParent().resolve("CoordTransform").resolve(name);
		try {
			Files.createDirectories(newFolder);
		} catch (Exception e) {
			return "";
		}

		// 构造目标文件路径
		String extName = FileServiceHelper.getFileExtension(srcPath.toString());
		Path tmpFile2 = newFolder.resolve(name + extName);

		// 执行坐标转换（假设 ogrtransform 为 GDAL 工具封装方法）
		ogrtransform(str2, wktReference, files, tmpFile2.toString(),null,null, null);

		// 获取转换后的 .shp 文件
		try (var filesStream = Files.list(newFolder)) {
			return filesStream
				.filter(path -> path.getFileName().toString().toLowerCase().endsWith(".shp"))
				.map(Path::toString)
				.findFirst()
				.orElse("");
		} catch (Exception e) {
			return "";
		}
	}

}
