package com.porcupinestudios.lotrport.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.faction.Alignment;
import com.porcupinestudios.lotrport.player.PlayerLotrData;
import com.porcupinestudios.lotrport.registry.LotrRegistries;
import com.porcupinestudios.ringbearer.api.condition.Condition;
import com.porcupinestudios.ringbearer.api.condition.ConditionContext;
import com.porcupinestudios.ringbearer.api.condition.Conditions;
import com.porcupinestudios.ringbearer.api.history.HistoryEntry;
import com.porcupinestudios.ringbearer.api.history.HistoryLog;
import com.porcupinestudios.ringbearer.api.history.HistoryTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Stage 1 done-when: alignment survives a save and load, the history log records a block change and survives a save
 * and load, and condition JSON is read through the shared registry. Run with {@code ./gradlew :lotr:runGameTestServer}.
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FoundationTests {
    private static final String EMPTY = "empty";
    private static final ResourceLocation GONDOR = LotrPort.id("gondor");

    private FoundationTests() {}

    @GameTest(template = EMPTY)
    public static void alignmentSurvivesSaveAndLoad(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        int before = HistoryLog.get(helper.getLevel().getServer()).size();
        Alignment.set(player, GONDOR, 250f);
        helper.assertTrue(Alignment.get(player, GONDOR) == 250f, "alignment was not set");

        Tag saved = PlayerLotrData.CODEC.encodeStart(NbtOps.INSTANCE, player.getData(LotrRegistries.PLAYER_DATA))
                .getOrThrow();
        PlayerLotrData loaded = PlayerLotrData.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
        helper.assertTrue(loaded.alignment(GONDOR) == 250f, "alignment lost in save/load: " + saved);

        int after = HistoryLog.get(helper.getLevel().getServer()).size();
        helper.assertTrue(after == before + 1, "alignment change not written to history");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void historyRecordsBlockPlace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0));
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        HistoryLog log = HistoryLog.get(level.getServer());
        int before = log.size();

        NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(
                BlockSnapshot.create(level.dimension(), level, pos), Blocks.AIR.defaultBlockState(), player));

        helper.assertTrue(log.size() == before + 1, "block place not recorded");
        HistoryEntry entry = log.entries().get(log.size() - 1);
        helper.assertTrue(entry.type().equals(HistoryTypes.BLOCK_PLACED), "wrong entry type " + entry.type());
        helper.assertTrue(entry.pos().filter(pos::equals).isPresent(), "wrong position " + entry.pos());
        helper.assertTrue("minecraft:stone".equals(entry.data().getString("block")), "wrong block " + entry.data());

        CompoundTag saved = log.save(new CompoundTag(), level.registryAccess());
        HistoryLog loaded = HistoryLog.load(saved, level.registryAccess());
        helper.assertTrue(loaded.size() == log.size(), "history lost entries in save/load");
        helper.assertTrue(loaded.entries().get(loaded.size() - 1).equals(entry), "history entry changed in save/load");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void conditionsReadFromJson(GameTestHelper helper) {
        ConditionContext context = ConditionContext.of(helper.getLevel());
        helper.assertTrue(parse("{\"type\":\"ringbearer_api:all\",\"conditions\":[{\"type\":\"ringbearer_api:always\"}]}")
                .test(context), "all(always) should be true");
        helper.assertFalse(parse("{\"type\":\"ringbearer_api:not\",\"condition\":{\"type\":\"ringbearer_api:always\"}}")
                .test(context), "not(always) should be false");
        helper.assertFalse(parse("{\"type\":\"ringbearer_api:any\",\"conditions\":[]}").test(context),
                "any() should be false");
        helper.succeed();
    }

    private static Condition parse(String json) {
        return Conditions.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }
}
