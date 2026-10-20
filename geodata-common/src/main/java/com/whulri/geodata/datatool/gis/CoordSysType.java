package com.whulri.geodata.datatool.gis;


import com.whulri.geodata.common.model.OutObject;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.gdal;
import org.gdal.osr.SpatialReference;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 坐标系统类型
 */
public enum CoordSysType {
    /**
     * 地理坐标系
     */
    CST_GEO,

    /**
     * 投影坐标系
     */
    CST_PRJ,

    /**
     * 未知坐标系
     */
    CST_UNKNOWN;

    public static final int SIZE = java.lang.Integer.SIZE;

    public int getValue() {
        return this.ordinal();
    }

    public static CoordSysType forValue(int value) {
        return values()[value];
    }

    /**
     * 坐标系
     */
    public static class CoordSystem {
        private String descript; //坐标系原始描述字符串

        private SpatialReference sr = null;
        private String coodSysName = "";
        private CoordSysType type = CoordSysType.CST_UNKNOWN;

        /**
         * SpatialReference
         */
        public final SpatialReference getSR() {
            return sr;
        }

        /**
         * 坐标系可辨识名称
         */
        public final String getName() {
            return coodSysName;
        }

        /**
         * 坐标系名称的简短描述,如WGS84/BJ54/XIAN80
         */
        public final String getShortName() {
            String temp = getName().toLowerCase();

            if (this.type == CoordSysType.CST_GEO) {
                if (temp.equals("wgs 84")) {
                    return "WGS84";
                } else if (temp.equals("gcs_beijing_1954")) {
                    return "BJ54";
                } else if (temp.equals("gcs_xian_1980")) {
                    return "XIAN80";
                }
            } else if (this.type == CoordSysType.CST_PRJ) {
                if (temp.equals("gcs_wgs_1984")) {
                    return "WGS84";
                }

                //Regex reg = new Regex("((xian_1980)|(beijing_1954))(_3_degree)?_gk_((cm)|(zone))_(.+)", RegexOptions.IgnoreCase);
                Pattern reg = Pattern.compile("((xian_1980)|(beijing_1954))(_3_degree)?_gk_((cm)|(zone))_(.+)", Pattern.CASE_INSENSITIVE);
                if (reg.matcher(getName()).matches()) {
                    Matcher match = reg.matcher(getName());
                    String[] submatchs = new String[match.groupCount()];
                    for (int i = 0; i < submatchs.length; i++)
                        submatchs[i++] = match.group(i);
                    //Count == 9
                    StringBuilder sb = new StringBuilder();

                    if (!submatchs[2].equals("")) { //xian_1980
                        sb.append("XIAN80");
                    } else if (!submatchs[3].equals("")) { //beijing_1954
                        sb.append("BJ54");
                    } else {
                        return getName();
                    }

                    if (!submatchs[4].equals("")) { //_3_degree
                        sb.append("_3度");
                    }

                    sb.append("_");
                    sb.append(submatchs[8]); //num

                    return sb.toString();
                }

            }

            return getName();
        }

        /**
         * 坐标系类型
         */
        public final CoordSysType getCSType() {
            return type;
        }

        /**
         * 坐标系WKT格式定义
         */
        public final String getWKT() {
            String wkt;
            OutObject<String> tempOutWkt = new OutObject();
            String[] strings = new String[1];
            sr.ExportToWkt(strings);
            tempOutWkt.value = strings[0];
            wkt = tempOutWkt.value;
            wkt = wkt.replaceAll("WGS\\s+84", "WGS84");
            wkt = wkt.replaceAll("WGS\\s*84\\s*/\\s*UTM\\s*zone\\s*50N", "WGS84/UTMzone50N");
            return wkt;
        }

        /**
         * 构造函数
         *
         * @param projection 坐标系描述字符串
         */
        public CoordSystem(String projection) {
            descript = projection;
            this.sr = new SpatialReference(projection);

            if (sr.IsGeographic() == 1 && sr.IsProjected() == 0 && sr.IsLocal() == 0) {
                this.coodSysName = this.sr.GetAttrValue("GEOGCS", 0);
                this.type = CoordSysType.CST_GEO;
            } else if (sr.IsGeographic() == 0 && sr.IsProjected() == 1 && sr.IsLocal() == 0) {
                this.coodSysName = this.sr.GetAttrValue("PROJCS", 0);
                this.type = CoordSysType.CST_PRJ;
            } else if (sr.IsGeographic() == 0 && sr.IsProjected() == 0) {
                this.type = CoordSysType.CST_UNKNOWN;
            }
        }

        /**
         * 构造函数
         *
         * @param sr 坐标系
         */
        public CoordSystem(SpatialReference sr) {
            if (sr == null) {
                return;
            }
            OutObject<String> tempOutDescript = new OutObject<String>();
            String[] strings = new String[1];
            sr.ExportToWkt(strings);
            tempOutDescript.value = strings[0];
            descript = tempOutDescript.value;

            this.sr = sr;

            if (sr.IsGeographic() == 1 && sr.IsProjected() == 0 && sr.IsLocal() == 0) {
                this.coodSysName = this.sr.GetAttrValue("GEOGCS", 0);
                this.type = CoordSysType.CST_GEO;
            } else if (sr.IsGeographic() == 0 && sr.IsProjected() == 1 && sr.IsLocal() == 0) {
                this.coodSysName = this.sr.GetAttrValue("PROJCS", 0);
                this.type = CoordSysType.CST_PRJ;
            } else if (sr.IsGeographic() == 0 && sr.IsProjected() == 0) {
                this.type = CoordSysType.CST_UNKNOWN;
            }
        }

        /**
         * 给栅格或矢量文件添加坐标系
         *
         * @param path 文件完整路径
         * @return 成功返回true，失败返回false
         */
        public final boolean setCoordSystem(String path) {
            GdalHelp.initGdal();
            Dataset ds = gdal.Open(path);
            if (ds != null) {
                int i = ds.SetProjection(getWKT());
                ds.FlushCache();
                return i != 0;
            }
            return false;
        }

        /**
         * 获得投影坐标对应的地理坐标系定义
         *
         * @return 1.坐标系自身不是投影坐标系时返回string.Empty
         * 2.投影系不是bj54/xian80/wgs84时也返回string.Empty
         */
        public final String getGeoCoordSystem() {
            if (type != CoordSysType.CST_PRJ) {
                return "";
            }

            String geocs;
            OutObject<String> tempOutGeocs = new OutObject<String>();
            String[] strings = new String[1];
            sr.CloneGeogCS().ExportToWkt(strings);
            tempOutGeocs.value = strings[0];
            geocs = tempOutGeocs.value;
            geocs = geocs.replaceAll("WGS\\s+84", "WGS84");
            return geocs;
        }

        /**
         * 获得投影坐标对应的地理坐标系定义
         *
         * @return 1.坐标系自身不是投影坐标系时返回string.Empty
         * 2.投影系不是bj54/xian80/wgs84时也返回string.Empty
         */
        public static String getGeoCoordSystem(String src) {
            return (new CoordSystem(src)).getGeoCoordSystem();
        }
    }


}