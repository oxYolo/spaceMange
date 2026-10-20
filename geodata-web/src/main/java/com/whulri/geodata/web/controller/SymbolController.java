package com.whulri.geodata.web.controller;

import com.whulri.geodata.common.dto.symbol.SymbolInputDto;
import com.whulri.geodata.common.result.Result;
import com.whulri.geodata.datarelease.service.ISymbolService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 样式符号信息
 */
@RestController
@RequestMapping("/symbol")
public class SymbolController {

    @Autowired
    private ISymbolService symbolService;

    /**
     * 获取所有符号
     *
     * @return
     */
    @GetMapping("/GetAllList")
    public Result<?> getAllList() {
        return Result.data(symbolService.getAllList());
    }

    /**
     * 根据查询条件返回样式分页数据
     *
     * @param input
     * @param pageIndex
     * @param pageSize
     * @return
     */
    @PostMapping("/GetAllListPage")
    public Result<?> getAllListPage(@RequestBody SymbolInputDto input,
                                    @RequestParam("pageIndex") int pageIndex,
                                    @RequestParam("pageSize") int pageSize) {
        return Result.data(symbolService.GetAllListPage(input, pageIndex, pageSize));
    }

    /**
     * 根据id查询样式详情
     *
     * @param id
     * @return
     */
    @GetMapping("/GetDetailById")
    public Result<?> getDetailById(@RequestParam("id") String id) {
        return Result.data(symbolService.getDetailById(id));
    }

    /**
     * 新增符号
     *
     * @param input
     * @return
     */
    @PostMapping("/insert")
    public Result<?> insert(@RequestBody SymbolInputDto input) {
        return Result.data(symbolService.insert(input));
    }

    /**
     * 更新符号
     *
     * @param input
     * @return
     */
    @PostMapping("/Update")
    public Result<?> update(@RequestBody SymbolInputDto input) {
        return Result.data(symbolService.update(input));
    }

    /**
     * 删除符号
     *
     * @param id
     * @return
     */
    @PostMapping("/Delete")
    public Result<?> delete(@RequestParam("id") String id) {
        return Result.status(symbolService.delete(id));
    }

}
