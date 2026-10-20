package com.whulri.geodata.datamanage.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("com.whulri.geodata.datamanage.service")
@ComponentScan("com.whulri.geodata.datamanage.util")
public class DataManagementAutoConfiguration {

}
