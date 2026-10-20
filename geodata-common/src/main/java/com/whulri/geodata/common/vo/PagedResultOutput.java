package com.whulri.geodata.common.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@AllArgsConstructor
public class PagedResultOutput<T> implements Serializable {
    private long totalCount;
    private List<T> items;
}
