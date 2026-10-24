package dev.bastion.research;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** {@code /bastion research grant|revoke <node|all>} and {@code list} for admins (PLAN 8 "Riset & Turret Codex", op level 2). */
final class ResearchCommand {
    private static final SuggestionProvider<CommandSourceStack> NODES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(Stream.concat(Stream.of("all"), ResearchTree.ALL.stream().filter(n -> !n.free()).map(ResearchNode::id)), builder);

    private ResearchCommand() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bastion").requires(source -> source.hasPermission(2)).then(Commands.literal("research")
                .then(Commands.literal("grant").then(Commands.argument("node", StringArgumentType.word()).suggests(NODES).executes(ctx -> change(ctx, true))))
                .then(Commands.literal("revoke").then(Commands.argument("node", StringArgumentType.word()).suggests(NODES).executes(ctx -> change(ctx, false))))
                .then(Commands.literal("list").executes(ResearchCommand::list))));
    }

    private static int change(CommandContext<CommandSourceStack> ctx, boolean grant) {
        String id = StringArgumentType.getString(ctx, "node");
        MinecraftServer server = ctx.getSource().getServer();
        ResearchData data = ResearchData.get(server);
        if (id.equals("all")) {
            data.set(grant ? ResearchTree.ALL.stream().map(ResearchNode::id).collect(Collectors.toSet()) : Set.of());
        } else if (ResearchTree.get(id) == null) {
            ctx.getSource().sendFailure(Component.translatable("commands.bastion.research.unknown", id));
            return 0;
        } else if (ResearchTree.get(id).free()) {
            ctx.getSource().sendFailure(Component.translatable("commands.bastion.research.free", id));
            return 0;
        } else if (grant) {
            data.add(id);
        } else {
            data.remove(id);
        }
        Research.syncAll(server);
        ctx.getSource().sendSuccess(() -> Component.translatable(grant ? "commands.bastion.research.granted" : "commands.bastion.research.revoked", id), true);
        return 1;
    }

    /** The researched nodes, free ones left out (they always are); returns how many. */
    private static int list(CommandContext<CommandSourceStack> ctx) {
        Set<String> done = new HashSet<>(ResearchData.get(ctx.getSource().getServer()).all());
        List<String> ids = ResearchTree.ALL.stream().filter(n -> !n.free() && done.contains(n.id())).map(ResearchNode::id).toList();
        long total = ResearchTree.ALL.stream().filter(n -> !n.free()).count();
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.bastion.research.list", ids.size(), total, String.join(", ", ids)), false);
        return ids.size();
    }
}
