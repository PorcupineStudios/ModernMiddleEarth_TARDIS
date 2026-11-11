package com.porcupinestudios.lotrport.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * How a {@link View} goes over the wire. Written by hand (records with more than six fields don't fit
 * {@code StreamCodec.composite}); each type has a write and a read that mirror each other. Changing a shape here
 * changes the protocol: bump {@link UiNet#VERSION}.
 */
public final class UiCodecs {
    /** Most rows one table sends (a colony of 100+ people fits; a world list beyond this says it was cut). */
    public static final int MAX_ROWS = 2000;

    private UiCodecs() {}

    public static final StreamCodec<RegistryFriendlyByteBuf, View> VIEW = StreamCodec.of(UiCodecs::writeView,
            UiCodecs::readView);

    // ---------------------------------------------------------------- small pieces

    private static void text(RegistryFriendlyByteBuf buf, Component c) {
        ComponentSerialization.STREAM_CODEC.encode(buf, c);
    }

    private static Component text(RegistryFriendlyByteBuf buf) {
        return ComponentSerialization.STREAM_CODEC.decode(buf);
    }

    private static void str(RegistryFriendlyByteBuf buf, String s) {
        ByteBufCodecs.STRING_UTF8.encode(buf, s);
    }

    private static String str(RegistryFriendlyByteBuf buf) {
        return ByteBufCodecs.STRING_UTF8.decode(buf);
    }

    private static <T> void list(RegistryFriendlyByteBuf buf, List<T> list, BiConsumer<RegistryFriendlyByteBuf, T> w) {
        buf.writeVarInt(list.size());
        for (T t : list) {
            w.accept(buf, t);
        }
    }

    private static <T> List<T> list(RegistryFriendlyByteBuf buf, Function<RegistryFriendlyByteBuf, T> r) {
        int n = buf.readVarInt();
        if (n < 0 || n > 100_000) {
            throw new IllegalArgumentException("UI list of " + n);
        }
        List<T> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(r.apply(buf));
        }
        return out;
    }

    private static <T> void opt(RegistryFriendlyByteBuf buf, Optional<T> o, BiConsumer<RegistryFriendlyByteBuf, T> w) {
        buf.writeBoolean(o.isPresent());
        o.ifPresent(t -> w.accept(buf, t));
    }

    private static <T> Optional<T> opt(RegistryFriendlyByteBuf buf, Function<RegistryFriendlyByteBuf, T> r) {
        return buf.readBoolean() ? Optional.of(r.apply(buf)) : Optional.empty();
    }

    private static <E extends Enum<E>> E enumOf(RegistryFriendlyByteBuf buf, E[] values) {
        int i = buf.readVarInt();
        return i >= 0 && i < values.length ? values[i] : values[0];
    }

    // ---------------------------------------------------------------- types

    static void writeAction(RegistryFriendlyByteBuf buf, Action a) {
        str(buf, a.id());
        text(buf, a.label());
        buf.writeBoolean(a.enabled());
        opt(buf, a.why(), UiCodecs::text);
        buf.writeBoolean(a.confirm());
    }

    static Action readAction(RegistryFriendlyByteBuf buf) {
        return new Action(str(buf), text(buf), buf.readBoolean(), opt(buf, UiCodecs::text), buf.readBoolean());
    }

    static void writeColumn(RegistryFriendlyByteBuf buf, Column c) {
        str(buf, c.id());
        text(buf, c.label());
        buf.writeVarInt(c.weight());
        buf.writeVarInt(c.align().ordinal());
        buf.writeBoolean(c.numeric());
        buf.writeBoolean(c.searchable());
    }

    static Column readColumn(RegistryFriendlyByteBuf buf) {
        return new Column(str(buf), text(buf), buf.readVarInt(), enumOf(buf, Column.Align.values()), buf.readBoolean(),
                buf.readBoolean());
    }

    static void writeCell(RegistryFriendlyByteBuf buf, Cell c) {
        str(buf, c.text());
        buf.writeVarInt(c.tone().ordinal());
        buf.writeDouble(c.sort());
    }

    static Cell readCell(RegistryFriendlyByteBuf buf) {
        return new Cell(str(buf), enumOf(buf, Tone.values()), buf.readDouble());
    }

    static void writeRow(RegistryFriendlyByteBuf buf, Row r) {
        str(buf, r.id());
        list(buf, r.cells(), UiCodecs::writeCell);
        list(buf, r.tags(), UiCodecs::str);
    }

    static Row readRow(RegistryFriendlyByteBuf buf) {
        return new Row(str(buf), list(buf, UiCodecs::readCell), list(buf, UiCodecs::str));
    }

    static void writeGroup(RegistryFriendlyByteBuf buf, FilterGroup g) {
        str(buf, g.id());
        text(buf, g.label());
        list(buf, g.filters(), (b, f) -> {
            str(b, f.tag());
            text(b, f.label());
        });
    }

    static FilterGroup readGroup(RegistryFriendlyByteBuf buf) {
        return new FilterGroup(str(buf), text(buf), list(buf, b -> new FilterGroup.Filter(str(b), text(b))));
    }

    static void writeStat(RegistryFriendlyByteBuf buf, Stat s) {
        text(buf, s.label());
        str(buf, s.value());
        buf.writeVarInt(s.tone().ordinal());
        buf.writeDouble(s.bar());
        opt(buf, s.stepper(), (b, st) -> {
            str(b, st.action());
            b.writeInt(st.value());
            b.writeInt(st.min());
            b.writeInt(st.max());
            b.writeVarInt(st.step());
        });
    }

    static Stat readStat(RegistryFriendlyByteBuf buf) {
        return new Stat(text(buf), str(buf), enumOf(buf, Tone.values()), buf.readDouble(),
                opt(buf, b -> new Stat.Stepper(str(b), b.readInt(), b.readInt(), b.readInt(), b.readVarInt())));
    }

    static void writePage(RegistryFriendlyByteBuf buf, Page page) {
        switch (page) {
            case Page.Table t -> {
                buf.writeByte(0);
                list(buf, t.columns(), UiCodecs::writeColumn);
                list(buf, t.filters(), UiCodecs::writeGroup);
                list(buf, t.rows().size() > MAX_ROWS ? t.rows().subList(0, MAX_ROWS) : t.rows(), UiCodecs::writeRow);
                buf.writeVarInt(t.sortColumn() + 1);
                buf.writeBoolean(t.descending());
                opt(buf, t.open(), UiCodecs::str);
                list(buf, t.rowActions(), UiCodecs::writeAction);
                list(buf, t.buttons(), UiCodecs::writeAction);
                buf.writeVarInt(Math.max(t.total(), t.rows().size()));
                text(buf, t.empty());
            }
            case Page.Stats s -> {
                buf.writeByte(1);
                list(buf, s.sections(), (b, sec) -> {
                    text(b, sec.title());
                    list(b, sec.stats(), UiCodecs::writeStat);
                });
                list(buf, s.buttons(), UiCodecs::writeAction);
            }
        }
    }

    static Page readPage(RegistryFriendlyByteBuf buf) {
        byte kind = buf.readByte();
        if (kind == 0) {
            return new Page.Table(list(buf, UiCodecs::readColumn), list(buf, UiCodecs::readGroup),
                    list(buf, UiCodecs::readRow), buf.readVarInt() - 1, buf.readBoolean(), opt(buf, UiCodecs::str),
                    list(buf, UiCodecs::readAction), list(buf, UiCodecs::readAction), buf.readVarInt(), text(buf));
        }
        return new Page.Stats(list(buf, b -> new Page.Section(text(b), list(b, UiCodecs::readStat))),
                list(buf, UiCodecs::readAction));
    }

    static void writeTarget(RegistryFriendlyByteBuf buf, View.Target t) {
        ResourceLocation.STREAM_CODEC.encode(buf, t.view());
        str(buf, t.context());
        str(buf, t.tab());
    }

    static View.Target readTarget(RegistryFriendlyByteBuf buf) {
        return new View.Target(ResourceLocation.STREAM_CODEC.decode(buf), str(buf), str(buf));
    }

    static void writeView(RegistryFriendlyByteBuf buf, View v) {
        ResourceLocation.STREAM_CODEC.encode(buf, v.id());
        str(buf, v.context());
        text(buf, v.title());
        list(buf, v.tabs(), (b, t) -> {
            str(b, t.id());
            text(b, t.label());
        });
        str(buf, v.tab());
        writePage(buf, v.page());
        opt(buf, v.back(), UiCodecs::writeTarget);
    }

    static View readView(RegistryFriendlyByteBuf buf) {
        return new View(ResourceLocation.STREAM_CODEC.decode(buf), str(buf), text(buf),
                list(buf, b -> new View.Tab(str(b), text(b))), str(buf), readPage(buf), opt(buf, UiCodecs::readTarget));
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, View.Target> TARGET = StreamCodec.of(
            UiCodecs::writeTarget, UiCodecs::readTarget);
}
