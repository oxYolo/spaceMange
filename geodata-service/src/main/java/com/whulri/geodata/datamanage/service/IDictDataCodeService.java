package com.whulri.geodata.datamanage.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.whulri.geodata.common.dto.dicDataCode.DicDataCodeDto;
import com.whulri.geodata.common.dto.dicDataCode.DicDataCodeInputDto;
import com.whulri.geodata.common.entity.DictDataCodeEntity;
import com.whulri.geodata.common.result.ListResultOutput;

public interface IDictDataCodeService extends IService<DictDataCodeEntity> {

    ListResultOutput<DicDataCodeDto> GetDetailByTypeID(String typeId);

    ListResultOutput<DicDataCodeDto> getAllList();

    ListResultOutput<DicDataCodeDto> getDetailByConn(String typeID, String keyWord);

    DicDataCodeDto insert(DicDataCodeInputDto input);

    DicDataCodeDto update(DicDataCodeInputDto input);

    boolean delete(String id);
}
