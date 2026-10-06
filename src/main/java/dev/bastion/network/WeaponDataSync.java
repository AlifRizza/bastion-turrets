package dev.bastion.network;

import dev.bastion.modifier.ModifierDataLoader;
import dev.bastion.modifier.ModifierEffect;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponDataLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.function.Supplier;

/** S->C copy of every turret weapon and modifier JSON, sent on login and after /reload. */
public record WeaponDataSync(Map<ResourceLocation, WeaponData> weapons, Map<ResourceLocation, ModifierEffect> modifiers) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeMap(weapons, FriendlyByteBuf::writeResourceLocation, (b, data) -> b.writeJsonWithCodec(WeaponData.CODEC, data));
        buf.writeMap(modifiers, FriendlyByteBuf::writeResourceLocation, (b, effect) -> b.writeJsonWithCodec(ModifierEffect.CODEC, effect));
    }

    public static WeaponDataSync decode(FriendlyByteBuf buf) {
        return new WeaponDataSync(buf.readMap(FriendlyByteBuf::readResourceLocation, b -> b.readJsonWithCodec(WeaponData.CODEC)),
                buf.readMap(FriendlyByteBuf::readResourceLocation, b -> b.readJsonWithCodec(ModifierEffect.CODEC)));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        // On an integrated server both sides share this map already; replacing it with an equal copy is harmless.
        WeaponDataLoader.accept(weapons);
        ModifierDataLoader.accept(modifiers);
        context.get().setPacketHandled(true);
    }
}
