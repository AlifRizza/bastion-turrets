package dev.bastion.client.render;

import dev.bastion.Bastion;
import dev.bastion.client.anim.WeaponAnimatable;
import dev.bastion.weapon.WeaponModuleItem;
import dev.bastion.weapon.types.MissileLauncherWeapon;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

/**
 * Weapon model files named after the item (PLAN 6.5) and the procedural bones (PLAN 6.2, 6.4): yaw_pivot and
 * pitch_pivot aim (never keyframed), the recoil spring kicks pitch_pivot, barrel_cluster spins (Machine Gun).
 * Models face north (-Z) in Blockbench.
 */
public class WeaponModel<T extends GeoAnimatable> extends GeoModel<T> {
    /** Bones whose world position the client needs for VFX: muzzles, casing port, sensor. */
    /** How far (px) a reloading rocket sits back inside its tube before it slides forward. */
    private static final float ROCKET_SLIDE = 2.6f;
    /** Railgun coil bands along the channel, breech 0 to muzzle 1 (design_weapons.py RG_COILS). */
    private static final float[] RAIL_COILS = {0.128f, 0.299f, 0.470f, 0.642f, 0.813f};
    static final String[] TRACKED = {"muzzle_0", "muzzle_1", "muzzle_2", "muzzle_3", "muzzle_4", "muzzle_5", "muzzle_6", "muzzle_7",
            "muzzle_8", "muzzle_9", "muzzle_10", "muzzle_11", "eject_0", "sensor"};

    private static String name(GeoAnimatable animatable) {
        return animatable instanceof WeaponAnimatable weapon ? weapon.item().modelName() : ((WeaponModuleItem) animatable).modelName();
    }

    @Override
    public ResourceLocation getModelResource(T animatable) {
        return Bastion.id("geo/" + name(animatable) + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(T animatable) {
        return Bastion.id("textures/item/" + name(animatable) + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(T animatable) {
        return Bastion.id("animations/" + name(animatable) + ".animation.json");
    }

    // Bones are shared by every renderer of this model, so the held item resets them to rest explicitly.
    @Override
    public void setCustomAnimations(T animatable, long instanceId, AnimationState<T> animationState) {
        float yaw = 0, pitch = 0, spin = 0;
        if (animatable instanceof WeaponAnimatable weapon) {
            // Minecraft yaw 0 faces +Z; the model faces -Z, hence the 180 deg offset. Pitch > 0 looks down.
            yaw = (180f - weapon.yaw()) * Mth.DEG_TO_RAD;
            pitch = (-weapon.pitch() + weapon.recoil.sample()) * Mth.DEG_TO_RAD;
            spin = Mth.lerp(animationState.getPartialTick(), weapon.prevSpinAngle, weapon.spinAngle);
            for (String name : TRACKED) getBone(name).ifPresent(bone -> bone.setTrackingMatrices(true));
        }
        float focus = animatable instanceof WeaponAnimatable weapon
                ? Mth.lerp(animationState.getPartialTick(), weapon.prevFocusAngle, weapon.focusAngle) : 0;
        float yawRad = yaw, pitchRad = pitch, spinRad = spin;
        getBone("focus_rings").ifPresent(bone -> bone.setRotZ(focus)); // Laser Rifle
        getBone("dome_ring").ifPresent(bone -> bone.setRotY(focus)); // Repulsor Dome
        getBone("yaw_pivot").ifPresent(bone -> bone.setRotY(yawRad));
        getBone("pitch_pivot").ifPresent(bone -> bone.setRotX(pitchRad));
        // Absolute, never additive (unanimated bones are not always reset per frame). Untouched until the
        // first spin, so the deploy clip's twist still plays on a fresh Machine Gun.
        if (spinRad != 0) getBone("barrel_cluster").ifPresent(bone -> bone.setRotZ(spinRad));
        // Missile Launcher: a missile shows in each loaded tube (server-synced; always full in hand).
        int missiles = animatable instanceof WeaponAnimatable weapon ? weapon.missiles : MissileLauncherWeapon.ALL_TUBES;
        for (int i = 0; i < MissileLauncherWeapon.TUBES; i++) {
            boolean loaded = (missiles & 1 << i) != 0;
            getBone("missile_" + i).ifPresent(bone -> bone.setHidden(!loaded));
        }
        // Railgun: while it charges, each coil band stays dark until the light reaches it (always lit in hand).
        float coilFill = animatable instanceof WeaponAnimatable weapon ? weapon.coilFill : -1;
        for (int i = 0; i < RAIL_COILS.length; i++) {
            boolean dark = coilFill >= 0 && coilFill < RAIL_COILS[i];
            getBone("glow_coil_" + i).ifPresent(bone -> bone.setHidden(dark));
        }
        // Rocket Launcher: each tube shows its own rocket, slid back into the pod while it reloads (always full in hand).
        for (int i = 0; i < 4; i++) {
            float load = animatable instanceof WeaponAnimatable weapon ? weapon.tubeLoad[i] : 1;
            getBone("rocket_" + i).ifPresent(bone -> {
                bone.setHidden(load < 0.02f);
                bone.setPosZ((1 - load) * ROCKET_SLIDE);
            });
        }
    }

}
