package com.porcupinestudios.lotrexport;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.IWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Writes creatures.json: for every LOTR mob, what riding it takes (rideable, saddle, inventory, the alignment LOTR
 * checks before you may ride, feed or saddle it, which mount armor fits, the rider it may spawn with), the sounds it
 * plays (recorded from a throwaway world as the mob plays them), its random variants (warg colours, spider kinds and
 * sizes), and a summary of the code that decides where it may spawn (the calls, constants and fields of its spawn
 * checks, read from the class files); plus every LOTR mount armor item (mount, material, protection, texture).
 */
final class CreatureExporter {
    private static final int SAMPLES = 5000;
    /** LOTR's spawn checks by SRG and MCP name. */
    private static final String[][] SPAWN_METHODS = {
            { "func_70601_bi", "getCanSpawnHere" }, { "canWorldGenSpawnAt" }, { "func_110161_a", "onSpawnWithEgg" } };
    private static final String[] VARIANTS = { "getWargType", "getSpiderType", "getSpiderScale", "canWargBeRidden",
            "canRideSpider", "getHorseType", "func_110265_bP" };

    private final JsonArray errors;
    private final List<Object[]> sounds = new ArrayList<>();
    private final Map<String, ClassNode> classes = new TreeMap<>();

    CreatureExporter(JsonArray errors) {
        this.errors = errors;
    }

    JsonObject creatures() {
        JsonObject root = new JsonObject();
        World world = world();
        List<ItemStack> armors = new ArrayList<>();
        root.add("mountArmor", mountArmor(armors));
        JsonObject entities = new JsonObject();
        try {
            Class<?> reg = Class.forName("lotr.common.entity.LOTREntities");
            Map<?, ?> classToId = (Map<?, ?>) reg.getField("classToIDMapping").get(null);
            Map<?, ?> idToString = (Map<?, ?>) reg.getField("IDToStringMapping").get(null);
            for (Map.Entry<?, ?> e : classToId.entrySet()) {
                Class<?> c = (Class<?>) e.getKey();
                if (!EntityLiving.class.isAssignableFrom(c) || java.lang.reflect.Modifier.isAbstract(c.getModifiers())) {
                    continue;
                }
                String name = String.valueOf(idToString.get(e.getValue()));
                try {
                    entities.add(name, creature(c, world, armors));
                } catch (Throwable t) {
                    error("creature " + name, t);
                }
            }
        } catch (Throwable t) {
            error("creatures", t);
        }
        root.add("entities", entities);
        return root;
    }

    private World world() {
        // A client world that hears every sound played in it: playSoundAtEntity hands each to its world accesses.
        World world = new WorldClient(new NetHandlerPlayClient(Minecraft.getMinecraft(), null, null),
                new WorldSettings(0L, WorldSettings.GameType.SURVIVAL, false, false, WorldType.DEFAULT), 0,
                EnumDifficulty.NORMAL, Minecraft.getMinecraft().mcProfiler);
        world.addWorldAccess((IWorldAccess) Proxy.newProxyInstance(IWorldAccess.class.getClassLoader(),
                new Class<?>[] { IWorldAccess.class }, (proxy, m, args) -> {
                    if (args != null && m.getName().startsWith("playSound")) {
                        int at = args[0] instanceof String ? 0 : 1; // playSoundToNearExcept starts with the player
                        sounds.add(new Object[] { args[at], args[at + 4], args[at + 5] });
                    }
                    return null;
                }));
        return world;
    }

    // ---------------------------------------------------------------- one creature

    private JsonObject creature(Class<?> c, World world, List<ItemStack> armors) throws Exception {
        JsonObject o = new JsonObject();
        o.addProperty("class", c.getName());
        EntityLiving entity = (EntityLiving) c.getConstructor(World.class).newInstance(world);
        entity.setPosition(0, 64, 0);
        o.add("sounds", sounds(entity));
        o.add("mount", mount(entity, world, armors));
        o.add("variants", variants(c, world));
        JsonObject spawn = new JsonObject();
        for (String[] names : SPAWN_METHODS) {
            JsonObject s = summary(c, names);
            if (s != null) {
                spawn.add(names[names.length - 1], s);
            }
        }
        Object dark = NpcExporter.field(entity, "spawnsInDarkness");
        if (dark instanceof Boolean d) {
            spawn.addProperty("spawnsInDarkness", d);
        }
        o.add("spawn", spawn);
        return o;
    }

