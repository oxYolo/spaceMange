package com.whulri.geodata.model3d.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 三维数据上传初始化参数
 */
@Data
public class Model3dUploadInitDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 数据名称（必填） */
    private String name;

    /** 目录 id（sdms_directorys，可空） */
    private String directoryId;

    /** 数据类型 TILESET/OSGB/POINTCLOUD/SINGLE/BIM（必填） */
    private String modelType;

    /** 源格式 tileset/osgb/las/laz/obj/fbx/ifc（必填） */
    private String srcFormat;

    /** 上传文件名（必填，zip 或单文件如 xx.las） */
    private String fileName;

    /** 文件总字节数（可空，用于校验） */
    private Long totalSize;

    /** 分片总数（≥1） */
    private Integer partCount;

    /** 空间参考（如 EPSG:4326，可空） */
    private String srs;

    /**
     * 操作人编码（可空）。无登录态（AuthUtil.getNickName() 为空）时作为兜底操作人，
     * 同时决定 SignalR 实时进度（Model3dConvert）的推送目标，须与浏览器连接 ?name= 一致。
     */
    private String userCode;
}
