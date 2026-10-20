package com.whulri.geodata.common.util;

import okhttp3.OkHttpClient;

import java.util.concurrent.TimeUnit;

public class OkHttpUtil {


    private static final int CONNECT_TIMEOUT = 10;
    private static final int READ_TIMEOUT = 30;
    private static final int WRITE_TIMEOUT = 30;

    private static OkHttpClient instance;

    private OkHttpUtil() {}

    public static OkHttpClient getInstance() {
        if (instance == null) {
            synchronized (OkHttpUtil.class) {
                if (instance == null) {
//                    HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
//                    loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

                    instance = new OkHttpClient.Builder()
                            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
                            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
                            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
//                            .addInterceptor(loggingInterceptor)
                            .build();
                }
            }
        }
        return instance;
    }
}
