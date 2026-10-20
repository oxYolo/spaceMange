package com.whulri.geodata.common.util;

import com.whulri.geodata.common.config.AppSettingConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

@Slf4j
@Component
@RefreshScope
public class ThumbnailHelper {

    @Resource
    private AppSettingConfig appSettingConfig;

    /**
     * 生成缩略图
     */
    public String createThumbnail(String imageName, String type, String bbox, String uploadFileType, String uploadFileSrs) {
        String requestUrl = getWMSRequestUrl(imageName, bbox, uploadFileType, uploadFileSrs);
        byte[] byteImage = getImageByte(requestUrl);
        return saveThumbnail(byteImage, type, imageName);
    }


    /**
     * 保存图片
     */
    public String saveThumbnail(byte[] thumbnail, String type, String fileName) {
        if (thumbnail == null || thumbnail.length == 0) {
            return "";
        }

        try {
            Path basePath = Paths.get(System.getProperty("user.dir"), appSettingConfig.getThumbnailPath(), type);
            if (!Files.exists(basePath)) {
                Files.createDirectories(basePath);
            }

            Path filePath = basePath.resolve(fileName + ".png");

            try (ByteArrayInputStream bis = new ByteArrayInputStream(thumbnail)) {
                BufferedImage image = ImageIO.read(bis);
                if (Files.exists(filePath)) {
                    Files.delete(filePath);
                }
                ImageIO.write(image, "PNG", filePath.toFile());
            }

            return filePath.toString();
        } catch (Exception ex) {
            log.error("Error saving thumbnail: " + fileName, ex);
            writeError(thumbnail);
            return "";
        }
    }

    /**
     * 生成本地缩略图
     */
    public String createLocalThumbnail(String imageName, String type, String bbox, String uploadFileType, String uploadFileSrs) {
        String requestUrl = getWMSRequestUrl(imageName, bbox, uploadFileType, uploadFileSrs);
        byte[] byteImage = getImageByte(requestUrl);
        return saveLocalThumbnail(byteImage, type, imageName);
    }

    /**
     * 保存本地图片
     */
    public String saveLocalThumbnail(byte[] thumbnail, String type, String fileName) {
        if (thumbnail == null || thumbnail.length == 0) {
            return "";
        }

        try {
            Path basePath = Paths.get(System.getProperty("user.dir"), "ThumbnailPath", type);
            if (!Files.exists(basePath)) {
                Files.createDirectories(basePath);
            }

            Path filePath = basePath.resolve(fileName + ".png");

            try (ByteArrayInputStream bis = new ByteArrayInputStream(thumbnail)) {
                BufferedImage image = ImageIO.read(bis);
                if (Files.exists(filePath)) {
                    Files.delete(filePath);
                }
                ImageIO.write(image, "PNG", filePath.toFile());
            }

            return filePath.toString();
        } catch (Exception ex) {
            log.error("Error saving local thumbnail: " + fileName, ex);
            writeError(thumbnail);
            return "";
        }
    }

    /**
     * 错误输出
     */
    private void writeError(byte[] message) {
        try {
            Path errorPath = Paths.get(System.getProperty("user.dir"), "tupak.txt");
            Files.write(errorPath, message);
        } catch (IOException ex) {
            log.error("Error writing error log", ex);
        }
    }

    /**
     * 组织wms服务URL
     */
    public String getWMSRequestUrl(String name, String bbox, String uploadFileType, String uploadFileSrs) {
        String geoWorkSpace = appSettingConfig.getGeoWorkspace();
        StringBuilder requestUrl = new StringBuilder();
        requestUrl.append(String.format("http://%s:%s/geoserver/%s/%s", appSettingConfig.getGeoServerIP(), appSettingConfig.getGeoServerPort(), geoWorkSpace, "wms"));
        requestUrl.append("?service=WMS");
        requestUrl.append("&version=1.1.0");
        requestUrl.append("&request=GetMap");
        requestUrl.append("&layers=").append(geoWorkSpace == null || geoWorkSpace.isEmpty() ? name : (geoWorkSpace + ":" + name));
        requestUrl.append("&bbox=").append(bbox);
        requestUrl.append("&styles=");
        requestUrl.append("&width=196");
        requestUrl.append("&height=171");

        String srs = appSettingConfig.getEpsg();
        if (uploadFileType != null && !uploadFileType.isEmpty()) {
            if (uploadFileType.equals("2") && uploadFileSrs != null && !uploadFileSrs.isEmpty()) {
                srs = uploadFileSrs;
            }
        }
        requestUrl.append("&srs=").append(srs);
        requestUrl.append("&format=image/png");

        return requestUrl.toString();
    }

    /**
     * 获取图片
     */
    public byte[] getImageByte(String requestUrl) {
        HttpURLConnection connection = null;
        int retryCount = 0;
        final int MAX_RETRIES = 2;

        while (retryCount < MAX_RETRIES) {
            try {
                System.gc();
                URL url = new URL(requestUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                String auth = Base64.getEncoder().encodeToString(
                        (appSettingConfig.getGeoServerUser() + ":" + appSettingConfig.getGeoServerPwd()).getBytes());
                connection.setRequestProperty("Authorization", "Basic " + auth);

                connection.setRequestProperty("Content-Type", "text/html");
                connection.setRequestProperty("Accept", "application/octet-stream");
                connection.setUseCaches(false);
                connection.setConnectTimeout(600000); // 10 minutes

                try (InputStream in = connection.getInputStream();
                     ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                    return out.toByteArray();
                }
            } catch (Exception ex) {
                log.error("Error getting image from URL: " + requestUrl, ex);
                retryCount++;
                if (connection != null) {
                    connection.disconnect();
                }
                if (retryCount < MAX_RETRIES) {
                    continue;
                }
            }
        }
        return null;
    }

    /**
     * 删除缩略图
     */
    public boolean deleteThumbnail(String type, String fileName) {
        try {
            Path thumbnailFile = Paths.get(System.getProperty("user.dir"), appSettingConfig.getThumbnailPath(), type, fileName + ".png");
            if (Files.exists(thumbnailFile)) {
                Files.delete(thumbnailFile);
            }
            return true;
        } catch (Exception ex) {
            log.error("Error deleting thumbnail: " + fileName, ex);
            return false;
        }
    }
}
