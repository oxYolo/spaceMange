package com.whulri.geodata.web.controller;

import com.whulri.geodata.common.dto.dataStyle.DataStyleDeleteDto;
import com.whulri.geodata.common.dto.dataStyle.DataStyleInputDto;
import com.whulri.geodata.common.result.Result;
import com.whulri.geodata.common.util.JenksBreaksUtil;
import com.whulri.geodata.datarelease.service.IDataStyleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/dataStyle")
@Tag(name = "样式管理模块")
public class DataStyleController {

    @Autowired
    private IDataStyleService dataStyleService;

    @GetMapping("/GetAllList")
    @Operation(summary = "根据样式名称模糊查找所有样式")
    public Result<?> getAllList(@RequestParam("name") String name) {
        return Result.data(dataStyleService.getAllList(name));
    }

    @PostMapping("/IsExists")
    @Operation(summary = "判断样式是否存在")
    public Result<?> isExists(@RequestParam("name") String name) {
        return Result.data(dataStyleService.isExists(name));
    }

    @PostMapping("/GetAllListPage")
    @Operation(summary = "根据查询条件返回样式分页数据")
    public Result<?> getAllListPage(@RequestBody DataStyleInputDto dataStyleInputDto,
                                    @RequestParam("pageSize") int pageSize,
                                    @RequestParam("pageIndex") int pageIndex) {
        return Result.data(dataStyleService.getAllListPage(dataStyleInputDto, pageSize, pageIndex));
    }


    @GetMapping("/GetDetailById")
    @Operation(summary = "根据样式编号查询样式详细信息")
    public Result<?> getDetailById(@RequestParam("id") String id) {
        return Result.data(dataStyleService.getDetailById(id));
    }


    @GetMapping("/GetDetailByLayerID")
    @Operation(summary = "根据图层编号查询图层的样式信息")
    public Result<?> getDetailByLayerID(@RequestParam("id") String id) {
        return Result.data(dataStyleService.getDetailByLayerID(id));
    }


    @PostMapping("/Insert")
    @Operation(summary = "新增样式")
    public Result<?> insert(@RequestBody DataStyleInputDto input) {
        return Result.data(dataStyleService.insert(input));
    }


    @PostMapping("/Update")
    @Operation(summary = "更新样式")
    public Result<?> update(@RequestBody DataStyleInputDto input) {
        return Result.data(dataStyleService.update(input));
    }

    @GetMapping("/getTifMaxMin")
    @Operation(summary = "获取栅格数据的最大最小值")
    public Result<?> getTifMaxMin(@RequestParam String id,
                                  @RequestParam(required = false) Integer bandIndex){//LayerId
        return Result.data(dataStyleService.getTifMaxMin(id,bandIndex));
    }

    @GetMapping("/getTifJenksBreaks")
    @Operation(summary = "获取栅格数据自然断点法范围值")
    public Result<?> getTifJenksBreaks(@RequestParam String id,
                                       @RequestParam Integer lvNum,
                                       @RequestParam(required = false) Integer bandIndex){
        return Result.data(dataStyleService.getTifJenksBreaks(id,lvNum,bandIndex));
    }

    @GetMapping("/getTifValues")
    @Operation(summary = "获取栅格数据所有值集合")
    public Result<?> getTifValues(@RequestParam String id,
                                  @RequestParam(required = false) Integer bandIndex){//LayerId
        List<Double> tifValues = dataStyleService.getTifValues(id, bandIndex);
        if (tifValues.size()>50){
            return Result.fail(400,"图层像元值过多，请选择其他方法！！！");
        }
        return Result.data(tifValues);
    }

    @PostMapping("/Delete")
    @Operation(summary = "删除样式")
    public Result<?> delete(@RequestParam("id") String id,
                            @RequestParam("user") String user) {
        return Result.status(dataStyleService.delete(id, user));
    }


    @PostMapping("/DeleteById")
    @Operation(summary = "删除样式")
    public Result<?> deleteById(@RequestBody DataStyleDeleteDto dto) {
        return Result.status(dataStyleService.deleteById(dto));
    }


    @GetMapping("/GetRandomColorRamps")
    @Operation(summary = "获取所有随机颜色带")
    public Result<?> getRandomColorRamps() {
        return Result.data(dataStyleService.getRandomColorRamps());
    }

