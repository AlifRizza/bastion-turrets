package dev.bastion.weapon;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.bastion.Bastion;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads data/<ns>/turret_weapons/*.json on the server. Clients receive the same map through
 * WeaponDataSync, so tooltips, slot validation and the Info tab agree with the server.
 */
public class WeaponDataLoader extends SimpleJsonResourceReloadListener {
    private static volatile Map<ResourceLocation, WeaponData> weapons = Map.of();

    public WeaponDataLoader() {
        super(new Gson(), "turret_weapons");
    }

    @Nullable
    public static WeaponData get(ResourceLocation id) {
        return weapons.get(id);
    }

    public static Map<ResourceLocation, WeaponData> all() {
        return weapons;
    }

    /** Client side: replaces the map with the one the server sent. */
    public static void accept(Map<ResourceLocation, WeaponData> synced) {
        weapons = Map.copyOf(synced);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller profiler) {
        Map<ResourceLocation, WeaponData> loaded = new HashMap<>();
        files.forEach((id, json) -> WeaponData.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error -> Bastion.LOGGER.error("Bad turret weapon {}: {}", id, error))
                .ifPresent(data -> loaded.put(id, data)));
        weapons = Map.copyOf(loaded);
        Bastion.LOGGER.info("Loaded {} turret weapons", loaded.size());
    }
}
