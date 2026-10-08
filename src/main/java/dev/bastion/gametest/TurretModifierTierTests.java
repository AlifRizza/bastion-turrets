package dev.bastion.gametest;

import dev.bastion.Bastion;
import dev.bastion.modifier.ModifierItem;
import net.minecraft.core.registries.BuiltInRegistries;
import dev.bastion.modifier.ModifierEffect;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import dev.bastion.turret.TurretTier;
import dev.bastion.workstation.WorkstationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;

/** Modifier Mk1-Mk3 (PLAN 4.6 "Tier modul"): one per kind, scaled numbers, upgrade at the Module Workstation. */
@GameTestHolder(Bastion.MOD_ID)
@PrefixGameTestTemplate(false)
public class TurretModifierTierTests {
    private static final String ARENA = "arena";

    static ModifierItem item(String path) {
        return ForgeRegistries.ITEMS.getValue(Bastion.id(path)) instanceof ModifierItem modifier ? modifier : null;
    }

    /** A base takes one of each kind whatever its Mk: Range Mk1 in, Range Mk3 refused, Damage Amplifier Mk2 fine. */
    @GameTest(template = ARENA, batch = "oneModifierPerKind")
    public static void oneModifierPerKind(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 3);
        helper.setBlock(pos, BastionBlocks.TURRET_BASE.get());
        TurretBaseBlockEntity turret = (TurretBaseBlockEntity) helper.getBlockEntity(pos);
        turret.upgradeTo(TurretTier.T2);
        turret.upgradeTo(TurretTier.T3); // 4 module slots
        ModifierItem rangeMk3 = item("range_module_mk3"), damageMk2 = item("damage_amplifier_mk2");
        helper.assertTrue(rangeMk3 != null && damageMk2 != null, "Mk2/Mk3 items missing");
        int first = TurretInventory.MODIFIER_START;
        turret.inventory().setStackInSlot(first, new ItemStack(BastionItems.RANGE_MODULE.get()));
        helper.assertTrue(!turret.inventory().isItemValid(first + 1, new ItemStack(rangeMk3)), "Range Mk3 accepted next to Range Mk1");
        helper.assertTrue(turret.inventory().isItemValid(first + 1, new ItemStack(damageMk2)), "Damage Amplifier Mk2 refused");
        helper.succeed();
    }

    /** Every float of Mk2/Mk3 is Mk1 x1.5 / x2, Penetrator's pierce 1/2/3, the weapons list unchanged. */
    @GameTest(template = ARENA, batch = "modifierTiersScale")
    public static void modifierTiersScale(GameTestHelper helper) throws ReflectiveOperationException {
        List<ModifierItem> mk1 = BuiltInRegistries.ITEM.stream().filter(i -> i instanceof ModifierItem m && m.mk() == 1).map(i -> (ModifierItem) i).toList();
        helper.assertTrue(mk1.size() == 10, "expected 10 modifier kinds, found " + mk1.size());
        for (ModifierItem base : mk1) {
            String id = BuiltInRegistries.ITEM.getKey(base).getPath();
            for (int mk = 2; mk <= 3; mk++) {
                ModifierItem tier = item(id + "_mk" + mk);
                helper.assertTrue(tier != null && tier.mk() == mk && tier.family().equals(BuiltInRegistries.ITEM.getKey(base)), id + " Mk" + mk + " missing");
                double scale = mk == 2 ? 1.5 : 2;
                for (RecordComponent c : ModifierEffect.class.getRecordComponents()) {
                    Object a = c.getAccessor().invoke(base.effect()), b = c.getAccessor().invoke(tier.effect());
                    if (a instanceof Float f) helper.assertTrue(Math.abs((Float) b - f * scale) < 1e-4, id + " Mk" + mk + " " + c.getName() + " " + b + " vs Mk1 " + f);
                    else if (a instanceof Integer n) helper.assertTrue((Integer) b == n * mk, id + " Mk" + mk + " " + c.getName() + " " + b);
                    else helper.assertTrue(a.equals(b), id + " Mk" + mk + " " + c.getName() + " differs");
                }
            }
        }
        helper.succeed();
    }

    /** Module Workstation: Range Module + 2 diamonds + 4 redstone become a Range Module Mk2 (instant for the player). */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "moduleUpgradesToMk2")
    public static void moduleUpgradesToMk2(GameTestHelper helper) {
        BlockPos core = new BlockPos(4, 2, 2);
        BastionBlocks.MODULE_WORKSTATION.get().placeAt(helper.getLevel(), helper.absolutePos(core), Direction.NORTH);
        helper.setBlock(core.east(), BastionBlocks.CREATIVE_POWER_SOURCE.get());
        WorkstationBlockEntity station = (WorkstationBlockEntity) helper.getBlockEntity(core);
        helper.runAtTickTime(5, () -> {
            station.selectRecipe(Bastion.id("workstation/module_workstation/range_module_mk2"));
            FakePlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
            player.getInventory().clearContent();
            player.getInventory().add(new ItemStack(BastionItems.RANGE_MODULE.get()));
            player.getInventory().add(new ItemStack(Items.DIAMOND, 2));
            player.getInventory().add(new ItemStack(Items.REDSTONE, 4));
            station.instantCraft(player, 1);
            helper.assertTrue(player.getInventory().countItem(item("range_module_mk2")) == 1, "no Range Module Mk2");
            helper.assertTrue(player.getInventory().countItem(BastionItems.RANGE_MODULE.get()) == 0, "the Mk1 was not used up");
            player.getInventory().clearContent();
            // Arenas keep what a test leaves inside: clear the station and the source, or a later test placed here breaks.
            helper.setBlock(core.east(), Blocks.AIR);
            helper.setBlock(core, Blocks.AIR);
            helper.succeed();
        });
    }

    /**
     * Review 2026-10-08: a hopper-fed Module Workstation upgrading modules. Modules stack to one, so they used to fill all
     * six inputs and the diamonds and redstone never got in. A chest of 6 Mk1 + diamonds + redstone, fed one item a tick
     * like a hopper, the output taken like a hopper below: it keeps upgrading.
     */
    @GameTest(template = ARENA, timeoutTicks = 500, batch = "moduleUpgradesByAutomation")
    public static void moduleUpgradesByAutomation(GameTestHelper helper) {
        BlockPos core = new BlockPos(4, 2, 2);
        BastionBlocks.MODULE_WORKSTATION.get().placeAt(helper.getLevel(), helper.absolutePos(core), Direction.NORTH);
        helper.setBlock(core.east(), BastionBlocks.CREATIVE_POWER_SOURCE.get());
        WorkstationBlockEntity station = (WorkstationBlockEntity) helper.getBlockEntity(core);
        station.selectRecipe(Bastion.id("workstation/module_workstation/range_module_mk2"));
        IItemHandler hopper = helper.getBlockEntity(core).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(IllegalStateException::new);
        List<ItemStack> chest = new ArrayList<>();
        for (int i = 0; i < 6; i++) chest.add(new ItemStack(BastionItems.RANGE_MODULE.get()));
        chest.add(new ItemStack(Items.DIAMOND, 12));
        chest.add(new ItemStack(Items.REDSTONE, 24));
        int[] made = {0};
        helper.onEachTick(() -> {
            for (ItemStack stack : chest) { // like a hopper: the first stack that fits gives one item
                if (stack.isEmpty()) continue;
                if (ItemHandlerHelper.insertItem(hopper, stack.copyWithCount(1), false).isEmpty()) {
                    stack.shrink(1);
                    break;
                }
            }
            if (hopper.extractItem(WorkstationBlockEntity.OUTPUT, 1, false).is(item("range_module_mk2"))) made[0]++;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(made[0] >= 3, "only " + made[0] + " Mk2 made; inputs " + java.util.stream.IntStream.range(0, WorkstationBlockEntity.INPUTS)
                    .mapToObj(i -> station.items().getStackInSlot(i).toString()).toList());
            helper.setBlock(core.east(), Blocks.AIR); // arenas keep what a test leaves
            helper.setBlock(core, Blocks.AIR);
        });
    }
}
