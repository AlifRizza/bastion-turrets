package dev.bastion.registry;

import dev.bastion.Bastion;
import dev.bastion.projectile.TurretRocketEntity;
import dev.bastion.turret.TurretHitboxEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class BastionEntities {
    public static final DeferredRegister<EntityType<?>> REGISTER = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Bastion.MOD_ID);

    /** Damage receiver for a turret base (PLAN 4.1): turret-sized, never saved, never rendered. */
    public static final RegistryObject<EntityType<TurretHitboxEntity>> TURRET_HITBOX = REGISTER.register("turret_hitbox",
            () -> EntityType.Builder.<TurretHitboxEntity>of(TurretHitboxEntity::new, MobCategory.MISC)
                    .sized(0.9f, 1.1f).clientTrackingRange(6).updateInterval(20).noSummon().fireImmune()
                    .build(Bastion.id("turret_hitbox").toString()));

    /** Rocket Launcher Turret projectile: small, fast, tracked every tick so flight looks smooth. */
    public static final RegistryObject<EntityType<TurretRocketEntity>> TURRET_ROCKET = REGISTER.register("turret_rocket",
            () -> EntityType.Builder.<TurretRocketEntity>of(TurretRocketEntity::new, MobCategory.MISC)
                    .sized(0.35f, 0.35f).clientTrackingRange(8).updateInterval(1).noSummon().fireImmune()
                    .build(Bastion.id("turret_rocket").toString()));

    /** Missile Launcher Turret homing missile: same projectile class as the rocket, smaller model, lighter blast. */
    public static final RegistryObject<EntityType<TurretRocketEntity>> TURRET_MISSILE = REGISTER.register("turret_missile",
            () -> EntityType.Builder.<TurretRocketEntity>of(TurretRocketEntity::new, MobCategory.MISC)
                    .sized(0.3f, 0.3f).clientTrackingRange(8).updateInterval(1).setShouldReceiveVelocityUpdates(true).noSummon().fireImmune()
                    .build(Bastion.id("turret_missile").toString()));

    /** The hitbox is a LivingEntity (so mobs can target it) and every LivingEntity needs attributes. */
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(TURRET_HITBOX.get(), TurretHitboxEntity.createAttributes().build());
    }
}
