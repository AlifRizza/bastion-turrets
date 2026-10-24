package dev.bastion.client.screen;

import com.mojang.math.Axis;
import dev.bastion.Bastion;
import dev.bastion.registry.BastionItems;
import dev.bastion.research.Research;
import dev.bastion.research.ResearchNode;
import dev.bastion.research.ResearchTree;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponDataLoader;
import dev.bastion.weapon.WeaponModuleItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * The Codex's right panel (PLAN 8 "Riset & Turret Codex"): the selected node's name, its item turning in 3D (drag turns it),
 * for a turret its base, ammo and three stat bars, the item's own description, what the node unlocks and still needs, and at
 * the bottom the research block (costs with what the player carries). The middle scrolls when it is taller than the panel.
 */
final class CodexPage {
    static final int TEXT = 0xFFD8E2EC, MUTED = 0xFF7F8C9A;
    private static final int TITLE_H = 14, MODEL_H = 58, ROW = 18, BUTTON_H = 16, COST_CELL = 50;

    private int x, y, w, h;
    private int scroll, contentH;
    private float dragYaw;
    @Nullable
    private ItemStack hovered;
    /** The middle's lower edge as last drawn (over the bottom block). */
    private int lastBottom;

    void layout(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    void reset() {
        scroll = 0;
        dragYaw = 0;
    }

    boolean inside(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    boolean onModel(double mx, double my) {
        return mx >= x && mx < x + w && my >= y + TITLE_H && my < y + TITLE_H + MODEL_H;
    }

    void turn(double dx) {
        dragYaw += (float) dx * 2;
    }

    void scroll(double delta) {
        scroll = Mth.clamp(scroll - (int) (delta * 12), 0, Math.max(0, contentH - bodyHeight(lastBottom)));
    }

    /** The bottom button row's top (Research, and Recipe when JEI is there). */
    int buttonY() {
        return y + h - BUTTON_H - 2;
    }

    int left() {
        return x + 4;
    }

    int width() {
        return w - 8;
    }

    @Nullable
    ItemStack hovered() {
        return hovered;
    }

    private int bodyHeight(int bottom) {
        return Math.max(0, bottom - (y + TITLE_H + MODEL_H));
    }

    void render(GuiGraphics g, ResearchNode node, Research.State state, List<ItemStack> cost, Player player, boolean buttons,
                long now, float partialTick, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        hovered = null;
        fit(g, font, node.name(), x + 4, y + 4, w - 8, TEXT);
        model(g, mc, new ItemStack(node.icon()), now + partialTick);

        // Bottom block, fixed over the button row: the costs with what the player carries (names on hover), or why there are none.
        int bottom = buttons ? buttonY() - 2 : y + h - 2, base = bottom;
        if (!state.enabled()) {
            bottom -= 11;
            fit(g, font, Component.translatable("gui.bastion.codex.disabled"), x + 4, bottom + 1, w - 8, MUTED);
        } else if (!node.free() && !state.researched(node.id()) && !cost.isEmpty()) {
            int perRow = Math.max(1, (w - 8) / COST_CELL);
            bottom -= (cost.size() + perRow - 1) / perRow * ROW;
            for (int i = 0; i < cost.size(); i++) {
                ItemStack stack = cost.get(i);
                int cx = x + 4 + i % perRow * COST_CELL, cy = bottom + i / perRow * ROW + 1;
                int have = player.getInventory().countItem(stack.getItem());
                boolean enough = player.isCreative() || have >= stack.getCount();
                icon(g, stack, cx, cy, mouseX, mouseY);
                fit(g, font, Component.literal(Math.min(have, 9999) + "/" + stack.getCount()), cx + 18, cy + 5, COST_CELL - 20, enough ? TEXT : CodexTree.RED);
            }
            bottom -= 11;
            fit(g, font, Component.translatable("gui.bastion.codex.cost"), x + 4, bottom + 1, w - 8, MUTED);
        }
        if (bottom < base) g.fill(x + 4, bottom - 2, x + w - 4, bottom - 1, FlatButton.EDGE);
        lastBottom = bottom - 3;

        // The middle, scrolled and clipped between the model and the bottom block.
        int top = y + TITLE_H + MODEL_H, cursor = top - scroll;
        g.enableScissor(x, top, x + w, lastBottom);
        cursor = line(g, font, CodexTree.status(state, node), cursor, TEXT);
        if (node.icon() instanceof WeaponModuleItem module) cursor = weapon(g, font, module, cursor, mouseX, mouseY, top, lastBottom);
        for (Component text : description(node, player)) cursor = line(g, font, text, cursor, MUTED);
        cursor = icons(g, font, Component.translatable("gui.bastion.codex.unlocks"), node.unlockItems(), cursor, mouseX, mouseY, top, lastBottom);
        List<Item> needs = new ArrayList<>();
        for (String id : node.parents()) if (!state.researched(id)) needs.add(ResearchTree.get(id).icon());
        for (String id : node.requires()) if (!state.researched(id)) needs.add(ResearchTree.get(id).icon());
        if (!needs.isEmpty()) cursor = icons(g, font, Component.translatable("gui.bastion.codex.needs"), needs, cursor, mouseX, mouseY, top, lastBottom);
        g.disableScissor();
        contentH = cursor + scroll - top;
        int body = bodyHeight(lastBottom);
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentH - body));
        if (contentH > body && body > 0) { // a thin scrollbar: there is more below
            int thumb = Math.max(6, body * body / contentH), at = (body - thumb) * scroll / Math.max(1, contentH - body);
            g.fill(x + w - 3, top, x + w - 1, lastBottom, FlatButton.DARK);
            g.fill(x + w - 3, top + at, x + w - 1, top + at + thumb, FlatButton.LIGHT);
        }
    }

