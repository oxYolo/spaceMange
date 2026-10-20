package com.whulri.geodata.convert.osgb;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OSGB metadata.xml 解析结果（Smart3D/ContextCapture 产出）
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OsgbMetadata {

    /** SRS 描述串（如 ENU:121.5,31.2,10 或 WKT 片段） */
    private String srs;

    /** 局部原点 X */
    private Double originX;

    /** 局部原点 Y */
    private Double originY;

    /** 局部原点 Z */
    private Double originZ;
}
