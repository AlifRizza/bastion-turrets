package dev.bastion.registry;

import dev.bastion.Bastion;
import dev.bastion.turret.FeedHubBlock;
import dev.bastion.turret.LargeTurretBaseBlock;
import dev.bastion.turret.TurretBaseBlock;
import dev.bastion.workstation.CreativePowerSourceBlock;
import dev.bastion.workstation.WorkstationBlock;
import dev.bastion.workstation.WorkstationType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class BastionBlocks {
    public static final DeferredRegister<Block> REGISTER = DeferredRegister.create(ForgeRegistries.BLOCKS, Bastion.MOD_ID);

    // No requiresCorrectToolForDrops: breaking by hand must still drop the base and its stored ammo.
    public static final RegistryObject<TurretBaseBlock> TURRET_BASE = REGISTER.register("turret_base", () -> new TurretBaseBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f, 6f).sound(SoundType.METAL).noOcclusion().dynamicShape()));
    /** 2x2x1 multiblock for big weapon modules; pistons cannot split it. */
    public static final RegistryObject<LargeTurretBaseBlock> LARGE_TURRET_BASE = REGISTER.register("large_turret_base", () -> new LargeTurretBaseBlock(
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
    public static final RegistryObject<CreativePowerSourceBlock> CREATIVE_POWER_SOURCE = REGISTER.register("creative_power_source",
            () -> new CreativePowerSourceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(-1f, 3600000f).sound(SoundType.METAL)));

    private static RegistryObject<WorkstationBlock> workstation(WorkstationType type) {
        return REGISTER.register(type.id(), () -> new WorkstationBlock(type, BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                .strength(3.5f, 6f).sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.BLOCK)));
    }
}
