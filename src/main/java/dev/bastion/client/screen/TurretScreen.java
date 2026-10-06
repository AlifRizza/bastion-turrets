package dev.bastion.client.screen;

import dev.bastion.Bastion;
import dev.bastion.config.BastionConfig;
import dev.bastion.menu.TurretMenu;
import dev.bastion.network.BastionNetwork;
import dev.bastion.network.TurretConfigUpdate;
import dev.bastion.registry.BastionItems;
import dev.bastion.registry.BastionWeaponTypes;
import dev.bastion.turret.TurretInventory;
import dev.bastion.turret.TurretState;
import dev.bastion.turret.targeting.TargetFilter.Category;
import dev.bastion.turret.targeting.TargetFilter.Rule;
import dev.bastion.turret.targeting.TargetFilter;
import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponModuleItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Turret GUI, PLAN 4.7: Status (slots, HP, heat, state, on/off), Targeting (categories, player mode,
 * priority, redstone, trusted players, per-entity rules) and Info (final stats). Every settings change
 * sends the whole configuration in one TurretConfigUpdate.
 */
public class TurretScreen extends AbstractContainerScreen<TurretMenu> {
    private static final ResourceLocation TEXTURE = Bastion.id("textures/gui/turret.png");
    private static final ResourceLocation PLAIN = Bastion.id("textures/gui/turret_plain.png");
    // Sprite positions inside TEXTURE, see tools/gen_textures.py turret_gui().
    private static final int SLOT_U = 216, LOCKED_SLOT_U = 234, TAB_U = 216, TAB_V = 18, TAB_ACTIVE_V = 42;
    private static final int TEXT = 0xD8E2EC, MUTED = 0x7F8C9A, ACCENT = 0x9FC4E8, GREEN = 0x7CFF8A, RED = 0xFF5A4F, AMBER = 0xFFB347;
    private static final int GROUP_LABEL_Y = 25;
    // Status tab gauges sit between the weapon bay and the ammo grid.
    private static final int BAR_Y = 37, BAR_H = 54, HP_BAR_X = 48, HEAT_BAR_X = 56, BAR_W = 6;
    // State lamp, centred under the weapon bay.
    private static final int LAMP_SIZE = 8, LAMP_X = 22, LAMP_Y = 79;
    // Targeting tab: settings on the left, the two lists on the right.
    private static final int LEFT_X = 10, RIGHT_X = 112, COLUMN_W = 94, ROW_H = 11;
    private static final int TRUSTED_LIST_Y = 50, TRUSTED_ROWS = 4, RULE_LIST_Y = 130, RULE_ROWS = 5;
    private static final int TAB_SIZE = 24, TAB_X = -23, TAB_Y = 8, TAB_GAP = 26;

    private enum Tab {STATUS, TARGETING, INFO}

    private static List<EntityType<?>> livingTypes;

    private Tab tab = Tab.STATUS;
    @Nullable
    private FlatButton fireModeButton;
    private EditBox trustedBox, searchBox;
    private String trustedDraft = "", search = "";
    private int trustedScroll, ruleScroll;

    public TurretScreen(TurretMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 216;
        imageHeight = 200;
        titleLabelX = 10;
        titleLabelY = 7;
        inventoryLabelX = TurretMenu.PLAYER_INV_X - 1;
        inventoryLabelY = 103;
    }

    @Override
    protected void init() {
        super.init();
        fireModeButton = null;
        menu.slotsVisible = tab == Tab.STATUS;
        addRenderableWidget(new FlatButton(leftPos + imageWidth - 38, topPos + 5, 28, 11,
                () -> Component.translatable(menu.enabled ? "gui.bastion.turret.on" : "gui.bastion.turret.off"),
                () -> {
                    menu.enabled = !menu.enabled;
                    sendConfig();
                }, () -> menu.enabled));
        if (tab == Tab.STATUS) {
            // Fire mode, under the module slots; only for weapons that can salvo (Missile Launcher).
            fireModeButton = addRenderableWidget(new FlatButton(leftPos + TurretMenu.MODIFIER_X - 1, topPos + 79, 56, 13,
                    () -> Component.translatable(menu.salvo ? "gui.bastion.fire_mode.salvo" : "gui.bastion.fire_mode.single"),
                    () -> {
                        menu.salvo = !menu.salvo;
                        sendConfig();
                    }));
            fireModeButton.visible = supportsSalvo();
        }
        if (tab == Tab.TARGETING) initTargeting();
    }

