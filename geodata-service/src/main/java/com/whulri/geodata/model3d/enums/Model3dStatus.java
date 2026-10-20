package com.whulri.geodata.model3d.enums;

/**
 * 三维数据处理状态（与 sdms_model3d.status 存储码一致）
 */
public enum Model3dStatus {

    /** 上传中（分片未合并） */
    UPLOADING("0", "上传中"),
    /** 已上传（现成 3D Tiles 已发布；其余等待转换） */
    UPLOADED("1", "已上传"),
    /** 已排队（转换任务已入 MQ） */
    QUEUED("2", "已排队"),
    /** 转换中 */
    CONVERTING("3", "转换中"),
    /** 已发布（可签发加载地址） */
    PUBLISHED("4", "已发布"),
    /** 失败 */
    FAILED("5", "失败");

    private final String code;
    private final String desc;

    Model3dStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
