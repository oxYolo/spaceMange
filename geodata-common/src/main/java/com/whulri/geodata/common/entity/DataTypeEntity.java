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
 * 数据分类表
 *
 * @TableName sdms_datatype
 */
@TableName(value = "sdms_datatype")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DataTypeEntity implements Serializable {

    @TableId("id")
    @Schema(description = "数据类别ID")
    private String Id;

    @Schema(description = "类别名称")
    @TableField("typename")
    private String TypeName;

    @Schema(description = "类别描述")
    @TableField("typedesc")
    private String TypeDesc;

    @Schema(description = "字典代码")
    @TableField("dictcodeid")
    private String DictCodeID;

    @Schema(description = "父数据类别ID")
    @TableField("parentid")
    private String ParentID;

    @Schema(description = "创建人")
    @TableField("createuser")
    private String CreateUser;

    @Schema(description = "类型")
    @TableField("type")
    private String Type;

    @Schema(description = "排序")
    @TableField("codesort")
    private Integer CodeSort;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

}

