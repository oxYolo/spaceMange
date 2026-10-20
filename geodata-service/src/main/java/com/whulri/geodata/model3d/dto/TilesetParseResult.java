package com.whulri.geodata.model3d.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * tileset.json 解析结果
 */
@Data
public class TilesetParseResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 3D Tiles asset.version */
    private String version;

    /** 地理包围盒 [xmin,ymin,zmin,xmax,ymax,zmax]（region 已换算为经纬度度） */
    private List<Double> bbox;

    /** root.transform 16 元组（可空） */
    private List<Double> transform;

    /** 瓦片节点总数 */
    private Long tileCount;

    /** LOD 层级深度 */
    private Integer lodDepth;

    /** 根节点几何误差 */
    private Double geometricError;
}
