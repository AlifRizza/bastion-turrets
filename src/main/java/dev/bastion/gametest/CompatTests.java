package dev.bastion.gametest;

import dev.bastion.Bastion;
import dev.bastion.compat.BlockInfo;
import dev.bastion.hub.ControlHubStructure;
import dev.bastion.hub.HubInterfaceBlockEntity;
import dev.bastion.network.WorkstationFill;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.FeedHubBlockEntity;
import dev.bastion.turret.LargeTurretBaseBlock;
import dev.bastion.turret.TurretBaseBlock;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import dev.bastion.turret.TurretTier;
import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponDataLoader;
import dev.bastion.weapon.WeaponStatRows;
import dev.bastion.workstation.WorkstationBlock;
import dev.bastion.workstation.WorkstationBlockEntity;
import dev.bastion.workstation.WorkstationMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/** What JEI and Jade show, tested without either installed (PLAN "Compat JEI & Jade"). */
@GameTestHolder(Bastion.MOD_ID)
@PrefixGameTestTemplate(false)
public class CompatTests {
    private static final String ARENA = "arena", EMPTY = "empty", YARD = "yard";

    /** A component as "key[arg,arg]" (servers have no mod lang): what the tests compare. */
    static String text(Component component) {
        StringBuilder out = new StringBuilder();
        if (component.getContents() instanceof TranslatableContents translatable) {
            out.append(translatable.getKey());
            if (translatable.getArgs().length > 0) {
                out.append(Arrays.stream(translatable.getArgs()).map(arg -> arg instanceof Component c ? text(c) : String.valueOf(arg))
                        .collect(Collectors.joining(",", "[", "]")));
            }
        } else if (component.getContents() instanceof LiteralContents literal) {
            out.append(literal.text());
        }
        for (Component sibling : component.getSiblings()) out.append(text(sibling));
        return out.toString();
    }

    private static List<String> keys(List<WeaponStatRows.Row> rows) {
        return rows.stream().map(WeaponStatRows.Row::key).toList();
    }

    private static String value(List<WeaponStatRows.Row> rows, String key) {
        return rows.stream().filter(r -> r.key().equals(key)).findFirst().orElseThrow().value();
    }

    /** The Info tab's table, now shared with JEI: same rows, same order, same text as before the move. */
    @GameTest(template = EMPTY, batch = "statRowsMatchTheInfoTab")
    public static void statRowsMatchTheInfoTab(GameTestHelper helper) {
        WeaponData gun = WeaponDataLoader.get(Bastion.id("gun"));
        StatSheet stats = StatSheet.of(gun, TurretTier.T1);
        List<WeaponStatRows.Row> left = WeaponStatRows.left(gun, stats);
        helper.assertTrue(keys(left).equals(List.of("damage", "fire_rate", "range", "turn_speed", "pierce", "spread", "ammo_save")), "left " + keys(left));
        helper.assertTrue(value(left, "damage").equals("7") && value(left, "fire_rate").equals("1.7/s") && value(left, "range").equals("32")
                && value(left, "pierce").equals("0") && value(left, "spread").equals("0.3°") && value(left, "ammo_save").equals("0%"), "gun left " + left);
        List<WeaponStatRows.Row> right = WeaponStatRows.right(gun, stats, 40, 0.5f);
        helper.assertTrue(keys(right).equals(List.of("heat_per_shot", "max_heat", "cooling", "max_hp", "regen", "ammo")), "right " + keys(right));
        helper.assertTrue(value(right, "max_hp").equals("40") && value(right, "regen").equals("0.5/s") && value(right, "ammo").equals("1.0"), "gun right " + right);

        WeaponData tesla = WeaponDataLoader.get(Bastion.id("tesla"));
        helper.assertTrue(keys(WeaponStatRows.right(tesla, StatSheet.of(tesla, TurretTier.T1), 40, 0))
                .equals(List.of("energy_per_shot", "energy_capacity", "max_input", "max_hp", "regen", "bolts")), "tesla is an energy weapon with bolts");
        WeaponData laser = WeaponDataLoader.get(Bastion.id("laser_rifle"));
        helper.assertTrue(value(WeaponStatRows.left(laser, StatSheet.of(laser, TurretTier.T1)), "pierce").equals("∞"), "the Laser Rifle pierces everything");
        WeaponData missiles = WeaponDataLoader.get(Bastion.id("missile_launcher"));
        List<WeaponStatRows.Row> missileRight = WeaponStatRows.right(missiles, StatSheet.of(missiles, TurretTier.T1), 60, 0);
        helper.assertTrue(missileRight.get(missileRight.size() - 1).key().equals("reload"), "the Missile Launcher shows its reload: " + keys(missileRight));
        helper.assertTrue(WeaponStatRows.fe(3000).equals("3k") && WeaponStatRows.fe(1500).equals("1.5k") && WeaponStatRows.fe(800).equals("800"), "fe()");
        helper.succeed();
    }

