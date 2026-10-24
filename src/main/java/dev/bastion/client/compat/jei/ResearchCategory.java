package dev.bastion.client.compat.jei;

import dev.bastion.registry.BastionItems;
import dev.bastion.research.Research;
import dev.bastion.research.ResearchNode;
import dev.bastion.research.ResearchTree;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * "Research" in JEI (PLAN 8 "Riset & Turret Codex"): per node its cost (inputs, so R on an iron ingot lists what it pays
 * for), what it unlocks (outputs, so U on a turret shows its research), and its slice of the tree: what it needs and what
 * it leads to. Costs are the page's (the server's last sync, or the mod's defaults before it; see ResearchPage).
 */
final class ResearchCategory implements IRecipeCategory<ResearchPage> {
    static final int WIDTH = 162, HEIGHT = 86;
    private static final int UNLOCK_X = 90, STATUS_Y = 38, NEEDS_Y = 50, LEADS_Y = 68, ROW_X = 52, TEXT = 0xFF404040;

    private final IDrawable background, icon, slot, arrow;

    ResearchCategory(IGuiHelper gui) {
        background = gui.createBlankDrawable(WIDTH, HEIGHT);
        icon = gui.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(BastionItems.TURRET_CODEX.get()));
        slot = gui.getSlotDrawable();
        arrow = gui.getRecipeArrow();
    }

    @Override
    public RecipeType<ResearchPage> getRecipeType() {
        return BastionJeiPlugin.RESEARCH;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.bastion.research");
    }

    @Override
    @SuppressWarnings({"deprecation", "removal"})
    public IDrawable getBackground() { // older JEI 15.20 builds size the page from it
        return background;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    private static List<Item> needs(ResearchNode node) {
        List<Item> needs = new ArrayList<>();
        for (String id : node.parents()) needs.add(ResearchTree.get(id).icon());
        for (String id : node.requires()) needs.add(ResearchTree.get(id).icon());
        return needs;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ResearchPage page, IFocusGroup focuses) {
        ResearchNode node = page.node();
        List<ItemStack> cost = page.cost();
        for (int i = 0; i < cost.size() && i < 8; i++) builder.addSlot(RecipeIngredientRole.INPUT, 1 + i % 4 * 18, 1 + i / 4 * 18).addItemStack(cost.get(i));
        List<Item> unlocks = node.unlockItems();
        for (int i = 0; i < unlocks.size() && i < 8; i++) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, UNLOCK_X + 1 + i % 4 * 18, 1 + i / 4 * 18).addItemStack(new ItemStack(unlocks.get(i)));
        }
        List<Item> needs = needs(node);
        for (int i = 0; i < needs.size() && i < 6; i++) builder.addSlot(RecipeIngredientRole.RENDER_ONLY, ROW_X + i * 18, NEEDS_Y).addItemStack(new ItemStack(needs.get(i)));
        List<ResearchNode> children = ResearchTree.children(node.id());
        for (int i = 0; i < children.size() && i < 6; i++) {
            builder.addSlot(RecipeIngredientRole.RENDER_ONLY, ROW_X + i * 18, LEADS_Y).addItemStack(new ItemStack(children.get(i).icon()));
        }
    }

    @Override
    public void draw(ResearchPage page, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        ResearchNode node = page.node();
        int costs = Math.min(8, page.cost().size()), unlocks = Math.min(8, node.unlockItems().size());
        for (int i = 0; i < costs; i++) slot.draw(graphics, i % 4 * 18, i / 4 * 18);
        for (int i = 0; i < unlocks; i++) slot.draw(graphics, UNLOCK_X + i % 4 * 18, i / 4 * 18);
        arrow.draw(graphics, 74 - 8, 10);
        Font font = Minecraft.getInstance().font;
        graphics.drawString(font, Component.translatable("jei.bastion.research.needs"), 0, NEEDS_Y + 4, TEXT, false);
        graphics.drawString(font, Component.translatable("jei.bastion.research.leads_to"), 0, LEADS_Y + 4, TEXT, false);
        Minecraft mc = Minecraft.getInstance();
        boolean done = mc.level != null && Research.researched(mc.level, node.id());
        graphics.drawString(font, Component.translatable(done ? "gui.bastion.codex.status.researched" : "jei.bastion.research.not_yet"), 0, STATUS_Y, done ? 0xFF1E8FB0 : 0xFFB03030, false);
    }
}
