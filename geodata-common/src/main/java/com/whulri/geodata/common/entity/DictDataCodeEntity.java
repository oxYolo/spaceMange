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
 * 基本代码字典表
 *
 * @TableName sdms_dictdatacode
 */
@TableName(value = "sdms_dictdatacode")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DictDataCodeEntity implements Serializable {

    @TableId
    @Schema(description = "代码ID")
    private String id;

    @Schema(description = "代码名称")
    @TableField("codename")
    private String codeName;

    @Schema(description = "代码值")
    @TableField("codevalue")
    private String codeValue;

    @Schema(description = "代码描述")
    @TableField("codedesc")
    private String codeDesc;

    @Schema(description = "代码类型")
    @TableField("datatypeid")
    private String dataTypeID;

    @Schema(description = "代码排序")
    @TableField("codesort")
    private int codeSort;

    @Schema(description = "备注")
    @TableField("remark")
    private String remark;

    @Schema(description = "关键字")
    @TableField("keywords")
    private String keywords;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
