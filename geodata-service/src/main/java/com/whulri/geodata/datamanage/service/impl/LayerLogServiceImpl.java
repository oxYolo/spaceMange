package com.whulri.geodata.datamanage.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.whulri.geodata.basecore.mapper.LayerLogMapper;
import com.whulri.geodata.common.entity.LayerLogEntity;
import com.whulri.geodata.datamanage.service.ILayerLogService;
import org.springframework.stereotype.Service;

@Service
public class LayerLogServiceImpl extends ServiceImpl<LayerLogMapper, LayerLogEntity> implements ILayerLogService {
}
