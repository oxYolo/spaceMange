package com.whulri.geodata.common.gdal.pojo;

import lombok.Data;

@Data
public class Coverage
{
    public String Name ;

    public String Srs ;

    public Double West;
    public Double South;
    public Double East;
    public Double North;

   /* public String url;*/
}
