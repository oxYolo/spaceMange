package com.whulri.geodata.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * 实体类Map
 */
@Data
@TableName("sdms_map")
@Schema(name = "MapEntity 地图信息", description = "地图信息实体类")
public class MapEntity {

	/**
	 * 地图编号
	 */
	@TableId("id")
	@Schema(description = "地图编号")
	private String id;

	/**
	 * 地图名称
	 */
	@TableField(value = "mapname")
	@Schema(description = "地图名称")
	private String mapName;

	/**
	 * 地图英文名称
	 */
	@TableField(value = "mapenname")
	@Schema(description = "地图英文名称")
	private String mapEnName;

	/**
	 * 边界范围
	 */
	@TableField(value = "mapbbox")
	@Schema(description = "边界范围")
	private String mapBBox;

	/**
	 * 地图发布地址
	 */
	@TableField(value = "mappublishaddress")
	@Schema(description = "地图发布地址")
	private String mapPublishAddress;

	/**
	 * 地图状态
	 */
	@TableField(value = "mapstatus")
	@Schema(description = "地图状态")
	private String mapStatus;

	/**
	 * 地图描述
	 */
	@TableField(value = "mapdesc")
	@Schema(description = "地图描述")
	private String mapDesc;

	/**
	 * 地图分类
	 */
	@TableField(value = "maptype")
	@Schema(description = "地图分类")
	private String mapType;

	/**
	 * 地图标签
	 */
	@TableField(value = "maptag")
	@Schema(description = "地图标签")
	private String mapTag;

	/**
	 * 发布时间
	 */
	@TableField(value = "publishdt")
	@Schema(description = "发布时间")
	private LocalDateTime publishDT;

	/**
	 * 排序编号
	 */
	@TableField(value = "sortcode")
	@Schema(description = "排序编号")
	private Integer sortCode;

	/**
	 * 是否可用
	 */
	@TableField(value = "enabledmark")
	@Schema(description = "是否可用")
	private Integer enabledMark;

	/**
	 * 是否删除
	 */
	@TableField(value = "deletemark")
	@Schema(description = "是否删除")
	private Integer deleteMark;

	/**
	 * 创建人编号
	 */
	@TableField(value = "createuserid")
	@Schema(description = "创建人编号")
	private String createUserId;

	/**
	 * 创建人名称
	 */
	@TableField(value = "createusername")
	@Schema(description = "创建人名称")
	private String createUserName;

	/**
	 * 创建日期
	 */
	@TableField(value = "createdt")
	@Schema(description = "创建日期")
	private LocalDateTime createDT;

	/**
	 * 修改人编号
	 */
	@TableField(value = "modifyuserid")
	@Schema(description = "修改人编号")
	private String modifyUserId;

	/**
	 * 修改人名称
	 */
	@TableField(value = "modifyusername")
	@Schema(description = "修改人名称")
	private String modifyUserName;

	/**
	 * 修改日期
	 */
	@TableField(value = "modifydate")
	@Schema(description = "修改日期")
	private LocalDateTime modifyDate;

	/**
	 * 比例尺
	 */
	@TableField(value = "mapscale")
	@Schema(description = "比例尺")
	private String mapScale;

	/**
	 * 空间参考
	 */
	@TableField(value = "spatialrefence")
	@Schema(description = "空间参考")
	private String spatialRefence;

	/**
	 * north(北)
	 */
	@TableField(value = "maxy")
	@Schema(description = "north(北)")
	private BigDecimal maxY;

	/**
	 * south(南)
	 */
	@TableField(value = "miny")
	@Schema(description = "south(南)")
	private BigDecimal minY;

	/**
	 * west(西)
	 */
	@TableField(value = "minx")
	@Schema(description = "west(西)")
	private BigDecimal minX;

	/**
	 * east(东)
	 */
	@TableField(value = "maxx")
	@Schema(description = "east(东)")
	private BigDecimal maxX;

	/**
	 * 地图图例
	 */
	@TableField(value = "maplegend")
	@Schema(description = "地图图例")
	private String mapLegend;

	/**
	 * 数据来源
	 */
	@TableField(value = "datasource")
	@Schema(description = "数据来源")
	private String dataSource;

	/**
	 * 数据机密
	 */
	@TableField(value = "dataconfidential")
	@Schema(description = "数据机密")
	private String dataConfidential;

	/**
	 * 服务对象
	 */
	@TableField(value = "serviceobject")
	@Schema(description = "服务对象")
	private String serviceObject;

	/**
	 * 关键词
	 */
	@TableField(value = "keywords")
	@Schema(description = "关键词")
	private String keywords;

	/**
	 * 数据描述
	 */
	@TableField(value = "datadesc")
	@Schema(description = "数据描述")
	private String dataDesc;

	/**
	 * 数据类型
	 */
	@TableField(value = "datatypes")
	@Schema(description = "数据类型")
	private String dataTypes;

	/**
	 * 文件大小
	 */
	@TableField(value = "filesize")
	@Schema(description = "文件大小")
	private Integer fileSize;

	/**
	 * 数据精度
	 */
	@TableField(value = "dataaccuracy")
	@Schema(description = "数据精度")
	private String dataAccuracy;
}
