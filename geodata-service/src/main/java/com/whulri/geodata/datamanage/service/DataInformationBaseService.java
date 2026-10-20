package com.whulri.geodata.datamanage.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.whulri.geodata.common.entity.DataInformationEntity;

/**
 * 该接口专门用于查询数据库的公共方法  不做业务处理 防止循环依赖
 */
public interface DataInformationBaseService extends IService<DataInformationEntity> {

}
