package dev.bastion.client.compat.jei;

import dev.bastion.network.BastionNetwork;
import dev.bastion.network.WorkstationFill;
import dev.bastion.registry.BastionMenus;
import dev.bastion.workstation.WorkstationBlockEntity;
import dev.bastion.workstation.WorkstationMenu;
import dev.bastion.workstation.WorkstationRecipe;
import dev.bastion.workstation.WorkstationType;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JEI's + button on a station recipe while that station's GUI is open: the server picks the recipe and moves one craft's
 * ingredients (shift: as many as fit) from the inventory into the inputs (WorkstationFill). Missing ingredients turn the
 * button red and mark their slots; another station's GUI hides it.
 */
record StationTransfer(IRecipeTransferHandlerHelper helper, RecipeType<WorkstationRecipe> type, WorkstationType station)
        implements IRecipeTransferHandler<WorkstationMenu, WorkstationRecipe> {
    @Override
    public Class<? extends WorkstationMenu> getContainerClass() {
        return WorkstationMenu.class;
    }

    @Override
    public Optional<MenuType<WorkstationMenu>> getMenuType() {
        return Optional.of(BastionMenus.WORKSTATION.get());
    }

    @Override
    public RecipeType<WorkstationRecipe> getRecipeType() {
        return type;
    }

    @Override
    @Nullable
    public IRecipeTransferError transferRecipe(WorkstationMenu menu, WorkstationRecipe recipe, IRecipeSlotsView slots, Player player,
                                               boolean maxTransfer, boolean doTransfer) {
        if (menu.type() != station) return helper.createInternalError();
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < WorkstationBlockEntity.INPUTS; i++) stacks.add(menu.getSlot(i).getItem());
        stacks.addAll(player.getInventory().items);
        List<IRecipeSlotView> inputs = slots.getSlotViews(RecipeIngredientRole.INPUT);
        List<IRecipeSlotView> missing = new ArrayList<>();
        for (int i = 0; i < recipe.ingredients().size() && i < inputs.size(); i++) {
            WorkstationRecipe.Counted counted = recipe.ingredients().get(i);
            if (WorkstationBlockEntity.count(counted, stacks) < counted.count()) missing.add(inputs.get(i));
        }
        if (!missing.isEmpty()) return helper.createUserErrorForMissingSlots(Component.translatable("jei.bastion.transfer.missing"), missing);
        if (doTransfer) BastionNetwork.CHANNEL.sendToServer(new WorkstationFill(menu.pos(), recipe.id(), maxTransfer ? WorkstationFill.MAX_CRAFTS : 1));
        return null;
    }
}
