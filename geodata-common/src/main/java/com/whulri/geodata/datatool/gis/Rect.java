package com.whulri.geodata.datatool.gis;

import lombok.Data;
import org.locationtech.jts.geom.Envelope;

/**
 * 外接矩形
 */
@Data
public class Rect {
    /**
     *
     */
    public double minx;
    /**
     *
     */
    public double maxx;
    /**
     *
     */
    public double miny;
    /**
     *
     */
    public double maxy;

    /**
     *
     */
    public Rect() {

    }

    /**
     *
     */
    public Rect(double minX, double minY, double maxX, double maxY) {
        minx = minX;
        miny = minY;
        maxx = maxX;
        maxy = maxY;
    }

    /**
     * @return
     */
    @Override
    public String toString() {
        String result;
        result = "x: " + String.format("%1$.5f", minx) + " - " + String.format("%1$.5f", maxx) + "  y: " + String.format("%1$.5f", miny) + " - " + String.format("%1$.5f", maxy);

        return result;
    }

    /**
     * 点是否在多边形内
     *
     * @param lon
     * @param lat
     * @return
     */
    public final boolean inRect(double lon, double lat) {
        return !(lon < minx || lon > maxx || lat < miny || lat > maxy);
    }

    /**
     * 矩形面积
     *
     * @return
     */
    public final double area() {
        return (maxy - miny) * (maxx - minx);
    }

    /**
     * 宽度
     */
    public final double width() {
        return maxx - minx;
    }

    /**
     * 高度
     */
    public final double height() {
        return maxy - miny;
    }

    /**
     * 两个矩形是否相交
     *
     * @param rect
     * @return
     */
    public final boolean intersects(Rect rect) {
        if (rect.minx > maxx || rect.maxx < minx || rect.miny > maxy || rect.maxy < miny) {
            return false;
        }
        return true;
    }

    /**
     * Update bounds.
     *
     * @param x X coordinate.
     * @param y Y coordinate.
     */
    public final void update(float x, float y) {
        minx = Math.min(minx, x);
        miny = Math.min(miny, y);
        maxx = Math.max(maxx, x);
        maxy = Math.max(maxy, y);
    }

    /**
     * 获取两个矩形的交集
     *
     * @param rc1
     * @param rc2
     * @return
     */
    public static Rect getIntersection(Rect rc1, Rect rc2) {
        Rect intersection = new Rect();

        intersection.minx = Math.max(rc1.minx, rc2.minx);
        intersection.miny = Math.max(rc1.miny, rc2.miny);
        intersection.maxx = Math.min(rc1.maxx, rc2.maxx);
        intersection.maxy = Math.min(rc1.maxy, rc2.maxy);

        return intersection;
    }

    /**
     * Envelope类型转为Rect类型
     *
     * @param env
     * @return
     */
    public static Rect envelope2Rect(Envelope env) {
        Rect result = new Rect();
        result.maxx = env.getMaxX();
        result.minx = env.getMinX();
        result.maxy = env.getMaxY();
        result.miny = env.getMinY();

        return result;
    }
}
