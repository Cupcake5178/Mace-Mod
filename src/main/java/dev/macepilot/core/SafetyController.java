package dev.macepilot.core;

import dev.macepilot.config.Settings;

public final class SafetyController {
    private final Settings settings;
    public SafetyController(Settings settings) { this.settings=settings; }
    public String hardStop(CombatFrame frame) {
        if(!frame.alive()) return "Player died";
        if(frame.screenOpen()) return "Screen opened";
        if(frame.submerged()) return "Water/lava: automation stopped";
        if(!frame.mace()) return "No mace in configured hotbar";
        return null;
    }
    public boolean lowHealth(CombatFrame frame) { return frame.health()<settings.minHealth; }
    public boolean aerialReady(CombatFrame frame) {
        return settings.elytraAutomation && frame.usableElytra() && settings.rocketAssistance
                && frame.rockets()>settings.rocketReserve;
    }
}

