package com.whulri.geodata.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("sdms_layer_log")
public class LayerLogEntity {

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String Id;

    /**
     * 图层名称
     */
    @TableField(value = "layername")
    private String LayerName;

    /**
     * 图层id
     */
    @TableField(value = "layerid")
    private String LayerId;

    /**
     * 上传图层样式（1-矢量图层；2-影像图层）
     */
    @TableField(value = "uploadfiletype")
    private String UploadFileType;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "createdt")
    private LocalDateTime CreateDT;

    /**
     * 拥有者
     */
    @TableField(value = "createby")
    private String CreateBy;

    /**
     * 状态
     */
    @TableField(value = "status")
    private String status;

    /**
     * 失败原因
     */
    @TableField(value = "reason")
    private String reason;


}
