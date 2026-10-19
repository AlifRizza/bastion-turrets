package dev.bastion.weapon;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.bastion.Bastion;
import dev.bastion.config.TurretStatsConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Loads data/<ns>/turret_weapons/*.json on the server. Clients receive the effective map (the JSON with bastion-turrets.toml
 * laid over it) through WeaponDataSync, so tooltips, slot validation and the Info tab agree with the server.
 */
public class WeaponDataLoader extends SimpleJsonResourceReloadListener {
    /** The JSON as loaded (datapacks included), before bastion-turrets.toml; server side only. */
    private static volatile Map<ResourceLocation, WeaponData> raw = Map.of();
    /** What turrets use: raw with the config's numbers laid over it (on clients: what the server sent). */
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

    /**
     * {@code raw} with each Bastion weapon's overrides (looked up by the file name, "sniper") laid over it. Other namespaces pass
     * through untouched; overrides for a weapon that isn't in {@code raw} are simply never used.
     */
    public static Map<ResourceLocation, WeaponData> merge(Map<ResourceLocation, WeaponData> raw,
                                                          Function<String, Map<String, Double>> overrides) {
        Map<ResourceLocation, WeaponData> merged = new HashMap<>();
        raw.forEach((id, data) -> merged.put(id, Bastion.MOD_ID.equals(id.getNamespace()) ? data.withOverrides(overrides.apply(id.getPath())) : data));
        return Map.copyOf(merged);
    }

    /** Lays bastion-turrets.toml over the last loaded JSON again (after a datapack load, or when the file changed). */
    public static void rebuild() {
        weapons = merge(raw, TurretStatsConfig::overrides);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller profiler) {
        Map<ResourceLocation, WeaponData> loaded = new HashMap<>();
        files.forEach((id, json) -> WeaponData.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error -> Bastion.LOGGER.error("Bad turret weapon {}: {}", id, error))
                .ifPresent(data -> loaded.put(id, data)));
        raw = Map.copyOf(loaded);
        rebuild();
        Bastion.LOGGER.info("Loaded {} turret weapons", loaded.size());
    }
}
