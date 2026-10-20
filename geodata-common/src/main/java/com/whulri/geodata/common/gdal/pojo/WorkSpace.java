package com.whulri.geodata.common.gdal.pojo;


import com.whulri.geodata.common.gdal.handler.GeoServer;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WorkSpace {
    private List<DataStore> dataStores;
    private List<Layer> layers;
    private List<LayerGroup> layerGroups;
    private GeoServer geoServer;
    /// 工作区名称
    private String name ;
    /// 工作区地址
    private String href ;


	/// 工作区数据存储
	public List<DataStore> DataStores() throws Exception {
		if (dataStores == null && geoServer != null) {
			List<DataStore> dataStores1 = geoServer.getDataStores(name); // 假设这个方法存在并返回DataStore列表
			dataStores = new ArrayList<>(dataStores1);
		}
		return dataStores != null ? dataStores : new ArrayList<>();
	}

	/// 工作区图层
	public List<Layer> Layers()
	{
		if (layers == null && geoServer != null)
		{
//			List<Layer> layers1 = geoServer.getLayers(name);
//			layers = new ArrayList<>(layers1);
		}
		return layers;
	}

	/// 工作区图层组
	public List<LayerGroup> LayerGroups()
	{
		if (layerGroups == null && geoServer != null)
		{
//			List<LayerGroup> layerGroups1 = geoServer.GetLayerGroups(name);
//			layerGroups = new ArrayList<>(layerGroups1);
		}
		return layerGroups;
	}
}
