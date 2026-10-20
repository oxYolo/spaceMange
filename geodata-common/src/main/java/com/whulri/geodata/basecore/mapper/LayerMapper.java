package com.whulri.geodata.basecore.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whulri.geodata.common.entity.LayerContentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface LayerMapper extends BaseMapper<LayerContentEntity> {

    List<Map<String, String>> selectLayerInfo(@Param("id") String id);
}
