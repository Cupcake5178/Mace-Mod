package dev.macepilot.config;

import java.util.Locale;

public final class Settings {
    public enum FightMode {
        ONE(1, "1v1"), TWO(2, "2v1"), THREE(3, "3v1"), FOUR(4, "4v1"), FIVE(5, "5v1"), AUTO(16, "Auto");
        public final int limit;
        private final String label;
        FightMode(int limit, String label) { this.limit = limit; this.label = label; }
        @Override public String toString() { return label; }
    }
    public enum TargetMode { NEAREST, CROSSHAIR, LOWEST_HEALTH, MANUAL_LOCK, SMART }
    public enum Priority { CURRENT_TARGET, LOWEST_HEALTH, CLOSEST, HIGHEST_THREAT, ISOLATED, SMART }

    @Option(category="Combat", label="Fight Mode") public FightMode fightMode = FightMode.AUTO;
    @Option(category="Combat", label="Target Mode") public TargetMode targetMode = TargetMode.SMART;
    @Option(category="Combat", label="Target Priority") public Priority priority = Priority.SMART;
    @Option(category="Combat", label="Finish Current Target") public boolean finishCurrentTarget = true;
    @Option(category="Combat", label="Continue After Kill") public boolean continueAfterKill = false;
    @Option(category="Combat", label="Attack Range", min=1, max=3, step=.1, tip="Also limited by the actual game interaction range and line of sight.") public double attackRange = 3;
    @Option(category="Combat", label="Chase Range", min=8, max=128) public double chaseRange = 64;
    @Option(category="Combat", label="Target Switch Cooldown (ticks)", min=10, max=200) public int switchCooldown = 40;
    @Option(category="Combat", label="Aim Speed", min=.05, max=1, step=.05) public double aimSpeed = .35;
    @Option(category="Combat", label="Horizontal Turn Speed", min=1, max=90) public double horizontalTurn = 18;
    @Option(category="Combat", label="Vertical Turn Speed", min=1, max=90) public double verticalTurn = 12;
    @Option(category="Combat", label="Maximum Rotation Per Tick", min=1, max=90) public double maxRotation = 24;
    @Option(category="Combat", label="Prediction") public boolean prediction = true;
    @Option(category="Combat", label="Mace Automation") public boolean maceAutomation = true;
    @Option(category="Combat", label="Minimum Smash Height", min=2, max=30) public double minSmashHeight = 6;
    @Option(category="Combat", label="Maximum Smash Height", min=3, max=50) public double maxSmashHeight = 18;
    @Option(category="Combat", label="Maximum Nearby Enemies For Dive", min=0, max=4, tip="Counts secondary enemies, excluding the primary. 4 = Unlimited.") public int maxDiveEnemies = 1;

    @Option(category="Flight", label="Elytra Automation") public boolean elytraAutomation = true;
    @Option(category="Flight", label="Rocket Assistance") public boolean rocketAssistance = true;
    @Option(category="Flight", label="Rocket Delay (ticks)", min=10, max=200) public int rocketDelay = 35;
    @Option(category="Flight", label="Minimum Rocket Reserve", max=64) public int rocketReserve = 3;
    @Option(category="Flight", label="Maximum Combat Altitude", min=8, max=128, tip="Height above the tracked target; never an absolute world Y limit.") public double maxCombatAltitude = 48;
    @Option(category="Flight", label="Recovery Flight") public boolean recoveryFlight = true;
    @Option(category="Flight", label="Re-engage After Recovery") public boolean reengage = true;

    @Option(category="Wind Charge", label="Wind Charge Assistance") public boolean windAssistance = true;
    @Option(category="Wind Charge", label="Height Boost") public boolean windHeight = true;
    @Option(category="Wind Charge", label="Recovery Boost") public boolean windRecovery = true;
    @Option(category="Wind Charge", label="Reposition Boost") public boolean windReposition = true;
    @Option(category="Wind Charge", label="Minimum Reserve", max=64) public int windReserve = 2;

    @Option(category="Multi-Target", label="Danger Radius", min=2, max=24) public double dangerRadius = 8;
    @Option(category="Multi-Target", label="Threat Range", min=8, max=128) public double threatRange = 48;
    @Option(category="Multi-Target", label="Prefer Isolated Targets") public boolean preferIsolated = true;
    @Option(category="Multi-Target", label="Avoid Enemy Clusters") public boolean avoidClusters = true;
    @Option(category="Multi-Target", label="Show Secondary Threats") public boolean showSecondary = true;
    @Option(category="Multi-Target", label="Maximum Active Threats", min=1, max=16) public int maxThreats = 8;

    @Option(category="Safety", label="Minimum Health (HP)", min=1, max=20) public double minHealth = 8;
    @Option(category="Safety", label="Emergency Disengage") public boolean emergencyDisengage = true;
    @Option(category="Safety", label="Auto Escape") public boolean autoEscape = true;
    @Option(category="Safety", label="Maximum Chase Time (seconds)", min=5, max=300) public int maxChaseSeconds = 90;
    @Option(category="Safety", label="Stop When Target Disappears") public boolean stopOnDisappear = true;

    @Option(category="Inventory", label="Automatic Hotbar Detection") public boolean autoSlots = true;
    @Option(category="Inventory", label="Mace Slot", min=1, max=9) public int maceSlot = 1;
    @Option(category="Inventory", label="Rocket Slot", min=1, max=9) public int rocketSlot = 2;
    @Option(category="Inventory", label="Wind Charge Slot", min=1, max=9) public int windSlot = 3;

    @Option(category="Interface", label="Combat HUD") public boolean combatHud = true;
    @Option(category="Interface", label="Target HUD") public boolean targetHud = true;
    @Option(category="Interface", label="Threat Indicators") public boolean threatIndicators = true;
    @Option(category="Interface", label="Notifications") public boolean notifications = true;
    @Option(category="Interface", label="HUD X", max=2000) public int hudX = 12;
    @Option(category="Interface", label="HUD Y", max=1200) public int hudY = 36;
    @Option(category="Interface", label="HUD Scale", min=.5, max=2, step=.1) public double hudScale = 1;
    @Option(category="Interface", label="Animation Speed", min=.05, max=1, step=.05) public double animationSpeed = .2;
    @Option(category="Interface", label="Debug HUD") public boolean debug = false;

    public String friends = "";
    public String blacklist = "";

    public void normalize() {
        Settings defaults = new Settings();
        for (var f : Settings.class.getFields()) {
            Option o = f.getAnnotation(Option.class);
            try {
                if (f.getType().isEnum() && f.get(this) == null) f.set(this, f.get(defaults));
                if (o != null && (f.getType() == double.class || f.getType() == int.class)) {
                    double n = ((Number) f.get(this)).doubleValue();
                    if (!Double.isFinite(n)) n = ((Number) f.get(defaults)).doubleValue();
                    n = Math.clamp(n, o.min(), o.max());
                    if (f.getType() == int.class) f.setInt(this, (int)Math.round(n)); else f.setDouble(this, n);
                }
            } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
        }
        maxSmashHeight = Math.max(minSmashHeight, maxSmashHeight);
        maxCombatAltitude = Math.max(maxSmashHeight, maxCombatAltitude);
        threatRange = Math.min(threatRange, chaseRange);
        friends = friends == null ? "" : friends;
        blacklist = blacklist == null ? "" : blacklist;
    }

    public static String label(Object value) {
        if (value instanceof FightMode) return value.toString();
        String s = value.toString().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}

