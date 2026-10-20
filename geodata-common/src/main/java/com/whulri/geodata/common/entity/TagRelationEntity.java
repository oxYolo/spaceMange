package com.whulri.geodata.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 数据标签关系表
 *
 * @TableName sdms_tag_releation
 */
@TableName(value = "sdms_tag_releation")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TagRelationEntity implements Serializable {
    /**
     * 标签关系ID
     */
    @TableId
    private String id;

    @Schema(description = "数据标签id")
    @TableField("datatagid")
    private String dataTagID;

    @Schema(description = "地图或图层id")
    @TableField("mapid")
    private String mapID;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}