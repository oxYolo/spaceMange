package com.whulri.geodata.datatool.vector;

import lombok.Data;
import org.gdal.ogr.Feature;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;

/**
 * 一条要素的属性数据
 */
@Data
public class FeatAttrs implements Closeable {
    /**
     * 私有成员变量 id
     */
    private int oid;
    /**
     * 私有成员变量 属性Dic
     */
    private HashMap<String, String> attrValue;

    /**
     * 要素feature对象
     */
    private Feature refFeature;

    @Override
    public void close() throws IOException {
        if (null != refFeature) {
            refFeature.delete();
            refFeature = null;
        }
    }
}