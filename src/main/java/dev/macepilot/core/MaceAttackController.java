package dev.macepilot.core;

import dev.macepilot.config.Settings;

public final class MaceAttackController {
    private final Settings settings;
    private long lastAttack=Long.MIN_VALUE/2;
    public MaceAttackController(Settings settings) { this.settings=settings; }
    public boolean ready(CombatFrame frame, PlayerSnapshot target) {
        return settings.maceAutomation && target.alive() && target.visible() && frame.mace()
                && !frame.gliding() && frame.velocity().y()<0 && frame.fallDistance()>1.5
                && frame.attackStrength()>=.95 && frame.tick()-lastAttack>=10
                && frame.position().distance(target.position())<=settings.attackRange;
    }
    public void requested(long tick) { lastAttack=tick; }
}

