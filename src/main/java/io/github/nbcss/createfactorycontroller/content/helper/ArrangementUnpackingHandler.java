package io.github.nbcss.createfactorycontroller.content.helper;

import com.simibubi.create.api.packager.unpacking.UnpackingHandler;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Unpacks a Custom-Arrangement package into any container <b>by position</b>
 */
public enum ArrangementUnpackingHandler implements UnpackingHandler {
    INSTANCE;

    /** Registers the positional handler */
    public static void register() {
        UnpackingHandler.REGISTRY.registerProvider(block -> INSTANCE);
    }

    /** The sentinel entry flagging an order as a Custom Arrangement */
    public static BigItemStack marker() {
        return new BigItemStack(new ItemStack(CreateFactoryController.ARRANGEMENT_MARKER_ITEM.get()), 0);
    }

    /** O(1): whether this plain order ends with the arrangement marker. */
    public static boolean isMarked(List<BigItemStack> order) {
        if (order.isEmpty()) return false;
        return order.get(order.size() - 1).stack.is(CreateFactoryController.ARRANGEMENT_MARKER_ITEM.get());
    }

    @Override
    public boolean unpack(Level level, BlockPos pos, BlockState state, Direction side,
                          List<ItemStack> items, @Nullable PackageOrderWithCrafts orderContext, boolean simulate) {
        if (orderContext == null || !orderContext.orderedCrafts().isEmpty() || !isMarked(orderContext.stacks()))
            return UnpackingHandler.DEFAULT.unpack(level, pos, state, side, items, orderContext, simulate);

        List<BigItemStack> payload = orderContext.stacks().subList(0, orderContext.stacks().size() - 1);
        IItemHandler inv = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, state, null, side);
        ItemStack[] plan = inv == null ? null : planPlacement(inv, payload, items);
        if (plan == null) return false;   // unfit — refuse the box; it waits at the packager and retries

        if (!simulate)
            for (int i = 0; i < plan.length; i++)
                if (plan[i] != null) inv.insertItem(i, plan[i], false);
        return true;
    }

    /**
     * Maps the box's actual contents onto the payload's slot positions */
    @Nullable
    private static ItemStack[] planPlacement(IItemHandler inv, List<BigItemStack> payload, List<ItemStack> items) {
        List<ItemStack> remaining = new ArrayList<>(items.size());
        for (ItemStack s : items) remaining.add(s.copy());

        ItemStack[] plan = new ItemStack[payload.size()];
        for (int i = 0; i < payload.size(); i++) {
            BigItemStack e = payload.get(i);
            if (isGap(e)) continue;                            // its mapped slot may hold anything
            int taken = 0;
            for (ItemStack r : remaining) {
                if (taken >= e.count) break;
                if (r.isEmpty() || !ItemStack.isSameItemSameComponents(r, e.stack)) continue;
                int take = Math.min(e.count - taken, r.getCount());
                r.shrink(take);
                taken += take;
            }
            if (taken == 0) continue;                          // short box: this slot's items never arrived
            if (i >= inv.getSlots()) return null;              // arrangement wider than the target
            plan[i] = e.stack.copyWithCount(taken);
            if (!inv.insertItem(i, plan[i], true).isEmpty()) return null;   // doesn't fit its exact slot
        }
        for (ItemStack r : remaining)
            if (!r.isEmpty()) return null;                     // box holds items the payload doesn't mention
        return plan;
    }

    private static boolean isGap(BigItemStack e) {
        return e.stack.isEmpty() || e.count <= 0;
    }
}
