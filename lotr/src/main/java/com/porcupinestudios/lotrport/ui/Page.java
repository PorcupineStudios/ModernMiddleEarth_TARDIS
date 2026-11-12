package com.porcupinestudios.lotrport.ui;

import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;

/** What one tab shows. Only the open tab is built and sent, so a view with many tabs costs one tab's work. */
public sealed interface Page permits Page.Table, Page.Stats, Page.Profile, Page.Timetable, Page.Blocks {
    /** Buttons under the page. */
    List<Action> buttons();

    /**
     * A list the player can search, sort and filter. All of it happens on the client ({@link TableQuery}), so typing
     * in the search box sends nothing.
     *
     * @param sortColumn the column it is sorted by until the player picks another (-1: as sent)
     * @param open the action a click on a row sends (open the citizen's page), if any
     * @param rowActions buttons that act on the selected row (set a role, take items...)
     * @param total how many rows there are; more than {@code rows.size()} means the server cut the list
     * @param empty shown when there are no rows at all
     */
    record Table(List<Column> columns, List<FilterGroup> filters, List<Row> rows, int sortColumn, boolean descending,
            Optional<String> open, List<Action> rowActions, List<Action> buttons, int total, Component empty)
            implements Page {
        public Table {
            columns = List.copyOf(columns);
            filters = List.copyOf(filters);
            rows = List.copyOf(rows);
            rowActions = List.copyOf(rowActions);
            buttons = List.copyOf(buttons);
        }
    }

    /** Headed lists of label and value lines (an overview, the treasury). */
    record Stats(List<Section> sections, List<Action> buttons) implements Page {
        public Stats {
            sections = List.copyOf(sections);
            buttons = List.copyOf(buttons);
        }
    }

    /**
     * One person or thing up close: a portrait, a name the player may change, a line under it, then headed sections
     * in two columns (needs and facts on the left, thoughts and orders on the right).
     *
     * @param portrait the entity whose head and shoulders are drawn, if its body is loaded on the client
     * @param rename the action a new name is sent with (as the row), if this player may rename
     * @param subtitle under the name (race, age, work)
     * @param tree a family tree drawn in place of the right column (zoom with the wheel, drag to pan), if any
     */
    record Profile(Optional<java.util.UUID> portrait, Component name, Optional<String> rename, Component subtitle,
            List<Section> left, List<Section> right, List<Action> buttons, Optional<Tree> tree) implements Page {
        public Profile {
            left = List.copyOf(left);
            right = List.copyOf(right);
            buttons = List.copyOf(buttons);
        }

        public Profile(Optional<java.util.UUID> portrait, Component name, Optional<String> rename,
                Component subtitle, List<Section> left, List<Section> right, List<Action> buttons) {
            this(portrait, name, rename, subtitle, left, right, buttons, Optional.empty());
        }
    }

    /**
     * A family tree: people and the links between them; the client lays it out by generation round {@code focus}.
     * Clicking someone {@code openable} sends {@code open} with their id as the row.
     */
    record Tree(String focus, List<TreeNode> nodes, List<TreeLink> links, Optional<String> open) {
        public Tree {
            nodes = List.copyOf(nodes);
            links = List.copyOf(links);
        }
    }

    /**
     * One person in a tree; {@code detail} is a short line under the name (race and age).
     *
     * @param mark a badge on the box: {@link #DEAD}, {@link #LEFT} (left the colony) or empty
     */
    record TreeNode(String id, Component name, Component detail, boolean alive, boolean openable, String mark) {
        public static final String DEAD = "dead";
        public static final String LEFT = "left";
    }

    /**
     * A link: {@link #PARENT} (from the parent to the child), {@link #SPOUSE} (married now) or {@link #FORMER} (were
     * married: divorced, or one remarried).
     */
    record TreeLink(String from, String to, String kind) {
        public static final String PARENT = "parent";
        public static final String SPOUSE = "spouse";
        public static final String FORMER = "former";

        public boolean partners() {
            return !PARENT.equals(kind);
        }
    }

