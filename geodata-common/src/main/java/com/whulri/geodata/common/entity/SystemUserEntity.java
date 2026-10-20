package com.whulri.geodata.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实体类 SystemUserEntity
 * 对应数据库表：sdms_user
 */
@Data
@TableName("sdms_user")
public class SystemUserEntity {


    /**
     * 主键 ID
     */
    @TableId(value = "id", type = IdType.INPUT)
    private String Id;

    /**
     * 姓名
     */
    @TableField("user_name")
    private String UserName;

    /**
     * 登录帐号
     */
    @TableField("user_code")
    private String UserCode;

    /**
     * 性别
     */
    @TableField("user_sex")
    private String UserSex;

    /**
     * 登录密码
     */
    @TableField("password")
    private String Password;

    /**
     * 联系电话
     */
    @TableField("tel_phone")
    private String TelPhone;

    /**
     * 联系手机
     */
    @TableField("phone")
    private String Phone;

    /**
     * 部门
     */
    @TableField("department")
    private String Department;

    /**
     * 职务（
     */
    @TableField("position")
    private String Position;

    /**
     * 创建时间
     */
    @TableField("create_dt")
    private LocalDateTime CreateDT;

    /**
     * 说明
     */
    @TableField("remark")
    private String Remark;
}
