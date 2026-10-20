package com.whulri.geodata.common.dto.serverInterface;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.whulri.geodata.common.dto.LayerFieldQuery;
import lombok.Data;

import java.util.List;

@Data
public class ServerInterfaceOutputDto {
    /**
     * 服务名称
     */
    @JsonProperty("服务名称")
    private String serverName;

    /**
     * 服务描述
     */
    @JsonProperty("服务描述")
    private String serverDesc;

    /**
     * 请求类型
     */
    @JsonProperty("请求类型")
    private String requestType;

    /**
     * 服务路径
     */
    @JsonProperty("服务路径")
    private String serverPath;

    /**
     * 排序
     */
    @JsonProperty("排序")
    private Integer serverSort;

    /**
     * 关键字
     */
    @JsonProperty("关键字")
    private String serverKeyWord;

    /**
     * 服务请求参数列表
     */
    @JsonProperty("服务请求参数列表")
    private List<ParameterOutputDto> serverRequestParameters;

    /**
     * 服务返回参数列表
     */
    @JsonProperty("服务返回参数列表")
    private List<ParameterOutputDto> serverResponseParameters;


    /**
     * 服务返回参数列表
     */
    @JsonProperty("服务请求体参数列表")
    private List<ParameterOutputDto> serverRequestBodyParameters;
}
