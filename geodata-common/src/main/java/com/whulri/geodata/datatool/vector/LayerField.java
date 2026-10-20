package com.whulri.geodata.datatool.vector;


import lombok.Data;

/**
 * 属性表字段名及字段类型
 */
@Data
public class LayerField {
    /**
     * 属性名
     */
    private String fieldName;

    /**
     * 属性类型
     */
    private String fieldType;
}