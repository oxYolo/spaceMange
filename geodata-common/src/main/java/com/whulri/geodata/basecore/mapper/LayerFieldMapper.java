package com.whulri.geodata.basecore.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whulri.geodata.common.entity.LayerFieldEntity;
import com.whulri.geodata.common.vo.AttributeModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LayerFieldMapper extends BaseMapper<LayerFieldEntity> {

    /**
     * warn 借用attributeinputctrl 避免重新创建dto
     * @param layerId
     * @return
     */
    List<AttributeModel> getLayerFieldById(@Param("layerId") String layerId);

}
