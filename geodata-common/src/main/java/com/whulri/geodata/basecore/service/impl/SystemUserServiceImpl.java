package com.whulri.geodata.basecore.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.whulri.geodata.basecore.mapper.SystemUserMapper;
import com.whulri.geodata.basecore.service.ISystemUserService;
import com.whulri.geodata.common.dto.systemUser.SystemUserDto;
import com.whulri.geodata.common.entity.SystemUserEntity;
import com.whulri.geodata.common.bridge.BeanUtil;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SystemUserServiceImpl extends ServiceImpl<SystemUserMapper, SystemUserEntity> implements ISystemUserService {


    @Override
    public List<SystemUserDto> getUserDataByUserCodeAsync(String userCode) {
        List<SystemUserEntity> userQuery = null;
        try {
            SystemUserEntity userInfo = this.getOne(new LambdaQueryWrapper<SystemUserEntity>()
                    .eq(SystemUserEntity::getUserCode, userCode));
            if (userInfo != null) {
                userQuery = this.list(new LambdaQueryWrapper<SystemUserEntity>()
                        .eq(SystemUserEntity::getDepartment, userInfo.getDepartment())
                        .eq(SystemUserEntity::getUserCode, userCode));

            }
        } catch (Exception e) {
            log.error("获取用户信息失败", e);
        }
        return BeanUtil.copy(userQuery, SystemUserDto.class);
    }
}
