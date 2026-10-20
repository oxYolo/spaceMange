package com.whulri.geodata.datamanage.util;

import com.whulri.gdal.apps.GDALWarp;
import com.whulri.gdal.config.TifConfig;
import com.whulri.gdal.enums.DataType;
import com.whulri.gdal.tif.tiffileReader;
import lombok.extern.slf4j.Slf4j;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.VectorTranslateOptions;
import org.gdal.gdal.WarpOptions;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconstConstants;
import org.gdal.ogr.*;
import org.gdal.osr.SpatialReference;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * @description:
 * @author: 999
 * @create: 2025-02-27
 **/
@Component
@Slf4j
public class ShpFileUtil {

	//wkt转shp
	public static void wktToShp(String wktString, String shpFilePath, String colName, String value) {
		// 注册所有的驱动
		ogr.RegisterAll();
		// 为了支持中文路径，请添加下面这句代码
		gdal.SetConfigOption("GDAL_FILENAME_IS_UTF8", "YES");
		//创建数据
		String strDriverName = "ESRI Shapefile";
		//创建一个文件，根据strDriverName扩展名自动判断是创建shp文件或其他文件
		Driver oDriver = ogr.GetDriverByName(strDriverName);
		if (oDriver == null) {
			System.out.println(strDriverName + " 驱动文件不支持！\n");
			return;
		}
		// 创建数据源
		DataSource oDS = oDriver.CreateDataSource(shpFilePath, null);
		if (oDS == null) {
			System.out.println("创建矢量文件【" + shpFilePath + "】失败！\n");
			return;
		}
		// 创建图层，创建一个多边形图层，这里没有指定空间参考，如果需要的话，需要在这里进行指定
		//如果是mif或者tab，其实是可以多元素混合存放的，shp每个图层只能存放点线面中的一种。
		//所以tab创建图层的时候，选择什么都不影响后面的操作和存储结果
		SpatialReference spatialReference = new SpatialReference();
		spatialReference.ImportFromEPSG(4326);
		Layer oLayer = oDS.CreateLayer("TestPolygon", spatialReference, ogr.wkbPolygon, null);
		if (oLayer == null) {
			System.out.println("图层创建失败！\n");
			return;
		}
		// 下面创建属性表
		// 先创建一个叫FieldID的整型属性
		FieldDefn oFieldID = new FieldDefn(colName, ogr.OFTInteger);
		oLayer.CreateField(oFieldID, 1);
		FeatureDefn oDefn = oLayer.GetLayerDefn();
		//使用wkt方式创建几何对象
		Feature oFeaturePentagon = new Feature(oDefn);
		oFeaturePentagon.SetField(0, value);
		oFeaturePentagon.SetField(1, "wkt");
		Geometry geomPentagon = Geometry.CreateFromWkt(wktString);
		oFeaturePentagon.SetGeometry(geomPentagon);
		oLayer.CreateFeature(oFeaturePentagon);
		//写入文件
		oLayer.delete();
		oDS.delete();
		System.out.println("\n数据集创建完成！\n");
	}

