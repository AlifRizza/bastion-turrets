package dev.bastion.client.screen;

import dev.bastion.research.Research;
import dev.bastion.research.ResearchNode;
import dev.bastion.research.ResearchTree;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Codex's left panel (PLAN 8 "Riset & Turret Codex"): one tab's cards on a grid, row 0 at the bottom, growing up like
 * Mindustry's tree. What a card brings along (ammo, stations, tools; not parts) sits beside it as smaller cards in its
 * colour, so each column is as wide as its widest group. Lines run from a parent's top half a step up, across to the
 * child's column, then up into the child. Cyan = researched, red = not, red pulsing = researchable now. Dragged or
 * scrolled to pan.
 */
final class CodexTree {
    static final int STEP_X = 28, STEP_Y = 27, CARD = 22, PAD = 6, COMP = 18, GAP = STEP_X - CARD;
    static final int CYAN = 0xFF4FD8FF, RED = 0xFFFF5A4F, DIM_RED = 0x80FF5A4F;
    private static final int FLASH_TICKS = 10;

    private int x, y, w, h;
    private List<ResearchNode> nodes = List.of();
    private int cols, rows;
    /** Each column's left edge from the content's left, and the content's width (columns widen for companions). */
    private int[] colX = new int[0];
    private int width;
    /** How far the content is pushed right / up from where it starts; clamped to the content. */
    private double panX, panY;
    /** Client game time a node turned researched while the screen was open (it flashes cyan). */
    private final Map<String, Long> flashes = new HashMap<>();

    void layout(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        panX = Mth.clamp(panX, Math.min(0, w - 2 * PAD - contentW()), 0);
        panY = Mth.clamp(panY, 0, Math.max(0, contentH() - (h - 2 * PAD)));
    }

    /** Shows {@code tab} from its bottom row. */
    void show(ResearchNode.Tab tab) {
        nodes = ResearchTree.tab(tab);
        cols = nodes.stream().mapToInt(ResearchNode::col).max().orElse(0) + 1;
        rows = nodes.stream().mapToInt(ResearchNode::row).max().orElse(0) + 1;
        colX = new int[cols];
        int x = 0;
        for (int c = 0; c < cols; c++) {
            int col = c, most = nodes.stream().filter(n -> n.col() == col).mapToInt(n -> n.companions().size()).max().orElse(0);
            colX[c] = x;
            width = x + CARD + companionsWidth(most);
            x = width + GAP;
        }
        panX = panY = 0;
    }

    void flash(String id, long now) {
        flashes.put(id, now);
    }

    private int contentW() {
        return width;
    }

    /** Room the companions take right of a card: a 2 px gap, then COMP-wide cards 1 px apart. */
    private static int companionsWidth(int count) {
        return count == 0 ? 0 : 2 + count * (COMP + 1) - 1;
    }

    private static int companionX(int cardX, int i) {
        return cardX + CARD + 2 + i * (COMP + 1);
    }

    private int contentH() {
        return (rows - 1) * STEP_Y + CARD;
    }

    /** Content narrower than the panel sits centred and does not pan that way. */
    private int originX() {
        int free = w - 2 * PAD - contentW();
        return x + PAD + (free > 0 ? free / 2 : (int) panX);
    }

    /** The bottom row's top edge. */
    private int originY() {
        return y + h - PAD - CARD + (int) panY;
    }

    int cardX(ResearchNode node) {
        return originX() + colX[node.col()];
    }

    int cardY(ResearchNode node) {
        return originY() - node.row() * STEP_Y;
    }

