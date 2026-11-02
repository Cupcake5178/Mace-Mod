package dev.macepilot.core;

import java.util.EnumSet;

/** Tracks ownership of simulated keys, so panic never leaves an owned key pressed. */
public final class MovementController {
    public enum Key { FORWARD, BACK, LEFT, RIGHT, JUMP, SPRINT, ATTACK, USE }
    public interface Sink { void set(Key key, boolean down); }
    private final Sink sink;
    private final EnumSet<Key> owned = EnumSet.noneOf(Key.class);
    public MovementController(Sink sink) { this.sink=sink; }
    public void apply(ActionPlan plan) {
        if(plan.releaseAll()) { release(); return; }
        set(Key.FORWARD,plan.forward()); set(Key.SPRINT,plan.sprint()); set(Key.JUMP,plan.jump());
        // Attacks and item use are discrete game interactions, never held inputs.
        set(Key.BACK,false); set(Key.LEFT,false); set(Key.RIGHT,false);
        set(Key.ATTACK,false); set(Key.USE,false);
    }
    private void set(Key key, boolean down) {
        if(down) { owned.add(key); sink.set(key,true); }
        else if(owned.remove(key)) sink.set(key,false);
    }
    public void release() {
        // Includes action keys even if another code path interrupted a press.
        for(Key key:Key.values()) sink.set(key,false);
        owned.clear();
    }
    public boolean holdingAny() { return !owned.isEmpty(); }
}

