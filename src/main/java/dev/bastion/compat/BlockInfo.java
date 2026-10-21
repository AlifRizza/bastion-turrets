package dev.bastion.compat;

import com.mojang.authlib.GameProfile;
import dev.bastion.hub.ControlHubStructure;
import dev.bastion.hub.HubCasingBlock;
import dev.bastion.hub.HubInterfaceBlock;
import dev.bastion.hub.HubInterfaceBlockEntity;
import dev.bastion.hub.SatelliteUplinkBlock;
import dev.bastion.turret.FeedHubBlock;
import dev.bastion.turret.FeedHubBlockEntity;
import dev.bastion.turret.TurretBaseBlock;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponData;
import dev.bastion.workstation.WorkstationBlock;
import dev.bastion.workstation.WorkstationBlockEntity;
import dev.bastion.workstation.WorkstationRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static dev.bastion.weapon.WeaponStatRows.fmt;

/**
 * What block-info mods show for a Bastion block (PLAN "Compat JEI & Jade"): {@link #write} on the server gathers it into a
 * tag, {@link #lines} on the client turns the tag into lines (Shift adds the details). Jade calls these through
 * dev.bastion.compat.jade; The One Probe can later use the same two.
 */
public final class BlockInfo {
    /** Key of the tag in Jade's server data. */
    public static final String TAG = "BastionInfo";

    private BlockInfo() {
    }

    public static CompoundTag write(@Nullable BlockEntity entity) {
        CompoundTag tag = new CompoundTag();
        if (entity instanceof TurretBaseBlockEntity turret) turret(tag, turret);
        else if (entity instanceof WorkstationBlockEntity station) workstation(tag, station);
        else if (entity instanceof FeedHubBlockEntity hub) {
            tag.putString("Kind", "feed_hub");
            tag.putInt("Feeds", hub.feeds().size());
        } else if (entity instanceof HubInterfaceBlockEntity hub) controlHub(tag, hub);
        return tag;
    }

    private static void turret(CompoundTag tag, TurretBaseBlockEntity turret) {
        tag.putString("Kind", "turret");
        ItemStack weapon = turret.inventory().getStackInSlot(TurretInventory.WEAPON);
        if (!weapon.isEmpty()) tag.putString("Weapon", id(weapon.getItem()));
        tag.putInt("Tier", turret.tier().ordinal() + 1);
        tag.putFloat("Hp", turret.health());
        tag.putFloat("MaxHp", turret.maxHealth());
        tag.putString("State", turret.state().name().toLowerCase(Locale.ROOT));
        if (turret.manual() != null) tag.putString("Controller", turret.manual().name());
        String owner = turret.owner() == null ? null : ownerName(turret.getLevel(), turret.owner());
        if (owner != null) tag.putString("Owner", owner);
        ListTag modules = new ListTag();
        for (int i = 0; i < turret.tier().modifierSlots; i++) {
            ItemStack module = turret.inventory().getStackInSlot(TurretInventory.MODIFIER_START + i);
            if (!module.isEmpty()) modules.add(StringTag.valueOf(id(module.getItem())));
        }
        tag.put("Modules", modules);
        WeaponData data = turret.inventory().weaponData();
        float maxHeat = data == null ? 0 : StatSheet.of(data, turret.tier(), turret.inventory().modifierEffect()).maxHeat();
        if (maxHeat > 0) {
            tag.putFloat("Heat", turret.heat());
            tag.putFloat("MaxHeat", maxHeat);
        }
        LivingEntity target = turret.target();
        if (target != null && target.isAlive()) tag.putString("Target", Component.Serializer.toJson(target.getDisplayName()));
    }

    /** The owner's name if they are online or in the server's profile cache (none on GameTest servers), else null. */
    @Nullable
    private static String ownerName(@Nullable Level level, UUID id) {
        if (level == null) return null;
        Player online = level.getPlayerByUUID(id);
        if (online != null) return online.getGameProfile().getName();
        MinecraftServer server = level.getServer();
        GameProfileCache cache = server == null ? null : server.getProfileCache();
        return cache == null ? null : cache.get(id).map(GameProfile::getName).orElse(null);
    }

    private static void workstation(CompoundTag tag, WorkstationBlockEntity station) {
        tag.putString("Kind", "workstation");
        WorkstationRecipe recipe = station.recipe();
        if (recipe == null) return;
        tag.putString("Recipe", id(recipe.result().getItem()));
        tag.putInt("Progress", station.progress());
        tag.putInt("Time", recipe.time());
    }

    private static void controlHub(CompoundTag tag, HubInterfaceBlockEntity hub) {
        tag.putString("Kind", "control_hub");
        tag.putString("Status", hub.status().name().toLowerCase(Locale.ROOT));
        if (!hub.formed()) return;
        tag.putInt("Tier", hub.tier());
        tag.putInt("Side", hub.side());
        tag.putInt("Turrets", hub.connectedCount());
        tag.putInt("Energy", hub.energy());
        tag.putInt("Capacity", hub.capacity());
        tag.putInt("Upkeep", hub.upkeep());
    }

