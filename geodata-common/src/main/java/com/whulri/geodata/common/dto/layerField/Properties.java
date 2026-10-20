package com.whulri.geodata.common.dto.layerField;

import lombok.Data;

import java.util.List;

/**
 * 新增字段计算数据
 */
@Data
public class Properties {

    private String Symol;
    private List<String> DataSource;
    private List<String> OriginalToChange;
}
