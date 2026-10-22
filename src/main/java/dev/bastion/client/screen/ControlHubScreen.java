package dev.bastion.client.screen;

import dev.bastion.Bastion;
import dev.bastion.hub.FilterMix;
import dev.bastion.hub.HubEdit;
import dev.bastion.hub.HubInterfaceBlock.Status;
import dev.bastion.menu.ControlHubMenu;
import dev.bastion.menu.ControlHubMenu.Entry;
import dev.bastion.network.BastionNetwork;
import dev.bastion.network.HubAction;
import dev.bastion.network.OverwatchRequest;
import dev.bastion.registry.BastionItems;
import dev.bastion.registry.BastionSounds;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretState;
import dev.bastion.turret.targeting.TargetFilter;
import dev.bastion.turret.targeting.TargetFilter.Category;
import dev.bastion.turret.targeting.TargetFilter.PlayerMode;
import dev.bastion.turret.targeting.TargetFilter.Priority;
import dev.bastion.turret.targeting.TargetFilter.Rule;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turret Control Hub screen (PLAN "Turret Control Hub"): a header (satellite tier, reach, turret count, FE bar) and side
 * tabs like the turret GUI: Map (the hub's area from above, one dot per turret coloured by HP) and List (one row per
 * turret) and Batch settings (one change at a time to every selected turret). Clicking a turret opens its own GUI through
 * the hub; selection, tab and map view
 * last for that trip (HubSelection). Everything shown comes from the server's roster, every half second.
 */
public class ControlHubScreen extends AbstractContainerScreen<ControlHubMenu> {
    private static final ResourceLocation TEXTURE = Bastion.id("textures/gui/control_hub.png");
    // Sprites under the 256 x 200 panel (tools/gen_textures.py control_hub_gui): side tab, active tab, checkbox off / on.
    private static final int SPRITE_V = 200, TAB_U = 0, TAB_ACTIVE_U = 24, CHECK_U = 48, CHECKED_U = 56;
    private static final int TEXT = 0xD8E2EC, MUTED = 0x7F8C9A, ACCENT = 0x9FC4E8, GREEN = 0x7CFF8A, YELLOW = 0xFFC21F, RED = 0xFF5A4F,
            DIM_RED = 0x6E100A, AMBER = 0xFFB347, GREY = 0x6B7480, CYAN = 0x4FD8FF;
    private static final int TAB_SIZE = 24, TAB_X = -23, TAB_Y = 8, TAB_GAP = 26;
    // The panel fills most of the screen (user 10-10): a margin round it and room for the side tabs on its left, never
    // smaller than the first 256 x 200 panel, capped so a small GUI scale does not stretch rows across a whole monitor.
    private static final int MARGIN = 6, TAB_ROOM = 24, MIN_W = 256, MIN_H = 200, MAX_W = 640, MAX_H = 400;
    private static final int FE_X = 8, FE_Y = 18, FE_H = 6;
    // Map tab: the map square on the left, as big as the panel allows; selection buttons, legend and hints right of it.
    private static final int MAP_X = 8, MAP_Y = 30, SIDE_MIN = 72, LEGEND_Y = 90, HINT_Y = 160, PICK = 4;
    // List tab: sort and selection buttons, then as many rows as fit.
    private static final int LIST_Y = 46, ROW_H = 18;
    private static final int[] ZOOMS = {1, 2, 4};
    // Batch tab: settings on the left, trusted players and mob rules on the right, as the turret's Targeting tab.
    private static final int B_LEFT = 8, LINE = 11, TRUSTED_Y = 57;
    // Positions that follow the panel size (layout()).
    private int mapSize, sideX, sideW, listRows, nameW, bRight, bW, trustedLines, ruleLines, ruleY;
    private EditBox trustedBox, searchBox;
    private String trustedDraft = "", search = "";
    private int trustedScroll, ruleScroll;

    enum Tab {MAP, LIST, BATCH}

    private final HubMap map = new HubMap();
    /**
     * Map drags, started at (pressX, pressY): a box selection (shift + left) or panning (right, or left once it moves: a
     * trackpad has no comfortable right-drag); a left press that does not move opens the turret under it on release.
     */
    private boolean boxing, panning, pressing;
    private double pressX, pressY;
    /** Scroll events closer together than this are one swipe: a trackpad sends dozens per swipe, momentum included. */
    private static final long SWIPE_GAP_MS = 200;
    private long lastScroll;
    private boolean swipeZoomed;
    /** The part of a trackpad's fractional scroll that has not made a whole list row yet. */
    private double scrollRest;
    private FlatButton zoomIn, zoomOut;
    /** Map tab: Take control of the one selected turret (PLAN "Overwatch Link"); says why not when it can't. */
    private FlatButton control;

    public ControlHubScreen(ControlHubMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 256;
        imageHeight = 200;
        HubSelection.open(menu.hubPos());
    }

    /** Map, side column, list columns and batch lists for the panel size; at 256 x 200 exactly the first layout. */
    private void layout() {
        mapSize = Math.min(imageHeight - MAP_Y - 8, imageWidth - MAP_X - 6 - SIDE_MIN - 8);
        sideX = MAP_X + mapSize + 6;
        sideW = imageWidth - 8 - sideX;
        listRows = (imageHeight - LIST_Y - 8) / ROW_H;
        nameW = (imageWidth - 132) / 2; // name and position share what the HP bar, ammo and lamp leave
        bW = (imageWidth - 24) / 2;
        bRight = imageWidth - 8 - bW;
        int extra = Math.max(0, imageHeight - MIN_H) / LINE; // rows a taller panel adds to the two batch lists
        trustedLines = 4 + extra / 2;
        ruleLines = 5 + extra - extra / 2;
        ruleY = TRUSTED_Y + trustedLines * LINE + 30; // the rules label and the search box sit above the list
    }

    private Tab tab() {
        return Tab.values()[Mth.clamp(HubSelection.tab, 0, Tab.values().length - 1)];
    }

    private boolean online() {
        return menu.header().status() == Status.ONLINE;
    }

