package com.whulri.geodata.common.model;

/**
 * 三维坐标（反编译自 dev-common-1.1.8）
 */
public class XYZ extends XY {

    protected double z;

    public XYZ(double x, double y) {
        super(x, y);
    }

    public XYZ(double x, double y, double z) {
        super(x, y);
        this.z = z;
    }

    @Override
    public String toString() {
        return String.format("(%s,%s,%s)", this.x, this.y, this.z);
    }

    public double getZ() {
        return this.z;
    }

    public void setZ(double z) {
        this.z = z;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof XYZ)) {
            return false;
        }
        XYZ other = (XYZ) o;
        if (!other.canEqual(this)) {
            return false;
        }
        return Double.compare(this.getZ(), other.getZ()) == 0;
    }

    @Override
    protected boolean canEqual(Object other) {
        return other instanceof XYZ;
    }

    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        long $z = Double.doubleToLongBits(this.getZ());
        result = result * 59 + (int) ($z >>> 32 ^ $z);
        return result;
    }
}