	// shp转tif
	public static void VectorToRasterize(String sourceShpFile, String targetTifFile, Integer width, Integer height, String filedName) {
		gdal.AllRegister();
		gdal.SetConfigOption("GDAL_FILENAME_IS_UTF8", "YES");
		// 为了使属性表字段支持中文，请添加下面这句
		gdal.SetConfigOption("SHAPE_ENCODING", "UTF-8");
		//创建数据，这里以创建ESRI的shp文件为例
		String strDriverName = "ESRI Shapefile";
		//创建一个文件，根据strDriverName扩展名自动判断是创建shp文件或其他文件
		Driver oDriver = ogr.GetDriverByName(strDriverName);

		DataSource dataSource = oDriver.Open(sourceShpFile);

		Layer layer = dataSource.GetLayer(0);
		// 加载图层的空间参考
		SpatialReference spatialRef = layer.GetSpatialRef();
		// 创建栅格数据集
		Dataset rasterDataset = gdal.GetDriverByName("GTiff").Create(targetTifFile, width, height, 1, DataType.Float32.getType(), TifConfig.GetTifCreateOption());
		// 设置空间参考
		rasterDataset.SetProjection(spatialRef.ExportToWkt());
		// 计算栅格化范围
		double minX = 0, maxX = 0, minY = 0, maxY = 0;
		double[] extent = layer.GetExtent(true);
		minX = extent[0];
		maxX = extent[1];
		minY = extent[2];
		maxY = extent[3];
		double cellXSize = (maxX - minX) / width;
		double cellYSize = (maxY - minY) / height;
		double geoTransform[] = new double[]{minX, cellXSize, 0, maxY, 0, -cellYSize};

		// 设置地理变换信息
		rasterDataset.SetGeoTransform(geoTransform);
		rasterDataset.GetRasterBand(1).SetNoDataValue(-60);

		Vector options = new Vector();
		options.add("ATTRIBUTE=" + filedName);
		//栅格化
		gdal.RasterizeLayer(rasterDataset, new int[]{1}, layer, null, options);
		rasterDataset.delete();
		layer.delete();
		dataSource.delete();
	}