    @Override
    protected void init() {
        imageWidth = Mth.clamp(width - 2 * MARGIN - TAB_ROOM, MIN_W, MAX_W);
        imageHeight = Mth.clamp(height - 2 * MARGIN, MIN_H, MAX_H);
        layout();
        super.init();
        leftPos += TAB_ROOM / 2; // the side tabs stick out on the left: centre the panel and its tabs together
        zoomIn = zoomOut = null;
        control = null;
        if (tab() == Tab.MAP) {
            zoomOut = addRenderableWidget(new FlatButton(leftPos + MAP_X + mapSize - 30, topPos + MAP_Y + 4, 12, 12,
                    () -> Component.literal("-"), () -> zoom(mapSize / 2.0, mapSize / 2.0, -1)));
            zoomIn = addRenderableWidget(new FlatButton(leftPos + MAP_X + mapSize - 16, topPos + MAP_Y + 4, 12, 12,
                    () -> Component.literal("+"), () -> zoom(mapSize / 2.0, mapSize / 2.0, 1)));
            addRenderableWidget(new FlatButton(leftPos + sideX, topPos + 44, sideW, 12,
                    () -> Component.translatable("gui.bastion.control_hub.select_all"), this::selectAll));
            addRenderableWidget(new FlatButton(leftPos + sideX, topPos + 58, sideW, 12,
                    () -> Component.translatable("gui.bastion.control_hub.clear"), this::clearSelection));
            control = addRenderableWidget(new FlatButton(leftPos + sideX, topPos + 72, sideW, 12, this::controlLabel, this::takeControl));
        } else if (tab() == Tab.LIST) {
            String[] sorts = {"distance", "health", "ammo"};
            for (int i = 0; i < sorts.length; i++) {
                int sort = i;
                addRenderableWidget(new FlatButton(leftPos + 8 + i * 52, topPos + 30, 50, 12,
                        () -> Component.translatable("gui.bastion.control_hub.sort." + sorts[sort]), () -> {
                            HubSelection.sort = sort;
                            HubSelection.listScroll = 0;
                        }, () -> HubSelection.sort == sort));
            }
            int pick = Math.min(60, (imageWidth - 172) / 2 - 2); // wide enough for "Select all" once the sort buttons leave room
            addRenderableWidget(new FlatButton(leftPos + imageWidth - 8 - 2 * pick - 2, topPos + 30, pick, 12,
                    () -> Component.translatable("gui.bastion.control_hub.select_all"), this::selectAll));
            addRenderableWidget(new FlatButton(leftPos + imageWidth - 8 - pick, topPos + 30, pick, 12,
                    () -> Component.translatable("gui.bastion.control_hub.clear"), this::clearSelection));
        } else if (tab() == Tab.BATCH) {
            initBatch();
        }
    }

    /** Widgets only while online; turrets that left the reach leave the selection; the map keeps up. */
    @Override
    protected void containerTick() {
        super.containerTick();
        boolean online = online(), any = !targets().isEmpty();
        for (var widget : renderables) {
            if (widget instanceof AbstractWidget w) w.visible = online && (tab() != Tab.BATCH || any);
        }
        if (control != null) control.active = controlBlock() == null;
        if (!online || minecraft == null || minecraft.level == null) return;
        Set<BlockPos> present = new HashSet<>();
        for (Entry turret : menu.turrets()) present.add(turret.pos());
        HubSelection.selected.retainAll(present);
    }

    @Override
    public void removed() {
        super.removed();
        map.close();
    }

    private void switchTab(Tab next) {
        if (next == tab()) return;
        HubSelection.tab = next.ordinal();
        trustedScroll = ruleScroll = 0;
        rebuildWidgets();
    }

