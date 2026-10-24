package dev.bastion.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.bastion.Bastion;
import dev.bastion.research.Research;
import dev.bastion.research.ResearchNode;
import dev.bastion.research.ResearchTree;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * config/bastion-research.toml (PLAN 8 "Riset & Turret Codex"): research on or off, the first-join Codex, and what each
 * node costs. Global for every world, like bastion-turrets.toml; a running server sends the numbers to its players.
 */
public final class ResearchConfig {
    public static final String FILE = "bastion-research.toml";
    /** Most of one item a cost entry may ask for. */
    public static final int MAX_COUNT = 4096;
    private static final String[] HEADER = {
            "Bastion research: what each research costs, taken from the player's inventory when they press Research in the Turret Codex.",
            "Each entry is \"namespace:item count\" (count 1 if left out). An unknown item or a count outside 1 ~ " + MAX_COUNT
                    + " is skipped (see the log); an empty list makes that research free.",
            "Changes apply as soon as the file is saved."};

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.BooleanValue GIVE_CODEX;
    public static final Map<String, ForgeConfigSpec.ConfigValue<List<? extends String>>> COSTS = new LinkedHashMap<>();
    /** Bad entries already reported since the file was last loaded (one warning each). */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        ENABLED = b.comment("Recipes need research. false: every recipe is open and the Turret Codex is only a guide.")
                .define("enabled", true);
        GIVE_CODEX = b.comment("Every player gets one Turret Codex the first time they join a world (while research is on).")
                .define("giveCodexOnFirstJoin", true);
        b.comment(HEADER).push("costs");
        Map<String, String> lang = englishNames();
        for (ResearchNode node : ResearchTree.ALL) {
            if (node.free()) continue;
            List<String> cost = node.tier().defaultCost();
            String name = lang.getOrDefault("research.bastion." + node.id(),
                    lang.getOrDefault("item.bastion." + node.unlocks().get(0), lang.getOrDefault("block.bastion." + node.unlocks().get(0), node.id())));
            COSTS.put(node.id(), b.comment(name, "Mod default: " + String.join(", ", cost))
                    .defineListAllowEmpty(List.of(node.id()), () -> cost, o -> o instanceof String));
        }
        b.pop();
        SPEC = b.build();
    }

    private ResearchConfig() {
    }

    /** The bad cost entries reported since the file was last loaded ("<node> <entry>"). */
    public static Set<String> warned() {
        return Collections.unmodifiableSet(WARNED);
    }

    /** True before the file is loaded too (a value read before that throws). */
    public static boolean enabled() {
        return !SPEC.isLoaded() || ENABLED.get();
    }

    public static boolean giveCodex() {
        return !SPEC.isLoaded() || GIVE_CODEX.get();
    }

    /** "namespace:item count" as a stack; {@link ItemStack#EMPTY} when the item is unknown or the count is out of range. */
    public static ItemStack parse(String entry) {
        String[] parts = entry.trim().split("\\s+");
        if (parts.length > 2 || parts[0].isEmpty()) return ItemStack.EMPTY;
        ResourceLocation key = ResourceLocation.tryParse(parts[0]);
        if (key == null || !ForgeRegistries.ITEMS.containsKey(key)) return ItemStack.EMPTY;
        int count = 1;
        if (parts.length == 2) {
            try {
                count = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                return ItemStack.EMPTY;
            }
        }
        if (count < 1 || count > MAX_COUNT) return ItemStack.EMPTY;
        Item item = ForgeRegistries.ITEMS.getValue(key);
        return item == null ? ItemStack.EMPTY : new ItemStack(item, count);
    }

    /** What {@code id} costs now: its entries that parse (each bad one logged once). Unknown and free nodes cost nothing. */
    public static List<ItemStack> cost(String id) {
        ForgeConfigSpec.ConfigValue<List<? extends String>> value = COSTS.get(id);
        return value == null ? List.of() : costOf(id, SPEC.isLoaded() ? value.get() : value.getDefault());
    }

    /** {@code entries} of node {@code id} as stacks, the ones that do not parse left out (each logged once). */
    public static List<ItemStack> costOf(String id, List<? extends String> entries) {
        List<ItemStack> cost = new ArrayList<>();
        for (String entry : entries) {
            ItemStack stack = parse(entry);
            if (!stack.isEmpty()) cost.add(stack);
            else if (WARNED.add(id + " " + entry)) Bastion.LOGGER.warn("{}: research cost \"{}\" of {} skipped (unknown item or count outside 1 ~ {})",
                    FILE, entry, id, MAX_COUNT);
        }
        return cost;
    }

    /** Forge saw the file change (its watcher thread): send the new numbers from the server thread. */
    public static void onReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != SPEC) return;
        WARNED.clear();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) server.execute(() -> Research.syncAll(server));
    }

    /** The mod's English names from the jar, for the file's comments. */
    private static Map<String, String> englishNames() {
        Map<String, String> names = new LinkedHashMap<>();
        try (InputStream in = ResearchConfig.class.getResourceAsStream("/assets/" + Bastion.MOD_ID + "/lang/en_us.json")) {
            if (in == null) return names;
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            json.entrySet().forEach(e -> names.put(e.getKey(), e.getValue().getAsString()));
        } catch (IOException | RuntimeException e) {
            Bastion.LOGGER.warn("{}: could not read the English names for its comments", FILE, e);
        }
        return names;
    }
}
