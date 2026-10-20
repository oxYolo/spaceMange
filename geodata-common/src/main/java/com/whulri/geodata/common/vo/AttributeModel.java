package com.whulri.geodata.common.vo;

import com.whulri.geodata.common.enums.FieldTypeEnum;
import lombok.Data;

@Data
public class AttributeModel {

	/**
	 * 属性字段名
	 */
	private String AttributeName;

	/**
	 * 属性字段类型
	 */
	private FieldTypeEnum AttributeType;

	/**
	 * 属性字段名称
	 */
	private String AttributeTypeName;

	/**
	 * 属性字段长度
	 */
	private Integer AttributeWidth;

	/**
	 * 精度值
	 */
	private Integer AttributePrecision;

	private Integer AttributeApproxOK;
}
