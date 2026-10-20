package com.whulri.geodata.convert.core;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 三维转换任务消息（业务侧 → MQ → 转换消费者）。
 */
@Data
public class ConvertTask implements Serializable {

    private static final long serialVersionUID = 1L;

    /** sdms_model3d.id */
    private String model3dId;

    /** 数据名称（日志/推送用） */
    private String name;

    /** 工具标识（与 ConvertCommandBuilder.toolId() / 配置 key 一致） */
    private String toolId;

    /** 源格式：osgb/las/laz/obj/fbx/ifc */
    private String srcFormat;

    /** 原始上传对象键（MinIO） */
    private String srcObjectKey;

    /** 上传文件名（决定 zip 解压还是直接拷贝） */
    private String fileName;

    /** 空间参考 */
    private String srs;

    /** 创建人（SignalR 定向推送用） */
    private String createBy;

    /** sdms_tool_his.id */
    private String taskHisId;

    /** 扩展参数（如 OSGB 的 --trans-location 等，由工具实现解释） */
    private Map<String, String> params;
}
