package com.whulri.geodata.common.dto;

import lombok.Data;

import java.util.Map;

@Data
public class AttributeObj {

	private int _oId;
	/// <summary>
	/// id
	/// </summary>
	public int oId;

	private Map<String, String> _attributeValue;
	/// <summary>
	/// 属性Dic
	/// </summary>
	public Map<String, String> attributeValue;
}
