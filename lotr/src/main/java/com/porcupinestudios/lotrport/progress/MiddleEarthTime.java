package com.porcupinestudios.lotrport.progress;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.porcupinestudios.lotrport.LotrConfig;
import com.porcupinestudios.lotrport.world.LotrDimensions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * LOTR's day length (LOTRTime): Middle-earth's clock runs at {@code 24000 / time.dayLength} clock ticks a game tick, so
 * a day lasts 48000 ticks by default. The clock itself still counts 24000 a day, so the sky, beds and the
 * {@link Calendar} (which counts days of clock time) all keep working. Only when Middle-earth is the world type: as a
 * dimension its clock is the overworld's, which this leaves alone.
 */
public final class MiddleEarthTime {
    private MiddleEarthTime() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Pre e) -> {
            if (e.getLevel() instanceof ServerLevel level && level.getGameTime() % 20 == 0
                    && level == level.getServer().overworld() && LotrDimensions.isMiddleEarth(level)) {
                apply(level);
            }
        });
    }

    /** Clock ticks a game tick for the configured day length. */
    public static float perTick() {
        return (float) Level.TICKS_PER_DAY / LotrConfig.DAY_LENGTH.get();
    }

    /** Sets the level's clock speed to the configured day length (NeoForge syncs it to clients). */
    public static void apply(ServerLevel level) {
        level.setDayTimePerTick(perTick());
    }

    /** {@code /lotr time}: the date and the time of day in LOTR ticks; {@code /lotr time set <ticks>} sets it. */
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("time")
                .executes(ctx -> {
                    ServerLevel level = ctx.getSource().getLevel();
                    int now = timeOfDay(level);
                    ctx.getSource().sendSuccess(() -> Calendar.describe(level).copy().append(Component.literal(
                            ", " + now + " of " + LotrConfig.DAY_LENGTH.get() + " ticks into the day")), false);
                    return now;
                })
                .then(Commands.literal("set").then(Commands.argument("ticks", IntegerArgumentType.integer(0))
                        .executes(ctx -> {
                            ServerLevel level = ctx.getSource().getLevel();
                            int ticks = IntegerArgumentType.getInteger(ctx, "ticks") % LotrConfig.DAY_LENGTH.get();
                            long day = level.getDayTime() / Level.TICKS_PER_DAY;
                            for (ServerLevel l : ctx.getSource().getServer().getAllLevels()) {
                                l.setDayTime(day * Level.TICKS_PER_DAY
                                        + (long) ticks * Level.TICKS_PER_DAY / LotrConfig.DAY_LENGTH.get());
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal("Set the time of day to " + ticks
                                    + " of " + LotrConfig.DAY_LENGTH.get()), true);
                            return ticks;
                        })));
    }

    /** How far into today, in ticks of a Middle-earth day. */
    public static int timeOfDay(Level level) {
        long clock = level.getDayTime() % Level.TICKS_PER_DAY;
        return (int) (clock * LotrConfig.DAY_LENGTH.get() / Level.TICKS_PER_DAY);
    }
}
