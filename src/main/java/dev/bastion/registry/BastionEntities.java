package dev.bastion.registry;

import dev.bastion.Bastion;
import dev.bastion.projectile.BroodCapsuleEntity;
import dev.bastion.projectile.JavelinMissileEntity;
import dev.bastion.projectile.TurretArrowEntity;
import dev.bastion.projectile.TurretRocketEntity;
import dev.bastion.turret.TurretHitboxEntity;
import dev.bastion.unit.BroodSpiderEntity;
import dev.bastion.unit.HiveDroneEntity;
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

    /** Mortar shell: falls on a ballistic arc (velocity to clients every tick), bursts into a fire patch. */
    public static final RegistryObject<EntityType<TurretRocketEntity>> TURRET_MORTAR_SHELL = REGISTER.register("turret_mortar_shell",
            () -> EntityType.Builder.<TurretRocketEntity>of(TurretRocketEntity::new, MobCategory.MISC)
                    .sized(0.4f, 0.4f).clientTrackingRange(8).updateInterval(1).setShouldReceiveVelocityUpdates(true).noSummon().fireImmune()
                    .build(Bastion.id("turret_mortar_shell").toString()));

    /** Javelin Missile: climbs out of sight and dives on its mark; tracked from 128 blocks (a 60-block climb), every tick. */
    public static final RegistryObject<EntityType<JavelinMissileEntity>> TURRET_JAVELIN = REGISTER.register("turret_javelin",
            () -> EntityType.Builder.<JavelinMissileEntity>of(JavelinMissileEntity::new, MobCategory.MISC)
                    .sized(0.5f, 1.5f).clientTrackingRange(8).updateInterval(1).setShouldReceiveVelocityUpdates(true).noSummon().fireImmune()
                    .build(Bastion.id("turret_javelin").toString()));

    /** Arrow Launcher arrow: a vanilla arrow (vanilla arrow renderer, the early-game exception to PLAN 1), sized and tracked like one. */
    public static final RegistryObject<EntityType<TurretArrowEntity>> TURRET_ARROW = REGISTER.register("turret_arrow",
            () -> EntityType.Builder.<TurretArrowEntity>of(TurretArrowEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(4).updateInterval(20).noSummon()
                    .build(Bastion.id("turret_arrow").toString()));

    /** Brood Cannon capsule: flies the Mortar's arc (velocity to clients every tick), lands, opens, releases spiders. */
    public static final RegistryObject<EntityType<BroodCapsuleEntity>> TURRET_BROOD_CAPSULE = REGISTER.register("turret_brood_capsule",
            () -> EntityType.Builder.<BroodCapsuleEntity>of(BroodCapsuleEntity::new, MobCategory.MISC)
                    .sized(0.8f, 0.8f).clientTrackingRange(8).updateInterval(1).setShouldReceiveVelocityUpdates(true).noSummon().noSave().fireImmune()
                    .build(Bastion.id("turret_brood_capsule").toString()));

    /** Brood Cannon spider (PLAN "Turret 3x3"): a small robot hexapod; never spawns naturally, never saved. */
    public static final RegistryObject<EntityType<BroodSpiderEntity>> BROOD_SPIDER = REGISTER.register("brood_spider",
            () -> EntityType.Builder.<BroodSpiderEntity>of(BroodSpiderEntity::new, MobCategory.MISC)
                    .sized(0.7f, 0.5f).clientTrackingRange(8).noSummon().noSave() // noSave: not even as a boat's passenger
                    .build(Bastion.id("brood_spider").toString()));

    /** Hive Carrier drone (PLAN "Turret 3x3"): a small quadcopter flying its own controller; never spawns naturally, never saved. */
    public static final RegistryObject<EntityType<HiveDroneEntity>> HIVE_DRONE = REGISTER.register("hive_drone",
            () -> EntityType.Builder.<HiveDroneEntity>of(HiveDroneEntity::new, MobCategory.MISC)
                    .sized(0.7f, 0.4f).clientTrackingRange(8).updateInterval(2).noSummon().noSave() // noSave: not even as a passenger
                    .build(Bastion.id("hive_drone").toString()));

    /** The hitbox is a LivingEntity (so mobs can target it) and every LivingEntity needs attributes. */
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(TURRET_HITBOX.get(), TurretHitboxEntity.createAttributes().build());
        event.put(BROOD_SPIDER.get(), BroodSpiderEntity.createAttributes().build());
        event.put(HIVE_DRONE.get(), HiveDroneEntity.createAttributes().build());
    }
}
