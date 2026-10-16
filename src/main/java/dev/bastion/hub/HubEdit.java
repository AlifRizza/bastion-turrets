package dev.bastion.hub;

import dev.bastion.network.TurretConfigUpdate;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.targeting.TargetFilter;
import dev.bastion.turret.targeting.TargetFilter.Category;
import dev.bastion.turret.targeting.TargetFilter.PlayerMode;
import dev.bastion.turret.targeting.TargetFilter.Priority;
import dev.bastion.turret.targeting.TargetFilter.Rule;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * One change from the Control Hub's batch settings (PLAN "Atur massal"): a single targeting setting, or on/off, applied to
 * every selected turret on its own, so the rest of each turret's settings stays as it was. The screen applies it to its
 * copies at once; the server checks it (valid) before touching the real turrets.
 *
 * <p>CATEGORY: key = category name, value 1 on / 0 off. PLAYER_MODE, PRIORITY: value = ordinal. TRUSTED_ADD /
 * TRUSTED_REMOVE: key = player name. RULE: key = entity type id, value = Rule ordinal or -1 to remove. ENABLED: value 1 / 0.
 */
public record HubEdit(Kind kind, String key, int value) {
    public enum Kind {CATEGORY, PLAYER_MODE, PRIORITY, TRUSTED_ADD, TRUSTED_REMOVE, RULE, ENABLED}

    /** The names the turret GUI's trusted box takes. */
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    public static HubEdit category(Category category, boolean on) {
        return new HubEdit(Kind.CATEGORY, category.name(), on ? 1 : 0);
    }

    public static HubEdit playerMode(PlayerMode mode) {
        return new HubEdit(Kind.PLAYER_MODE, "", mode.ordinal());
    }

    public static HubEdit priority(Priority priority) {
        return new HubEdit(Kind.PRIORITY, "", priority.ordinal());
    }

    public static HubEdit trust(String name, boolean add) {
        return new HubEdit(add ? Kind.TRUSTED_ADD : Kind.TRUSTED_REMOVE, name, 0);
    }

    /** {@code rule} null removes the mob's rule. */
    public static HubEdit rule(ResourceLocation id, @Nullable Rule rule) {
        return new HubEdit(Kind.RULE, id.toString(), rule == null ? -1 : rule.ordinal());
    }

    public static HubEdit enabled(boolean on) {
        return new HubEdit(Kind.ENABLED, "", on ? 1 : 0);
    }

    /** Whether this is a change the turret GUI could have made itself (server check for packets). */
    public boolean valid() {
        return switch (kind) {
            case CATEGORY -> Arrays.stream(Category.values()).anyMatch(c -> c.name().equals(key)) && (value == 0 || value == 1);
            case PLAYER_MODE -> value >= 0 && value < PlayerMode.values().length;
            case PRIORITY -> value >= 0 && value < Priority.values().length;
            case TRUSTED_ADD, TRUSTED_REMOVE -> NAME.matcher(key).matches();
            case RULE -> {
                ResourceLocation id = ResourceLocation.tryParse(key);
                yield id != null && ForgeRegistries.ENTITY_TYPES.containsKey(id) && value >= -1 && value < Rule.values().length;
            }
            case ENABLED -> value == 0 || value == 1;
        };
    }

    /** Applies a targeting change to {@code filter} (a valid edit); false when nothing changed or a list is full. */
    public boolean applyTo(TargetFilter filter) {
        switch (kind) {
            case CATEGORY -> {
                Category category = Category.valueOf(key);
                if (filter.category(category) == (value != 0)) return false;
                filter.setCategory(category, value != 0);
                return true;
            }
            case PLAYER_MODE -> {
                PlayerMode mode = PlayerMode.values()[value];
                if (filter.playerMode == mode) return false;
                filter.playerMode = mode;
                return true;
            }
            case PRIORITY -> {
                Priority priority = Priority.values()[value];
                if (filter.priority == priority) return false;
                filter.priority = priority;
                return true;
            }
            case TRUSTED_ADD -> {
                return filter.trusted.size() < TurretConfigUpdate.MAX_TRUSTED && filter.trusted.add(key.toLowerCase(Locale.ROOT));
            }
            case TRUSTED_REMOVE -> {
                return filter.trusted.remove(key.toLowerCase(Locale.ROOT));
            }
            case RULE -> {
                ResourceLocation id = new ResourceLocation(key);
                if (value < 0) return filter.rules.remove(id) != null;
                Rule rule = Rule.values()[value];
                if (filter.rules.get(id) == rule) return false;
                if (!filter.rules.containsKey(id) && filter.rules.size() >= TurretConfigUpdate.MAX_RULES) return false;
                filter.rules.put(id, rule);
                return true;
            }
            default -> {
                return false; // ENABLED is the turret's switch, not a targeting setting
            }
        }
    }

    /** Applies it to a turret (server); false when nothing changed. */
    public boolean apply(TurretBaseBlockEntity turret) {
        if (kind == Kind.ENABLED) {
            if (turret.enabled() == (value != 0)) return false;
            turret.setEnabled(value != 0);
            return true;
        }
        TargetFilter filter = turret.filter().copy();
        if (!applyTo(filter)) return false;
        turret.setFilter(filter);
        return true;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeEnum(kind);
        buf.writeUtf(key, 64);
        buf.writeVarInt(value);
    }

    public static HubEdit read(FriendlyByteBuf buf) {
        return new HubEdit(buf.readEnum(Kind.class), buf.readUtf(64), buf.readVarInt());
    }
}
