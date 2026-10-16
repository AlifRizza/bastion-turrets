package dev.bastion.client.screen;

import dev.bastion.turret.targeting.TargetFilter.Category;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Mob types for the targeting rule lists: the turret's Targeting tab and the Control Hub's batch settings. */
final class MobList {
    private static List<EntityType<?>> living;

    private MobList() {
    }

    /** Registered mobs whose id or name contains {@code query}, by name. */
    static List<ResourceLocation> matching(String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<ResourceLocation> rows = new ArrayList<>();
        for (EntityType<?> type : living()) {
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
            if (id != null && (id.toString().contains(q) || type.getDescription().getString().toLowerCase(Locale.ROOT).contains(q))) rows.add(id);
        }
        return rows;
    }

    /** Every registered mob: living entity types are exactly the ones with default attributes. */
    private static List<EntityType<?>> living() {
        if (living == null) {
            living = ForgeRegistries.ENTITY_TYPES.getValues().stream()
                    .filter(type -> type != EntityType.PLAYER && DefaultAttributes.hasSupplier(type))
                    .sorted(Comparator.comparing(type -> type.getDescription().getString()))
                    .toList();
        }
        return living;
    }

    static Component name(ResourceLocation id) {
        return ForgeRegistries.ENTITY_TYPES.containsKey(id) ? ForgeRegistries.ENTITY_TYPES.getValue(id).getDescription() : Component.literal(id.toString());
    }

    /**
     * Category a mob type falls under without a rule. ponytail: approximated from its spawn group, since the real check
     * needs an entity instance; bosses and neutral mobs show as hostile/passive here.
     */
    static Category category(ResourceLocation id) {
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(id)) return Category.PASSIVE;
        return ForgeRegistries.ENTITY_TYPES.getValue(id).getCategory().isFriendly() ? Category.PASSIVE : Category.HOSTILE;
    }
}
