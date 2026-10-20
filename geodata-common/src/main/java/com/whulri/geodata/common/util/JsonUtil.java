package com.whulri.geodata.common.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;

import java.lang.reflect.Type;
import java.util.List;

public class JsonUtil {

    @Getter
    private static final Gson gson = new Gson();

    public static String toJson(Object obj) {
        return gson.toJson(obj);
    }

    public static <T> T fromJson(String json, Class<T> t) {
        return gson.fromJson(json, t);
    }

    public static <T> T fromJson(String json, Type t) {
        return gson.fromJson(json, t);
    }
    public static <T> List<T> listFromJson(String json, TypeToken<List<T>> t) {
        return gson.fromJson(json, t.getType());
    }

}
