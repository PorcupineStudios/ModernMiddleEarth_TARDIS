package com.porcupinestudios.lotrport.crafting;

import java.util.Optional;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.CommonHooks;

/**
 * A faction table's crafting grid: the table's own recipes first, then normal crafting. Runs on the server only; the
 * client shows the plain crafting screen, which is why the menu keeps the vanilla menu type.
 */
public class FactionCraftingMenu extends CraftingMenu {
    private final ContainerLevelAccess access;
    private final ResourceLocation table;
    private final Player player;
    private final CraftingContainer grid;
    private final ResultContainer result;

    public FactionCraftingMenu(int id, Inventory inventory, ContainerLevelAccess access, ResourceLocation table) {
        super(id, inventory, access);
        this.access = access;
        this.table = table;
        this.player = inventory.player;
        this.grid = (CraftingContainer) slots.get(1).container;
        this.result = (ResultContainer) slots.get(0).container;
        Slot old = slots.get(0);
        Slot slot = new TableResultSlot(player, grid, result, old.x, old.y);
        slot.index = 0;
        slots.set(0, slot);
    }

    /** Finds the recipe this table makes from the grid: its own recipes first, then vanilla crafting. */
    public static Optional<? extends RecipeHolder<? extends Recipe<CraftingInput>>> find(
            Level level, ResourceLocation table, CraftingInput input) {
        for (RecipeHolder<FactionRecipe> holder : level.getRecipeManager().getAllRecipesFor(
                LotrRecipes.FACTION_CRAFTING.get())) {
            if (holder.value().table().equals(table) && holder.value().matches(input, level)) {
                return Optional.of(holder);
            }
        }
        return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
    }

    @Override
    public void slotsChanged(Container container) {
        access.execute((level, pos) -> update(level));
    }

    private void update(Level level) {
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) {
            return;
        }
        CraftingInput input = grid.asCraftInput();
        ItemStack stack = ItemStack.EMPTY;
        var found = find(level, table, input);
        if (found.isPresent()) {
            if (result.setRecipeUsed(level, sp, found.get())) {
                ItemStack made = found.get().value().assemble(input, level.registryAccess());
                if (made.isItemEnabled(level.enabledFeatures())) {
                    stack = made;
                }
            }
        }
        result.setItem(0, stack);
        setRemoteSlot(0, stack);
        sp.connection.send(new ClientboundContainerSetSlotPacket(containerId, incrementStateId(), 0, stack));
    }

    @Override
    public boolean stillValid(Player player) {
        Block block = BuiltInRegistries.BLOCK.get(table);
        return stillValid(access, player, block);
    }

    /** Takes the result and leaves the right remains (buckets, bowls) for whichever recipe made it. */
    private final class TableResultSlot extends ResultSlot {
        TableResultSlot(Player player, CraftingContainer grid, Container result, int x, int y) {
            super(player, grid, result, 0, x, y);
        }

        @Override
        public void onTake(Player taker, ItemStack stack) {
            checkTakeAchievements(stack);
            CraftingInput.Positioned positioned = grid.asPositionedCraftInput();
            CraftingInput input = positioned.input();
            CommonHooks.setCraftingPlayer(taker);
            NonNullList<ItemStack> remaining = find(taker.level(), table, input)
                    .map(h -> h.value().getRemainingItems(input))
                    .orElseGet(() -> NonNullList.withSize(input.size(), ItemStack.EMPTY));
            CommonHooks.setCraftingPlayer(null);
            for (int y = 0; y < input.height(); y++) {
                for (int x = 0; x < input.width(); x++) {
                    int slot = x + positioned.left() + (y + positioned.top()) * grid.getWidth();
                    ItemStack inSlot = grid.getItem(slot);
                    ItemStack left = remaining.get(x + y * input.width());
                    if (!inSlot.isEmpty()) {
                        grid.removeItem(slot, 1);
                        inSlot = grid.getItem(slot);
                    }
                    if (left.isEmpty()) {
                        continue;
                    }
                    if (inSlot.isEmpty()) {
                        grid.setItem(slot, left);
                    } else if (ItemStack.isSameItemSameComponents(inSlot, left)) {
                        left.grow(inSlot.getCount());
                        grid.setItem(slot, left);
                    } else if (!taker.getInventory().add(left)) {
                        taker.drop(left, false);
                    }
                }
            }
        }
    }
}
