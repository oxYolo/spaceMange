package com.whulri.geodata.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
@Slf4j
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path basePath = Paths.get(System.getProperty("user.dir"), "file");
        String absolutePath = basePath.toAbsolutePath().toString();

        // 确保路径以 / 结尾（Windows 和 Linux 兼容）
        if (!absolutePath.endsWith(File.separator)) {
            absolutePath += File.separator;
        }

        log.info("📁 静态资源映射: URL [/file/shp/**] -> 文件系统 [{}]", absolutePath);

        // 添加资源处理器，映射URL路径到文件系统路径
        registry.addResourceHandler("/file/**")
                .addResourceLocations("file:" + absolutePath);
    }
}