    private JsonObject sounds(EntityLiving e) {
        JsonObject o = new JsonObject();
        o.add("ambient", played(() -> e.playLivingSound()));
        o.add("step", played(() -> invoke(e, new Class<?>[] { int.class, int.class, int.class,
                net.minecraft.block.Block.class }, new Object[] { 0, 64, 0, Blocks.grass }, "func_145780_a",
                "playStepSound")));
        o.addProperty("hurt", str(invoke(e, null, null, "func_70621_aR", "getHurtSound")));
        o.addProperty("death", str(invoke(e, null, null, "func_70673_aS", "getDeathSound")));
        o.addProperty("attack", str(invoke(e, null, null, "getAttackSound")));
        o.add("volume", NpcExporter.number(invoke(e, null, null, "func_70599_aP", "getSoundVolume")));
        o.add("interval", NpcExporter.number(invoke(e, null, null, "func_70627_aG", "getTalkInterval")));
        return o;
    }

    /** What the action played: [sound, volume, pitch] each, or null. */
    private JsonArray played(Runnable action) {
        sounds.clear();
        try {
            action.run();
        } catch (Throwable ignored) {
            // nothing played
        }
        JsonArray a = new JsonArray();
        for (Object[] s : sounds) {
            JsonArray one = new JsonArray();
            one.add(new JsonPrimitive(String.valueOf(s[0])));
            one.add(NpcExporter.number(s[1]));
            one.add(NpcExporter.number(s[2]));
            a.add(one);
        }
        return a;
    }

    private JsonObject mount(EntityLiving e, World world, List<ItemStack> armors) {
        JsonObject o = new JsonObject();
        Class<?> npcMount = cls("lotr.common.entity.npc.LOTRNPCMount");
        Class<?> rideableNpc = cls("lotr.common.entity.npc.LOTREntityNPCRideable");
        boolean horse = e instanceof EntityHorse;
        boolean npc = rideableNpc != null && rideableNpc.isInstance(e);
        o.addProperty("rideable", horse || npc);
        o.addProperty("kind", horse ? "horse" : npc ? "npc" : null);
        if (!horse && !npc) {
            return o;
        }
        Object inv = invoke(e, null, null, "getMountInventory");
        if (inv instanceof IInventory i) {
            o.addProperty("inventory", i.getSizeInventory());
        }
        JsonArray fits = new JsonArray();
        if (npcMount != null && npcMount.isInstance(e)) {
            for (ItemStack s : armors) {
                if (Boolean.TRUE.equals(invoke(e, new Class<?>[] { ItemStack.class }, new Object[] { s },
                        "isMountArmorValid"))) {
                    fits.add(new JsonPrimitive(Item.itemRegistry.getNameForObject(s.getItem())));
                }
            }
        }
        o.add("armor", fits);
        o.addProperty("faction", NpcExporter.enumName(NpcExporter.call(e, "getFaction")));
        JsonObject align = alignment(e.getClass());
        o.add("alignment", align);
        // the rider a warg may spawn with (LOTR picks one at random): name -> how many of 200
        JsonObject riders = new JsonObject();
        for (int i = 0; i < 200; i++) {
            Object rider = invoke(e, null, null, "createWargRider");
            if (!(rider instanceof Entity r)) {
                break;
            }
            String n = EntityList.getEntityString(r);
            riders.addProperty(n, riders.has(n) ? riders.get(n).getAsInt() + 1 : 1);
        }
        o.add("riders", riders);
        return o;
    }

