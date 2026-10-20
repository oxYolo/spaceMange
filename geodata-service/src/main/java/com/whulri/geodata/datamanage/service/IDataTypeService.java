package com.whulri.geodata.datamanage.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.whulri.geodata.common.dto.dataType.DataTypeDto;
import com.whulri.geodata.common.dto.dataType.DataTypeInputDto;
import com.whulri.geodata.common.dto.dataType.DataTypeOutputDto;
import com.whulri.geodata.common.dto.dataType.DataTypeUpdateDto;
import com.whulri.geodata.common.entity.DataTypeEntity;

import java.util.List;

public interface IDataTypeService extends IService<DataTypeEntity> {

    void GetChild(String id, List<DataTypeEntity> dte, List<DataTypeEntity> entities);

    List<DataTypeDto> getAllList();

    List<DataTypeDto> getAllListByDataType(String dataType);

    DataTypeOutputDto getDetailById(String id);

    List<DataTypeDto> getDetailByName(String name, String dataType);

    DataTypeDto insert(DataTypeInputDto input);

    DataTypeDto update(DataTypeInputDto input);

    boolean delete(String id);

    boolean updateList(List<DataTypeUpdateDto> dataTypeUpdataDtoList);
}
