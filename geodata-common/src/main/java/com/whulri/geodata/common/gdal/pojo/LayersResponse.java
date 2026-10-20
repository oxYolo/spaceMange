package com.whulri.geodata.common.gdal.pojo;

import java.util.ArrayList;
import java.util.List;

public class LayersResponse {

    public LayerSet LayersSet;


   /* public List<Layer> Layers
    {
        get {
        return (LayersSet != null && LayersSet.Layers != null) ? LayersSet.Layers.ToList() : new List<Layer>();
    }*/

    public List<Layer> Layers;

    public List<Layer> getLayers() {
        return (LayersSet!=null && LayersSet.Layers!=null)?LayersSet.Layers:new ArrayList<Layer>();
    }
}

