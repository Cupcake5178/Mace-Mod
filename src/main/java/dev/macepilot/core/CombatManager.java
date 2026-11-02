package dev.macepilot.core;

import dev.macepilot.config.Settings;
import java.util.Objects;

/** Decision engine. It never mutates world physics, sends packets, or infers unreceived entities. */
public final class CombatManager {
    private final Settings settings;
    public final CombatStateMachine machine = new CombatStateMachine();
    public final ThreatManager threats;
    public final TargetManager targets;
    public final AimController aim;
    public final FireworkController fireworks;
    public final WindChargeController wind;
    public final SafetyController safety;
    public final MaceAttackController mace;
    public final RecoveryController recovery = new RecoveryController();
    private final Runnable releaseInputs;
    private boolean enabled;
    private String world;
    private long enabledAt, targetSince, escapeSince = -1;
    private java.util.UUID timedTarget;
    private String notice = "Disabled";
    private RecoveryController.Outcome lastOutcome;

    public CombatManager(Settings settings, Runnable releaseInputs) {
        this.settings=settings; this.releaseInputs=releaseInputs;
        threats=new ThreatManager(settings,new FriendManager(settings));
        targets=new TargetManager(settings,threats);
        aim=new AimController(settings); fireworks=new FireworkController(settings);
        wind=new WindChargeController(settings); safety=new SafetyController(settings);
        mace=new MaceAttackController(settings);
    }
    public boolean enabled() { return enabled; }
    public String notice() { return notice; }
    public RecoveryController.Outcome lastOutcome() { return lastOutcome; }
    public void enable(CombatFrame frame) {
        disable(frame==null?0:frame.tick(),"Reset");
        if(frame==null) { notice="Join a world first"; return; }
        String stop=safety.hardStop(frame);
        if(stop!=null) { notice=stop; return; }
        enabled=true; world=frame.world(); enabledAt=frame.tick(); targetSince=frame.tick();
        machine.transition(CombatState.SEARCHING,frame.tick(),"Combat enabled");
        notice="Combat enabled";
    }
    public void disable(long tick,String reason) {
        // Release synchronously: panic need not wait for the next decision tick.
        enabled=false; releaseInputs.run(); targets.clear(); threats.clear();
        world=null; timedTarget=null; escapeSince=-1;
        machine.transition(CombatState.IDLE,tick,reason); notice=reason;
    }
    public void panic(long tick) { disable(tick,"Panic: control returned"); }
    public void disconnected(long tick) { disable(tick,"Disconnected / world changed"); }

