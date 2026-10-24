package dev.bastion.research;

import dev.bastion.Bastion;
import dev.bastion.workstation.PartItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Set;

/**
 * One card of the research tree (PLAN 8 "Riset & Turret Codex"): where it sits ({@code col}, {@code row}; row 0 at the
 * bottom), what must be researched first ({@code parents} in its own tab, drawn as lines; {@code requires} from other tabs,
 * drawn as small icons), the item ids it unlocks (the first is its icon) and its cost tier (the costs themselves are in
 * config/bastion-research.toml).
 */
public record ResearchNode(String id, Tab tab, int col, int row, List<String> parents, List<String> requires, List<String> unlocks, Tier tier) {
    public enum Tab { TURRET, WORKSHOP, MODULE }

    /** Default costs (spec "Biaya default"); FREE nodes are always researched. */
    public enum Tier {
        FREE(), R1("minecraft:iron_ingot 8", "minecraft:copper_ingot 8", "minecraft:redstone 4"),
        R2("minecraft:iron_ingot 16", "minecraft:redstone 8", "minecraft:gold_ingot 4"),
        R3("minecraft:iron_block 4", "minecraft:gold_ingot 8", "minecraft:diamond 4"),
        R4("minecraft:iron_block 8", "minecraft:diamond 8", "minecraft:netherite_scrap 2");

        private final List<String> cost;

        Tier(String... cost) {
            this.cost = List.of(cost);
        }

        public List<String> defaultCost() {
            return cost;
        }
    }

    /** Nodes that bundle several things and so have a name of their own (research.bastion.<id>) instead of their icon's. */
    private static final Set<String> NAMED = Set.of("workstations", "control_hub");

    public Item icon() {
        return item(unlocks.get(0));
    }

    public List<Item> unlockItems() {
        return unlocks.stream().map(ResearchNode::item).toList();
    }

    /**
     * What the card brings along besides its own item, drawn beside it in the tree (user 2026-10-24 16:24): its unlocks
     * without the parts, which stay in the page's list.
     */
    public List<Item> companions() {
        return unlocks.stream().skip(1).map(ResearchNode::item).filter(item -> !(item instanceof PartItem)).distinct().toList();
    }

    public boolean free() {
        return tier == Tier.FREE;
    }

    public boolean named() {
        return NAMED.contains(id);
    }

    public Component name() {
        return named() ? Component.translatable("research.bastion." + id) : icon().getDescription();
    }

    private static Item item(String id) {
        return ForgeRegistries.ITEMS.getValue(Bastion.id(id));
    }
}
