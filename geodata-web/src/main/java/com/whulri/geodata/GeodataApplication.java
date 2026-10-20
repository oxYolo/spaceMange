package com.whulri.geodata;

import com.whulri.common.security.annotation.EnableCustomConfig;
import com.whulri.common.security.annotation.EnableWhulriFeignClients;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 空间数据管理微服务启动类
 *
 * <p>迁移自 dmsp-parent（SpringBlade 2.7 / Java 11）→ Boot 3.3.5 / Java 17 / whulri 基座。
 * 包结构已统一规范至 com.whulri.geodata 命名空间（common / basecore / datatool /
 * datamanage / datarelease / web / model3d），由 scanBasePackages 统一扫描。</p>
 */
@EnableCustomConfig
@EnableWhulriFeignClients
@SpringBootApplication(scanBasePackages = {"com.whulri"})
@MapperScan({"com.whulri.geodata.basecore.mapper", "com.whulri.geodata.model3d.mapper"})
@EnableScheduling
public class GeodataApplication {

    public static void main(String[] args) {
        SpringApplication.run(GeodataApplication.class, args);
        System.out.println(
                "(♥◠‿◠)ﾉﾞ  geodata-service 空间数据管理服务启动成功   ლ(´ڡ`ლ)ﾞ");
    }
}
