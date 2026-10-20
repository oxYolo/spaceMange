package com.whulri.geodata.common.dto.layerTool;

import lombok.Data;

/**
 * 重分类参数
 * @author zzw
 * @date 2026/3/17 11:23
 */
@Data
public class ReclassificationDTO {

    /**
     * 分类类型  1:分类   2:唯一值
     */
    private Integer type;

    /**
     * 分类类型为1的时候[{max:4.125,min:3.2231,value:1},{max:4.125,min:3.2231,value:1},{max:4.125,min:3.2231,value:1}]
     * 分类类型为2的时候[{old:4.123,value:1},{old:4.123,value:1},{old:4.123,value:1}]
     */
    private String matrix;

}
