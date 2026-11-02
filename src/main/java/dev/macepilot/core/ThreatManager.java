package dev.macepilot.core;

import dev.macepilot.config.Settings;
import java.util.*;

public final class ThreatManager {
    public record Threat(PlayerSnapshot player, double distance, double score, int neighbors, double opportunity) {}
    public record Memory(UUID id, String name, Point3 lastPosition, Point3 velocity, double health,
                         long lastKnownTick, double threat, boolean recentlyAttackedUs, boolean wasPrimary) {}
    private final Settings settings;
    private final FriendManager friends;
    private final Map<UUID,Memory> memory = new HashMap<>();
    private List<Threat> threats = List.of();
    private int danger;
    private Point3 escape = Point3.ZERO;

    public ThreatManager(Settings settings, FriendManager friends) { this.settings=settings; this.friends=friends; }
    public List<Threat> threats() { return threats; }
    public Collection<Memory> memory() { return List.copyOf(memory.values()); }
    public int danger() { return danger; }
    public Point3 escapeDirection() { return escape; }
    public void clear() { threats=List.of(); memory.clear(); danger=0; escape=Point3.ZERO; }

    public boolean eligible(CombatFrame frame, PlayerSnapshot p, double range) {
        return !p.id().equals(frame.selfId()) && p.alive() && p.health()>0 && !p.spectator()
                && !friends.excluded(p) && frame.position().distance(p.position()) <= range;
    }

    public void update(CombatFrame frame, UUID primary) {
        // Bound the expensive pairwise clustering pass by a configurable cap <=16.
        List<PlayerSnapshot> nearby=frame.players().stream()
                .filter(p->eligible(frame,p,settings.threatRange))
                .sorted(Comparator.comparingDouble(p->p.position().distance(frame.position())))
                .limit(settings.maxThreats).toList();
        List<Threat> result = new ArrayList<>();
        Point3 away=Point3.ZERO;
        danger=0;
        for (PlayerSnapshot p:nearby) {
            double distance=p.position().distance(frame.position());
            int cluster=0;
            for(PlayerSnapshot other:nearby) if(!other.id().equals(p.id()) && p.position().distance(other.position())<settings.dangerRadius) cluster++;
            Point3 toward=frame.position().subtract(p.position()).normalized();
            double closing=Math.max(0,p.velocity().subtract(frame.velocity()).dot(toward));
            double looking=Math.max(0,p.look().dot(toward));
            double score=18/(distance+1) + closing*5 + looking*2 + (p.recentlyAttackedUs()?8:0)
                    + (distance<3?8:0) + Math.max(0,p.position().y()-frame.position().y())*.1;
            double opportunity=score*.2 + (20-p.health())*.55 + 12/(distance+2)
                    - (settings.preferIsolated?cluster*4:0) + (p.visible()?2:0)
                    + (frame.position().y()-p.position().y()>settings.minSmashHeight?2:0);
            result.add(new Threat(p,distance,score,cluster,opportunity));
            if(distance<settings.dangerRadius) {
                danger++;
                away=away.add(frame.position().subtract(p.position()).scale(1/Math.max(1,distance*distance)));
            }
            Memory old=memory.get(p.id());
            memory.put(p.id(),new Memory(p.id(),p.name(),p.position(),p.velocity(),p.health(),frame.tick(),score,
                    p.recentlyAttackedUs(),p.id().equals(primary) || old!=null && old.wasPrimary()));
        }
        // History is never used as a source of live targets or predicted hidden positions.
        memory.values().removeIf(m->frame.tick()-m.lastKnownTick()>100);
        threats=List.copyOf(result);
        escape=new Point3(away.x(),0,away.z()).normalized();
    }

    public int enemiesNear(Point3 position, UUID except) {
        return (int)threats.stream().filter(t->!t.player().id().equals(except)
                && t.player().position().distance(position)<settings.dangerRadius).count();
    }
    public int fightSize() { return Math.min(settings.fightMode.limit,threats.size()); }
}

