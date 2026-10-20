package com.whulri.geodata.common.model;

/**
 * 三维点（反编译自 dev-common-1.1.8，签名与原实现保持一致）
 */
public class Point3D extends XYZ {

    public Point3D() {
        super(0.0, 0.0, 0.0);
    }

    public Point3D(double x, double y) {
        super(x, y, 0.0);
    }

    public Point3D(double x, double y, double z) {
        super(x, y, z);
    }

    @Override
    public Point3D clone() {
        return new Point3D(this.x, this.y, this.z);
    }

    @Override
    public boolean equals(Object o) {
        if (null == o) {
            return false;
        }
        if (o instanceof Point3D) {
            Point3D p = (Point3D) o;
            return this.getX() == p.getX() && this.getY() == p.getY() && this.getZ() == p.getZ();
        }
        return false;
    }

    public void normalize() {
        double length = this.length();
        this.x /= length;
        this.y /= length;
        this.z /= length;
    }

    public double length() {
        return Math.sqrt(this.lengthSq());
    }

    public double lengthSq() {
        return this.x * this.x + this.y * this.y + this.z * this.z;
    }

    public Point3D add(Point3D point) {
        return new Point3D(this.getX() + point.getX(), this.getY() + point.getY(), this.getZ() + point.getZ());
    }

    public Point3D sub(Point3D point) {
        return new Point3D(this.getX() - point.getX(), this.getY() - point.getY(), this.getZ() - point.getZ());
    }

    public Point3D multiply(double n) {
        return new Point3D(this.getX() * n, this.getY() * n, this.getZ() * n);
    }

    public Point3D divide(double n) {
        return new Point3D(this.getX() / n, this.getY() / n, this.getZ() / n);
    }

    public double dotMulti(Point3D point) {
        return this.getX() * point.getX() + this.getY() * point.getY() + this.getZ() * point.getZ();
    }

    public double dotMulti2(Point3D point) {
        return this.getX() * point.getX() + this.getY() * point.getY();
    }

    public Point3D crossMulti(Point3D point) {
        double x = this.getY() * point.getZ() - point.getY() * this.getZ();
        double y = this.getZ() * point.getX() - point.getZ() * this.getX();
        double z = this.getX() * point.getY() - point.getX() * this.getY();
        return new Point3D(x, y, z);
    }
}
