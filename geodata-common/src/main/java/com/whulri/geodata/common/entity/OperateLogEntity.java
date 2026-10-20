package com.whulri.geodata.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

/**
 *
 * @TableName sdms_operatelog
 */
@TableName(value ="sdms_operatelog")
@Data
public class OperateLogEntity implements Serializable {
    /**
     *
     */
    @TableId(value = "\"Id\"", type = IdType.ASSIGN_UUID)
	@JsonProperty("Id")
    private String Id;

    /**
     * 操作用户ID
     */
	@TableField(value = "\"UserCode\"")
	@JsonProperty("UserCode")
    private String UserCode;

    /**
     * 系统功能（1-图层管理；2-地图管理；3-样式管理；null-无限制）
     */
	@TableField(value = "\"SystemFunc\"")
	@JsonProperty("SystemFunc")
    private String SystemFunc;

    /**
     * 操作类型（1-新增；2-编辑；3-导入；4-清空；5-删除；6-刷新；null-无限制）
     */
	@TableField(value = "\"OperateType\"")
	@JsonProperty("OperateType")
    private String OperateType;

    /**
     * 执行结果
     */
	@TableField(value = "\"Result\"")
	@JsonProperty("Result")
    private String Result;

    /**
     * 执行结果描述
     */
	@TableField(value = "\"ResultDescribe\"")
	@JsonProperty("ResultDescribe")
    private String ResultDescribe;

    /**
     * 图层ID
     */
	@TableField(value = "\"LayerID\"")
	@JsonProperty("LayerID")
    private String LayerID;

    /**
     * 操作用户名
     */
	@TableField(value = "\"UserName\"")
	@JsonProperty("UserName")
    private String UserName;

    /**
     * 操作时间
     */
	@TableField(value = "\"OperateTime\"")
	@JsonProperty("OperateTime")
    private LocalDateTime OperateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
