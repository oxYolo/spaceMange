package com.whulri.geodata.common.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 系统常量帮助类
 */
public final class ConstHelper {

    private ConstHelper() {
    }

    public static final String CONST_SYSTEMCODE = "0000";
    public static final String CONST_SYSTEMNAME = "System";
    public static final String UPLOAD_SUCCESS = "1";

    public static final String IMAGE_FORMAT = "IMAGE";
    public static final String VECTOR_FORMAT = "VECTOR";

    /**
     * 数据类型，矢量，影像文件
     */
    public static final Map<String, String> DATA_FORMAT_HELPER;

    static {
        Map<String, String> map = new HashMap<>();
        map.put(IMAGE_FORMAT, "application/openlayers");
        map.put(VECTOR_FORMAT, "image/png");
        DATA_FORMAT_HELPER = Collections.unmodifiableMap(map);
    }
}