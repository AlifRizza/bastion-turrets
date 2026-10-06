package dev.bastion.client.anim;

import dev.bastion.Bastion;
import dev.bastion.weapon.WeaponModuleItem;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.util.GeckoLibUtil;
import software.bernie.geckolib.util.RenderUtils;

import java.util.function.Supplier;

/**
 * Client-side animation state of the weapon mounted on one turret (PLAN 6.1). Two controllers: "main"
 * plays deploy, then the pose of the turret state (idle, aim, no_ammo, overheat); "action" plays one-shot
 * clips (fire, pump, cooldown_vent) on top. Recoil and barrel spin are procedural, read by WeaponModel.
 */
public class WeaponAnimatable implements GeoAnimatable {
    /** What the turret is doing, as far as the weapon pose cares. */
    public enum Pose {IDLE, AIM, NO_AMMO, OVERHEAT}

    /** Longer than every deploy clip (0.8-0.9 s), so the pose takes over only once deploy is done. */
    private static final double DEPLOY_TICKS = 20;

    private final WeaponModuleItem item;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final String prefix;
    private final boolean deploy;
    private final double createdTick = RenderUtils.getCurrentTick();
    private Supplier<Pose> pose = () -> Pose.IDLE;
    private AnimationController<WeaponAnimatable> action;
    private float yaw, pitch;
    /** Kick of pitch_pivot in degrees (positive = muzzle up). */
    public final RecoilSpring recoil = new RecoilSpring(260f, 22f);
    /** Barrel cluster angle (radians, this and last tick) and speed (radians per tick), Machine Gun only. */
    public float spinAngle, prevSpinAngle, spinSpeed;
    /** Heat as a fraction of max heat; turns the emissive glow from the energy colour to red. */
    public float heat;
    /**
     * Rocket Launcher tubes, muzzle order: 1 = a rocket sits in the tube, 0 = empty. A tube empties when it fires and a
     * new rocket slides in after RELOAD_DELAY ticks, over RELOAD_TICKS; with no ammo left the tubes run dry.
     */
    public final float[] tubeLoad = {1, 1, 1, 1};
    /** Laser Rifle focus rings: angle (radians, this and last tick) and speed; they spin up while it charges. */
    public float focusAngle, prevFocusAngle, focusSpeed;
    /** Missile Launcher: loaded tubes as a bit mask, copied from the turret each tick. */
    public int missiles;
    private final int[] tubeDelay = new int[4];
    private static final int RELOAD_DELAY = 8, RELOAD_TICKS = 14;

    /** @param deploy play the deploy animation first: true when mounted just now, false when loaded with the chunk. */
    public WeaponAnimatable(WeaponModuleItem item, boolean deploy) {
        this.item = item;
        this.prefix = "animation." + item.modelName() + ".";
        this.deploy = deploy;
        if (deploy) { // freshly mounted: the rockets slide in one after another as part of the deploy
            for (int i = 0; i < 4; i++) {
                tubeLoad[i] = 0;
                tubeDelay[i] = 6 + 4 * i;
            }
        }
    }

    /** The tube this shot left from is empty now. */
    public void emptyTube(int tube) {
        int i = Math.floorMod(tube, 4);
        tubeLoad[i] = 0;
        tubeDelay[i] = RELOAD_DELAY;
    }

    /** Once per client tick: refill empty tubes, or unload them all while the turret has no ammo. */
    public void tickTubes(boolean hasAmmo) {
        for (int i = 0; i < 4; i++) {
            if (!hasAmmo) {
                tubeLoad[i] = Math.max(0, tubeLoad[i] - 1f / RELOAD_TICKS);
            } else if (tubeDelay[i] > 0) {
                tubeDelay[i]--;
            } else {
                tubeLoad[i] = Math.min(1, tubeLoad[i] + 1f / RELOAD_TICKS);
            }
        }
    }

    public WeaponModuleItem item() {
        return item;
    }

    public void setPose(Supplier<Pose> pose) {
        this.pose = pose;
    }

    public void setAim(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    /** Plays a one-shot clip ("fire", "pump", "cooldown_vent") over the current pose; restarting mid-way is fine. */
    public void trigger(String clip) {
        if (action != null && has(clip)) action.tryTriggerAnimation(clip);
    }

    /** Whether this weapon's animation file defines the clip (each weapon has its own subset, PLAN 6.3). */
    public boolean has(String clip) {
        BakedAnimations animations = GeckoLibCache.getBakedAnimations().get(Bastion.id("animations/" + item.modelName() + ".animation.json"));
        return animations != null && animations.getAnimation(prefix + clip) != null;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        RawAnimation deployClip = RawAnimation.begin().thenPlayAndHold(prefix + "deploy");
        RawAnimation idle = RawAnimation.begin().thenLoop(prefix + "idle");
        RawAnimation aim = RawAnimation.begin().thenPlayAndHold(prefix + "aim");
        RawAnimation noAmmo = RawAnimation.begin().thenLoop(prefix + "no_ammo");
        RawAnimation overheat = RawAnimation.begin().thenPlayAndHold(prefix + "overheat");
        controllers.add(new AnimationController<>(this, "main", 4, state -> {
            if (deploy && RenderUtils.getCurrentTick() - createdTick < DEPLOY_TICKS) return state.setAndContinue(deployClip);
            Pose current = pose.get();
            if (current == Pose.AIM && has("aim")) return state.setAndContinue(aim);
            if (current == Pose.NO_AMMO && has("no_ammo")) return state.setAndContinue(noAmmo);
            if (current == Pose.OVERHEAT && has("overheat")) return state.setAndContinue(overheat);
            return state.setAndContinue(idle);
        }));
        action = new AnimationController<>(this, "action", 0, state -> PlayState.STOP)
                .triggerableAnim("fire", RawAnimation.begin().thenPlay(prefix + "fire"))
                .triggerableAnim("pump", RawAnimation.begin().thenPlay(prefix + "pump"))
                .triggerableAnim("cooldown_vent", RawAnimation.begin().thenPlay(prefix + "cooldown_vent"));
        controllers.add(action);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object object) {
        return RenderUtils.getCurrentTick();
    }
}