    /** Core of the test station; with the front north the station grows west, south and up (as in TurretWorkstationTests). */
    private static final BlockPos STATION = new BlockPos(4, 2, 2);
    private static final ResourceLocation KINETIC = Bastion.id("workstation/ammo_workstation/kinetic_rounds");

    private static WorkstationBlockEntity station(GameTestHelper helper, RegistryObject<WorkstationBlock> block) {
        block.get().placeAt(helper.getLevel(), helper.absolutePos(STATION), Direction.NORTH);
        return (WorkstationBlockEntity) helper.getBlockEntity(STATION);
    }

    /** The fake player two blocks in front of the station with its GUI open and an empty inventory. */
    private static FakePlayer guiOpen(GameTestHelper helper, WorkstationBlockEntity station) {
        FakePlayer player = ControlHubTests.player(helper, STATION.north(2));
        player.getInventory().clearContent();
        player.containerMenu = new WorkstationMenu(1, player.getInventory(), station);
        return player;
    }

    private static void done(GameTestHelper helper, FakePlayer player) {
        player.getInventory().clearContent();
        player.containerMenu = player.inventoryMenu;
        helper.succeed();
    }

    private static int input(WorkstationBlockEntity station, Item item) {
        int n = 0;
        for (int i = 0; i < WorkstationBlockEntity.INPUTS; i++) if (station.items().getStackInSlot(i).is(item)) n += station.items().getStackInSlot(i).getCount();
        return n;
    }

    /** The + button: the recipe gets picked and one craft's ingredients move from the inventory into the inputs. */
    @GameTest(template = ARENA, batch = "fillMovesOneCraft")
    public static void fillMovesOneCraft(GameTestHelper helper) {
        WorkstationBlockEntity station = station(helper, BastionBlocks.AMMO_WORKSTATION);
        FakePlayer player = guiOpen(helper, station);
        player.getInventory().add(new ItemStack(Items.COPPER_INGOT, 10));
        player.getInventory().add(new ItemStack(Items.GUNPOWDER, 10));
        player.getInventory().add(new ItemStack(Items.IRON_NUGGET, 10));
        int crafts = new WorkstationFill(station.getBlockPos(), KINETIC, 1).apply(player);
        helper.assertTrue(crafts == 1 && KINETIC.equals(station.recipeId()), "filled " + crafts + ", recipe " + station.recipeId());
        helper.assertTrue(input(station, Items.COPPER_INGOT) == 1 && input(station, Items.GUNPOWDER) == 1 && input(station, Items.IRON_NUGGET) == 1,
                "inputs should hold one of each");
        helper.assertTrue(player.getInventory().countItem(Items.GUNPOWDER) == 9, "gunpowder left " + player.getInventory().countItem(Items.GUNPOWDER));
        done(helper, player);
    }

    /** Shift + button: as many crafts as the scarcest ingredient allows; what the inputs already hold counts. */
    @GameTest(template = ARENA, batch = "shiftFillsAsManyAsThePlayerHas")
    public static void shiftFillsAsManyAsThePlayerHas(GameTestHelper helper) {
        WorkstationBlockEntity station = station(helper, BastionBlocks.AMMO_WORKSTATION);
        station.selectRecipe(KINETIC);
        station.items().setStackInSlot(0, new ItemStack(Items.COPPER_INGOT, 2));
        FakePlayer player = guiOpen(helper, station);
        player.getInventory().add(new ItemStack(Items.COPPER_INGOT, 10));
        player.getInventory().add(new ItemStack(Items.GUNPOWDER, 5));
        player.getInventory().add(new ItemStack(Items.IRON_NUGGET, 64));
        player.getInventory().add(new ItemStack(Items.IRON_NUGGET, 36));
        int crafts = new WorkstationFill(station.getBlockPos(), KINETIC, WorkstationFill.MAX_CRAFTS).apply(player);
        helper.assertTrue(crafts == 5, "expected 5 crafts (5 gunpowder), got " + crafts);
        helper.assertTrue(input(station, Items.COPPER_INGOT) == 5 && input(station, Items.GUNPOWDER) == 5 && input(station, Items.IRON_NUGGET) == 5,
                "inputs " + input(station, Items.COPPER_INGOT) + "/" + input(station, Items.GUNPOWDER) + "/" + input(station, Items.IRON_NUGGET));
        helper.assertTrue(player.getInventory().countItem(Items.COPPER_INGOT) == 7, "took copper the inputs already had: " + player.getInventory().countItem(Items.COPPER_INGOT));
        helper.assertTrue(player.getInventory().countItem(Items.IRON_NUGGET) == 95 && player.getInventory().countItem(Items.GUNPOWDER) == 0, "wrong amounts taken");
        done(helper, player);
    }

