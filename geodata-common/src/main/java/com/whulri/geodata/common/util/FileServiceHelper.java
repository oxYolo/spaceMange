package com.whulri.geodata.common.util;


import com.google.gson.Gson;
import com.whulri.geodata.common.gdal.pojo.FileDownloadInfo;
import com.whulri.geodata.common.gdal.pojo.FileUploadInfo;
import com.whulri.geodata.common.gdal.pojo.RequestInfo;
import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;
import okhttp3.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

@Slf4j
public class FileServiceHelper {
	private static final int BUFFER_SIZE = 1024;

	private static final String TEMP_DIR = System.getProperty("user.dir") + File.separator + "UpLoadFile" + File.separator + "temp";

	/**
	 * 根据给定的文件路径返回文件名（不包含扩展名）。
	 *
	 * @param path 文件路径。
	 * @return 不包含扩展名的文件名。
	 */
	public static String getFileNameWithoutExtension(String path) {
		// 使用File类获取文件对象
		File file = new File(path);

		// 获取文件名（包含扩展名）
		String fileNameWithExtension = file.getName();

		// 查找最后一个'.'的位置，如果没有找到返回-1
		int dotIndex = fileNameWithExtension.lastIndexOf('.');

		// 如果找到了'.'，则截取从开始到'.'之前的字符串
		if (dotIndex == -1) {
			// 如果没有找到'.'，则整个文件名就是没有扩展名的部分
			return fileNameWithExtension;
		} else {
			// 截取从文件名开始到'.'之前的部分
			return fileNameWithExtension.substring(0, dotIndex);
		}
	}

	/**
	 * 提取文件的扩展名部分。
	 *
	 * @param file 文件对象
	 * @return 文件的扩展名部分，如果没有扩展名则返回空字符串
	 */
	public static String getFileExtension(File file) {
		String fileName = file.getName();
		int lastDotIndex = fileName.lastIndexOf('.');
		if (lastDotIndex == -1) {
			return "";
		} else {
			return fileName.substring(lastDotIndex);
		}
	}

