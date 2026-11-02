package dev.macepilot.core;

/** Generates vanilla jump-key edges for starting/stopping flight, not custom movement packets. */
public final class ElytraController {
    private long lastEdge=Long.MIN_VALUE/2;
    private boolean pressedLastTick;
    public boolean jumpEdge(CombatFrame frame,boolean requestDeploy,boolean requestStop) {
        if(pressedLastTick) { pressedLastTick=false;return false; }
        if(!frame.usableElytra() || frame.grounded() || frame.submerged()) return false;
        boolean deploy=requestDeploy && !frame.gliding() && frame.velocity().y()<0;
        boolean stop=requestStop && frame.gliding();
        if((deploy || stop) && frame.tick()-lastEdge>=6) {
            lastEdge=frame.tick();pressedLastTick=true;return true;
        }
        return false;
    }
    public void reset() { pressedLastTick=false;lastEdge=Long.MIN_VALUE/2; }
}

