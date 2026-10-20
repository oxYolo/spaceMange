package com.whulri.geodata.common.gdal.pojo;

import lombok.Data;

/**
 * @Author: Administrator
 * @Date: 2024/6/1916:15
 * @Description
 */
@Data
public class RequestInfo {

	/**
	 * 请求地址
	 */
	private String requestUrl;

	/**
	 * 请求参数
	 */
	private String requestParamter;

	/**
	 * 结果是否封装
	 */
	private boolean isResultWrap;

	/**
	 * Token信息
	 */
	private String token;
}
