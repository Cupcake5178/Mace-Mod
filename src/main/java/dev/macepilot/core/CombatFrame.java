package dev.macepilot.core;

import java.util.List;
import java.util.UUID;

/** Immutable tick input. Never constructed from hidden/unloaded entity data. */
public record CombatFrame(long tick, String world, UUID selfId, Point3 position, Point3 velocity,
                          double health, boolean alive, boolean grounded, boolean gliding,
                          boolean submerged, boolean screenOpen, boolean corrected,
                          boolean mace, boolean usableElytra, int rockets, int windCharges,
                          double attackStrength, double fallDistance, double pingMillis,
                          UUID crosshairTarget, List<PlayerSnapshot> players) {
    public CombatFrame { players = List.copyOf(players); }
}

