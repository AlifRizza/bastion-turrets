package dev.bastion.client.screen;

import com.mojang.math.Axis;
import dev.bastion.Bastion;
import dev.bastion.network.BastionNetwork;
import dev.bastion.network.WorkstationAction;
import dev.bastion.workstation.WorkstationBlock;
import dev.bastion.workstation.WorkstationBlockEntity;
import dev.bastion.workstation.WorkstationMenu;
import dev.bastion.workstation.WorkstationRecipe;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Workstation GUI (PLAN Fase 9), all ours: category tabs (one per turret, or module/ammo group) over a recipe list, a
 * slowly turning 3D preview of the selected result with its time and energy, the ingredients with how many are at hand,
 * then the six inputs, progress and output, the energy gauge and (instant stations) a Craft button. Picking a recipe
 * tells the server; the station keeps it for automation.
 */
public class WorkstationScreen extends AbstractContainerScreen<WorkstationMenu> {
    private static final ResourceLocation TEXTURE = Bastion.id("textures/gui/workstation.png");
    private static final int TAB_X = 8, TAB_Y = 18, TAB_STEP = 20;
    private static final int LIST_X = 8, LIST_Y = 40, LIST_W = 98, ROWS = 4, ROW_H = 20;
    private static final int PREVIEW_X = 110, PREVIEW_Y = 40, PREVIEW_W = 68, PREVIEW_H = 84;
    private static final int ING_X = 182, ING_Y = 40, ING_W = 66;
    private static final int ARROW_X = 123, ARROW_Y = 135, ARROW_W = 27;
    private static final int ENERGY_X = 150, ENERGY_Y = 7, ENERGY_W = 98, ENERGY_H = 6;
    private static final int SIDE_X = 180, SIDE_Y = 129, SIDE_W = 68;
    private static final int TEXT = 0xD8E2EC, MUTED = 0x7F8C9A, GREEN = 0x7CFF8A, RED = 0xFF5A4F, CYAN = 0x4FD8FF;

    private List<WorkstationRecipe> recipes = List.of();
    private final List<ResourceLocation> categories = new ArrayList<>();
    private int category, scroll;
    /** What the player just clicked, shown until the station's own state catches up. */
    @Nullable
    private ResourceLocation pending;
    @Nullable
    private FlatButton craftButton;

    public WorkstationScreen(WorkstationMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 256;
        imageHeight = 236;
        titleLabelX = 8;
        titleLabelY = 6;
    }

    @Override
    protected void init() {
        super.init();
        recipes = minecraft.level == null ? List.of() : WorkstationBlockEntity.recipes(minecraft.level, menu.type());
        categories.clear();
        for (WorkstationRecipe recipe : recipes) if (!categories.contains(recipe.category())) categories.add(recipe.category());
        WorkstationRecipe selected = selected();
        category = selected == null ? 0 : Math.max(0, categories.indexOf(selected.category()));
        scroll = selected == null ? 0 : Math.max(0, visible().indexOf(selected) - ROWS + 1); // the selected recipe in view
        craftButton = null;
        if (menu.type().instant) {
            craftButton = addRenderableWidget(new FlatButton(leftPos + SIDE_X, topPos + SIDE_Y, SIDE_W, 18,
                    () -> Component.translatable(hasShiftDown() ? "gui.bastion.workstation.craft_many" : "gui.bastion.workstation.craft"),
                    () -> {
                        WorkstationRecipe recipe = selected();
                        if (recipe != null) BastionNetwork.CHANNEL.sendToServer(WorkstationAction.craft(menu.pos(), recipe.id(), hasShiftDown() ? 8 : 1));
                    }));
        }
    }

    @Nullable
    private WorkstationBlockEntity station() {
        return minecraft.level == null ? null : WorkstationBlock.core(minecraft.level, menu.pos());
    }

    @Nullable
    private WorkstationRecipe selected() {
        WorkstationBlockEntity station = station();
        ResourceLocation id = pending != null ? pending : station == null ? null : station.recipeId();
        if (station != null && pending != null && pending.equals(station.recipeId())) pending = null;
        if (id == null) return null;
        for (WorkstationRecipe recipe : recipes) if (recipe.id().equals(id)) return recipe;
        return null;
    }

