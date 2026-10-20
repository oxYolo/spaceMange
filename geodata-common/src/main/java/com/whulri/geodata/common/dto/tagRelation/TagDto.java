package com.whulri.geodata.common.dto.tagRelation;

import lombok.Data;

@Data
public class TagDto {
    private String tagName;
    private String dataID;
    private String mapLayerID;
    private String createUser;
}
