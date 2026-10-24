package dev.bastion.research;

import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** The world's research (PLAN 8 "Riset & Turret Codex"): one set of node ids for the whole world, kept with the overworld. */
public final class ResearchData extends SavedData {
    private static final String NAME = "bastion_research";
    private final Set<String> researched;

    private ResearchData(Set<String> ids) {
        researched = new LinkedHashSet<>(ids);
    }

    public static ResearchData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(ResearchData::load, () -> {
            ResearchData data = new ResearchData(initial(overworld.getGameTime(), server instanceof GameTestServer));
            data.setDirty(); // saved at once, so a new world is never taken for an old one on its next load
            return data;
        }, NAME);
    }

    /**
     * What a world starts with the first time it meets research: a world that ran before (0.5.x or older, game time past 0)
     * gets every node there is now; a new world nothing. The GameTest world gets everything, so the older tests run as before.
     */
    public static Set<String> initial(long overworldGameTime, boolean gameTestServer) {
        if (overworldGameTime <= 0 && !gameTestServer) return Set.of();
        return ResearchTree.ALL.stream().map(ResearchNode::id).collect(Collectors.toSet());
    }

    private static ResearchData load(CompoundTag tag) {
        Set<String> ids = new HashSet<>();
        for (Tag id : tag.getList("Researched", Tag.TAG_STRING)) ids.add(id.getAsString());
        return new ResearchData(ids);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag ids = new ListTag();
        for (String id : researched) ids.add(StringTag.valueOf(id));
        tag.put("Researched", ids);
        return tag;
    }

    public boolean has(String id) {
        return researched.contains(id);
    }

    public Set<String> all() {
        return Collections.unmodifiableSet(researched);
    }

    public void add(String id) {
        if (researched.add(id)) setDirty();
    }

    public void remove(String id) {
        if (researched.remove(id)) setDirty();
    }

    public void set(Set<String> ids) {
        researched.clear();
        researched.addAll(ids);
        setDirty();
    }
}
