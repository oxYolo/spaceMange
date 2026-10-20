package com.whulri.geodata.datamanage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.whulri.geodata.basecore.mapper.DictDataTypeMapper;
import com.whulri.geodata.common.dto.dicDataType.DicDataTypeDto;
import com.whulri.geodata.common.entity.DicDataTypeEntity;
import com.whulri.geodata.common.result.ListResultOutput;
import com.whulri.geodata.datamanage.service.IDicDataTypeService;
import com.whulri.geodata.common.bridge.BeanUtil;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.UUID;

@Service
public class DicDataTypeServiceImpl extends ServiceImpl<DictDataTypeMapper, DicDataTypeEntity> implements IDicDataTypeService {

    @Resource
    private DictDataTypeMapper dictDataTypeMapper;

    @Override
    public ListResultOutput<DicDataTypeDto> getAllList() {
        try {
            List<DicDataTypeEntity> dicDataTypeEntities = dictDataTypeMapper.selectList(new LambdaQueryWrapper<>());
            List<DicDataTypeDto> dicDataTypeDtos = BeanUtil.copy(dicDataTypeEntities, DicDataTypeDto.class);
            return new ListResultOutput<>(dicDataTypeDtos);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public DicDataTypeDto getDetailById(String id) {
        try {
            DicDataTypeEntity dicDataTypeEntity = dictDataTypeMapper.selectById(id);
            return BeanUtil.copy(dicDataTypeEntity, DicDataTypeDto.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public DicDataTypeDto insert(DicDataTypeDto input) {
        try {
            input.setId(UUID.randomUUID().toString());
            DicDataTypeEntity dicDataTypeEntity = BeanUtil.copy(input, DicDataTypeEntity.class);
            dictDataTypeMapper.insert(dicDataTypeEntity);
            return input;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public DicDataTypeDto update(DicDataTypeDto input) {
        try {
            DicDataTypeEntity dicDataTypeEntity = BeanUtil.copy(input, DicDataTypeEntity.class);
            dictDataTypeMapper.updateById(dicDataTypeEntity);
            return input;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean delete(String id) {
        try {
            dictDataTypeMapper.deleteById(id);
            return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
