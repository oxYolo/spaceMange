package com.whulri.geodata.convert.core;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 三维转换总配置（geodata.convert.*）。
 *
 * <p>tools 的 key 与 {@link ConvertCommandBuilder#toolId()} 一致，
 * 例如 3dtiles / py3dtiles / obj2gltf / fbx2gltf / ifcconvert。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "geodata.convert")
public class ConvertProperties {

    /** 转换工作根目录（解压/产物临时区），默认系统临时目录 */
    private String workRoot = System.getProperty("java.io.tmpdir") + "/geodata-convert";

    /** 同实例最大并发转换数（转换属 CPU/IO 密集型，建议 1~2） */
    private int maxConcurrent = 1;

    /** 各工具配置，key = toolId */
    private Map<String, ConvertToolConfig> tools = new ConcurrentHashMap<>();

    public ConvertToolConfig getTool(String toolId) {
        return tools == null ? null : tools.get(toolId);
    }

    /** 取工具配置，未配置时返回默认开启的空配置（命令模板缺失会在构建命令时报错） */
    public ConvertToolConfig toolOrEmpty(String toolId) {
        ConvertToolConfig cfg = getTool(toolId);
        if (cfg == null) {
            cfg = new ConvertToolConfig();
            tools.put(toolId, cfg);
        }
        return cfg;
    }
}
