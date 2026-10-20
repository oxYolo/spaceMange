package com.whulri.geodata.datarelease.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.whulri.geodata.common.dto.dataStyle.*;
import com.whulri.geodata.common.entity.DataStyleEntity;
import com.whulri.geodata.common.result.ListResultOutput;
import com.whulri.geodata.common.util.style.ColorRampInfo;
import com.whulri.geodata.common.util.style.DataStyleOutputImgFileInfo;
import com.whulri.geodata.common.vo.PagedResultOutput;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

public interface IDataStyleService extends IService<DataStyleEntity> {


    ListResultOutput<DataStyleDto> getAllList(String name);

    boolean isExists(String name);

    PagedResultOutput<DataStyleDto> getAllListPage(DataStyleInputDto dataStyleInputDto, int pageSize, int pageIndex);

    DataStyleOutputDto getDetailById(String id);

    DataStyleOutputDto getDetailByLayerID(String layerId);

    DataStyleDto insert(DataStyleInputDto input);

    DataStyleDto update(DataStyleInputDto input);

    Map<String,Object> getTifMaxMin(String id,Integer bandIndex);

    List<Map<String, Double>> getTifJenksBreaks(String id,Integer lvNum,Integer bandIndex);

    List<Double> getTifValues(String id,Integer bandIndex);

    boolean delete(String id, String user);

    boolean deleteById(DataStyleDeleteDto dto);

    ListResultOutput<ColorRampInfo> getRandomColorRamps();

    ColorRampInfo getRandomColorRampsByName(String colorName, int count);

    ListResultOutput<ColorRampInfo> getLinearColorRamps();

    ColorRampInfo getLinearColorRampsByName(String colorName, int count);

    List<String> getAllFolders();

    boolean addFolder(String folderName);

    boolean updateFolder(String oldName, String newName);

    boolean deleteFolder(String folderName);

    List<DataStyleOutputImgFileInfo> getAllFiles(String folderName);

    boolean updateFile(String folderName, String oldName, String newName);

    boolean deleteFile(String folderName, String fileName);

    List<Map<String, Object>> getDataAttribute(String layerId, String layerAttr);

    String getDataAttributesPage(String layerId, String layerAttr, String colorName, String style, int PageSize, int PageIndex);

    String getDataAttributesSort(String layerId, String layerAttr, String colorName, String style, int PageSize, int PageIndex);

    String getDataAttributes(String layerId, String layerAttr, String colorName, String style);

    Object getDataAttributesById(String styleId);

    String getXmlContent(DataStyleInputDto input);

    void insertGeoServerStyle(DataStyleInputDto input);

    void insertStyle(String id, String styleName, String styleContent);

    DataStyleOutputDto parsingSld(String id);

    List<RuleData> parsethefile(String path);
}
