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
 * 数据标签表
 * @TableName sdms_datatag
 */
@TableName(value ="sdms_datatag")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DataTagEntity implements Serializable {

    @TableId("id")
	@Schema(description = "数据标签ID")
    private String id;

	@Schema(description = "标签名称")
	@TableField("tagname")
    private String tagName;

	@Schema(description = "标签备注")
	@TableField("tagdesc")
    private String tagDesc;

	@Schema(description = "标签代码类型")
	@TableField("dictcodeid")
    private String dictCodeID;

	@Schema(description = "创建人")
	@TableField("createuser")
    private String createUser;

	@Schema(description = "类型")
	@TableField("type")
    private String type;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
