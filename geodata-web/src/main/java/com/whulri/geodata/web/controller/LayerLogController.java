package com.whulri.geodata.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whulri.geodata.common.entity.LayerLogEntity;
import com.whulri.geodata.common.result.Result;
import com.whulri.geodata.datamanage.service.ILayerLogService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("layerLog")
public class LayerLogController {

    @Autowired
    private ILayerLogService layerLogService;

    @Operation(summary = "历史记录", tags = "")
    @GetMapping("/getList")
    public Result getList(int pageSize, int pageIndex) {
        IPage page = new Page<>(pageIndex, pageSize);
        QueryWrapper<LayerLogEntity> qw = new QueryWrapper<>();
        qw.lambda().orderByDesc(LayerLogEntity::getCreateDT);
        IPage<LayerLogEntity> resultPage = layerLogService.page(page, qw);
        return Result.data(resultPage);
    }

    @Operation(summary = "删除记录", tags = "")
    @GetMapping("/delete")
    public Result delete(String id) {
        return Result.data(layerLogService.removeById(id));
    }

}
