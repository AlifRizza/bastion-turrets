package dev.bastion.registry;

import dev.bastion.Bastion;
import dev.bastion.hub.HubCasingBlock;
import dev.bastion.hub.HubInterfaceBlock;
import dev.bastion.hub.HubPowerPortBlock;
import dev.bastion.hub.SatelliteUplinkBlock;
import dev.bastion.turret.FeedHubBlock;
import dev.bastion.turret.FortressTurretBaseBlock;
import dev.bastion.turret.LargeTurretBaseBlock;
import dev.bastion.turret.MakeshiftTurretBaseBlock;
import dev.bastion.turret.TurretBaseBlock;
import dev.bastion.workstation.CreativePowerSourceBlock;
import dev.bastion.workstation.WorkstationBlock;
import dev.bastion.workstation.WorkstationType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.common.util.ForgeSoundType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class BastionBlocks {
    public static final DeferredRegister<Block> REGISTER = DeferredRegister.create(ForgeRegistries.BLOCKS, Bastion.MOD_ID);

    // No requiresCorrectToolForDrops: breaking by hand must still drop the base and its stored ammo.
    public static final RegistryObject<TurretBaseBlock> TURRET_BASE = REGISTER.register("turret_base", () -> new TurretBaseBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f, 6f).sound(SoundType.METAL).noOcclusion().dynamicShape()));
    /**
     * The early-game base (BaseKind.MAKESHIFT): wood and scrap iron, so softer than the metal ones. It sounds like wood, but
     * placing it creaks (its own sound; suppliers because sounds register after blocks).
     */
    public static final RegistryObject<MakeshiftTurretBaseBlock> MAKESHIFT_TURRET_BASE = REGISTER.register("makeshift_turret_base", () -> new MakeshiftTurretBaseBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2f, 3f).noOcclusion().dynamicShape()
                    .sound(new ForgeSoundType(1f, 1f, () -> SoundEvents.WOOD_BREAK, () -> SoundEvents.WOOD_STEP, BastionSounds.MAKESHIFT_BASE_PLACE,
                            () -> SoundEvents.WOOD_HIT, () -> SoundEvents.WOOD_FALL))));
    /** 2x2x1 multiblock for big weapon modules; pistons cannot split it. */
    public static final RegistryObject<LargeTurretBaseBlock> LARGE_TURRET_BASE = REGISTER.register("large_turret_base", () -> new LargeTurretBaseBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5f, 9f).sound(SoundType.METAL).noOcclusion().dynamicShape()
                    .pushReaction(PushReaction.BLOCK)));
    /** 3x3x1 multiblock for huge weapon modules (BaseKind.FORTRESS); pistons cannot split it. */
    public static final RegistryObject<FortressTurretBaseBlock> FORTRESS_TURRET_BASE = REGISTER.register("fortress_turret_base", () -> new FortressTurretBaseBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5f, 9f).sound(SoundType.METAL).noOcclusion().dynamicShape()
                    .pushReaction(PushReaction.BLOCK)));

    // Workstations (PLAN Fase 9): multiblocks, so pistons cannot split them.
    public static final RegistryObject<WorkstationBlock> PART_WORKSTATION = workstation(WorkstationType.PART_WORKSTATION);
    public static final RegistryObject<WorkstationBlock> PART_ASSEMBLER = workstation(WorkstationType.PART_ASSEMBLER);
    public static final RegistryObject<WorkstationBlock> MODULE_WORKSTATION = workstation(WorkstationType.MODULE_WORKSTATION);
    public static final RegistryObject<WorkstationBlock> AMMO_WORKSTATION = workstation(WorkstationType.AMMO_WORKSTATION);
    public static final RegistryObject<WorkstationBlock> CHARGING_STATION = workstation(WorkstationType.CHARGING_STATION);
    /** Creative only: unlimited FE for whatever touches it. */
    /** Turret bases mount on its faces; it hands piped-in ammo out to them (FeedHubBlockEntity). */
    public static final RegistryObject<FeedHubBlock> FEED_HUB = REGISTER.register("feed_hub", () -> new FeedHubBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f, 6f).sound(SoundType.METAL)));
    // Turret Control Hub (PLAN "Turret Control Hub"): parts placed one by one into a 3x3 with a satellite on the middle; pistons cannot move them.
    public static final RegistryObject<HubCasingBlock> HUB_CASING = REGISTER.register("hub_casing", () -> new HubCasingBlock(hubPart()));
    public static final RegistryObject<HubPowerPortBlock> HUB_POWER_PORT = REGISTER.register("hub_power_port", () -> new HubPowerPortBlock(hubPart()));
    public static final RegistryObject<HubInterfaceBlock> HUB_INTERFACE = REGISTER.register("hub_interface",
            () -> new HubInterfaceBlock(hubPart().noOcclusion()));
    public static final RegistryObject<SatelliteUplinkBlock> SATELLITE_UPLINK_T1 = satellite(1);
    public static final RegistryObject<SatelliteUplinkBlock> SATELLITE_UPLINK_T2 = satellite(2);
    public static final RegistryObject<SatelliteUplinkBlock> SATELLITE_UPLINK_T3 = satellite(3);
    public static final RegistryObject<CreativePowerSourceBlock> CREATIVE_POWER_SOURCE = REGISTER.register("creative_power_source",
            () -> new CreativePowerSourceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(-1f, 3600000f).sound(SoundType.METAL)));

    private static BlockBehaviour.Properties hubPart() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f, 6f).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK);
    }

    private static RegistryObject<SatelliteUplinkBlock> satellite(int tier) {
        return REGISTER.register("satellite_uplink_t" + tier, () -> new SatelliteUplinkBlock(tier, hubPart().noOcclusion()));
    }

    private static RegistryObject<WorkstationBlock> workstation(WorkstationType type) {
        return REGISTER.register(type.id(), () -> new WorkstationBlock(type, BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                .strength(3.5f, 6f).sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.BLOCK)));
    }
}
