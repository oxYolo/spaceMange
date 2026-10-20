package com.whulri.geodata.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "spring.datasource.dynamic.datasource.master")
public class DataBaseConfig {
	private String url;
	private String username;
	private String password;
	private String driverClassName;
}
