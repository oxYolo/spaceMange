package com.whulri.geodata.datamanage.service;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.extension.service.IService;
import com.whulri.geodata.common.dto.Coordinate;
import com.whulri.geodata.common.dto.LayerFieldQuery;
import com.whulri.geodata.common.dto.LayerParameter;
import com.whulri.geodata.common.dto.layerContent.*;
import com.whulri.geodata.common.entity.LayerContentEntity;
import com.whulri.geodata.common.entity.LayerFieldEntity;
import com.whulri.geodata.common.entity.MapEntity;
import com.whulri.geodata.common.result.ListResultOutput;
import com.whulri.geodata.common.result.PageResultDTO;
import com.whulri.geodata.common.vo.PagedResultOutput;

import java.util.List;
import java.util.Map;

public interface ILayerContentService extends IService<LayerContentEntity> {

    public List<LayerContentDto> getAllList();

    /**
     * 图层统计列表
     *
     * @param userCode 用户编码
     * @return 数据类型统计输出
     */
    DataTypeCountOutput getAllCountByDataType(String userCode);

    /**
     * 条件分页查询图层信息
     *
     * @param input     查询参数
     * @param pageSize  分页大小
     * @param pageIndex 当前页码
     * @return 分页结果输出
     */
    PagedResultOutput<LayerContentDto> getAllListStatus(LayerContentInputDto input, int pageSize, int pageIndex);

    /**
     * 图层统计数量
     *
     * @param userCode 用户编码
     * @return 异步返回统计数量
     */
    Integer getAllCount(String userCode);

    /**
     * 根据查询条件获取所有图层
     *
     * @param input 查询参数
     * @return 图层内容DTO列表结果
     */
    ListResultOutput<LayerContentDto> getAllListByName(LayerContentInputDto input);

    /**
     * 根据查询条件获取图层分页信息
     *
     * @param input     查询参数
     * @param pageSize  分页大小
     * @param pageIndex 当前页码
     * @return 分页结果输出
     */
    PagedResultOutput<LayerContentDto> getPageListByName(LayerContentInputDto input, int pageSize, int pageIndex);

    /**
     * 根据地图ID获取所有图层
     *
     * @param mapID 地图ID
     * @return 图层内容DTO列表结果
     */
    ListResultOutput<LayerContentDto> getAllListByMapID(String mapID);


    LayerContentDto getDetailById(String id);

    /**
     * 根据图层ID获取图层属性分页信息
     *
     * @param layerID   图层ID
     * @param pageSize  分页大小
     * @param pageIndex 当前页码
     * @return 分页属性信息字符串
     */
    String getLayerAttrTabledDetail(String layerID, int pageSize, int pageIndex);

    /**
     * 新增图层
     *
     * @param inputLc      图层信息
     * @param filePathName 文件路径名称
     * @param user         用户
     * @param isImport     是否导入
     * @return 异步返回图层内容DTO
     */
    LayerContentDto insert(LayerContentInputDto inputLc, String filePathName, String user, String isImport);

    /**
     * 新增影像图层
     *
     * @param inputLc 图层信息
     * @return 异步返回地图实体
     */
    MapEntity insertImageLayer(LayerContentInputDto inputLc);

    boolean update(LayerContentInputDto input);

    /**
     * 更新图层上传状态为1
     *
     * @param layerID 图层ID
     * @return 异步返回图层内容DTO
     */
    LayerContentDto updateStatus(String layerID);

    //    删除图层
    boolean delete(String id, String user);

    /**
     * 删除图层及发布信息
     *
     * @param id 图层ID
     * @return 是否成功
     */
    boolean deleteLayer(String id);

    List<CatalogTreeVO> getLayerTree(String dictType, String dataType, String publishStatus, String layerName, String uploadFileType, Integer maxRaster, Boolean isclassify);

    List<CatalogTreeVO> getLayerTreeByType(String dataTypeId, String publishStatus, String layerName);

    // 根据图层ID分页获取属性表
    PagedResultOutput<String> getAttrtableByLayerId(String id, int PageSize, int PageIndex);

    /**
     * 根据图层ID和矩形范围分页查询属性表信息
     *
     * @param layerIds  图层ID列表
     * @param minLon    最小经度
     * @param minLat    最小纬度
     * @param maxLon    最大经度
     * @param maxLat    最大纬度
     * @param pageSize  每页大小
     * @param pageIndex 当前页码
     * @return 分页结果输出
     */
    PagedResultOutput<String> getInfoBylayerIdsAndRectPage(List<String> layerIds, double minLon, double minLat, double maxLon, double maxLat, int pageSize, int pageIndex);


