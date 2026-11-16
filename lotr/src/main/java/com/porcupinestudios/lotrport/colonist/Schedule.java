package com.porcupinestudios.lotrport.colonist;

import java.util.Locale;

/**
 * A colony's timetable for some of its colonists, ONI style: 24 hour blocks, each Work, Leisure, Sleep or Any. Kept in
 * the colony's {@link Schedules}; a colonist follows the one their {@link Orders#schedule} names.
 *
 * @param id never changes (orders point at it)
 * @param name what players see
 * @param blocks 24 letters, one per clock hour from midnight: {@code W}, {@code L}, {@code S}, {@code A}
 */
public record Schedule(String id, String name, String blocks) {
    public static final int HOURS = 24;

    /** What an hour is for. */
    public enum Block {
        WORK('W'),
        LEISURE('L'),
        SLEEP('S'),
        /** Whatever they need most: sleep when tired, leisure when they have had too little, else work. */
        ANY('A');

        public final char letter;

        Block(char letter) {
            this.letter = letter;
        }

        public static Block of(char c) {
            for (Block b : values()) {
                if (b.letter == Character.toUpperCase(c)) {
                    return b;
                }
            }
            return ANY;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public Schedule {
        StringBuilder b = new StringBuilder(HOURS);
        for (int h = 0; h < HOURS; h++) {
            b.append(h < blocks.length() ? Block.of(blocks.charAt(h)).letter : Block.ANY.letter);
        }
        blocks = b.toString();
    }

    public Block at(int hour) {
        return Block.of(blocks.charAt(Math.floorMod(hour, HOURS)));
    }

    public Schedule with(int hour, Block block) {
        char[] c = blocks.toCharArray();
        c[Math.floorMod(hour, HOURS)] = block.letter;
        return new Schedule(id, name, new String(c));
    }

    public Schedule named(String newName) {
        return new Schedule(id, newName, blocks);
    }

    public int count(Block block) {
        int n = 0;
        for (int h = 0; h < HOURS; h++) {
            if (at(h) == block) {
                n++;
            }
        }
        return n;
    }

    /** The clock hour (0-23, midnight 0) at a level's day time: Minecraft's day starts at 06:00. */
    public static int hourOf(long dayTime) {
        return (int) ((Math.floorMod(dayTime, 24000L) / 1000 + 6) % HOURS);
    }
}
