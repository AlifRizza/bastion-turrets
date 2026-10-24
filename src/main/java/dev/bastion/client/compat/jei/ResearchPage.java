package dev.bastion.client.compat.jei;

import dev.bastion.research.Research;
import dev.bastion.research.ResearchNode;
import dev.bastion.research.ResearchTree;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * One JEI Research page: a node with the cost JEI indexed it under (R on a cost item finds it). New pages replace the old
 * ones when the server's costs differ (JeiBridge.refreshResearch), since JEI builds its lookup once.
 */
record ResearchPage(ResearchNode node, List<ItemStack> cost) {
    static List<ResearchPage> all() {
        return ResearchTree.ALL.stream().filter(node -> !node.free())
                .map(node -> new ResearchPage(node, Research.client().costs().getOrDefault(node.id(), List.of()))).toList();
    }

    boolean sameCost(ResearchPage other) {
        if (!node.id().equals(other.node.id()) || cost.size() != other.cost.size()) return false;
        for (int i = 0; i < cost.size(); i++) {
            if (cost.get(i).getItem() != other.cost.get(i).getItem() || cost.get(i).getCount() != other.cost.get(i).getCount()) return false;
        }
        return true;
    }
}
