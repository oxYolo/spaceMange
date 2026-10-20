package com.whulri.geodata.datamanage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.whulri.geodata.basecore.mapper.DictDataCodeMapper;
import com.whulri.geodata.common.dto.dicDataCode.DicDataCodeDto;
import com.whulri.geodata.common.dto.dicDataCode.DicDataCodeInputDto;
import com.whulri.geodata.common.entity.DictDataCodeEntity;
import com.whulri.geodata.common.result.ListResultOutput;
import com.whulri.geodata.datamanage.service.IDictDataCodeService;
import com.whulri.geodata.common.bridge.BeanUtil;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.UUID;

@Service
public class DictDataCodeServiceImpl extends ServiceImpl<DictDataCodeMapper, DictDataCodeEntity>
        implements IDictDataCodeService {

    @Resource
    private DictDataCodeMapper dictDataCodeMapper;

    @Override
    public ListResultOutput<DicDataCodeDto> GetDetailByTypeID(String typeId) {
        try {
            List<DictDataCodeEntity> dictDataCodeEntities = dictDataCodeMapper.selectList(new LambdaQueryWrapper<DictDataCodeEntity>().eq(DictDataCodeEntity::getDataTypeID, typeId));
            List<DicDataCodeDto> dicDataCodeDtos = BeanUtil.copy(dictDataCodeEntities, DicDataCodeDto.class);
            return new ListResultOutput<>(dicDataCodeDtos);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public ListResultOutput<DicDataCodeDto> getAllList() {
        try {
            List<DictDataCodeEntity> query = dictDataCodeMapper.selectList(new LambdaQueryWrapper<>());
            List<DicDataCodeDto> dicDataCodeDtos = BeanUtil.copy(query, DicDataCodeDto.class);
            return new ListResultOutput<>(dicDataCodeDtos);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ListResultOutput<DicDataCodeDto> getDetailByConn(String typeID, String keyWord) {
        try {
            List<DictDataCodeEntity> dictDataCodeEntities = dictDataCodeMapper.selectList(new LambdaQueryWrapper<DictDataCodeEntity>()
                    .eq(DictDataCodeEntity::getDataTypeID, typeID)
                    .eq(DictDataCodeEntity::getKeywords, keyWord));
            List<DicDataCodeDto> dicDataCodeDtos = BeanUtil.copy(dictDataCodeEntities, DicDataCodeDto.class);
            return new ListResultOutput<>(dicDataCodeDtos);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public DicDataCodeDto insert(DicDataCodeInputDto input) {
        try {
            input.setId(UUID.randomUUID().toString());
            DictDataCodeEntity entity = BeanUtil.copy(input, DictDataCodeEntity.class);
            dictDataCodeMapper.insert(entity);
            return BeanUtil.copy(entity, DicDataCodeDto.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public DicDataCodeDto update(DicDataCodeInputDto input) {
        try {
            DictDataCodeEntity entity = BeanUtil.copy(input, DictDataCodeEntity.class);
            dictDataCodeMapper.updateById(entity);
            return BeanUtil.copy(entity, DicDataCodeDto.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean delete(String id) {
        try {
            dictDataCodeMapper.deleteById(id);
            return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


}