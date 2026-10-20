package com.whulri.geodata.common.gdal.pojo;

import lombok.Data;

@Data
public class WorkSpaceRequest
{
	public WorkSpaceRequest(String  workSpaceName)
	{
		WorkSpace = new TargetWorkSpace(workSpaceName)  ;
	}
    private TargetWorkSpace WorkSpace ;

    private class TargetWorkSpace
    {
		public TargetWorkSpace(String name) {
			this.Name = name;
		}
        public String Name ;
    }
}
