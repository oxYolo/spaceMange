package com.whulri.geodata.datamanage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 实时通讯signalR配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "imservice")
public class IMConfig {

	private Integer imPort;

	private String imIP;

	private Boolean isEnableSSL;

}
