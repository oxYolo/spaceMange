package com.whulri.geodata.common.gdal.pojo;

import java.util.ArrayList;
import java.util.List;

public class CoverageStoreResponse
{

    public CoverageStoreSet CoverageStoresSet ;


  /*  public List<CoverageStore> CoverageStores
    {
        get { return (CoverageStoresSet != null && CoverageStoresSet.CoverageStores != null) ? CoverageStoresSet.CoverageStores.ToList() : new List<CoverageStore>(); }
    }*/

    public List<CoverageStore> CoverageStores;

    public List<CoverageStore> getCoverageStores() {
        return (CoverageStoresSet != null && CoverageStoresSet.CoverageStores != null) ? CoverageStores : new ArrayList<CoverageStore>();
    }

    public class CoverageStoreSet {

        public CoverageStore[] CoverageStores;
    }
}
