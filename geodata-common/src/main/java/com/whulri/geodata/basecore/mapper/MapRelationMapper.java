package com.whulri.geodata.basecore.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whulri.geodata.common.entity.MapRelationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface MapRelationMapper extends BaseMapper<MapRelationEntity> {

    List<Map<String, Object>> selectMapRelationByMapId(@Param("mapId") String mapId);

    List<Map<String, Object>> selectMapRelationAndDataStyle();
}
