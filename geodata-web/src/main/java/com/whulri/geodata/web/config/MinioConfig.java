package com.whulri.geodata.web.config;

import io.minio.MinioClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties("minio")
public class MinioConfig {

    private String url;
    private String bucket;
    private String accessKey;
    private String secretKey;

    @Bean
    public MinioClient getMinioClient() {
        return MinioClient.builder().endpoint(url)
                .credentials(accessKey, secretKey).build();
    }
}
