package dev.macepilot.core;

import dev.macepilot.config.Settings;

public final class FireworkController {
    private final Settings settings;
    private long lastUse=Long.MIN_VALUE/2;
    public FireworkController(Settings settings) { this.settings=settings; }
    public long cooldown(long tick) { return Math.max(0,settings.rocketDelay-(tick-lastUse)); }
    public boolean canUse(CombatFrame frame, boolean emergency) {
        return settings.rocketAssistance && frame.gliding() && frame.usableElytra()
                && frame.rockets()>(emergency?0:settings.rocketReserve) && cooldown(frame.tick())==0;
    }
    /** Call after issuing a use request, even on server rejection, to prevent request spam. */
    public void requested(long tick) { lastUse=tick; }
}

