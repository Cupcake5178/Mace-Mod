package dev.macepilot.core;

public final class RecoveryController {
    public enum Outcome { ATTEMPTED_HIT, MISSED, PASSED_TARGET, LANDED, AIRBORNE, TERRAIN_COLLISION, TARGET_MOVED, TARGET_DIED, TARGET_LOST, ENEMY_CLUSTER }
    public Outcome evaluate(CombatFrame f, PlayerSnapshot target, int danger, boolean attacked) {
        if(target==null) return Outcome.TARGET_LOST;
        if(!target.alive() || target.health()<=0) return Outcome.TARGET_DIED;
        if(danger>=2) return Outcome.ENEMY_CLUSTER;
        if(f.grounded()) return Outcome.LANDED;
        if(attacked) return Outcome.ATTEMPTED_HIT; // A client swing is not authoritative confirmation of damage.
        if(f.position().y()<target.position().y()) return Outcome.PASSED_TARGET;
        if(f.position().subtract(target.position()).horizontal()>8) return Outcome.TARGET_MOVED;
        return Outcome.AIRBORNE;
    }
}