    /** Short of an ingredient: nothing moves (the recipe is still picked, as a click on it in the GUI would). */
    @GameTest(template = ARENA, batch = "fillMovesNothingWhenShort")
    public static void fillMovesNothingWhenShort(GameTestHelper helper) {
        WorkstationBlockEntity station = station(helper, BastionBlocks.AMMO_WORKSTATION);
        FakePlayer player = guiOpen(helper, station);
        player.getInventory().add(new ItemStack(Items.COPPER_INGOT, 10));
        player.getInventory().add(new ItemStack(Items.IRON_NUGGET, 10));
        int crafts = new WorkstationFill(station.getBlockPos(), KINETIC, WorkstationFill.MAX_CRAFTS).apply(player);
        helper.assertTrue(crafts == 0 && input(station, Items.COPPER_INGOT) == 0 && player.getInventory().countItem(Items.COPPER_INGOT) == 10, "moved without gunpowder");
        helper.assertTrue(KINETIC.equals(station.recipeId()), "recipe not picked");
        done(helper, player);
    }

    /** Inputs full of something else (another recipe's leftovers): no room for one craft, so nothing moves or vanishes. */
    @GameTest(template = ARENA, batch = "fillMovesNothingWhenItDoesNotFit")
    public static void fillMovesNothingWhenItDoesNotFit(GameTestHelper helper) {
        WorkstationBlockEntity station = station(helper, BastionBlocks.AMMO_WORKSTATION);
        for (int i = 0; i < 5; i++) station.items().setStackInSlot(i, new ItemStack(Items.DIRT));
        FakePlayer player = guiOpen(helper, station);
        player.getInventory().add(new ItemStack(Items.COPPER_INGOT, 10));
        player.getInventory().add(new ItemStack(Items.GUNPOWDER, 10));
        player.getInventory().add(new ItemStack(Items.IRON_NUGGET, 10));
        int crafts = new WorkstationFill(station.getBlockPos(), KINETIC, 1).apply(player);
        helper.assertTrue(crafts == 0, "filled " + crafts + " into one free slot");
        helper.assertTrue(input(station, Items.DIRT) == 5 && player.getInventory().countItem(Items.COPPER_INGOT) == 10
                && player.getInventory().countItem(Items.GUNPOWDER) == 10 && player.getInventory().countItem(Items.IRON_NUGGET) == 10, "items moved or lost");
        done(helper, player);
    }

    /** Only the station whose GUI is open, and only its own recipes. */
    @GameTest(template = ARENA, batch = "fillRefusesOtherStationsAndClosedGuis")
    public static void fillRefusesOtherStationsAndClosedGuis(GameTestHelper helper) {
        WorkstationBlockEntity station = station(helper, BastionBlocks.AMMO_WORKSTATION);
        FakePlayer player = guiOpen(helper, station);
        player.getInventory().add(new ItemStack(Items.IRON_INGOT, 10));
        player.getInventory().add(new ItemStack(Items.COPPER_INGOT, 10));
        player.getInventory().add(new ItemStack(Items.GUNPOWDER, 10));
        player.getInventory().add(new ItemStack(Items.IRON_NUGGET, 10));
        ResourceLocation barrel = Bastion.id("workstation/part_workstation/gun_barrel");
        helper.assertTrue(new WorkstationFill(station.getBlockPos(), barrel, 1).apply(player) == 0 && station.recipeId() == null,
                "an Ammo Workstation took a Part Workstation recipe");
        helper.assertTrue(new WorkstationFill(station.getBlockPos().east(5), KINETIC, 1).apply(player) == 0, "filled a station whose GUI is not open");
        player.containerMenu = player.inventoryMenu;
        helper.assertTrue(new WorkstationFill(station.getBlockPos(), KINETIC, 1).apply(player) == 0, "filled with no GUI open");
        helper.assertTrue(input(station, Items.COPPER_INGOT) == 0 && player.getInventory().countItem(Items.COPPER_INGOT) == 10, "items moved");
        done(helper, player);
    }

