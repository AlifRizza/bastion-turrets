package dev.bastion.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Code-drawn button in the turret GUI palette (PLAN 1: no vanilla widget textures). A non-null {@code lit}
 * makes it a toggle with an indicator light.
 */
public class FlatButton extends AbstractButton {
    static final int BG = 0xFF1E232A, EDGE = 0xFF3C4550, LIGHT = 0xFF5F6B79, DARK = 0xFF111418, ACCENT = 0xFF9FC4E8;
    static final int TEXT = 0xFFD8E2EC, MUTED = 0xFF7F8C9A, GREEN = 0xFF7CFF8A;

    private final Supplier<Component> label;
    private final Runnable onPress;
    private final BooleanSupplier lit;

    public FlatButton(int x, int y, int width, int height, Supplier<Component> label, Runnable onPress, BooleanSupplier lit) {
        super(x, y, width, height, label.get());
        this.label = label;
        this.onPress = onPress;
        this.lit = lit;
    }

    public FlatButton(int x, int y, int width, int height, Supplier<Component> label, Runnable onPress) {
        this(x, y, width, height, label, onPress, null);
    }

    @Override
    public void onPress() {
        onPress.run();
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean on = lit != null && lit.getAsBoolean();
        int x = getX(), y = getY();
        graphics.fill(x, y, x + width, y + height, isHoveredOrFocused() ? LIGHT : EDGE);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, isHoveredOrFocused() ? 0xFF2A313A : BG);
        // Label area: inside the frame, right of the indicator light when there is one.
        int left = x + (lit != null ? 9 : 3), right = x + width - 3;
        if (lit != null) graphics.fill(x + 3, y + height / 2 - 2, x + 7, y + height / 2 + 2, on ? GREEN : DARK);
        Font font = Minecraft.getInstance().font;
        Component text = label.get();
        int colour = lit == null || on ? TEXT : MUTED;
        float scale = Math.min(1, (right - left) / (float) font.width(text));
        graphics.pose().pushPose();
        graphics.pose().translate((left + right) / 2f, y + height / 2f, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, text, -font.width(text) / 2, -4, colour, true);
        graphics.pose().popPose();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    @Override
    public Component getMessage() {
        return label.get();
    }
}
