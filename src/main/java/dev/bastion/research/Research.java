package dev.bastion.research;

import dev.bastion.config.ResearchConfig;
import dev.bastion.network.BastionNetwork;
import dev.bastion.network.ResearchSync;
import dev.bastion.registry.BastionSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The research rules (PLAN 8 "Riset & Turret Codex"). A node can be researched once its parents and requires are; it costs
 * the items in the config, taken from the player's inventory (creative: free). An item is unlocked when no node unlocks it
 * or one that does is researched, and a recipe is locked while its result is not unlocked.
 */
public final class Research {
    public enum Outcome { DONE, DISABLED, UNKNOWN, ALREADY, LOCKED, MISSING_ITEMS }

    /** What one side knows: whether research is on, and the nodes researched in this world. */
    public record State(boolean enabled, Set<String> researched) {
        /** Free nodes, and every node while research is off, count as researched; an unknown id never does. */
        public boolean researched(String id) {
            ResearchNode node = ResearchTree.get(id);
            return node != null && (node.free() || !enabled || researched.contains(id));
        }

        public boolean unlocked(Item item) {
            List<ResearchNode> nodes = ResearchTree.unlocking(item);
            if (nodes.isEmpty() || !enabled) return true;
            for (ResearchNode node : nodes) if (researched(node.id())) return true;
            return false;
        }

        /** Not researched yet, and every parent and require is. */
        public boolean available(ResearchNode node) {
            if (researched(node.id())) return false;
            for (String id : node.parents()) if (!researched(id)) return false;
            for (String id : node.requires()) if (!researched(id)) return false;
            return true;
        }
    }

    /** The client's copy: the last ResearchSync from the server (common code, so no client classes). */
    @Nullable
    private static volatile ResearchSync client;

    private Research() {
    }

    /** The server's state (the config switch and the world's data), or on the client what the server last sent. */
    public static State state(Level level) {
        if (level.isClientSide) return new State(client().enabled(), client().researched());
        MinecraftServer server = level.getServer();
        return new State(ResearchConfig.enabled(), server == null ? Set.of() : ResearchData.get(server).all());
    }

    /** What the client knows; before the first sync: research on, nothing researched, the mod's default costs. */
    public static ResearchSync client() {
        ResearchSync sync = client;
        return sync != null ? sync : Defaults.SYNC;
    }

    /** True once the server's first ResearchSync has arrived (until logout). */
    public static boolean clientSynced() {
        return client != null;
    }

    /** Built on first use, after the item registry is filled (class initialisation is thread-safe). */
    private static final class Defaults {
        static final ResearchSync SYNC = build();

        private static ResearchSync build() {
            Map<String, List<ItemStack>> costs = new LinkedHashMap<>();
            for (ResearchNode node : ResearchTree.ALL) if (!node.free()) costs.put(node.id(), ResearchConfig.costOf(node.id(), node.tier().defaultCost()));
            return new ResearchSync(true, Set.of(), Map.copyOf(costs));
        }
    }

    /** Runs on the client thread after each ResearchSync (JEI re-indexes changed costs); null clears it. */
    @Nullable
    private static volatile Runnable clientSyncListener;

    public static void onClientSync(@Nullable Runnable listener) {
        clientSyncListener = listener;
    }

    public static void acceptClient(@Nullable ResearchSync sync) {
        client = sync;
        Runnable listener = clientSyncListener;
        if (sync != null && listener != null) listener.run();
    }

    public static boolean researched(Level level, String id) {
        return state(level).researched(id);
    }

    public static boolean unlocked(Level level, Item item) {
        return state(level).unlocked(item);
    }

    public static boolean available(Level level, ResearchNode node) {
        return state(level).available(node);
    }

    public static List<ItemStack> cost(Level level, String id) {
        return level.isClientSide ? client().costs().getOrDefault(id, List.of()) : ResearchConfig.cost(id);
    }

    public static Outcome research(ServerPlayer player, String id) {
        Level level = player.level();
        State state = state(level);
        if (!state.enabled()) return Outcome.DISABLED;
        ResearchNode node = ResearchTree.get(id);
        if (node == null) return Outcome.UNKNOWN;
        if (state.researched(id)) return Outcome.ALREADY;
        if (!state.available(node)) return Outcome.LOCKED;
        if (!player.isCreative() && !pay(player.getInventory(), cost(level, id))) return Outcome.MISSING_ITEMS;
        ResearchData.get(player.server).add(id);
        onResearched(player, node);
        return Outcome.DONE;
    }

    /** Takes {@code cost} from anywhere in the inventory (offhand and armour too), only when all of it is there. */
    public static boolean pay(Inventory inventory, List<ItemStack> cost) {
        Map<Item, Integer> need = new LinkedHashMap<>();
        for (ItemStack stack : cost) need.merge(stack.getItem(), stack.getCount(), Integer::sum);
        for (Map.Entry<Item, Integer> e : need.entrySet()) if (inventory.countItem(e.getKey()) < e.getValue()) return false;
        for (Map.Entry<Item, Integer> e : need.entrySet()) {
            int left = e.getValue();
            for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!stack.is(e.getKey())) continue;
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
            }
        }
        inventory.setChanged();
        return true;
    }

    /** After a research: every player gets the new state and a chat line, the researcher a chime. */
    private static void onResearched(ServerPlayer player, ResearchNode node) {
        syncAll(player.server);
        player.server.getPlayerList().broadcastSystemMessage(Component.translatable("chat.bastion.research.done", player.getDisplayName(), node.name()), false);
        player.playNotifySound(BastionSounds.RESEARCH_COMPLETE.get(), SoundSource.PLAYERS, 1, 1);
    }

    public static void syncAll(MinecraftServer server) {
        BastionNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), ResearchSync.current(server));
    }
}
