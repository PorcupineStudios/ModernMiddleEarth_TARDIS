package com.porcupinestudios.lotrport.progress;

import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.porcupinestudios.lotrport.content.ContentData;
import com.porcupinestudios.lotrport.faction.Alignment;
import com.porcupinestudios.lotrport.registry.LotrRegistries;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.AttachmentType;

/**
 * LOTR's shields (LOTRShields) and capes (LOTRCapes) as a player's chosen cosmetic ({@code /lotr_shield},
 * {@code /lotr_cape}), kept in attachments. Alignment shields unlock at their {@code alignment} with the faction;
 * achievable and exclusive shields, and capes, only for operators (LOTR handed those out by player). Drawing them on
 * the player is not done yet (see lotr/docs/import-checklist.md, section 8).
 */
public final class Cosmetics {
    static final Map<String, JsonObject> SHIELDS = load("shields.json");
    static final Map<String, JsonObject> CAPES = load("capes.json");

    private Cosmetics() {}

    private static Map<String, JsonObject> load(String file) {
        Map<String, JsonObject> m = new LinkedHashMap<>();
        for (JsonObject o : ContentData.read(file)) {
            m.put(o.get("id").getAsString(), o);
        }
        return m;
    }

    public static boolean shieldUnlocked(ServerPlayer p, JsonObject s) {
        if (s.has("faction")) {
            return Alignment.get(p, ResourceLocation.parse(s.get("faction").getAsString()))
                    >= ContentData.num(s, "alignment", 1000);
        }
        return p.hasPermissions(2);
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> d) {
        d.register(command("lotr_shield", SHIELDS, LotrRegistries.SHIELD, true));
        d.register(command("lotr_cape", CAPES, LotrRegistries.CAPE, false));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> command(String name,
            Map<String, JsonObject> all, Supplier<AttachmentType<String>> slot, boolean shield) {
        return Commands.literal(name)
                .then(Commands.literal("clear").executes(ctx -> {
                    ctx.getSource().getPlayerOrException().setData(slot, "");
                    return 1;
                }))
                .then(Commands.argument("id", StringArgumentType.word())
                        .suggests((ctx, b) -> {
                            all.keySet().forEach(b::suggest);
                            return b.buildFuture();
                        })
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            JsonObject o = all.get(StringArgumentType.getString(ctx, "id"));
                            boolean ok = o != null && (shield ? shieldUnlocked(p, o) : p.hasPermissions(2));
                            if (!ok) {
                                ctx.getSource().sendFailure(Component.literal("Not unlocked"));
                                return 0;
                            }
                            p.setData(slot, o.get("id").getAsString());
                            ctx.getSource().sendSuccess(() -> Component.literal("Now wearing " + o.get("id")
                                    .getAsString()), false);
                            return 1;
                        }));
    }
}
