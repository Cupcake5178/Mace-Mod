package dev.macepilot.core;

import java.util.UUID;

/** One tick's requested ordinary inputs. The game adapter must still validate reach/LOS/items. */
public record ActionPlan(Point3 lookAt, boolean forward, boolean sprint, boolean jump,
                         boolean deployElytra, boolean stopGliding, Item useItem,
                         boolean equipMace, UUID attackTarget, boolean releaseAll, String notice) {
    public enum Item { NONE, ROCKET, WIND_CHARGE }
    public static ActionPlan released(String notice) {
        return new ActionPlan(null,false,false,false,false,false,Item.NONE,false,null,true,notice);
    }
    public static ActionPlan flight(Point3 look, boolean deploy, Item item, String notice) {
        return new ActionPlan(look,true,true,false,deploy,false,item,false,null,false,notice);
    }
}

