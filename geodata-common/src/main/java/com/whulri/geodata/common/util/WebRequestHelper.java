package com.whulri.geodata.common.util;


import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.whulri.geodata.common.gdal.pojo.RequestInfo;
import com.whulri.geodata.common.gdal.pojo.WebApiResult;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import com.whulri.geodata.common.bridge.Func;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 该类可以使用hutool的httpUtil替代
 */
@Slf4j
public class WebRequestHelper {

    public static final OkHttpClient client = OkHttpUtil.getInstance();
    public static final Gson gson = JsonUtil.getGson();

    /**
     * 发起GET请求并返回泛型类型的结果
     *
     * @param info 请求信息
     * @return 返回泛型类型的结果
     */
    public static <T> T get(RequestInfo info, Class<T> tClass) {

        Request.Builder requestBuild = new Request.Builder()
                .get()
                .url(info.getRequestUrl());

        if (Func.isNotBlank(info.getToken())) {
            requestBuild.addHeader("Authorization", info.getToken());
        }

        try {
            String resp = sendHttp(requestBuild.build(), info);
            if (!info.getRequestUrl().contains("app/layerContent/GetLayerAttrTabledDetail")) {
                log.info("请求参数:{}, 请求响应:{}", JsonUtil.toJson(info), resp);
            }
            return dealResult(resp, info, tClass);
        } catch (Exception e) {
            log.error("请求错误:{},{}", info.getRequestUrl(), e.getMessage());
            throw new RuntimeException(e);
        }


    }


