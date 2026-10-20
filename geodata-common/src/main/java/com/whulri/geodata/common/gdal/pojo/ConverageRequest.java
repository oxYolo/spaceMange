package com.whulri.geodata.common.gdal.pojo;

import lombok.Data;

@Data
public class ConverageRequest
{
    public Coverage Coverage;

    public ConverageRequest(Coverage coverage) {
        Coverage = coverage;
    }
    public ConverageRequest() {

    }
}
