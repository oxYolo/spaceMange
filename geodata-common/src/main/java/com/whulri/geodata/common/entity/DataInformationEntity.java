package com.whulri.geodata.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 *
 * @TableName sdms_datainformation
 */
@TableName(value ="sdms_datainformation")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DataInformationEntity implements Serializable {
    /**
     *
     */
    @TableId
    private String id;

	@Schema(description = "数据类型(图层，模型，文档)")
	@TableField("datatype")
    private String dataType;

	@Schema(description = "各种数据的id")
	@TableField("dataid")
    private String dataId;

	@Schema(description = "数据名称")
	@TableField("dataname")
    private String dataName;

	@Schema(description = "数据的各种格式(图层里的类型，模型里的类型，文档里的类型，视频里的类型，图片里的类型)")
	@TableField("dataformat")
    private String dataFormat;

	@Schema(description = "资源目录的id")
	@TableField("datatypeid")
    private String datatypeID;

	@Schema(description = "标签")
	@TableField("tag")
    private String tag;

	@Schema(description = "创建者")
	@TableField("createuser")
    private String createUser;


	@Schema(description = "实体类")
	@TableField("attribute")
    private String attribute;


	@Schema(description = "创建者")
	@TableField("codesort")
    private Integer codeSort;

	@Schema(description = "")
	@TableField("createdt")
    private Date createDT;

	@Schema(description = "创建者")
	@TableField("\"public\"")
    private String Public;

	@Schema(description = "自编分类")
	@TableField("othertype")
    private String otherType;

	@Schema(description = "上传图层样式（1-矢量图层；2-影像图层）")
	@TableField("layertype")
    private String layertype;

	@Schema(description = "图片请求")
	@TableField("picture")
    private String picture;

	@Schema(description = "文件大小KB")
	@TableField("filesize")
    private Long fileSize;

	@Schema(description = "高程基准")
	@TableField("gcjz")
    private String gcjz;

	@Schema(description = "所属项目")
	@TableField("ssxm")
    private String ssxm;

	@Schema(description = "文件名称")
	@TableField("filenames")
    private String fileNames;

	@Schema(description = "位置信息")
	@TableField("location")
    private String location;

	@Schema(description = "")
	@TableField("resourceclass")
    private String resourceclass;

	@Schema(description = "年份")
	@TableField("year")
    private String year;

	@Schema(description = "")
	@TableField("institution")
    private String institution;

	@Schema(description = "市")
	@TableField("city")
    private String city;

	@Schema(description = "市名称")
	@TableField("cityname")
    private String cityname;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

}
