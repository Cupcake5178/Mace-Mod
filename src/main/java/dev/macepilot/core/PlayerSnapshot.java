package dev.macepilot.core;

import java.util.UUID;

/** Only current client-observed data; health is an observation, not server authority. */
public record PlayerSnapshot(UUID id, String name, Point3 position, Point3 velocity,
                             Point3 look, double health, boolean alive, boolean spectator,
                             boolean visible, boolean recentlyAttackedUs) {}