    /**
     * 根据图层ID和点容差范围分页查询属性表信息
     *
     * @param layerIds  图层ID列表
     * @param lon       经度
     * @param lat       纬度
     * @param distance  半径（单位：米）
     * @param pageSize  每页大小
     * @param pageIndex 当前页码
     * @return 分页结果输出
     */
    PagedResultOutput<String> getInfoByLayerIdsAndPtTolerancePage(List<String> layerIds, double lon, double lat, float distance, int pageSize, int pageIndex);

    /**
     * 根据图层ID和点容差范围下载查询属性表信息
     *
     * @param layerIds 图层ID列表
     * @param lon      经度
     * @param lat      纬度
     * @param distance 半径（单位：米）
     * @return 属性表信息字符串
     */
    String getInfoByLayerIdsAndPtToleranceDownload(List<String> layerIds, double lon, double lat, float distance);

    /**
     * 根据图层ID和多边形范围下载查询属性表信息
     *
     * @param layerIds   图层ID列表
     * @param coordinate 坐标列表
     * @return 属性表信息字符串
     */
    String getInfoBylayerIdsAndPolygonDownLoad(String layerIds, List<Coordinate> coordinate);

    /**
     * 根据图层ID和多边形范围分页查询属性表信息
     *
     * @param layerIds   图层ID
     * @param coordinate 坐标列表
     * @param pageSize   每页大小
     * @param pageIndex  当前页码
     * @return 分页结果输出
     */
    PagedResultOutput<String> getInfoBylayerIdsAndPolygonPage(String layerIds, List<Coordinate> coordinate, int pageSize, int pageIndex);

    /**
     * 根据图层ID和矩形范围下载查询属性表信息
     *
     * @param layerIds 图层ID列表
     * @param minLon   最小经度
     * @param minLat   最小纬度
     * @param maxLon   最大经度
     * @param maxLat   最大纬度
     * @return 属性表信息字符串
     */
    String getInfoBylayerIdsAndRectDownLoad(List<String> layerIds, double minLon, double minLat, double maxLon, double maxLat);

    // 根据条件分页获取图层属性表
    PagedResultOutput<String> getLayerAttrTableByWhere(String layerId, String where, int PageSize, int PageIndex);

    /**
     * 插入图层属性
     *
     * @param layerParamete 图层参数
     * @return 插入是否成功
     */
    boolean insertAttrtable(LayerParameter layerParamete);

    /**
     * 删除图层属性
     *
     * @param id   图层ID
     * @param gid 属性GUID
     * @return 删除是否成功
     */
    boolean deleteAttrtable(String id, String gid);

    /**
     * 更新图层属性
     *
     * @param layerParameter 图层参数
     * @return 更新是否成功
     */
    boolean updateAttrtable(LayerParameter layerParameter);

    /**
     * 批量更新图层属性
     *
     * @param layerParameters 图层参数列表
     * @return 更新是否成功
     */
    boolean updateAttrtableList(List<LayerParameter> layerParameters);

    // 根据图层ID获取字段
    List<LayerFieldEntity> getFieldByLayerId(String layerid, String attributeType);


    // 验证条件格式
    boolean validateCondition(String layerId, List<JSONObject> where);

    /**
     * 空间查询
     *
     * @param query
     * @param pageSize
     * @param pageIndex
     * @return
     */
    PageResultDTO<Map<String, Object>> pageAttrInfoBySwitch(LayerFieldQuery query, int pageSize, int pageIndex);

    /**
     * 空间查询 下载矢量
     *
     * @param query
     * @return
     */
    String downloadAttrInfoBySwitch(LayerFieldQuery query);


    /**
     * 输出图层 shp 文件
     *
     * @param layerID 图层ID
     * @return 文件路径
     */
    String outputShpFileData(String layerID);

    /**
     * 输出图层 tiff 文件路径
     *
     * @param layerID 图层ID
     * @return 文件路径字符串
     */
    String outputTifFileData(String layerID);

    boolean updateList(List<LayerUpdateDto> layerUpdateDtoList);

    boolean updateBothList(List<LayerUpdateDto> layerUpdateDtoList);

    /**
     *
     * @param query
     * @return
     */
    List<PageResultDTO<Map<String, Object>>> pageAttrInfoByPoint(LayerFieldQuery query);

    /**
     * 获取栅格图层值
     *
     * @param layerId
     * @param longitude
     * @param latitude
     * @return
     */
    Object getLayerValue(String layerId, String longitude, String latitude);
}
