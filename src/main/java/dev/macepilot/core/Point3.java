package dev.macepilot.core;

public record Point3(double x, double y, double z) {
    public static final Point3 ZERO = new Point3(0, 0, 0);
    public Point3 add(Point3 p) { return new Point3(x+p.x, y+p.y, z+p.z); }
    public Point3 subtract(Point3 p) { return new Point3(x-p.x, y-p.y, z-p.z); }
    public Point3 scale(double n) { return new Point3(x*n, y*n, z*n); }
    public double length() { return Math.sqrt(x*x+y*y+z*z); }
    public double horizontal() { return Math.hypot(x,z); }
    public Point3 normalized() { double len=length(); return len<1e-6 ? ZERO : scale(1/len); }
    public double dot(Point3 p) { return x*p.x+y*p.y+z*p.z; }
    public double distance(Point3 p) { return subtract(p).length(); }
}

