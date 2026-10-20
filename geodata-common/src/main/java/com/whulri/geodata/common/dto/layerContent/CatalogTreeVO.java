package com.whulri.geodata.common.dto.layerContent;

import com.whulri.geodata.common.entity.LayerContentEntity;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CatalogTreeVO {
    private String id;
    private String dataTypeId;
    private String parentId;
    private String name; // 假设目录表有 name 字段
    private LayerContentEntity layerContent;

    //    private List<LayerContentEntity> directLayers; // 本目录直接图层
    private Integer directLayerCount;              // 直接图层数
    private Integer totalLayerCount;               // 自身 + 所有子孙图层数
    private Integer sort;           // 排序

    private List<CatalogTreeVO> children = new ArrayList<>(); // 子目录（递归）
}
