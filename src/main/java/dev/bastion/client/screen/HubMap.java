package dev.bastion.client.screen;

import com.mojang.blaze3d.platform.NativeImage;
import dev.bastion.Bastion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;

/**
 * The hub screen's terrain: one pixel per block column of the hub's area, coloured like a vanilla map (the top block's
 * map colour, lighter or darker as the ground rises or falls northward) from the chunks this client has loaded; dark where
 * it has none. Redrawn every 5 s while the screen is open.
 */
final class HubMap {
    private static final ResourceLocation ID = Bastion.id("control_hub_map");
    private static final int UNKNOWN = 0xFF141618; // ABGR, as NativeImage takes it
    private static final int REDRAW_TICKS = 100;
    @Nullable
    private DynamicTexture texture;
    private int size, minX, minZ;
    private long drawnAt = Long.MIN_VALUE;

    ResourceLocation id() {
        return ID;
    }

    void update(ClientLevel level, int minX, int minZ, int size) {
        boolean moved = texture == null || size != this.size || minX != this.minX || minZ != this.minZ;
        if (!moved && level.getGameTime() - drawnAt < REDRAW_TICKS) return;
        if (texture == null || size != this.size) {
            close();
            texture = new DynamicTexture(size, size, false);
            Minecraft.getInstance().getTextureManager().register(ID, texture);
        }
        this.size = size;
        this.minX = minX;
        this.minZ = minZ;
        drawnAt = level.getGameTime();
        NativeImage image = texture.getPixels();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < size; x++) {
            int north = Integer.MIN_VALUE; // the column to the north, for the slope shading
            for (int z = 0; z < size; z++) {
                int wx = minX + x, wz = minZ + z;
                if (!level.hasChunk(wx >> 4, wz >> 4)) {
                    image.setPixelRGBA(x, z, UNKNOWN);
                    north = Integer.MIN_VALUE;
                    continue;
                }
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz) - 1;
                pos.set(wx, top, wz);
                MapColor colour = level.getBlockState(pos).getMapColor(level, pos);
                MapColor.Brightness light = north == Integer.MIN_VALUE || top == north ? MapColor.Brightness.NORMAL
                        : top > north ? MapColor.Brightness.HIGH : MapColor.Brightness.LOW;
                image.setPixelRGBA(x, z, colour == MapColor.NONE ? UNKNOWN : colour.calculateRGBColor(light));
                north = top;
            }
        }
        texture.upload();
    }

    void close() {
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(ID);
            texture = null;
        }
    }
}
