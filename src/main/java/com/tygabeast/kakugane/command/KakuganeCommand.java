package com.tygabeast.kakugane.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.tygabeast.kakugane.KakuganeConfig;
import com.tygabeast.kakugane.core.Affinity;
import com.tygabeast.kakugane.core.AffinityData;
import com.tygabeast.kakugane.core.Hearts;
import com.tygabeast.kakugane.core.Milestones;
import com.tygabeast.kakugane.core.SerialRegistry;
import com.tygabeast.kakugane.data.Archetype;
import com.tygabeast.kakugane.data.KakuganeData;
import com.tygabeast.kakugane.data.Trait;
import com.tygabeast.kakugane.registry.ModRegistry;
import com.tygabeast.kakugane.world.LabTracker;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;

/**
 * /kakugane choose deepen|retemper      - anyone: answer a pending milestone (the chat buttons run this)
 * /kakugane affinity                    - anyone: see how your fighting style reads
 * /kakugane give <players> [form] [tier] - ops: a new Kakugane, optionally pre-formed
 * /kakugane affinity add <players> <form> <amount> - ops: add affinity (testing)
 * /kakugane deed <players> human|embryo|elite      - ops: credit a deed to the active Kakugane (testing)
 * /kakugane heart reset <players>       - ops: return the heart as an item and allow a new one
 * /kakugane lab                         - anyone: the state of the lab you are standing in
 * /kakugane lab stage <0-3> | advance   - ops: set a lab's stage, or run one day of growth now (testing)
 */