	public static void unzip(String zipFilePath, String destDirectory, String Guid) {
		byte[] buffer = new byte[1024];
		try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath), Charset.forName("GBK"))) {
			ZipEntry zipEntry = zis.getNextEntry();
			while (zipEntry != null && !zipEntry.getName().endsWith("/")) {
				String filePath = "";
				if (zipEntry.getName().contains("shp.xml")) {
					filePath = destDirectory + File.separator + Guid + "." + zipEntry.getName().split("\\.")[1] + "." + zipEntry.getName().split("\\.")[2];
				} else {
					filePath = destDirectory + File.separator + Guid + "." + zipEntry.getName().split("\\.")[1];
				}

				if (!zipEntry.isDirectory()) {
					// 如果条目不是目录，则创建父目录
					File newFile = new File(filePath);
					String parent = newFile.getParent();
					if (parent != null && !new File(parent).exists()) {
						new File(parent).mkdirs();
					}

					// 写入文件
					try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath))) {
						int len;
						while ((len = zis.read(buffer)) > 0) {
							bos.write(buffer, 0, len);
						}
					}
				} else {
					// 如果条目是目录，则创建目录
					File folder = new File(filePath);
					folder.mkdirs();
				}
				zipEntry = zis.getNextEntry();
			}
			zis.closeEntry();
		} catch (IOException e) {
			log.warn("解压 shp 压缩包失败: {}", e.getMessage(), e);
		}
	}

	/**
	 * 压缩 shapefile 文件组为 zip
	 * @param folderPath  原始文件夹路径
	 * @param guid        文件名前缀（如 12345）
	 * @return            生成的 zip 文件路径
	 */
	public static String zipShapefile(String folderPath, String guid) {
		// 需要打包的扩展名
		String[] exts = {"shp", "shx", "dbf", "prj", "cpg"};

		// 输出 zip 文件路径
		String zipPath = folderPath + File.separator + guid + ".zip";

		try (FileOutputStream fos = new FileOutputStream(zipPath);
			 ZipOutputStream zos = new ZipOutputStream(fos, Charset.forName("GBK"))) {

			for (String ext : exts) {
				File file = new File(folderPath, guid + "." + ext);
				if (file.exists()) {
					addFileToZip(file, zos);
				} else {
					System.out.println("文件缺失: " + file.getAbsolutePath());
				}
			}

		} catch (IOException e) {
			log.warn("收集 shp 附属文件失败: {}", e.getMessage(), e);
			return null;
		}

		return zipPath;
	}


	private static void addFileToZip(File file, ZipOutputStream zos) throws IOException {
		try (FileInputStream fis = new FileInputStream(file)) {
			ZipEntry zipEntry = new ZipEntry(file.getName()); // 存入 zip 的名字
			zos.putNextEntry(zipEntry);

			byte[] buffer = new byte[1024];
			int len;
			while ((len = fis.read(buffer)) > 0) {
				zos.write(buffer, 0, len);
			}

			zos.closeEntry();
		}
	}

	public static void deleteShapefileGroup(String shpPath) {
		try {
			Path base = Paths.get(shpPath);
			String fileNameWithoutExt = cn.hutool.core.io.FileUtil.mainName(base.toString());
			Path parent = base.getParent();
			if (parent == null) {
				parent = Paths.get(".");
			}
			String[] exts = {".shp", ".shx", ".dbf", ".prj", ".cpg", ".qix" ,".tif"};
			for (String ext : exts) {
				Path p = parent.resolve(fileNameWithoutExt + ext);
				Files.deleteIfExists(p);
			}
		} catch (IOException e) {
			log.warn("删除 Shapefile 相关文件失败: {}", shpPath, e);
		}
	}

	public static void resampling(String sourceFilename, String outputFilename, int sizeX, int sizeY) throws Exception {
		gdal.AllRegister();
		ogr.RegisterAll();
		String outputFormat = "GTiff";
		Dataset _dataset = gdal.Open(sourceFilename, gdalconstConstants.GA_ReadOnly);
		if (_dataset == null) {
			throw new Exception("GDAL read error: " + gdal.GetLastErrorMsg());
		} else {
			int band = _dataset.getRasterCount();
			org.gdal.gdal.Driver driver = gdal.GetDriverByName(outputFormat);
			if (driver == null) {
				throw new Exception("驱动程序找不到");
			} else {
				int outputType = gdalconstConstants.GDT_Float32;
				if (band == 3) {
					outputType = gdalconstConstants.GDT_Byte;
				}

				Dataset dstDS = driver.Create(outputFilename, sizeX, sizeY, band, outputType, TifConfig.GetTifCreateOption());
				if (dstDS == null) {
					System.out.println("Unable to create dataset '" + outputFilename);
					System.out.println(gdal.GetLastErrorMsg());
				}

				dstDS.SetProjection(_dataset.GetProjection());
				if (band == 1) {
					Double[] nodatas = new Double[1];
					_dataset.GetRasterBand(1).GetNoDataValue(nodatas);
					if (nodatas != null && nodatas.length > 0 && nodatas[0] != null) {
						dstDS.GetRasterBand(1).SetNoDataValue(nodatas[0]);
					}
				}

				double[] transform = _dataset.GetGeoTransform();
				double[] transformDS = new double[]{transform[0], (double)_dataset.GetRasterXSize() * transform[1] / (double)sizeX, 0.0, transform[3], 0.0, (double)_dataset.GetRasterYSize() * transform[5] / (double)sizeY};
				dstDS.SetGeoTransform(transformDS);
				Dataset[] datasets = new Dataset[]{_dataset};
				Vector vector = new Vector();
				vector.addElement("-ts");
				vector.addElement(" " + sizeX + " " + sizeY + " ");
				vector.addElement("-r");
				vector.addElement("bilinear");
				vector.addElement("-of");
				vector.addElement(outputFormat);
				WarpOptions warpOptions = new WarpOptions(vector);
				gdal.Warp(dstDS, datasets, warpOptions);
				dstDS.delete();
				_dataset.delete();
			}
		}
	}

	public static void CutTifByShpFileNewInfo(String sourceTifFile, String targetTifFile, String cutShpFile) throws Exception {
		tiffileReader tiffileReader = new tiffileReader(sourceTifFile);
		double[] geoTransform = tiffileReader.get_tiffileInfo().getGeoTransform();
		double pixelWidth = geoTransform[1];
		double pixelHeight = Math.abs(geoTransform[5]);
		DataSource shp = ogr.Open(cutShpFile, 0);
		Layer layer = shp.GetLayer(0);
		double[] envs = layer.GetExtent();
		int sizeX = (int)Math.ceil((envs[1] - envs[0]) / pixelWidth);
		int sizeY = (int)Math.ceil((envs[3] - envs[2]) / pixelHeight);
		String newGeoTransform = envs[0] + "," + geoTransform[1] + "," + geoTransform[2] + "," + envs[3] + "," + geoTransform[4] + "," + geoTransform[5];
		String[] args = new String[]{"gdalwarp", "-outsize", String.valueOf(sizeX), String.valueOf(sizeY), "-geoTransform", newGeoTransform, sourceTifFile, cutShpFile, targetTifFile};
		GDALWarp.build(args);
	}

	/**
	 * 转换 shp 文件编码（只处理 .dbf 和生成对应的 .cpg）
	 * 使用 GDAL VectorTranslate 配合 -lco ENCODING=xxx 选项重写文件
	 * 使用临时子目录方案，避免 .shx/.dbf/.prj 等附文件孤儿化问题
	 * @param sourcePath 源 shp 文件路径
	 * @param sourceEncoding 源编码（如 "UTF-8"）
	 * @param targetEncoding 目标编码（如 "GBK"）
	 */
	public static void convertShpEncoding(String sourcePath, String sourceEncoding, String targetEncoding) {
		ogr.RegisterAll();
		gdal.SetConfigOption("GDAL_FILENAME_IS_UTF8", "YES");
		gdal.SetConfigOption("SHAPE_ENCODING", sourceEncoding);  // 读取源文件时用 sourceEncoding

		Dataset dataset = gdal.OpenEx(sourcePath, gdalconstConstants.GA_ReadOnly);
		if (dataset == null) {
			log.error("无法打开 shp 文件进行编码转换: " + sourcePath + " - " + gdal.GetLastErrorMsg());
			return;
		}

		File sourceFile = new File(sourcePath);
		String baseName = sourceFile.getName().replace(".shp", "");
		File parentDir = sourceFile.getParentFile();
		File tempDir = new File(parentDir, baseName + "_encoding_temp");

		// 创建临时子目录
		if (!tempDir.exists()) {
			tempDir.mkdirs();
		}
		String tempPath = new File(tempDir, baseName + ".shp").getAbsolutePath();

		Vector<String> options = new Vector<>();
		options.add("-skipfailures");
		options.add("-f");
		options.add("ESRI Shapefile");
		options.add("-lco");
		options.add("ENCODING=" + targetEncoding);  // 指定目标编码（GBK）
		VectorTranslateOptions vectorOptions = new VectorTranslateOptions(options);

		Dataset target = gdal.VectorTranslate(tempPath, dataset, vectorOptions);
		dataset.delete();

		if (target != null) {
			target.delete();

			// GDAL VectorTranslate 完成后，GDAL 只设置了 .cpg 但未转换数据字节
			// 需要手动对 DBF 进行字节级编码转换
			String dbfPath = tempPath.replace(".shp", ".dbf");
			if (!sourceEncoding.equalsIgnoreCase(targetEncoding)) {
				convertDbfEncoding(dbfPath, sourceEncoding, targetEncoding);
			}

			// 删除原 shp 文件组（.shp/.shx/.dbf/.prj/.cpg）
			deleteShapefileGroup(sourcePath);

			// 把临时目录里的文件全部移回原目录
			String[] exts = new String[]{".shp", ".shx", ".dbf", ".prj", ".cpg"};
			for (String ext : exts) {
				File src = new File(tempDir, baseName + ext);
				File dst = new File(parentDir, baseName + ext);
				if (src.exists()) {
					src.renameTo(dst);
				}
			}

			// 删除临时目录
			tempDir.delete();
		} else {
			log.error("shp 文件编码转换失败: " + sourcePath + " - " + gdal.GetLastErrorMsg());
			// 清理临时目录
			for (String ext : new String[]{".shp", ".shx", ".dbf", ".prj", ".cpg"}) {
				new File(tempDir, baseName + ext).delete();
			}
			tempDir.delete();
			return;
		}

		// 写入 .cpg 文件指定编码
		String cpgPath = sourcePath.replace(".shp", ".cpg");
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(cpgPath))) {
			writer.write(targetEncoding);
		} catch (IOException e) {
			log.warn("写入 .cpg 文件失败: " + cpgPath, e);
		}
	}

	/**
	 * 手动转换 DBF 文件的字符串字段编码（字节级转换）
	 * DBF 格式：32字节header + 32*N字段描述符 + N条记录
	 * 每条记录 = 1字节删除标记 + 各字段数据（定长，无分隔符）
	 * GDAL 的 ENCODING layer creation option 只影响 .cpg 文件和 DBF header codepage 字节，
	 * 不做实际字节编码转换，因此需要此方法手动完成字节转换。
	 *
	 * @param dbfPath      DBF 文件路径
	 * @param fromEncoding 源编码（如 "UTF-8"）
	 * @param toEncoding   目标编码（如 "GBK"）
	 */
	private static void convertDbfEncoding(String dbfPath, String fromEncoding, String toEncoding) {
		File dbfFile = new File(dbfPath);
		if (!dbfFile.exists()) {
			log.warn("DBF 文件不存在，跳过手动编码转换: " + dbfPath);
			return;
		}

		try (RandomAccessFile raf = new RandomAccessFile(dbfPath, "rw")) {
			// 1. 读 header (32字节)
			byte[] header = new byte[32];
			raf.readFully(header);
			int headerSize = (header[8] & 0xFF) | ((header[9] & 0xFF) << 8);

			// 2. 计算字段数和记录大小
			int fieldCount = (headerSize - 32) / 32;
			int recordSize = (header[10] & 0xFF) | ((header[11] & 0xFF) << 8);

			// 3. 读字段定义，计算每个字符字段的偏移和长度
			int[] charFieldOffsets = new int[fieldCount];
			int[] charFieldLengths = new int[fieldCount];
			int charFieldCount = 0;
			int curOffset = 1; // 跳过删除标记字节
			for (int i = 0; i < fieldCount; i++) {
				byte[] fieldDesc = new byte[32];
				raf.readFully(fieldDesc);
				char type = (char) fieldDesc[11];
				int len = fieldDesc[16] & 0xFF;
				if (type == 'C' || type == 'M') { // 字符/备忘录字段
					charFieldOffsets[charFieldCount] = curOffset;
					charFieldLengths[charFieldCount] = len;
					charFieldCount++;
				}
				curOffset += len;
			}

			// 4. 获取记录总数
			long fileLen = raf.length();
			int recordCount = (int) ((fileLen - headerSize) / recordSize);

			// 5. 逐记录转换每个字符字段
			byte[] record = new byte[recordSize];
			Charset fromCharset = Charset.forName(fromEncoding);
			Charset toCharset = Charset.forName(toEncoding);
			for (int r = 0; r < recordCount; r++) {
				raf.readFully(record);
				if (record[0] == '*') {
					continue; // 跳过已删除记录
				}
				for (int f = 0; f < charFieldCount; f++) {
					int off = charFieldOffsets[f];
					int len = charFieldLengths[f];
					// 提取字符串原始字节并解码为字符串
					String s = new String(record, off, len, fromCharset);
					// 转为目标编码字节，不足部分补空格
					String padded = String.format("%-" + len + "s", s.trim());
					byte[] converted = padded.getBytes(toCharset);
					System.arraycopy(converted, 0, record, off, Math.min(converted.length, len));
				}
				// 写回
				raf.seek(headerSize + r * recordSize);
				raf.write(record);
			}

			log.info("DBF 手动编码转换完成: {} ({} -> {}), {} 条记录, {} 个字符字段",
					dbfPath, fromEncoding, toEncoding, recordCount, charFieldCount);
		} catch (IOException e) {
			log.error("DBF 手动编码转换失败: " + dbfPath, e);
		}
	}

}
