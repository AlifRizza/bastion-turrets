package dev.bastion.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import dev.bastion.Bastion;
import dev.bastion.config.ResearchConfig;
import dev.bastion.network.ResearchRequest;
import dev.bastion.network.ResearchSync;
import dev.bastion.research.Research;
import dev.bastion.research.ResearchData;
import dev.bastion.research.ResearchEvents;
import dev.bastion.research.ResearchNode;
import dev.bastion.research.ResearchShapedRecipe;
import dev.bastion.research.ResearchTree;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.weapon.WeaponModuleItem;
import dev.bastion.workstation.WorkstationBlockEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLModContainer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Research and the Turret Codex (PLAN 8 "Riset & Turret Codex"): the tree's shape, the rules for researching a node, what
 * a node unlocks, new and old worlds, the config. The world's research is one shared set, so every test that changes it
 * puts it back in a finally (each test is its own batch and batches run one after another).
 */
@GameTestHolder(Bastion.MOD_ID)
@PrefixGameTestTemplate(false)
public class ResearchTests {
    private static final String ARENA = "arena";

    /** Runs {@code body} with the world's research set to {@code start}, then puts the world's own set back. */
    static void withResearch(GameTestHelper helper, Set<String> start, Runnable body) {
        ResearchData data = ResearchData.get(helper.getLevel().getServer());
        Set<String> before = new HashSet<>(data.all());
        data.set(start);
        try {
            body.run();
        } finally {
            data.set(before);
        }
    }

