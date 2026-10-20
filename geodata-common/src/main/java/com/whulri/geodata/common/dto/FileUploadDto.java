package com.whulri.geodata.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadDto {
	private String fileGuid;
	private String physicalName;
	private String logicName;
	private String fileSize;
	private String extension;
	private String physicalPath;
	private String httpPath;
	private boolean isImport;
	private String message;
}
