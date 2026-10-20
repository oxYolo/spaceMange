package com.whulri.geodata.common.util;

import com.whulri.geodata.common.config.AppSettingConfig;
import com.whulri.geodata.datatool.vector.ShpReader;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.gdal.ogr.Feature;
import org.gdal.ogr.Geometry;
import org.gdal.ogr.Layer;

import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class ShpHelper {

    private AppSettingConfig appSettingConfig;
    private String shpFilePath =  appSettingConfig.getUploadFilePath();
    private ShpReader shpReader;
    private static Layer ogrLayer;

    public ShpHelper(String shpName) {
        String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        String fullPath = Paths.get(shpFilePath, date, shpName).toString();
        this.shpReader = new ShpReader(fullPath);
    }

    public ShpHelper(String filePath, String fileName) {
        String fullPath = Paths.get(filePath, fileName).toString();
        this.shpReader = new ShpReader(fullPath);
    }

    public Map<String, String> DataList() {
        return shpReader.getAttrList();
    }

    public List<Feature> GetAllFeatures() {
        return shpReader.getAllFeatures();
    }

    public boolean IsException() {
        return shpReader.isValidDataSource();
    }

    public boolean IsInvalidSpatialRefence() {
        String csSrc = shpReader.getSrIdWkt();
        return !csSrc.toUpperCase().contains("SpatialRefence".toUpperCase()) &&
                !csSrc.toUpperCase().contains("SpatialRefence".toUpperCase().replace("GCS_", ""));
    }

    public List<String> GetShpFile(String uploadFile, Date createDT) {
        String date = new SimpleDateFormat("yyyy-MM-dd").format(createDT);
        String filename = uploadFile.substring(0, uploadFile.lastIndexOf('.'));
        List<String> files = new ArrayList<>();
        files.add(Paths.get(shpFilePath, date, filename + ".shp").toString());
        files.add(Paths.get(shpFilePath, date, filename + ".prj").toString());
        files.add(Paths.get(shpFilePath, date, filename + ".shx").toString());
        files.add(Paths.get(shpFilePath, date, filename + ".dbf").toString());
        return files;
    }

    public static String GetWKT(List<String> files) {
        String allWKTs = "";
        Geometry poly = null;
        for (String file : files) {
            ShpReader shpReader = new ShpReader(file);
            List<Feature> featureInfos = shpReader.getAllFeatures();
            Geometry poly1 = featureInfos.get(0).GetGeometryRef();
            for (int i = 1; i < featureInfos.size(); i++) {
                poly1 = poly1.Union(featureInfos.get(i).GetGeometryRef());
            }
            if (poly == null) {
                poly = poly1;
            } else {
                poly = poly.Union(poly1);
            }
        }
        if (poly != null) {
            allWKTs = poly.ExportToWkt();
        }
        return allWKTs;
    }
}

