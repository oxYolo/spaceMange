package com.whulri.geodata.common.gdal.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileUploadInfo {


    /**
     * 文件路径
     */
    public String FilePath;

    /**
     * 文件数据
     */
    public byte[] FileData;


}