    private boolean supportsSalvo() {
        WeaponData data = menu.turret().weaponData();
        return data != null && data.params().containsKey("salvo_interval");
    }

    /** The weapon can change while the screen is open; the fire mode button follows it. */
    @Override
    protected void containerTick() {
        super.containerTick();
        if (fireModeButton != null) fireModeButton.visible = tab == Tab.STATUS && supportsSalvo();
    }

    private void initTargeting() {
        TargetFilter filter = menu.filter;
        Category[] categories = Category.values();
        for (int i = 0; i < categories.length; i++) {
            Category category = categories[i];
            addRenderableWidget(new FlatButton(leftPos + LEFT_X, topPos + 34 + i * 14, COLUMN_W, 12,
                    () -> Component.translatable("gui.bastion.category." + key(category)),
                    () -> {
                        filter.setCategory(category, !filter.category(category));
                        sendConfig();
                    }, () -> filter.category(category)));
        }
        addRenderableWidget(new FlatButton(leftPos + LEFT_X, topPos + 116, COLUMN_W, 13,
                () -> Component.translatable("gui.bastion.player_mode." + key(filter.playerMode)),
                () -> {
                    filter.playerMode = next(filter.playerMode);
                    sendConfig();
                }));
        addRenderableWidget(new FlatButton(leftPos + LEFT_X, topPos + 143, COLUMN_W, 13,
                () -> Component.translatable("gui.bastion.priority." + key(filter.priority)),
                () -> {
                    filter.priority = next(filter.priority);
                    sendConfig();
                }));
        addRenderableWidget(new FlatButton(leftPos + LEFT_X, topPos + 170, COLUMN_W, 13,
                () -> Component.translatable(menu.redstoneInverted ? "gui.bastion.redstone.enables" : "gui.bastion.redstone.disables"),
                () -> {
                    menu.redstoneInverted = !menu.redstoneInverted;
                    sendConfig();
                }));

        trustedBox = textBox(leftPos + RIGHT_X, topPos + 34, COLUMN_W - 18, trustedDraft, "gui.bastion.trusted.hint");
        trustedBox.setMaxLength(16);
        trustedBox.setFilter(s -> s.matches("[A-Za-z0-9_]*"));
        trustedBox.setResponder(s -> trustedDraft = s);
        addRenderableWidget(new FlatButton(leftPos + RIGHT_X + COLUMN_W - 16, topPos + 34, 16, 12, () -> Component.literal("+"), this::addTrusted));

        searchBox = textBox(leftPos + RIGHT_X, topPos + 114, COLUMN_W, search, "gui.bastion.rules.hint");
        searchBox.setMaxLength(48);
        searchBox.setResponder(s -> {
            search = s;
            ruleScroll = 0;
        });
    }

    /** Borderless vanilla text field; the frame around it is drawn in renderBg. */
    private EditBox textBox(int x, int y, int width, String value, String hint) {
        EditBox box = new EditBox(font, x + 3, y + 2, width - 6, 10, Component.translatable(hint));
        box.setBordered(false);
        box.setTextColor(TEXT);
        box.setValue(value);
        box.setHint(Component.translatable(hint).withStyle(ChatFormatting.DARK_GRAY));
        return addRenderableWidget(box);
    }

    private void addTrusted() {
        String name = trustedDraft.trim().toLowerCase(Locale.ROOT);
        if (name.isEmpty() || menu.filter.trusted.size() >= 32) return;
        menu.filter.trusted.add(name);
        trustedDraft = "";
        trustedBox.setValue("");
        sendConfig();
    }

    private void sendConfig() {
        BastionNetwork.CHANNEL.sendToServer(new TurretConfigUpdate(menu.pos(), menu.filter, menu.enabled, menu.redstoneInverted, menu.salvo));
    }

    private void switchTab(Tab next) {
        if (next == tab) return;
        tab = next;
        trustedScroll = ruleScroll = 0;
        rebuildWidgets();
    }

