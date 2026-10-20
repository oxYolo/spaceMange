package com.whulri.geodata.common.gdal.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Layer implements Serializable {

    @JsonProperty("name")
    private String name;

    @JsonProperty("href")
    private String href;

    private String NativeName;

    private WorkSpace Workspace;

    private String Title;

    private String Srs;

    private BoundSet BoundSet;

    private DataStore DataStore;

    private AttributesSet AttributesSet;
}



