package com.whulri.geodata.common.result;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class PageResultDTO<T> implements Serializable {

    private List<T> records;

    private String layerName;

    private String layerId;

    private Integer total;

}
