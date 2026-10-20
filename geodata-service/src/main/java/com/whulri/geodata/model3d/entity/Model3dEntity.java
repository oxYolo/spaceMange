package com.whulri.geodata.model3d.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 三维数据管理实体（sdms_model3d）
 */
@Data
@TableName("sdms_model3d")
public class Model3dEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private String id;

    /** 数据名称 */
    @TableField("name")
    private String name;

    /** 目录 id（sdms_directorys） */
    @TableField("directory_id")
    private String directoryId;

    /** 数据类型 TILESET/OSGB/POINTCLOUD/SINGLE/BIM */
    @TableField("model_type")
    private String modelType;

    /** 源格式 tileset/osgb/las/laz/obj/fbx/ifc */
    @TableField("src_format")
    private String srcFormat;

    /** 上传源文件名（zip 或单文件） */
    @TableField("file_name")
    private String fileName;

    /** 源文件总字节数 */
    @TableField("total_size")
    private Long totalSize;

    /** 分片总数 */
    @TableField("part_count")
    private Integer partCount;

    /** 空间参考 */
    @TableField("srs")
    private String srs;

    /** OSGB 局部原点 X */
    @TableField("origin_x")
    private Double originX;

    /** OSGB 局部原点 Y */
    @TableField("origin_y")
    private Double originY;

    /** OSGB 局部原点 Z */
    @TableField("origin_z")
    private Double originZ;

    /** 地理包围盒 JSON [xmin,ymin,zmin,xmax,ymax,zmax] */
    @TableField("bbox")
    private String bbox;

    /** 3D Tiles root.transform 16 元组 JSON */
    @TableField("transformation")
    private String transformation;

    /** 瓦片节点总数 */
    @TableField("tile_count")
    private Long tileCount;

    /** LOD 层级深度 */
    @TableField("lod_depth")
    private Integer lodDepth;

    /** 根节点几何误差 */
    @TableField("geometric_error")
    private Double geometricError;

    /** 原始上传对象键 */
    @TableField("src_object_key")
    private String srcObjectKey;

    /** 发布成果对象键（tileset.json 或单体 glb/gltf） */
    @TableField("publish_object_key")
    private String publishObjectKey;

    /** 处理进度 0-100 */
    @TableField("progress")
    private Integer progress;

    /** 状态 0上传中/1已上传/2已排队/3转换中/4已发布/5失败 */
    @TableField("status")
    private String status;

    /** 失败原因 */
    @TableField("fail_reason")
    private String failReason;

    /** 转换任务 id（sdms_tool_his.id） */
    @TableField("task_id")
    private String taskId;

    /** 转换执行实例标识（host 或 geodata.convert.instance-id），用于服务重启后定位本实例残留任务 */
    @TableField("convert_owner")
    private String convertOwner;

    /** 转换心跳时间（转换期间周期刷新，看门狗据此判定并回收僵尸任务） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("heartbeat_time")
    private LocalDateTime heartbeatTime;

    @TableField("create_user")
    private String createUser;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_user")
    private String updateUser;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("update_time")
    private LocalDateTime updateTime;
}