    @GetMapping("/GetRandomColorRampsByName")
    @Operation(summary = "根据颜色名称获取随机颜色带")
    public Result<?> getRandomColorRampsByName(@RequestParam("colorName") String colorName,
                                               @RequestParam("count") int count) {
        return Result.data(dataStyleService.getRandomColorRampsByName(colorName, count));
    }

    @GetMapping("/GetLinearColorRamps")
    @Operation(summary = "获取所有渐变颜色带")
    public Result<?> getLinearColorRamps() {
        return Result.data(dataStyleService.getLinearColorRamps());
    }


    @GetMapping("/GetLinearColorRampsByName")
    @Operation(summary = "根据颜色名称获取渐变颜色带")
    public Result<?> getLinearColorRampsByName(@RequestParam("colorName") String colorName,
                                               @RequestParam("count") int count) {
        return Result.data(dataStyleService.getLinearColorRampsByName(colorName, count));
    }


    @GetMapping("/GetAllFolders")
    @Operation(summary = "获取样式所有文件夹目录")
    public Result<?> getAllFolders() {
        return Result.data(dataStyleService.getAllFolders());
    }

    @PostMapping("/AddFolder")
    @Operation(summary = "新增样式文件夹")
    public Result<?> addFolder(@RequestParam("folderName") String folderName) {
        return Result.status(dataStyleService.addFolder(folderName));
    }

    @PostMapping("/UpdateFolder")
    @Operation(summary = "修改样式文件夹名称")
    public Result<?> updateFolder(@RequestParam("oldName") String oldName,
                                  @RequestParam("newName") String newName) {
        return Result.status(dataStyleService.updateFolder(oldName, newName));
    }

    @PostMapping("/DeleteFolder")
    @Operation(summary = "删除样式文件夹")
    public Result<?> deleteFolder(@RequestParam("folderName") String folderName) {
        return Result.status(dataStyleService.deleteFolder(folderName));
    }

    @GetMapping("/GetAllFiles")
    @Operation(summary = "获取指定目录所有文件")
    public Result<?> getAllFiles(@RequestParam("folderName") String folderName) {
        return Result.data(dataStyleService.getAllFiles(folderName));
    }

    @PostMapping("/UpdateFile")
    @Operation(summary = "修改文件名称")
    public Result<?> updateFile(@RequestParam("folderName") String folderName,
                                @RequestParam("oldName") String oldName,
                                @RequestParam("newName") String newName) {
        return Result.status(dataStyleService.updateFile(folderName, oldName, newName));
    }


    @PostMapping("/DeleteFile")
    @Operation(summary = "删除指定文件")
    public Result<?> deleteFile(@RequestParam("folderName") String folderName,
                                @RequestParam("fileName") String fileName) {
        return Result.status(dataStyleService.deleteFile(folderName, fileName));
    }

    @GetMapping("/GetDataAttribute")
    @Operation(summary = "获取图层属性数据")
    public Result<?> getDataAttribute(@RequestParam("layerId") String layerId,
                                           @RequestParam("layerAttr") String layerAttr){
        return Result.data(dataStyleService.getDataAttribute(layerId, layerAttr));
    }

    @GetMapping("/getShpMaxMin")
    @Operation(summary = "获取shp属性最大最小值")
    public Result<?> getShpMaxMin(@RequestParam("layerId") String layerId,
                                  @RequestParam("layerAttr") String layerAttr){
        List<Map<String, Object>> list = dataStyleService.getDataAttribute(layerId, layerAttr);
        if (list.size()>0){
            try {
                ArrayList<Double> doubles = new ArrayList<>();
                for (Map<String, Object> map : list) {
                    String value = String.valueOf(map.get("value"));
                    Double aDouble = Double.valueOf(value);
                    doubles.add(aDouble);
                }
                double min = doubles.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
                double max = doubles.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
                Map<String, Double> map = new HashMap<>();
                map.put("max",max);
                map.put("min",min);
                return Result.data(map);
            }catch (Exception e){
                return Result.fail(400,"数据类型不对，只支持数值");
            }
        }
        return Result.fail(400,"无数据");
    }

