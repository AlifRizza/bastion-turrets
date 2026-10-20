package dev.bastion.client.compat.jei;

import dev.bastion.config.BastionConfig;
import dev.bastion.modifier.ModifierEffect;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretTier;
import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponModuleItem;
import dev.bastion.weapon.WeaponStatRows;
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
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

/**
 * "Turret Weapons" in JEI: per weapon the base it mounts on, its ammo (so U on an ammo lists the turrets that fire it) and
 * the turret GUI's Info table at T1 without modules, read from the live weapon data whenever the page is drawn; the
 * ability is in the weapon slot's tooltip.
 */
final class WeaponCategory implements IRecipeCategory<WeaponModuleItem> {
    static final int WIDTH = 176, HEIGHT = 140;
    private static final int COLUMN_W = 84, STATS_Y = 56, LINE = 10, AMMO_X = 89, TEXT = 0xFF202020, MUTED = 0xFF606060;

    private final IDrawable background, icon, slot;

    WeaponCategory(IGuiHelper gui) {
        background = gui.createBlankDrawable(WIDTH, HEIGHT);
        icon = gui.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(BastionItems.GUN_TURRET.get()));
        slot = gui.getSlotDrawable();
    }

    @Override
    public RecipeType<WeaponModuleItem> getRecipeType() {
        return BastionJeiPlugin.WEAPONS;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.bastion.weapons");
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

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, WeaponModuleItem weapon, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 1, 1).addItemStack(new ItemStack(weapon));
        builder.addSlot(RecipeIngredientRole.INPUT, 1, 23)
                .addItemStack(new ItemStack(weapon.large() ? BastionItems.LARGE_TURRET_BASE.get() : BastionItems.TURRET_BASE.get()));
        Ingredient ammo = weapon.ammo();
        if (!ammo.isEmpty()) builder.addSlot(RecipeIngredientRole.INPUT, AMMO_X, 23).addIngredients(ammo);
    }

    @Override
    public void draw(WeaponModuleItem weapon, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        slot.draw(graphics, 0, 0);
        slot.draw(graphics, 0, 22);
        graphics.drawString(font, weapon.getDescription(), 22, 5, TEXT, false);
        graphics.drawString(font, Component.translatable("jei.bastion.weapon.mounts"), 22, 27, MUTED, false);
        WeaponData data = weapon.data();
        if (data == null) return; // removed by a datapack, or not synced yet: nothing more to show
        boolean ammo = !weapon.ammo().isEmpty();
        if (ammo) slot.draw(graphics, AMMO_X - 1, 22);
        graphics.drawString(font, Component.translatable(ammo ? "jei.bastion.weapon.ammo" : "jei.bastion.weapon.fe_only"), ammo ? AMMO_X + 20 : AMMO_X, 27, MUTED, false);
        graphics.drawString(font, Component.translatable("jei.bastion.weapon.base_stats"), 0, STATS_Y - 12, MUTED, false);
        StatSheet stats = StatSheet.of(data, TurretTier.T1);
        column(graphics, font, WeaponStatRows.left(data, stats), 0);
        column(graphics, font, WeaponStatRows.right(data, stats, TurretBaseBlockEntity.maxHealth(TurretTier.T1, weapon.large(), ModifierEffect.NONE),
                BastionConfig.regenPerSecond(TurretTier.T1)), WIDTH - COLUMN_W);
        // The ability is in the weapon's own tooltip; drawn here it ran past the page (Mortar: 11 lines in id_id), and the page
        // has to stay short enough for a 1080p screen at GUI scale 4.
        graphics.drawString(font, Component.translatable("jei.bastion.weapon.ability_hint"), 0, STATS_Y + 7 * LINE + 4, MUTED, false);
    }

    private static void column(GuiGraphics graphics, Font font, List<WeaponStatRows.Row> rows, int x) {
        for (int i = 0; i < rows.size(); i++) {
            int y = STATS_Y + i * LINE;
            String value = rows.get(i).value();
            int valueWidth = font.width(value);
            graphics.drawString(font, Language.getInstance().getVisualOrder(
                    font.substrByWidth(Component.translatable("gui.bastion.info." + rows.get(i).key()), COLUMN_W - valueWidth - 4)), x, y, MUTED, false);
            graphics.drawString(font, value, x + COLUMN_W - valueWidth, y, TEXT, false);
        }
    }
}
