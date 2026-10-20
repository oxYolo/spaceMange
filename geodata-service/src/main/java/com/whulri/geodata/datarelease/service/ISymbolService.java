package com.whulri.geodata.datarelease.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.whulri.geodata.common.dto.symbol.SymbolDto;
import com.whulri.geodata.common.dto.symbol.SymbolInputDto;
import com.whulri.geodata.common.dto.symbol.SymbolOutputDto;
import com.whulri.geodata.common.entity.SymbolEntity;
import com.whulri.geodata.common.result.ListResultOutput;
import com.whulri.geodata.common.vo.PagedResultOutput;

public interface ISymbolService extends IService<SymbolEntity> {

    ListResultOutput<SymbolDto> getAllList();

    PagedResultOutput<SymbolDto> GetAllListPage(SymbolInputDto input, int PageIndex, int PageSize);

    SymbolOutputDto getDetailById(String id);

    SymbolDto insert(SymbolInputDto input);

    SymbolDto update(SymbolInputDto input);

    boolean delete(String id);
}
