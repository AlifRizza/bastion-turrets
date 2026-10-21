package dev.bastion.menu;

import dev.bastion.hub.HubInterfaceBlock.Status;
import dev.bastion.hub.HubEdit;
import dev.bastion.hub.HubInterfaceBlockEntity;
import dev.bastion.hub.OverwatchLinkItem;
import dev.bastion.network.BastionNetwork;
import dev.bastion.network.HubRoster;
import dev.bastion.registry.BastionMenus;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import dev.bastion.turret.TurretState;
import dev.bastion.turret.targeting.TargetFilter;
import dev.bastion.weapon.WeaponData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Turret Control Hub screen (PLAN "Turret Control Hub"). No slots: every half second the server sends the header and the
 * turrets this player can reach (HubRoster), each turret's targeting only when it changed since the last send (at most
 * FILTERS_PER_SEND per packet). On the server it also carries out what the screen asks.
 */
public class ControlHubMenu extends AbstractContainerMenu {
    public static final int MAX_TURRETS = 256, FILTERS_PER_SEND = 32;
    private static final int SEND_INTERVAL = 10;

    /** The hub as the header shows it; total = turrets in reach before the 256 cap. */
    public record Header(Status status, int tier, int energy, int capacity, int upkeep, int side, int total, BlockPos center) {
        /** Until the first roster arrives. */
        public static final Header LOADING = new Header(Status.UNFORMED, 1, 0, 0, 0, 1, 0, BlockPos.ZERO);

        public void write(FriendlyByteBuf buf) {
            buf.writeEnum(status);
            buf.writeVarInt(tier);
            buf.writeVarInt(energy);
            buf.writeVarInt(capacity);
            buf.writeVarInt(upkeep);
            buf.writeVarInt(side);
            buf.writeVarInt(total);
            buf.writeBlockPos(center);
        }

        public static Header read(FriendlyByteBuf buf) {
            return new Header(buf.readEnum(Status.class), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readBlockPos());
        }
    }

    /** One turret as the hub shows it. */
    public record Entry(BlockPos pos, Item weapon, float health, float maxHealth, TurretState state, boolean enabled,
                        int ammo, int ammoCapacity, int energy, int energyCapacity, boolean linked, String controller) {
        public static Entry of(TurretBaseBlockEntity turret) {
            TurretInventory inventory = turret.inventory();
            int ammo = 0;
            for (int i = 0; i < inventory.ammoSlots(); i++) {
                ItemStack stack = inventory.getStackInSlot(TurretInventory.AMMO_START + i);
                if (inventory.isAmmo(stack)) ammo += stack.getCount();
            }
            WeaponData data = inventory.weaponData();
            int perSlot = data == null ? 64 : BuiltInRegistries.ITEM.getTag(data.ammo())
                    .flatMap(tag -> tag.stream().findFirst()).map(item -> item.value().getMaxStackSize()).orElse(64);
            return new Entry(turret.getBlockPos(), inventory.getStackInSlot(TurretInventory.WEAPON).getItem(), turret.health(),
                    turret.maxHealth(), turret.state(), turret.enabled(), ammo, inventory.ammoSlots() * perSlot, turret.energy(),
                    turret.energyCapacity(), OverwatchLinkItem.fitted(turret), turret.manual() == null ? "" : turret.manual().name());
        }

        public float healthFraction() {
            return maxHealth <= 0 ? 1 : health / maxHealth;
        }

        public boolean energyWeapon() {
            return energyCapacity > 0;
        }

        /** How full, 0-1: FE against the capacitor for energy weapons, else ammo items against the open ammo slots. */
        public float fill() {
            if (energyWeapon()) return energy / (float) energyCapacity;
            return ammoCapacity <= 0 ? 0 : Math.min(1, ammo / (float) ammoCapacity);
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeId(BuiltInRegistries.ITEM, weapon);
            buf.writeFloat(health);
            buf.writeFloat(maxHealth);
            buf.writeEnum(state);
            buf.writeBoolean(enabled);
            buf.writeVarInt(ammo);
            buf.writeVarInt(ammoCapacity);
            buf.writeVarInt(energy);
            buf.writeVarInt(energyCapacity);
            buf.writeBoolean(linked);
            buf.writeUtf(controller);
        }

        public static Entry read(FriendlyByteBuf buf) {
            return new Entry(buf.readBlockPos(), buf.readById(BuiltInRegistries.ITEM), buf.readFloat(), buf.readFloat(),
                    buf.readEnum(TurretState.class), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readBoolean(), buf.readUtf(64));
        }
    }