    /**
     * 执行POST请求并返回指定类型的对象
     *
     * @param info 请求信息
     * @return 泛型对象
     */
    public static <T> T post(RequestInfo info, Class<T> tClass) {

        Request.Builder requestBuild = new Request.Builder()
                .url(info.getRequestUrl());

        // 设置Authorization头
        if (Func.isNotBlank(info.getToken())) {
            requestBuild.addHeader("Authorization", info.getToken());
        }

        // 设置请求参数
        if (Func.isNotBlank(info.getRequestParamter())) {
            RequestBody body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), info.getRequestParamter());
            requestBuild.post(body);
        }
        try {
            log.info("请求路径:{},请求参数:{}", info.getRequestUrl(), gson.toJson(info.getRequestParamter()));
            String resp = sendHttp(requestBuild.build(), info);
            log.info("请求路径:{} 请求响应:{}", info.getRequestUrl(), resp);
            return dealResult(resp, info, tClass);
        } catch (Exception e) {
            log.error("请求错误:{},{}", info.getRequestUrl(), e.getMessage());
            return null;
        }

    }

    /**
     * 发起GET请求并返回泛型类型的结果
     *
     * @param info 请求信息
     * @return 返回泛型类型的结果
     */
    public static <T> List<T> getList(RequestInfo info, Class<T> tClass) {

        Request.Builder requestBuild = new Request.Builder()
                .get()
                .url(info.getRequestUrl());

        if (Func.isNotBlank(info.getToken())) {
            requestBuild.addHeader("Authorization", info.getToken());
        }

        try {
            String resp = sendHttp(requestBuild.build(), info);
            log.info("请求路径:{},请求参数:{}, 请求响应:{}", info.getRequestUrl(), info.getRequestParamter(), resp);
            return dealResults(resp, info, tClass);
        } catch (Exception e) {
            log.error("请求错误:{}", info.getRequestUrl(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 执行POST请求并返回指定类型的对象
     *
     * @param info 请求信息
     * @return 泛型对象
     */
    public static <T> List<T> postList(RequestInfo info, Class<T> tClass) {

        Request.Builder requestBuild = new Request.Builder()
                .url(info.getRequestUrl());

        // 设置Authorization头
        if (Func.isNotBlank(info.getToken())) {
            requestBuild.addHeader("Authorization", info.getToken());
        }

        // 设置请求参数
        if (Func.isNotBlank(info.getRequestParamter())) {
            RequestBody body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), info.getRequestParamter());
            requestBuild.post(body);
        }
        try {
            String resp = sendHttp(requestBuild.build(), info);
            log.info("请求路径:{} ,请求参数:{}, 请求响应:{}", info.getRequestUrl(), info.getRequestParamter(), resp);
            return dealResults(resp, info, tClass);
        } catch (Exception e) {
            log.error("请求错误:{}", info.getRequestUrl(), e);
            return new ArrayList<>();
        }

    }

    private static String sendHttp(Request request, RequestInfo info) throws IOException {

        // 设置超时时间为5秒
        client.newBuilder().build();

        Response response = client.newCall(request).execute();

        if (!response.isSuccessful() || response.code() != 200 || response.body() == null) {
            log.error("请求路径:{}, 请求响应", info.getRequestUrl());
            return null;
        }

        //默认UTF-8
        return response.body().string();

    }

    public static <T> T dealResult(String resp, RequestInfo info, Class<T> tClass) {

        if (Func.isBlank(resp)) {
            return null;
            //return BeanUtils.instantiateClass(tClass);
        }
        String regex = "^\\s*(?:\\[.*\\]|\\{.*\\})\\s*$";
        if (!resp.matches(regex)) {
            return new Gson().fromJson(resp, tClass);
        }

        //如果响应不是包装
        if (!info.isResultWrap()) {
            // 尝试将字符串作为JSON对象处理
            return new Gson().fromJson(resp, tClass);


        }

        //处理响应被包装的场景
        JsonElement jsonElement = JsonParser.parseString(resp);
        boolean success = jsonElement.getAsJsonObject().get("success").getAsBoolean();
        if (success) {
            JsonElement result = jsonElement.getAsJsonObject().get("result");
            if (Func.isBlank(result.toString())) {
                return null;
            }
            return new Gson().fromJson(result, tClass);
        } else {
            return null;
        }

    }

    public static <T> List<T> dealResults(String resp, RequestInfo info, Class<T> tClass) {

        if (Func.isBlank(resp) || resp.equals("[]")) {
            return null;
        }

        //如果响应不是包装
        if (!info.isResultWrap()) {
            return JsonUtil.listFromJson(resp, new TypeToken<List<T>>() {
            });
        }

        //处理响应被包装的场景
        JsonElement jsonElement = JsonParser.parseString(resp);
        boolean success = jsonElement.getAsJsonObject().get("success").getAsBoolean();
        if (success) {
            JsonElement resultJson = jsonElement.getAsJsonObject().get("result");
            return JsonUtil.listFromJson(resultJson.toString(), new TypeToken<List<T>>() {
            });
        } else {
            return null;
        }

    }

    /**
     * Get请求
     *
     * @param info 请求信息
     * @return 请求结果
     */
    public static String Get(RequestInfo info) {
        Request request = new Request.Builder()
                .url(info.getRequestUrl())
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                String result = response.body().string();
                if (!info.isResultWrap()) {
                    return result;
                }
                JsonObject json = JsonParser.parseString(result).getAsJsonObject();
                boolean success = json.get("Success").getAsBoolean();
                if (success) {
                    return json.get("Result").getAsString();
                }
            }
        } catch (IOException e) {
            log.warn("GET 请求失败: {}", e.getMessage(), e);
        }
        return null;
    }

    /**
     * Post请求
     *
     * @param info 请求信息
     * @return Web API 结果
     */
    public static WebApiResult Post(RequestInfo info) {
        RequestBody body = null;
        if (info.getRequestParamter() != null && !info.getRequestParamter().isEmpty()) {
            body = RequestBody.create(info.getRequestParamter(), MediaType.get("application/json; charset=utf-8"));
        }
        Request request = new Request.Builder()
                .url(info.getRequestUrl())
                .post(body)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                String result = response.body().string();
                if (!info.isResultWrap()) {
                    return gson.fromJson(result, WebApiResult.class);
                }
                JsonObject json = JsonParser.parseString(result).getAsJsonObject();
                boolean success = json.has("Success") && json.get("Success").getAsBoolean();
                if (success) {
                    JsonObject jo = json.getAsJsonObject("Result");
                    if (jo != null) {
                        return gson.fromJson(jo.toString(), WebApiResult.class);
                    }
                }
            }
        } catch (IOException e) {
            WebApiResult infoWR = new WebApiResult();
            infoWR.setSuccess(false);
            infoWR.setMessage(e.getMessage());
            return infoWR;
        }
        return null;
    }

    /**
     * Post(统计)
     *
     * @param uri   请求地址
     * @param fuhui 回复信息
     * @return 统计结果
     */
    public static String PostFunction(String uri, String fuhui) {
        Request request = new Request.Builder()
                .url(uri)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                String returnXml = response.body().string();
                JsonObject jboct = JsonParser.parseString(returnXml).getAsJsonObject();
                String result = jboct.get("result").getAsString();
                result = result.replace("\n", "").replace("[", "").replace("]", "");
                if (!result.isEmpty()) {
                    jboct = JsonParser.parseString(result).getAsJsonObject();
                    if ("图名".equals(fuhui)) {
                        return jboct.get("styleName").getAsString();
                    } else {
                        return jboct.get("styleContent").getAsString();
                    }
                }
            }
        } catch (IOException e) {
            log.warn("获取样式内容失败: {}", e.getMessage(), e);
        }
        return null;
    }

    /**
     * GET请求
     *
     * @param url 请求地址
     * @return 请求结果
     */
    public static String HttpGet(String url) {
        Request request = new Request.Builder()
                .url(url)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return response.body().string();
            }
        } catch (IOException e) {
            log.warn("HTTP 请求失败: {}", e.getMessage(), e);
        }
        return null;
    }

    /**
     * 下载文件
     *
     * @param url  文件地址
     * @param path 保存路径
     */
    public static void DownLoad(String url, String path) {
        Request request = new Request.Builder()
                .url(url)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                java.nio.file.Files.write(java.nio.file.Paths.get(path), response.body().bytes());
            }
        } catch (IOException e) {
            log.warn("下载文件失败: url={}, path={}, err={}", url, path, e.getMessage(), e);
        }
    }

}
