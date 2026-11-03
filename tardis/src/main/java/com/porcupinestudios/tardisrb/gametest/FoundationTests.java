package com.porcupinestudios.tardisrb.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.core.TardisManager;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.progression.Cost;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import com.porcupinestudios.tardisrb.progression.Unlocks;
import com.porcupinestudios.tardisrb.repair.Repairs;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * TARDIS phase 1 done-when: a TARDIS's subsystems load from JSON, a repair level goes up and down, and /reload picks
 * up a changed cost. Plus the validator, stages and milestone caps.
 */
@GameTestHolder(TardisRB.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FoundationTests {
    private static final String EMPTY = "empty";
    private static final ResourceLocation NAVIGATION = TardisRB.id("navigation");
    private static final ResourceLocation POWER = TardisRB.id("power");
    private static final ResourceLocation SHORT_HOP = TardisRB.id("short_hop");

    private FoundationTests() {}

    private static TardisRecord fresh(GameTestHelper helper, String name) {
        MinecraftServer server = helper.getLevel().getServer();
        return TardisManager.get(server).create(server, name, Optional.empty());
    }

    @GameTest(template = EMPTY)
    public static void subsystemsLoadFromJson(GameTestHelper helper) {
        TardisContent.Snapshot data = TardisContent.get();
        helper.assertTrue(data.problems().isEmpty(), "data problems: " + data.problems());
        for (String s : List.of("navigation", "power", "console", "hull", "rooms")) {
            helper.assertTrue(data.subsystems().containsKey(TardisRB.id(s)), "missing subsystem " + s);
        }
        helper.assertTrue(data.subsystems().get(NAVIGATION).value(1, "range") > 0, "navigation level 1 has no range");
        TardisRecord t = fresh(helper, "Loads");
        helper.assertTrue(t.stage().equals(Optional.of(TardisRB.id("wreck"))), "starts in " + t.stage());
        helper.assertTrue(Unlocks.level(t, NAVIGATION) == 0, "wreck navigation isn't level 0");
        helper.assertTrue(Unlocks.stat(t, "range") == 0, "wreck can already jump");
        helper.assertFalse(Unlocks.check(helper.getLevel().getServer(), t, SHORT_HOP, Optional.empty()).allowed(),
                "wreck can hop");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void validatorRejectsMissingIdsAndLoops(GameTestHelper helper) {
        Map<String, Map<ResourceLocation, JsonElement>> files = new HashMap<>();
        put(files, "subsystem", "a", "{\"levels\": [{}, {\"range\": 1}]}");
        put(files, "repair", "ok", "{\"subsystem\": \"test:a\", \"level\": 1}");
        put(files, "repair", "unknown_subsystem", "{\"subsystem\": \"test:nope\", \"level\": 1}");
        put(files, "repair", "too_high", "{\"subsystem\": \"test:a\", \"level\": 5}");
        put(files, "repair", "unknown_cost", "{\"subsystem\": \"test:a\", \"level\": 1, \"cost\": \"test:nope\"}");
        put(files, "stage", "bad", "{\"order\": 0, \"repairs\": [\"test:unknown_subsystem\"]}");
        put(files, "research", "x", "{\"parents\": [\"test:y\"]}");
        put(files, "research", "y", "{\"parents\": [\"test:x\"]}");
        put(files, "research", "after_loop", "{\"parents\": [\"test:x\"]}");
        put(files, "research", "fine", "{}");
        put(files, "research", "child", "{\"parents\": [\"test:fine\"]}");
        put(files, "research", "orphan", "{\"parents\": [\"test:missing\"]}");
        TardisContent.Snapshot s = TardisContent.build(files, -1);
        helper.assertTrue(s.repairs().keySet().equals(java.util.Set.of(id("ok"))), "repairs kept: " + s.repairs());
        helper.assertTrue(s.stages().isEmpty(), "bad stage kept");
        helper.assertTrue(s.research().keySet().equals(java.util.Set.of(id("fine"), id("child"))),
                "research kept: " + s.research().keySet());
        String all = String.join("\n", s.problems());
        for (String expect : List.of("unknown subsystem test:nope", "above test:a", "unknown cost test:nope",
                "unknown repair", "loop", "test:missing")) {
            helper.assertTrue(all.contains(expect), "no problem mentions '" + expect + "': " + all);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void repairRaisesAndDamageLowersALevel(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        // A fake player, not a mock one: mock players join the player list, and Create's sync packets to them throw.
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.fromString("7a7d15c0-0000-4000-8000-000000000001"), "tardis_test"));
        player.getInventory().clearContent();
        player.getAbilities().instabuild = false;
        TardisRecord t = fresh(helper, "Repairs");
        ResourceLocation repair = TardisRB.id("navigation_1");

        Repairs.Outcome broke = Repairs.attempt(server, t, repair, Optional.of(player));
        helper.assertFalse(broke.done(), "repaired without the parts");
        Cost cost = TardisContent.get().cost(TardisContent.repair(repair).orElseThrow().cost());
        give(player, cost);
        Repairs.Outcome fixed = Repairs.attempt(server, t, repair, Optional.of(player));
        if (!fixed.done()) {
            helper.fail("repair failed: " + fixed.problem().get().getString());
        }
        helper.assertTrue(Unlocks.level(t, NAVIGATION) == 1, "navigation not level 1");
        helper.assertTrue(Unlocks.stat(t, "range") > 0, "no range after repair");
        helper.assertTrue(Unlocks.check(server, t, SHORT_HOP, Optional.empty()).allowed(), "can't hop after repair");
        helper.assertTrue(Repairs.missing(player.getInventory(), cost).isPresent() || cost.items().isEmpty(),
                "parts were not taken");

        helper.assertTrue(t.damage(NAVIGATION, 3) == 1, "damage didn't stop at level 0");
        helper.assertTrue(Unlocks.level(t, NAVIGATION) == 0, "damage didn't lower the level");
        helper.assertTrue(t.repairedLevel(NAVIGATION) == 1, "damage forgot the repair");
        helper.assertFalse(Unlocks.check(server, t, SHORT_HOP, Optional.empty()).allowed(), "damaged TARDIS hops");

        give(player, cost);
        helper.assertTrue(Repairs.attempt(server, t, repair, Optional.of(player)).done(), "repairing again failed");
        helper.assertTrue(Unlocks.level(t, NAVIGATION) == 1 && t.damage(NAVIGATION) == 0, "second repair");

        Tag saved = TardisRecord.CODEC.encodeStart(NbtOps.INSTANCE, t).getOrThrow();
        TardisRecord back = TardisRecord.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
        helper.assertTrue(back.repairedLevel(NAVIGATION) == 1 && back.repairs().contains(repair)
                && back.stage().equals(t.stage()), "save round trip lost data: " + saved);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void stagesAdvanceAndAMilestoneRaisesCaps(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        TardisRecord t = fresh(helper, "Stages");
        for (String r : List.of("navigation_1", "power_1", "console_1")) {
            mustRepair(helper, server, t, r);
        }
        helper.assertTrue(t.stage().equals(Optional.of(TardisRB.id("patched"))), "after the wreck: " + t.stage());
        t.addCharge(100_000);
        mustRepair(helper, server, t, "navigation_2");
        helper.assertFalse(Repairs.attempt(server, t, TardisRB.id("power_2"), Optional.empty()).done(),
                "power_2 done without its research");
        t.completeResearch(TardisRB.id("artron_theory"));
        mustRepair(helper, server, t, "power_2");
        t.addCharge(100_000);
        helper.assertTrue(Unlocks.cap(t, NAVIGATION) == 3, "navigation cap before the milestone");
        mustRepair(helper, server, t, "hull_1");
        mustRepair(helper, server, t, "rooms_1");
        helper.assertTrue(t.milestones().contains(TardisRB.id("restored_core")), "milestone not passed");
        helper.assertTrue(t.stage().equals(Optional.of(TardisRB.id("flight_worthy"))), "stage " + t.stage());
        helper.assertTrue(Unlocks.cap(t, NAVIGATION) == 4, "milestone didn't raise the navigation cap");
        helper.assertTrue(Unlocks.level(t, POWER) == 2, "power level");
        helper.succeed();
    }

    /** Writes a world datapack that changes a cost, reloads like /reload, and waits for the new value. */
    @GameTest(template = EMPTY, timeoutTicks = 600)
    public static void reloadPicksUpAChangedCost(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ResourceLocation id = TardisRB.id("hull_plating");
        Path pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("tardis_rb_test_cost");
        try {
            Files.createDirectories(pack.resolve("data/tardis_rb/tardis/cost"));
            Files.writeString(pack.resolve("pack.mcmeta"),
                    "{\"pack\": {\"pack_format\": 48, \"description\": \"TARDIS test\"}}", StandardCharsets.UTF_8);
            Files.writeString(pack.resolve("data/tardis_rb/tardis/cost/hull_plating.json"),
                    "{\"items\": [{\"ingredient\": {\"item\": \"minecraft:dirt\"}, \"count\": 3}]}",
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            helper.fail("couldn't write the test pack: " + e);
            return;
        }
        server.getPackRepository().reload();
        List<String> selected = new ArrayList<>(server.getPackRepository().getSelectedIds());
        String packId = "file/tardis_rb_test_cost";
        helper.assertTrue(server.getPackRepository().getAvailableIds().contains(packId),
                "pack not found: " + server.getPackRepository().getAvailableIds());
        selected.add(packId);
        server.reloadResources(selected);
        // Only the items change, so other tests using this cost (with free items) don't notice the reload.
        helper.succeedWhen(() -> {
            Cost now = TardisContent.get().costs().get(id);
            helper.assertTrue(now.items().size() == 1 && now.items().get(0).count() == 3
                    && now.items().get(0).ingredient().test(new ItemStack(Items.DIRT)), "cost still " + now);
        });
    }

    private static void mustRepair(GameTestHelper helper, MinecraftServer server, TardisRecord t, String repair) {
        Repairs.Outcome out = Repairs.attempt(server, t, TardisRB.id(repair), Optional.empty());
        if (!out.done()) {
            helper.fail(repair + ": " + out.problem().get().getString());
        }
    }

    private static void give(ServerPlayer player, Cost cost) {
        for (Cost.ItemCost c : cost.items()) {
            ItemStack[] options = c.ingredient().getItems();
            ItemStack stack = options.length > 0 ? options[0].copy() : new ItemStack(Items.STONE);
            stack.setCount(c.count());
            player.getInventory().add(stack);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }

    private static void put(Map<String, Map<ResourceLocation, JsonElement>> files, String folder, String path,
            String json) {
        files.computeIfAbsent(folder, f -> new HashMap<>()).put(id(path), JsonParser.parseString(json));
    }
}
