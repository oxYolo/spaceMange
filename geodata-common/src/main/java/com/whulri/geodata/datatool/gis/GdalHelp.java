package com.whulri.geodata.datatool.gis;

import cn.hutool.core.io.FileUtil;
import com.whulri.geodata.common.Environment;
import com.whulri.geodata.common.io.Path;
import lombok.extern.slf4j.Slf4j;
import org.gdal.gdal.gdal;
import org.gdal.ogr.ogr;
import org.gdal.osr.osr;

@Slf4j
public class GdalHelp {
    private static boolean isInit = false;

    static {
        initGdal();
    }

    /**
     * 初始化GDAL
     */
    public static void initGdal() {
        if (isInit) return;

        proj7Config();

        gdal.AllRegister();
        ogr.RegisterAll();

        gdal.SetConfigOption("GDAL_FILENAME_IS_UTF8", "YES");    // 为了支持中文路径，请添加下面这句代码
        gdal.SetConfigOption("SHAPE_ENCODING", "CP936");              // 为了使属性表字段支持中文，请添加下面这句

//        initGdalDir();

        isInit = true;
    }

    /**
     * 初始化proj7
     */
    public static void proj7Config() {
        if (isInit) return;

        String proj7Dir = Path.combine(Environment.getHomeDir(), "proj7");
//        log.warn("proj7Dir:" + proj7Dir);
        osr.SetPROJSearchPath(proj7Dir);
    }

    public static void initGdalDir() {
        String dataDir = getGdalDataDir();
        String pluginDir = getGdalPluginDir();

        gdal.SetConfigOption("GDAL_DATA", dataDir);
        gdal.SetConfigOption("GDAL_DRIVER_PATH", pluginDir);
    }

    /**
     * @return 获取gdal的data目录
     */
    public static String getGdalDataDir() {
        String dir = Path.combine(Environment.getHomeDir(), "data");
        if (FileUtil.exist(dir)) return dir;
        return "";
    }

    /**
     * @return 获取gdal的plugin目录
     */
    public static String getGdalPluginDir() {
        String dir = Path.combine(Environment.getHomeDir(), "plugins");
        if (FileUtil.exist(dir)) return dir;
        return "";
    }
}
