package dev.macepilot.core;

import dev.macepilot.config.*;
import java.nio.file.*;
import java.util.*;

/** Offline regression tests for decisions; these do not claim to simulate Minecraft physics. */
public final class CoreTests {
    private static final UUID SELF = new UUID(0,1), A = new UUID(0,2), B = new UUID(0,3), C = new UUID(0,4);
    private static int passed;
    private static PlayerSnapshot p(UUID id,double x,double y,double hp) {
        return new PlayerSnapshot(id,"Player"+id.getLeastSignificantBits(),new Point3(x,y,0),Point3.ZERO,
                new Point3(-1,0,0),hp,hp>0,false,true,false);
    }
    private static CombatFrame f(long tick,List<PlayerSnapshot> players) {
        return new CombatFrame(tick,"test-world",SELF,new Point3(0,10,0),new Point3(0,-.3,0),20,true,
                false,false,false,false,false,true,true,32,16,1,8,50,A,players);
    }
    private static CombatFrame at(CombatFrame f,Point3 pos,boolean grounded,boolean gliding,double hp) {
        return new CombatFrame(f.tick(),f.world(),f.selfId(),pos,f.velocity(),hp,f.alive(),grounded,gliding,
                f.submerged(),f.screenOpen(),f.corrected(),f.mace(),f.usableElytra(),f.rockets(),f.windCharges(),
                f.attackStrength(),f.fallDistance(),f.pingMillis(),f.crosshairTarget(),f.players());
    }
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
    private static void run(String name,Checked test) throws Exception {
        test.run(); passed++; System.out.println("PASS "+name);
    }
    @FunctionalInterface private interface Checked { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        run("1v1 holds its primary despite a better challenger",()->{
            Settings s=new Settings();s.fightMode=Settings.FightMode.ONE;s.finishCurrentTarget=false;
            ThreatManager tm=new ThreatManager(s,new FriendManager(s));TargetManager t=new TargetManager(s,tm);
            CombatFrame first=f(0,List.of(p(A,4,0,20)));tm.update(first,null);t.select(first);
            CombatFrame later=f(100,List.of(p(A,4,0,20),p(B,0,9,1)));tm.update(later,A);
            check(t.select(later).id().equals(A),"1v1 switched primary");
        });
        run("live 1v1 to 3v1 to Auto changes targeting without restart",()->{
            Settings s=new Settings();s.fightMode=Settings.FightMode.ONE;s.finishCurrentTarget=false;
            s.targetMode=Settings.TargetMode.LOWEST_HEALTH;
            ThreatManager tm=new ThreatManager(s,new FriendManager(s));TargetManager t=new TargetManager(s,tm);
            CombatFrame first=f(0,List.of(p(A,3,0,20)));tm.update(first,null);t.select(first);
            CombatFrame later=f(100,List.of(p(A,3,0,20),p(B,4,0,2),p(C,5,0,10)));
            s.fightMode=Settings.FightMode.THREE;tm.update(later,A);
            check(tm.fightSize()==3,"3v1 not applied");check(t.select(later).id().equals(B),"did not reassess");
            s.fightMode=Settings.FightMode.AUTO;tm.update(f(101,List.of(p(B,3,0,2))),B);
            check(tm.fightSize()==1,"Auto did not shrink");
        });
        run("target switch cooldown and hysteresis",()->{
            Settings s=new Settings();s.finishCurrentTarget=false;s.targetMode=Settings.TargetMode.NEAREST;
            ThreatManager tm=new ThreatManager(s,new FriendManager(s));TargetManager t=new TargetManager(s,tm);
            CombatFrame first=f(0,List.of(p(A,10,10,20)));tm.update(first,null);t.select(first);
            CombatFrame soon=f(10,List.of(p(A,10,10,20),p(B,1,10,20)));tm.update(soon,A);
            check(t.select(soon).id().equals(A),"cooldown ignored");
            CombatFrame close=f(100,List.of(p(A,10,10,20),p(B,9.9,10,20)));tm.update(close,A);
            check(t.select(close).id().equals(A),"hysteresis ignored");
        });
        run("friends, blacklist, self, dead and spectator never selected",()->{
            Settings s=new Settings();s.friends="pLaYeR2";s.blacklist=B.toString();
            ThreatManager tm=new ThreatManager(s,new FriendManager(s));
            PlayerSnapshot spec=new PlayerSnapshot(C,"spectator",Point3.ZERO,Point3.ZERO,Point3.ZERO,20,true,true,true,false);
            tm.update(f(0,List.of(p(A,1,0,20),p(B,2,0,20),p(SELF,0,0,20),p(new UUID(0,8),3,0,0),spec)),null);
            check(tm.threats().isEmpty(),"ineligible target present");
        });
        run("friend list changes immediately invalidate current target",()->{
            Settings s=new Settings();ThreatManager tm=new ThreatManager(s,new FriendManager(s));TargetManager t=new TargetManager(s,tm);
            CombatFrame frame=f(0,List.of(p(A,2,0,20)));tm.update(frame,null);t.select(frame);
            s.friends=A.toString();check(t.loss(frame)==TargetManager.Loss.EXCLUDED,"friend not excluded");
        });
        run("manual lock requires a currently received eligible entity",()->{
            Settings s=new Settings();s.targetMode=Settings.TargetMode.MANUAL_LOCK;
            ThreatManager tm=new ThreatManager(s,new FriendManager(s));TargetManager t=new TargetManager(s,tm);
            CombatFrame frame=f(0,List.of(p(A,2,0,20)));tm.update(frame,null);
            check(t.select(frame)==null,"manual mode auto acquired");check(!t.lock(frame,B),"locked unreceived entity");
            check(t.lock(frame,A),"lock failed");check(t.select(frame).id().equals(A),"lock not retained");
        });
        run("dead target with Continue After Kill off stops immediately",()->{
            Settings s=new Settings();CombatManager m=new CombatManager(s,()->{});
            m.enable(f(0,List.of(p(A,2,0,20))));m.tick(f(1,List.of(p(A,2,0,20))));
            ActionPlan action=m.tick(f(2,List.of(p(A,2,0,0),p(B,3,0,20))));
            check(!m.enabled() && action.releaseAll() && action.attackTarget()==null,"did not stop on death");
        });
        run("continuous combat A dies -> B -> no remaining opponents -> idle",()->{
            Settings s=new Settings();s.fightMode=Settings.FightMode.THREE;s.continueAfterKill=true;
            CombatManager m=new CombatManager(s,()->{});
            m.enable(f(0,List.of(p(A,1,0,20),p(B,20,0,20))));m.tick(f(1,List.of(p(A,1,0,20),p(B,20,0,20))));
            check(m.targets.primary().equals(A),"wrong first target");
            ActionPlan death=m.tick(f(2,List.of(p(A,1,0,0),p(B,20,0,20))));
            check(death.attackTarget()==null && m.enabled() && m.targets.primary()==null,"attacked dead A");
            m.tick(f(3,List.of(p(A,1,0,0),p(B,20,0,20))));check(m.targets.primary().equals(B),"B not acquired");
            m.tick(f(4,List.of(p(B,20,0,0))));m.tick(f(5,List.of(p(B,20,0,0))));
            check(!m.enabled() && m.machine.state()==CombatState.IDLE,"did not idle after last death");
        });
        run("disappearance differs from death and stale memory cannot target",()->{
            Settings s=new Settings();s.continueAfterKill=true;CombatManager m=new CombatManager(s,()->{});
            m.enable(f(0,List.of(p(A,1,0,20))));m.tick(f(1,List.of(p(A,1,0,20))));
            m.tick(f(2,List.of()));check(!m.enabled(),"continued tracking missing player");
            check(m.notice().contains("DISAPPEARED"),"misreported death");
        });
        run("panic releases every simulated key in every combat state",()->{
            for(CombatState state:CombatState.values()) {
                EnumMap<MovementController.Key,Boolean> pressed=new EnumMap<>(MovementController.Key.class);
                MovementController movement=new MovementController(pressed::put);
                CombatManager m=new CombatManager(new Settings(),movement::release);
                m.enable(f(0,List.of(p(A,2,0,20))));
                movement.apply(new ActionPlan(null,true,true,true,false,false,ActionPlan.Item.NONE,false,null,false,""));
                m.machine.transition(state,1,"Test panic in state");m.panic(2);
                check(!m.enabled() && !movement.holdingAny(),"panic remained active in "+state);
                check(pressed.size()==8 && pressed.values().stream().noneMatch(Boolean::booleanValue),"stuck key in "+state);
            }
        });
        run("null player, disconnect, and dimension changes release input",()->{
            int[] releases={0};CombatManager m=new CombatManager(new Settings(),()->releases[0]++);
            m.enable(f(0,List.of(p(A,2,0,20))));m.tick(null);check(!m.enabled(),"null not stopped");
            m.enable(f(0,List.of(p(A,2,0,20))));m.disconnected(1);check(!m.enabled(),"disconnect not stopped");
            m.enable(f(0,List.of(p(A,2,0,20))));CombatFrame x=f(2,List.of(p(A,2,0,20)));
            CombatFrame other=new CombatFrame(x.tick(),"other",x.selfId(),x.position(),x.velocity(),20,true,false,false,false,false,false,true,true,32,16,1,8,50,A,x.players());
            m.tick(other);check(!m.enabled() && releases[0]>=6,"dimension not stopped");
        });
        run("outnumbered progresses into evasion rather than attacking cluster",()->{
            Settings s=new Settings();CombatManager m=new CombatManager(s,()->{});
            CombatFrame frame=at(f(0,List.of(p(A,1,0,20),p(B,2,0,20),p(C,3,0,20))),new Point3(0,0,0),true,false,20);
            m.enable(frame);m.tick(frame);check(m.machine.state()==CombatState.OUTNUMBERED,"no outnumbered state");
            CombatFrame next=at(f(1,frame.players()),frame.position(),true,false,20);
            ActionPlan escape=m.tick(next);
            check(m.machine.state()==CombatState.EVADING && escape.attackTarget()==null,"did not evade");
        });
        run("low health preempts an in-range strike",()->{
            Settings s=new Settings();CombatManager m=new CombatManager(s,()->{});
            m.enable(f(0,List.of(p(A,0,8,20))));m.tick(f(1,List.of(p(A,0,8,20))));
            m.machine.transition(CombatState.DIVING,1,"fixture");
            ActionPlan plan=m.tick(at(f(2,List.of(p(A,0,8,20))),new Point3(0,10,0),false,true,3));
            check(plan.attackTarget()==null && m.machine.state()==CombatState.DISENGAGING,"low health strike");
        });
        run("rocket delay, reserve and gliding gating",()->{
            Settings s=new Settings();FireworkController rockets=new FireworkController(s);
            CombatFrame plain=f(0,List.of());check(!rockets.canUse(plain,false),"ground/non-glide rocket");
            CombatFrame flying=at(plain,plain.position(),false,true,20);check(rockets.canUse(flying,false),"rocket blocked");
            rockets.requested(0);check(!rockets.canUse(flying,false),"rocket spam");
            check(rockets.canUse(at(f(40,List.of()),plain.position(),false,true,20),false),"cooldown did not expire");
            s.rocketReserve=32;check(!rockets.canUse(at(f(80,List.of()),plain.position(),false,true,20),false),"spent reserve");
        });
        run("wind charges need ground and respect their request cooldown",()->{
            Settings s=new Settings();WindChargeController w=new WindChargeController(s);
            CombatFrame x=f(0,List.of());check(!w.canBoost(x,false),"airborne blind boost");
            x=at(x,Point3.ZERO,true,false,20);check(w.canBoost(x,false),"ground boost blocked");
            w.requested(0);check(!w.canBoost(x,false),"wind spam");
        });
        run("mace strikes require falling, cooldown and non-gliding state",()->{
            Settings s=new Settings();MaceAttackController mace=new MaceAttackController(s);
            CombatFrame x=f(0,List.of());PlayerSnapshot target=p(A,0,8,20);
            check(mace.ready(x,target),"valid falling strike blocked");
            check(!mace.ready(at(x,x.position(),false,true,20),target),"gliding smash allowed");
            check(!mace.ready(x,p(A,0,8,0)),"dead target attacked");
            mace.requested(0);check(!mace.ready(x,target),"attack spam");
        });
        run("aim wraps yaw and limits combined per-tick rotation",()->{
            Settings s=new Settings();AimController aim=new AimController(s);
            AimController.Rotation r=aim.toward(Point3.ZERO,new Point3(0,100,100),179,80);
            check(Math.hypot(AimController.wrap(r.yaw()-179),r.pitch()-80)<=s.maxRotation+1e-6,"rotation limit");
            check(r.pitch()>=-90 && r.pitch()<=90,"pitch limit");
            check(Math.abs(AimController.wrap(-358)-2)<1e-6,"yaw wrap");
        });
        run("intercept prediction bounds extrapolation",()->{
            Settings s=new Settings();AimController aim=new AimController(s);
            PlayerSnapshot target=new PlayerSnapshot(A,"A",Point3.ZERO,new Point3(40,0,0),Point3.ZERO,20,true,false,true,false);
            check(aim.intercept(f(0,List.of()),target).horizontal()<=4.00001,"unbounded extrapolation");
            s.prediction=false;check(aim.intercept(f(0,List.of()),target).equals(Point3.ZERO),"prediction setting ignored");
        });
        run("threat cap bounds clustering and stale history expires",()->{
            Settings s=new Settings();s.maxThreats=3;ThreatManager tm=new ThreatManager(s,new FriendManager(s));
            List<PlayerSnapshot> many=new ArrayList<>();for(int i=10;i<50;i++)many.add(p(new UUID(0,i),i*.1,0,20));
            tm.update(f(0,many),null);check(tm.threats().size()==3,"cap not applied");
            tm.update(f(1,List.of()),null);check(tm.threats().isEmpty() && !tm.memory().isEmpty(),"memory mistaken for targets");
            tm.update(f(102,List.of()),null);check(tm.memory().isEmpty(),"memory leak");
        });
        run("config survives save/reload and preserves lists",()->{
            Path dir=Files.createTempDirectory("macepilot-test-");Path path=dir.resolve("macepilot.properties");
            try {
                ConfigManager cfg=new ConfigManager(path);cfg.settings().fightMode=Settings.FightMode.THREE;
                cfg.settings().continueAfterKill=true;cfg.settings().friends="Alice,"+A;cfg.save();
                ConfigManager reload=new ConfigManager(path);reload.load();
                check(reload.settings().fightMode==Settings.FightMode.THREE && reload.settings().continueAfterKill,"settings lost");
                check(reload.settings().friends.equals("Alice,"+A),"friend list lost");
                reload.reset("Flight");check(reload.settings().fightMode==Settings.FightMode.THREE,"category reset leaked");
                reload.reset(null);check(!reload.settings().continueAfterKill,"reset all failed");
            } finally { Files.deleteIfExists(path);Files.deleteIfExists(dir); }
        });
        run("corrupt config values are normalized without unsafe ranges",()->{
            Path path=Files.createTempFile("macepilot-corrupt-",".properties");
            try {
                Files.writeString(path,"attackRange=999\naimSpeed=NaN\nfightMode=INVALID\nmaxThreats=-10\nrocketDelay=garbage\n");
                ConfigManager cfg=new ConfigManager(path);cfg.load();Settings s=cfg.settings();
                check(s.attackRange==3 && Double.isFinite(s.aimSpeed) && s.maxThreats==1,"unsafe config");
                check(s.fightMode==Settings.FightMode.AUTO && s.rocketDelay==35,"invalid defaults");
            } finally { Files.deleteIfExists(path); }
        });
        run("hotbar detection settles, resists flicker and restores original slot",()->{
            Settings s=new Settings();int[] selected={7};
            InventoryController inventory=new InventoryController(s,new InventoryController.Selection() {
                public int selected() { return selected[0]; }
                public void select(int slot) { selected[0]=slot; }
            });
            inventory.update(List.of(new InventoryController.Stack(0,InventoryController.Kind.MACE,1,500),
                    new InventoryController.Stack(1,InventoryController.Kind.ROCKET,12,0),
                    new InventoryController.Stack(12,InventoryController.Kind.WIND_CHARGE,24,0)));
            check(!inventory.equip(InventoryController.Kind.MACE,0) && selected[0]==0,"selection did not settle");
            check(inventory.equip(InventoryController.Kind.MACE,1),"mace never settled");
            check(!inventory.equip(InventoryController.Kind.ROCKET,1) && selected[0]==0,"rapid slot flicker");
            check(!inventory.equip(InventoryController.Kind.ROCKET,3) && selected[0]==1,"rocket switch failed");
            check(inventory.equip(InventoryController.Kind.ROCKET,4),"rocket not usable after settle");
            check(inventory.slot(InventoryController.Kind.WIND_CHARGE)==-1 && inventory.count(InventoryController.Kind.WIND_CHARGE)==24,"inventory vs hotbar confusion");
            inventory.release();check(selected[0]==7,"original slot not restored");
        });
        run("elytra requests create spaced rising edges and release the jump key",()->{
            ElytraController e=new ElytraController();CombatFrame frame=f(0,List.of());
            check(e.jumpEdge(frame,true,false),"no deploy edge");
            check(!e.jumpEdge(f(1,List.of()),true,false),"jump never released");
            check(!e.jumpEdge(f(2,List.of()),true,false),"deploy spam");
            check(e.jumpEdge(f(6,List.of()),true,false),"retry did not occur");
            e.reset();check(!e.jumpEdge(at(frame,Point3.ZERO,true,false,20),true,false),"deploy from ground");
        });
        System.out.println("Core regression suite: "+passed+" passed, 0 failed");
    }
}