    public ActionPlan tick(CombatFrame frame) {
        if(!enabled) return ActionPlan.released(notice);
        if(frame==null) return stop(0,"No world/player");
        if(!Objects.equals(world,frame.world())) return stop(frame.tick(),"World changed");
        settings.normalize();
        String failure=safety.hardStop(frame);
        if(failure!=null) return stop(frame.tick(),failure);
        if(frame.corrected()) return stop(frame.tick(),"Server correction: control returned");

        threats.update(frame,targets.primary());
        // Survival preempts attacks and sticky target preferences.
        if(safety.lowHealth(frame)) {
            if(!settings.emergencyDisengage || !settings.autoEscape) return stop(frame.tick(),"Low health: stopped");
            if(escapeSince<0) escapeSince=frame.tick();
            if(frame.tick()-escapeSince>=40 || !safety.aerialReady(frame) && !wind.canBoost(frame,true))
                return stop(frame.tick(),"Low health: disengaged");
            transition(frame,CombatState.DISENGAGING,"Low health: escaping");
            return escape(frame,true);
        }
        // An emergency ends with automation disabled, even if health regenerates during escape.
        if(escapeSince>=0) return stop(frame.tick(),"Emergency escape complete");

        TargetManager.Loss loss=targets.loss(frame);
        if(loss!=TargetManager.Loss.NONE) {
            targets.forgetPrimary();
            if(loss==TargetManager.Loss.DEAD) {
                transition(frame,CombatState.FINISHING,"Target defeated");
                notice="Target defeated";
                if(!settings.continueAfterKill) return stop(frame.tick(),"Target defeated: combat disabled");
                return ActionPlan.released(notice);
            }
            if(settings.stopOnDisappear) return stop(frame.tick(),"Target lost: "+loss);
            transition(frame,CombatState.RETARGETING,"Target invalid: "+loss);
        }
        if(machine.state()==CombatState.FINISHING) transition(frame,CombatState.RETARGETING,"Reassessing remaining targets");
        PlayerSnapshot target=targets.select(frame);
        if(target==null) {
            if(machine.state()==CombatState.RETARGETING || frame.tick()-enabledAt>100)
                return stop(frame.tick(),"No valid targets");
            transition(frame,CombatState.SEARCHING,"Waiting for eligible visible target");
            return ActionPlan.released("Searching");
        }
        if(!target.id().equals(timedTarget)) {
            timedTarget=target.id(); targetSince=frame.tick(); notice="Target acquired: "+target.name();
            transition(frame,CombatState.TRACKING,notice);
        }
        if(frame.tick()-targetSince>settings.maxChaseSeconds*20L) return stop(frame.tick(),"Maximum chase time reached");
        if(!settings.maceAutomation) return stop(frame.tick(),"Mace automation disabled");

        // Never commit to an aerial plan with no usable recovery equipment.
        if(!safety.aerialReady(frame)) return stop(frame.tick(),frame.usableElytra()?"No usable rocket reserve":"No usable elytra");
        double height=frame.position().y()-target.position().y();
        double horizontal=frame.position().subtract(target.position()).horizontal();
        Point3 intercept=aim.intercept(frame,target);
        int cluster=threats.enemiesNear(intercept,target.id());
        boolean unsafeDive=settings.avoidClusters && settings.maxDiveEnemies<4 && cluster>settings.maxDiveEnemies;

        if(threats.danger()>=2 && height<settings.minSmashHeight) {
            if(machine.state()!=CombatState.OUTNUMBERED && machine.state()!=CombatState.EVADING && machine.state()!=CombatState.SEPARATING) {
                transition(frame,CombatState.OUTNUMBERED,"Several nearby threats");
                return ActionPlan.released("Outnumbered: preparing escape");
            }
        }
        if(machine.state()==CombatState.OUTNUMBERED) {
            transition(frame,CombatState.EVADING,"Create distance before attacking");
            return escape(frame,false);
        }
        if(machine.state()==CombatState.EVADING || machine.state()==CombatState.SEPARATING) {
            if(threats.danger()<2 && height>=settings.minSmashHeight) {
                transition(frame,CombatState.RETARGETING,"Separated from danger");
                return ActionPlan.released("Reassessing");
            }
            if(machine.age(frame.tick())>100) return stop(frame.tick(),"Unable to separate safely");
            if(machine.state()==CombatState.EVADING && machine.age(frame.tick())>10)
                transition(frame,CombatState.SEPARATING,"Drawing threats apart");
            return escape(frame,false);
        }
        switch(machine.state()) {
            case SEARCHING, TRACKING, RETARGETING -> {
                transition(frame,CombatState.PREPARING,"Equipment and target validated");
                return ActionPlan.released("Preparing");
            }
            case PREPARING, GAINING_HEIGHT, CHASING, REPOSITIONING -> {
                if(height>settings.maxCombatAltitude) {
                    transition(frame,CombatState.RECOVERING,"Above maximum relative altitude");
                    return ActionPlan.flight(intercept,false,ActionPlan.Item.NONE,"Altitude cap");
                }
                if(height>=settings.minSmashHeight && horizontal<Math.max(3,height*.45)) {
                    transition(frame,CombatState.ALIGNING,"Above target at useful height");
                    return ActionPlan.flight(intercept,false,ActionPlan.Item.NONE,"Aligning");
                }
                if(machine.age(frame.tick())>160) return stop(frame.tick(),"Unable to gain height/position safely");
                transition(frame,horizontal>24?CombatState.CHASING:CombatState.GAINING_HEIGHT,"Need altitude or horizontal alignment");
                if(frame.grounded()) return launch(frame,false);
                Point3 direction=intercept.subtract(frame.position());
                Point3 climb=frame.position().add(new Point3(direction.x(),Math.max(12,direction.horizontal()),direction.z()));
                if(height>=settings.maxSmashHeight) climb=new Point3(intercept.x(),frame.position().y()+1,intercept.z());
                return flight(frame,climb,false,height<settings.maxSmashHeight);
            }
            case ALIGNING -> {
                if(unsafeDive) {
                    transition(frame,CombatState.REPOSITIONING,"Predicted landing is clustered");
                    return escape(frame,false);
                }
                if(height<settings.minSmashHeight) {
                    transition(frame,CombatState.GAINING_HEIGHT,"Too low to begin smash");
                    return flight(frame,intercept.add(new Point3(0,settings.maxSmashHeight,0)),false,true);
                }
                if(horizontal>Math.max(3,height*.45)) {
                    transition(frame,CombatState.REPOSITIONING,"Target moved outside dive corridor");
                    return ActionPlan.flight(intercept,false,ActionPlan.Item.NONE,"Repositioning");
                }
                transition(frame,CombatState.DIVING,"Safe predicted corridor");
                return dive(frame,target,intercept);
            }
            case DIVING -> {
                if(unsafeDive || !target.visible() || horizontal>Math.max(5,height*.8) || frame.grounded() || height<0) {
                    lastOutcome=recovery.evaluate(frame,target,threats.danger(),false);
                    transition(frame,CombatState.RECOVERING,"Dive invalid: "+lastOutcome);
                    return flight(frame,intercept.add(new Point3(0,12,0)),false,true);
                }
                if(mace.ready(frame,target)) {
                    mace.requested(frame.tick());
                    transition(frame,CombatState.ATTACKING,"Mace ready and target inside range");
                    return new ActionPlan(intercept.add(new Point3(0,.8,0)),false,false,false,false,false,
                            ActionPlan.Item.NONE,true,target.id(),false,"Strike requested");
                }
                if(machine.age(frame.tick())>80) transition(frame,CombatState.RECOVERING,"Dive timeout");
                return dive(frame,target,intercept);
            }
            case ATTACKING -> {
                lastOutcome=recovery.evaluate(frame,target,threats.danger(),true);
                transition(frame,CombatState.RECOVERING,"Attempt completed; recover before next attack");
                return flight(frame,intercept.add(new Point3(0,14,0)),false,true);
            }
            case RECOVERING -> {
                if(!settings.recoveryFlight) return stop(frame.tick(),"Recovery flight disabled");
                if(!settings.reengage) return stop(frame.tick(),"Recovery: manual control requested");
                if(frame.grounded()) return launch(frame,true);
                if(frame.gliding() && frame.velocity().y()>0 && height>=settings.minSmashHeight) {
                    transition(frame,CombatState.REPOSITIONING,"Recovered flight and altitude");
                    return ActionPlan.flight(intercept,false,ActionPlan.Item.NONE,"Repositioning");
                }
                if(machine.age(frame.tick())>120) return stop(frame.tick(),"Recovery timeout");
                return flight(frame,intercept.add(new Point3(0,18,0)),false,true);
            }
            default -> { return ActionPlan.released(notice); }
        }
    }

