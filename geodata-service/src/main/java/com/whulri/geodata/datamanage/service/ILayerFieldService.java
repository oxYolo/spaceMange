package com.whulri.geodata.datamanage.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.whulri.geodata.common.dto.layerField.*;
import com.whulri.geodata.common.entity.LayerFieldEntity;

import java.util.List;

public interface ILayerFieldService extends IService<LayerFieldEntity> {

    LayerFieldListDto getLayerFieldByFileName(String shpFileName);

    boolean createTable(String id);

    List<LayerFieldDto> getAllList();

    LayerFieldOutputDto GetDetailById(String id);

    List<LayerFieldDto> GetDetailByLayerID(String layerID);

    LayerFieldDto insert(LayerFieldInputDto input);

    LayerFieldDto insertSync(LayerFieldInputDto input);

    boolean MultiInsert(List<LayerFieldInputDto> listInput);

    LayerFieldDto update(LayerFieldInputDto input);

    boolean delete(String id);

    boolean deleteByLayerID(String layerID);
}
