package com.whulri.geodata.basecore.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.whulri.geodata.common.dto.operateLog.OperateLogDto;
import com.whulri.geodata.common.entity.OperateLogEntity;


public interface IOperateLogService extends IService<OperateLogEntity> {

    /**
     * 新增操作日志
     * 图层编号
     * 用户编号
     * 系统编号
     * 操作类型
     * 执行结果
     * 结果描述
     * 图层名称
     *
     * @param dto
     * @return
     */
    Boolean writeOperateLog(OperateLogDto dto);

    Boolean writeOperateLog(String LayerID, String userCode, int systemFunc, int operateType, int result, int resultDescribe, String LayerName);

}
