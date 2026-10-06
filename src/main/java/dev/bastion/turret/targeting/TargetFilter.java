package dev.bastion.turret.targeting;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.Tags;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Who a turret may shoot, set in the Targeting tab (PLAN 4.7). Categories first, then per-entity-type
 * rules override them (whitelist = always, blacklist = never); players follow the player mode.
 * Defaults: hostiles and bosses only, never the owner, team mates or trusted players.
 */
public final class TargetFilter {
    public enum Category {HOSTILE, NEUTRAL, PASSIVE, PLAYERS, BOSS}

    public enum PlayerMode {
        /** Every player in range. */
        ALL,
        /** Everyone but the owner. */
        ALL_EXCEPT_OWNER,
        /** Everyone but the owner, the owner's team and trusted players. */
        ALL_EXCEPT_TRUSTED
    }

    public enum Rule {ALWAYS, NEVER}

    public enum Priority {NEAREST, LOWEST_HEALTH, HIGHEST_THREAT}

    private final boolean[] categories = {true, false, false, false, true};
    public PlayerMode playerMode = PlayerMode.ALL_EXCEPT_TRUSTED;
    public Priority priority = Priority.NEAREST;
    /** Lower-case player names. */
    public final Set<String> trusted = new TreeSet<>();
    public final Map<ResourceLocation, Rule> rules = new TreeMap<>();

    public boolean category(Category category) {
        return categories[category.ordinal()];
    }

    public void setCategory(Category category, boolean on) {
        categories[category.ordinal()] = on;
    }

    public boolean isTrusted(Player player, @Nullable UUID owner) {
        if (player.getUUID().equals(owner)) return true;
        if (trusted.contains(player.getGameProfile().getName().toLowerCase(Locale.ROOT))) return true;
        if (owner == null) return false;
        Player ownerPlayer = player.level().getPlayerByUUID(owner);
        return ownerPlayer != null && ownerPlayer.getTeam() != null && ownerPlayer.isAlliedTo(player);
    }

    public boolean test(LivingEntity entity, @Nullable UUID owner) {
        if (entity instanceof Player player) {
            if (player.isCreative() || player.isSpectator() || !category(Category.PLAYERS)) return false;
            return switch (playerMode) {
                case ALL -> true;
                case ALL_EXCEPT_OWNER -> !player.getUUID().equals(owner);
                case ALL_EXCEPT_TRUSTED -> !isTrusted(player, owner);
            };
        }
        Rule rule = rules.get(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
        if (rule != null) return rule == Rule.ALWAYS;
        return category(categoryOf(entity));
    }

    public static Category categoryOf(LivingEntity entity) {
        if (entity.getType().is(Tags.EntityTypes.BOSSES)) return Category.BOSS;
        if (entity instanceof Enemy) return Category.HOSTILE;
        if (entity instanceof NeutralMob) return Category.NEUTRAL;
        return Category.PASSIVE;
    }

    // --- persistence and sync -------------------------------------------------------------------

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        for (Category c : Category.values()) tag.putBoolean(c.name(), category(c));
        tag.putString("PlayerMode", playerMode.name());
        tag.putString("Priority", priority.name());
        ListTag names = new ListTag();
        trusted.forEach(n -> names.add(StringTag.valueOf(n)));
        tag.put("Trusted", names);
        CompoundTag ruleTag = new CompoundTag();
        rules.forEach((id, r) -> ruleTag.putString(id.toString(), r.name()));
        tag.put("Rules", ruleTag);
        return tag;
    }

    public static TargetFilter load(CompoundTag tag) {
        TargetFilter f = new TargetFilter();
        if (tag.isEmpty()) return f;
        for (Category c : Category.values()) {
            if (tag.contains(c.name())) f.setCategory(c, tag.getBoolean(c.name()));
        }
        f.playerMode = parse(PlayerMode.class, tag.getString("PlayerMode"), PlayerMode.ALL_EXCEPT_TRUSTED);
        f.priority = parse(Priority.class, tag.getString("Priority"), Priority.NEAREST);
        tag.getList("Trusted", Tag.TAG_STRING).forEach(t -> f.trusted.add(t.getAsString()));
        CompoundTag ruleTag = tag.getCompound("Rules");
        for (String key : ruleTag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            Rule rule = parse(Rule.class, ruleTag.getString(key), null);
            if (id != null && rule != null) f.rules.put(id, rule);
        }
        return f;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeNbt(save());
    }

    public static TargetFilter read(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return tag == null ? new TargetFilter() : load(tag);
    }

    public TargetFilter copy() {
        return load(save());
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String name, E fallback) {
        for (E e : type.getEnumConstants()) {
            if (e.name().equals(name)) return e;
        }
        return fallback;
    }
}
