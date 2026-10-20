package com.whulri.geodata.common.gdal.pojo;

import java.util.ArrayList;
import java.util.List;

public class DataStoresResponse {

    public DataStoreSet DataStoresSet ;


  /*  public List<DataStore> DataStores
    {
        get { return (DataStoresSet != null && DataStoresSet.DataStores != null) ? DataStoresSet.DataStores.ToList() : new List<DataStore>(); }
    }*/
  public List<DataStore> DataStores;

    public List<DataStore> getDataStores() {
        return (DataStoresSet != null && DataStoresSet.DataStores != null) ? DataStores : new ArrayList<DataStore>();
    }

    public class DataStoreSet
    {

        public DataStore[] DataStores ;
    }
}
