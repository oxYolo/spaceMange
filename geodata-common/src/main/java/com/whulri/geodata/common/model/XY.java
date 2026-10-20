package com.whulri.geodata.common.model;

/**
 * 二维坐标（反编译自 dev-common-1.1.8）
 */
public class XY {

    protected double x;
    protected double y;

    public XY(double x, double y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public String toString() {
        return String.format("(%s,%s)", this.x, this.y);
    }

    public double getX() {
        return this.x;
    }

    public double getY() {
        return this.y;
    }

    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof XY)) {
            return false;
        }
        XY other = (XY) o;
        if (!other.canEqual(this)) {
            return false;
        }
        if (Double.compare(this.getX(), other.getX()) != 0) {
            return false;
        }
        return Double.compare(this.getY(), other.getY()) == 0;
    }

    protected boolean canEqual(Object other) {
        return other instanceof XY;
    }

    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        long $x = Double.doubleToLongBits(this.getX());
        result = result * 59 + (int) ($x >>> 32 ^ $x);
        long $y = Double.doubleToLongBits(this.getY());
        result = result * 59 + (int) ($y >>> 32 ^ $y);
        return result;
    }
}
