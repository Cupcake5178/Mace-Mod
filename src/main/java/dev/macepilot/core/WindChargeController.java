package dev.macepilot.core;

import dev.macepilot.config.Settings;

public final class WindChargeController {
    private final Settings settings;
    private long lastUse=Long.MIN_VALUE/2;
    public WindChargeController(Settings settings) { this.settings=settings; }
    public boolean canBoost(CombatFrame frame, boolean emergency) {
        return settings.windAssistance && frame.grounded() && !frame.submerged()
                && frame.windCharges()>(emergency?0:settings.windReserve) && frame.tick()-lastUse>=24;
    }
    public void requested(long tick) { lastUse=tick; }
}

