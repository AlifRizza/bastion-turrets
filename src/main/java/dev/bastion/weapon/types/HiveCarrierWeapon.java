package dev.bastion.weapon.types;

import dev.bastion.registry.BastionItems;
import dev.bastion.registry.BastionWeaponTypes;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.targeting.LineOfSight;
import dev.bastion.unit.DroneStats;
import dev.bastion.unit.HiveDroneEntity;
import dev.bastion.unit.TurretUnits;
import dev.bastion.weapon.FireContext;
import dev.bastion.weapon.FireProfile;
import dev.bastion.weapon.ShotReport;
import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponState;
import dev.bastion.weapon.WeaponType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Hive Carrier (Fortress Turret Base, PLAN "Turret 3x3", user 2026-10-23): it does not aim (turn_speed 0, so Overwatch only
 * watches it). While a valid target is in range it sends out a wave of drones_per_wave drones every fire_interval, from
 * its bays: docked drones first (each for reload_energy FE, bullets refilled), then new ones (each a Hive Drone item and
 * energy_per_drone FE). ammo_per_shot is 0: the base never takes an item, onFire pays per drone. Docked plus flying drones
 * never pass unit_cap (nor the dimension's maxTurretUnits); at the cap it waits, spending nothing. Drones come back and
 * dock here (dock), kept as a count saved with the turret (flying drones are never saved).
 */
public class HiveCarrierWeapon extends WeaponType {
    /**
     * Blocks over the base's top the carrier "looks" from for targets not under open sky: over the hive's cap, and at its
     * doorways (the drones' way out; under a low roof the cap's point is inside the roof).
     */
    private static final double[] LOOKOUTS = {3.0, 1.0};

    /**
     * Bay points in px over the turntable (design_weapons.py HC_BAYS): three landing pads on the hive's floor, 12 px from
     * the middle, each behind a hole in the wall facing away from the middle (north, south-east, south-west). The floor
     * stands 10 px up, clear over the armed base's solid top (8 px, FortressTurretBaseBlock.ARMED): drones fly level out.
     */
    private static final Vec3[] BAYS = {new Vec3(0, 10, -12), new Vec3(10.392, 10, 6), new Vec3(-10.392, 10, 6)};

    @Override
    public FireProfile fireProfile() {
        return FireProfile.PROJECTILE;
    }

    /**
     * Drones fly, so the carrier needs no line of sight of its own: a target counts when drones can get at it, under open
     * sky or seen from over the hive (user 2026-10-24 17:09: a mob in a cave or behind walls in range kept sending waves
     * out and back every 15 s, as drones must see what they shoot). Flyers too.
     */
    @Override
    @Nullable
    public Vec3 sight(TurretBaseBlockEntity turret, LivingEntity target, StatSheet stats) {
        Vec3 centre = target.getBoundingBox().getCenter();
        if (JavelinWeapon.openSky(target)) return centre;
        for (double height : LOOKOUTS) {
            Vec3 lookout = turret.alongMount(TurretBaseBlockEntity.BASE_HEIGHT + height);
            if (LineOfSight.clear(turret.getLevel(), lookout, target.getEyePosition()) || LineOfSight.clear(turret.getLevel(), lookout, centre)) return centre;
        }
        return null;
    }

    @Override
    public void tick(TurretBaseBlockEntity turret, WeaponState state, StatSheet stats, boolean engaged) {
        // Full when nothing can leave: no room to fly at all (cap or the dimension's total), or none docked and no room for a new one.
        state.unitsFull = flyRoom(turret, stats.data()) <= 0 || state.dockedDrones <= 0 && room(turret, state, stats.data()) <= 0;
        boolean items = turret.inventory().findAmmoSlot() >= 0;
        if (items != state.droneItems) { // clients draw new drones being built only with something to build them from
            state.droneItems = items;
            turret.getLevel().sendBlockUpdated(turret.getBlockPos(), turret.getBlockState(), turret.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public boolean ready(StatSheet stats, WeaponState state) {
        return !state.unitsFull;
    }

    /** Nothing docked and no Hive Drone or not the FE for a new one. */
    @Override
    public boolean outOfAmmo(TurretBaseBlockEntity turret, WeaponState state) {
        WeaponData data = turret.inventory().weaponData();
        if (data == null) return true;
        if (state.dockedDrones > 0) return !turret.hasEnergy(Math.round(data.param("reload_energy")));
        return turret.inventory().findAmmoSlot() < 0 || !turret.hasEnergy(Math.round(data.param("energy_per_drone")));
    }

    @Override
    public void onFire(FireContext context) {
        TurretBaseBlockEntity turret = context.turret();
        WeaponState state = context.state();
        WeaponData data = context.stats().data();
        DroneStats stats = DroneStats.of(context.stats());
        int wave = Math.round(data.param("drones_per_wave")), launched = 0, docked = state.dockedDrones;
        List<Vec3> bays = clearBaysFirst(turret);
        for (int i = 0; i < wave; i++) {
            if (state.dockedDrones > 0 && flyRoom(turret, data) > 0 && turret.useEnergy(Math.round(data.param("reload_energy")))) {
                state.dockedDrones--;
            } else if (room(turret, state, data) > 0 && turret.hasEnergy(Math.round(data.param("energy_per_drone"))) && takeDrone(turret)) {
                turret.useEnergy(Math.round(data.param("energy_per_drone"))); // a new drone: FE checked before its item is taken
            } else {
                break;
            }
            HiveDroneEntity.spawn(context.level(), bays.get(i % bays.size()), turret.getBlockPos(), turret.filter(), turret.owner(), stats, stats.ammo());
            launched++;
        }
        if (launched > 0) ShotReport.send(context, this, 0, List.of());
        if (state.dockedDrones != docked) { // the racks emptied: clients stop drawing those drones
            context.level().sendBlockUpdated(turret.getBlockPos(), turret.getBlockState(), turret.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * The bays in the order a wave takes them: those whose doorway is clear (HiveDroneEntity.doorwayClear) first, so with a
     * wall in front of one doorway the drones leave by the others; all blocked, all three anyway.
     */
    private static List<Vec3> clearBaysFirst(TurretBaseBlockEntity turret) {
        List<Vec3> clear = new ArrayList<>(), blocked = new ArrayList<>();
        for (int i = 0; i < BAYS.length; i++) {
            Vec3 bay = bay(turret, i);
            (HiveDroneEntity.doorwayClear(turret.getLevel(), bay, turret.getBlockPos()) ? clear : blocked).add(bay);
        }
        return clear.isEmpty() ? blocked : clear;
    }

    /** Room to fly for one more drone, docked or new: the cap less what is out, never past the dimension's total. */
    private static int flyRoom(TurretBaseBlockEntity turret, WeaponData data) {
        return TurretUnits.room(turret.getLevel(), turret.getBlockPos(), Math.round(data.param("unit_cap")));
    }

    /** Places left for new drones: the cap less what is out and docked, never past the dimension's total. */
    private static int room(TurretBaseBlockEntity turret, WeaponState state, WeaponData data) {
        return TurretUnits.room(turret.getLevel(), turret.getBlockPos(), Math.round(data.param("unit_cap")) - state.dockedDrones);
    }

    /** One Hive Drone out of the ammo slots (Creative Ammo pays without being used up). */
    private static boolean takeDrone(TurretBaseBlockEntity turret) {
        int slot = turret.inventory().findAmmoSlot();
        if (slot < 0) return false;
        if (!turret.inventory().getStackInSlot(slot).is(BastionItems.CREATIVE_AMMO.get())) turret.inventory().extractItem(slot, 1, false);
        return true;
    }

    /** Where bay {@code i} opens, in the world (the carrier never turns). */
    public static Vec3 bay(TurretBaseBlockEntity turret, int i) {
        Vec3 px = BAYS[Math.floorMod(i, BAYS.length)];
        return turret.alongMount(TurretBaseBlockEntity.BASE_HEIGHT + px.y / 16).add(px.x / 16, 0, px.z / 16);
    }

    /**
     * The middle of each doorway's mouth in px over the turntable, where a drone leaving bay i is out of the wall
     * (HiveDroneEntity.EXIT past the bay, half a doorway up): the model's muzzle_i locators, where a wave's puffs play.
     */
    public static List<Vec3> doorwayMouthsPx() {
        return Arrays.stream(BAYS).map(b -> b.add(new Vec3(b.x, 0, b.z).normalize().scale(HiveDroneEntity.EXIT * 16)).add(0, 4, 0)).toList();
    }

    /** A drone back in its bay: counted as docked when a Hive Carrier still stands at {@code home}. */
    public static void dock(ServerLevel level, BlockPos home) {
        if (isCarrier(level, home) && level.getBlockEntity(home) instanceof TurretBaseBlockEntity turret) {
            turret.weaponState().dockedDrones++;
            turret.setChanged(); // the count is saved: a save made while it was out must not have the last word
            level.sendBlockUpdated(home, turret.getBlockState(), turret.getBlockState(), Block.UPDATE_CLIENTS); // clients draw it on its rack
        }
    }

    /** A loaded turret at {@code pos} with a Hive Carrier mounted (no chunk load). */
    public static boolean isCarrier(Level level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof TurretBaseBlockEntity turret && turret.inventory().weaponData() != null
                && BastionWeaponTypes.REGISTRY.get().getValue(turret.inventory().weaponData().type()) instanceof HiveCarrierWeapon;
    }

    /** Unused: drones are entities, not shots. */
    @Override
    public boolean canTarget(Entity entity, TurretBaseBlockEntity turret) {
        return true;
    }
}
