package com.whulri.geodata.datamanage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.whulri.geodata.basecore.mapper.DataTypeMapper;
import com.whulri.geodata.common.dto.dataType.DataTypeUpdateDto;
import com.whulri.geodata.common.util.ConstHelper;
import com.whulri.geodata.common.dto.dataType.DataTypeDto;
import com.whulri.geodata.common.dto.dataType.DataTypeInputDto;
import com.whulri.geodata.common.dto.dataType.DataTypeOutputDto;
import com.whulri.geodata.common.entity.DataTypeEntity;
import com.whulri.geodata.datamanage.service.IDataTypeService;
import com.whulri.geodata.common.bridge.BeanUtil;
import com.whulri.geodata.common.bridge.Func;
import org.springframework.beans.BeansException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DataTypeServiceImpl extends ServiceImpl<DataTypeMapper, DataTypeEntity> implements IDataTypeService {

    @Resource
    private DataTypeMapper dataTypeMapper;

    @Override
    public void GetChild(String id, List<DataTypeEntity> dte, List<DataTypeEntity> entities) {
        if (Func.isNotEmpty(id)) {
            List<DataTypeEntity> list = dte.stream().filter(x -> Objects.equals(id, x.getParentID())).collect(Collectors.toList());
            if (Func.isNotEmpty(list)) {
                for (DataTypeEntity item : list) {
                    entities.add(item);
                    GetChild(item.getId(), dte, entities);
                }
            }
        }
    }

    @Override
    public List<DataTypeDto> getAllList() {
        try {
            List<DataTypeEntity> query = this.list();
            return BeanUtil.copyProperties(query, DataTypeDto.class);
        } catch (BeansException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<DataTypeDto> getAllListByDataType(String dataType) {
        try {
            List<DataTypeEntity> collect = this.list().stream()
                    .filter(s -> Func.isNotEmpty(s.getDictCodeID()) && dataType.contains(s.getDictCodeID()))
                    .collect(Collectors.toList());
            return BeanUtil.copy(collect, DataTypeDto.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public DataTypeOutputDto getDetailById(String id) {
        try {
            DataTypeEntity query = this.getById(id);
            return BeanUtil.copyProperties(query, DataTypeOutputDto.class);
        } catch (BeansException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<DataTypeDto> getDetailByName(String name, String dataType) {
        try {
            List<DataTypeEntity> dataTypeEntities = dataTypeMapper.selectList(new LambdaQueryWrapper<DataTypeEntity>()
                    .eq(DataTypeEntity::getTypeName, name)
                    .eq(DataTypeEntity::getDictCodeID, dataType));
            return BeanUtil.copy(dataTypeEntities, DataTypeDto.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public DataTypeDto insert(DataTypeInputDto input) {
        try {
            DataTypeEntity dataTypeEntity = new DataTypeEntity(
                    UUID.randomUUID().toString(),
                    input.getTypeName(),
                    input.getTypeDesc(),
                    input.getDictCodeID(),
                    input.getParentID(),
                    input.getCreateUser(),
                    Func.isNotEmpty(input.getCreateUser()) && input.getCreateUser().equals(ConstHelper.CONST_SYSTEMNAME) ? "0" : "1",
                    input.getCodeSort()
            );
            boolean save = this.save(dataTypeEntity);
            if (save) {
                return BeanUtil.copyProperties(dataTypeEntity, DataTypeDto.class);
            }
        } catch (BeansException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    @Override
    public DataTypeDto update(DataTypeInputDto input) {
        try {
            DataTypeEntity dataTypeEntity = new DataTypeEntity(
                    input.getId(),
                    input.getTypeName(),
                    input.getTypeDesc(),
                    input.getDictCodeID(),
                    input.getParentID(),
                    input.getCreateUser(),
                    Func.isNotEmpty(input.getCreateUser()) && input.getCreateUser().equals(ConstHelper.CONST_SYSTEMNAME) ? "0" : "1",
                    input.getCodeSort()
            );
            boolean update = this.updateById(dataTypeEntity);
            if (update) {
                return BeanUtil.copyProperties(dataTypeEntity, DataTypeDto.class);
            }
        } catch (BeansException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    @Override
    public boolean delete(String id) {
        try {
            return this.removeById(id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Transactional
    @Override
    public boolean updateList(List<DataTypeUpdateDto> dataTypeUpdataDtoList) {
        try {
            List<DataTypeEntity> dataTypeEntities = dataTypeUpdataDtoList.stream().map(x -> {
                        DataTypeEntity entity = new DataTypeEntity();
                        entity.setId(x.getId());
                        entity.setParentID(x.getParentId());
                        entity.setCodeSort(x.getCodeSort());
                        return entity;
                    }
            ).collect(Collectors.toList());
            this.updateBatchById(dataTypeEntities);
            return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


}