    boolean inside(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** The mouse wheel: up and down, or sideways when the tab is no taller than the panel. */
    void wheel(double delta) {
        if (contentH() > h - 2 * PAD) pan(0, delta * STEP_Y / 2);
        else pan(delta * STEP_X / 2, 0);
    }

    void pan(double dx, double dy) {
        panX = Mth.clamp(panX + dx, Math.min(0, w - 2 * PAD - contentW()), 0);
        panY = Mth.clamp(panY + dy, 0, Math.max(0, contentH() - (h - 2 * PAD)));
    }

    /** The node whose card, or one of its companions, is under the mouse. */
    @Nullable
    ResearchNode at(double mx, double my) {
        if (!inside(mx, my)) return null;
        for (ResearchNode node : nodes) {
            int cx = cardX(node), cy = cardY(node);
            if (mx >= cx && mx < cx + CARD + companionsWidth(node.companions().size()) && my >= cy && my < cy + CARD) return node;
        }
        return null;
    }

    /** The companion item under the mouse, if any (its node: {@link #at}). */
    @Nullable
    Item companionAt(double mx, double my) {
        ResearchNode node = at(mx, my);
        if (node == null) return null;
        List<Item> companions = node.companions();
        int cx = cardX(node), cy = cardY(node) + (CARD - COMP) / 2;
        for (int i = 0; i < companions.size(); i++) {
            int x = companionX(cx, i);
            if (mx >= x && mx < x + COMP && my >= cy && my < cy + COMP) return companions.get(i);
        }
        return null;
    }

    void render(GuiGraphics graphics, Research.State state, @Nullable String selected, long now, float partialTick) {
        graphics.enableScissor(x, y, x + w, y + h);
        for (ResearchNode node : nodes) {
            for (String id : node.parents()) {
                ResearchNode parent = ResearchTree.get(id);
                if (parent != null) line(graphics, parent, node, state.researched(parent.id()) ? CYAN : DIM_RED);
            }
        }
        float pulse = 0.45f + 0.55f * (0.5f + 0.5f * Mth.sin((now + partialTick) * Mth.TWO_PI / 20));
        for (ResearchNode node : nodes) {
            int cx = cardX(node), cy = cardY(node);
            boolean done = state.researched(node.id());
            int edge = done ? CYAN : state.available(node) ? alpha(RED, pulse) : RED;
            graphics.fill(cx, cy, cx + CARD, cy + CARD, edge);
            graphics.fill(cx + 1, cy + 1, cx + CARD - 1, cy + CARD - 1, node.id().equals(selected) ? 0xFF243440 : FlatButton.BG);
            if (node.id().equals(selected)) {
                graphics.fill(cx - 1, cy - 1, cx + CARD + 1, cy, edge);
                graphics.fill(cx - 1, cy + CARD, cx + CARD + 1, cy + CARD + 1, edge);
                graphics.fill(cx - 1, cy, cx, cy + CARD, edge);
                graphics.fill(cx + CARD, cy, cx + CARD + 1, cy + CARD, edge);
            }
            graphics.renderItem(new ItemStack(node.icon()), cx + 3, cy + 3);
            List<Item> companions = node.companions();
            for (int i = 0; i < companions.size(); i++) { // what comes along, in the card's colour, joined to it
                int x = companionX(cx, i), y = cy + (CARD - COMP) / 2;
                if (i == 0) graphics.fill(cx + CARD, cy + CARD / 2, x, cy + CARD / 2 + 1, edge);
                graphics.fill(x, y, x + COMP, y + COMP, edge);
                graphics.fill(x + 1, y + 1, x + COMP - 1, y + COMP - 1, FlatButton.BG);
                graphics.renderItem(new ItemStack(companions.get(i)), x + 1, y + 1);
            }
            Long flashed = flashes.get(node.id());
            if (flashed != null && now - flashed < FLASH_TICKS) { // over the icon (items draw at z 150)
                float left = 1 - (now - flashed + partialTick) / FLASH_TICKS;
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 200);
                graphics.fill(cx + 1, cy + 1, cx + CARD - 1, cy + CARD - 1, alpha(CYAN, Math.max(0, left) * 0.8f));
                graphics.pose().popPose();
            }
            // Requires from other tabs, half size on the top-right corner while not researched.
            int i = 0;
            for (String id : node.requires()) {
                ResearchNode required = ResearchTree.get(id);
                if (required == null || state.researched(id)) continue;
                graphics.pose().pushPose();
                graphics.pose().translate(cx + CARD - 7 - i * 8, cy - 3, 200);
                graphics.pose().scale(0.5f, 0.5f, 1);
                graphics.renderItem(new ItemStack(required.icon()), 0, 0);
                graphics.pose().popPose();
                i++;
            }
        }
        graphics.disableScissor();
        // More content off an edge: a small arrow there (drag or scroll to see it), over the cards and their icons.
        int midY = y + h / 2 - 5, midX = x + w / 2 - 4;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        if (originX() < x + PAD) edgeArrow(graphics, "<", x, midY);
        if (originX() + contentW() > x + w - PAD) edgeArrow(graphics, ">", x + w - 8, midY);
        if (originY() - (rows - 1) * STEP_Y < y + PAD) edgeArrow(graphics, "^", midX, y);
        if (originY() + CARD > y + h - PAD) edgeArrow(graphics, "v", midX, y + h - 11);
        graphics.pose().popPose();
    }

    private static void edgeArrow(GuiGraphics graphics, String glyph, int x, int y) {
        Font font = Minecraft.getInstance().font;
        graphics.fill(x, y, x + 8, y + 11, 0xE0101418);
        graphics.drawString(font, glyph, x + 2, y + 2, FlatButton.ACCENT, false);
    }

    /** Parent top centre → half a step up → across to the child's column → up into the child's bottom centre. */
    private void line(GuiGraphics graphics, ResearchNode parent, ResearchNode child, int colour) {
        int px = cardX(parent) + CARD / 2, py = cardY(parent);
        int cx = cardX(child) + CARD / 2, cy = cardY(child) + CARD;
        int bend = py - (STEP_Y - CARD) / 2;
        graphics.fill(px, bend, px + 1, py, colour);
        graphics.fill(Math.min(px, cx), bend, Math.max(px, cx) + 1, bend + 1, colour);
        graphics.fill(cx, cy, cx + 1, bend + 1, colour); // from the child's bottom down to the bend
    }

    static Component status(Research.State state, ResearchNode node) {
        if (node.free()) return Component.translatable("gui.bastion.codex.status.free").withStyle(s -> s.withColor(CYAN & 0xFFFFFF));
        if (state.researched(node.id())) return Component.translatable("gui.bastion.codex.status.researched").withStyle(s -> s.withColor(CYAN & 0xFFFFFF));
        if (state.available(node)) return Component.translatable("gui.bastion.codex.status.available").withStyle(s -> s.withColor(0xFFD84F));
        return Component.translatable("gui.bastion.codex.status.locked").withStyle(s -> s.withColor(RED & 0xFFFFFF));
    }

    static int alpha(int argb, float alpha) {
        return ((int) (Mth.clamp(alpha, 0, 1) * 255) << 24) | (argb & 0xFFFFFF);
    }
}
