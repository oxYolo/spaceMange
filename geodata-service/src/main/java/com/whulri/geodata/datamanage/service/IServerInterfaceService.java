package com.whulri.geodata.datamanage.service;

import com.alibaba.fastjson.JSONObject;
import com.whulri.geodata.common.dto.layerContent.LayerContentInputDto;
import com.whulri.geodata.common.dto.serverInterface.ServerInterfaceOutputDto;
import com.whulri.geodata.common.dto.serverInterface.TileProgressOutputDto;

import java.util.List;

public interface IServerInterfaceService {

    /**
     * 服务发布
     *
     * @param dto
     * @return
     */
    boolean publishService(LayerContentInputDto dto);

    /**
     * 服务信息
     *
     * @param dataId
     * @param serverType
     * @return
     */
    JSONObject getServerInterface(String dataId, String serverType);

    /**
     * 查询图层切片进度（基于 GeoServer GWC seed 任务）
     *
     * @param dataId 数据编号（图层ID）
     * @return 切片进度；status: IDLE/RUNNING/DONE/FAILED
     */
    TileProgressOutputDto getTileProgress(String dataId);

}
