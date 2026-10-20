package com.whulri.geodata.common.gdal.pojo;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileDownloadInfo {

    /**
     * 下载文件路径
     */
    private String downLoadFilePath;

    /**
     * 文件偏移
     */
    private int offSet;

    /**
     * 读取大小
     */
    private int bufferSize;
}