    private static List<String> lines(BlockEntity entity, boolean details) {
        return BlockInfo.lines(BlockInfo.write(entity), details).stream().map(CompatTests::text).toList();
    }

    /** Turret: weapon and tier, HP, state; Shift adds modules and heat (no owner line when the name is unknown, no target). */
    @GameTest(template = ARENA, batch = "turretInfoLines")
    public static void turretInfoLines(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 2, 3), BastionBlocks.TURRET_BASE.get());
        TurretBaseBlockEntity turret = (TurretBaseBlockEntity) helper.getBlockEntity(new BlockPos(3, 2, 3));
        turret.setOwner(UUID.randomUUID()); // nobody online or cached by that id
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.GUN_TURRET.get()));
        turret.inventory().setStackInSlot(TurretInventory.MODIFIER_START, new ItemStack(BastionItems.RANGE_MODULE.get()));
        List<String> compact = lines(turret, false);
        String hp = WeaponStatRows.fmt(turret.health()), max = WeaponStatRows.fmt(turret.maxHealth());
        helper.assertTrue(compact.equals(List.of("jade.bastion.turret.weapon[item.bastion.gun_turret,gui.bastion.turret.tier[1]]",
                "gui.bastion.turret.hp[" + hp + "," + max + "]",
                "jade.bastion.state[gui.bastion.state." + turret.state().name().toLowerCase(Locale.ROOT) + "]")), "compact " + compact);
        List<String> details = lines(turret, true);
        helper.assertTrue(details.subList(0, 3).equals(compact), "Shift should only add lines: " + details);
        helper.assertTrue(details.subList(3, details.size()).equals(List.of("jade.bastion.modules[item.bastion.range_module]",
                "gui.bastion.turret.heat[0," + WeaponStatRows.fmt(StatSheet.of(turret.inventory().weaponData(), TurretTier.T1).maxHeat()) + "]")),
                "details " + details);
        helper.succeed();
    }

    /** An empty base says so; no heat line without a weapon. */
    @GameTest(template = ARENA, batch = "unarmedTurretSaysNoWeapon")
    public static void unarmedTurretSaysNoWeapon(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 2, 3), BastionBlocks.TURRET_BASE.get());
        List<String> details = lines(helper.getBlockEntity(new BlockPos(3, 2, 3)), true);
        helper.assertTrue(details.get(0).equals("jade.bastion.turret.weapon[gui.bastion.info.no_weapon,gui.bastion.turret.tier[1]]"), "first line " + details);
        helper.assertTrue(details.stream().noneMatch(line -> line.startsWith("gui.bastion.turret.heat")), "heat line without a weapon: " + details);
        helper.succeed();
    }

    /** Workstation: pick a recipe, then what it makes, then the progress while it works. */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "workstationInfoLines")
    public static void workstationInfoLines(GameTestHelper helper) {
        WorkstationBlockEntity station = station(helper, BastionBlocks.AMMO_WORKSTATION);
        helper.assertTrue(lines(station, false).equals(List.of("gui.bastion.workstation.status.pick")), "no recipe: " + lines(station, false));
        station.selectRecipe(KINETIC);
        helper.assertTrue(lines(station, false).equals(List.of("jade.bastion.workstation.recipe[item.bastion.kinetic_rounds]")), "picked: " + lines(station, false));
        helper.setBlock(STATION.east(), BastionBlocks.CREATIVE_POWER_SOURCE.get());
        station.items().setStackInSlot(0, new ItemStack(Items.COPPER_INGOT));
        station.items().setStackInSlot(1, new ItemStack(Items.GUNPOWDER));
        station.items().setStackInSlot(2, new ItemStack(Items.IRON_NUGGET));
        helper.succeedWhen(() -> {
            helper.assertTrue(station.progress() > 0, "not working yet");
            List<String> working = lines(station, false);
            helper.assertTrue(working.size() == 2 && working.get(1).equals("gui.bastion.workstation.status.working[" + station.progress() * 100 / 40 + "]"),
                    "working: " + working);
        });
    }

    /** Feed Hub: how many turrets it feeds. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "feedHubInfoLines")
    public static void feedHubInfoLines(GameTestHelper helper) {
        BlockPos hubPos = new BlockPos(5, 3, 3);
        helper.setBlock(hubPos, BastionBlocks.FEED_HUB.get());
        helper.setBlock(hubPos.east(), BastionBlocks.TURRET_BASE.get().defaultBlockState().setValue(TurretBaseBlock.FACING, Direction.EAST));
        ((TurretBaseBlockEntity) helper.getBlockEntity(hubPos.east())).inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.GUN_TURRET.get()));
        helper.runAtTickTime(5, () -> {
            FeedHubBlockEntity hub = (FeedHubBlockEntity) helper.getBlockEntity(hubPos);
            helper.assertTrue(hub.feeds().size() == 1, "the hub should feed the one turret on it, feeds " + hub.feeds().size());
            helper.assertTrue(lines(hub, false).equals(List.of("jade.bastion.feed_hub.feeds[1]")), "lines " + lines(hub, false));
            helper.succeed();
        });
    }

    /** Control Hub: status, then satellite tier, reach and connected turrets, then FE; an unformed Interface only its status. */
    // Yard and a T2 satellite (3x3 chunks), as in ControlHubTests.hubListsOnlyConnectedTurrets: the turrets are surely in reach.
    @GameTest(template = YARD, timeoutTicks = 80, batch = "controlHubInfoLines")
    public static void controlHubInfoLines(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HubInterfaceBlockEntity hub = ControlHubTests.hub(helper, new BlockPos(15, 2, 14), 2);
        hub.setEnergy(100_000);
        TurretBaseBlockEntity first = ControlHubTests.base(level, hub.center().offset(-4, 0, 4), null);
        ControlHubTests.connect(first, hub);
        helper.runAtTickTime(5, () -> {
            List<String> online = lines(hub, false);
            helper.assertTrue(online.equals(List.of("jade.bastion.control_hub.status[jade.bastion.control_hub.status.online]",
                    "gui.bastion.control_hub.summary[2," + hub.side() + ",1]",
                    "gui.bastion.control_hub.fe[" + hub.energy() + "," + hub.capacity() + "," + hub.upkeep() + "]")), "online " + online);
            // A second connected turret shows up within a second, not on every Jade request.
            TurretBaseBlockEntity second = ControlHubTests.base(level, hub.center().offset(4, 0, 4), null);
            ControlHubTests.connect(second, hub);
            helper.assertTrue(hub.connectedCount() == 1, "counted again within the same second");
        });
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(hub.connectedCount() == 2, "count not refreshed after a second: " + hub.connectedCount());
            helper.setBlock(new BlockPos(0, 2, 0), BastionBlocks.HUB_INTERFACE.get());
            helper.assertTrue(lines(helper.getBlockEntity(new BlockPos(0, 2, 0)), true)
                    .equals(List.of("jade.bastion.control_hub.status[jade.bastion.control_hub.status.unformed]")), "unformed Interface");
            helper.succeed();
        });
    }

    /** Jade shows a multiblock's core: every block of a Large Turret Base leads to it, a 1x1 base to itself, stone nowhere. */
    @GameTest(template = ARENA, batch = "largeBasePartsLeadToTheCore")
    public static void largeBasePartsLeadToTheCore(GameTestHelper helper) {
        BlockPos min = helper.absolutePos(new BlockPos(2, 2, 2));
        LargeTurretBaseBlock.placeAt(helper.getLevel(), min, BastionBlocks.LARGE_TURRET_BASE.get());
        BlockPos core = TurretBaseBlock.core(helper.getLevel(), min).getBlockPos();
        for (BlockPos p : List.of(min, min.east(), min.south(), min.south().east())) {
            helper.assertTrue(core.equals(BlockInfo.corePos(helper.getLevel(), p, helper.getLevel().getBlockState(p))), "part " + p + " does not lead to " + core);
        }
        BlockPos single = helper.absolutePos(new BlockPos(6, 2, 2));
        helper.getLevel().setBlock(single, BastionBlocks.TURRET_BASE.get().defaultBlockState(), 3);
        helper.assertTrue(single.equals(BlockInfo.corePos(helper.getLevel(), single, helper.getLevel().getBlockState(single))), "a 1x1 base is its own core");
        BlockPos stone = helper.absolutePos(new BlockPos(6, 2, 5));
        helper.getLevel().setBlock(stone, Blocks.STONE.defaultBlockState(), 3);
        helper.assertTrue(BlockInfo.corePos(helper.getLevel(), stone, helper.getLevel().getBlockState(stone)) == null, "stone has a core");
        helper.succeed();
    }

    /** Every block of a Part Assembler leads to its core. */
    @GameTest(template = ARENA, batch = "workstationPartsLeadToTheCore")
    public static void workstationPartsLeadToTheCore(GameTestHelper helper) {
        WorkstationBlockEntity station = station(helper, BastionBlocks.PART_ASSEMBLER);
        for (BlockPos p : BastionBlocks.PART_ASSEMBLER.get().footprint(station.getBlockPos(), Direction.NORTH)) {
            helper.assertTrue(station.getBlockPos().equals(BlockInfo.corePos(helper.getLevel(), p, helper.getLevel().getBlockState(p))), "block " + p);
        }
        helper.succeed();
    }

    /** Casings, the Power Port and both satellite halves of a formed hub lead to its Interface; a loose casing to nothing. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "hubPartsLeadToTheInterface")
    public static void hubPartsLeadToTheInterface(GameTestHelper helper) {
        HubInterfaceBlockEntity hub = ControlHubTests.hub(helper, new BlockPos(6, 2, 3), 1);
        BlockPos loose = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.getLevel().setBlock(loose, BastionBlocks.HUB_CASING.get().defaultBlockState(), 3);
        helper.succeedWhen(() -> {
            helper.assertTrue(hub.formed(), "not formed");
            List<BlockPos> parts = new ArrayList<>(ControlHubStructure.slots(hub.getBlockPos(), Direction.NORTH));
            parts.add(hub.center().above(2));
            parts.add(hub.center().above(3));
            parts.add(hub.getBlockPos());
            for (BlockPos p : parts) {
                helper.assertTrue(hub.getBlockPos().equals(BlockInfo.corePos(helper.getLevel(), p, helper.getLevel().getBlockState(p))), "part " + p);
            }
            helper.assertTrue(BlockInfo.corePos(helper.getLevel(), loose, helper.getLevel().getBlockState(loose)) == null, "a loose casing has a core");
        });
    }

    /** JEI's ammo slot: an FE-only weapon (empty ammo tag) has none, not Forge's "Empty Tag" barrier; the Gun lists its rounds. */
    @GameTest(template = EMPTY, batch = "feWeaponsHaveNoAmmoItems")
    public static void feWeaponsHaveNoAmmoItems(GameTestHelper helper) {
        helper.assertTrue(BastionItems.TESLA_TURRET.get().ammo().isEmpty(), "the Tesla Coil shows ammo: " + Arrays.toString(BastionItems.TESLA_TURRET.get().ammo().getItems()));
        helper.assertTrue(BastionItems.GUN_TURRET.get().ammo().test(new ItemStack(BastionItems.KINETIC_ROUNDS.get())), "the Gun's ammo slot lacks Kinetic Rounds");
        helper.succeed();
    }

    /** The + button leaves the rest of the inventory alone: an untouched stack stays the very same object, not a copy. */
    @GameTest(template = ARENA, batch = "fillLeavesOtherStacksAlone")
    public static void fillLeavesOtherStacksAlone(GameTestHelper helper) {
        WorkstationBlockEntity station = station(helper, BastionBlocks.AMMO_WORKSTATION);
        FakePlayer player = guiOpen(helper, station);
        player.getInventory().items.set(0, new ItemStack(Items.COPPER_INGOT, 10));
        player.getInventory().items.set(1, new ItemStack(Items.GUNPOWDER, 10));
        player.getInventory().items.set(2, new ItemStack(Items.IRON_NUGGET, 10));
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        player.getInventory().items.set(9, sword);
        int crafts = new WorkstationFill(station.getBlockPos(), KINETIC, 1).apply(player);
        helper.assertTrue(crafts == 1 && player.getInventory().items.get(0).getCount() == 9, "copper not taken");
        helper.assertTrue(player.getInventory().items.get(9) == sword, "an untouched stack was swapped for a copy");
        done(helper, player);
    }
}
