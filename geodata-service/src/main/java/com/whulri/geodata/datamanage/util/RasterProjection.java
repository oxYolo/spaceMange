
package com.whulri.geodata.datamanage.util;

import java.util.Vector;
import lombok.extern.slf4j.Slf4j;

import com.whulri.gdal.config.GDALConfig;
import com.whulri.geodata.datamanage.config.GdalEnvInitializer;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.WarpOptions;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconstConstants;

@Slf4j
public class RasterProjection {
    public RasterProjection() {
    }

    public static void ChangeRasterSrs(String sourcePath, String targetPath, String projWKT) {
        gdal.AllRegister();
        GdalEnvInitializer.ensureConfigured();
        Dataset dataset = null;
        Dataset target = null;

        try {
            dataset = gdal.Open(sourcePath, gdalconstConstants.GA_ReadOnly);
            if (dataset == null) {
                return;
            }
            int width = dataset.GetRasterXSize();
            int height = dataset.GetRasterYSize();
            String sourceProjWkt = dataset.GetProjection();
            
            // 获取源数据的NoData值
            Double[] noDataVal = new Double[1];
            dataset.GetRasterBand(1).GetNoDataValue(noDataVal);

            Vector<String> options = new Vector<>();
            
            // 如果源数据有NoData值，则保持一致；否则不强制设置，或者根据数据类型设置默认值
            if (noDataVal[0] != null) {
                options.add("-srcnodata");
                options.add(String.valueOf(noDataVal[0]));
                options.add("-dstnodata");
                options.add(String.valueOf(noDataVal[0]));
            } else {
                // 如果没有NoData值，可以考虑设置0为NoData，或者不设置，视情况而定
                // 这里暂时不强制设置 -9999，防止覆盖有效数据
                // options.add("-dstnodata");
                // options.add("0");
            }

            if (sourceProjWkt != null && !sourceProjWkt.isEmpty()) {
                options.add("-s_srs");
                options.add(sourceProjWkt);
            }
            
            options.add("-t_srs");
            options.add(projWKT);

            options.add("-ts");
            options.add(String.valueOf(width));
            options.add(String.valueOf(height));
            
            // 使用双线性插值，效果更好
            options.add("-r");
            options.add("bilinear");
            
            options.add("-multi");
            options.add("-wo");
            options.add("NUM_THREADS=ALL_CPUS");
            options.add("-co");
            options.add("COMPRESS=LZW");
            options.add("-co");
            options.add("TILED=YES");
            // 移除固定的块大小，使用默认或根据需要设置
            // options.add("-co");
            // options.add("BLOCKXSIZE=256");
            // options.add("-co");
            // options.add("BLOCKYSIZE=256");
            
            WarpOptions warpOptions = new WarpOptions(options);
            target = gdal.Warp(targetPath, new Dataset[]{dataset}, warpOptions);
        } catch (Exception var14) {
            Exception ex = var14;
            log.error("GDAL 投影转换失败: source={}, target={}, err={}", sourcePath, targetPath, ex.getMessage(), ex);
        } finally {
            if (dataset != null) {
                dataset.delete();
            }

            if (target != null) {
                target.delete();
            }

        }

    }
}