    // --- rendering --------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTabs(graphics, mouseX, mouseY);
        if (tab == Tab.STATUS) renderStatusTooltips(graphics, mouseX, mouseY);
        if (tab == Tab.TARGETING) renderListTooltips(graphics, mouseX, mouseY);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(tab == Tab.STATUS ? TEXTURE : PLAIN, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        if (tab == Tab.STATUS) {
            // Ammo and modifier slots: open frame when the tier unlocks them, hatched frame while locked.
            for (int i = TurretInventory.AMMO_START; i < TurretInventory.SIZE; i++) {
                Slot slot = menu.slots.get(i);
                graphics.blit(TEXTURE, leftPos + slot.x - 1, topPos + slot.y - 1, slot.isActive() ? SLOT_U : LOCKED_SLOT_U, 0, 18, 18);
            }
            float hp = menu.maxHealth() > 0 ? menu.health() / menu.maxHealth() : 1;
            gauge(graphics, HP_BAR_X, hp, hp < 0.3f ? 0xFFFF5A4F : 0xFF7CFF8A);
            if (menu.maxEnergy() > 0) { // energy weapons (Tesla Coil) have no heat: the second gauge is their capacitor
                gauge(graphics, HEAT_BAR_X, (float) menu.energy() / menu.maxEnergy(), 0xFF86A8FF);
            } else {
                gauge(graphics, HEAT_BAR_X, menu.maxHeat() > 0 ? menu.heat() / menu.maxHeat() : 0,
                        menu.state() == TurretState.OVERHEAT ? 0xFFFF5A4F : 0xFFFFB347);
            }
        } else if (tab == Tab.TARGETING) {
            frame(graphics, RIGHT_X, 34, COLUMN_W - 18, 12);
            frame(graphics, RIGHT_X, 114, COLUMN_W, 12);
            frame(graphics, RIGHT_X, TRUSTED_LIST_Y - 1, COLUMN_W, TRUSTED_ROWS * ROW_H + 2);
            frame(graphics, RIGHT_X, RULE_LIST_Y - 1, COLUMN_W, RULE_ROWS * ROW_H + 2);
        }
    }

    private void frame(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(leftPos + x, topPos + y, leftPos + x + w, topPos + y + h, FlatButton.EDGE);
        graphics.fill(leftPos + x + 1, topPos + y + 1, leftPos + x + w - 1, topPos + y + h - 1, FlatButton.DARK);
    }

    /** Vertical gauge filling from the bottom. */
    private void gauge(GuiGraphics graphics, int x, float fraction, int colour) {
        frame(graphics, x, BAR_Y, BAR_W, BAR_H);
        int fill = Math.round((BAR_H - 2) * Mth.clamp(fraction, 0, 1));
        int bottom = topPos + BAR_Y + BAR_H - 1;
        graphics.fill(leftPos + x + 1, bottom - fill, leftPos + x + BAR_W - 1, bottom, colour);
    }

