package com.whulri.geodata.datamanage.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.whulri.geodata.basecore.mapper.DataInformationMapper;
import com.whulri.geodata.common.entity.DataInformationEntity;
import com.whulri.geodata.datamanage.service.DataInformationBaseService;
import org.springframework.stereotype.Service;

@Service
public class DataInformationBaseServiceImpl extends ServiceImpl<DataInformationMapper, DataInformationEntity>
	implements DataInformationBaseService {
}
