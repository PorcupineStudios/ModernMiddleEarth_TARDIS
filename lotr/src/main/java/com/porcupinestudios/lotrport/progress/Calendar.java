package com.porcupinestudios.lotrport.progress;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.porcupinestudios.lotrport.content.ContentData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;

/**
 * LOTR's calendar (LOTRDate, {@code calendar.json}): Shire Reckoning months (Yule, the Lithe days...), weekdays, and
 * the Third Age year, counted from the world's day. {@code /lotr_date} says today's date.
 */
public final class Calendar {
    static final JsonObject DATA = ContentData.readObject("calendar.json");

    private Calendar() {}

    /** Today: [year (Third Age), month index, day of month (1-based), weekday index or -1]. */
    public static int[] date(Level level) {
        long ticks = Math.max(1, ContentData.integer(DATA, "ticks_per_day", 24000));
        long day = level.getDayTime() / ticks + ContentData.integer(DATA, "start_day", 0);
        JsonArray months = DATA.has("months") ? DATA.getAsJsonArray("months") : new JsonArray();
        int yearLength = 0;
        for (JsonElement m : months) {
            if (!ContentData.bool(m.getAsJsonObject(), "leap_only", false)) {
                yearLength += ContentData.integer(m.getAsJsonObject(), "days", 0);
            }
        }
        if (yearLength <= 0) {
            return new int[] { ContentData.integer(DATA, "start_year", 3001), 0, (int) day + 1, -1 };
        }
        int year = ContentData.integer(DATA, "start_year", 3001) + (int) (day / yearLength);
        int inYear = (int) (day % yearLength);
        int weekdays = DATA.has("weekdays") ? DATA.getAsJsonArray("weekdays").size() : 7;
        int weekday = (int) (day % Math.max(1, weekdays));
        for (int i = 0; i < months.size(); i++) {
            JsonObject m = months.get(i).getAsJsonObject();
            if (ContentData.bool(m, "leap_only", false)) {
                continue;
            }
            int len = ContentData.integer(m, "days", 0);
            if (inYear < len) {
                return new int[] { year, i, inYear + 1, ContentData.bool(m, "weekdays", true) ? weekday : -1 };
            }
            inYear -= len;
        }
        return new int[] { year, 0, 1, -1 };
    }

    public static Component describe(Level level) {
        int[] d = date(level);
        JsonArray months = DATA.getAsJsonArray("months");
        MutableComponent c = Component.empty();
        if (d[3] >= 0 && DATA.has("weekdays")) {
            c.append(Component.translatable(DATA.getAsJsonArray("weekdays").get(d[3]).getAsJsonObject().get("lang")
                    .getAsString())).append(", ");
        }
        if (months != null && !months.isEmpty()) {
            JsonObject m = months.get(d[1]).getAsJsonObject();
            if (ContentData.integer(m, "days", 1) > 1) {
                c.append(d[2] + " ");
            }
            c.append(Component.translatable(m.get("lang").getAsString()));
        }
        int shire = d[0] - ContentData.integer(DATA, "shire_offset", 1600);
        return c.append(" " + shire + " ").append(Component.translatable("lotr.date.shire"))
                .append(" (T.A. " + d[0] + ")");
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("lotr_date").executes(ctx -> {
            ctx.getSource().sendSuccess(() -> describe(ctx.getSource().getLevel()), false);
            return 1;
        }));
    }
}
