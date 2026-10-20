package com.whulri.geodata.datarelease.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.whulri.geodata.basecore.mapper.DataTypeMapper;
import com.whulri.geodata.basecore.mapper.SymbolMapper;
import com.whulri.geodata.basecore.service.ISystemUserService;
import com.whulri.geodata.common.dto.symbol.SymbolDto;
import com.whulri.geodata.common.dto.symbol.SymbolInputDto;
import com.whulri.geodata.common.dto.symbol.SymbolOutputDto;
import com.whulri.geodata.common.dto.systemUser.SystemUserDto;
import com.whulri.geodata.common.entity.DataTypeEntity;
import com.whulri.geodata.common.entity.SymbolEntity;
import com.whulri.geodata.common.result.ListResultOutput;
import com.whulri.geodata.common.util.ConstHelper;
import com.whulri.geodata.common.vo.PagedResultOutput;
import com.whulri.geodata.datarelease.service.ISymbolService;
import com.whulri.geodata.common.bridge.BeanUtil;
import com.whulri.geodata.common.bridge.Func;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class SymbolServiceImpl extends ServiceImpl<SymbolMapper, SymbolEntity> implements ISymbolService {

    @Resource
    private SymbolMapper symbolMapper;
    @Resource
    private DataTypeMapper dataTypeMapper;
    @Resource
    private ISystemUserService systemUserService;

    @Override
    public ListResultOutput<SymbolDto> getAllList() {
        try {
            List<SymbolEntity> symbolEntities = symbolMapper.selectList(new LambdaQueryWrapper<>());
            List<SymbolDto> copy = BeanUtil.copy(symbolEntities, SymbolDto.class);
            return new ListResultOutput<>(copy);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public PagedResultOutput<SymbolDto> GetAllListPage(SymbolInputDto input, int PageIndex, int PageSize) {

        try {
            String name = input.getSymbolName();
            String type;
            String dataType = "";

            if (Func.isNotBlank(input.getSymbolClassify()))
                getMultiChildTypeByType(input.getSymbolClassify());

            if (Func.isNotBlank(input.getSymbolType()))
                type = input.getSymbolType();
            else {
                type = "";
            }

            if (Func.isBlank(input.getCreateBy()))
                return null;

            List<SymbolEntity> symbols = getDataByUserCodeAsync(input.getCreateBy());
            if (Func.isNotBlank(name)) {
                symbols = symbols.stream()
                        .filter(x -> Func.isNotEmpty(x.getSymbolName())
                                && x.getSymbolName().contains(name))
                        .collect(Collectors.toList());
            }
            if (Func.isNotBlank(type)) {
                symbols = symbols.stream()
                        .filter(s -> Func.isNotEmpty(s.getSymbolType())
                                && s.getSymbolType().contains(type))
                        .collect(Collectors.toList());
            }
            if (Func.isNotBlank(dataType)) {
                symbols = symbols.stream()
                        .filter(s -> Func.isNotBlank(s.getSymbolClassify())
                                && dataType.contains(s.getSymbolClassify()))
                        .collect(Collectors.toList());
            }
            List<DataTypeEntity> listStyleType = dataTypeMapper.selectList(new LambdaQueryWrapper<>());

            List<SymbolDto> query = symbols.stream()
                    .flatMap(d -> {
                                List<DataTypeEntity> matchedTypes = listStyleType.stream()
                                        .filter(t -> t.getId().equals(d.getSymbolClassify()))
                                        .collect(Collectors.toList());

                                // 如果没有匹配项，返回一个包含默认值的流
                                if (matchedTypes.isEmpty()) {
                                    return Stream.of(new SymbolDto(
                                            d.getId(),
                                            d.getSymbolName(),
                                            d.getSymbolType(),
                                            d.getSymbolClassify(),
                                            d.getSymbolDesc(),
                                            d.getImageorFontType(),
                                            d.getPath(),
                                            d.getImagelong(),
                                            d.getImageWide(),
                                            d.getImageAngle(),
                                            d.getFontSize(),
                                            d.getFontColor(),
                                            "", // 默认空字符串
                                            d.getCreateDT(),
                                            d.getCreateBy(),
                                            d.getFontFamily(),
                                            d.getCharUnicode(),
                                            d.getFontAngle()
                                    ));
                                } else {
                                    // 返回所有匹配项的流
                                    return matchedTypes.stream()
                                            .map(dt -> new SymbolDto(
                                                    d.getId(),
                                                    d.getSymbolName(),
                                                    d.getSymbolType(),
                                                    d.getSymbolClassify(),
                                                    d.getSymbolDesc(),
                                                    d.getImageorFontType(),
                                                    d.getPath(),
                                                    d.getImagelong(),
                                                    d.getImageWide(),
                                                    d.getImageAngle(),
                                                    d.getFontSize(),
                                                    d.getFontColor(),
                                                    dt.getTypeName(),
                                                    d.getCreateDT(),
                                                    d.getCreateBy(),
                                                    d.getFontFamily(),
                                                    d.getCharUnicode(),
                                                    d.getFontAngle()
                                            ));
                                }
                            }
                    ).sorted(Comparator.comparing(SymbolDto::getCreateDT).reversed())
                    .collect(Collectors.toList());


            int count = query.size();
            List<SymbolDto> result = query.stream()
                    .skip((long) (PageIndex - 1) * PageSize).limit(PageSize)
                    .collect(Collectors.toList());

            List<SymbolDto> ir;
            if (Func.isEmpty(result)) {
                ir = BeanUtil.copy(result, SymbolDto.class);
            } else {
                ir = new ArrayList<>();
            }
            return new PagedResultOutput<>(count, ir);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    @Override
    public SymbolOutputDto getDetailById(String id) {
        try {
            SymbolEntity query = symbolMapper.selectById(id);
            return BeanUtil.copy(query, SymbolOutputDto.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public SymbolDto insert(SymbolInputDto input) {
        try {
            input.setId(UUID.randomUUID().toString());
            input.setCreateDT(LocalDateTime.now());
            SymbolEntity entity = BeanUtil.copy(input, SymbolEntity.class);
            symbolMapper.insert(entity);
            return BeanUtil.copy(entity, SymbolDto.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public SymbolDto update(SymbolInputDto input) {
        try {
            SymbolEntity entity = BeanUtil.copy(input, SymbolEntity.class);
            symbolMapper.updateById(entity);
            return BeanUtil.copy(entity, SymbolDto.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean delete(String id) {
        try {
            symbolMapper.deleteById(id);
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    /**
     * 根据当前登录人所在行政区划，获取对应行政区划下所有人员信息,在返回对应人员信息相关数据
     *
     * @param userCode
     * @return
     */
    public List<SymbolEntity> getDataByUserCodeAsync(String userCode) {
        try {
            if (Func.isBlank(userCode)) return null;
            List<SymbolEntity> query = null;

            if (ConstHelper.CONST_SYSTEMNAME.equalsIgnoreCase(userCode)) {
                // 超级用户
                query = symbolMapper.selectList(new QueryWrapper<>());
            } else {
                List<SystemUserDto> userQuery = systemUserService.getUserDataByUserCodeAsync(userCode);
                if (Func.isNotEmpty(userQuery)) {
                    List<String> list = userQuery.stream().map(SystemUserDto::getUserCode).collect(Collectors.toList());
                    if (Func.isNotEmpty(list)) {
                        query = symbolMapper.selectList(new LambdaQueryWrapper<SymbolEntity>()
                                .in(SymbolEntity::getCreateBy, list));
                    }
                }
            }
            return query;
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
    }


    /**
     * 通过父类查询所有子类
     *
     * @param typeId 父类id
     * @return
     */
    public String getMultiChildTypeByType(String typeId) {
        try {
            StringBuilder multiTypeId = new StringBuilder(typeId + ",");
            List<DataTypeEntity> query = dataTypeMapper.selectList(new LambdaQueryWrapper<DataTypeEntity>()
                    .eq(DataTypeEntity::getParentID, typeId));

            if (Func.isNotEmpty(query)) {
                for (DataTypeEntity item : query) {
                    multiTypeId.append(item.getId()).append(",");
                }
            }
            if (multiTypeId.length() > 0) {
                multiTypeId.deleteCharAt(multiTypeId.length() - 1);
            }
            return multiTypeId.toString();
        } catch (Exception e) {
            log.debug(e.getMessage());
            return typeId;
        }
    }


}
