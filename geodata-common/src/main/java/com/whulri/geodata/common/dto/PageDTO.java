package com.whulri.geodata.common.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class PageDTO implements Serializable {

    private Integer pageSize;

    private Integer pageIndex;

}