	public static void deleteDirectory(Path directory) {
		try {
			Files.walk(directory)
				.sorted(Comparator.reverseOrder())
				.map(Path::toFile)
				.forEach(File::delete);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 删除目录及其所有子文件和子目录
	 * @param file 要删除的目录路径
	 */
	public static void deleteDir(String file) {
		try {
			// 去除文件和目录的只读属性
			File dir = new File(file);
			dir.setWritable(true);

			// 判断目录是否存在
			if (dir.exists() && dir.isDirectory()) {
				File[] files = dir.listFiles();
				if (files != null) {
					for (File f : files) {
						if (f.isFile()) {
							// 删除文件
							f.delete();
						} else {
							// 递归删除子目录
							deleteDir(f.getAbsolutePath());
						}
					}
				}
				// 删除空目录
				dir.delete();
			}
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
	/**
	 * 提取文件的扩展名部分。
	 *
	 * @param
	 * @return 文件的扩展名部分，如果没有扩展名则返回空字符串
	 */
	public static String getFileExtension(String filePath) {
		String fileName = Paths.get(filePath).getFileName().toString();
		int lastDotIndex = fileName.lastIndexOf('.');
		if (lastDotIndex == -1) {
			return "";
		} else {
			return fileName.substring(lastDotIndex);
		}
	}


	/**
	 * 文件下载
	 *
	 * @param downloadUrl  下载路径
	 * @param taskId       任务ID
	 * @param destFilePath 目标路径
	 * @throws Exception
	 */
	public static void downloadFile(String downloadUrl, String taskId, String destFilePath) throws Exception {
		String url = downloadUrl + "?taskId=" + taskId;
		HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
		connection.setRequestProperty("Accept-Encoding", "gzip");

		try (InputStream inputStream = new GZIPInputStream(connection.getInputStream());
			 FileOutputStream outputStream = new FileOutputStream(destFilePath)) {
			byte[] buffer = new byte[1024 * 1024];
			int bytesRead;
			while ((bytesRead = inputStream.read(buffer)) != -1) {
				outputStream.write(buffer, 0, bytesRead);
			}
			outputStream.flush();
		} finally {
			connection.disconnect();
		}
	}

	/**
	 * 从空间数据管理下载文件
	 *
	 * @param downLoadUrl  下载基础URL
	 * @param mapid        地图ID
	 * @param destFilePath 目标文件路径
	 */
	public static void downloadFileFromKJGL(String downLoadUrl, String mapid, String destFilePath) {
		// 检查文件是否已存在
		File destFile = new File(destFilePath);
		if (destFile.exists()) {
			return;
		}

		// 构建完整的下载URL
		String fullUrl = downLoadUrl + "?layerid=" + mapid;

		try {
			// 创建URL对象
			URL url = new URL(fullUrl);

			// 打开HTTP连接
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();

			// 设置请求方法为GET
			conn.setRequestMethod("GET");

			// 创建文件输出流，用于保存下载的数据
			FileOutputStream saveFileStream = new FileOutputStream(destFilePath);

			// 获取响应的输入流
			InputStream respStream = conn.getInputStream();

			// 将响应流复制到文件输出流
			byte[] buffer = new byte[4096];
			int bytesRead;
			while ((bytesRead = respStream.read(buffer)) != -1) {
				saveFileStream.write(buffer, 0, bytesRead);
			}

			// 清空缓冲区
			saveFileStream.flush();

			// 关闭文件输出流
			saveFileStream.close();

			// 关闭连接
			conn.disconnect();
		} catch (IOException e) {
			// 处理可能出现的异常，例如网络问题或文件操作错误
			log.error("从空间数据管理下载文件失败", e);
		}
	}

	/**
	 * 上传文件
	 *
	 * @param uploadUrl 上传URL
	 * @param filePath  文件路径
	 */
	public static void uploadFile(String uploadUrl, String filePath) {
		HttpURLConnection connection = null;
		DataOutputStream outputStream = null;
		FileInputStream fileInputStream = null;
		try {
			URL url = new URL(uploadUrl);
			connection = (HttpURLConnection) url.openConnection();
			connection.setDoInput(true); // Allow Inputs
			connection.setDoOutput(true); // Allow Outputs
			connection.setUseCaches(false); // Don't use a Cached Copy
			connection.setRequestMethod("POST"); // Send a POST request
			connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=---------------------------" + System.currentTimeMillis());

			// Create a new data output stream to the connection
			outputStream = new DataOutputStream(connection.getOutputStream());

			// Open our local file for reading
			fileInputStream = new FileInputStream(filePath);

			// Write the header for the form data
			writeHeader(outputStream, filePath);

			// Write the file data
			byte[] buffer = new byte[4096];
			int bytesRead;
			while ((bytesRead = fileInputStream.read(buffer)) != -1) {
				outputStream.write(buffer, 0, bytesRead);
			}

			// End of multipart form data
			outputStream.writeBytes("\r\n-------------------------------" + System.currentTimeMillis() + "--\r\n");

			// Check server response code
			int responseCode = connection.getResponseCode();
			System.out.println("Response Code : " + responseCode);

			// Read the response from the server
			BufferedReader in = new BufferedReader(
				new InputStreamReader(connection.getInputStream()));
			String inputLine;
			StringBuffer response = new StringBuffer();
			while ((inputLine = in.readLine()) != null) {
				response.append(inputLine);
			}
			in.close();

			// Print result
			System.out.println(response.toString());
		} catch (MalformedURLException e) {
			log.warn("文件服务 URL 格式错误: {}", e.getMessage(), e);
		} catch (IOException e) {
			log.warn("文件服务上传 IO 失败: {}", e.getMessage(), e);
		} finally {
			try {
				if (outputStream != null) {
					outputStream.close();
				}
				if (fileInputStream != null) {
					fileInputStream.close();
				}
				if (connection != null) {
					connection.disconnect();
				}
			} catch (IOException e) {
				log.debug("关闭文件服务连接失败（忽略）: {}", e.getMessage());
			}
		}
	}

	private static void writeHeader(DataOutputStream outputStream, String filePath) throws IOException {
		String boundary = "---------------------------" + System.currentTimeMillis();
		outputStream.writeBytes("--" + boundary + "\r\n");
		outputStream.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + new File(filePath).getName() + "\"\r\n");
		outputStream.writeBytes("Content-Type: application/octet-stream\r\n\r\n");
	}


	/**
	 * 下载文件到指定路径
	 *
	 * @param downloadUrl 下载地址
	 * @param savePath    本地保存路径
	 */
	public static void download(String downloadUrl, String savePath) {
		try (InputStream in = new URL(downloadUrl).openStream()) {
			Files.copy(in, Paths.get(savePath));
		} catch (IOException e) {
			log.error("文件下载失败");
		}
	}

	/**
	 * 下载文件
	 *
	 * @param siteUrl        服务接口Ip地址
	 * @param remoteFilePath 远程服务器文件路径
	 * @param localFilePath  本地文件路径
	 * @throws Exception
	 */
	public static void downloadFileEx(String siteUrl, String remoteFilePath, String localFilePath) {
		log.info("开始下载文件 \n远程文件路径:{} \n本地保存路径:{}", remoteFilePath, localFilePath);
		// 检查本地文件是否已经存在
		if (Files.exists(Paths.get(localFilePath))) {
			return;
		}

		// 初始化文件下载信息
		FileDownloadInfo info = new FileDownloadInfo();
		info.setDownLoadFilePath(remoteFilePath);
		info.setOffSet(0);
		info.setBufferSize(1024 * 1024);

		// 初始化请求信息以获取文件大小
		RequestInfo reqInfo = new RequestInfo();

		reqInfo.setRequestUrl(replaceDoubleSlashesWithSingle(siteUrl + "/api/FileHelper/GetFileSize"));
		reqInfo.setRequestParamter(JsonUtil.toJson(info));

		// 从服务器获取文件大小
		Long fileSize = WebRequestHelper.post(reqInfo, Long.class);
		if (fileSize == null || fileSize <= 0) {
			throw new RuntimeException("获取文件失败...");
		}

		try {
			downloadFileWithOkHttp(siteUrl, info, fileSize, localFilePath);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

	}

	public static String replaceDoubleSlashesWithSingle(String url) {
		// 找到首次出现的双斜杠的位置
		int firstSlashPos = url.indexOf("//");
		if (firstSlashPos >= 0) {
			// 分割字符串
			String beforeFirstSlash = url.substring(0, firstSlashPos + 2); // 包括首次出现的双斜杠
			String afterFirstSlash = url.substring(firstSlashPos + 2);
			// 替换首次出现的双斜杠之后的所有双斜杠
			return beforeFirstSlash + afterFirstSlash.replace("//", "/");
		} else {
			// 如果首次出现的双斜杠都没有找到，直接返回原字符串
			return url;
		}
	}

	private static void downloadFileWithOkHttp(String siteUrl, FileDownloadInfo info, long fileSize, String localFilePath) throws IOException {
		// 下载URL
		String downUrl = replaceDoubleSlashesWithSingle(siteUrl + "/api/FileHelper/DownFile");
		int maxBufferSize = 1024 * 1024; // 最大缓冲区大小为1MB

		// 创建 OkHttpClient 实例
		OkHttpClient client = new OkHttpClient();

		// 在循环外部打开文件流
		try (FileOutputStream fileStream = new FileOutputStream(localFilePath, true)) {
			// 循环直到文件全部下载完成
			while (info.getOffSet() < fileSize) {
				int buffer = (int) ((fileSize - info.getOffSet()) <= maxBufferSize ? (fileSize - info.getOffSet()) : maxBufferSize);
				info.setBufferSize(buffer);

				// 将 FileInformation 对象序列化为 JSON 字符串
				String jsonInfo = new Gson().toJson(info);

				// 创建 RequestBody
				RequestBody requestBody = RequestBody.create(jsonInfo, MediaType.parse("application/json; charset=utf-8"));

				// 创建 HTTP POST 请求
				Request request = new Request.Builder()
					.url(downUrl)
					.post(requestBody)
					.build();

				try (Response response = client.newCall(request).execute();
					 InputStream inputStream = response.body().byteStream()) {
					// 检查 HTTP 响应状态码是否为成功
					if (!response.isSuccessful()) {
						throw new IOException("Unexpected code " + response);
					}

					// 逐块读取数据并写入文件
					byte[] buffers = new byte[1024];
					int bytesRead;
					while ((bytesRead = inputStream.read(buffers)) != -1) {
						fileStream.write(buffers, 0, bytesRead);
					}
				}

				// 更新偏移量
				info.setOffSet(info.getOffSet() + buffer);
			}
		}
	}

	/**
	 * 文件上传
	 *
	 * @param siteUrl        服务接口IP地址
	 * @param localFilePath  本地文件路径
	 * @param remoteFilePath 远程服务器文件路径
	 */
	public static void uploadFileEx(String siteUrl, String localFilePath, String remoteFilePath) {
		String uploadUrl = siteUrl + "/api/FileHelper/UploadFile";
		long fileSize = 0;
		int maxBufferSize = 1024 * 1024 * 1; // 缓冲区最大大小1MB
		int offSet = 0;

		try (FileInputStream stream = new FileInputStream(localFilePath)) {
			fileSize = stream.getChannel().size();
			FileUploadInfo info = new FileUploadInfo();
			info.setFilePath(remoteFilePath);

			while (offSet < fileSize) {
				int buffer = (int) Math.min(maxBufferSize, fileSize - offSet);
				byte[] bufferData = new byte[buffer];
				stream.read(bufferData, 0, bufferData.length);
				info.setFileData(bufferData);

				// 将FileUploadInfo对象序列化为JSON字符串
				String json = JsonUtil.toJson(info);

				// 创建OkHttp客户端
				OkHttpClient client = new OkHttpClient();
				// 创建RequestBody
				RequestBody body = RequestBody.create(MediaType.parse("application/json"), json);

				// 创建Request
				Request request = new Request.Builder()
					.url(uploadUrl)
					.post(body)
					.build();

				// 发送请求并处理响应
				Response response = client.newCall(request).execute();
				if (!response.isSuccessful()) {
					throw new IOException("Unexpected code " + response);
				}
				// 读取响应内容
				String result = response.body().string();
				System.out.println(result);

				offSet += buffer;
			}
		} catch (IOException e) {
			log.error("文件上传失败");
		}
	}

	public static boolean uploadFile(InputStream inputStream, String filePath, String fileName) {
		String jvmStartDirectory = System.getProperty("user.dir");
		filePath = jvmStartDirectory.substring(0, 2) + filePath;
		File file = new File(filePath);
		if (!file.exists()) {
			boolean mkdirs = file.mkdirs();
			log.warn("文件创建:{} 路径:{}", filePath + fileName, mkdirs);
		}
		try (FileOutputStream fileOutputStream = new FileOutputStream(file.getAbsolutePath() + File.separator + fileName);
			 InputStream input = inputStream) {

			byte[] buffer = new byte[BUFFER_SIZE];
			int bytesRead;
			while ((bytesRead = input.read(buffer)) != -1) {
				fileOutputStream.write(buffer, 0, bytesRead);
			}
			return true;
		} catch (IOException e) {
			// 记录错误日志
			log.error("文件上传失败", e);
			return false;
		}
	}


	private static boolean isLastChunk(String id, long totalFileSize) throws IOException {
		long currentTotalSize = Files.walk(Paths.get(TEMP_DIR))
			.filter(path -> path.toString().endsWith(id + ".tmp"))
			.mapToLong(path -> path.toFile().length())
			.sum();
		return currentTotalSize >= totalFileSize;
	}

	private static void mergeChunksIntoFinalFile(String id, Path finalFilePath) throws IOException {
//		Files.deleteIfExists(finalFilePath);
		try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(finalFilePath.toString()))) {
			Files.walk(Paths.get(TEMP_DIR)) // 遍历临时文件夹
				.filter(path -> path.toString().endsWith(id + ".tmp"))
				.sorted(Comparator.comparingLong(path -> Long.parseLong(path.getFileName().toString().split("_")[0])))
				.forEach(path -> {
					try (BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(path))) {
						byte[] buffer = new byte[4096];
						int bytesRead;
						while ((bytesRead = bis.read(buffer)) != -1) {
							bos.write(buffer, 0, bytesRead);
						}
					} catch (IOException e) {
						throw new RuntimeException("Error merging chunks.", e);
					}
				});
		}

		Files.walk(Paths.get(TEMP_DIR))
			.filter(path -> path.toString().endsWith(id + ".tmp"))
			.forEach(path -> {
				try {
					Files.delete(path);
				} catch (IOException e) {
					throw new RuntimeException("Error cleaning up temporary files.", e);
				}
			});
	}

	/**
	 * 判断文件是否被占用
	 *
	 * @param fileName 文件路径
	 * @return true表示正在使用，false表示没有使用
	 */
	public static boolean isFileInUse(String fileName) {
		File file = new File(fileName);
		try {
			// 尝试以独占模式打开文件进行写入
			FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.WRITE);
			try {
				// 获取独占锁
				FileLock lock = channel.tryLock();
				if (lock != null) {
					lock.release(); // 释放锁
					channel.close(); // 关闭通道
					return false; // 文件未被占用
				}
			} catch (IOException e) {
				// 如果获取锁失败，说明文件可能正在被其他进程使用
				return true;
			}
			channel.close();
		} catch (IOException e) {
			// 如果打开文件失败，比如权限问题等，这里也认为文件是“被占用”的一种情况
			return true;
		}
		return false;
	}

