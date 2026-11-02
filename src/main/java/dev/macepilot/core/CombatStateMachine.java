package dev.macepilot.core;

import java.util.function.Consumer;

public final class CombatStateMachine {
    public record Transition(CombatState from, CombatState to, long tick, String reason) {}
    private CombatState state = CombatState.IDLE;
    private long enteredAt;
    private String reason = "Disabled";
    private Consumer<Transition> listener = t -> {};
    public CombatState state() { return state; }
    public String reason() { return reason; }
    public long age(long tick) { return Math.max(0, tick-enteredAt); }
    public void listen(Consumer<Transition> listener) { this.listener = listener; }
    public void transition(CombatState next, long tick, String why) {
        if (next == state) return;
        Transition t = new Transition(state, next, tick, why);
        state = next; enteredAt = tick; reason = why;
        listener.accept(t);
    }
}