    /** A fresh survival player with an empty inventory (a new profile, so no other test's items). */
    static FakePlayer player(GameTestHelper helper) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "researcher"));
        player.getInventory().clearContent();
        return player;
    }

    static Item item(String id) {
        return ForgeRegistries.ITEMS.getValue(Bastion.id(id));
    }

    private static int count(Player player, Item item) {
        return player.getInventory().countItem(item);
    }

    private static String describe(List<ItemStack> stacks) {
        return stacks.stream().map(s -> s.getCount() + " " + ForgeRegistries.ITEMS.getKey(s.getItem())).collect(Collectors.joining(", "));
    }

    /** True when {@code target} is reachable from {@code from} through parents and requires (a cycle when they are equal). */
    private static boolean reaches(String from, String target, Set<String> seen) {
        ResearchNode node = ResearchTree.get(from);
        if (node == null || !seen.add(from)) return false;
        for (String next : node.parents()) if (next.equals(target) || reaches(next, target, seen)) return true;
        for (String next : node.requires()) if (next.equals(target) || reaches(next, target, seen)) return true;
        return false;
    }

    @GameTest(template = ARENA, batch = "treeIsWhole")
    public static void treeIsWhole(GameTestHelper helper) {
        helper.assertTrue(ResearchTree.ALL.size() == 64, "expected 64 nodes, got " + ResearchTree.ALL.size());
        Set<String> ids = new HashSet<>();
        Set<String> cells = new HashSet<>();
        for (ResearchNode node : ResearchTree.ALL) {
            helper.assertTrue(ids.add(node.id()), "two nodes named " + node.id());
            helper.assertTrue(cells.add(node.tab() + " " + node.col() + "," + node.row()), node.id() + " shares its cell");
            helper.assertTrue(!node.unlocks().isEmpty(), node.id() + " unlocks nothing");
            for (String id : node.unlocks()) helper.assertTrue(ForgeRegistries.ITEMS.containsKey(Bastion.id(id)), node.id() + " unlocks unknown item " + id);
        }
        for (ResearchNode node : ResearchTree.ALL) {
            for (String id : node.parents()) {
                ResearchNode parent = ResearchTree.get(id);
                helper.assertTrue(parent != null && parent.tab() == node.tab(), node.id() + ": parent " + id + " missing or in another tab");
            }
            for (String id : node.requires()) {
                ResearchNode required = ResearchTree.get(id);
                helper.assertTrue(required != null && required.tab() != node.tab(), node.id() + ": require " + id + " missing or in its own tab");
            }
            helper.assertTrue(!reaches(node.id(), node.id(), new HashSet<>()), "a cycle runs through " + node.id());
        }
        RegistryAccess access = helper.getLevel().registryAccess();
        for (Recipe<?> recipe : helper.getLevel().getRecipeManager().getRecipes()) {
            ItemStack result = recipe.getResultItem(access);
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(result.getItem());
            if (result.isEmpty() || key == null || !Bastion.MOD_ID.equals(key.getNamespace())) continue;
            helper.assertTrue(!ResearchTree.unlocking(result.getItem()).isEmpty(), "no node unlocks " + key + " (recipe " + recipe.getId() + ")");
        }
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "turretsNeedTheirBase")
    public static void turretsNeedTheirBase(GameTestHelper helper) {
        int turrets = 0;
        for (ResearchNode node : ResearchTree.ALL) {
            if (node.free() || !(node.icon() instanceof WeaponModuleItem module)) continue;
            String base = switch (module.kind()) {
                case STANDARD -> "turret_base";
                case LARGE -> "large_turret_base";
                case FORTRESS -> "fortress_turret_base";
                case MAKESHIFT -> "makeshift_turret_base";
            };
            helper.assertTrue(node.requires().contains(base), node.id() + " should require " + base + ", requires " + node.requires());
            turrets++;
        }
        helper.assertTrue(turrets == 19, "expected 19 researchable turrets, got " + turrets);
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "researchNeedsParentsAndItems")
    public static void researchNeedsParentsAndItems(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        withResearch(helper, Set.of(), () -> helper.assertTrue(Research.research(player(helper), "sniper") == Research.Outcome.LOCKED,
                "the Sniper was researchable without the Gun"));
        withResearch(helper, Set.of("workstations", "turret_base", "gun"), () -> {
            List<ItemStack> cost = Research.cost(level, "sniper");
            helper.assertTrue(describe(cost).equals("16 minecraft:iron_ingot, 8 minecraft:redstone, 4 minecraft:gold_ingot"),
                    "the Sniper should cost R2, costs " + describe(cost));
            FakePlayer player = player(helper);
            player.getInventory().add(new ItemStack(Items.IRON_INGOT, 17));
            helper.assertTrue(Research.research(player, "sniper") == Research.Outcome.MISSING_ITEMS, "researched with only the iron");
            helper.assertTrue(count(player, Items.IRON_INGOT) == 17, "a refused research took items");
            player.getInventory().add(new ItemStack(Items.REDSTONE, 8));
            player.getInventory().add(new ItemStack(Items.GOLD_INGOT, 4));
            helper.assertTrue(Research.research(player, "sniper") == Research.Outcome.DONE, "the Sniper was refused with its parents and items");
            helper.assertTrue(Research.researched(level, "sniper"), "the Sniper is not researched after DONE");
            helper.assertTrue(count(player, Items.IRON_INGOT) == 1 && count(player, Items.REDSTONE) == 0 && count(player, Items.GOLD_INGOT) == 0,
                    "expected exactly the cost taken, left " + count(player, Items.IRON_INGOT) + " iron");
            helper.assertTrue(Research.research(player, "sniper") == Research.Outcome.ALREADY, "researched the Sniper twice");
            helper.assertTrue(Research.research(player, "nope") == Research.Outcome.UNKNOWN, "an unknown node was not UNKNOWN");
        });
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "researchTakesFromSeveralStacks")
    public static void researchTakesFromSeveralStacks(GameTestHelper helper) {
        withResearch(helper, Set.of("workstations", "turret_base"), () -> {
            FakePlayer player = player(helper);
            player.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 5));
            player.getInventory().setItem(5, new ItemStack(Items.IRON_INGOT, 3));
            player.getInventory().offhand.set(0, new ItemStack(Items.COPPER_INGOT, 8));
            player.getInventory().setItem(9, new ItemStack(Items.REDSTONE, 4));
            helper.assertTrue(Research.research(player, "gun") == Research.Outcome.DONE, "the Gun was refused with its cost in several stacks");
            helper.assertTrue(count(player, Items.IRON_INGOT) + count(player, Items.COPPER_INGOT) + count(player, Items.REDSTONE) == 0,
                    "items left over after paying from several stacks");
        });
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "researchTwiceTakesItemsOnce")
    public static void researchTwiceTakesItemsOnce(GameTestHelper helper) {
        withResearch(helper, Set.of("workstations", "turret_base"), () -> {
            FakePlayer player = player(helper);
            player.getInventory().add(new ItemStack(Items.IRON_INGOT, 16));
            player.getInventory().add(new ItemStack(Items.COPPER_INGOT, 16));
            player.getInventory().add(new ItemStack(Items.REDSTONE, 8));
            helper.assertTrue(Research.research(player, "gun") == Research.Outcome.DONE, "the first click failed");
            helper.assertTrue(Research.research(player, "gun") == Research.Outcome.ALREADY, "the second click was not ALREADY");
            helper.assertTrue(count(player, Items.IRON_INGOT) == 8 && count(player, Items.COPPER_INGOT) == 8 && count(player, Items.REDSTONE) == 4,
                    "two clicks took the cost twice");
        });
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "creativeResearchIsFree")
    public static void creativeResearchIsFree(GameTestHelper helper) {
        withResearch(helper, Set.of("workstations"), () -> {
            FakePlayer player = player(helper);
            player.setGameMode(GameType.CREATIVE);
            helper.assertTrue(Research.research(player, "turret_base") == Research.Outcome.DONE, "a creative player had to pay");
        });
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "sharedAmmoOpensWithEither")
    public static void sharedAmmoOpensWithEither(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        withResearch(helper, Set.of("workstations", "turret_base"), () -> {
            helper.assertTrue(!Research.unlocked(level, item("kinetic_rounds")), "Kinetic Rounds open without a gun");
            ResearchData.get(level.getServer()).add("machine_gun");
            helper.assertTrue(Research.unlocked(level, item("kinetic_rounds")), "Kinetic Rounds stay shut after the Machine Gun");
            helper.assertTrue(!Research.unlocked(level, item("charging_station")), "the Charging Station opens without a cell turret");
        });
        withResearch(helper, Set.of("plasma_repeater"), () ->
                helper.assertTrue(Research.unlocked(level, item("charging_station")), "the Charging Station stays shut after the Plasma Repeater"));
        withResearch(helper, Set.of(), () -> {
            helper.assertTrue(Research.unlocked(level, item("cobblestone_turret")), "an early turret is locked");
            helper.assertTrue(Research.unlocked(level, Items.IRON_INGOT), "a vanilla item is locked");
        });
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "newWorldsStartEmpty")
    public static void newWorldsStartEmpty(GameTestHelper helper) {
        Set<String> every = ResearchTree.ALL.stream().map(ResearchNode::id).collect(Collectors.toSet());
        helper.assertTrue(ResearchData.initial(0, false).isEmpty(), "a new world starts with research done");
        helper.assertTrue(ResearchData.initial(100, false).equals(every), "an old world did not get every node");
        helper.assertTrue(ResearchData.initial(0, true).equals(every), "the GameTest world did not get every node");
        helper.succeed();
    }

    /** Research switched off: every recipe opens and every node counts as researched (the rule, without writing the file). */
    @GameTest(template = ARENA, batch = "disabledOpensEverything")
    public static void disabledOpensEverything(GameTestHelper helper) {
        Research.State off = new Research.State(false, Set.of()), on = new Research.State(true, Set.of());
        helper.assertTrue(off.unlocked(item("railgun_turret")), "the Railgun is locked with research off");
        helper.assertTrue(off.researched("javelin"), "a node counts as not researched with research off");
        helper.assertTrue(!off.researched("nope"), "an unknown node counts as researched");
        helper.assertTrue(!on.unlocked(item("railgun_turret")) && !on.researched("javelin"), "a new world with research on has the Railgun");
        helper.succeed();
    }

    /**
     * The config's cost entries (tests never write config/bastion-research.toml: Forge saves each set at once and its file
     * watcher can load an in-between state back after the test put the old value back).
     */
    @GameTest(template = ARENA, batch = "costSkipsBadEntries")
    public static void costSkipsBadEntries(GameTestHelper helper) {
        ItemStack eight = ResearchConfig.parse("minecraft:iron_ingot 8");
        helper.assertTrue(eight.is(Items.IRON_INGOT) && eight.getCount() == 8, "\"minecraft:iron_ingot 8\" parsed as " + eight);
        ItemStack one = ResearchConfig.parse("minecraft:iron_ingot");
        helper.assertTrue(one.is(Items.IRON_INGOT) && one.getCount() == 1, "a missing count is not 1: " + one);
        for (String bad : List.of("minecraft:iron_ingt 8", "minecraft:diamond 0", "minecraft:diamond 5000", "", "minecraft:diamond many")) {
            helper.assertTrue(ResearchConfig.parse(bad).isEmpty(), "\"" + bad + "\" parsed as " + ResearchConfig.parse(bad));
        }
        List<ItemStack> two = ResearchConfig.costOf("gun", List.of("minecraft:iron_ingot 2", "bad:item 1"));
        helper.assertTrue(describe(two).equals("2 minecraft:iron_ingot"), "costs " + describe(two));
        helper.assertTrue(ResearchConfig.costOf("gun", List.of()).isEmpty(), "an empty list costs something");
        FakePlayer player = player(helper);
        player.getInventory().add(new ItemStack(Items.IRON_INGOT, 2));
        helper.assertTrue(Research.pay(player.getInventory(), List.of()) && count(player, Items.IRON_INGOT) == 2, "an empty cost took items");
        helper.assertTrue(Research.pay(player.getInventory(), two) && count(player, Items.IRON_INGOT) == 0, "the 2 iron were not taken");
        helper.assertTrue(!Research.pay(player.getInventory(), two), "paid with nothing left");
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "researchSyncRoundTrips")
    public static void researchSyncRoundTrips(GameTestHelper helper) {
        ResearchSync sent = new ResearchSync(true, Set.of("gun", "sniper"),
                Map.of("gun", List.of(new ItemStack(Items.IRON_INGOT, 8), new ItemStack(Items.REDSTONE, 4)), "sniper", List.of(),
                        "railgun", List.of(new ItemStack(Items.DIAMOND, ResearchConfig.MAX_COUNT))));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        sent.encode(buf);
        ResearchSync got = ResearchSync.decode(buf);
        helper.assertTrue(got.enabled() == sent.enabled() && got.researched().equals(sent.researched()), "flag or nodes changed: " + got);
        helper.assertTrue(got.costs().keySet().equals(sent.costs().keySet()), "cost keys changed: " + got.costs().keySet());
        helper.assertTrue(describe(got.costs().get("gun")).equals("8 minecraft:iron_ingot, 4 minecraft:redstone") && got.costs().get("sniper").isEmpty(),
                "costs changed: " + describe(got.costs().get("gun")));
        helper.assertTrue(describe(got.costs().get("railgun")).equals(ResearchConfig.MAX_COUNT + " minecraft:diamond"),
                "a big count did not survive the trip: " + describe(got.costs().get("railgun")));
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "codexGivenOnce")
    public static void codexGivenOnce(GameTestHelper helper) {
        FakePlayer player = player(helper);
        ResearchEvents.giveCodexOnce(player);
        ResearchEvents.giveCodexOnce(player);
        helper.assertTrue(count(player, item("turret_codex")) == 1, "expected one Codex, got " + count(player, item("turret_codex")));
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "commandGrantsAndRevokes")
    public static void commandGrantsAndRevokes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CommandSourceStack op = level.getServer().createCommandSourceStack().withPermission(2).withSuppressedOutput();
        withResearch(helper, Set.of(), () -> {
            level.getServer().getCommands().performPrefixedCommand(op, "bastion research grant gun");
            helper.assertTrue(Research.researched(level, "gun"), "grant gun did nothing");
            level.getServer().getCommands().performPrefixedCommand(op, "bastion research revoke gun");
            helper.assertTrue(!Research.researched(level, "gun"), "revoke gun did nothing");
            level.getServer().getCommands().performPrefixedCommand(op, "bastion research grant all");
            helper.assertTrue(ResearchTree.ALL.stream().allMatch(n -> Research.researched(level, n.id())), "grant all left nodes out");
            level.getServer().getCommands().performPrefixedCommand(op, "bastion research revoke all");
            helper.assertTrue(!Research.researched(level, "workstations"), "revoke all left Workstations researched");
        });
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "requestPacketResearches")
    public static void requestPacketResearches(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        withResearch(helper, Set.of("workstations"), () -> {
            FakePlayer player = player(helper);
            player.getInventory().add(new ItemStack(Items.IRON_INGOT, 8));
            player.getInventory().add(new ItemStack(Items.COPPER_INGOT, 8));
            player.getInventory().add(new ItemStack(Items.REDSTONE, 4));
            new ResearchRequest("turret_base").apply(player);
            helper.assertTrue(Research.researched(level, "turret_base"), "the request did not research the Turret Base");
        });
        helper.succeed();
    }

    private static final BlockPos CORE = new BlockPos(4, 2, 2);

    private static WorkstationBlockEntity station(GameTestHelper helper) {
        BastionBlocks.PART_WORKSTATION.get().placeAt(helper.getLevel(), helper.absolutePos(CORE), Direction.NORTH);
        return (WorkstationBlockEntity) helper.getBlockEntity(CORE);
    }

    private static final ResourceLocation GUN_BARREL = Bastion.id("workstation/part_workstation/gun_barrel");

    private static Set<String> every() {
        return ResearchTree.ALL.stream().map(ResearchNode::id).collect(Collectors.toSet());
    }

    @GameTest(template = ARENA, batch = "workstationRefusesLockedRecipes")
    public static void workstationRefusesLockedRecipes(GameTestHelper helper) {
        WorkstationBlockEntity station = station(helper);
        withResearch(helper, Set.of("workstations", "turret_base"), () -> {
            station.selectRecipe(GUN_BARREL);
            helper.assertTrue(station.recipeId() == null, "picked the Gun Barrel before the Gun was researched");
            ResearchData.get(helper.getLevel().getServer()).add("gun");
            station.selectRecipe(GUN_BARREL);
            helper.assertTrue(GUN_BARREL.equals(station.recipeId()), "refused the Gun Barrel after the Gun");
        });
        helper.succeed();
    }

    /** A recipe picked while it was open (research switched back on later) is neither made nor filled by JEI's + button. */
    @GameTest(template = ARENA, batch = "lockedSelectedRecipeStops")
    public static void lockedSelectedRecipeStops(GameTestHelper helper) {
        WorkstationBlockEntity station = station(helper);
        withResearch(helper, every(), () -> {
            station.selectRecipe(GUN_BARREL);
            helper.assertTrue(station.recipe() != null, "the Gun Barrel was not picked with everything researched");
            ResearchData.get(helper.getLevel().getServer()).set(Set.of("workstations", "turret_base"));
            helper.assertTrue(station.recipe() == null, "a locked recipe still counts as the station's recipe");
            FakePlayer player = player(helper);
            player.getInventory().add(new ItemStack(Items.IRON_INGOT, 3));
            player.getInventory().add(new ItemStack(Items.COPPER_INGOT, 1));
            helper.assertTrue(station.fill(player, 1) == 0, "filled a locked recipe");
        });
        helper.succeed();
    }

    /** A 3x3 crafting grid holding {@code pattern} (' ' = empty). */
    private static CraftingContainer grid(String[] pattern, Map<Character, Item> key) {
        CraftingContainer grid = new TransientCraftingContainer(new AbstractContainerMenu(null, -1) {
            @Override
            public ItemStack quickMoveStack(Player player, int slot) {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean stillValid(Player player) {
                return true;
            }
        }, 3, 3);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                char c = pattern[row].charAt(col);
                if (c != ' ') grid.setItem(row * 3 + col, new ItemStack(key.get(c)));
            }
        }
        return grid;
    }

    @GameTest(template = ARENA, batch = "stationCraftingNeedsResearch")
    public static void stationCraftingNeedsResearch(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CraftingContainer grid = grid(new String[]{"PSP", "RCR", "III"}, Map.of('P', Items.PISTON, 'S', Items.SMITHING_TABLE, 'R', Items.REDSTONE,
                'C', Items.CRAFTING_TABLE, 'I', Items.IRON_INGOT));
        withResearch(helper, Set.of(), () -> helper.assertTrue(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level).isEmpty(),
                "the crafting table made a Part Workstation before Workstations was researched"));
        withResearch(helper, Set.of("workstations"), () -> helper.assertTrue(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level)
                .map(r -> r.getResultItem(level.registryAccess()).is(item("part_workstation"))).orElse(false), "no Part Workstation after Workstations"));
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "earlyRecipesStayOpen")
    public static void earlyRecipesStayOpen(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CraftingContainer grid = grid(new String[]{"CSC", "ICI", "P P"}, Map.of('C', Items.COBBLESTONE, 'S', Items.STRING, 'I', Items.IRON_INGOT,
                'P', Items.OAK_PLANKS));
        withResearch(helper, Set.of(), () -> helper.assertTrue(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level)
                .map(r -> r.getResultItem(level.registryAccess()).is(item("cobblestone_turret"))).orElse(false), "the Cobblestone Turret needs research"));
        helper.succeed();
    }

    /** Saving config/bastion-research.toml reaches ResearchConfig on the mod bus: bad entries are reported afresh, clients resynced. */
    @GameTest(template = ARENA, batch = "configReloadReachesResearch")
    public static void configReloadReachesResearch(GameTestHelper helper) {
        ResearchConfig.costOf("gun", List.of("bad:reload_probe 1"));
        helper.assertTrue(ResearchConfig.warned().contains("gun bad:reload_probe 1"), "the bad entry was not reported");
        ModConfig config = ConfigTracker.INSTANCE.fileMap().get(ResearchConfig.FILE);
        helper.assertTrue(config != null, "Forge does not track " + ResearchConfig.FILE);
        IEventBus bus = ModList.get().getModContainerById(Bastion.MOD_ID).map(c -> ((FMLModContainer) c).getEventBus()).orElseThrow();
        bus.post(new ModConfigEvent.Reloading(config));
        helper.assertTrue(!ResearchConfig.warned().contains("gun bad:reload_probe 1"), "a saved file never reached ResearchConfig.onReloading");
        helper.succeed();
    }

    /** Before the first sync (and on the server, which never gets one) the client copy is built once, not on every call. */
    @GameTest(template = ARENA, batch = "clientDefaultsAreBuiltOnce")
    public static void clientDefaultsAreBuiltOnce(GameTestHelper helper) {
        helper.assertTrue(Research.client() == Research.client(), "the default client copy is rebuilt on every call");
        helper.assertTrue(describe(Research.client().costs().get("gun")).equals("8 minecraft:iron_ingot, 8 minecraft:copper_ingot, 4 minecraft:redstone"),
                "the default copy has the wrong Gun cost");
        helper.succeed();
    }

    /** Free nodes are always researched: granting or revoking one says so and changes nothing; list counts only real research. */
    @GameTest(template = ARENA, batch = "commandsLeaveFreeNodesAlone")
    public static void commandsLeaveFreeNodesAlone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CommandSourceStack op = level.getServer().createCommandSourceStack().withPermission(2).withSuppressedOutput();
        withResearch(helper, every(), () -> {
            helper.assertTrue(level.getServer().getCommands().performPrefixedCommand(op, "bastion research revoke cobblestone") == 0,
                    "revoking a free node reported success");
            helper.assertTrue(Research.researched(level, "cobblestone"), "a free node stopped being researched");
            long real = ResearchTree.ALL.stream().filter(n -> !n.free()).count();
            int listed = level.getServer().getCommands().performPrefixedCommand(op, "bastion research list");
            helper.assertTrue(listed == real, "list counted " + listed + " with everything researched, expected " + real);
        });
        helper.succeed();
    }

    /** What a dedicated server sends its clients: a station recipe comes back as the same research-locked recipe. */
    @GameTest(template = ARENA, batch = "researchRecipeSurvivesTheNetwork")
    public static void researchRecipeSurvivesTheNetwork(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Recipe<?> sent = level.getRecipeManager().byKey(Bastion.id("part_workstation")).orElseThrow();
        helper.assertTrue(sent instanceof ResearchShapedRecipe, "the Part Workstation recipe is not research_shaped: " + sent.getClass());
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        ResearchShapedRecipe.Serializer serializer = (ResearchShapedRecipe.Serializer) sent.getSerializer();
        serializer.toNetwork(buf, (ResearchShapedRecipe) sent);
        ResearchShapedRecipe got = serializer.fromNetwork(sent.getId(), buf);
        helper.assertTrue(got.getResultItem(level.registryAccess()).is(item("part_workstation")), "the result changed on the way");
        helper.assertTrue(got.getWidth() == 3 && got.getHeight() == 3 && got.getIngredients().size() == sent.getIngredients().size(), "the shape changed");
        for (int i = 0; i < got.getIngredients().size(); i++) {
            helper.assertTrue(got.getIngredients().get(i).toJson().equals(sent.getIngredients().get(i).toJson()), "ingredient " + i + " changed");
        }
        helper.succeed();
    }

    /**
     * User 2026-10-24 16:24: what a card brings with it shows beside it in the tree (left-right), parts left out (they stay
     * on the page): the Control Hub's blocks and Hub Connector, a turret's ammo, the Workstations' other stations.
     */
    @GameTest(template = ARENA, batch = "companionsShowWhatComesAlong")
    public static void companionsShowWhatComesAlong(GameTestHelper helper) {
        java.util.function.Function<String, List<String>> ids = id -> ResearchTree.get(id).companions().stream()
                .map(i -> ForgeRegistries.ITEMS.getKey(i).getPath()).toList();
        helper.assertTrue(ids.apply("control_hub").equals(List.of("hub_casing", "hub_power_port", "satellite_uplink_t1", "satellite_uplink_t2",
                "satellite_uplink_t3", "hub_connector")), "Control Hub companions: " + ids.apply("control_hub"));
        helper.assertTrue(ids.apply("gun").equals(List.of("kinetic_rounds")), "Gun companions: " + ids.apply("gun"));
        helper.assertTrue(ids.apply("laser_rifle").equals(List.of("laser_cell", "empty_laser_cell", "charging_station")), "Laser: " + ids.apply("laser_rifle"));
        helper.assertTrue(ids.apply("workstations").equals(List.of("part_assembler", "ammo_workstation", "module_workstation")), "Workstations: " + ids.apply("workstations"));
        helper.assertTrue(ids.apply("turret_base").equals(List.of("repair_kit", "turret_configurator")), "Turret Base: " + ids.apply("turret_base"));
        helper.assertTrue(ids.apply("large_turret_base").isEmpty() && ids.apply("ion_rifle").isEmpty() && ids.apply("range_module").isEmpty(),
                "parts or nothing should give no companions");
        helper.succeed();
    }
}
