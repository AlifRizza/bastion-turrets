package dev.bastion.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.Event;
import org.jetbrains.annotations.Nullable;

/**
 * A turret hit with a real impact point, posted on the Forge event bus (server side) right before the target is hurt:
 * every bullet, pellet, beam, bolt and slug, and a rocket's, capsule's or drone bullet's direct hit. Splash, fire,
 * burning and Tesla chains have no such point and post nothing. Body-part mods resolve the part from here: the
 * {@link #getSource() source} is the very instance the following {@code hurt()} gets.
 */
public class TurretHitEvent extends Event {
    private final Entity target;
    private final Vec3 hitPos;
    private final Vec3 direction;
    @Nullable
    private final ResourceLocation weapon;
    private final DamageSource source;

    public TurretHitEvent(Entity target, Vec3 hitPos, Vec3 direction, @Nullable ResourceLocation weapon, DamageSource source) {
        this.target = target;
        this.hitPos = hitPos;
        this.direction = direction.normalize();
        this.weapon = weapon;
        this.source = source;
    }

    public Entity getTarget() {
        return target;
    }

    /** Where the shot met the target's (slightly grown) bounding box. */
    public Vec3 getHitPos() {
        return hitPos;
    }

    /** Unit vector the shot travelled along. */
    public Vec3 getDirection() {
        return direction;
    }

    /**
     * The weapon type for turret weapons ({@code bastion:gun}, {@code bastion:railgun}...), the entity type for
     * projectiles and units ({@code bastion:turret_rocket}, {@code bastion:hive_drone}...). Null only for a bullet
     * made outside a turret shot (GameTests).
     */
    @Nullable
    public ResourceLocation getWeapon() {
        return weapon;
    }

    public DamageSource getSource() {
        return source;
    }
}
