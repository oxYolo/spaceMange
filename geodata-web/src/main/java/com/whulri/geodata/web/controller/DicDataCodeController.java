package com.whulri.geodata.web.controller;

import com.whulri.geodata.common.dto.dicDataCode.DicDataCodeDto;
import com.whulri.geodata.common.dto.dicDataCode.DicDataCodeInputDto;
import com.whulri.geodata.common.result.ListResultOutput;
import com.whulri.geodata.common.result.Result;
import com.whulri.geodata.datamanage.service.IDictDataCodeService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

@RequestMapping("/dicDataCode")
@RestController
public class DicDataCodeController {

    @Resource
    private IDictDataCodeService dictDataCodeService;

    /**
     * 根据字典类型查询所有数据字典
     *
     * @param typeID
     * @return
     */
    @GetMapping("/GetDetailByTypeID")
    public Result<?> GetDetailByTypeID(@RequestParam String typeID) {
        ListResultOutput<DicDataCodeDto> detailByTypeID = dictDataCodeService.GetDetailByTypeID(typeID);
        return Result.data(detailByTypeID);
    }

    /**
     * 获取所有数据字典
     *
     * @return
     */
    @GetMapping("/GetAllList")
    public Result<?> getAllList() {
        return Result.data(dictDataCodeService.getAllList());
    }

    /**
     * 根据字典类型和关键字查询所有数据字典
     *
     * @return
     */
    @GetMapping("/GetDetailByConn")
    public Result<?> getDetailByConn(@RequestParam("typeID") String typeID,
                                     @RequestParam("keyWord") String keyWord) {
        return Result.data(dictDataCodeService.getDetailByConn(typeID, keyWord));
    }

    /**
     * 新增数据字典
     *
     * @return
     */
    @PostMapping("/Insert")
    public Result<?> insert(@RequestBody DicDataCodeInputDto input) {
        return Result.data(dictDataCodeService.insert(input));
    }

    /**
     * 更新数据字典
     *
     * @param input
     * @return
     */
    @PostMapping("/Update")
    public Result<?> update(@RequestBody DicDataCodeInputDto input) {
        return Result.data(dictDataCodeService.update(input));
    }

    /**
     * 删除数据字典
     *
     * @param id
     * @return
     */
    @PostMapping("/Delete")
    public Result<?> delete(@RequestParam("id") String id) {
        return Result.status(dictDataCodeService.delete(id));
    }


}