    /** Random looks and rideability, sampled from fresh mobs: value -> how many of SAMPLES. */
    private JsonObject variants(Class<?> c, World world) {
        JsonObject o = new JsonObject();
        // rare looks (LOTR's white warg is 1 in 500) need many samples: only mobs that have variants get them
        for (int i = 0; i < (o.entrySet().isEmpty() ? 1 : SAMPLES); i++) {
            EntityLiving e;
            try {
                e = (EntityLiving) c.getConstructor(World.class).newInstance(world);
            } catch (Throwable t) {
                return o;
            }
            StringBuilder key = new StringBuilder();
            for (String m : VARIANTS) {
                Object v = invoke(e, null, null, m);
                if (v != null) {
                    key.append(key.length() == 0 ? "" : ",").append(m).append('=')
                            .append(v instanceof Enum<?> en ? en.name() : v);
                }
            }
            if (key.length() > 0) {
                String k = key.toString();
                o.addProperty(k, o.has(k) ? o.get(k).getAsInt() + 1 : 1);
            }
        }
        return o;
    }

    // ---------------------------------------------------------------- mount armor

    private JsonArray mountArmor(List<ItemStack> out) {
        JsonArray arr = new JsonArray();
        Class<?> armor = cls("lotr.common.item.LOTRItemMountArmor");
        if (armor == null) {
            return arr;
        }
        for (Object key : Item.itemRegistry.getKeys()) {
            Item item = (Item) Item.itemRegistry.getObject(key);
            if (!armor.isInstance(item)) {
                continue;
            }
            JsonObject o = new JsonObject();
            o.addProperty("id", String.valueOf(key));
            o.addProperty("mount", NpcExporter.enumName(NpcExporter.field(item, "mountType")));
            o.addProperty("material", NpcExporter.enumName(NpcExporter.field(item, "armorMaterial")));
            o.add("protection", NpcExporter.number(NpcExporter.call(item, "getDamageReduceAmount")));
            o.addProperty("texture", str(NpcExporter.call(item, "getArmorTexture")));
            Object template = NpcExporter.field(item, "templateItem");
            o.addProperty("template", template instanceof Item t ? Item.itemRegistry.getNameForObject(t) : null);
            arr.add(o);
            out.add(new ItemStack(item));
        }
        return arr;
    }

    // ---------------------------------------------------------------- reading the class files

    /**
     * The alignment LOTR asks for, per method: the float compared with {@code getAlignment(...)} right after the call,
     * or passed to {@code notifyAlignmentNotHighEnough}.
     */
    private JsonObject alignment(Class<?> c) {
        JsonObject o = new JsonObject();
        for (Class<?> s = c; s != null && s.getName().startsWith("lotr."); s = s.getSuperclass()) {
            ClassNode node = node(s.getName());
            if (node == null) {
                continue;
            }
            for (MethodNode m : node.methods) {
                for (AbstractInsnNode in = m.instructions.getFirst(); in != null; in = in.getNext()) {
                    Float f = null;
                    if (in instanceof MethodInsnNode call && call.name.equals("getAlignment")) {
                        AbstractInsnNode next = in.getNext();
                        for (int k = 0; k < 3 && next != null && f == null; k++, next = next.getNext()) {
                            f = floatConst(next);
                        }
                    } else if (in instanceof MethodInsnNode call && call.name.equals("notifyAlignmentNotHighEnough")) {
                        for (AbstractInsnNode prev = in.getPrevious(); prev != null && f == null;
                                prev = prev.getPrevious()) {
                            f = floatConst(prev);
                            if (prev instanceof MethodInsnNode) {
                                break;
                            }
                        }
                    }
                    if (f != null && !o.has(m.name)) {
                        o.addProperty(m.name, f);
                    }
                }
            }
        }
        return o;
    }

    private static Float floatConst(AbstractInsnNode in) {
        return in instanceof LdcInsnNode ldc && ldc.cst instanceof Float f ? f : null;
    }

    /**
     * What a spawn check does, as data: the class that declares it, then in order the calls, numbers and fields it
     * uses (and those of LOTR methods it calls, two deep). The converter turns known shapes into spawn rules.
     */
    private JsonObject summary(Class<?> c, String... names) {
        for (Class<?> s = c; s != null && s != Object.class; s = s.getSuperclass()) {
            ClassNode node = node(s.getName());
            MethodNode m = node == null ? null : method(node, names);
            if (m != null) {
                JsonObject o = new JsonObject();
                o.addProperty("owner", s.getName());
                JsonArray ops = new JsonArray();
                walk(s.getName(), m, ops, 0, new HashSet<>());
                o.add("ops", ops);
                return o;
            }
            if (node == null && !s.getName().startsWith("lotr.")) {
                JsonObject o = new JsonObject(); // vanilla's own check
                o.addProperty("owner", s.getName());
                return o;
            }
        }
        return null;
    }

