package dev.bastion.registry;

import dev.bastion.Bastion;
import dev.bastion.turret.FeedHubBlockEntity;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretPartBlockEntity;
import dev.bastion.workstation.CreativePowerSourceBlock;
import dev.bastion.workstation.WorkstationBlockEntity;
import dev.bastion.workstation.WorkstationPartBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class BastionBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> REGISTER = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Bastion.MOD_ID);

    @SuppressWarnings("DataFlowIssue") // null data fixer type is the standard for modded block entities
    public static final RegistryObject<BlockEntityType<TurretBaseBlockEntity>> TURRET_BASE = REGISTER.register("turret_base",
            () -> BlockEntityType.Builder.of(TurretBaseBlockEntity::new, BastionBlocks.TURRET_BASE.get(), BastionBlocks.LARGE_TURRET_BASE.get())
                    .build(null));
    /** The three non-core blocks of a large base; they forward automation to the core. */
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<TurretPartBlockEntity>> TURRET_PART = REGISTER.register("turret_part",
            () -> BlockEntityType.Builder.of(TurretPartBlockEntity::new, BastionBlocks.LARGE_TURRET_BASE.get()).build(null));

    /** The core block of every workstation. */
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<WorkstationBlockEntity>> WORKSTATION = REGISTER.register("workstation",
            () -> BlockEntityType.Builder.of(WorkstationBlockEntity::new, workstations()).build(null));
    /** The other blocks of a workstation; they forward automation and energy to the core. */
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<WorkstationPartBlockEntity>> WORKSTATION_PART = REGISTER.register("workstation_part",
            () -> BlockEntityType.Builder.of(WorkstationPartBlockEntity::new, workstations()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<FeedHubBlockEntity>> FEED_HUB = REGISTER.register("feed_hub",
            () -> BlockEntityType.Builder.of(FeedHubBlockEntity::new, BastionBlocks.FEED_HUB.get()).build(null));
    public static final RegistryObject<BlockEntityType<CreativePowerSourceBlock.Source>> CREATIVE_POWER_SOURCE = REGISTER.register("creative_power_source",
            () -> BlockEntityType.Builder.of(CreativePowerSourceBlock.Source::new, BastionBlocks.CREATIVE_POWER_SOURCE.get()).build(null));

    private static Block[] workstations() {
        return new Block[]{BastionBlocks.PART_WORKSTATION.get(), BastionBlocks.PART_ASSEMBLER.get(), BastionBlocks.MODULE_WORKSTATION.get(),
                BastionBlocks.AMMO_WORKSTATION.get(), BastionBlocks.CHARGING_STATION.get()};
    }
}