	public static String zipFileList(List<String> filePaths) {

		String relPath = Paths.get("DownLoad", UUID.randomUUID().toString()).toString();

		// 创建临时ZIP文件
		Path folder = Paths.get(System.getProperty("user.dir"), relPath);

		createDir(folder);
		for (String filePath : filePaths) {
			try {
				File file = new File(filePath);
				if (file.exists()) {
					Files.copy(file.toPath(), folder.resolve(file.getName()));
				} else {
					log.error("文件不存在或不是一个有效的文件: " + filePath);
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		//添加文件到ZIP 初始化ZipFile对象 设置压缩参数
		ZipParameters parameters = new ZipParameters();
		parameters.setCompressionMethod(CompressionMethod.DEFLATE);
		parameters.setCompressionLevel(CompressionLevel.NORMAL);

		try (ZipFile zip = new ZipFile(folder + ".zip")) {
			zip.addFolder(new File(folder.toString()), parameters);
			return relPath;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	public static String zipDir(String sourceFolderPath, String destZipFilePath) {
		try {
			File zipFile = new File(destZipFilePath + ".zip");
			ZipParameters parameters = new ZipParameters();
			parameters.setCompressionMethod(CompressionMethod.DEFLATE);
			parameters.setCompressionLevel(CompressionLevel.NORMAL);

			ZipFile zip = new ZipFile(zipFile);
			zip.addFolder(new File(sourceFolderPath), parameters);

			return zipFile.getAbsolutePath();
		} catch (ZipException e) {
			throw new RuntimeException(e);
		}
	}

	public static void createDir(Path path) {
		try {
			Files.createDirectories(path);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static boolean zipDir(String dirToZip, String zipFileName, int compressionLevel) {
		if (!zipFileName.toLowerCase().endsWith(".zip")) {
			zipFileName += ".zip";
		}

		File sourceDir = new File(dirToZip);
		if (!sourceDir.exists() || !sourceDir.isDirectory()) {
			log.error("源目录不存在或不是一个目录: " + dirToZip);
			return false;
		}

		File zipFile = new File(zipFileName);
		// 确保父目录存在
		File parentDir = zipFile.getParentFile();
		if (parentDir != null && !parentDir.exists()) {
			parentDir.mkdirs();
		}

		try (ZipFile zip = new ZipFile(zipFile)) {
			ZipParameters parameters = new ZipParameters();
			parameters.setCompressionMethod(CompressionMethod.DEFLATE);
			parameters.setCompressionLevel(getCompressionLevel(compressionLevel));

			@SuppressWarnings("unchecked")
			List<File> fileList = (List<File>) FileUtils.listFiles(sourceDir, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);

			for (File file : fileList) {
				if (file.isFile()) { // 明确判断是否为文件
					Path sourcePath = Paths.get(file.getAbsolutePath()).toAbsolutePath().normalize();
					Path sourceDirPath = sourceDir.toPath().toAbsolutePath().normalize();

					// ✅ 正确计算相对路径：文件路径相对于源目录
					Path relativePath = sourceDirPath.relativize(sourcePath);

					parameters.setFileNameInZip(relativePath.toString().replace("\\", "/")); // ZIP 内部使用正斜杠

					zip.addFile(file, parameters);
				}
			}

			return true;
		} catch (Exception e) {
			log.error("zipDir 错误: 压缩目录 [" + dirToZip + "] 到 [" + zipFileName + "] 失败", e);
			return false;
		}
	}

	private static CompressionLevel getCompressionLevel(int level) {
		switch (level) {
			case 0:
				return CompressionLevel.NO_COMPRESSION;
			case 1:
				return CompressionLevel.FASTEST;
			case 6:
				return CompressionLevel.FAST;
			case 9:
				return CompressionLevel.MAXIMUM;
			default:
				return CompressionLevel.NORMAL;
		}
	}
}

