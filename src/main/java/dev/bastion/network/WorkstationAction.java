package dev.bastion.network;

import dev.bastion.workstation.WorkstationBlock;
import dev.bastion.workstation.WorkstationBlockEntity;
import dev.bastion.workstation.WorkstationMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** C->S from the workstation GUI: select a recipe, or (instant stations) craft the selected one {@code count} times. */
public record WorkstationAction(BlockPos pos, boolean craft, ResourceLocation recipe, int count) {
    public static WorkstationAction select(BlockPos pos, ResourceLocation recipe) {
        return new WorkstationAction(pos, false, recipe, 0);
    }

    public static WorkstationAction craft(BlockPos pos, ResourceLocation recipe, int count) {
        return new WorkstationAction(pos, true, recipe, count);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeBoolean(craft);
        buf.writeResourceLocation(recipe);
        buf.writeVarInt(count);
    }

    public static WorkstationAction decode(FriendlyByteBuf buf) {
        return new WorkstationAction(buf.readBlockPos(), buf.readBoolean(), buf.readResourceLocation(), Math.min(64, buf.readVarInt()));
    }

    /** Only for the station whose GUI the sender has open and can still reach. */
    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null && player.containerMenu instanceof WorkstationMenu menu && menu.pos().equals(pos) && menu.stillValid(player)) {
            WorkstationBlockEntity station = WorkstationBlock.core(player.level(), pos);
            if (station != null) {
                station.selectRecipe(recipe);
                if (craft) station.instantCraft(player, count);
            }
        }
        context.get().setPacketHandled(true);
    }
}
