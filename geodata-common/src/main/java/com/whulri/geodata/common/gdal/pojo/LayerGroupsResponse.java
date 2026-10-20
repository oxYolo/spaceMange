package com.whulri.geodata.common.gdal.pojo;

import java.util.ArrayList;
import java.util.List;

public class LayerGroupsResponse
{

    public LayerGroupSet LayerGroupsSet;


 /*   public List<LayerGroup> LayerGroups
    {
        get { return (LayerGroupsSet != null && LayerGroupsSet.LayerGroups != null) ? LayerGroupsSet.LayerGroups.ToList() : new List<LayerGroup>(); }
    }*/

    public List<LayerGroup> LayerGroups;

    public List<LayerGroup> getLayerGroups() {
        return (LayerGroupsSet!=null &&LayerGroupsSet.LayerGroups!=null)?LayerGroupsSet.LayerGroups:new ArrayList<LayerGroup>();
    }
}


