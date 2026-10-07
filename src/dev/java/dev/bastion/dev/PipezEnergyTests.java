package dev.bastion.dev;

import dev.bastion.Bastion;
import dev.bastion.registry.BastionBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Dev-only GameTests that need Pipez in the run (a runtime-only dev mod: its API is reached by name). */
@GameTestHolder(Bastion.MOD_ID)
@PrefixGameTestTemplate(false)
public class PipezEnergyTests {
    /** Creative Power Source -> two Pipez energy pipes (extracting at the source) -> Feed Hub: FE arrives in the hub. */
    @GameTest(template = "arena", batch = "pipezCarriesCreativePower", timeoutTicks = 100)
    public static void pipezCarriesCreativePower(GameTestHelper helper) throws ReflectiveOperationException {
        Block pipe = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("pipez", "energy_pipe"));
        BlockPos source = new BlockPos(3, 2, 2), first = source.east(), hub = source.east(3);
        helper.setBlock(first, pipe);
        helper.setBlock(first.east(), pipe);
        helper.setBlock(source, BastionBlocks.CREATIVE_POWER_SOURCE.get()); // placing them updates the pipes' connections
        helper.setBlock(hub, BastionBlocks.FEED_HUB.get());
        helper.assertTrue(helper.getBlockState(first).getValue(BooleanProperty.create("west")), "the pipe did not connect to the power source");
        pipe.getClass().getMethod("setExtracting", Level.class, BlockPos.class, Direction.class, boolean.class)
                .invoke(pipe, helper.getLevel(), helper.absolutePos(first), Direction.WEST, true);
        helper.succeedWhen(() -> {
            int stored = helper.getBlockEntity(hub).getCapability(ForgeCapabilities.ENERGY, Direction.UP).map(e -> e.getEnergyStored()).orElse(0);
            helper.assertTrue(stored > 0, "no FE reached the Feed Hub");
        });
    }
}