    public static List<Component> lines(CompoundTag tag, boolean details) {
        List<Component> lines = new ArrayList<>();
        switch (tag.getString("Kind")) {
            case "turret" -> {
                Component weapon = tag.contains("Weapon") ? item(tag.getString("Weapon")).getDescription()
                        : Component.translatable("gui.bastion.info.no_weapon");
                lines.add(Component.translatable("jade.bastion.turret.weapon", weapon, Component.translatable("gui.bastion.turret.tier", tag.getInt("Tier"))));
                lines.add(Component.translatable("gui.bastion.turret.hp", fmt(tag.getFloat("Hp")), fmt(tag.getFloat("MaxHp"))));
                lines.add(Component.translatable("jade.bastion.state", Component.translatable("gui.bastion.state." + tag.getString("State"))));
                if (tag.contains("Controller")) lines.add(Component.translatable("gui.bastion.turret.manual", tag.getString("Controller")));
                if (!details) break;
                if (tag.contains("Owner")) lines.add(Component.translatable("jade.bastion.owner", tag.getString("Owner")));
                ListTag modules = tag.getList("Modules", Tag.TAG_STRING);
                if (!modules.isEmpty()) {
                    MutableComponent names = Component.empty();
                    for (int i = 0; i < modules.size(); i++) {
                        if (i > 0) names.append(", ");
                        names.append(item(modules.getString(i)).getDescription());
                    }
                    lines.add(Component.translatable("jade.bastion.modules", names));
                }
                if (tag.contains("MaxHeat")) lines.add(Component.translatable("gui.bastion.turret.heat", fmt(tag.getFloat("Heat")), fmt(tag.getFloat("MaxHeat"))));
                Component target = tag.contains("Target") ? Component.Serializer.fromJson(tag.getString("Target")) : null;
                if (target != null) lines.add(Component.translatable("jade.bastion.target", target));
            }
            case "workstation" -> {
                if (!tag.contains("Recipe")) {
                    lines.add(Component.translatable("gui.bastion.workstation.status.pick"));
                    break;
                }
                lines.add(Component.translatable("jade.bastion.workstation.recipe", item(tag.getString("Recipe")).getDescription()));
                int progress = tag.getInt("Progress"), time = Math.max(1, tag.getInt("Time"));
                if (progress > 0) lines.add(Component.translatable("gui.bastion.workstation.status.working", progress * 100 / time));
            }
            case "feed_hub" -> lines.add(Component.translatable("jade.bastion.feed_hub.feeds", tag.getInt("Feeds")));
            case "control_hub" -> {
                lines.add(Component.translatable("jade.bastion.control_hub.status", Component.translatable("jade.bastion.control_hub.status." + tag.getString("Status"))));
                if (!tag.contains("Tier")) break;
                lines.add(Component.translatable("gui.bastion.control_hub.summary", tag.getInt("Tier"), tag.getInt("Side"), tag.getInt("Turrets")));
                lines.add(Component.translatable("gui.bastion.control_hub.fe", tag.getInt("Energy"), tag.getInt("Capacity"), tag.getInt("Upkeep")));
            }
            default -> {
            }
        }
        return lines;
    }

    /**
     * The core of the Bastion multiblock {@code pos} belongs to (a core gives its own position): the turret of a base, the
     * station of a workstation block, the Interface of a formed Control Hub's casing, Power Port or satellite half. Null for
     * anything else, a loose hub part included. Works on both sides (Jade calls it on the client).
     */
    @Nullable
    public static BlockPos corePos(Level level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block instanceof TurretBaseBlock) {
            TurretBaseBlockEntity core = TurretBaseBlock.core(level, pos);
            return core == null ? null : core.getBlockPos();
        }
        if (block instanceof WorkstationBlock) {
            WorkstationBlockEntity core = WorkstationBlock.core(level, pos);
            return core == null ? null : core.getBlockPos();
        }
        if (block instanceof HubInterfaceBlock) return pos;
        HubInterfaceBlockEntity hub = null;
        if (block instanceof HubCasingBlock && state.getValue(HubCasingBlock.X) != FeedHubBlock.Part.ALONE) { // casings and Power Ports
            hub = ControlHubStructure.hubAt(level, ControlHubStructure.partCenter(pos, state));
        } else if (block instanceof SatelliteUplinkBlock) { // stands on the cube's top middle block: center + 2 (bottom half), + 3 (top)
            hub = ControlHubStructure.hubAt(level, pos.below(2 + state.getValue(SatelliteUplinkBlock.PART)));
        }
        return hub == null ? null : hub.getBlockPos();
    }

    private static String id(Item item) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
        return key == null ? "minecraft:air" : key.toString();
    }

    private static Item item(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        Item item = key == null ? null : ForgeRegistries.ITEMS.getValue(key);
        return item == null ? Items.AIR : item;
    }
}
