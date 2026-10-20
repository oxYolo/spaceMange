package com.whulri.geodata.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;

import java.lang.reflect.Type;
import java.util.List;

public class JsonUtils {

    private static final ObjectMapper mapper = new ObjectMapper();

    static {
        // 全局配置一次即可（避免每次创建新实例）
        mapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
    }

    /**
     * 将 JSON 字符串反序列化为指定类型的对象
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return mapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON 解析失败: " + e.getMessage(), e);
        }
    }
}
