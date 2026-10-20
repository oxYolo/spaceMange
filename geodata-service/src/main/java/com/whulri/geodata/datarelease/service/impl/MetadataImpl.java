package com.whulri.geodata.datarelease.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.whulri.geodata.basecore.mapper.MetadataMapper;
import com.whulri.geodata.common.dto.metadata.MetaDataDto;
import com.whulri.geodata.common.dto.metadata.MetaDataInputDto;
import com.whulri.geodata.common.dto.metadata.MetaDataOutputDto;
import com.whulri.geodata.common.entity.MetadataEntity;
import com.whulri.geodata.common.result.ListResultOutput;
import com.whulri.geodata.datarelease.service.IMetadataService;
import com.whulri.geodata.common.bridge.BeanUtil;
import com.whulri.geodata.common.bridge.Func;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MetadataImpl extends ServiceImpl<MetadataMapper, MetadataEntity> implements IMetadataService {

    @Resource
    private MetadataMapper metadataMapper;

    @Override
    public ListResultOutput<MetaDataDto> getAllList() {
        try {
            List<MetadataEntity> mapMetadataEntities = metadataMapper.selectList(new LambdaQueryWrapper<>());
            List<MetaDataDto> mapMetaDataDtos = BeanUtil.copy(mapMetadataEntities, MetaDataDto.class);
            return new ListResultOutput<>(mapMetaDataDtos);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public MetaDataOutputDto getDetailById(String id) {
        try {
            MetadataEntity mapMetadataEntity = metadataMapper.selectById(id);
            return BeanUtil.copy(mapMetadataEntity, MetaDataOutputDto.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public MetaDataDto insert(MetaDataInputDto input) {
        try {
            input.setId(UUID.randomUUID().toString());
            MetadataEntity entity = BeanUtil.copy(input, MetadataEntity.class);
            metadataMapper.insert(entity);
            return BeanUtil.copy(entity, MetaDataDto.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public MetaDataDto update(MetaDataInputDto input) {
        try {
            MetadataEntity entity = BeanUtil.copy(input, MetadataEntity.class);
            metadataMapper.updateById(entity);
            return BeanUtil.copy(entity, MetaDataDto.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean delete(String id) {
        try {
            metadataMapper.deleteById(id);
            return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean deleteByDataId(String dataId) {
        try {
            List<MetadataEntity> mapMetadataEntities = metadataMapper.selectList(new LambdaQueryWrapper<MetadataEntity>()
                    .eq(MetadataEntity::getDataId, dataId));
            if (Func.isNotEmpty(mapMetadataEntities)) {
                List<String> list = mapMetadataEntities.stream()
                        .map(MetadataEntity::getId)
                        .collect(Collectors.toList());
                metadataMapper.deleteBatchIds(list);
            }
            return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public MetaDataOutputDto getDetailByDataId(String dataId) {
        try {
            MetadataEntity metadataEntities = metadataMapper.selectOne(new LambdaQueryWrapper<MetadataEntity>()
                    .eq(MetadataEntity::getDataId, dataId));
            return BeanUtil.copy(metadataEntities, MetaDataOutputDto.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
}
