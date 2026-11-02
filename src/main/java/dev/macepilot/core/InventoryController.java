package dev.macepilot.core;

import dev.macepilot.config.Settings;
import java.util.*;

/** Hotbar decisions only. Never fabricates an item or moves server inventory without a game interaction. */
public final class InventoryController {
    public enum Kind { MACE, ROCKET, WIND_CHARGE, FOOD, TOTEM, SECONDARY_WEAPON, ELYTRA, OTHER }
    public record Stack(int slot, Kind kind, int count, int remainingDurability) {
        public Stack { if(slot<0 || slot>35 || count<0) throw new IllegalArgumentException("Invalid inventory snapshot"); }
    }
    public interface Selection { int selected(); void select(int slot); }
    private final Settings settings;
    private final Selection selection;
    private List<Stack> contents = List.of();
    private int originalSlot=-1;
    private long switchedAt=Long.MIN_VALUE/2;
    private boolean owned;
    public InventoryController(Settings settings,Selection selection) { this.settings=settings;this.selection=selection; }
    public void update(List<Stack> contents) { this.contents=List.copyOf(contents); }
    public int count(Kind kind) { return contents.stream().filter(s->s.kind()==kind).mapToInt(Stack::count).sum(); }
    public int hotbarCount(Kind kind) { return contents.stream().filter(s->s.kind()==kind && s.slot()<9).mapToInt(Stack::count).sum(); }
    public int slot(Kind kind) {
        int preferred=switch(kind) { case MACE->settings.maceSlot-1;case ROCKET->settings.rocketSlot-1;case WIND_CHARGE->settings.windSlot-1;default->-1; };
        for(Stack s:contents) if(s.slot()==preferred && s.kind()==kind && s.count()>0) return preferred;
        if(!settings.autoSlots) return -1;
        return contents.stream().filter(s->s.slot()<9 && s.kind()==kind && s.count()>0)
                .min(Comparator.comparingInt(Stack::slot)).map(Stack::slot).orElse(-1);
    }
    /** Return true only when the item has settled in the selected slot for a tick. */
    public boolean equip(Kind kind,long tick) {
        int slot=slot(kind);
        if(slot<0) return false;
        if(!owned) { originalSlot=selection.selected();owned=true; }
        if(selection.selected()==slot) return tick>switchedAt;
        if(tick-switchedAt<3) return false;
        selection.select(slot);switchedAt=tick;return false;
    }
    public void release() {
        if(owned && originalSlot>=0 && originalSlot<9) selection.select(originalSlot);
        owned=false;originalSlot=-1;switchedAt=Long.MIN_VALUE/2;
    }
}

