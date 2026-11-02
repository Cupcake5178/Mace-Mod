package dev.macepilot.core;

import dev.macepilot.config.Settings;

public final class AimController {
    public record Rotation(double yaw, double pitch) {}
    private final Settings settings;
    public AimController(Settings settings) { this.settings=settings; }
    public Point3 intercept(CombatFrame frame, PlayerSnapshot target) {
        if(!settings.prediction) return target.position();
        double vertical=Math.max(0, frame.position().y()-target.position().y());
        double fallSpeed=Math.max(.15,-frame.velocity().y());
        double ticks=Math.min(12,vertical/(fallSpeed+.5)+Math.clamp(frame.pingMillis(),0,500)/100);
        Point3 offset=target.velocity().scale(ticks);
        if(offset.horizontal()>4) offset=offset.scale(4/offset.horizontal());
        return target.position().add(offset);
    }
    public Rotation toward(Point3 eye, Point3 destination, double yaw, double pitch) {
        Point3 delta=destination.subtract(eye);
        double desiredYaw=Math.toDegrees(Math.atan2(-delta.x(),delta.z()));
        double desiredPitch=-Math.toDegrees(Math.atan2(delta.y(),delta.horizontal()));
        double dy=wrap(desiredYaw-yaw)*settings.aimSpeed;
        double dp=(desiredPitch-pitch)*settings.aimSpeed;
        double yLimit=Math.min(settings.horizontalTurn,settings.maxRotation);
        double pLimit=Math.min(settings.verticalTurn,settings.maxRotation);
        dy=Math.clamp(dy,-yLimit,yLimit); dp=Math.clamp(dp,-pLimit,pLimit);
        double length=Math.hypot(dy,dp);
        if(length>settings.maxRotation) { dy*=settings.maxRotation/length; dp*=settings.maxRotation/length; }
        return new Rotation(yaw+dy,Math.clamp(pitch+dp,-90,90));
    }
    public static double wrap(double degrees) { return ((degrees+180)%360+360)%360-180; }
}

