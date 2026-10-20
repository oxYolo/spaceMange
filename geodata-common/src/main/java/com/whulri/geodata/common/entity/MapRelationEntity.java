package com.whulri.geodata.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@TableName("sdms_map_releation")
@Schema(name = "MapRelationEntity 地图关联实体", description = "地图关联表")
@AllArgsConstructor
@NoArgsConstructor
public class MapRelationEntity {


    @TableId
    @Schema(description = "地图关联ID")
    private String id;

    @TableField(value = "mapid")
    @Schema(description = "地图ID")
    private String mapId;

    @TableField(value = "dataconfigid")
    @Schema(description = "图层目录ID")
    private String dataConfigID;

    @TableField(value = "datastyleid")
    @Schema(description = "图层样式ID")
    private String dataStyleID;

    @TableField(value = "datasort")
    @Schema(description = "图层排序")
    private Integer dataSort;

    @TableField(value = "configdt")
    @Schema(description = "配置日期")
    private LocalDateTime configDT;

    @TableField(value = "modifydt")
    @Schema(description = "修改日期")
    private LocalDateTime modifyDT;

    @TableField(value = "minscaledenominator")
    @Schema(description = "最小比例尺")
    private String minScaleDenominator;

    @TableField(value = "maxscaledenominator")
    @Schema(description = "最大比例尺")
    private String maxScaleDenominator;

    @TableField(value = "stylename")
    @Schema(description = "名称")
    private String styleName;
}
