package dev.bastion.client.screen;

import dev.bastion.Bastion;
import dev.bastion.menu.FeedHubMenu;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.FeedHubBlockEntity;
import dev.bastion.turret.FeedHubStructure;
import dev.bastion.turret.TurretInventory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * Feed Hub GUI (PLAN "Feed Hub", 2b), side tabs like the turret's: Summary (size, the weapons of the turrets on the
 * structure, FE, ammo totals) and Storage (the structure's slots, 4 rows that scroll with the wheel or the bar).
 */
public class FeedHubScreen extends AbstractContainerScreen<FeedHubMenu> {
    private static final ResourceLocation TEXTURE = Bastion.id("textures/gui/feed_hub.png");
    private static final ResourceLocation PLAIN = Bastion.id("textures/gui/feed_hub_plain.png");
    // Sprites inside TEXTURE, see tools/gen_textures.py feed_hub_gui().
    private static final int LOCKED_U = 192, TAB_U = 192, TAB_V = 18, TAB_ACTIVE_V = 42;
    private static final int TEXT = 0xD8E2EC, MUTED = 0x7F8C9A;
    private static final int TAB_SIZE = 24, TAB_X = -23, TAB_Y = 8, TAB_GAP = 26;
    // Storage: scrollbar right of the grid.
    private static final int BAR_X = 174, BAR_Y = FeedHubMenu.STORAGE_Y - 1, BAR_W = 10, BAR_H = FeedHubMenu.ROWS * 18, KNOB_H = 15;
    // Summary.
    private static final int PAD = 10, ICONS_Y = 46, ICONS_PER_ROW = 6, MAX_ICONS = 12, ENERGY_Y = 92, AMMO_Y = 118, AMMO_COLUMN = 88;

    private enum Tab {SUMMARY, STORAGE}

    private Tab tab = Tab.SUMMARY;
    private boolean dragging;

    public FeedHubScreen(FeedHubMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 192;
        imageHeight = 194;
        inventoryLabelY = FeedHubMenu.PLAYER_INV_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        menu.slotsVisible = tab == Tab.STORAGE;
    }

    private void switchTab(Tab next) {
        if (next == tab) return;
        tab = next;
        rebuildWidgets();
    }

    // --- rendering --------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTabs(graphics, mouseX, mouseY);
        if (tab == Tab.SUMMARY) renderSummaryTooltips(graphics, mouseX, mouseY);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(tab == Tab.STORAGE ? TEXTURE : PLAIN, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        if (tab == Tab.SUMMARY) {
            List<ItemStack> weapons = weapons();
            for (int i = 0; i < weapons.size(); i++) graphics.renderItem(weapons.get(i), leftPos + iconX(i), topPos + iconY(i));
            int width = imageWidth - 2 * PAD;
            frame(graphics, PAD, ENERGY_Y + 11, width, 8);
            int fill = menu.capacity() == 0 ? 0 : (int) ((width - 2) * Math.min(1, (double) menu.energy() / menu.capacity()));
            graphics.fill(leftPos + PAD + 1, topPos + ENERGY_Y + 12, leftPos + PAD + 1 + fill, topPos + ENERGY_Y + 18, 0xFF86A8FF);
            List<FeedHubMenu.AmmoTotal> ammo = menu.ammo();
            for (int i = 0; i < ammo.size(); i++) graphics.renderItem(ammo.get(i).item(), leftPos + ammoX(i), topPos + ammoY(i));
            return;
        }
        for (int i = 0; i < FeedHubMenu.VISIBLE; i++) { // rows past the structure's last slot: hatched
            Slot slot = menu.slots.get(i);
            if (!slot.isActive()) graphics.blit(TEXTURE, leftPos + slot.x - 1, topPos + slot.y - 1, LOCKED_U, 0, 18, 18);
        }
        frame(graphics, BAR_X, BAR_Y, BAR_W, BAR_H);
        int max = menu.maxScroll();
        int knobY = BAR_Y + 1 + (max == 0 ? 0 : (BAR_H - 2 - KNOB_H) * menu.scroll() / max);
        graphics.fill(leftPos + BAR_X + 1, topPos + knobY, leftPos + BAR_X + BAR_W - 1, topPos + knobY + KNOB_H,
                max == 0 ? FlatButton.EDGE : FlatButton.ACCENT);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        fit(graphics, title, 8, 6, imageWidth - 16, TEXT);
        if (tab == Tab.STORAGE) {
            fit(graphics, playerInventoryTitle, 8, inventoryLabelY, imageWidth - 16, MUTED);
            return;
        }
        FeedHubBlockEntity hub = menu.hub();
        if (hub == null) return;
        int width = imageWidth - 2 * PAD;
        FeedHubStructure.Box box = hub.group().box();
        fit(graphics, Component.translatable("gui.bastion.feed_hub.size", box.size(), box.blocks()), PAD, 22, width, TEXT);
        int turrets = hub.feeds().size();
        fit(graphics, turrets == 0 ? Component.translatable("gui.bastion.feed_hub.none") : Component.translatable("gui.bastion.feed_hub.turrets", turrets),
                PAD, 34, width, MUTED);
        fit(graphics, Component.translatable("gui.bastion.feed_hub.energy"), PAD, ENERGY_Y, width / 2 - 4, MUTED);
        Component fe = Component.translatable("gui.bastion.feed_hub.fe", number(menu.energy()), number(menu.capacity()));
        int feWidth = Math.min(font.width(fe), width / 2 + 4);
        fit(graphics, fe, PAD + width - feWidth, ENERGY_Y, feWidth, TEXT);
        fit(graphics, Component.translatable("gui.bastion.feed_hub.ammo"), PAD, AMMO_Y, width, MUTED);
        List<FeedHubMenu.AmmoTotal> ammo = menu.ammo();
        if (ammo.isEmpty()) fit(graphics, Component.translatable("gui.bastion.feed_hub.empty"), PAD, AMMO_Y + 16, width, TEXT);
        for (int i = 0; i < ammo.size(); i++) {
            fit(graphics, Component.literal(number(ammo.get(i).count()) + " ").append(ammo.get(i).item().getHoverName()),
                    ammoX(i) + 18, ammoY(i) + 4, AMMO_COLUMN - 22, TEXT);
        }
    }

    private void renderSummaryTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        List<ItemStack> weapons = weapons();
        for (int i = 0; i < weapons.size(); i++) {
            if (over(iconX(i), iconY(i), 16, 16, mouseX, mouseY)) graphics.renderTooltip(font, weapons.get(i), mouseX, mouseY);
        }
        List<FeedHubMenu.AmmoTotal> ammo = menu.ammo();
        for (int i = 0; i < ammo.size(); i++) {
            if (over(ammoX(i), ammoY(i), AMMO_COLUMN - 4, 16, mouseX, mouseY)) graphics.renderTooltip(font, ammo.get(i).item(), mouseX, mouseY);
        }
    }

    private void renderTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        ItemStack[] icons = {new ItemStack(BastionItems.FEED_HUB.get()), new ItemStack(BastionItems.KINETIC_ROUNDS.get())};
        for (Tab t : Tab.values()) {
            int x = leftPos + TAB_X, y = topPos + TAB_Y + t.ordinal() * TAB_GAP;
            graphics.blit(TEXTURE, x, y, TAB_U, t == tab ? TAB_ACTIVE_V : TAB_V, TAB_SIZE, TAB_SIZE);
            graphics.renderItem(icons[t.ordinal()], x + 5, y + 4);
            if (overTab(t, mouseX, mouseY)) {
                graphics.renderTooltip(font, Component.translatable("gui.bastion.feed_hub.tab." + t.name().toLowerCase(Locale.ROOT)), mouseX, mouseY);
            }
        }
    }

    private static int iconX(int i) {
        return PAD + i % ICONS_PER_ROW * 20;
    }

    private static int iconY(int i) {
        return ICONS_Y + i / ICONS_PER_ROW * 20;
    }

    private static int ammoX(int i) {
        return PAD + i / 4 * AMMO_COLUMN;
    }

    private static int ammoY(int i) {
        return AMMO_Y + 12 + i % 4 * 18;
    }

    private boolean over(int x, int y, int w, int h, double mouseX, double mouseY) {
        return mouseX >= leftPos + x && mouseX < leftPos + x + w && mouseY >= topPos + y && mouseY < topPos + y + h;
    }

    private boolean overTab(Tab t, double mouseX, double mouseY) {
        int x = leftPos + TAB_X, y = topPos + TAB_Y + t.ordinal() * TAB_GAP;
        return mouseX >= x && mouseX < x + TAB_SIZE - 2 && mouseY >= y && mouseY < y + TAB_SIZE;
    }

    private void frame(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(leftPos + x, topPos + y, leftPos + x + w, topPos + y + h, FlatButton.EDGE);
        graphics.fill(leftPos + x + 1, topPos + y + 1, leftPos + x + w - 1, topPos + y + h - 1, FlatButton.DARK);
    }

    private static String number(int value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    /** The weapon of each armed turret on the structure. */
    private List<ItemStack> weapons() {
        FeedHubBlockEntity hub = menu.hub();
        if (hub == null) return List.of();
        return hub.feeds().stream().map(feed -> feed.turret().inventory().getStackInSlot(TurretInventory.WEAPON))
                .filter(stack -> !stack.isEmpty()).limit(MAX_ICONS).toList();
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
        if (tab == Tab.STORAGE && button == 0 && over(BAR_X, BAR_Y, BAR_W, BAR_H, mouseX, mouseY)) {
            dragging = true;
            scrollTo(rowAt(mouseY));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            scrollTo(rowAt(mouseY));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (tab == Tab.STORAGE && menu.maxScroll() > 0) {
            scrollTo(menu.scroll() - (int) Math.signum(delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /** Clicking a side tab must not count as clicking outside the window, which would drop the held stack. */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        for (Tab t : Tab.values()) {
            if (overTab(t, mouseX, mouseY)) return false;
        }
        return super.hasClickedOutside(mouseX, mouseY, left, top, button);
    }

    private int rowAt(double mouseY) {
        double t = (mouseY - topPos - BAR_Y - 1 - KNOB_H / 2.0) / (BAR_H - 2 - KNOB_H);
        return (int) Math.round(Mth.clamp(t, 0, 1) * menu.maxScroll());
    }

    private void scrollTo(int row) {
        int before = menu.scroll();
        menu.setScroll(row);
        if (menu.scroll() != before && minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, menu.scroll());
        }
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
