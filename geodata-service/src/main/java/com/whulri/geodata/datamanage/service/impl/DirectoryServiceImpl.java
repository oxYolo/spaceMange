package com.whulri.geodata.datamanage.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.whulri.geodata.basecore.mapper.DirectoryMapper;
import com.whulri.geodata.common.entity.DirectorysEntity;
import com.whulri.geodata.datamanage.service.IDirectoryService;
import org.springframework.stereotype.Service;

@Service
public class DirectoryServiceImpl extends ServiceImpl<DirectoryMapper, DirectorysEntity> implements IDirectoryService {
}