    private void renderTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        ItemStack[] icons = {new ItemStack(BastionItems.TURRET_BASE.get()), new ItemStack(BastionItems.TARGETING_AI.get()),
                new ItemStack(BastionItems.TURRET_CONFIGURATOR.get())};
        for (Tab t : Tab.values()) {
            int x = leftPos + TAB_X, y = topPos + TAB_Y + t.ordinal() * TAB_GAP;
            graphics.blit(TEXTURE, x, y, TAB_U, t == tab ? TAB_ACTIVE_V : TAB_V, TAB_SIZE, TAB_SIZE);
            graphics.renderItem(icons[t.ordinal()], x + 5, y + 4);
            if (overTab(t, mouseX, mouseY)) {
                graphics.renderTooltip(font, Component.translatable("gui.bastion.tab." + key(t)), mouseX, mouseY);
            }
        }
    }

    private boolean overTab(Tab t, double mouseX, double mouseY) {
        int x = leftPos + TAB_X, y = topPos + TAB_Y + t.ordinal() * TAB_GAP;
        return mouseX >= x && mouseX < x + TAB_SIZE - 2 && mouseY >= y && mouseY < y + TAB_SIZE;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        Component tier = Component.translatable("gui.bastion.turret.tier", menu.tier().ordinal() + 1);
        int tierX = imageWidth - 42 - font.width(tier);
        fit(graphics, title, titleLabelX, titleLabelY, tierX - 4 - titleLabelX, TEXT);
        graphics.drawString(font, tier, tierX, titleLabelY, ACCENT, false);
        switch (tab) {
            case STATUS -> renderStatusLabels(graphics);
            case TARGETING -> renderTargetingLabels(graphics, mouseX - leftPos, mouseY - topPos);
            case INFO -> renderInfo(graphics);
        }
    }

    private void renderStatusLabels(GuiGraphics graphics) {
        // Group labels sit above their slots, each in its own column, so longer translations never collide.
        int ammoX = TurretMenu.AMMO_X - 1, modulesX = TurretMenu.MODIFIER_X - 1;
        fit(graphics, Component.translatable("gui.bastion.turret.weapon"), 10, GROUP_LABEL_Y, ammoX - 4 - 10, MUTED);
        fit(graphics, Component.translatable("gui.bastion.turret.ammo"), ammoX, GROUP_LABEL_Y, modulesX - 4 - ammoX, MUTED);
        fit(graphics, Component.translatable("gui.bastion.turret.modules"), modulesX, GROUP_LABEL_Y, imageWidth - 10 - modulesX, MUTED);
        fit(graphics, playerInventoryTitle, inventoryLabelX, inventoryLabelY, imageWidth - 10 - inventoryLabelX, MUTED);
        renderStateLamp(graphics);
    }

    /**
     * State as a status light under the weapon bay, no text (hover for the name): grey off, green idle,
     * cyan tracking, amber firing (pulsing), red overheat / no ammo (blinking).
     */
    private void renderStateLamp(GuiGraphics graphics) {
        TurretState state = menu.state();
        int colour = stateColour(state);
        long time = minecraft.level == null ? 0 : minecraft.level.getGameTime();
        boolean blink = (state == TurretState.OVERHEAT || state == TurretState.NO_AMMO) && time % 20 < 10;
        float glow = switch (state) {
            case DISABLED -> 0;
            case CHARGING, FIRING, COOLDOWN -> 0.55f + 0.45f * Mth.sin(time * 0.9f);
            default -> 0.6f;
        };
        int x = LAMP_X, y = LAMP_Y;
        if (!blink && glow > 0) {
            int halo = Math.round(glow * 0x50) << 24 | colour;
            graphics.fill(x - 3, y - 3, x + LAMP_SIZE + 3, y + LAMP_SIZE + 3, halo);
        }
        graphics.fill(x - 1, y - 1, x + LAMP_SIZE + 1, y + LAMP_SIZE + 1, FlatButton.EDGE);
        graphics.fill(x, y, x + LAMP_SIZE, y + LAMP_SIZE, blink || state == TurretState.DISABLED ? FlatButton.DARK : 0xFF000000 | colour);
        if (!blink && state != TurretState.DISABLED) {
            graphics.fill(x + 1, y + 1, x + 3, y + 3, 0xB0FFFFFF); // specular dot
        }
    }

    /** Draws text scaled down (never up) to fit maxWidth, so no translation can run into its neighbour. */
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

    private static int stateColour(TurretState state) {
        return switch (state) {
            case DISABLED -> MUTED;
            case IDLE -> GREEN;
            case ACQUIRING, AIMING -> 0x4FD8FF;
            case CHARGING, FIRING, COOLDOWN -> AMBER;
            case OVERHEAT, NO_AMMO -> RED;
        };
    }

    private void renderStatusTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = mouseX - leftPos, y = mouseY - topPos;
        if (x >= LAMP_X - 3 && x < LAMP_X + LAMP_SIZE + 3 && y >= LAMP_Y - 3 && y < LAMP_Y + LAMP_SIZE + 3) {
            graphics.renderTooltip(font, Component.translatable("gui.bastion.state." + key(menu.state())), mouseX, mouseY);
            return;
        }
        if (y < BAR_Y || y >= BAR_Y + BAR_H) return;
        if (x >= HP_BAR_X && x < HP_BAR_X + BAR_W) {
            graphics.renderTooltip(font, Component.translatable("gui.bastion.turret.hp", fmt(menu.health()), fmt(menu.maxHealth())), mouseX, mouseY);
        } else if (x >= HEAT_BAR_X && x < HEAT_BAR_X + BAR_W) {
            graphics.renderTooltip(font, menu.maxEnergy() > 0
                    ? Component.translatable("gui.bastion.turret.energy", String.format("%,d", menu.energy()), String.format("%,d", menu.maxEnergy()))
                    : Component.translatable("gui.bastion.turret.heat", fmt(menu.heat()), fmt(menu.maxHeat())), mouseX, mouseY);
        }
    }

    // --- targeting tab ------------------------------------------------------------------------

    private void renderTargetingLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        fit(graphics, Component.translatable("gui.bastion.targeting.targets"), LEFT_X, GROUP_LABEL_Y, COLUMN_W, MUTED);
        fit(graphics, Component.translatable("gui.bastion.targeting.players"), LEFT_X, 107, COLUMN_W, MUTED);
        fit(graphics, Component.translatable("gui.bastion.targeting.priority"), LEFT_X, 134, COLUMN_W, MUTED);
        fit(graphics, Component.translatable("gui.bastion.targeting.redstone"), LEFT_X, 161, COLUMN_W, MUTED);
        fit(graphics, Component.translatable("gui.bastion.targeting.trusted"), RIGHT_X, GROUP_LABEL_Y, COLUMN_W, MUTED);
        fit(graphics, Component.translatable("gui.bastion.targeting.rules"), RIGHT_X, 104, COLUMN_W, MUTED);

        List<String> names = new ArrayList<>(menu.filter.trusted);
        for (int row = 0; row < TRUSTED_ROWS && row + trustedScroll < names.size(); row++) {
            int y = TRUSTED_LIST_Y + row * ROW_H;
            boolean hover = inRow(mouseX, mouseY, y);
            graphics.drawString(font, font.plainSubstrByWidth(names.get(row + trustedScroll), COLUMN_W - 16), RIGHT_X + 3, y + 2,
                    hover ? RED : TEXT, false);
            if (hover) graphics.drawString(font, "x", RIGHT_X + COLUMN_W - 9, y + 2, RED, false);
        }
        if (names.isEmpty()) fit(graphics, Component.translatable("gui.bastion.trusted.empty"), RIGHT_X + 3, TRUSTED_LIST_Y + 2, COLUMN_W - 6, MUTED);

        List<ResourceLocation> rules = ruleRows();
        for (int row = 0; row < RULE_ROWS && row + ruleScroll < rules.size(); row++) {
            ResourceLocation id = rules.get(row + ruleScroll);
            int y = RULE_LIST_Y + row * ROW_H;
            Rule rule = menu.filter.rules.get(id);
            int light = rule == Rule.ALWAYS ? 0xFF7CFF8A : rule == Rule.NEVER ? 0xFFFF5A4F : 0xFF3C4550;
            graphics.fill(RIGHT_X + 3, y + 3, RIGHT_X + 8, y + 8, light);
            graphics.drawString(font, font.plainSubstrByWidth(entityName(id).getString(), COLUMN_W - 14), RIGHT_X + 11, y + 2,
                    inRow(mouseX, mouseY, y) ? ACCENT : TEXT, false);
        }
        if (rules.isEmpty()) {
            fit(graphics, Component.translatable(search.isEmpty() ? "gui.bastion.rules.empty" : "gui.bastion.rules.none"),
                    RIGHT_X + 3, RULE_LIST_Y + 2, COLUMN_W - 6, MUTED);
        }
    }

    private boolean inRow(int x, int y, int rowY) {
        return x >= RIGHT_X && x < RIGHT_X + COLUMN_W && y >= rowY && y < rowY + ROW_H;
    }

    private void renderListTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = mouseX - leftPos, y = mouseY - topPos;
        int row = (y - RULE_LIST_Y) / ROW_H;
        List<ResourceLocation> rules = ruleRows();
        if (y >= RULE_LIST_Y && row < RULE_ROWS && row + ruleScroll < rules.size() && inRow(x, y, RULE_LIST_Y + row * ROW_H)) {
            ResourceLocation id = rules.get(row + ruleScroll);
            Rule rule = menu.filter.rules.get(id);
            Component state = rule == null
                    ? Component.translatable("gui.bastion.rule.default", Component.translatable("gui.bastion.category." + key(categoryOf(id))))
                    : Component.translatable("gui.bastion.rule." + key(rule));
            graphics.renderComponentTooltip(font, List.of(entityName(id), state.copy().withStyle(ChatFormatting.GRAY),
                    Component.translatable("gui.bastion.rule.click").withStyle(ChatFormatting.DARK_GRAY)), mouseX, mouseY);
        }
    }

    /** The current search's matches, or the existing rules while the search box is empty. */
    private List<ResourceLocation> ruleRows() {
        if (search.isBlank()) return new ArrayList<>(menu.filter.rules.keySet());
        String query = search.trim().toLowerCase(Locale.ROOT);
        List<ResourceLocation> rows = new ArrayList<>();
        for (EntityType<?> type : livingTypes()) {
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
            if (id != null && (id.toString().contains(query) || type.getDescription().getString().toLowerCase(Locale.ROOT).contains(query))) {
                rows.add(id);
            }
        }
        return rows;
    }

    /** Every registered mob: living entity types are exactly the ones with default attributes. */
    private static List<EntityType<?>> livingTypes() {
        if (livingTypes == null) {
            livingTypes = ForgeRegistries.ENTITY_TYPES.getValues().stream()
                    .filter(type -> type != EntityType.PLAYER && DefaultAttributes.hasSupplier(type))
                    .sorted(Comparator.comparing(type -> type.getDescription().getString()))
                    .toList();
        }
        return livingTypes;
    }

    private static Component entityName(ResourceLocation id) {
        return ForgeRegistries.ENTITY_TYPES.containsKey(id) ? ForgeRegistries.ENTITY_TYPES.getValue(id).getDescription() : Component.literal(id.toString());
    }

    /**
     * Category a mob type falls under without a rule. ponytail: approximated from its spawn group, since the
     * real check needs an entity instance; bosses and neutral mobs show as hostile/passive here.
     */
    private static Category categoryOf(ResourceLocation id) {
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(id)) return Category.PASSIVE;
        return ForgeRegistries.ENTITY_TYPES.getValue(id).getCategory().isFriendly() ? Category.PASSIVE : Category.HOSTILE;
    }

    // --- info tab -----------------------------------------------------------------------------

    private void renderInfo(GuiGraphics graphics) {
        ItemStack weapon = menu.turret().getStackInSlot(TurretInventory.WEAPON);
        WeaponData data = menu.turret().weaponData();
        if (data == null || !(weapon.getItem() instanceof WeaponModuleItem module)) {
            fit(graphics, Component.translatable("gui.bastion.info.no_weapon"), 10, 30, imageWidth - 20, MUTED);
            return;
        }
        graphics.renderItem(weapon, 10, 25);
        fit(graphics, weapon.getHoverName(), 30, 29, imageWidth - 10 - 30, TEXT);

        StatSheet stats = StatSheet.of(data, menu.tier(), menu.turret().modifierEffect());
        String[][] left = {
                {"damage", fmt(stats.damage())},
                {"fire_rate", fmt(20f / stats.fireInterval()) + "/s"},
                {"range", fmt(stats.range())},
                {"turn_speed", fmt(stats.turnSpeed() * 20) + "°/s"},
                // The Laser Rifle passes through every valid target on its line.
                {"pierce", data.type().equals(BastionWeaponTypes.LASER_RIFLE.getId()) ? "∞" : String.valueOf(stats.pierce())},
                {"ammo_save", Math.round(stats.ammoSaveChance() * 100) + "%"},
        };
        String[][] right = data.params().containsKey("energy_per_shot") ? new String[][]{ // energy weapons: capacitor, no heat
                {"energy_per_shot", fe(data.param("energy_per_shot")) + " FE"},
                {"energy_capacity", fe(data.param("energy_capacity")) + " FE"},
                {"max_input", fe(data.param("max_input")) + " FE/t"},
                {"max_hp", fmt(menu.maxHealth())},
                {"regen", fmt(BastionConfig.regenPerSecond(menu.tier())) + "/s"},
                {"bolts", String.valueOf(Math.round(data.param("bolts")))},
        } : new String[][]{
                {"heat_per_shot", fmt(stats.heatPerShot())},
                {"max_heat", fmt(stats.maxHeat())},
                {"cooling", fmt(stats.heatDissipationPerTick() * 20) + "/s"},
                {"max_hp", fmt(menu.maxHealth())},
                {"regen", fmt(BastionConfig.regenPerSecond(menu.tier())) + "/s"},
                // Weapons that load ahead (Missile Launcher) show their per-missile reload instead of ammo per shot.
                data.params().containsKey("reload_ticks") ? new String[]{"reload", fmt(data.param("reload_ticks") / 20f) + "s"}
                        : new String[]{"ammo", String.valueOf(data.ammoPerShot())},
        };
        statColumn(graphics, left, LEFT_X);
        statColumn(graphics, right, RIGHT_X);

        graphics.fill(10, 120, imageWidth - 10, 121, 0xFF3C4550);
        fit(graphics, Component.translatable("gui.bastion.info.ability"), 10, 126, imageWidth - 20, MUTED);
        List<FormattedCharSequence> lines = font.split(Component.translatable("tooltip.bastion.weapon." + module.modelName() + ".ability"),
                imageWidth - 20);
        for (int i = 0; i < lines.size(); i++) graphics.drawString(font, lines.get(i), 10, 138 + i * 10, TEXT, false);
    }

    /** 3000 -> "3k", 1500 -> "1.5k", 800 -> "800": energy figures that fit the info columns. */
    private static String fe(float value) {
        return value >= 1000 ? fmt(value / 1000f) + "k" : fmt(value);
    }

    private void statColumn(GuiGraphics graphics, String[][] rows, int x) {
        for (int i = 0; i < rows.length; i++) {
            int y = 50 + i * 11;
            int valueWidth = font.width(rows[i][1]);
            fit(graphics, Component.translatable("gui.bastion.info." + rows[i][0]), x, y, COLUMN_W - valueWidth - 4, MUTED);
            graphics.drawString(font, rows[i][1], x + COLUMN_W - valueWidth, y, TEXT, false);
        }
    }

    // --- input --------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Tab t : Tab.values()) {
            if (overTab(t, mouseX, mouseY)) {
                switchTab(t);
                return true;
            }
        }
        if (tab == Tab.TARGETING && button == 0 && clickList((int) mouseX - leftPos, (int) mouseY - topPos)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean clickList(int x, int y) {
        if (y >= TRUSTED_LIST_Y && y < TRUSTED_LIST_Y + TRUSTED_ROWS * ROW_H && inRow(x, y, y)) {
            List<String> names = new ArrayList<>(menu.filter.trusted);
            int index = (y - TRUSTED_LIST_Y) / ROW_H + trustedScroll;
            if (index < names.size()) {
                menu.filter.trusted.remove(names.get(index));
                sendConfig();
            }
            return true;
        }
        if (y >= RULE_LIST_Y && y < RULE_LIST_Y + RULE_ROWS * ROW_H && inRow(x, y, y)) {
            List<ResourceLocation> rows = ruleRows();
            int index = (y - RULE_LIST_Y) / ROW_H + ruleScroll;
            if (index < rows.size()) {
                // none -> always -> never -> none
                ResourceLocation id = rows.get(index);
                Rule rule = menu.filter.rules.get(id);
                if (rule == null) menu.filter.rules.put(id, Rule.ALWAYS);
                else if (rule == Rule.ALWAYS) menu.filter.rules.put(id, Rule.NEVER);
                else menu.filter.rules.remove(id);
                sendConfig();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (tab == Tab.TARGETING) {
            int x = (int) mouseX - leftPos, y = (int) mouseY - topPos;
            int step = delta > 0 ? -1 : 1;
            if (x >= RIGHT_X && x < RIGHT_X + COLUMN_W && y >= TRUSTED_LIST_Y && y < TRUSTED_LIST_Y + TRUSTED_ROWS * ROW_H) {
                trustedScroll = Mth.clamp(trustedScroll + step, 0, Math.max(0, menu.filter.trusted.size() - TRUSTED_ROWS));
                return true;
            }
            if (x >= RIGHT_X && x < RIGHT_X + COLUMN_W && y >= RULE_LIST_Y && y < RULE_LIST_Y + RULE_ROWS * ROW_H) {
                ruleScroll = Mth.clamp(ruleScroll + step, 0, Math.max(0, ruleRows().size() - RULE_ROWS));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
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

    /** Clicking a side tab must not count as clicking outside the window, which would drop the held stack. */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        for (Tab t : Tab.values()) {
            if (overTab(t, mouseX, mouseY)) return false;
        }
        return super.hasClickedOutside(mouseX, mouseY, left, top, button);
    }

    // --- helpers ------------------------------------------------------------------------------

    private static String key(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static <E extends Enum<E>> E next(E value) {
        E[] values = value.getDeclaringClass().getEnumConstants();
        return values[(value.ordinal() + 1) % values.length];
    }

    private static String fmt(float value) {
        return value == Math.round(value) ? String.valueOf(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
    }
}
