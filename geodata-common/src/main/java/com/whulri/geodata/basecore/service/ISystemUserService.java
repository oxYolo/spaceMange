package com.whulri.geodata.basecore.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.whulri.geodata.common.dto.systemUser.SystemUserDto;
import com.whulri.geodata.common.entity.SystemUserEntity;

import java.util.List;

public interface ISystemUserService extends IService<SystemUserEntity> {

    List<SystemUserDto> getUserDataByUserCodeAsync(String userCode);
}
