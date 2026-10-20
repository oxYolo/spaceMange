package com.whulri.geodata.datamanage.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.whulri.geodata.common.dto.dicDataType.DicDataTypeDto;
import com.whulri.geodata.common.entity.DicDataTypeEntity;
import com.whulri.geodata.common.result.ListResultOutput;

public interface IDicDataTypeService extends IService<DicDataTypeEntity> {

    ListResultOutput<DicDataTypeDto> getAllList();

    DicDataTypeDto getDetailById(String id);

    DicDataTypeDto insert(DicDataTypeDto input);

    DicDataTypeDto update(DicDataTypeDto input);

    boolean delete(String id);

}
