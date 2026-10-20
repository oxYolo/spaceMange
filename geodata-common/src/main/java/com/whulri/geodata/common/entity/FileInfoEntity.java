package com.whulri.geodata.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;

@Data
@TableName("sdms_fileinfo")
@Schema(name = "FileInfo对象", description = "文件信息表")
public class FileInfoEntity {

	@TableId(value = "guid")
	@Schema(description = "guid")
	private String id;

    @Schema(description = "文件名称")
    @TableField("filename")
    private String fileName;

    @Schema(description = "文件类型")
    @TableField("filetype")
    private String fileType;

    @Schema(description = "文件后缀名")
    @TableField("fileext")
    private String fileExt;

    @Schema(description = "文件大小")
    @TableField("filesize")
    private String fileSize;

    @Schema(description = "数据来源")
    @TableField("datasrcource")
    private String datasrcource;

    @Schema(description = "供应者名字")
    @TableField("providername")
    private String providerName;

    @Schema(description = "储存类型")
    @TableField("storagetype")
    private String storageType;

    @Schema(description = "文件路径")
    @TableField("filepath")
    private String filePath;

    @Schema(description = "创建时间")
    @TableField("createdate")
    private LocalDateTime createDate;

    @Schema(description = "创建ID")
    @TableField("creatorid")
    private String creatorId;

    @Schema(description = "创建者")
    @TableField("creator")
    private String creator;

    @Schema(description = "目录id")
    @TableField("catid")
    private String catId;

    @Schema(description = "目录名字")
    @TableField("catname")
    private String catName;

    @Schema(description = "关联ID")
    @TableField("relationid")
    private String relationId;

    @Schema(description = "数据名称")
    @TableField("dataname")
    private String dataName;

    @Schema(description = "数据说明")
    @TableField("dataexplain")
    private String dataExplain;

    @Schema(description = "数据来源")
    @TableField("datasource")
    private String dataSource;

    @Schema(description = "数据机密")
    @TableField("dataconfidential")
    private String dataConfidential;

    @Schema(description = "服务对象")
    @TableField("serviceobject")
    private String serviceObject;

    @Schema(description = "关键词")
    @TableField("keywords")
    private String keyWords;

    @Schema(description = "数据描述")
    @TableField("datadesc")
    private String dataDesc;

    @Schema(description = "数据类型")
    @TableField("datatypes")
    private String dataTypes;
}
