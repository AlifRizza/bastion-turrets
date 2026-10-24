package dev.bastion.client.screen;

import dev.bastion.client.compat.jei.JeiBridge;
import dev.bastion.network.BastionNetwork;
import dev.bastion.network.ResearchRequest;
import dev.bastion.research.Research;
import dev.bastion.research.ResearchNode;
import dev.bastion.research.ResearchNode.Tab;
import dev.bastion.research.ResearchTree;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Turret Codex (PLAN 8 "Riset & Turret Codex", layout B): the research tree of one tab on the left (Turret, Workshop,
 * Modul), the selected node's page on the right. Code-drawn in the turret GUI palette; it reads the research the server last
 * sent (Research.client), so a node researched by anyone turns cyan here at once.
 */
public class CodexScreen extends Screen {
    private static final int MARGIN = 8, MIN_W = 320, MIN_H = 200, TAB_H = 14;
    /** The last tab and node of this game session, where the next open starts. */
    private static Tab lastTab = Tab.TURRET;
    private static String lastSelected = "gun";

    private Tab tab;
    private String selected;
    private final CodexTree tree = new CodexTree();
    private final CodexPage page = new CodexPage();
    private FlatButton researchButton, recipeButton;
    private int x0, y0, w, h, leftW;
    private boolean draggingTree, draggingModel;
    /** The card a left press landed on: picked on release unless the press became a drag. */
    @Nullable
    private ResearchNode pressed;
    private double dragged;
    /** Nodes researched when last drawn: a new one flashes. */
    @Nullable
    private Set<String> seen;

    public CodexScreen(Tab tab, @Nullable String selected) {
        super(Component.translatable("item.bastion.turret_codex"));
        this.tab = tab;
        this.selected = selected != null && ResearchTree.get(selected) != null ? selected : ResearchTree.tab(tab).get(0).id();
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new CodexScreen(lastTab, lastSelected));
    }

    @Override
    protected void init() {
        w = Math.max(MIN_W, width - 2 * MARGIN);
        h = Math.max(MIN_H, height - 2 * MARGIN);
        x0 = (width - w) / 2;
        y0 = (height - h) / 2;
        leftW = w * 3 / 5;
        int tabW = (leftW - 8 - 4) / 3;
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            addRenderableWidget(new FlatButton(x0 + 4 + i * (tabW + 2), y0 + 4, tabW, TAB_H,
                    () -> Component.translatable("gui.bastion.codex.tab." + t.name().toLowerCase()), () -> tab(t), () -> tab == t));
        }
        tree.show(tab);
        tree.layout(x0 + 4, y0 + 4 + TAB_H + 4, leftW - 8, h - TAB_H - 12);
        page.layout(x0 + leftW + 2, y0 + 2, w - leftW - 4, h - 4);
        boolean jei = ModList.get().isLoaded("jei");
        int recipeW = jei ? 48 : 0, researchW = page.width() - (jei ? recipeW + 3 : 0);
        researchButton = addRenderableWidget(new FlatButton(page.left(), page.buttonY(), researchW, 16,
                () -> Component.translatable("gui.bastion.codex.research"), this::research));
        recipeButton = addRenderableWidget(new FlatButton(page.left() + page.width() - recipeW, page.buttonY(), recipeW, 16,
                () -> Component.translatable("gui.bastion.codex.recipe"), () -> JeiBridge.showRecipes(new ItemStack(node().icon()))));
        recipeButton.visible = jei;
    }

    /** Showcase hooks: switch tab / pick a node as a click would. */
    public void tab(Tab t) {
        if (t == tab) return;
        tab = t;
        lastTab = t;
        tree.show(t);
        select(ResearchTree.tab(t).get(0).id());
    }

    public void select(String id) {
        ResearchNode node = ResearchTree.get(id);
        if (node == null) return;
        if (node.tab() != tab) tab(node.tab());
        selected = id;
        lastSelected = id;
        page.reset();
    }

    private ResearchNode node() {
        return ResearchTree.get(selected);
    }

    private void research() {
        BastionNetwork.CHANNEL.sendToServer(new ResearchRequest(selected));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        panel(g, x0, y0, leftW, h);
        panel(g, x0 + leftW, y0, w - leftW, h);

        Minecraft mc = Minecraft.getInstance();
        long now = mc.level == null ? 0 : mc.level.getGameTime();
        Research.State state = mc.level == null ? new Research.State(true, Set.of()) : Research.state(mc.level);
        // A card researched while open flashes; the first sync (defaults → the world's research) flashes nothing.
        if (seen != null && Research.clientSynced()) for (String id : state.researched()) if (!seen.contains(id)) tree.flash(id, now);
        seen = Research.clientSynced() ? new HashSet<>(state.researched()) : null;

        ResearchNode node = node();
        List<ItemStack> cost = mc.level == null ? List.of() : Research.cost(mc.level, node.id());
        boolean canResearch = state.enabled() && !node.free() && !state.researched(node.id());
        researchButton.visible = canResearch;
        researchButton.active = canResearch && state.available(node) && mc.player != null
                && (mc.player.isCreative() || cost.stream().allMatch(s -> mc.player.getInventory().countItem(s.getItem()) >= s.getCount()));
        tree.render(g, state, selected, now, partialTick);
        if (mc.player != null) page.render(g, node, state, cost, mc.player, canResearch || recipeButton.visible, now, partialTick, mouseX, mouseY);
        super.render(g, mouseX, mouseY, partialTick);

        ResearchNode hoveredNode = tree.at(mouseX, mouseY);
        Item companion = tree.companionAt(mouseX, mouseY);
        if (companion != null) {
            g.renderComponentTooltip(font, List.of(companion.getDescription(), Component.translatable("gui.bastion.codex.comes_with", hoveredNode.name()),
                    CodexTree.status(state, hoveredNode)), mouseX, mouseY);
        } else if (hoveredNode != null) {
            g.renderComponentTooltip(font, List.of(hoveredNode.name(), CodexTree.status(state, hoveredNode)), mouseX, mouseY);
        } else if (page.hovered() != null && page.inside(mouseX, mouseY)) {
            g.renderTooltip(font, page.hovered(), mouseX, mouseY);
        }
    }

    private static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, FlatButton.EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xF0161A20);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button != 0) return false;
        pressed = tree.at(mx, my);
        dragged = 0;
        draggingTree = tree.inside(mx, my);
        draggingModel = page.onModel(mx, my);
        return draggingTree || draggingModel;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingTree) {
            tree.pan(dx, dy); // the content follows the mouse
            dragged += Math.abs(dx) + Math.abs(dy);
        }
        else if (draggingModel) page.turn(dx);
        return draggingTree || draggingModel || super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && pressed != null && dragged < 3 && pressed == tree.at(mx, my)) select(pressed.id());
        pressed = null;
        draggingTree = draggingModel = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (tree.inside(mx, my)) tree.wheel(delta); // wheel up: the rows above
        else if (page.inside(mx, my)) page.scroll(delta);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false; // a singleplayer server keeps ticking, so a research answers at once
    }
}
