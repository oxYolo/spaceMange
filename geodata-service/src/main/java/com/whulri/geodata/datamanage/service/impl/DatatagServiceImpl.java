package com.whulri.geodata.datamanage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.whulri.geodata.basecore.mapper.DataTagMapper;
import com.whulri.geodata.common.util.ConstHelper;
import com.whulri.geodata.common.dto.dataTag.DataTagDto;
import com.whulri.geodata.common.entity.DataTagEntity;
import com.whulri.geodata.common.entity.TagRelationEntity;
import com.whulri.geodata.datamanage.service.IDataTagService;
import com.whulri.geodata.datamanage.service.ITagRelationService;
import com.whulri.geodata.common.bridge.BeanUtil;
import com.whulri.geodata.common.bridge.Func;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DatatagServiceImpl extends ServiceImpl<DataTagMapper, DataTagEntity> implements IDataTagService {

    @Resource
    private ITagRelationService tagRelationService;

    @Override
    public List<DataTagDto> getAllList() {
        try {
            List<DataTagEntity> list = this.list();
            return BeanUtil.copy(list, DataTagDto.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<DataTagDto> getAllListByDataType(String dataType) {
        try {
            List<TagRelationEntity> list = tagRelationService.list();
            Set<String> collect = list.stream()
                    .flatMap(t -> Arrays.stream(t.getDataTagID().split(",")))
                    .collect(Collectors.toSet());
            LambdaQueryWrapper<DataTagEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(DataTagEntity::getDictCodeID, dataType)
                    .in(DataTagEntity::getId, collect);
            List<DataTagEntity> datatagList = this.list(wrapper);
            return BeanUtil.copy(datatagList, DataTagDto.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public DataTagDto getDetailById(String id) {
        try {
            DataTagEntity dataTagEntity = this.getById(id);
            return BeanUtil.copy(dataTagEntity, DataTagDto.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public DataTagDto insert(DataTagDto dataTagDto) {
        try {
            DataTagEntity dataTagEntity = new DataTagEntity(
                    UUID.randomUUID().toString(),
                    dataTagDto.getTagName(),
                    dataTagDto.getTagDesc(),
                    dataTagDto.getDictCodeID(),
                    dataTagDto.getCreateUser(),
                    Func.isNotEmpty(dataTagDto.getCreateUser()) && dataTagDto.getCreateUser().equals(ConstHelper.CONST_SYSTEMNAME) ? "0" : "1"
            );
            boolean save = this.save(dataTagEntity);
            if (save) {
                return BeanUtil.copy(dataTagEntity, DataTagDto.class);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    @Override
    public DataTagDto update(DataTagDto dataTagDto) {
        try {
            DataTagEntity dataTagEntity = new DataTagEntity(
                    dataTagDto.getId(),
                    dataTagDto.getTagName(),
                    dataTagDto.getTagDesc(),
                    dataTagDto.getDictCodeID(),
                    dataTagDto.getCreateUser(),
                    Func.isNotEmpty(dataTagDto.getCreateUser()) && dataTagDto.getCreateUser().equals(ConstHelper.CONST_SYSTEMNAME) ? "0" : "1"
            );
            boolean b = this.updateById(dataTagEntity);
            if (b) {
                return BeanUtil.copy(dataTagEntity, DataTagDto.class);
            }
        } catch (Exception e) {
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
}