    private void model(GuiGraphics g, Minecraft mc, ItemStack stack, float time) {
        boolean model = mc.getItemRenderer().getModel(stack, mc.level, null, 0).isGui3d();
        g.pose().pushPose();
        g.pose().translate(x + w / 2f, y + TITLE_H + MODEL_H / 2f, 150);
        float scale = model ? 54 : 40;
        g.pose().scale(scale, -scale, scale);
        if (model) {
            g.pose().mulPose(Axis.XP.rotationDegrees(-18));
            g.pose().mulPose(Axis.YP.rotationDegrees(time * 1f + dragYaw));
        }
        mc.getItemRenderer().renderStatic(stack, model ? ItemDisplayContext.FIXED : ItemDisplayContext.GUI, LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, g.pose(), g.bufferSource(), mc.level, 0);
        g.flush();
        g.pose().popPose();
    }

    /** Base size and surfaces ("2x2 · Large Turret Base · floor only"), ammo, and damage / shots per second / range. */
    private int weapon(GuiGraphics g, Font font, WeaponModuleItem module, int cursor, int mouseX, int mouseY, int top, int bottom) {
        Item base = switch (module.kind()) {
            case STANDARD -> BastionItems.TURRET_BASE.get();
            case LARGE -> BastionItems.LARGE_TURRET_BASE.get();
            case FORTRESS -> BastionItems.FORTRESS_TURRET_BASE.get();
            case MAKESHIFT -> BastionItems.MAKESHIFT_TURRET_BASE.get();
        };
        String size = switch (module.kind()) {
            case LARGE -> "2x2";
            case FORTRESS -> "3x3";
            default -> "1x1";
        };
        cursor = line(g, font, Component.translatable("gui.bastion.codex.base", size, base.getDescription(),
                Component.translatable(module.floorOnly() ? "gui.bastion.codex.surface.floor" : "gui.bastion.codex.surface.any")), cursor, TEXT);
        List<Item> ammo = Arrays.stream(module.ammo().getItems()).map(ItemStack::getItem).filter(i -> i != BastionItems.CREATIVE_AMMO.get()).distinct().toList();
        if (ammo.isEmpty()) cursor = line(g, font, Component.translatable("gui.bastion.codex.ammo_fe"), cursor, TEXT);
        else cursor = icons(g, font, Component.translatable("gui.bastion.codex.ammo"), ammo, cursor, mouseX, mouseY, top, bottom);
        WeaponData data = module.data();
        if (data == null) return cursor;
        cursor = bar(g, font, "damage", data.damage(), max(WeaponData::damage), cursor);
        cursor = bar(g, font, "fire_rate", 20f / data.fireInterval(), max(d -> 20f / d.fireInterval()), cursor);
        return bar(g, font, "range", data.range(), max(WeaponData::range), cursor) + 2;
    }

