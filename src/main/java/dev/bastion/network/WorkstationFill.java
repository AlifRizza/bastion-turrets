package dev.bastion.network;

import dev.bastion.workstation.WorkstationBlock;
import dev.bastion.workstation.WorkstationBlockEntity;
import dev.bastion.workstation.WorkstationMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C->S from JEI's + button on a workstation recipe (PLAN "Compat JEI & Jade"): pick the recipe and move its ingredients for
 * {@code crafts} crafts from the player's inventory into the inputs.
 */
public record WorkstationFill(BlockPos pos, ResourceLocation recipe, int crafts) {
    /** Shift + click: as many crafts as fit. */
    public static final int MAX_CRAFTS = 64;

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeResourceLocation(recipe);
        buf.writeVarInt(crafts);
    }

    public static WorkstationFill decode(FriendlyByteBuf buf) {
        return new WorkstationFill(buf.readBlockPos(), buf.readResourceLocation(), Mth.clamp(buf.readVarInt(), 1, MAX_CRAFTS));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) apply(player);
        context.get().setPacketHandled(true);
    }

    /** Only for the station whose GUI the sender has open and can still reach (as WorkstationAction); returns the crafts moved. */
    public int apply(ServerPlayer player) {
        if (!(player.containerMenu instanceof WorkstationMenu menu) || !menu.pos().equals(pos) || !menu.stillValid(player)) return 0;
        WorkstationBlockEntity station = WorkstationBlock.core(player.level(), pos);
        if (station == null) return 0;
        station.selectRecipe(recipe);
        return recipe.equals(station.recipeId()) ? station.fill(player, crafts) : 0;
    }
}
