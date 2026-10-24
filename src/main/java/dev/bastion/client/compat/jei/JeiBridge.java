package dev.bastion.client.compat.jei;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** The Codex's Recipe button (PLAN 8 "Riset & Turret Codex"); only loaded when JEI is, so nothing else touches JEI classes. */
public final class JeiBridge {
    @Nullable
    static IJeiRuntime runtime;
    /** The Research pages JEI holds now. */
    static List<ResearchPage> pages = List.of();

    private JeiBridge() {
    }

    /** After a ResearchSync: when the server's costs differ from the indexed ones, swap in new pages so R finds them. */
    static void refreshResearch() {
        if (runtime == null) return;
        List<ResearchPage> fresh = ResearchPage.all();
        boolean same = fresh.size() == pages.size();
        for (int i = 0; same && i < fresh.size(); i++) same = fresh.get(i).sameCost(pages.get(i));
        if (same) return;
        runtime.getRecipeManager().hideRecipes(BastionJeiPlugin.RESEARCH, pages);
        runtime.getRecipeManager().addRecipes(BastionJeiPlugin.RESEARCH, fresh);
        pages = fresh;
    }

    /** JEI's recipe screen for {@code stack} (as pressing R on it), once JEI has its runtime. */
    public static void showRecipes(ItemStack stack) {
        if (runtime == null) return;
        runtime.getRecipesGui().show(runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, stack));
    }
}
