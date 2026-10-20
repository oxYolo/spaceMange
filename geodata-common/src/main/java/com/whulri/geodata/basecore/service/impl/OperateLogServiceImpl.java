package com.whulri.geodata.basecore.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.whulri.geodata.basecore.mapper.OperateLogMapper;
import com.whulri.geodata.basecore.service.IOperateLogService;
import com.whulri.geodata.basecore.service.ISystemUserService;
import com.whulri.geodata.common.config.AppSettingConfig;
import com.whulri.geodata.common.dto.operateLog.OperateLogDto;
import com.whulri.geodata.common.dto.operateLog.TranslateOutput;
import com.whulri.geodata.common.entity.OperateLogEntity;
import com.whulri.geodata.common.util.ConstHelper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.whulri.geodata.common.bridge.Func;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @description 针对表【sdms_operatelog】的数据库操作Service实现
 * @createDate 2025-02-28 16:05:38
 */
@Service
@AllArgsConstructor
@Slf4j
public class OperateLogServiceImpl extends ServiceImpl<OperateLogMapper, OperateLogEntity>
        implements IOperateLogService {

    private final OperateLogMapper operateLogMapper;
    private final ISystemUserService userService;
    private final AppSettingConfig appSettingConfig;


    @Override
    public Boolean writeOperateLog(OperateLogDto dto) {
        try {
            String name;
            if (ConstHelper.CONST_SYSTEMCODE.equals(dto.getUserCode())) {
                name = ConstHelper.CONST_SYSTEMNAME;
            } else {
                name = dto.getUserCode();
            }
            List<TranslateOutput> listTranslate = new ArrayList<>();
            Path path = Paths.get(System.getProperty("user.dir"), "Data", "Translate.xml");
            InputStream inputStream = Files.newInputStream(path);
//            Resource resource = new ClassPathResource("data/Translate.xml");
//            InputStream inputStream = resource.getInputStream();
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(inputStream);
            NodeList classNodes = doc.getElementsByTagName("Class");
            for (int i = 0; i < classNodes.getLength(); i++) {
                Element classElement = (Element) classNodes.item(i);
                if ("OperateLog".equals(classElement.getAttribute("name"))) {
                    NodeList wordNodes = classElement.getElementsByTagName("word");
                    for (int j = 0; j < wordNodes.getLength(); j++) {
                        listTranslate.add(getTranslateOutput((Element) wordNodes.item(j)));
                    }
                    break;
                }
            }
            String SystemFunc = null;
            String IsEnglish = Func.equals(appSettingConfig.getLanguage().toUpperCase(), "ENGLISH") ? "1" : "0";
            boolean bIsEnglish = Func.equals(IsEnglish, "1");
            switch (dto.getSystemFunc()) {
                case "1":
                    SystemFunc = getTranslate("1001", bIsEnglish, listTranslate);
                    break;
                case "2":
                    SystemFunc = getTranslate("1002", bIsEnglish, listTranslate);
                    break;
                case "3":
                    SystemFunc = getTranslate("1003", bIsEnglish, listTranslate);
                    break;
                default:
                    break;
            }
            String OperateType = null;
            switch (dto.getOperateType()) {
                case "1":
                    OperateType = getTranslate("1101", bIsEnglish, listTranslate);
                    break;
                case "2":
                    OperateType = getTranslate("1102", bIsEnglish, listTranslate);
                    break;
                case "3":
                    OperateType = getTranslate("1103", bIsEnglish, listTranslate);
                    break;
                case "4":
                    OperateType = getTranslate("1104", bIsEnglish, listTranslate);
                    break;
                case "5":
                    OperateType = getTranslate("1105", bIsEnglish, listTranslate);
                    break;
                case "6":
                    OperateType = getTranslate("1106", bIsEnglish, listTranslate);
                    break;
                default:
                    break;
            }
            String Result = null;
            switch (dto.getResult()) {
                case "成功":
                    Result = getTranslate("1201", bIsEnglish, listTranslate);
                    break;
                case "失败":
                    Result = getTranslate("1202", bIsEnglish, listTranslate);
                    break;
                default:
                    break;
            }
            OperateLogEntity entity = new OperateLogEntity();
            // 主键显式赋值（varchar 主键，不依赖数据库自增）
            entity.setId(java.util.UUID.randomUUID().toString());
            entity.setLayerID(dto.getLayerID());
            entity.setOperateTime(LocalDateTime.now());
            entity.setUserName(name);
            entity.setUserCode(dto.getUserCode());
            entity.setSystemFunc(SystemFunc);
            entity.setOperateType(OperateType);
            entity.setResult(Result);
            entity.setResultDescribe(getTranslate(dto.getResultDescribe(), bIsEnglish, listTranslate) + dto.getLayerName());
            int insert = operateLogMapper.insert(entity);
            return insert > 0;
        } catch (Exception e) {
            log.warn("记录操作日志失败: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public Boolean writeOperateLog(String LayerID, String userCode, int systemFunc, int operateType, int result, int resultDescribe, String LayerName) {
        OperateLogDto dto = new OperateLogDto();
        dto.setLayerID(LayerID);
        dto.setUserCode(userCode);
        dto.setSystemFunc(String.valueOf(systemFunc));
        dto.setOperateType(String.valueOf(operateType));
        dto.setResult(String.valueOf(result));
        dto.setResultDescribe(String.valueOf(resultDescribe));
        dto.setLayerName(LayerName);
        return writeOperateLog(dto);
    }

    private static TranslateOutput getTranslateOutput(Element data) {
        TranslateOutput obj = new TranslateOutput();
        obj.setCode(data.getAttribute("Code"));
        obj.setChinese(data.getAttribute("Chinese"));
        obj.setEnglish(data.getAttribute("English"));
        return obj;
    }

    private static String getTranslate(String code, boolean bIsEnglish, List<TranslateOutput> list) {
        if (Func.isEmpty(code)) {
            return null;
        }
        String s = list.stream()
                .filter(item -> code.equals(item.getCode()))
                .findFirst()
                .map(item -> bIsEnglish ? item.getEnglish() : item.getChinese())
                .orElse(null);
        return s;
    }
}
