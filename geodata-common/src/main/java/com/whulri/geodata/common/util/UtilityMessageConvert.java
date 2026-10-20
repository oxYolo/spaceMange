package com.whulri.geodata.common.util;

import com.whulri.geodata.common.config.AppSettingConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class UtilityMessageConvert {

    private static List<CMessage> listCMessage = new ArrayList<>();
    //    private static AppSettingConfig appSettingConfig;
    private static String filePath;

    private static final ReentrantLock INIT_LOCK = new ReentrantLock();
    private static boolean initialized = false;

    @Resource
    private AppSettingConfig appSettingConfig;  // 实例级别的配置注入

//    @PostConstruct
//    public void init() {
//        // 将实例配置桥接到静态变量
//        appSettingConfig = instanceConfig;
//        // 延迟初始化文件路径
//        filePath = Paths.get(System.getProperty("user.dir"),
//                        appSettingConfig.getLanguagePackFilePath())
//                .toString();
//        // 可选：预加载消息列表
//        initMessage();
//    }

    public String get(String value) {
        if (listCMessage.isEmpty()) {
            initMessage();
        }

        filePath = Paths.get(System.getProperty("user.dir"), appSettingConfig.getLanguagePackFilePath()).toString();

        CMessage cMessage = listCMessage.stream()
                .filter(t -> t.getCN().equals(value) || t.getId().equals(value))
                .findFirst()
                .orElse(null);

        String key = appSettingConfig.getLanguage();
        if (cMessage != null && key != null && key.equalsIgnoreCase("ENGLISH")) {
            return cMessage.getEN();
        }
        return value;
    }

    /**
     * 初始化消息列表（线程安全）
     */
    private static void initMessage() {
        if (initialized) return;

        INIT_LOCK.lock();
        try {
            if (initialized) return; // 双重检查

            String path = filePath;
            if (path == null || path.trim().isEmpty()) {
                // 默认路径：当前工作目录/Data/Message.txt
                path = Paths.get(System.getProperty("user.dir"), "Data", "Message.txt").toString();
            }

            Path filePath = Paths.get(path);

            try (BufferedReader reader = Files.newBufferedReader(filePath)) {
                loadFromReader(reader);
            } catch (IOException e) {
                System.err.println("Failed to read message file: " + e.getMessage());
            }
            initialized = true;
        } finally {
            INIT_LOCK.unlock();
        }
    }

    /**
     * 从 Reader 加载消息
     */
    private static void loadFromReader(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            // 跳过空行和注释行（以 # 开头）
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            String[] parts = line.split("#", -1); // -1 保留尾部空字符串
            if (parts.length >= 3) {
                String id = parts[0].trim();
                String cn = parts[1].trim();
                String en = parts[2].trim();
                listCMessage.add(new CMessage(id, cn, en));
            }
        }
    }

    @Data
    @AllArgsConstructor
    private static class CMessage {
        private String id;
        private String CN;
        private String EN;
    }
}
