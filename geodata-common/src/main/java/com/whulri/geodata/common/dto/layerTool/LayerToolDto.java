package com.whulri.geodata.common.dto.layerTool;

import lombok.Data;

@Data
public class LayerToolDto {

    private String layerId;

    private String layerName;

    private String detailJson;

    private String createBy;

    private String taskId;

    private String taskType;//1:裁剪  2:重采样  3:栅格矢量化  4:矢量栅格化  5:重分类   6:擦除   7:缓冲区   8:矢量交并补   9:栅格叠加计算

    //裁剪参数
    private CutDto cutDto;

    //重采样参数
    private ResamplingDTO resamplingDto;

    //栅格矢量化参数
    private TifToShpDTO tifToShpDto;

    //矢量栅格化参数
    private ShpToTifDTO shpToTifDto;

    //重分类参数
    private ReclassificationDTO reclassificationDto;



    //缓冲区参数
    private BufferZoneDTO bufferZoneDto;

    //矢量交并补
    private IntersectionAndUnionDto intersectionAndUnionDto;

    //栅格叠加计算
    private OverlayCalculationDTO overlayCalculationDto;
}
