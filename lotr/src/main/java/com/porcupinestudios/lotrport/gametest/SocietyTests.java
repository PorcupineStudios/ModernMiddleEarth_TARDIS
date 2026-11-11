package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.society.ComponentMap;
import com.porcupinestudios.lotrport.society.CoreComponents;
import com.porcupinestudios.lotrport.society.Owner;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Persons;
import com.porcupinestudios.lotrport.society.Society;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The society core (colony-first architecture): Person records, owners, components, old saves moved over, and the
 * scale target (50 colonies x 150 people) saved and read back quickly.
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SocietyTests {
    private static final ResourceLocation SOLDIER = LotrPort.id("gondor_soldier");
    private static final ResourceLocation GONDOR = LotrPort.id("gondor");

    private SocietyTests() {}

    @GameTest(template = "empty")
    public static void ownersAndComponentsSurviveSavingAndUnknownDataIsKept(GameTestHelper helper) {
        for (Owner o : List.of(Owner.player(UUID.randomUUID()), Owner.colony(UUID.randomUUID()),
                new Owner.OfRealm(LotrPort.id("realm/3")), Owner.NONE)) {
            helper.assertTrue(Owner.parse(o.encode()).result().equals(Optional.of(o)), "owner did not survive: " + o);
        }
        ComponentMap map = new ComponentMap();
        CompoundTag saved = new CompoundTag();
        CompoundTag unit = new CompoundTag();
        unit.putInt("v", 1);
        CompoundTag data = new CompoundTag();
        data.putString("order", "guard");
        data.putLong("hired", 42);
        unit.put("d", data);
        saved.put("lotr_port:unit", unit);
        CompoundTag future = new CompoundTag();
        future.putInt("v", 3);
        future.putString("d", "a feature this build does not have");
        saved.put("someaddon:morale", future);
        map = ComponentMap.load(saved);
        Optional<CoreComponents.Unit> read = map.get(CoreComponents.UNIT);
        helper.assertTrue(read.map(u -> u.order() == CoreComponents.Order.GUARD && u.hired() == 42).orElse(false),
                "the unit component was not read: " + read);
        CompoundTag again = map.save();
        helper.assertTrue(again.getCompound("someaddon:morale").equals(future),
                "data of an unknown component was not kept: " + again);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void oldSavesMoveIntoThePersonRegistry(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        // version 1 People: one hired unit
        UUID unitId = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        CompoundTag people = new CompoundTag();
        people.putInt("dataVersion", 1);
        ListTag list = new ListTag();
        CompoundTag rec = new CompoundTag();
        rec.putUUID("entity", unitId);
        rec.putString("type", SOLDIER.toString());
        rec.putString("name", "Beregond");
        rec.putString("faction", GONDOR.toString());
        rec.putUUID("owner", player);
        rec.putLong("since", 7);
        rec.putBoolean("alive", true);
        list.add(rec);
        people.put("people", list);
        Persons persons = Persons.load(people, server.registryAccess());
        Person p = persons.get(unitId).orElse(null);
        helper.assertTrue(p != null && p.owner().equals(Owner.player(player)) && p.has(CoreComponents.UNIT)
                && p.faction().equals(Optional.of(GONDOR)), "the hired unit did not move over: " + p);
        helper.assertTrue(persons.ownedBy(Owner.player(player), CoreComponents.UNIT).size() == 1,
                "the owner index misses the unit");

        // version 1 Colonies: a colony with a citizen and an applicant in its own lists
        UUID colonyId = UUID.randomUUID();
        UUID citizen = UUID.randomUUID();
        UUID applicant = UUID.randomUUID();
        CompoundTag c = new CompoundTag();
        c.put("id", UUIDUtil.CODEC.encodeStart(NbtOps.INSTANCE, colonyId).getOrThrow());
        c.putString("name", "Old Hold");
        c.put("founder", UUIDUtil.CODEC.encodeStart(NbtOps.INSTANCE, player).getOrThrow());
        c.putString("faction", GONDOR.toString());
        c.putString("dimension", Level.OVERWORLD.location().toString());
        c.put("hall", new IntArrayTag(new int[] {100000, 64, 100000}));
        ListTag citizens = new ListTag();
        citizens.add(legacy(citizen, "joined", 5, true));
        c.put("citizens", citizens);
        ListTag applicants = new ListTag();
        CompoundTag a = legacy(applicant, "days", 2, false);
        applicants.add(a);
        c.put("applicants", applicants);
        CompoundTag colonies = new CompoundTag();
        colonies.putInt("dataVersion", 1);
        ListTag cl = new ListTag();
        cl.add(c);
        colonies.put("colonies", cl);
        Colonies old = Colonies.load(colonies, server.registryAccess());
        Colony colony = old.get(colonyId).orElse(null);
        helper.assertTrue(colony != null, "the old colony did not load");
        old.migratePeople(server);
        Person cit = Society.person(server, citizen).orElse(null);
        helper.assertTrue(cit != null && cit.owner().equals(colony.asOwner())
                && cit.get(CoreComponents.CITIZEN).map(CoreComponents.Citizen::rare).orElse(false),
                "the old citizen did not move over: " + cit);
        Person app = Society.person(server, applicant).orElse(null);
        helper.assertTrue(app != null && app.get(CoreComponents.APPLICANT).map(x -> x.days() == 2).orElse(false),
                "the old applicant did not move over: " + app);
        helper.assertTrue(Society.citizenCount(server, colony) == 1, "citizens counted wrong");
        Society.persons(server).remove(server, citizen);
        Society.persons(server).remove(server, applicant);
        helper.succeed();
    }

    private static CompoundTag legacy(UUID id, String key, int value, boolean rare) {
        CompoundTag t = new CompoundTag();
        t.put("entity", UUIDUtil.CODEC.encodeStart(NbtOps.INSTANCE, id).getOrThrow());
        t.putString("type", SOLDIER.toString());
        t.putString("name", "Old " + key);
        if ("joined".equals(key)) {
            t.putLong(key, value);
            t.putBoolean("rare", rare);
        } else {
            t.putInt(key, value);
        }
        return t;
    }

    /** The scale target: 50 colonies x 150 people saved, read back, and queried by colony, within a time budget. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void fiftyColoniesOfOneHundredFiftyPeopleSaveAndLoad(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Persons persons = new Persons();
        List<UUID> colonies = new ArrayList<>();
        for (int c = 0; c < 50; c++) {
            UUID colony = UUID.randomUUID();
            colonies.add(colony);
            for (int i = 0; i < 150; i++) {
                Person p = persons.migrated(UUID.randomUUID(), SOLDIER, "P" + c + "_" + i, i, Owner.colony(colony));
                persons.setQuiet(p, CoreComponents.CITIZEN, new CoreComponents.Citizen(i, i % 50 == 0));
                if (i % 10 == 0) {
                    persons.setQuiet(p, CoreComponents.UNIT, CoreComponents.Unit.hired(i).withWage(2));
                }
            }
        }
        long t0 = System.nanoTime();
        CompoundTag saved = persons.save(new CompoundTag(), server.registryAccess());
        long t1 = System.nanoTime();
        Persons back = Persons.load(saved, server.registryAccess());
        long t2 = System.nanoTime();
        int citizens = 0;
        int soldiers = 0;
        for (UUID c : colonies) {
            citizens += back.count(Owner.colony(c), CoreComponents.CITIZEN);
            soldiers += back.ownedBy(Owner.colony(c), CoreComponents.UNIT).size();
        }
        long t3 = System.nanoTime();
        LotrPort.LOG.info("[society] 7500 persons: save {} ms, load {} ms, 100 colony queries {} us",
                (t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000, (t3 - t2) / 1000);
        helper.assertTrue(back.size() == 7500, "persons after loading: " + back.size());
        helper.assertTrue(citizens == 7500 && soldiers == 750, "citizens " + citizens + ", soldiers " + soldiers);
        helper.assertTrue((t3 - t2) < 20_000_000L, "colony queries took " + (t3 - t2) / 1000 + " us");
        helper.assertTrue((t2 - t0) < 2_000_000_000L, "saving and loading took " + (t2 - t0) / 1_000_000 + " ms");
        helper.succeed();
    }
}
