package com.whulri.geodata.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sdms_tool_his")
public class ToolHisEntity {

    @TableId(value = "id")
    private String Id;

    /**
     * 图层id
     */
    @TableField(value = "layerId")
    private String layerId;

    /**
     * 图层名称
     */
    @TableField(value = "layername")
    private String LayerName;

    /**
     * 图层业务表
     */
    @TableField(value = "layerattrtable")
    private String LayerAttrTable;

    /**
     * 创建时间
     */
    @TableField(value = "createdt")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime CreateDT;

    /**
     * 创建人
     */
    @TableField(value = "createby")
    private String CreateBy;

    /**
     * 状态 0计算中，1成功，2失败
     */
    @TableField(value = "status")
    private String status;

    /**
     * //1:裁剪  2:重采样  3:栅格矢量化  4:矢量栅格化  5:重分类   6:擦除   7:缓冲区   8:矢量交并补   9:栅格叠加计算
     */
    @TableField(value = "origin")
    private String origin;

    /**
     * 是否存储 0是1否
     */
    @TableField(value = "storage")
    private String storage;

    /**
     * 上传图层样式（1-矢量图层；2-影像图层）
     */
    @TableField(value = "uploadfiletype")
    private String UploadFileType;

    /**
     * 详情信息（JSON格式，前端自行解析）
     */
    @TableField(value = "detailjson")
    private String detailJson;
}
