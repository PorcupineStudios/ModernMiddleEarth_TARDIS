package com.porcupinestudios.tardisrb.gravity;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.porcupinestudios.tardisrb.TardisRB;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Changing gravity by command (Aaron, 2026-11-04: "commands to change local gravity"). Two multipliers on the
 * {@code GRAVITY} attribute, so they work anywhere, reach the client on their own (the attribute is synced) and also
 * scale space gravity ({@code space/Orbits} reads them):
 * <ul>
 * <li>per entity: {@code /gravity set <targets> <scale>}, {@code /gravity reset <targets>} (lasts until reset or
 * death; modifier {@code tardis_rb:gravity_scale});</li>
 * <li>per dimension: {@code /gravity dimension <dimension> <scale>} or {@code ... reset}, saved with the world and
 * put on every living thing in it (modifier {@code tardis_rb:dimension_gravity}).</li>
 * </ul>
 * {@code /gravity get [targets]} shows what applies. Scale 0 is weightless, 1 normal.
 */
public final class GravityScale {
    public static final ResourceLocation ENTITY_ID = TardisRB.id("gravity_scale");
    public static final ResourceLocation DIMENSION_ID = TardisRB.id("dimension_gravity");

    private GravityScale() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> commands(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener(GravityScale::onJoin);
    }

    /** The command and dimension multipliers on this entity together (1 = normal). */
    public static double of(Entity e) {
        if (!(e instanceof LivingEntity living)) {
            return 1;
        }
        AttributeInstance gravity = living.getAttribute(Attributes.GRAVITY);
        if (gravity == null) {
            return 1;
        }
        double scale = 1;
        AttributeModifier own = gravity.getModifier(ENTITY_ID);
        if (own != null) {
            scale *= 1 + own.amount();
        }
        AttributeModifier dim = gravity.getModifier(DIMENSION_ID);
        if (dim != null) {
            scale *= 1 + dim.amount();
        }
        return scale;
    }

    private static void set(LivingEntity living, ResourceLocation id, double scale) {
        AttributeInstance gravity = living.getAttribute(Attributes.GRAVITY);
        if (gravity == null) {
            return;
        }
        if (scale == 1) {
            gravity.removeModifier(id);
        } else {
            gravity.addOrUpdateTransientModifier(new AttributeModifier(id, scale - 1,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /** Sets a living thing's own gravity multiplier; true if it has gravity at all. */
    public static boolean setEntity(Entity e, double scale) {
        if (e instanceof LivingEntity living && living.getAttribute(Attributes.GRAVITY) != null) {
            set(living, ENTITY_ID, scale);
            return true;
        }
        return false;
    }

    public static void setDimension(MinecraftServer server, ResourceKey<Level> dimension, double scale) {
        Saved.get(server).put(dimension, scale);
        ServerLevel level = server.getLevel(dimension);
        if (level != null) {
            for (Entity e : level.getAllEntities()) {
                if (e instanceof LivingEntity living) {
                    set(living, DIMENSION_ID, scale);
                }
            }
        }
    }

    public static double dimension(MinecraftServer server, ResourceKey<Level> dimension) {
        return Saved.get(server).scales.getOrDefault(dimension.location(), 1.0);
    }

    private static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof LivingEntity living) {
            set(living, DIMENSION_ID, dimension(level.getServer(), level.dimension()));
        }
    }

    private static void commands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("gravity").requires(s -> s.hasPermission(2))
                .then(Commands.literal("get")
                        .executes(c -> get(c.getSource(), java.util.List.of(c.getSource().getEntityOrException())))
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(c -> get(c.getSource(), EntityArgument.getEntities(c, "targets")))))
                .then(Commands.literal("set")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .then(Commands.argument("scale", DoubleArgumentType.doubleArg(0, 10))
                                        .executes(c -> setAll(c.getSource(), EntityArgument.getEntities(c, "targets"),
                                                DoubleArgumentType.getDouble(c, "scale"))))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(c -> setAll(c.getSource(), EntityArgument.getEntities(c, "targets"), 1))))
                .then(Commands.literal("dimension")
                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                .executes(c -> {
                                    ServerLevel l = DimensionArgument.getDimension(c, "dimension");
                                    double s = dimension(c.getSource().getServer(), l.dimension());
                                    c.getSource().sendSuccess(() -> Component.literal("Gravity in "
                                            + l.dimension().location() + ": x" + s), false);
                                    return 1;
                                })
                                .then(Commands.literal("reset").executes(c -> dim(c.getSource(),
                                        DimensionArgument.getDimension(c, "dimension"), 1)))
                                .then(Commands.argument("scale", DoubleArgumentType.doubleArg(0, 10))
                                        .executes(c -> dim(c.getSource(),
                                                DimensionArgument.getDimension(c, "dimension"),
                                                DoubleArgumentType.getDouble(c, "scale")))))));
    }

    private static int get(CommandSourceStack source, Collection<? extends Entity> targets) {
        for (Entity e : targets) {
            double s = of(e);
            source.sendSuccess(() -> Component.literal(e.getName().getString() + ": gravity x" + s), false);
        }
        return targets.size();
    }

    private static int setAll(CommandSourceStack source, Collection<? extends Entity> targets, double scale) {
        int n = 0;
        for (Entity e : targets) {
            if (setEntity(e, scale)) {
                n++;
            }
        }
        int count = n;
        source.sendSuccess(() -> Component.literal("Gravity x" + scale + " for " + count + " entities"), true);
        return n;
    }

    private static int dim(CommandSourceStack source, ServerLevel level, double scale) {
        setDimension(source.getServer(), level.dimension(), scale);
        source.sendSuccess(() -> Component.literal("Gravity in " + level.dimension().location() + " is now x"
                + scale), true);
        return 1;
    }

    /** Dimension multipliers, saved with the world. */
    static final class Saved extends SavedData {
        static final int DATA_VERSION = 1;
        private static final SavedData.Factory<Saved> FACTORY = new SavedData.Factory<>(Saved::new, Saved::load, null);
        final Map<ResourceLocation, Double> scales = new HashMap<>();

        static Saved get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "tardis_rb_gravity");
        }

        void put(ResourceKey<Level> dimension, double scale) {
            if (scale == 1) {
                scales.remove(dimension.location());
            } else {
                scales.put(dimension.location(), scale);
            }
            setDirty();
        }

        static Saved load(CompoundTag tag, HolderLookup.Provider registries) {
            Saved s = new Saved();
            CompoundTag dims = tag.getCompound("dimensions");
            for (String key : dims.getAllKeys()) {
                ResourceLocation id = ResourceLocation.tryParse(key);
                if (id != null) {
                    s.scales.put(id, dims.getDouble(key));
                }
            }
            return s;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putInt("data_version", DATA_VERSION);
            CompoundTag dims = new CompoundTag();
            scales.forEach((k, v) -> dims.putDouble(k.toString(), v));
            tag.put("dimensions", dims);
            return tag;
        }
    }
}
