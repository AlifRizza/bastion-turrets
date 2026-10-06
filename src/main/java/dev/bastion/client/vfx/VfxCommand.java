package dev.bastion.client.vfx;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.bastion.Bastion;
import dev.bastion.client.particle.VfxParticle;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client debug command (PLAN 8, Fase 3): {@code /bastion vfx <preset> [cyan|amber|magenta]} plays a preset
 * 3 blocks in front of the player (or on the block looked at); {@code /bastion vfx stress} spawns ~200
 * particles for the FPS check.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class VfxCommand {
    private VfxCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("bastion").then(Commands.literal("vfx")
                .then(Commands.literal("stress").executes(ctx -> stress()))
                .then(Commands.argument("preset", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(VfxPresets.ALL.keySet(), builder))
                        .executes(ctx -> play(StringArgumentType.getString(ctx, "preset"), "cyan"))
                        .then(Commands.argument("color", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(new String[]{"cyan", "amber", "magenta"}, builder))
                                .executes(ctx -> play(StringArgumentType.getString(ctx, "preset"), StringArgumentType.getString(ctx, "color")))))));
    }

    private static int play(String name, String color) {
        VfxPreset preset = VfxPresets.ALL.get(name);
        Player player = Minecraft.getInstance().player;
        if (preset == null || player == null) {
            if (player != null) player.displayClientMessage(Component.literal("Unknown preset: " + name), false);
            return 0;
        }
        int argb = switch (color) {
            case "amber" -> VfxPresets.AMBER;
            case "magenta" -> VfxPresets.MAGENTA;
            default -> VfxPresets.CYAN;
        };
        Vec3 look = player.getLookAngle();
        HitResult hit = Minecraft.getInstance().hitResult;
        Vec3 at = player.getEyePosition().add(look.scale(3));
        Vec3 normal = look.reverse();
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK && hit.getLocation().distanceTo(player.getEyePosition()) < 12) {
            at = hit.getLocation();
            normal = Vec3.atLowerCornerOf(block.getDirection().getNormal());
        }
        VfxManager.play(preset, at, look, VfxParams.of().color(argb).normal(normal).seed(player.level().getGameTime()).blockColor(0xFF8A7A66));
        return 1;
    }

    private static int stress() {
        Player player = Minecraft.getInstance().player;
        if (player == null) return 0;
        Vec3 center = player.getEyePosition().add(player.getLookAngle().scale(6));
        for (int i = 0; i < 20; i++) {
            VfxManager.play(VfxPresets.SPARK, center, player.getLookAngle(), VfxParams.of().color(VfxPresets.CYAN).seed(i));
        }
        player.displayClientMessage(Component.literal("Bastion particles active: " + VfxParticle.activeCount()), false);
        return 1;
    }
}