    private void walk(String owner, MethodNode m, JsonArray ops, int depth, Set<String> seen) {
        if (!seen.add(owner + "." + m.name + m.desc)) {
            return;
        }
        for (AbstractInsnNode in = m.instructions.getFirst(); in != null; in = in.getNext()) {
            if (in instanceof MethodInsnNode call) {
                String target = call.owner.replace('/', '.');
                ops.add(new JsonPrimitive("call " + simple(target) + "." + call.name));
                if (depth < 2 && target.startsWith("lotr.")) {
                    ClassNode n = node(target);
                    MethodNode inner = n == null ? null : method(n, call.name, call.desc);
                    if (inner != null) {
                        walk(target, inner, ops, depth + 1, seen);
                        ops.add(new JsonPrimitive("end " + call.name));
                    }
                }
            } else if (in instanceof FieldInsnNode f) {
                ops.add(new JsonPrimitive("field " + simple(f.owner.replace('/', '.')) + "." + f.name));
            } else if (in instanceof LdcInsnNode ldc && ldc.cst instanceof Number n) {
                ops.add(new JsonPrimitive(n));
            } else if (in instanceof IntInsnNode i && (i.getOpcode() == Opcodes.BIPUSH || i.getOpcode() == Opcodes.SIPUSH)) {
                ops.add(new JsonPrimitive(i.operand));
            } else if (in instanceof InsnNode i && i.getOpcode() >= Opcodes.ICONST_M1 && i.getOpcode() <= Opcodes.DCONST_1) {
                int op = i.getOpcode();
                Number n = op <= Opcodes.ICONST_5 ? (Number) (op - Opcodes.ICONST_0)
                        : op <= Opcodes.LCONST_1 ? (Number) (long) (op - Opcodes.LCONST_0)
                        : op <= Opcodes.FCONST_2 ? (Number) (float) (op - Opcodes.FCONST_0)
                        : (Number) (double) (op - Opcodes.DCONST_0);
                ops.add(new JsonPrimitive(n));
            } else if (in instanceof InsnNode i && i.getOpcode() >= Opcodes.FCMPL && i.getOpcode() <= Opcodes.DCMPG) {
                ops.add(new JsonPrimitive("cmp"));
            }
        }
    }

    private static MethodNode method(ClassNode node, String... names) {
        for (MethodNode m : node.methods) {
            for (String n : names) {
                if (m.name.equals(n) && (names.length != 2 || !names[1].startsWith("(") || m.desc.equals(names[1]))) {
                    return m;
                }
            }
        }
        return null;
    }

    private ClassNode node(String name) {
        if (classes.containsKey(name)) {
            return classes.get(name);
        }
        ClassNode node = null;
        try (InputStream in = CreatureExporter.class.getClassLoader().getResourceAsStream(
                name.replace('.', '/') + ".class")) {
            if (in != null && name.startsWith("lotr.")) {
                node = new ClassNode();
                new ClassReader(in).accept(node, ClassReader.SKIP_FRAMES);
            }
        } catch (Throwable t) {
            error("class " + name, t);
        }
        classes.put(name, node);
        return node;
    }

    // ---------------------------------------------------------------- helpers

    private static String simple(String name) {
        return name.substring(name.lastIndexOf('.') + 1);
    }

    private static String str(Object v) {
        return v == null ? null : v.toString();
    }

    private static Class<?> cls(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Calls the first method found under any of the names (SRG or MCP) with these parameter types. */
    private static Object invoke(Object o, Class<?>[] types, Object[] args, String... names) {
        Class<?>[] t = types == null ? new Class<?>[0] : types;
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            for (String n : names) {
                try {
                    Method m = c.getDeclaredMethod(n, t);
                    m.setAccessible(true);
                    return m.invoke(o, args);
                } catch (NoSuchMethodException e) {
                    // next name or superclass
                } catch (Throwable e) {
                    return null;
                }
            }
        }
        return null;
    }

    private void error(String where, Throwable t) {
        errors.add(new JsonPrimitive(where + ": " + t));
    }
}
