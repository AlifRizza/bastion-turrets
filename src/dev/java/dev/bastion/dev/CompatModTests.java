package dev.bastion.dev;

import dev.bastion.Bastion;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretHitboxEntity;
import dev.bastion.turret.TurretInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * Dev-only: turrets against other content mods (-PwithCompatMods). Each test passes at once when its mod is not in
 * the run. Fungal Infection: Spore mobs are shot and attack turrets; TaCZ bullets can hit a turret's hitbox.
 */
@GameTestHolder(Bastion.MOD_ID)
@PrefixGameTestTemplate(false)
public class CompatModTests {
    private static final String ARENA = "arena";
    private static final BlockPos BASE = new BlockPos(2, 2, 3);
    private static final ResourceLocation INFECTED = new ResourceLocation("spore", "inf_human");

    private static TurretBaseBlockEntity gunTurret(GameTestHelper helper, boolean armed) {
        helper.setBlock(BASE, BastionBlocks.TURRET_BASE.get());
        TurretBaseBlockEntity turret = (TurretBaseBlockEntity) helper.getBlockEntity(BASE);
        if (armed) {
            turret.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.GUN_TURRET.get()));
            turret.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.CREATIVE_AMMO.get()));
        }
        return turret;
    }

    @SuppressWarnings("unchecked")
    private static EntityType<Mob> mob(EntityType<?> type) {
        return (EntityType<Mob>) type;
    }

    /** A Spore Infected Human in range is a hostile target: the turret shoots it. */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "sporeMobIsShot")
    public static void sporeMobIsShot(GameTestHelper helper) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(INFECTED);
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(INFECTED) || type == null) {
            helper.succeed();
            return;
        }
        TurretBaseBlockEntity turret = gunTurret(helper, true);
        LivingEntity infected = helper.spawnWithNoFreeWill(mob(type), new BlockPos(9, 2, 3));
        helper.succeedWhen(() -> {
            helper.assertTrue(infected.getHealth() < infected.getMaxHealth() || !infected.isAlive(), "never shot, state " + turret.state());
            turret.inventory().setStackInSlot(TurretInventory.WEAPON, ItemStack.EMPTY);
        });
    }

    /** An unarmed turret and a free-willed Infected Human: it walks up and damages the turret. */
    @GameTest(template = ARENA, timeoutTicks = 400, batch = "sporeMobAttacksTurret")
    public static void sporeMobAttacksTurret(GameTestHelper helper) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(INFECTED);
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(INFECTED) || type == null) {
            helper.succeed();
            return;
        }
        TurretBaseBlockEntity turret = gunTurret(helper, false);
        Mob infected = helper.spawn(mob(type), new BlockPos(8, 2, 3));
        helper.succeedWhen(() -> helper.assertTrue(turret.health() < turret.maxHealth(),
                "turret untouched; the infected targets " + infected.getTarget()));
    }

    /**
     * TaCZ picks what its bullets hit with EntityUtil.findEntityOnPath (entities that are isPickable and not spectators).
     * A path straight through a turret's hitbox must find the hitbox, or gun bullets fly through turrets.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "taczBulletsFindTurrets")
    public static void taczBulletsFindTurrets(GameTestHelper helper) throws ReflectiveOperationException {
        Class<?> util;
        try {
            util = Class.forName("com.tacz.guns.util.EntityUtil");
        } catch (ClassNotFoundException absent) {
            helper.succeed();
            return;
        }
        gunTurret(helper, true);
        helper.succeedWhen(() -> { // the hitbox appears within 20 ticks
            List<TurretHitboxEntity> hitboxes = helper.getLevel().getEntitiesOfClass(TurretHitboxEntity.class, new AABB(helper.absolutePos(BASE)).inflate(2));
            helper.assertTrue(hitboxes.size() == 1, "expected one turret hitbox, found " + hitboxes.size());
            Vec3 centre = hitboxes.get(0).getBoundingBox().getCenter();
            Projectile probe = new Arrow(EntityType.ARROW, helper.getLevel());
            Vec3 from = centre.add(-5, 0, 0), to = centre.add(5, 0, 0);
            probe.setPos(from);
            probe.setDeltaMovement(to.subtract(from)); // TaCZ searches the bullet's box swept by its motion
            Object hit;
            try {
                hit = util.getMethod("findEntityOnPath", Projectile.class, Vec3.class, Vec3.class).invoke(null, probe, from, to);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            helper.assertTrue(hit != null, "a TaCZ bullet path through the turret found nothing (the hitbox is not pickable)");
        });
    }
}
