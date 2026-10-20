package com.whulri.geodata.common.gdal.pojo;

import java.util.ArrayList;
import java.util.List;

public class StylesResponse
{

    public StyleSet StylesSet ;


 /*   public List<Style> Styles
    {
        get { return (StylesSet != null && StylesSet.Styles != null) ? StylesSet.Styles.ToList() : new List<Style>(); }
    }*/
    public List<Style> Styles;

    public List<Style> getStyles() {
        return (StylesSet!=null&&StylesSet.Styles!=null)?StylesSet.Styles:new ArrayList<Style>();
    }
}