    private List<WorkstationRecipe> visible() {
        if (categories.isEmpty()) return List.of();
        ResourceLocation tab = categories.get(Mth.clamp(category, 0, categories.size() - 1));
        return recipes.stream().filter(r -> r.category().equals(tab)).toList();
    }

    /** Ingredients at hand: the inputs, plus the player's inventory for instant stations (which craft from both). */
    private int have(WorkstationRecipe.Counted counted) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < WorkstationBlockEntity.INPUTS; i++) stacks.add(menu.getSlot(i).getItem());
        if (menu.type().instant) stacks.addAll(minecraft.player.getInventory().items);
        return WorkstationBlockEntity.count(counted, stacks);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (craftButton != null) {
            WorkstationRecipe recipe = selected();
            craftButton.active = recipe != null && menu.energy() >= recipe.energy() && recipe.ingredients().stream().allMatch(c -> have(c) >= c.count());
        }
    }

    // --- drawing ------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltips(graphics, mouseX, mouseY);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        WorkstationRecipe selected = selected();
        renderTabs(graphics, mouseX, mouseY);
        renderList(graphics, selected, mouseX, mouseY);
        renderPreview(graphics, selected, partialTick);
        renderIngredients(graphics, selected);
        // progress arrow and energy gauge
        int filled = Math.round(ARROW_W * menu.progress());
        graphics.fill(leftPos + ARROW_X, topPos + ARROW_Y, leftPos + ARROW_X + ARROW_W, topPos + ARROW_Y + 8, FlatButton.DARK);
        graphics.fill(leftPos + ARROW_X, topPos + ARROW_Y, leftPos + ARROW_X + filled, topPos + ARROW_Y + 8, 0xFF000000 | CYAN);
        frame(graphics, ENERGY_X - 1, ENERGY_Y - 1, ENERGY_W + 2, ENERGY_H + 2);
        int energy = menu.capacity() <= 0 ? 0 : Math.round(ENERGY_W * Mth.clamp((float) menu.energy() / menu.capacity(), 0, 1));
        graphics.fill(leftPos + ENERGY_X, topPos + ENERGY_Y, leftPos + ENERGY_X + energy, topPos + ENERGY_Y + ENERGY_H, 0xFF86A8FF);
        if (craftButton != null && !craftButton.active) {
            graphics.fill(craftButton.getX(), craftButton.getY(), craftButton.getX() + craftButton.getWidth(), craftButton.getY() + craftButton.getHeight(), 0x30000000);
        }
    }

    private void renderTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int i = 0; i < categories.size(); i++) {
            int x = leftPos + TAB_X + i * TAB_STEP, y = topPos + TAB_Y;
            boolean active = i == category;
            graphics.fill(x, y, x + 18, y + 18, active ? 0xFF000000 | CYAN : FlatButton.EDGE);
            graphics.fill(x + 1, y + 1, x + 17, y + 17, active ? 0xFF243440 : FlatButton.BG);
            graphics.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(categories.get(i))), x + 1, y + 1);
        }
    }

    private void renderList(GuiGraphics graphics, @Nullable WorkstationRecipe selected, int mouseX, int mouseY) {
        frame(graphics, LIST_X, LIST_Y, LIST_W, ROWS * ROW_H + 2);
        List<WorkstationRecipe> rows = visible();
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows.size() - ROWS));
        for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
            WorkstationRecipe recipe = rows.get(scroll + i);
            int x = leftPos + LIST_X + 1, y = topPos + LIST_Y + 1 + i * ROW_H;
            boolean hover = mouseX >= x && mouseX < x + LIST_W - 6 && mouseY >= y && mouseY < y + ROW_H;
            if (recipe == selected) graphics.fill(x, y, x + LIST_W - 6, y + ROW_H, 0xFF243440);
            else if (hover) graphics.fill(x, y, x + LIST_W - 6, y + ROW_H, 0xFF20262E);
            if (recipe == selected) graphics.fill(x, y, x + 1, y + ROW_H, 0xFF000000 | CYAN);
            graphics.renderItem(recipe.result(), x + 2, y + 2);
            fit(graphics, recipe.result().getHoverName(), x + 21, y + 6, LIST_W - 6 - 23, recipe == selected ? TEXT : MUTED);
        }
        if (rows.size() > ROWS) { // scrollbar
            int track = ROWS * ROW_H, thumb = Math.max(8, track * ROWS / rows.size());
            int top = (track - thumb) * scroll / Math.max(1, rows.size() - ROWS);
            int x = leftPos + LIST_X + LIST_W - 4, y = topPos + LIST_Y + 1;
            graphics.fill(x, y, x + 2, y + track, FlatButton.DARK);
            graphics.fill(x, y + top, x + 2, y + top + thumb, FlatButton.LIGHT);
        }
    }

    private void renderPreview(GuiGraphics graphics, @Nullable WorkstationRecipe selected, float partialTick) {
        frame(graphics, PREVIEW_X, PREVIEW_Y, PREVIEW_W, PREVIEW_H);
        if (selected == null) {
            fit(graphics, Component.translatable("gui.bastion.workstation.pick"), leftPos + PREVIEW_X + 4, topPos + PREVIEW_Y + 38, PREVIEW_W - 8, MUTED);
            return;
        }
        // The result, big: 3D models turn slowly the way they look in a frame; flat icons face the viewer.
        float time = (minecraft.level == null ? 0 : minecraft.level.getGameTime()) + partialTick;
        boolean model = minecraft.getItemRenderer().getModel(selected.result(), minecraft.level, null, 0).isGui3d();
        graphics.pose().pushPose();
        graphics.pose().translate(leftPos + PREVIEW_X + PREVIEW_W / 2f, topPos + PREVIEW_Y + 34, 150);
        graphics.pose().scale(46, -46, 46);
        if (model) {
            graphics.pose().mulPose(Axis.XP.rotationDegrees(-18));
            graphics.pose().mulPose(Axis.YP.rotationDegrees(time * 1.6f));
        }
        minecraft.getItemRenderer().renderStatic(selected.result(), model ? ItemDisplayContext.FIXED : ItemDisplayContext.GUI, LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, graphics.pose(), graphics.bufferSource(), minecraft.level, 0);
        graphics.flush();
        graphics.pose().popPose();
        if (selected.result().getCount() > 1) {
            fit(graphics, Component.literal("x" + selected.result().getCount()), leftPos + PREVIEW_X + 4, topPos + PREVIEW_Y + 4, 30, TEXT);
        }
        Component cost = Component.translatable("gui.bastion.workstation.cost", seconds(selected.time()), fe(selected.energy()));
        fit(graphics, cost, leftPos + PREVIEW_X + 4, topPos + PREVIEW_Y + PREVIEW_H - 11, PREVIEW_W - 8, MUTED);
    }

    private void renderIngredients(GuiGraphics graphics, @Nullable WorkstationRecipe selected) {
        frame(graphics, ING_X, ING_Y, ING_W, ROWS * ROW_H + 2);
        if (selected == null) return;
        List<WorkstationRecipe.Counted> ingredients = selected.ingredients();
        for (int i = 0; i < ingredients.size() && i < ROWS; i++) {
            WorkstationRecipe.Counted counted = ingredients.get(i);
            int x = leftPos + ING_X + 2, y = topPos + ING_Y + 1 + i * ROW_H;
            ItemStack[] options = counted.ingredient().getItems();
            if (options.length > 0) { // tags: cycle through what fits, once a second
                int pick = (int) ((minecraft.level == null ? 0 : minecraft.level.getGameTime()) / 20 % options.length);
                graphics.renderItem(options[pick], x, y + 2);
            }
            int have = have(counted);
            fit(graphics, Component.literal(Math.min(have, 999) + "/" + counted.count()), x + 19, y + 6, ING_W - 23, have >= counted.count() ? GREEN : RED);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        fit(graphics, title, titleLabelX, titleLabelY, ENERGY_X - 6 - titleLabelX, TEXT);
        if (!menu.type().instant) { // timed stations: what the station is doing instead of a Craft button
            fit(graphics, status(), SIDE_X, SIDE_Y + 6, SIDE_W, MUTED);
        }
    }

    private Component status() {
        WorkstationRecipe recipe = selected();
        if (recipe == null) return Component.translatable("gui.bastion.workstation.status.pick");
        if (menu.progress() > 0) return Component.translatable("gui.bastion.workstation.status.working", Math.round(menu.progress() * 100));
        if (menu.energy() < recipe.energyPerTick()) return Component.translatable("gui.bastion.workstation.status.power");
        if (!menu.getSlot(WorkstationBlockEntity.OUTPUT).getItem().isEmpty()
                && !ItemStack.isSameItemSameTags(menu.getSlot(WorkstationBlockEntity.OUTPUT).getItem(), recipe.result())) {
            return Component.translatable("gui.bastion.workstation.status.full");
        }
        return Component.translatable("gui.bastion.workstation.status.missing");
    }

    private void renderTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = mouseX - leftPos, y = mouseY - topPos;
        if (y >= TAB_Y && y < TAB_Y + 18 && x >= TAB_X) {
            int i = (x - TAB_X) / TAB_STEP;
            if (i < categories.size() && (x - TAB_X) % TAB_STEP < 18) {
                graphics.renderTooltip(font, BuiltInRegistries.ITEM.get(categories.get(i)).getDescription(), mouseX, mouseY);
            }
        } else if (x >= ENERGY_X - 1 && x < ENERGY_X + ENERGY_W + 1 && y >= ENERGY_Y - 1 && y < ENERGY_Y + ENERGY_H + 1) {
            graphics.renderTooltip(font, Component.translatable("gui.bastion.workstation.energy",
                    String.format("%,d", menu.energy()), String.format("%,d", menu.capacity())), mouseX, mouseY);
        } else if (x >= ING_X && x < ING_X + 20 && y >= ING_Y && y < ING_Y + ROWS * ROW_H) {
            WorkstationRecipe recipe = selected();
            int i = (y - ING_Y - 1) / ROW_H;
            if (recipe != null && i < recipe.ingredients().size()) {
                ItemStack[] options = recipe.ingredients().get(i).ingredient().getItems();
                if (options.length > 0) graphics.renderTooltip(font, options[(int) (minecraft.level.getGameTime() / 20 % options.length)], mouseX, mouseY);
            }
        } else if (x >= LIST_X && x < LIST_X + LIST_W - 6 && y >= LIST_Y + 1 && y < LIST_Y + 1 + ROWS * ROW_H) {
            int i = scroll + (y - LIST_Y - 1) / ROW_H;
            List<WorkstationRecipe> rows = visible();
            if (i < rows.size()) graphics.renderTooltip(font, rows.get(i).result(), mouseX, mouseY);
        }
    }

    private void frame(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(leftPos + x, topPos + y, leftPos + x + w, topPos + y + h, FlatButton.EDGE);
        graphics.fill(leftPos + x + 1, topPos + y + 1, leftPos + x + w - 1, topPos + y + h - 1, FlatButton.DARK);
    }

    /** Draws text scaled down (never up) to fit maxWidth, so no translation runs into its neighbour. */
    private void fit(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int colour) {
        int width = font.width(text);
        if (width <= maxWidth) {
            graphics.drawString(font, text, x, y, colour, false);
            return;
        }
        float scale = maxWidth / (float) width;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y + (1 - scale) * 4, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, text, 0, 0, colour, false);
        graphics.pose().popPose();
    }

    private static String seconds(int ticks) {
        float s = ticks / 20f;
        return s == (int) s ? (int) s + "s" : String.format("%.1fs", s);
    }

    private static String fe(int energy) {
        return energy >= 1000 ? (energy % 1000 == 0 ? energy / 1000 + "k" : String.format("%.1fk", energy / 1000f)) : String.valueOf(energy);
    }

    // --- input --------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (int) mouseX - leftPos, y = (int) mouseY - topPos;
        if (button == 0 && y >= TAB_Y && y < TAB_Y + 18 && x >= TAB_X) {
            int i = (x - TAB_X) / TAB_STEP;
            if (i < categories.size() && (x - TAB_X) % TAB_STEP < 18) {
                category = i;
                scroll = 0;
                return true;
            }
        }
        if (button == 0 && x >= LIST_X && x < LIST_X + LIST_W - 6 && y >= LIST_Y + 1 && y < LIST_Y + 1 + ROWS * ROW_H) {
            int i = scroll + (y - LIST_Y - 1) / ROW_H;
            List<WorkstationRecipe> rows = visible();
            if (i < rows.size()) {
                pending = rows.get(i).id();
                BastionNetwork.CHANNEL.sendToServer(WorkstationAction.select(menu.pos(), pending));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int x = (int) mouseX - leftPos, y = (int) mouseY - topPos;
        if (x >= LIST_X && x < LIST_X + LIST_W && y >= LIST_Y && y < LIST_Y + ROWS * ROW_H + 2) {
            scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, Math.max(0, visible().size() - ROWS));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
}
