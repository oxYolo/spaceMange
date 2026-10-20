package com.whulri.geodata.common.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app-settings")
@Slf4j
public class AppSettingConfig {

    // 发布地址
	private String publishAddress;

    // 转换导出地址
//	private String convertExportPath;

	private String coordPath;

    private String language;
    private String languagePackFilePath;
//	private String map2ShpPath;

	private String dataResourceIp;
//	private String BusinessName;
//	private String ConvertExprotTempPath;

	private String bigDataIp;

//	private String modelsServerIp;

//	private String modelsPath;
//	private String taskModelIdDrill;
//	private String translateFilePath;

//	private String adminUser;

//	private String areaFilePath;

//	private String hangfireIp;

    private String database;

//	private String obj2gltf;

    private String uploadFilePath;
//    private boolean IsConversionCoordinates;

    //	private String HFJobApi;
//	private String uploadTempFile;
	private String downloadFile;
//	private String multiMediaIpNew;`
//	private String multiMediaIp;
//	private String saveFileFolder;
//	private String taskModelId;
//	private String taskModelIdShp;
//	private String taskModelIdBusiness;
//	private String businessName;
//	private String imageType;
//	private String documentType;
//	private String videoType;
//	private String videoSite;
//	private String ssoUrl;
    private String spatialReference;
//	private String spatialReferenceFile;
//	private String calculation;
//	private Double xCell;
//	private Double yCell;


//	private String texturePath;

    private String geoServerIP;
    private String geoServerPort;
    private String geoServerUser;
    private String geoServerPwd;
    private String geoWorkspace;
    private String geoDatastore;
    private String geoDb;
    //	private String spatialRefFile;
    private String epsg;
    private String isAutoCache;
    private String gridSetName;
    private String GISTolerance;
    private String MapMaskField;

//	private String minioIp;
//	private String minioUse;
//	private String minioKey;
//
//	private String isImportShp;
//
//	private String thumbnailPath;
//
//	private String taskUGModel;

    private String geoServerPublishType;
    private String geoServerDataPath;
    private String tiffRemoveBackGroundColor;
    //    private String hangfireIp;
    private String thumbnailPath;
    private int zoomStart;
    private int zoomStop;

    private String PostGisHost;
    private String PostGisPort;
    private String PostGisDB;
    private String PostGisUser;
    private String PostGisPwd;
    private String psqlBinDir;
    private String shp2pgsqlBinDir;

}
