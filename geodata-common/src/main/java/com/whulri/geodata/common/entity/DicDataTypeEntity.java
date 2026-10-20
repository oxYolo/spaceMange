package com.whulri.geodata.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@TableName(value = "sdms_dictdatatype")
public class DicDataTypeEntity {

    @TableId
    @Schema(description = "数据类型ID")
    private String id;

    @Schema(description = "类型名称")
    @TableField("typename")
    private String typeName;

    @Schema(description = "类型描述")
    @TableField("typedesc")
    private String typeDesc;

    @Schema(description = "类型编码")
    @TableField("typecode")
    private String typeCode;

    @Schema(description = "类型排序")
    @TableField("typesort")
    private Integer typeSort;

    @Schema(description = "父数据类型ID")
    @TableField("parentid")
    private String parentID;

    @Schema(description = "关键字")
    @TableField("keywords")
    private String keywords;
}
