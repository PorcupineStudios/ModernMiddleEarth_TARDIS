package com.porcupinestudios.lotrport.colonist;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.society.ComponentType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A colony's schedules (a colony part). The first is the standard one everybody without orders follows; it can be
 * changed but not removed. Absent: just the standard schedule from {@code colonist_sleep/settings.json}.
 */
public record Schedules(List<Schedule> list) {
    public static final String STANDARD = "standard";
    /** Most schedules one colony keeps. */
    public static final int MAX = 12;

    private static final Codec<Schedule> SCHEDULE_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(Schedule::id),
            Codec.STRING.fieldOf("name").forGetter(Schedule::name),
            Codec.STRING.fieldOf("blocks").forGetter(Schedule::blocks))
            .apply(i, Schedule::new));
    public static final Codec<Schedules> CODEC = SCHEDULE_CODEC.listOf().xmap(Schedules::new, Schedules::list);
    public static final ComponentType<Schedules> TYPE = ComponentType.register(LotrPort.id("schedules"), CODEC);

    public Schedules {
        list = List.copyOf(list);
    }

    /** Just the standard schedule, as the data says it. */
    public static Schedules standard() {
        return new Schedules(List.of(new Schedule(STANDARD, "Standard", ColonistData.sleep().standardBlocks())));
    }

    public Schedule first() {
        return list.isEmpty() ? standard().list.get(0) : list.get(0);
    }

    public Optional<Schedule> get(String id) {
        for (Schedule s : list) {
            if (s.id().equals(id)) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    /** The schedule with this id, else the standard one (a removed schedule's people fall back to it). */
    public Schedule orFirst(String id) {
        return get(id).orElse(first());
    }

    public Schedules put(Schedule s) {
        List<Schedule> out = new ArrayList<>(list);
        for (int i = 0; i < out.size(); i++) {
            if (out.get(i).id().equals(s.id())) {
                out.set(i, s);
                return new Schedules(out);
            }
        }
        out.add(s);
        return new Schedules(out);
    }

    /** Without that schedule; the first one stays. */
    public Schedules remove(String id) {
        if (list.isEmpty() || list.get(0).id().equals(id)) {
            return this;
        }
        List<Schedule> out = new ArrayList<>(list);
        out.removeIf(s -> s.id().equals(id));
        return new Schedules(out);
    }

    /** A new id: {@code s<n>} not yet used. */
    public String freeId() {
        for (int n = 2; ; n++) {
            String id = "s" + n;
            if (get(id).isEmpty()) {
                return id;
            }
        }
    }
}
