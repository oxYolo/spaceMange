package com.whulri.geodata.common.gdal.pojo;

import java.util.ArrayList;
import java.util.List;

public class WorkSpacesResponse
{

    public WorkSpaceSet workspaces;


 /*   public List<WorkSpace> WorkSpaces
    {
        get { return (WorkSpacesSet != null && WorkSpacesSet.WorkSpaces != null) ? WorkSpacesSet.WorkSpaces.ToList() : new List<WorkSpace>(); }
    }*/
 	public List<WorkSpace> WorkSpaces;

    public List<WorkSpace> getWorkSpaces() {
        return (workspaces !=null && workspaces.workspace !=null)? workspaces.workspace :new ArrayList<>(WorkSpaces);
    }


}