    // --- rendering --------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTabs(graphics, mouseX, mouseY);
        ControlHubMenu.Header h = menu.header();
        if (over(FE_X, FE_Y, (imageWidth - 16), FE_H, mouseX, mouseY) && h.capacity() > 0) {
            graphics.renderTooltip(font, Component.translatable("gui.bastion.control_hub.fe", number(h.energy()), number(h.capacity()), number(h.upkeep())),
                    mouseX, mouseY);
        } else if (online() && tab() == Tab.MAP && overMap(mouseX, mouseY) && !boxing && !panning) {
            renderTurretTooltip(graphics, under(mouseX, mouseY), mouseX, mouseY);
        }
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        ControlHubMenu.Header h = menu.header();
        frame(graphics, leftPos + FE_X, topPos + FE_Y, (imageWidth - 16), FE_H);
        int fill = h.capacity() == 0 ? 0 : (int) (((imageWidth - 16) - 2) * Math.min(1, (double) h.energy() / h.capacity()));
        graphics.fill(leftPos + FE_X + 1, topPos + FE_Y + 1, leftPos + FE_X + 1 + fill, topPos + FE_Y + FE_H - 1, online() ? 0xFF86A8FF : 0xFFFF5A4F);
        if (online() && tab() == Tab.MAP) renderMap(graphics, mouseX, mouseY);
        if (online() && tab() == Tab.BATCH && !targets().isEmpty()) batchFrames(graphics);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        ControlHubMenu.Header h = menu.header();
        Component summary = h.status() == Status.UNFORMED ? Component.translatable("gui.bastion.control_hub.connecting")
                : Component.translatable("gui.bastion.control_hub.summary", h.tier(), h.side(),
                h.total() > ControlHubMenu.MAX_TURRETS ? ControlHubMenu.MAX_TURRETS + "+" : String.valueOf(h.total()));
        int summaryW = Math.min(font.width(summary), imageWidth / 2);
        fit(graphics, title, 8, 6, imageWidth - 22 - summaryW, TEXT);
        fit(graphics, summary, imageWidth - 8 - summaryW, 6, summaryW, ACCENT);
        if (h.status() == Status.OFFLINE) {
            centered(graphics, Component.translatable("gui.bastion.control_hub.offline"), imageHeight / 2 - 4, RED);
            centered(graphics, Component.translatable("gui.bastion.control_hub.offline.hint"), imageHeight / 2 + 10, MUTED);
            return;
        }
        if (!online()) return;
        switch (tab()) {
            case MAP -> renderMapLabels(graphics);
            case LIST -> renderListLabels(graphics, mouseX - leftPos, mouseY - topPos);
            case BATCH -> renderBatchLabels(graphics, mouseX - leftPos, mouseY - topPos);
        }
    }

    private void renderTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        ItemStack[] icons = {new ItemStack(BastionItems.SATELLITE_UPLINK_T3.get()), new ItemStack(BastionItems.TURRET_BASE.get()),
                new ItemStack(BastionItems.TURRET_CONFIGURATOR.get())};
        for (Tab t : Tab.values()) {
            int x = leftPos + TAB_X, y = topPos + TAB_Y + t.ordinal() * TAB_GAP;
            graphics.blit(TEXTURE, x, y, t == tab() ? TAB_ACTIVE_U : TAB_U, SPRITE_V, TAB_SIZE, TAB_SIZE);
            graphics.renderItem(icons[t.ordinal()], x + 5, y + 4);
            if (overTab(t, mouseX, mouseY)) {
                graphics.renderTooltip(font, Component.translatable("gui.bastion.control_hub.tab." + t.name().toLowerCase(Locale.ROOT)), mouseX, mouseY);
            }
        }
    }

    // --- map --------------------------------------------------------------------------------------

    private int blocks() {
        return menu.header().side() * 16;
    }

    private int minX() {
        return ((menu.header().center().getX() >> 4) - menu.header().side() / 2) * 16;
    }

    private int minZ() {
        return ((menu.header().center().getZ() >> 4) - menu.header().side() / 2) * 16;
    }

    private int zoom() {
        return ZOOMS[Mth.clamp(HubSelection.zoom, 0, ZOOMS.length - 1)];
    }

    /** Blocks across the view. */
    private double visible() {
        return blocks() / (double) zoom();
    }

    /** Screen pixels per block. */
    private double scale() {
        return mapSize / visible();
    }

    private void clampView() {
        double max = blocks() - visible();
        HubSelection.viewX = Mth.clamp(HubSelection.viewX, 0, max);
        HubSelection.viewZ = Mth.clamp(HubSelection.viewZ, 0, max);
    }

    private double screenX(double worldX) {
        return leftPos + MAP_X + (worldX - minX() - HubSelection.viewX) * scale();
    }

    private double screenY(double worldZ) {
        return topPos + MAP_Y + (worldZ - minZ() - HubSelection.viewZ) * scale();
    }

    private boolean overMap(double mouseX, double mouseY) {
        return over(MAP_X, MAP_Y, mapSize, mapSize, mouseX, mouseY);
    }

    private void renderMap(GuiGraphics graphics, int mouseX, int mouseY) {
        // Here, not in containerTick: a screen reopened by "◀ Map" draws before its first tick, and a blit of the
        // unregistered map id made the texture manager look for it as a file.
        if (minecraft.level != null) map.update(minecraft.level, minX(), minZ(), blocks());
        int x0 = leftPos + MAP_X, y0 = topPos + MAP_Y;
        frame(graphics, x0 - 1, y0 - 1, mapSize + 2, mapSize + 2);
        clampView();
        int visible = (int) visible();
        graphics.blit(map.id(), x0, y0, mapSize, mapSize, (float) HubSelection.viewX, (float) HubSelection.viewZ, visible, visible, blocks(), blocks());
        graphics.enableScissor(x0, y0, x0 + mapSize, y0 + mapSize);
        for (int k = 1; k < menu.header().side(); k++) { // chunk borders
            int sx = (int) Math.round(screenX(minX() + 16 * k)), sy = (int) Math.round(screenY(minZ() + 16 * k));
            graphics.fill(sx, y0, sx + 1, y0 + mapSize, 0x30FFFFFF);
            graphics.fill(x0, sy, x0 + mapSize, sy + 1, 0x30FFFFFF);
        }
        BlockPos center = menu.header().center(); // the hub: a cyan square with a dark heart
        int hx = (int) screenX(center.getX() + 0.5), hy = (int) screenY(center.getZ() + 0.5);
        graphics.fill(hx - 4, hy - 4, hx + 5, hy + 5, 0xFF0B1418);
        graphics.fill(hx - 3, hy - 3, hx + 4, hy + 4, 0xFF000000 | CYAN);
        graphics.fill(hx - 1, hy - 1, hx + 2, hy + 2, 0xFF0B1418);
        long time = minecraft.level == null ? 0 : minecraft.level.getGameTime();
        for (Entry turret : menu.turrets()) {
            int x = (int) screenX(turret.pos().getX() + 0.5), y = (int) screenY(turret.pos().getZ() + 0.5);
            if (HubSelection.selected.contains(turret.pos())) graphics.fill(x - 4, y - 4, x + 5, y + 5, 0xFFFFFFFF); // white ring
            graphics.fill(x - 3, y - 3, x + 4, y + 4, 0xFF0B1418);
            graphics.fill(x - 2, y - 2, x + 3, y + 3, 0xFF000000 | dotColour(turret, time));
            if (turret.state() == TurretState.NO_AMMO && time % 20 < 14) { // "!" beside the dot
                graphics.fill(x + 4, y - 5, x + 5, y - 1, 0xFF000000 | AMBER);
                graphics.fill(x + 4, y, x + 5, y + 1, 0xFF000000 | AMBER);
            }
        }
        if (boxing) {
            graphics.fill((int) Math.min(pressX, mouseX), (int) Math.min(pressY, mouseY), (int) Math.max(pressX, mouseX),
                    (int) Math.max(pressY, mouseY), 0x304FD8FF);
        }
        graphics.disableScissor();
    }

    /** HP colour like the base's lights (PLAN 4.1); grey while switched off, unarmed or redstone-off. */
    private static int dotColour(Entry turret, long time) {
        if (turret.state() == TurretState.DISABLED) return GREY;
        float hp = turret.healthFraction();
        if (hp < TurretBaseBlockEntity.CRITICAL_FRACTION) return time % 10 < 5 ? RED : DIM_RED;
        return hp < TurretBaseBlockEntity.DAMAGED_FRACTION ? YELLOW : GREEN;
    }

    /** Turrets whose dot is under the cursor, highest first (turrets stacked in one column share a dot). */
    private List<Entry> under(double mouseX, double mouseY) {
        List<Entry> found = new ArrayList<>();
        for (Entry turret : menu.turrets()) {
            if (Math.abs(screenX(turret.pos().getX() + 0.5) - mouseX) <= PICK && Math.abs(screenY(turret.pos().getZ() + 0.5) - mouseY) <= PICK) found.add(turret);
        }
        found.sort(Comparator.comparingInt((Entry turret) -> turret.pos().getY()).reversed());
        return found;
    }

    private void renderMapLabels(GuiGraphics graphics) {
        if (menu.turrets().isEmpty()) { // nothing connected yet: say how, over the map
            List<FormattedCharSequence> lines = font.split(Component.translatable("gui.bastion.control_hub.none"), mapSize - 16);
            int top = MAP_Y + mapSize / 2 - lines.size() * 5;
            graphics.fill(MAP_X, top - 4, MAP_X + mapSize, top + lines.size() * 10 + 2, 0xC0101418);
            for (int i = 0; i < lines.size(); i++) {
                graphics.drawString(font, lines.get(i), MAP_X + (mapSize - font.width(lines.get(i))) / 2, top + i * 10, TEXT, false);
            }
        }
        fit(graphics, Component.translatable("gui.bastion.control_hub.selected", HubSelection.selected.size()), sideX, 32, sideW, TEXT);
        String[] legend = {"healthy", "damaged", "critical", "off", "no_ammo", "selected"};
        int[] colours = {GREEN, YELLOW, RED, GREY, AMBER, 0xFFFFFF};
        List<Component> lines = new ArrayList<>();
        for (String key : legend) lines.add(Component.translatable("gui.bastion.control_hub.legend." + key));
        float legendScale = scaleFor(lines, sideW - 8); // one size for all six (user 10-10: lines shrunk one by one looked uneven)
        for (int i = 0; i < legend.length; i++) {
            int y = LEGEND_Y + i * 11;
            graphics.fill(sideX, y + 1, sideX + 5, y + 6, 0xFF000000 | colours[i]);
            scaled(graphics, lines.get(i), sideX + 8, y, legendScale, MUTED);
        }
        // The hint as large as lets all of it fit under the legend (0.3.0 review: the 256 x 200 panel cut its last line),
        // never under half size.
        Component text = Component.translatable("gui.bastion.control_hub.hint");
        float small = 1;
        List<FormattedCharSequence> hint = font.split(text, sideW);
        while (small > 0.5f && hint.size() * 10 * small > imageHeight - 6 - HINT_Y) {
            small -= 0.05f;
            hint = font.split(text, (int) (sideW / small));
        }
        graphics.pose().pushPose();
        graphics.pose().translate(sideX, HINT_Y, 0);
        graphics.pose().scale(small, small, 1);
        for (int i = 0; i < Math.min((int) ((imageHeight - 6 - HINT_Y) / (10 * small)), hint.size()); i++) {
            graphics.drawString(font, hint.get(i), 0, i * 10, MUTED, false);
        }
        graphics.pose().popPose();
    }

    private void renderTurretTooltip(GuiGraphics graphics, List<Entry> turrets, int mouseX, int mouseY) {
        if (turrets.isEmpty()) return;
        List<Component> lines = new ArrayList<>();
        for (int i = 0; i < Math.min(4, turrets.size()); i++) {
            if (i > 0) lines.add(Component.empty());
            lines.addAll(describe(turrets.get(i)));
        }
        if (turrets.size() > 4) lines.add(Component.translatable("gui.bastion.control_hub.more", turrets.size() - 4).withStyle(ChatFormatting.DARK_GRAY));
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    /** Name, position, HP, ammo or FE, state: one turret's tooltip. */
    private static List<Component> describe(Entry turret) {
        BlockPos p = turret.pos();
        Component supply = turret.energyWeapon() ? Component.translatable("gui.bastion.control_hub.energy", Math.round(turret.fill() * 100))
                : Component.translatable("gui.bastion.control_hub.ammo", turret.ammo());
        List<Component> lines = new ArrayList<>(List.of(name(turret),
                Component.translatable("gui.bastion.control_hub.pos", p.getX(), p.getY(), p.getZ()).withStyle(ChatFormatting.GRAY),
                Component.translatable("gui.bastion.control_hub.hp", Math.round(turret.health()), Math.round(turret.maxHealth())).withStyle(ChatFormatting.GRAY),
                supply.copy().withStyle(ChatFormatting.GRAY),
                Component.translatable("gui.bastion.state." + turret.state().name().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.DARK_AQUA)));
        if (turret.linked()) lines.add(Component.translatable("gui.bastion.control_hub.linked").withStyle(ChatFormatting.AQUA));
        if (!turret.controller().isEmpty()) lines.add(Component.translatable("gui.bastion.control_hub.controller", turret.controller()).withStyle(ChatFormatting.GOLD));
        return lines;
    }

    private static Component name(Entry turret) {
        return turret.weapon() == Items.AIR ? Component.translatable("gui.bastion.control_hub.unarmed") : new ItemStack(turret.weapon()).getHoverName();
    }

    // --- list -------------------------------------------------------------------------------------

    private List<Entry> sorted() {
        BlockPos center = menu.header().center();
        Comparator<Entry> order = switch (HubSelection.sort) {
            case 1 -> Comparator.comparingDouble(Entry::healthFraction);
            case 2 -> Comparator.comparingDouble(Entry::fill);
            default -> Comparator.comparingDouble(turret -> turret.pos().distSqr(center));
        };
        return menu.turrets().stream().sorted(order).toList();
    }

    /** Rows in screen-relative coordinates (renderLabels runs with the pose moved to the panel). */
    private void renderListLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        List<Entry> rows = sorted();
        if (rows.isEmpty()) {
            centered(graphics, Component.translatable("gui.bastion.control_hub.none"), imageHeight / 2, MUTED);
            return;
        }
        HubSelection.listScroll = Mth.clamp(HubSelection.listScroll, 0, Math.max(0, rows.size() - listRows));
        long time = minecraft.level == null ? 0 : minecraft.level.getGameTime();
        List<Entry> shown = rows.subList(HubSelection.listScroll, Math.min(rows.size(), HubSelection.listScroll + listRows));
        // One text size per column (user 10-10: lines shrunk one by one looked uneven); full size whenever it fits.
        List<Component> names = shown.stream().map(ControlHubScreen::name).toList();
        List<Component> places = shown.stream().map(turret -> (Component) Component.translatable("gui.bastion.control_hub.pos",
                turret.pos().getX(), turret.pos().getY(), turret.pos().getZ())).toList();
        float nameScale = scaleFor(names, nameW), placeScale = scaleFor(places, nameW);
        int right = imageWidth - 8, bar = right - 74, ammo = right - 40, lamp = right - 9;
        for (int i = 0; i < shown.size(); i++) {
            Entry turret = shown.get(i);
            int y = LIST_Y + i * ROW_H;
            if (mouseY >= y && mouseY < y + ROW_H && mouseX >= 8 && mouseX < right) graphics.fill(8, y, right, y + ROW_H, 0x18FFFFFF);
            graphics.blit(TEXTURE, 10, y + 5, HubSelection.selected.contains(turret.pos()) ? CHECKED_U : CHECK_U, SPRITE_V, 8, 8);
            if (turret.weapon() != Items.AIR) graphics.renderItem(new ItemStack(turret.weapon()), 22, y + 1);
            scaled(graphics, names.get(i), 42, y + 5, nameScale, TEXT);
            scaled(graphics, places.get(i), 46 + nameW, y + 5, placeScale, MUTED);
            frame(graphics, bar, y + 7, 30, 4);
            graphics.fill(bar + 1, y + 8, bar + 1 + Math.round(28 * Mth.clamp(turret.healthFraction(), 0, 1)), y + 10, 0xFF000000 | dotColour(turret, time));
            Component supply = Component.literal(turret.energyWeapon() ? Math.round(turret.fill() * 100) + "%" : String.valueOf(turret.ammo()));
            fit(graphics, supply, ammo, y + 5, 28, turret.state() == TurretState.NO_AMMO ? AMBER : TEXT);
            graphics.fill(lamp, y + 6, lamp + 6, y + 12, 0xFF000000 | TurretScreen.stateColour(turret.state()));
        }
        if (rows.size() > listRows) { // slim scroll indicator
            int track = listRows * ROW_H, knob = Math.max(8, track * listRows / rows.size());
            int knobY = LIST_Y + (track - knob) * HubSelection.listScroll / (rows.size() - listRows);
            graphics.fill(right + 1, knobY, right + 3, knobY + knob, 0xFF000000 | ACCENT);
        }
    }

    // --- input ------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Tab t : Tab.values()) {
            if (overTab(t, mouseX, mouseY)) {
                switchTab(t);
                return true;
            }
        }
        if (online() && tab() == Tab.MAP && overMap(mouseX, mouseY) && !onZoomButton(mouseX, mouseY)) {
            pressX = mouseX;
            pressY = mouseY;
            if (button == 1) {
                panning = true;
            } else if (button == 0 && hasShiftDown()) {
                boxing = true;
            } else if (button == 0) {
                pressing = true;
            }
            return true;
        }
        if (online() && tab() == Tab.LIST && button == 0 && clickRow(mouseX - leftPos, mouseY - topPos)) return true;
        if (online() && tab() == Tab.BATCH && button == 0 && clickBatchList((int) mouseX - leftPos, (int) mouseY - topPos)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (pressing && (Math.abs(mouseX - pressX) > 3 || Math.abs(mouseY - pressY) > 3)) panning = true;
        if (panning) {
            HubSelection.viewX -= dragX / scale();
            HubSelection.viewZ -= dragY / scale();
            clampView();
            return true;
        }
        return boxing || pressing || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (boxing && button == 0) {
            boxing = false;
            if (Math.abs(mouseX - pressX) < 3 && Math.abs(mouseY - pressY) < 3) { // a shift-click, not a box
                List<Entry> hit = under(mouseX, mouseY);
                if (!hit.isEmpty()) toggle(hit.get(0).pos());
                return true;
            }
            double x0 = Math.min(pressX, mouseX), x1 = Math.max(pressX, mouseX), y0 = Math.min(pressY, mouseY), y1 = Math.max(pressY, mouseY);
            for (Entry turret : menu.turrets()) {
                double x = screenX(turret.pos().getX() + 0.5), y = screenY(turret.pos().getZ() + 0.5);
                if (x >= x0 && x <= x1 && y >= y0 && y <= y1) HubSelection.selected.add(turret.pos());
            }
            click();
            return true;
        }
        if (pressing && button == 0) { // a click opens the turret under it, a drag only moved the map
            pressing = false;
            if (!panning) {
                List<Entry> hit = under(mouseX, mouseY);
                if (!hit.isEmpty()) open(hit.get(0));
            }
            panning = false;
            return true;
        }
        if (panning && button == 1) {
            panning = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * Map: one zoom level (1x / 2x / 4x) round the block under the cursor per wheel notch or trackpad swipe (a swipe used to
     * run from 1x to 4x at once). Lists: scroll by whole rows, a trackpad's fractions adding up.
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (online() && tab() == Tab.MAP && overMap(mouseX, mouseY)) {
            long now = Util.getMillis();
            if (now - lastScroll > SWIPE_GAP_MS) swipeZoomed = false;
            lastScroll = now;
            if (!swipeZoomed && delta != 0) {
                swipeZoomed = true;
                zoom(mouseX - leftPos - MAP_X, mouseY - topPos - MAP_Y, delta > 0 ? 1 : -1);
            }
            return true;
        }
        if (online() && tab() == Tab.LIST) {
            HubSelection.listScroll = Math.max(0, HubSelection.listScroll - rows(delta));
            return true;
        }
        if (online() && tab() == Tab.BATCH && scrollBatchList((int) mouseX - leftPos, (int) mouseY - topPos, -rows(delta))) return true;
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /** Zooms {@code steps} levels round the point (px, pz) pixels into the map, which stays under the cursor. */
    private void zoom(double px, double pz, int steps) {
        double atX = HubSelection.viewX + px / scale(), atZ = HubSelection.viewZ + pz / scale();
        HubSelection.zoom = Mth.clamp(HubSelection.zoom + steps, 0, ZOOMS.length - 1);
        HubSelection.viewX = atX - px / scale();
        HubSelection.viewZ = atZ - pz / scale();
        clampView();
    }

    /** Whole rows from a scroll delta: a wheel notch is 1, a trackpad sends fractions that add up. */
    private int rows(double delta) {
        scrollRest += delta;
        int rows = (int) scrollRest;
        scrollRest -= rows;
        return rows;
    }

    private boolean onZoomButton(double mouseX, double mouseY) {
        return zoomIn != null && zoomIn.isMouseOver(mouseX, mouseY) || zoomOut != null && zoomOut.isMouseOver(mouseX, mouseY);
    }

    /** List: the checkbox toggles the selection, the rest of the row opens the turret. */
    private boolean clickRow(double x, double y) {
        if (y < LIST_Y || y >= LIST_Y + listRows * ROW_H || x < 8 || x >= imageWidth - 8) return false;
        List<Entry> rows = sorted();
        int index = (int) (y - LIST_Y) / ROW_H + HubSelection.listScroll;
        if (index >= rows.size()) return false;
        if (x < 20) toggle(rows.get(index).pos());
        else open(rows.get(index));
        return true;
    }

    /** Clicking a side tab must not count as clicking outside the window. */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        for (Tab t : Tab.values()) {
            if (overTab(t, mouseX, mouseY)) return false;
        }
        return super.hasClickedOutside(mouseX, mouseY, left, top, button);
    }

    private void toggle(BlockPos pos) {
        if (!HubSelection.selected.remove(pos)) HubSelection.selected.add(pos);
        click();
    }

    private void selectAll() {
        for (Entry turret : menu.turrets()) HubSelection.selected.add(turret.pos());
        click();
    }

    private void clearSelection() {
        HubSelection.selected.clear();
        click();
    }

    /** Asks the server to open the turret's own GUI through the hub; this screen's state waits in HubSelection. */
    private void open(Entry turret) {
        click();
        BastionNetwork.CHANNEL.sendToServer(HubAction.open(turret.pos()));
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(BastionSounds.CONTROL_HUB_UI_SELECT.get(), 1f));
    }

    // --- Overwatch (PLAN "Overwatch Link") --------------------------------------------------------

    /** The one selected turret, or null when none or several are selected. */
    @Nullable
    private Entry selectedOne() {
        if (HubSelection.selected.size() != 1) return null;
        BlockPos pos = HubSelection.selected.iterator().next();
        for (Entry turret : menu.turrets()) if (turret.pos().equals(pos)) return turret;
        return null;
    }

    /** Why the selection can't be taken over (the button shows it), or null when it can. */
    @Nullable
    private Component controlBlock() {
        Entry turret = selectedOne();
        if (turret == null) return Component.translatable("gui.bastion.control_hub.control.pick");
        if (!turret.linked()) return Component.translatable("gui.bastion.control_hub.control.no_link");
        String me = minecraft == null || minecraft.player == null ? "" : minecraft.player.getGameProfile().getName();
        if (!turret.controller().isEmpty() && !turret.controller().equals(me)) {
            return Component.translatable("gui.bastion.control_hub.control.taken", turret.controller());
        }
        // The camera only works where this client has the world loaded (PLAN: no chunks are sent for it).
        if (minecraft == null || minecraft.level == null || !minecraft.level.isLoaded(turret.pos())) {
            return Component.translatable("gui.bastion.control_hub.control.far");
        }
        return null;
    }

    private Component controlLabel() {
        Component block = controlBlock();
        return block != null ? block : Component.translatable("gui.bastion.control_hub.control");
    }

    private void takeControl() {
        Entry turret = selectedOne();
        if (turret == null || controlBlock() != null) return;
        click();
        BastionNetwork.CHANNEL.sendToServer(OverwatchRequest.start(turret.pos()));
    }

    // --- batch ------------------------------------------------------------------------------------

    private void initBatch() {
        Category[] categories = Category.values();
        for (int i = 0; i < categories.length; i++) {
            Category category = categories[i];
            addRenderableWidget(new FlatButton(leftPos + B_LEFT, topPos + 44 + i * 14, bW, 12, () -> {
                Component name = Component.translatable("gui.bastion.category." + category.name().toLowerCase(Locale.ROOT));
                return mix().category(category) == null ? mixed(name) : name;
            }, () -> send(HubEdit.category(category, !Boolean.TRUE.equals(mix().category(category)))), () -> Boolean.TRUE.equals(mix().category(category))));
        }
        addRenderableWidget(new FlatButton(leftPos + B_LEFT, topPos + 125, bW, 12, () -> {
            PlayerMode mode = mix().playerMode();
            return mode == null ? Component.translatable("gui.bastion.control_hub.mixed")
                    : Component.translatable("gui.bastion.player_mode." + mode.name().toLowerCase(Locale.ROOT));
        }, () -> {
            PlayerMode mode = mix().playerMode();
            send(HubEdit.playerMode(PlayerMode.values()[mode == null ? 0 : (mode.ordinal() + 1) % PlayerMode.values().length]));
        }));
        addRenderableWidget(new FlatButton(leftPos + B_LEFT, topPos + 150, bW, 12, () -> {
            Priority priority = mix().priority();
            return priority == null ? Component.translatable("gui.bastion.control_hub.mixed")
                    : Component.translatable("gui.bastion.priority." + priority.name().toLowerCase(Locale.ROOT));
        }, () -> {
            Priority priority = mix().priority();
            send(HubEdit.priority(Priority.values()[priority == null ? 0 : (priority.ordinal() + 1) % Priority.values().length]));
        }));
        addRenderableWidget(new FlatButton(leftPos + B_LEFT, topPos + imageHeight - 24, bW / 2 - 1, 13,
                () -> Component.translatable("gui.bastion.control_hub.turn_on"), () -> send(HubEdit.enabled(true))));
        addRenderableWidget(new FlatButton(leftPos + B_LEFT + bW / 2 + 1, topPos + imageHeight - 24, bW / 2 - 1, 13,
                () -> Component.translatable("gui.bastion.control_hub.turn_off"), () -> send(HubEdit.enabled(false))));
        trustedBox = textBox(bRight, 42, bW - 18, trustedDraft, "gui.bastion.trusted.hint");
        trustedBox.setMaxLength(16);
        trustedBox.setFilter(s -> s.matches("[A-Za-z0-9_]*"));
        trustedBox.setResponder(s -> trustedDraft = s);
        addRenderableWidget(new FlatButton(leftPos + bRight + bW - 16, topPos + 42, 16, 12, () -> Component.literal("+"), this::addTrusted));
        searchBox = textBox(bRight, ruleY - 15, bW, search, "gui.bastion.rules.hint");
        searchBox.setMaxLength(48);
        searchBox.setResponder(s -> {
            search = s;
            ruleScroll = 0;
        });
    }

    /** Borderless text field; its frame is drawn in batchFrames. */
    private EditBox textBox(int x, int y, int width, String value, String hint) {
        EditBox box = new EditBox(font, leftPos + x + 3, topPos + y + 2, width - 6, 10, Component.translatable(hint));
        box.setBordered(false);
        box.setTextColor(TEXT);
        box.setValue(value);
        box.setHint(Component.translatable(hint).withStyle(ChatFormatting.DARK_GRAY));
        return addRenderableWidget(box);
    }

    private void batchFrames(GuiGraphics graphics) {
        frame(graphics, leftPos + bRight, topPos + 42, bW - 18, 12);
        frame(graphics, leftPos + bRight, topPos + ruleY - 15, bW, 12);
        frame(graphics, leftPos + bRight, topPos + TRUSTED_Y - 1, bW, trustedLines * LINE + 2);
        frame(graphics, leftPos + bRight, topPos + ruleY - 1, bW, ruleLines * LINE + 2);
    }

    /** Selected turrets still in the roster, in roster order. */
    private List<BlockPos> targets() {
        List<BlockPos> targets = new ArrayList<>();
        for (Entry turret : menu.turrets()) {
            if (HubSelection.selected.contains(turret.pos())) targets.add(turret.pos());
        }
        return targets;
    }

    /** What the selected turrets' targeting has in common (turrets whose targeting has arrived). */
    private FilterMix mix() {
        List<TargetFilter> filters = new ArrayList<>();
        for (BlockPos pos : targets()) {
            TargetFilter filter = menu.filter(pos);
            if (filter != null) filters.add(filter);
        }
        return new FilterMix(filters);
    }

    /** One change to every selected turret: shown on this screen's copies at once, then sent; the next roster confirms it. */
    private void send(HubEdit edit) {
        List<BlockPos> targets = targets();
        if (targets.isEmpty() || !edit.valid()) return;
        for (BlockPos pos : targets) {
            TargetFilter filter = menu.filter(pos);
            if (filter != null) edit.applyTo(filter);
        }
        BastionNetwork.CHANNEL.sendToServer(HubAction.edit(targets, edit));
        click();
    }

    private void addTrusted() {
        if (trustedDraft.isBlank()) return;
        send(HubEdit.trust(trustedDraft.trim(), true));
        trustedDraft = "";
        trustedBox.setValue("");
    }

    private Component mixed(Component name) {
        return name.copy().append(" · ").append(Component.translatable("gui.bastion.control_hub.mixed"));
    }

    /** The search's matches, or every mob some selected turret has a rule for while the search is empty. */
    private List<ResourceLocation> ruleRows(FilterMix mix) {
        return search.isBlank() ? mix.ruleIds() : MobList.matching(search);
    }

    private boolean inColumn(int x, int y, int rowY) {
        return x >= bRight && x < bRight + bW && y >= rowY && y < rowY + LINE;
    }

    private void renderBatchLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        List<BlockPos> targets = targets();
        if (targets.isEmpty()) {
            centered(graphics, Component.translatable("gui.bastion.control_hub.batch.none"), imageHeight / 2, AMBER);
            return;
        }
        FilterMix mix = mix();
        fit(graphics, Component.translatable("gui.bastion.control_hub.batch.count", targets.size()), B_LEFT, 32, bW, TEXT);
        fit(graphics, Component.translatable("gui.bastion.targeting.players"), B_LEFT, 116, bW, MUTED);
        fit(graphics, Component.translatable("gui.bastion.targeting.priority"), B_LEFT, 141, bW, MUTED);
        long on = menu.turrets().stream().filter(turret -> HubSelection.selected.contains(turret.pos()) && turret.enabled()).count();
        fit(graphics, Component.translatable("gui.bastion.control_hub.on_count", on, targets.size()), B_LEFT, imageHeight - 34, bW, MUTED);
        fit(graphics, Component.translatable("gui.bastion.targeting.trusted"), bRight, 32, bW, MUTED);
        fit(graphics, Component.translatable("gui.bastion.targeting.rules"), bRight, ruleY - 25, bW, MUTED);

        List<Map.Entry<String, Integer>> names = new ArrayList<>(mix.trusted().entrySet());
        trustedScroll = Mth.clamp(trustedScroll, 0, Math.max(0, names.size() - trustedLines));
        for (int row = 0; row < trustedLines && row + trustedScroll < names.size(); row++) {
            Map.Entry<String, Integer> name = names.get(row + trustedScroll);
            int y = TRUSTED_Y + row * LINE;
            boolean hover = inColumn(mouseX, mouseY, y);
            Component text = Component.literal(name.getKey());
            if (name.getValue() < mix.size()) text = text.copy().append(" ").append(Component.translatable("gui.bastion.control_hub.partial"));
            fit(graphics, text, bRight + 3, y + 2, bW - 16, hover ? RED : TEXT);
            if (hover) graphics.drawString(font, "x", bRight + bW - 9, y + 2, RED, false);
        }
        if (names.isEmpty()) fit(graphics, Component.translatable("gui.bastion.trusted.empty"), bRight + 3, TRUSTED_Y + 2, bW - 6, MUTED);

        List<ResourceLocation> rules = ruleRows(mix);
        ruleScroll = Mth.clamp(ruleScroll, 0, Math.max(0, rules.size() - ruleLines));
        for (int row = 0; row < ruleLines && row + ruleScroll < rules.size(); row++) {
            ResourceLocation id = rules.get(row + ruleScroll);
            int y = ruleY + row * LINE, count = mix.ruleCount(id);
            Rule rule = mix.rule(id);
            boolean partial = count > 0 && rule == null;
            int light = count == 0 ? 0xFF3C4550 : partial ? 0xFF000000 | AMBER : rule == Rule.ALWAYS ? 0xFF7CFF8A : 0xFFFF5A4F;
            graphics.fill(bRight + 3, y + 3, bRight + 8, y + 8, light);
            Component text = MobList.name(id);
            if (partial) text = text.copy().append(" ").append(Component.translatable("gui.bastion.control_hub.partial"));
            fit(graphics, text, bRight + 11, y + 2, bW - 14, inColumn(mouseX, mouseY, y) ? ACCENT : TEXT);
        }
        if (rules.isEmpty()) {
            fit(graphics, Component.translatable(search.isBlank() ? "gui.bastion.rules.empty" : "gui.bastion.rules.none"), bRight + 3, ruleY + 2, bW - 6, MUTED);
        }
    }

    /** A trusted row removes that name from all; a rule row cycles none/mixed -> always -> never -> none for all. */
    private boolean clickBatchList(int x, int y) {
        if (targets().isEmpty()) return false;
        FilterMix mix = mix();
        if (y >= TRUSTED_Y && y < TRUSTED_Y + trustedLines * LINE && inColumn(x, y, y)) {
            List<String> names = new ArrayList<>(mix.trusted().keySet());
            int index = (y - TRUSTED_Y) / LINE + trustedScroll;
            if (index < names.size()) send(HubEdit.trust(names.get(index), false));
            return true;
        }
        if (y >= ruleY && y < ruleY + ruleLines * LINE && inColumn(x, y, y)) {
            List<ResourceLocation> rows = ruleRows(mix);
            int index = (y - ruleY) / LINE + ruleScroll;
            if (index < rows.size()) {
                ResourceLocation id = rows.get(index);
                Rule rule = mix.rule(id);
                send(HubEdit.rule(id, rule == null ? Rule.ALWAYS : rule == Rule.ALWAYS ? Rule.NEVER : null));
            }
            return true;
        }
        return false;
    }

    private boolean scrollBatchList(int x, int y, int step) {
        if (x < bRight || x >= bRight + bW) return false;
        if (y >= TRUSTED_Y && y < TRUSTED_Y + trustedLines * LINE) {
            trustedScroll += step; // clamped when drawn
            return true;
        }
        if (y >= ruleY && y < ruleY + ruleLines * LINE) {
            ruleScroll += step;
            return true;
        }
        return false;
    }

    /** Typing in a text box must not close the screen (inventory key) or swap hotbar slots (number keys). */
    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (getFocused() instanceof EditBox box && box.isFocused() && key != GLFW.GLFW_KEY_ESCAPE) {
            if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && box == trustedBox) addTrusted();
            else box.keyPressed(key, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    // --- helpers ----------------------------------------------------------------------------------

    private boolean over(int x, int y, int w, int h, double mouseX, double mouseY) {
        return mouseX >= leftPos + x && mouseX < leftPos + x + w && mouseY >= topPos + y && mouseY < topPos + y + h;
    }

    private boolean overTab(Tab t, double mouseX, double mouseY) {
        int x = leftPos + TAB_X, y = topPos + TAB_Y + t.ordinal() * TAB_GAP;
        return mouseX >= x && mouseX < x + TAB_SIZE - 2 && mouseY >= y && mouseY < y + TAB_SIZE;
    }

    /** A frame at (x, y) in the current pose's coordinates. */
    private static void frame(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, FlatButton.EDGE);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, FlatButton.DARK);
    }

    /** Lines centred across the panel from {@code y}, wrapped rather than shrunk. */
    private void centered(GuiGraphics graphics, Component text, int y, int colour) {
        List<FormattedCharSequence> lines = font.split(text, imageWidth - 32);
        for (int i = 0; i < lines.size(); i++) graphics.drawString(font, lines.get(i), (imageWidth - font.width(lines.get(i))) / 2, y + i * 10, colour, false);
    }

    /** The scale that fits the widest of {@code texts} into {@code maxWidth}: one size for a whole column. */
    private float scaleFor(List<Component> texts, int maxWidth) {
        int widest = texts.stream().mapToInt(font::width).max().orElse(0);
        return widest <= maxWidth ? 1 : maxWidth / (float) widest;
    }

    private void scaled(GuiGraphics graphics, Component text, int x, int y, float scale, int colour) {
        if (scale >= 1) {
            graphics.drawString(font, text, x, y, colour, false);
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x, y + (1 - scale) * 4, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, text, 0, 0, colour, false);
        graphics.pose().popPose();
    }

    /**
     * The panel at any size, as tools/gen_textures.py panel() paints the old 256 x 200 one: shaded body, edge line,
     * highlight along the top and left, corners cut by three pixels.
     */
    private static void panel(GuiGraphics graphics, int x, int y, int w, int h) {
        int top = 0xFF20262D, bottom = 0xFF1D2228, edge = FlatButton.EDGE; // body: UI_BG x1.08 at the top, x0.96 at the bottom
        graphics.fillGradient(x + 3, y + 1, x + w - 3, y + h - 1, top, bottom);
        graphics.fillGradient(x + 1, y + 3, x + 3, y + h - 3, top, bottom);
        graphics.fillGradient(x + w - 3, y + 3, x + w - 1, y + h - 3, top, bottom);
        graphics.fill(x + 3, y, x + w - 3, y + 1, edge);
        graphics.fill(x + 3, y + h - 1, x + w - 3, y + h, edge);
        graphics.fill(x, y + 3, x + 1, y + h - 3, edge);
        graphics.fill(x + w - 1, y + 3, x + w, y + h - 3, edge);
        graphics.fill(x + 3, y + 1, x + w - 3, y + 2, FlatButton.LIGHT);
        graphics.fill(x + 1, y + 3, x + 2, y + h - 3, FlatButton.LIGHT);
        for (int cx : new int[]{x, x + w - 1}) { // each cut corner: two edge pixels on the diagonal, body inside them
            for (int cy : new int[]{y, y + h - 1}) {
                int dx = cx == x ? 1 : -1, dy = cy == y ? 1 : -1;
                graphics.fill(cx + 2 * dx, cy + dy, cx + 2 * dx + 1, cy + dy + 1, edge);
                graphics.fill(cx + dx, cy + 2 * dy, cx + dx + 1, cy + 2 * dy + 1, edge);
                graphics.fill(cx + 2 * dx, cy + 2 * dy, cx + 2 * dx + 1, cy + 2 * dy + 1, dy > 0 ? top : bottom);
            }
        }
    }

    private static String number(int value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    /** Text scaled down (never up) to fit maxWidth, as in TurretScreen. */
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
}
