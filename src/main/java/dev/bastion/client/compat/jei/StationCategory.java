package dev.bastion.client.compat.jei;

import dev.bastion.weapon.WeaponStatRows;
import dev.bastion.workstation.WorkstationRecipe;
import dev.bastion.workstation.WorkstationType;
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

import java.util.Arrays;

/** One workstation's recipes in JEI: the counted ingredients, the result, time and FE (PLAN "Compat JEI & Jade"). */
final class StationCategory implements IRecipeCategory<WorkstationRecipe> {
    // Room for two rows of inputs: a datapack recipe may list more than a station's six (it can't be made, but shows whole).
    static final int WIDTH = 160, HEIGHT = 62;
    private static final int ARROW_X = 113, OUTPUT_X = 140, TEXT = 0xFF404040;

    private final RecipeType<WorkstationRecipe> type;
    private final WorkstationType station;
    private final IDrawable background, icon, slot, arrow;

    StationCategory(IGuiHelper gui, RecipeType<WorkstationRecipe> type, WorkstationType station) {
        this.type = type;
        this.station = station;
        background = gui.createBlankDrawable(WIDTH, HEIGHT);
        icon = gui.createDrawableIngredient(VanillaTypes.ITEM_STACK, BastionJeiPlugin.stationItem(station));
        slot = gui.getSlotDrawable();
        arrow = gui.getRecipeArrow();
    }

    @Override
    public RecipeType<WorkstationRecipe> getRecipeType() {
        return type;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("block.bastion." + station.id());
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

    private static int inputX(int i) {
        return (i % 6) * 18 + 1;
    }

    private static int inputY(int i) {
        return (i / 6) * 18 + 1;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, WorkstationRecipe recipe, IFocusGroup focuses) {
        for (int i = 0; i < recipe.ingredients().size(); i++) {
            WorkstationRecipe.Counted counted = recipe.ingredients().get(i);
            builder.addSlot(RecipeIngredientRole.INPUT, inputX(i), inputY(i))
                    .addItemStacks(Arrays.stream(counted.ingredient().getItems()).map(stack -> stack.copyWithCount(counted.count())).toList());
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X + 1, 1).addItemStack(recipe.result());
    }

    @Override
    public void draw(WorkstationRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        for (int i = 0; i < recipe.ingredients().size(); i++) slot.draw(graphics, inputX(i) - 1, inputY(i) - 1);
        slot.draw(graphics, OUTPUT_X, 0);
        arrow.draw(graphics, ARROW_X, 1);
        Font font = Minecraft.getInstance().font;
        int y = recipe.ingredients().size() > 6 ? 40 : 22;
        graphics.drawString(font, Component.translatable("jei.bastion.station.cost", WeaponStatRows.fmt(recipe.time() / 20f), recipe.energy(),
                recipe.energyPerTick()), 0, y, TEXT, false);
        if (station.instant) graphics.drawString(font, Component.translatable("jei.bastion.station.instant"), 0, y + 11, TEXT, false);
    }
}