    @GetMapping("/getShpJenksBreaks")
    @Operation(summary = "获取shp数据自然断点法范围值")
    public Result<?> getShpJenksBreaks(@RequestParam("layerId") String layerId,
                                       @RequestParam("layerAttr") String layerAttr,
                                       @RequestParam("lvNum") Integer lvNum){
        List<Map<String, Object>> list = dataStyleService.getDataAttribute(layerId, layerAttr);
        if (list.size()>0){
            try {
                List<Double> doubles = new ArrayList<>();
                for (Map<String, Object> map : list) {
                    String value = String.valueOf(map.get("value"));
                    Double aDouble = Double.valueOf(value);
                    doubles.add(aDouble);
                }
                if (doubles.size()>0){
                    List<Map<String, Double>> maps = JenksBreaksUtil.calculateJenksBreaksList(doubles, lvNum);
                    return Result.data(maps);
                }
            }catch (Exception e){
                return Result.fail(400,"数据类型不对，只支持数值");
            }
        }
        return Result.fail(400,"无数据");
    }

    @GetMapping("/GetDataAttributesPage")
    @Operation(summary = "获取图层属性分页数据")
    public Result<?> getDataAttributesPage(@RequestParam("layerId") String layerId,
                                           @RequestParam("layerAttr") String layerAttr,
                                           @RequestParam("colorName") String colorName,
                                           @RequestParam("style") String style,
                                           @RequestParam("pageSize") int pageSize,
                                           @RequestParam("pageIndex") int pageIndex) {
        return Result.data(dataStyleService.getDataAttributesPage(layerId, layerAttr, colorName, style, pageSize, pageIndex));
    }

    @GetMapping("/GetDataAttributesSort")
    @Operation(summary = "获取排序后的图层属性")
    public Result<?> getDataAttributesSort(@RequestParam("layerId") String layerId,
                                           @RequestParam("layerAttr") String layerAttr,
                                           @RequestParam("colorName") String colorName,
                                           @RequestParam("style") String style,
                                           @RequestParam("pageSize") int pageSize,
                                           @RequestParam("pageIndex") int pageIndex) {
        return Result.data(dataStyleService.getDataAttributesSort(layerId, layerAttr, colorName, style, pageSize, pageIndex));
    }

    @GetMapping("/GetDataAttributes")
    @Operation(summary = "获取图层属性数据")
    public Result<?> getDataAttributes(@RequestParam("layerId") String layerId,
                                       @RequestParam("layerAttr") String layerAttr,
                                       @RequestParam("colorName") String colorName,
                                       @RequestParam("style") String style) {
        return Result.data(dataStyleService.getDataAttributes(layerId, layerAttr, colorName, style));
    }


    @GetMapping("/GetDataAttributesById")
    @Operation(summary = "获取图层属性数据")
    public Result<?> getDataAttributesById(@RequestParam("styleId") String styleId) {
        return Result.data(dataStyleService.getDataAttributesById(styleId));
    }

    @GetMapping("/GetXmlContent")
    @Operation(summary = "获取样式内容")
    public Result<?> getXmlContent(@RequestBody DataStyleInputDto input) {
        return Result.data(dataStyleService.getXmlContent(input));
    }

    @PostMapping("/InsertGeoServerStyle")
    @Operation(summary = "新增或更新样式到GeoServer")
    public Result<Void> insertGeoServerStyle(@RequestBody DataStyleInputDto input) {
        dataStyleService.insertGeoServerStyle(input);
        return Result.success("操作成功");
    }

//    @PostMapping("/InsertStyle")
//    @Operation(summary = "新增样式到GeoServer")
//    public void insertStyle(@RequestParam("styleName") String styleName,
//                            @RequestParam("styleContent") String styleContent) {
//        dataStyleService.insertStyle(styleName, styleContent);
//    }

    @PostMapping("/ParsingSld")
    @Operation(summary = "符号和渲染规则")
    public Result<?> parsingSld(@RequestParam("id") String id) {
        return Result.data(dataStyleService.parsingSld(id));
    }

    @PostMapping("/Parsethefile")
    @Operation(summary = "将sld文件解析成RuleData")
    public Result<?> parsethefile(@RequestParam("id") String id) {
        return Result.data(dataStyleService.parsethefile(id));
    }


}
