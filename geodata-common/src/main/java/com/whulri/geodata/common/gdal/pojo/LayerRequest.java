package com.whulri.geodata.common.gdal.pojo;

public class LayerRequest
{
/*    public LayerRequest(String name, String desiredSrs)
    {
        Layer = new TargetLayer() { this.Name = name, Srs = desiredSrs, IsEnabled = true, Advertised = true };
        Layer.Parameters = new LayerParameterSet();
        Layer.Parameters.Entries = new ParameterEntry[]{new ParameterEntry(){KeyValue = new String[]{ "USE_JAI_IMAGEREAD", "false"}},
                new ParameterEntry(){KeyValue = new String[]{"USE_MULTITHREADING", "true"}},
                new ParameterEntry(){KeyValue = new String[]{"SUGGESTED_TILE_SIZE", "256,256"}}};
        SrsSet set = new SrsSet() { Srs = new String[] { desiredSrs } };
        Layer.RequestSrs = set;
        Layer.ResponseSrs = set;
    }*/


    private TargetLayer Layer;
}


