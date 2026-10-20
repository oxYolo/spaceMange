package com.whulri.geodata.datarelease.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.whulri.geodata.common.dto.metadata.MetaDataDto;
import com.whulri.geodata.common.dto.metadata.MetaDataInputDto;
import com.whulri.geodata.common.dto.metadata.MetaDataOutputDto;
import com.whulri.geodata.common.entity.MetadataEntity;
import com.whulri.geodata.common.result.ListResultOutput;

public interface IMetadataService extends IService<MetadataEntity> {

    ListResultOutput<MetaDataDto> getAllList();

    MetaDataOutputDto getDetailById(String id);

    MetaDataDto insert(MetaDataInputDto input);

    MetaDataDto update(MetaDataInputDto input);

    boolean delete(String id);

    boolean deleteByDataId(String dataId);

    MetaDataOutputDto getDetailByDataId(String dataId);

}