    /**
     * Rows of 24 hour cells (ONI's schedule screen): a palette of kinds above, and the player paints cells of the
     * editable rows with the picked kind (click or drag). Each painted cell sends {@code paint} with the row's id and
     * {@code hour * 16 + kind index} as the value; the client shows the change at once.
     *
     * @param kinds what a cell can be; the paintable ones make the palette
     * @param lines the rows; a row whose {@code editable} is false is only shown (a race's hours)
     * @param paint the action painting sends, if this player may paint
     * @param rowActions act on the selected row (picked by clicking its name)
     * @param notes lines of text under the grid
     */
    record Timetable(List<Kind> kinds, List<Line> lines, Optional<String> paint, List<Action> rowActions,
            List<Action> buttons, List<Component> notes) implements Page {
        public Timetable {
            kinds = List.copyOf(kinds);
            lines = List.copyOf(lines);
            rowActions = List.copyOf(rowActions);
            buttons = List.copyOf(buttons);
            notes = List.copyOf(notes);
        }
    }

    /**
     * What a timetable cell can be.
     *
     * @param letter the letter it has in a row's {@code cells}
     * @param swatch which of the theme's cell colours (0-7) it is drawn in; the client picks the colour
     */
    record Kind(char letter, Component label, int swatch, boolean paintable) {}

    /** One timetable row: its id comes back with actions; {@code cells} has one letter per hour. */
    record Line(String id, Component label, Component detail, String cells, boolean editable) {}

    record Section(Component title, List<Stat> stats) {
        public Section {
            stats = List.copyOf(stats);
        }
    }

    /**
     * A block program like Scratch's: a script of blocks drawn with their shapes (C blocks with arms for if/else,
     * stack blocks for commands, hexagons for yes/no conditions), each with inline inputs. Inputs are ordinary
     * pickers and steppers, so they send the same actions as anywhere else.
     *
     * @param add the "add a block" menu under the script, if any
     */
    record Blocks(List<Block> script, Optional<Stat.Picker> add, List<Action> buttons, List<Component> notes)
            implements Page {
        public Blocks {
            script = List.copyOf(script);
            buttons = List.copyOf(buttons);
            notes = List.copyOf(notes);
        }
    }

    /**
     * One block. {@code shape} "c" (head row, then arms), "stack" (one row) or "bool" (a hexagon, inside another
     * block's slot). {@code menu}, if any, opens when its plain parts are clicked (change, remove, move...).
     */
    record Block(String shape, int color, List<Part> head, List<Arm> arms, Optional<Stat.Picker> menu) {
        public Block {
            head = List.copyOf(head);
            arms = List.copyOf(arms);
        }
    }

    /** An arm of a C block: its label row (empty for the first), its blocks, and an "add" menu at its end. */
    record Arm(Component label, List<Block> body, Optional<Stat.Picker> add) {
        public Arm {
            body = List.copyOf(body);
        }
    }

    /**
     * A part of a block's row. {@code kind}: 0 text, 1 a picker (shows {@code text}), 2 a number (stepper), 3 a
     * nested block, 4 an empty slot that adds a block (picker).
     */
    record Part(int kind, Component text, Optional<Stat.Picker> picker, Optional<Stat.Stepper> stepper,
            Optional<Block> block) {
        public static Part text(String s) {
            return new Part(0, Component.literal(s), Optional.empty(), Optional.empty(), Optional.empty());
        }

        public static Part pick(String shown, Stat.Picker p) {
            return new Part(1, Component.literal(shown), Optional.of(p), Optional.empty(), Optional.empty());
        }

        public static Part number(Stat.Stepper st) {
            return new Part(2, Component.empty(), Optional.empty(), Optional.of(st), Optional.empty());
        }

        public static Part block(Block b) {
            return new Part(3, Component.empty(), Optional.empty(), Optional.empty(), Optional.of(b));
        }

        public static Part slot(Stat.Picker p) {
            return new Part(4, Component.empty(), Optional.of(p), Optional.empty(), Optional.empty());
        }
    }
}
