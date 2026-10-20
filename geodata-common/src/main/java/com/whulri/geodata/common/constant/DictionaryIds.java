package com.whulri.geodata.common.constant;

/**
 * 与数据库字典表硬编码绑定的常量 id。
 *
 * <p>这些 GUID 直接对应 sdms_datatype / sdms_dictdatacode 中已存在的字典记录，
 * 是既成事实的耦合（历史数据以 GUID 落库）。修改前必须同时同步：
 * <ul>
 *   <li>数据库字典表记录本身</li>
 *   <li>前端 geodata-web-ui/src/utils/datasets.ts 中的同名常量</li>
 * </ul>
 */
public final class DictionaryIds {

    private DictionaryIds() {
    }

    /** 图层几何类型（sdms_layer.datatype）：点 */
    public static final String GEOM_POINT = "6b6941f1-67a3-11e7-8eb2-005056bb1c7e";

    /** 图层几何类型（sdms_layer.datatype）：线 */
    public static final String GEOM_LINE = "7776934c-67a3-11e7-8eb2-005056bb1c7e";

    /** 图层几何类型（sdms_layer.datatype）：面 */
    public static final String GEOM_POLYGON = "a2758dc0-67a3-11e7-8eb2-005056bb1c7e";

    /** 图层几何类型（sdms_layer.datatype）：影像 / 栅格 */
    public static final String GEOM_RASTER = "acf11b57-0626-4e49-b385-2e9a4195221c";

    /** 数据分类字典分类 id（sdms_datatype.dictCodeID） */
    public static final String CATALOG_DATA_CATEGORY = "ba5ab799-6acd-11e7-87f3-005056bb1c7e";
}
