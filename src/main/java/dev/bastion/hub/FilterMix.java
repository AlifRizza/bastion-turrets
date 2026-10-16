package dev.bastion.hub;

import dev.bastion.turret.targeting.TargetFilter;
import dev.bastion.turret.targeting.TargetFilter.Category;
import dev.bastion.turret.targeting.TargetFilter.PlayerMode;
import dev.bastion.turret.targeting.TargetFilter.Priority;
import dev.bastion.turret.targeting.TargetFilter.Rule;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

/** What the selected turrets' targeting has in common, for the hub's batch settings: a shared value, or null when they differ. */
public final class FilterMix {
    private final List<TargetFilter> filters;

    public FilterMix(Collection<TargetFilter> filters) {
        this.filters = List.copyOf(filters);
    }

    public int size() {
        return filters.size();
    }

    @Nullable
    public Boolean category(Category category) {
        return common(filter -> filter.category(category));
    }

    @Nullable
    public PlayerMode playerMode() {
        return common(filter -> filter.playerMode);
    }

    @Nullable
    public Priority priority() {
        return common(filter -> filter.priority);
    }

    /** Every name some of them trust, with how many trust it. */
    public Map<String, Integer> trusted() {
        Map<String, Integer> names = new TreeMap<>();
        for (TargetFilter filter : filters) filter.trusted.forEach(name -> names.merge(name, 1, Integer::sum));
        return names;
    }

    /** Every mob some of them have a rule for, sorted. */
    public List<ResourceLocation> ruleIds() {
        TreeSet<ResourceLocation> ids = new TreeSet<>();
        for (TargetFilter filter : filters) ids.addAll(filter.rules.keySet());
        return new ArrayList<>(ids);
    }

    /** How many of them have any rule for this mob. */
    public int ruleCount(ResourceLocation id) {
        return (int) filters.stream().filter(filter -> filter.rules.containsKey(id)).count();
    }

    /** The rule all of them have for this mob, or null when they differ or some have none. */
    @Nullable
    public Rule rule(ResourceLocation id) {
        return ruleCount(id) == filters.size() ? common(filter -> filter.rules.get(id)) : null;
    }

    @Nullable
    private <T> T common(Function<TargetFilter, T> value) {
        if (filters.isEmpty()) return null;
        T first = value.apply(filters.get(0));
        for (TargetFilter filter : filters) {
            if (!Objects.equals(value.apply(filter), first)) return null;
        }
        return first;
    }
}
