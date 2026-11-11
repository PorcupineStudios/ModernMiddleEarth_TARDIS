package com.porcupinestudios.lotrport.society;

import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.Event;

/**
 * Events of the society core, posted on the NeoForge game bus (server thread). Systems that need to react
 * subscribe; the code that posts them never calls a system directly.
 */
public abstract class SocietyEvent extends Event {
    private final MinecraftServer server;

    protected SocietyEvent(MinecraftServer server) {
        this.server = server;
    }

    public MinecraftServer server() {
        return server;
    }

    /** Something about one Person. */
    public abstract static class OfPerson extends SocietyEvent {
        private final Person person;

        protected OfPerson(MinecraftServer server, Person person) {
            super(server);
            this.person = person;
        }

        public Person person() {
            return person;
        }
    }

    /** A Person record was made (hired, joined, applied...). */
    public static final class Added extends OfPerson {
        public Added(MinecraftServer server, Person person) {
            super(server, person);
        }
    }

    /** A Person changed owner (hired out, stationed, a colony taken). */
    public static final class OwnerChanged extends OfPerson {
        private final Owner from;

        public OwnerChanged(MinecraftServer server, Person person, Owner from) {
            super(server, person);
            this.from = from;
        }

        public Owner from() {
            return from;
        }
    }

    /** A Person was given a new name ({@link Persons#rename}). */
    public static final class Renamed extends OfPerson {
        private final String oldName;

        public Renamed(MinecraftServer server, Person person, String oldName) {
            super(server, person);
            this.oldName = oldName;
        }

        public String oldName() {
            return oldName;
        }
    }

    /** A component was set or removed on a Person. */
    public static final class ComponentChanged extends OfPerson {
        private final ComponentType<?> type;

        public ComponentChanged(MinecraftServer server, Person person, ComponentType<?> type) {
            super(server, person);
            this.type = type;
        }

        public ComponentType<?> type() {
            return type;
        }
    }

    /** A Person died. The record stays, marked dead, for history. */
    public static final class Died extends OfPerson {
        private final Optional<GlobalPos> where;

        public Died(MinecraftServer server, Person person, Optional<GlobalPos> where) {
            super(server, person);
            this.where = where;
        }

        public Optional<GlobalPos> where() {
            return where;
        }
    }

    /** A Person record was removed (a migrant who left): nothing more will happen to it. */
    public static final class Removed extends OfPerson {
        public Removed(MinecraftServer server, Person person) {
            super(server, person);
        }
    }

    /** Something about one colony. */
    public abstract static class OfColony extends SocietyEvent {
        private final com.porcupinestudios.lotrport.colony.Colony colony;

        protected OfColony(MinecraftServer server, com.porcupinestudios.lotrport.colony.Colony colony) {
            super(server);
            this.colony = colony;
        }

        public com.porcupinestudios.lotrport.colony.Colony colony() {
            return colony;
        }
    }

    /** A colony was founded (by a player or an AI ruler). */
    public static final class ColonyFounded extends OfColony {
        public ColonyFounded(MinecraftServer server, com.porcupinestudios.lotrport.colony.Colony colony) {
            super(server, colony);
        }
    }

    /** A colony was removed. */
    public static final class ColonyDisbanded extends OfColony {
        public ColonyDisbanded(MinecraftServer server, com.porcupinestudios.lotrport.colony.Colony colony) {
            super(server, colony);
        }
    }
}
