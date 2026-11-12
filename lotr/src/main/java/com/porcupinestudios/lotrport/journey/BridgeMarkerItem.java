package com.porcupinestudios.lotrport.journey;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import com.porcupinestudios.ringbearer.api.history.HistoryEntry;
import com.porcupinestudios.ringbearer.api.history.HistoryLog;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Marks a bridge for caravans and marching armies. Use it on the deck block at one end, then at the other: the blocks
 * between are read ({@link BridgeRules}: a deck all the way with room above, dry land at both ends, a river under it
 * somewhere) and, if they make a bridge, routes may cross there. Sneak-use on a marked bridge to unmark it (its marker
 * or an operator).
 */
public final class BridgeMarkerItem extends Item {
    public static final ResourceLocation HISTORY = LotrPort.id("bridge_marked");

    public BridgeMarkerItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatableWithFallback("item.lotr_port.bridge_marker", "Bridge Marker");
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(ctx.getPlayer() instanceof ServerPlayer player) || !(ctx.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.PASS;
        }
        ItemStack stack = ctx.getItemInHand();
        BlockPos pos = ctx.getClickedPos();
        Bridges bridges = Bridges.get(level.getServer());
        if (player.isShiftKeyDown()) {
            for (Bridges.Bridge b : bridges.in(level.dimension())) {
                if (b.covers(pos.getX(), pos.getZ()) && (b.owner().equals(player.getUUID())
                        || player.hasPermissions(2))) {
                    bridges.remove(b.id());
                    player.sendSystemMessage(Component.translatableWithFallback("journey.lotr_port.bridge_removed",
                            "Bridge unmarked: caravans won't plan over it"));
                    return InteractionResult.CONSUME;
                }
            }
            return InteractionResult.PASS;
        }
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!data.contains("start")) {
            data.putLong("start", pos.asLong());
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
            player.sendSystemMessage(Component.translatableWithFallback("journey.lotr_port.bridge_start",
                    "Bridge start marked at %s %s %s: now use it on the deck at the other end", pos.getX(),
                    pos.getY(), pos.getZ()));
            return InteractionResult.CONSUME;
        }
        BlockPos start = BlockPos.of(data.getLong("start"));
        stack.remove(DataComponents.CUSTOM_DATA);
        JourneySettings s = JourneyData.settings();
        List<BridgeRules.Column> columns = columns(level, start, pos);
        Optional<String> wrong = BridgeRules.check(columns, s.bridgeMaxLength());
        if (wrong.isPresent()) {
            player.sendSystemMessage(Component.translatableWithFallback("journey.lotr_port.bridge." + wrong.get(),
                    reason(wrong.get(), s.bridgeMaxLength())));
            return InteractionResult.FAIL;
        }
        Bridges.Bridge b = new Bridges.Bridge(UUID.randomUUID(), level.dimension(), start, pos, player.getUUID(),
                level.getGameTime());
        bridges.add(b);
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", b.id());
        tag.putLong("a", start.asLong());
        tag.putLong("b", pos.asLong());
        tag.putUUID("by", player.getUUID());
        HistoryLog.get(level.getServer()).append(new HistoryEntry(level.getGameTime(), HISTORY, level.dimension(),
                Optional.of(start), tag));
        player.sendSystemMessage(Component.translatableWithFallback("journey.lotr_port.bridge_marked",
                "Bridge marked (%s blocks): caravans and armies can cross the river here", columns.size()));
        return InteractionResult.CONSUME;
    }

    private static String reason(String key, int max) {
        return switch (key) {
            case "too_short" -> "That's too short for a bridge";
            case "too_long" -> "A bridge can be at most " + max + " blocks long";
            case "ends_in_water" -> "Both ends of a bridge must be on dry land";
            case "gap" -> "There's a gap in the deck: no solid block to walk on somewhere along it";
            case "blocked" -> "Something blocks the way over the deck: it needs two blocks of air above";
            case "no_river" -> "It doesn't cross a river";
            default -> key;
        };
    }

    /** The columns along the straight line between two deck blocks, at the first's height (a block either way). */
    static List<BridgeRules.Column> columns(ServerLevel level, BlockPos a, BlockPos b) {
        List<BridgeRules.Column> out = new ArrayList<>();
        int n = Math.max(Math.abs(b.getX() - a.getX()), Math.abs(b.getZ() - a.getZ()));
        MiddleEarthMap map = com.porcupinestudios.lotrport.world.LotrDimensions.middleEarth(level.getServer()) == level
                ? MiddleEarthMap.get() : null;
        for (int t = 0; t <= n; t++) {
            int x = (int) Math.round(a.getX() + (b.getX() - a.getX()) * (n == 0 ? 0 : t / (double) n));
            int z = (int) Math.round(a.getZ() + (b.getZ() - a.getZ()) * (n == 0 ? 0 : t / (double) n));
            int y = (int) Math.round(a.getY() + (b.getY() - a.getY()) * (n == 0 ? 0 : t / (double) n));
            int deckY = Integer.MIN_VALUE;
            for (int dy : new int[] {0, 1, -1}) {
                BlockPos p = new BlockPos(x, y + dy, z);
                if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
                    deckY = y + dy;
                    break;
                }
            }
            boolean deck = deckY != Integer.MIN_VALUE;
            boolean headroom = deck && level.getBlockState(new BlockPos(x, deckY + 1, z)).getCollisionShape(level,
                    new BlockPos(x, deckY + 1, z)).isEmpty() && level.getBlockState(new BlockPos(x, deckY + 2, z))
                    .getCollisionShape(level, new BlockPos(x, deckY + 2, z)).isEmpty();
            boolean water = false;
            for (int d = 1; d <= 12 && deck; d++) {
                if (level.getFluidState(new BlockPos(x, deckY - d, z)).is(FluidTags.WATER)) {
                    water = true;
                    break;
                }
            }
            boolean river = water && (level.getBiome(new BlockPos(x, deckY, z)).is(BiomeTags.IS_RIVER)
                    || map != null && map.riverBiome() >= 0 && map.biomeIndexAt(x, z) == map.riverBiome());
            out.add(new BridgeRules.Column(deck, headroom, water, river));
        }
        return out;
    }
}