    private static double max(ToDoubleFunction<WeaponData> stat) {
        return WeaponDataLoader.all().entrySet().stream().filter(e -> e.getKey().getNamespace().equals(Bastion.MOD_ID))
                .mapToDouble(e -> stat.applyAsDouble(e.getValue())).max().orElse(1);
    }

    private int bar(GuiGraphics g, Font font, String key, double value, double max, int cursor) {
        int labelW = 52, valueW = 30, bx = x + 4 + labelW, bw = w - 8 - labelW - valueW;
        fit(g, font, Component.translatable("gui.bastion.codex." + key), x + 4, cursor + 1, labelW - 2, MUTED);
        g.fill(bx, cursor + 2, bx + bw, cursor + 7, FlatButton.DARK);
        if (value > 0) g.fill(bx, cursor + 2, bx + Math.max(1, (int) (bw * Math.min(1, value / max))), cursor + 7, CodexTree.CYAN);
        String text = value <= 0 ? "—" : value < 0.1 ? String.format("%.2f", value) : value < 10 ? String.format("%.1f", value) : String.valueOf(Math.round(value));
        fit(g, font, Component.literal(text), bx + bw + 3, cursor + 1, valueW - 3, TEXT);
        return cursor + 10;
    }

    /** The icon item's own tooltip without its name, or a node's own text when it bundles several things. */
    private static List<Component> description(ResearchNode node, Player player) {
        if (node.named()) return List.of(Component.translatable("research.bastion." + node.id() + ".desc"));
        if (node.icon() instanceof WeaponModuleItem module) {
            return List.of(Component.translatable("tooltip.bastion.weapon." + module.modelName() + ".ability"));
        }
        // The item's own lines only: no name, no mod name, no other mod's tooltip events (nor our "not researched" line).
        List<Component> lines = new ArrayList<>();
        node.icon().appendHoverText(new ItemStack(node.icon()), player.level(), lines, TooltipFlag.NORMAL);
        return lines;
    }

    private int line(GuiGraphics g, Font font, Component text, int cursor, int colour) {
        for (FormattedCharSequence part : font.split(text, w - 8)) {
            g.drawString(font, part, x + 4, cursor + 1, colour, false);
            cursor += 10;
        }
        return cursor + 1;
    }

    private int icons(GuiGraphics g, Font font, Component label, List<Item> items, int cursor, int mouseX, int mouseY, int top, int bottom) {
        cursor = line(g, font, label, cursor, MUTED);
        int perRow = Math.max(1, (w - 8) / ROW);
        for (int i = 0; i < items.size(); i++) {
            int ix = x + 4 + (i % perRow) * ROW, iy = cursor + (i / perRow) * ROW;
            boolean visible = mouseY >= top && mouseY < bottom;
            icon(g, new ItemStack(items.get(i)), ix, iy, visible ? mouseX : -1, mouseY);
        }
        return cursor + ((items.size() + perRow - 1) / perRow) * ROW + 2;
    }

    private void icon(GuiGraphics g, ItemStack stack, int ix, int iy, int mouseX, int mouseY) {
        g.renderItem(stack, ix, iy);
        if (mouseX >= ix && mouseX < ix + 16 && mouseY >= iy && mouseY < iy + 16) hovered = stack;
    }

    static void fit(GuiGraphics g, Font font, Component text, int x, int y, int maxWidth, int colour) {
        float scale = Math.min(1, maxWidth / (float) Math.max(1, font.width(text)));
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }
}