    private final BlockPos hubPos;
    // Server side.
    @Nullable
    private final HubInterfaceBlockEntity hub;
    @Nullable
    private final ServerPlayer player;
    /** Hash of each turret's targeting as last sent to this screen. */
    private final Map<BlockPos, Integer> sentFilters = new HashMap<>();
    private int sendTimer;
    // Client mirror of the last roster.
    private Header header = Header.LOADING;
    private List<Entry> turrets = List.of();
    private final Map<BlockPos, TargetFilter> filters = new HashMap<>();

    /** Client side: the Interface's position from the open data. */
    public ControlHubMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        super(BastionMenus.CONTROL_HUB.get(), id);
        hubPos = buf.readBlockPos();
        hub = null;
        player = null;
    }

    public ControlHubMenu(int id, Inventory inventory, HubInterfaceBlockEntity hub) {
        super(BastionMenus.CONTROL_HUB.get(), id);
        this.hubPos = hub.getBlockPos();
        this.hub = hub;
        this.player = inventory.player instanceof ServerPlayer serverPlayer ? serverPlayer : null;
    }

    public BlockPos hubPos() {
        return hubPos;
    }

    @Nullable
    public HubInterfaceBlockEntity hub() {
        return hub;
    }

    @Nullable
    public ServerPlayer player() {
        return player;
    }

    /** Server: what the screen gets now. */
    public HubRoster roster() {
        List<TurretBaseBlockEntity> all = hub.visibleTurrets(player);
        List<TurretBaseBlockEntity> shown = all.subList(0, Math.min(MAX_TURRETS, all.size()));
        Map<BlockPos, CompoundTag> changed = new HashMap<>();
        for (TurretBaseBlockEntity turret : shown) {
            if (changed.size() >= FILTERS_PER_SEND) break;
            CompoundTag tag = turret.filter().save();
            Integer hash = tag.hashCode();
            if (!hash.equals(sentFilters.get(turret.getBlockPos()))) {
                changed.put(turret.getBlockPos(), tag);
                sentFilters.put(turret.getBlockPos(), hash);
            }
        }
        Header head = new Header(hub.status(), hub.tier(), hub.energy(), hub.capacity(), hub.upkeep(), hub.side(), all.size(), hub.center());
        return new HubRoster(containerId, head, shown.stream().map(Entry::of).toList(), changed);
    }

    /** Called every tick for the open menu: a roster to the player every SEND_INTERVAL ticks, the first one at once. */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (hub == null || player == null || --sendTimer > 0) return;
        sendTimer = SEND_INTERVAL;
        BastionNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), roster());
    }

    /** Server: opens the turret's own GUI linked to this hub, if the player can reach it through the hub. */
    public boolean openTurret(BlockPos pos) {
        TurretBaseBlockEntity turret = hub == null || player == null ? null : hub.reachable(player, pos);
        if (turret == null) return false;
        BlockPos link = hub.getBlockPos();
        NetworkHooks.openScreen(player, new SimpleMenuProvider((id, inventory, p) -> new TurretMenu(id, inventory, turret, link), turret.getDisplayName()),
                buf -> TurretMenu.writeOpenData(buf, turret, link));
        return true;
    }

    /** Back from a turret opened through the hub: the hub's screen again if the player can still use it, else nothing open. */
    public static void reopen(ServerPlayer player, BlockPos hubPos) {
        if (player.level().getBlockEntity(hubPos) instanceof HubInterfaceBlockEntity hub && hub.usableBy(player)) hub.openMenu(player);
        else player.closeContainer();
    }

    /** Server: one batch change on every listed turret the player can still reach through the hub; how many changed. */
    public int edit(Collection<BlockPos> positions, HubEdit edit) {
        if (hub == null || player == null || !edit.valid()) return 0;
        int changed = 0;
        for (BlockPos pos : new LinkedHashSet<>(positions)) {
            TurretBaseBlockEntity turret = hub.reachable(player, pos);
            if (turret != null && edit.apply(turret)) changed++;
        }
        return changed;
    }

    /** Client: the server's latest roster. */
    public void apply(HubRoster roster) {
        header = roster.header();
        turrets = roster.turrets();
        roster.filters().forEach((pos, tag) -> filters.put(pos, TargetFilter.load(tag)));
    }

    public Header header() {
        return header;
    }

    public List<Entry> turrets() {
        return turrets;
    }

    /** Client: the targeting of the turret at {@code pos} as last received (null before it arrives). */
    @Nullable
    public TargetFilter filter(BlockPos pos) {
        return filters.get(pos);
    }

    /** The server closes it once the player cannot use the hub; the client never does on its own. */
    @Override
    public boolean stillValid(Player player) {
        return hub == null || hub.usableBy(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
