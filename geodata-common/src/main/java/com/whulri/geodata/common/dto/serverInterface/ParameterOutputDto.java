package com.whulri.geodata.common.dto.serverInterface;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParameterOutputDto {
	/**
	 * 参数名称
	 */
	@JsonProperty("参数名称")
	private String parameterName;

	/**
	 * 参数类型
	 */
	@JsonProperty("参数类型")
	private String parameterType;

	/**
	 * 参数值
	 */
	@JsonProperty("参数值")
	private String parameterValue;

	/**
	 * 是否为可选参数
	 */
	@JsonProperty("是否为可选参数")
	private String parameterIsMust;

	/**
	 * 参数描述
	 */
	@JsonProperty("参数描述")
	private String parameterDesc;

	/**
	 * 排序
	 */
	@JsonProperty("排序")
	private Integer parameterSort;
}
