package dev.bastion.client.overwatch;

import dev.bastion.client.ClientTurret;
import dev.bastion.menu.ControlHubMenu;
import dev.bastion.registry.BastionWeaponTypes;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import dev.bastion.turret.TurretState;
import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponType;
import dev.bastion.weapon.types.MissileLauncherWeapon;
import dev.bastion.weapon.types.RocketLauncherWeapon;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * The Overwatch HUD (PLAN "Overwatch Link"), all drawn here, no vanilla sprites:
 * - scan lines, corner brackets, REC and the in-game time, the turret's name and distance to the hub;
 * - a crosshair that turns red when the shot can't go where it points;
 * - HP, ammo or FE, heat and charge bars, key hints.
 */
public final class OverwatchHud {
    private static final int TEXT = 0xFFD8E2EC, MUTED = 0xFF7F8C9A, ACCENT = 0xFF9FC4E8, RED = 0xFFFF3A30, GREEN = 0xFF7CFF8A,
            AMBER = 0xFFFFB347, BAR_BG = 0xA0101418;
    private static final int MARGIN = 10, BRACKET = 14, BAR_W = 70;

    private OverwatchHud() {
    }

    public static void render(GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        TurretBaseBlockEntity base = OverwatchClient.turret(mc.level);
        if (base == null || mc.options.hideGui) return;
        Font font = mc.font;
        long time = mc.level.getGameTime();
        for (int y = 0; y < height; y += 3) g.fill(0, y, width, y + 1, 0x0C000000); // scan lines
        int band = (int) ((time + partialTick) * 2 % (height + 40)) - 20;
        g.fill(0, band, width, band + 20, 0x08FFFFFF);
        brackets(g, width, height);
        // Every text fits its half of the screen and the hints wrap (user 2026-10-20: text ran off the screen).
        int half = width / 2 - MARGIN - 12;
        if (time % 20 < 12) g.fill(MARGIN + 6, MARGIN + 6, MARGIN + 12, MARGIN + 12, RED);
        fit(g, font, Component.translatable("hud.bastion.overwatch.rec"), MARGIN + 16, MARGIN + 5, half - 10, TEXT, false);
        long day = mc.level.getDayTime();
        String clock = String.format("%02d:%02d", (day / 1000 + 6) % 24, day % 1000 * 60 / 1000);
        fit(g, font, Component.translatable("hud.bastion.overwatch.time", day / 24000 + 1, clock), MARGIN + 6, MARGIN + 17, half, MUTED, false);
        WeaponData data = base.inventory().weaponData();
        Component name = data == null ? Component.translatable("gui.bastion.control_hub.unarmed")
                : Component.translatable("jade.bastion.turret.weapon", base.inventory().getStackInSlot(TurretInventory.WEAPON).getHoverName(),
                Component.translatable("gui.bastion.turret.tier", base.tier().ordinal() + 1));
        fit(g, font, name, width - MARGIN - 6, MARGIN + 5, half, TEXT, true);
        BlockPos hub = OverwatchClient.hubPos();
        if (hub != null) {
            Component distance = Component.translatable("hud.bastion.overwatch.distance", Math.round(Math.sqrt(base.getBlockPos().distSqr(hub))));
            fit(g, font, distance, width - MARGIN - 6, MARGIN + 17, half, MUTED, true);
        }
        crosshair(g, font, width / 2, height / 2);
        marker(g, width, height);
        Options o = mc.options;
        Component hints = Component.translatable("hud.bastion.overwatch.hints", o.keyAttack.getTranslatedKeyMessage(),
                Component.translatable("hud.bastion.overwatch.scroll"), o.keyTogglePerspective.getTranslatedKeyMessage(),
                OverwatchClient.PREVIOUS.getTranslatedKeyMessage(), OverwatchClient.NEXT.getTranslatedKeyMessage(), o.keyShift.getTranslatedKeyMessage());
        List<FormattedCharSequence> lines = font.split(hints, Math.max(40, width - 2 * (MARGIN + 6)));
        int hintsTop = height - MARGIN - 4 - (lines.size() - 1) * 10;
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), (width - font.width(lines.get(i))) / 2, hintsTop + i * 10, MUTED, false);
        }
        bars(g, font, base, data, partialTick, time, hintsTop - 6);
        if (OverwatchClient.zoom() > 1) {
            fit(g, font, Component.translatable("hud.bastion.overwatch.zoom", OverwatchClient.zoom()), width - MARGIN - 6, hintsTop - 16, half, ACCENT, true);
        }
    }

    /** {@code text} at (x, y), shrunk (never grown) to {@code maxWidth}; {@code right}: x is its right edge. */
    private static void fit(GuiGraphics g, Font font, Component text, int x, int y, int maxWidth, int colour, boolean right) {
        int w = font.width(text);
        float scale = maxWidth <= 0 ? 0 : Math.min(1, maxWidth / (float) w);
        if (scale <= 0) return;
        g.pose().pushPose();
        g.pose().translate(right ? x - w * scale : x, y + (1 - scale) * 4, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    private static void brackets(GuiGraphics g, int width, int height) {
        int l = MARGIN, r = width - MARGIN, t = MARGIN, b = height - MARGIN;
        g.fill(l, t, l + BRACKET, t + 1, ACCENT);
        g.fill(l, t, l + 1, t + BRACKET, ACCENT);
        g.fill(r - BRACKET, t, r, t + 1, ACCENT);
        g.fill(r - 1, t, r, t + BRACKET, ACCENT);
        g.fill(l, b - 1, l + BRACKET, b, ACCENT);
        g.fill(l, b - BRACKET, l + 1, b, ACCENT);
        g.fill(r - BRACKET, b - 1, r, b, ACCENT);
        g.fill(r - 1, b - BRACKET, r, b, ACCENT);
    }

    /**
     * Four ticks round a gap and a centre dot, drawn in screen pixels so they stay thin at any GUI scale (user 2026-10-21:
     * too thick); watch-only weapons get a label instead.
     */
    private static void crosshair(GuiGraphics g, Font font, int x, int y) {
        if (OverwatchClient.watchOnly()) {
            Component label = Component.translatable("hud.bastion.overwatch.view_only");
            g.fill(x, y, x + 1, y + 1, ACCENT);
            g.drawString(font, label, x - font.width(label) / 2, y + 8, ACCENT, false);
            return;
        }
        int c = OverwatchClient.canFire() ? TEXT : RED;
        double s = Minecraft.getInstance().getWindow().getGuiScale();
        int cx = (int) Math.round(x * s), cy = (int) Math.round(y * s), t = Math.max(1, (int) Math.round(s / 3));
        int gap = (int) Math.round(2.5 * s), len = (int) Math.round(3.5 * s);
        inScreenPixels(g, s, () -> {
            g.fill(cx - gap - len, cy, cx - gap, cy + t, c);
            g.fill(cx + gap + t, cy, cx + gap + t + len, cy + t, c);
            g.fill(cx, cy - gap - len, cx + t, cy - gap, c);
            g.fill(cx, cy + gap + t, cx + t, cy + gap + t + len, c);
            g.fill(cx, cy, cx + t, cy + t, c);
        });
    }

    /**
     * Where the shot lands, along the barrel, as a small amber square on screen: on the crosshair when nothing is in the
     * way, off it when the barrel cannot reach what the raised view can see (user 2026-10-21).
     */
    private static void marker(GuiGraphics g, int width, int height) {
        Vec3 at = OverwatchClient.barrelImpact();
        if (at == null) return;
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 rel = at.subtract(camera.getPosition());
        Vector3f v = new Vector3f((float) rel.x, (float) rel.y, (float) rel.z).rotate(new Quaternionf(camera.rotation()).conjugate());
        if (v.z < 0.05f) return; // behind the camera
        double tan = Math.tan(Math.toRadians(OverwatchClient.fov()) / 2);
        double sx = width / 2.0 * (1 - v.x / v.z / (tan * width / height)), sy = height / 2.0 * (1 - v.y / v.z / tan);
        double s = Minecraft.getInstance().getWindow().getGuiScale();
        int cx = (int) Math.round(sx * s), cy = (int) Math.round(sy * s), r = (int) Math.round(2.5 * s), t = Math.max(1, (int) Math.round(s / 3));
        inScreenPixels(g, s, () -> {
            g.fill(cx - r, cy - r, cx + r, cy - r + t, AMBER);
            g.fill(cx - r, cy + r - t, cx + r, cy + r, AMBER);
            g.fill(cx - r, cy - r, cx - r + t, cy + r, AMBER);
            g.fill(cx + r - t, cy - r, cx + r, cy + r, AMBER);
        });
    }

    /** Draws with one unit = one screen pixel. */
    private static void inScreenPixels(GuiGraphics g, double guiScale, Runnable draw) {
        g.pose().pushPose();
        g.pose().scale((float) (1 / guiScale), (float) (1 / guiScale), 1);
        draw.run();
        g.pose().popPose();
    }

    /** The bars, stacked up from {@code bottom} (just above the hints). */
    private static void bars(GuiGraphics g, Font font, TurretBaseBlockEntity base, WeaponData data, float partialTick, long time, int bottom) {
        StatSheet stats = data == null ? null : StatSheet.of(data, base.tier(), base.inventory().modifierEffect());
        WeaponType type = data == null ? null : BastionWeaponTypes.REGISTRY.get().getValue(data.type());
        int charge = type == null ? 0 : type.chargeTicks(stats);
        float reload = data == null ? -1 : reload(base, data, stats, time, partialTick);
        int count = 1 + (OverwatchClient.status() != null ? 1 : 0) + (stats != null && stats.heatPerShot() > 0 ? 1 : 0) + (charge > 0 ? 1 : 0)
                + (reload >= 0 ? 1 : 0);
        int x = MARGIN + 6, y = bottom - count * 11;
        float hp = Mth.clamp(base.clientHealth(), 0, 1);
        y = bar(g, font, x, y, "hud.bastion.overwatch.hp", hp, hp < TurretBaseBlockEntity.CRITICAL_FRACTION ? RED : hp < TurretBaseBlockEntity.DAMAGED_FRACTION ? AMBER : GREEN);
        ControlHubMenu.Entry status = OverwatchClient.status();
        if (status != null) {
            y = bar(g, font, x, y, status.energyWeapon() ? "hud.bastion.overwatch.energy" : "hud.bastion.overwatch.ammo", status.fill(),
                    base.clientState() == TurretState.NO_AMMO ? RED : ACCENT);
        }
        if (stats == null) return;
        if (stats.heatPerShot() > 0) y = bar(g, font, x, y, "hud.bastion.overwatch.heat", Mth.clamp(base.clientHeat() / stats.maxHeat(), 0, 1), AMBER);
        if (charge > 0) {
            float progress = base.clientState() == TurretState.CHARGING ? ClientTurret.of(base.getBlockPos()).chargeProgress(time, partialTick, charge) : 0;
            y = bar(g, font, x, y, "hud.bastion.overwatch.charge", progress, 0xFF000000 | data.energyColor());
        }
        if (reload >= 0) bar(g, font, x, y, "hud.bastion.overwatch.reload", reload, reload >= 1 ? GREEN : AMBER);
    }

    /**
     * How ready the next shot is, 0-1 (user 2026-10-21), or -1 for weapons that fire too fast for it to matter: the
     * launchers' loaded tubes (synced RackSync mask: the Missile Launcher's loaded tubes, the Rocket Launcher's spent ones),
     * else the time since the last shot against the fire interval for weapons that wait a second or more between shots.
     */
    private static float reload(TurretBaseBlockEntity base, WeaponData data, StatSheet stats, long time, float partialTick) {
        if (data.type().equals(BastionWeaponTypes.MISSILE_LAUNCHER.getId())) {
            return Integer.bitCount(base.clientTubes() & MissileLauncherWeapon.ALL_TUBES) / (float) MissileLauncherWeapon.TUBES;
        }
        if (data.type().equals(BastionWeaponTypes.ROCKET_LAUNCHER.getId())) {
            int all = (1 << RocketLauncherWeapon.TUBES) - 1;
            return (RocketLauncherWeapon.TUBES - Integer.bitCount(base.clientTubes() & all)) / (float) RocketLauncherWeapon.TUBES;
        }
        if (stats.fireInterval() < 20) return -1;
        return Mth.clamp((time - ClientTurret.of(base.getBlockPos()).lastShotTick + partialTick) / stats.fireInterval(), 0, 1);
    }

    /** One labelled bar; returns the y of the next. */
    private static int bar(GuiGraphics g, Font font, int x, int y, String key, float fill, int colour) {
        fit(g, font, Component.translatable(key), x, y, 40, MUTED, false);
        int bx = x + 44;
        g.fill(bx, y + 1, bx + BAR_W, y + 7, BAR_BG);
        g.fill(bx + 1, y + 2, bx + 1 + Math.round((BAR_W - 2) * fill), y + 6, colour);
        return y + 11;
    }
}
