package com.whulri.geodata.basecore.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("com.whulri.geodata.basecore.service")
@MapperScan("com.whulri.geodata.basecore.mapper")
public class BaseCoreAutoConfiguration {

}
