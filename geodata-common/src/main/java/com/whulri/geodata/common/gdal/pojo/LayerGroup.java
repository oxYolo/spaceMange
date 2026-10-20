package com.whulri.geodata.common.gdal.pojo;

import lombok.Data;

@Data
public class LayerGroup
{

    public String Name ;

    public String Mode ;

    public WorkSpace Workspace ;

    public PublishedSet LayersSet ;

    public StyleSet Styles ;

    public BoundSet BoundSet ;
}


