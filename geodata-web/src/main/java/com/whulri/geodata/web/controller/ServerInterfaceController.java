package com.whulri.geodata.web.controller;

import com.whulri.geodata.common.dto.layerContent.LayerContentInputDto;
import com.whulri.geodata.common.result.Result;
import com.whulri.geodata.datamanage.service.IServerInterfaceService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

/**
 * 服务信息
 */
@RestController
@RequestMapping("/serverInterface")
public class ServerInterfaceController {


    @Resource
    private IServerInterfaceService serverInterfaceService;

    /**
     * 服务发布
     *
     * @param dto
     * @return
     */
    @PostMapping("/publishService")
    public Result<?> publishService(@RequestBody LayerContentInputDto dto) {
        return Result.status(serverInterfaceService.publishService(dto));
    }

    /**
     * 服务信息
     *
     * @param dataId
     * @param serverType
     * @return
     */
    @GetMapping("/getServerInterface")
    public Result<?> getServerInterface(String dataId, String serverType) {
        return Result.data(serverInterfaceService.getServerInterface(dataId, serverType));
    }

    /**
     * 切片进度查询（基于 GeoServer GWC seed 任务）
     *
     * @param dataId 数据编号（图层ID）
     * @return status: IDLE-无任务 RUNNING-切片中 DONE-已完成 FAILED-查询失败
     */
    @GetMapping("/tileProgress")
    public Result<?> tileProgress(String dataId) {
        return Result.data(serverInterfaceService.getTileProgress(dataId));
    }
}
