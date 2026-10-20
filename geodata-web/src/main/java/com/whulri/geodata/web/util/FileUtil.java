package com.whulri.geodata.web.util;

import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileUtil {
	/**
	 * file转MultipartFile
	 *
	 * @param file file
	 * @return MultipartFile
	 */
	public static MultipartFile fileToMultipartFile(File file) {
		MultipartFile result = null;
		if (null != file) {
			try (FileInputStream input = new FileInputStream(file)) {
				result = new MockMultipartFile(file.getName().concat("temp"), file.getName(), "text/plain", input);
			} catch (IOException e) {
				log.warn("File 转 MultipartFile 失败: file={}, err={}", file.getName(), e.getMessage(), e);
			}
		}
		return result;
	}
}
