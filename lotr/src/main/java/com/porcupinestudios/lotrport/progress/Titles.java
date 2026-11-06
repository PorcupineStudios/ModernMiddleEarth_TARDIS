package com.porcupinestudios.lotrport.progress;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.porcupinestudios.lotrport.content.ContentData;
import com.porcupinestudios.lotrport.faction.Alignment;
import com.porcupinestudios.lotrport.registry.LotrRegistries;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * LOTR's titles (LOTRTitle, {@code titles.json}): a player picks one unlocked title with {@code /lotr_title} and it
 * shows before their name. Unlocks: {@code starter} always; {@code alignment} at {@code alignment} with the listed
 * factions (any one if {@code any}, else all); {@code achievement} with that achievement; {@code player_exclusive}
 * (LOTR gave these to named players) only for operators.
 */
public final class Titles {
    static final Map<String, JsonObject> ALL = new LinkedHashMap<>();

    static {
        for (JsonObject t : ContentData.read("titles.json")) {
            ALL.put(t.get("id").getAsString(), t);
        }
    }

    private Titles() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.NameFormat e) -> {
            String id = e.getEntity().getData(LotrRegistries.TITLE);
            JsonObject t = ALL.get(id);
            if (t != null) {
                e.setDisplayname(Component.literal("[").append(Component.translatable(t.get("lang").getAsString()))
                        .append("] ").withStyle(ChatFormatting.GOLD).append(e.getDisplayname()));
            }
        });
    }

    public static boolean unlocked(ServerPlayer player, JsonObject t) {
        String type = t.get("type").getAsString();
        return switch (type) {
            case "starter" -> true;
            case "alignment" -> {
                float need = ContentData.num(t, "alignment", 0);
                boolean any = ContentData.bool(t, "any", false);
                List<ResourceLocation> facs = new ArrayList<>();
                for (JsonElement f : t.getAsJsonArray("factions")) {
                    facs.add(ResourceLocation.parse(f.getAsString()));
                }
                if (facs.isEmpty()) {
                    yield false;
                }
                boolean all = true;
                boolean one = false;
                for (ResourceLocation f : facs) {
                    boolean ok = Alignment.get(player, f) >= need;
                    all &= ok;
                    one |= ok;
                }
                yield any ? one : all;
            }
            case "achievement" -> Achievements.has(player, t.get("achievement").getAsString());
            default -> player.hasPermissions(2);
        };
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("lotr_title")
                .executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    List<String> ids = ALL.values().stream().filter(t -> unlocked(p, t)).map(t -> t.get("id")
                            .getAsString()).toList();
                    ctx.getSource().sendSuccess(() -> Component.literal("Titles: " + String.join(", ", ids)), false);
                    return ids.size();
                })
                .then(Commands.literal("clear").executes(ctx -> {
                    ctx.getSource().getPlayerOrException().setData(LotrRegistries.TITLE, "");
                    ctx.getSource().getPlayerOrException().refreshDisplayName();
                    return 1;
                }))
                .then(Commands.argument("title", StringArgumentType.word())
                        .suggests((ctx, b) -> {
                            ServerPlayer p = ctx.getSource().getPlayer();
                            ALL.values().stream().filter(t -> p != null && unlocked(p, t))
                                    .forEach(t -> b.suggest(t.get("id").getAsString()));
                            return b.buildFuture();
                        })
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            JsonObject t = ALL.get(StringArgumentType.getString(ctx, "title"));
                            if (t == null || !unlocked(p, t)) {
                                ctx.getSource().sendFailure(Component.literal("You haven't earned that title"));
                                return 0;
                            }
                            p.setData(LotrRegistries.TITLE, t.get("id").getAsString());
                            p.refreshDisplayName();
                            ctx.getSource().sendSuccess(() -> Component.translatable(t.get("lang").getAsString()),
                                    false);
                            return 1;
                        })));
    }
}
