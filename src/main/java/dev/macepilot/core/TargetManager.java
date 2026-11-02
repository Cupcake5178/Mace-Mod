package dev.macepilot.core;

import dev.macepilot.config.Settings;
import java.util.*;

public final class TargetManager {
    public enum Loss { NONE, DEAD, DISAPPEARED, OUT_OF_RANGE, EXCLUDED }
    private final Settings settings;
    private final ThreatManager threats;
    private UUID primary, preferred;
    private long switchedAt = Long.MIN_VALUE/2;
    public TargetManager(Settings settings, ThreatManager threats) { this.settings=settings; this.threats=threats; }
    public UUID primary() { return primary; }
    public UUID preferred() { return preferred; }
    public void clear() { primary=null; preferred=null; switchedAt=Long.MIN_VALUE/2; }
    public PlayerSnapshot current(CombatFrame frame) {
        return frame.players().stream().filter(p->p.id().equals(primary)).findFirst().orElse(null);
    }
    public boolean lock(CombatFrame frame, UUID id) {
        PlayerSnapshot p=frame.players().stream().filter(v->v.id().equals(id)).findFirst().orElse(null);
        if(p==null || !threats.eligible(frame,p,settings.chaseRange)) return false;
        preferred=primary=id; switchedAt=frame.tick(); return true;
    }
    public void cycle(CombatFrame frame, int direction) {
        List<UUID> ids=threats.threats().stream().map(t->t.player().id()).toList();
        if(ids.isEmpty()) return;
        int index=ids.indexOf(primary);
        lock(frame,ids.get(Math.floorMod(index+(direction<0?-1:1),ids.size())));
    }
    public Loss loss(CombatFrame frame) {
        if(primary==null) return Loss.NONE;
        PlayerSnapshot p=current(frame);
        if(p==null) return Loss.DISAPPEARED;
        if(!p.alive() || p.health()<=0) return Loss.DEAD;
        if(p.position().distance(frame.position())>settings.chaseRange) return Loss.OUT_OF_RANGE;
        if(!threats.eligible(frame,p,settings.chaseRange)) return Loss.EXCLUDED;
        return Loss.NONE;
    }
    public void forgetPrimary() { if(Objects.equals(preferred,primary)) preferred=null; primary=null; }

    public PlayerSnapshot select(CombatFrame frame) {
        PlayerSnapshot current=current(frame);
        if(current!=null && loss(frame)==Loss.NONE) {
            boolean sticky=settings.fightMode==Settings.FightMode.ONE || preferred!=null
                    || settings.finishCurrentTarget || settings.priority==Settings.Priority.CURRENT_TARGET;
            if(sticky || frame.tick()-switchedAt<settings.switchCooldown) return current;
        }
        if(settings.targetMode==Settings.TargetMode.MANUAL_LOCK) return current;
        List<ThreatManager.Threat> candidates=threats.threats().stream()
                .filter(t->t.player().visible())
                .filter(t->settings.targetMode!=Settings.TargetMode.CROSSHAIR || t.player().id().equals(frame.crosshairTarget()))
                .toList();
        ThreatManager.Threat best=candidates.stream().max(Comparator.comparingDouble(this::utility)
                .thenComparing(t->t.player().id())).orElse(null);
        if(best==null) return current;
        if(current!=null) {
            ThreatManager.Threat old=threats.threats().stream().filter(t->t.player().id().equals(current.id())).findFirst().orElse(null);
            // Require an absolute and relative improvement; avoids jitter near zero scores.
            if(old!=null && utility(best)<utility(old)+Math.max(2,Math.abs(utility(old))*.2)) return current;
        }
        if(!best.player().id().equals(primary)) { primary=best.player().id(); switchedAt=frame.tick(); }
        return best.player();
    }

    private double utility(ThreatManager.Threat t) {
        if(settings.targetMode==Settings.TargetMode.NEAREST) return 100/(1+t.distance());
        if(settings.targetMode==Settings.TargetMode.LOWEST_HEALTH) return 40-t.player().health();
        return switch(settings.priority) {
            case CLOSEST -> 100/(1+t.distance());
            case LOWEST_HEALTH -> 40-t.player().health();
            case HIGHEST_THREAT -> t.score();
            case ISOLATED -> t.opportunity()-t.neighbors()*5;
            case SMART, CURRENT_TARGET -> t.opportunity();
        };
    }
}

