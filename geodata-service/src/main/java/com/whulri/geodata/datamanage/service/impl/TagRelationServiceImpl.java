package com.whulri.geodata.datamanage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.whulri.geodata.basecore.mapper.DataTagMapper;
import com.whulri.geodata.basecore.mapper.TagRelationMapper;
import com.whulri.geodata.common.dto.tagRelation.TagDto;
import com.whulri.geodata.common.dto.tagRelation.TagRelationDto;
import com.whulri.geodata.common.dto.tagRelation.TagReleationInputDto;
import com.whulri.geodata.common.dto.tagRelation.TagReleationOutputDto;
import com.whulri.geodata.common.entity.DataTagEntity;
import com.whulri.geodata.common.entity.TagRelationEntity;
import com.whulri.geodata.common.result.ListResultOutput;
import com.whulri.geodata.common.util.ConstHelper;
import com.whulri.geodata.datamanage.service.ITagRelationService;
import com.whulri.geodata.common.bridge.BeanUtil;
import com.whulri.geodata.common.bridge.Func;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class TagRelationServiceImpl extends ServiceImpl<TagRelationMapper, TagRelationEntity>
        implements ITagRelationService {

    @Resource
    private TagRelationMapper tagRelationMapper;
    @Resource
    private DataTagMapper dataTagMapper;

    @Override
    public boolean MultiInsert(TagDto dto) {

        try {
            String tagName = dto.getTagName();
            String dataID = dto.getDataID();
            StringBuilder mapLayerID = new StringBuilder(dto.getMapLayerID());

            tagRelationMapper.delete(new LambdaQueryWrapper<TagRelationEntity>()
                    .eq(TagRelationEntity::getMapID, mapLayerID));

            String multiTagID = "";
            String[] arr;
            if (Func.isNotEmpty(dto.getTagName())) {
                tagName = dto.getTagName()
                        .replace("，", ",")
                        .replace(" ", ",")
                        .replace("　", ",");

                if (tagName.contains(",")) {
                    arr = Arrays.stream(tagName.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .toArray(String[]::new);
                } else {
                    arr = new String[]{tagName};
                }
                for (String tag : arr) {
                    String tagID = "";

                    DataTagEntity one = dataTagMapper.selectOne(new LambdaQueryWrapper<DataTagEntity>()
                            .eq(DataTagEntity::getTagName, tag)
                            .eq(DataTagEntity::getDictCodeID, dataID));
                    if (one != null && one.getId() != null) {
                        tagID = one.getId();
                    } else {
                        DataTagEntity newTag = new DataTagEntity();
                        newTag.setId(UUID.randomUUID().toString());
                        newTag.setTagName(tag);
                        newTag.setDictCodeID(dataID);
                        newTag.setCreateUser(dto.getCreateUser());
                        newTag.setType((Func.isNotEmpty(dto.getCreateUser())
                                && dto.getCreateUser().equals(ConstHelper.CONST_SYSTEMNAME))
                                ? "0" : "1");
                        dataTagMapper.insert(newTag);
                        tagID = newTag.getId();
                    }
                    if (Func.isNotEmpty(tagID)) {
                        TagRelationEntity tagRelationEntity = new TagRelationEntity();
                        tagRelationEntity.setId(UUID.randomUUID().toString());
                        tagRelationEntity.setMapID(dto.getMapLayerID());
                        tagRelationEntity.setDataTagID(tagID);
                        tagRelationMapper.insert(tagRelationEntity);
                        mapLayerID.append(dto.getMapLayerID()).append(",");
                    }
                }
                return true;
            } else {
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean DeleteByMapID(String mapID) {
        try {
            tagRelationMapper.delete(new LambdaQueryWrapper<TagRelationEntity>()
                    .eq(TagRelationEntity::getMapID, mapID));
        } catch (Exception e) {
            log.error("删除标签关系表失败！");
            return false;
        }
        return true;
    }

    @Override
    public ListResultOutput<TagRelationDto> getAllList() {
        try {
            List<TagRelationEntity> query = tagRelationMapper.selectList(new LambdaQueryWrapper<>());
            List<TagRelationDto> copy = BeanUtil.copy(query, TagRelationDto.class);
            return new ListResultOutput<>(copy);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public TagReleationOutputDto getDetailById(String id) {
        try {
            TagRelationEntity query = tagRelationMapper.selectById(id);
            return BeanUtil.copy(query, TagReleationOutputDto.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getMultiTagNameByMapID(String mapLayerID) {
        try {
            String tagName = "";
            List<TagRelationEntity> query = tagRelationMapper.selectList(new LambdaQueryWrapper<TagRelationEntity>()
                    .eq(TagRelationEntity::getMapID, mapLayerID));
            if (Func.isNotEmpty(query)) {
                for (TagRelationEntity item : query) {
                    DataTagEntity tag = dataTagMapper.selectById(item.getDataTagID());
                    tagName += tag.getTagName();
                }
            }
            if (!tagName.isEmpty()) {
                tagName = tagName.substring(0, tagName.length() - 1);
            }
            return tagName;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public TagRelationDto insert(TagReleationInputDto input) {
        try {
            input.setId(UUID.randomUUID().toString());
            TagRelationEntity entity = BeanUtil.copy(input, TagRelationEntity.class);
            tagRelationMapper.insert(entity);
            return BeanUtil.copy(entity, TagRelationDto.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public TagRelationDto update(TagReleationInputDto input) {
        try {
            TagRelationEntity entity = tagRelationMapper.selectById(input.getId());
            if (Func.isNotEmpty(entity)) {
                entity.setMapID(input.getMapID());
                entity.setDataTagID(input.getDataTagID());
            }
            tagRelationMapper.updateById(entity);
            return BeanUtil.copy(entity, TagRelationDto.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean delete(String id) {
        try {
            tagRelationMapper.deleteById(id);
            return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


}
