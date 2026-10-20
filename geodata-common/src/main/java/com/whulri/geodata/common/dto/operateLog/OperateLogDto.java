package com.whulri.geodata.common.dto.operateLog;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.whulri.geodata.common.entity.OperateLogEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class OperateLogDto extends OperateLogEntity {
    @Schema(description = "图层名称")
    @JsonProperty("LayerName")
    private String layerName;
}