    private ActionPlan dive(CombatFrame frame,PlayerSnapshot target,Point3 intercept) {
        // Falling, not gliding, is required for a mace smash. Adapter must use vanilla jump edges.
        return new ActionPlan(intercept.add(new Point3(0,.8,0)),true,false,false,false,frame.gliding(),
                ActionPlan.Item.NONE,true,null,false,"Diving");
    }
    private ActionPlan launch(CombatFrame frame,boolean recovering) {
        if((recovering?settings.windRecovery:settings.windHeight) && wind.canBoost(frame,false)) {
            wind.requested(frame.tick());
            return new ActionPlan(frame.position().add(new Point3(0,-4,0)),false,false,true,false,false,
                    ActionPlan.Item.WIND_CHARGE,false,null,false,"Wind launch");
        }
        return new ActionPlan(frame.position().add(new Point3(0,10,6)),true,true,true,false,false,
                ActionPlan.Item.NONE,false,null,false,"Jump to deploy elytra");
    }
    private ActionPlan escape(CombatFrame frame,boolean emergency) {
        Point3 away=threats.escapeDirection();
        if(away.horizontal()<.1) away=new Point3(0,0,1);
        if(frame.grounded()) {
            if(settings.windReposition && wind.canBoost(frame,emergency)) {
                wind.requested(frame.tick());
                return new ActionPlan(frame.position().add(new Point3(0,-4,0)),false,false,true,false,false,
                        ActionPlan.Item.WIND_CHARGE,false,null,false,"Wind escape");
            }
            return new ActionPlan(frame.position().add(away.scale(12)).add(new Point3(0,10,0)),true,true,true,
                    false,false,ActionPlan.Item.NONE,false,null,false,"Escape launch");
        }
        return flight(frame,frame.position().add(away.scale(16)).add(new Point3(0,14,0)),emergency,true);
    }
    private ActionPlan flight(CombatFrame frame,Point3 look,boolean emergency,boolean boost) {
        ActionPlan.Item item=ActionPlan.Item.NONE;
        if(boost && fireworks.canUse(frame,emergency)) { item=ActionPlan.Item.ROCKET; fireworks.requested(frame.tick()); }
        boolean deploy=settings.elytraAutomation && !frame.gliding() && !frame.grounded() && frame.velocity().y()<0;
        return ActionPlan.flight(look,deploy,item,notice);
    }
    private void transition(CombatFrame frame,CombatState next,String reason) { machine.transition(next,frame.tick(),reason); }
    private ActionPlan stop(long tick,String reason) { disable(tick,reason); return ActionPlan.released(reason); }
}

