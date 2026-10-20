package com.whulri.geodata.model3d.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 三维数据分页查询参数
 */
@Data
public class Model3dPageQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 名称模糊查询 */
    private String keyword;

    /** 目录 id */
    private String directoryId;

    /** 数据类型 */
    private String modelType;

    /** 状态码 */
    private String status;

    /** 页码（1 起） */
    private long pageNum = 1;

    /** 页大小 */
    private long pageSize = 10;
}
