package com.whulri.geodata.datamanage.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.whulri.geodata.common.dto.tagRelation.TagDto;
import com.whulri.geodata.common.dto.tagRelation.TagRelationDto;
import com.whulri.geodata.common.dto.tagRelation.TagReleationInputDto;
import com.whulri.geodata.common.dto.tagRelation.TagReleationOutputDto;
import com.whulri.geodata.common.entity.TagRelationEntity;
import com.whulri.geodata.common.result.ListResultOutput;

public interface ITagRelationService extends IService<TagRelationEntity> {
    boolean MultiInsert(TagDto dto);

    boolean DeleteByMapID(String mapID);

    ListResultOutput<TagRelationDto> getAllList();

    TagReleationOutputDto getDetailById(String id);

    String getMultiTagNameByMapID(String mapLayerID);

    TagRelationDto insert(TagReleationInputDto input);

    TagRelationDto update(TagReleationInputDto input);

    boolean delete(String id);
}