public final class KakuganeCommand {
    private static final SuggestionProvider<CommandSourceStack> FORMS = (ctx, b) ->
            SharedSuggestionProvider.suggest(Arrays.stream(Archetype.values()).map(Archetype::id), b);

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("kakugane")
                .then(Commands.literal("choose")
                        .then(Commands.literal("deepen").executes(c -> Milestones.choose(c.getSource().getPlayerOrException(), true)))
                        .then(Commands.literal("retemper").executes(c -> Milestones.choose(c.getSource().getPlayerOrException(), false))))
                .then(Commands.literal("affinity")
                        .executes(c -> showAffinity(c.getSource(), c.getSource().getPlayerOrException()))
                        .then(Commands.literal("add").requires(s -> s.hasPermission(2))
                                .then(Commands.argument("players", EntityArgument.players())
                                        .then(Commands.argument("form", StringArgumentType.word()).suggests(FORMS)
                                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0, 10000))
                                                        .executes(KakuganeCommand::addAffinity))))))
                .then(Commands.literal("give").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(c -> give(c, null, 0))
                                .then(Commands.argument("form", StringArgumentType.word()).suggests(FORMS)
                                        .executes(c -> give(c, StringArgumentType.getString(c, "form"), 0))
                                        .then(Commands.argument("tier", IntegerArgumentType.integer(0, 3))
                                                .executes(c -> give(c, StringArgumentType.getString(c, "form"), IntegerArgumentType.getInteger(c, "tier")))))))
                .then(Commands.literal("deed").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.literal("human").executes(c -> deed(c, KakuganeData.Deeds::withHuman)))
                                .then(Commands.literal("embryo").executes(c -> deed(c, KakuganeData.Deeds::addEmbryo)))
                                .then(Commands.literal("elite").executes(c -> deed(c, KakuganeData.Deeds::withElite)))))
                .then(Commands.literal("lab")
                        .executes(c -> labInfo(c.getSource()))
                        .then(Commands.literal("stage").requires(s -> s.hasPermission(2))
                                .then(Commands.argument("stage", IntegerArgumentType.integer(0, 3)).executes(c -> {
                                    var st = labHere(c.getSource());
                                    if (st == null) return 0;
                                    var level = c.getSource().getLevel();
                                    // Also moves the day count (and clock) to the start of that stage, so growth carries on from there.
                                    LabTracker.forceStage(level, st, IntegerArgumentType.getInteger(c, "stage"));
                                    return labInfo(c.getSource());
                                })))
                        .then(Commands.literal("advance").requires(s -> s.hasPermission(2)).executes(c -> {
                            var st = labHere(c.getSource());
                            if (st == null) return 0;
                            LabTracker.advance(c.getSource().getLevel(), st);
                            return labInfo(c.getSource());
                        })))
                .then(Commands.literal("heart").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("reset")
                                .then(Commands.argument("players", EntityArgument.players()).executes(c -> {
                                    for (ServerPlayer p : EntityArgument.getPlayers(c, "players")) Hearts.reset(p);
                                    return 1;
                                })))));
    }

    private static net.minecraft.world.level.levelgen.structure.StructureStart labHere(CommandSourceStack src) {
        var st = LabTracker.labAt(src.getLevel(), net.minecraft.core.BlockPos.containing(src.getPosition()));
        if (st == null) src.sendFailure(Component.translatable("command.kakugane.lab.none"));
        return st;
    }

    private static int labInfo(CommandSourceStack src) {
        var st = labHere(src);
        if (st == null) return 0;
        src.sendSuccess(() -> LabTracker.describe(src.getLevel(), st), false);
        return 1;
    }

    private static int showAffinity(CommandSourceStack src, ServerPlayer p) {
        AffinityData a = p.getData(ModRegistry.AFFINITY.get());
        a.decayTo(Affinity.now(p), KakuganeConfig.AFFINITY_HALF_LIFE_DAYS.get() * 24000L);
        double need = KakuganeConfig.IMPRINT_THRESHOLD.get();
        src.sendSuccess(() -> Component.translatable("command.kakugane.affinity.header",
                String.format(Locale.ROOT, "%.1f", a.recorded()), String.format(Locale.ROOT, "%.0f", need)), false);
        for (Archetype f : Archetype.values()) {
            src.sendSuccess(() -> Component.literal("  ").append(f.displayName())
                    .append(String.format(Locale.ROOT, ": %.2f", a.score(f.id()))), false);
        }
        return 1;
    }

    private static int addAffinity(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Optional<Archetype> f = Archetype.byId(StringArgumentType.getString(c, "form"));
        if (f.isEmpty()) {
            c.getSource().sendFailure(Component.translatable("command.kakugane.unknown_form"));
            return 0;
        }
        double amount = DoubleArgumentType.getDouble(c, "amount");
        for (ServerPlayer p : EntityArgument.getPlayers(c, "players")) Affinity.record(p, f.get(), amount);
        c.getSource().sendSuccess(() -> Component.translatable("command.kakugane.done"), true);
        return 1;
    }

    private static int give(CommandContext<CommandSourceStack> c, String formId, int tier) throws CommandSyntaxException {
        Optional<Archetype> form = formId == null ? Optional.empty() : Archetype.byId(formId);
        if (formId != null && form.isEmpty()) {
            c.getSource().sendFailure(Component.translatable("command.kakugane.unknown_form"));
            return 0;
        }
        Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "players");
        for (ServerPlayer p : players) {
            int serial = SerialRegistry.get(p.server).claim(p.getRandom());
            KakuganeData d = KakuganeData.EMPTY.withSerial(serial);
            if (form.isPresent()) {
                d = d.withForm(form.get(), Trait.pick(serial, form.get().worn()), "").withTier(tier);
            }
            ItemStack s = new ItemStack(ModRegistry.KAKUGANE.get());
            s.set(ModRegistry.KAKUGANE_DATA.get(), d);
            p.getInventory().placeItemBackInInventory(s);
        }
        c.getSource().sendSuccess(() -> Component.translatable("command.kakugane.done"), true);
        return players.size();
    }

    private static int deed(CommandContext<CommandSourceStack> c, java.util.function.UnaryOperator<KakuganeData.Deeds> deed) throws CommandSyntaxException {
        int n = 0;
        for (ServerPlayer p : EntityArgument.getPlayers(c, "players")) {
            if (!com.tygabeast.kakugane.core.KakuganeAccess.state(p).hasActive()) {
                c.getSource().sendFailure(Component.translatable("command.kakugane.no_active", p.getDisplayName()));
                continue;
            }
            Milestones.credit(p, deed);
            n++;
        }
        if (n > 0) c.getSource().sendSuccess(() -> Component.translatable("command.kakugane.done"), true);
        return n;
    }

    private KakuganeCommand() {}
}
