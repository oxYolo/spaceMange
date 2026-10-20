package com.whulri.geodata.datamanage.service;

import com.whulri.geodata.datamanage.dto.VectorEditDTO;

/**
 * 矢量编辑服务接口
 */
public interface IVectorEditService {

    /**
     * 编辑矢量要素
     * @param dto 编辑参数（包含layerId和geoJsonList）
     * @return 是否成功
     */
    boolean editVectorFeatures(VectorEditDTO dto);
}
