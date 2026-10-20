package com.whulri.geodata.datamanage.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.whulri.geodata.common.dto.dataTag.DataTagDto;
import com.whulri.geodata.common.entity.DataTagEntity;

import java.util.List;

public interface IDataTagService extends IService<DataTagEntity> {

    List<DataTagDto> getAllList();

    List<DataTagDto> getAllListByDataType(String dataType);

    DataTagDto getDetailById(String id);

    DataTagDto insert(DataTagDto dataTagDto);

    DataTagDto update(DataTagDto dataTagDto);

    boolean delete(String id);
}
