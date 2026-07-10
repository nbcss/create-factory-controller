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
 * Unpacks a Custom-Arrangement package into any container <b>by position</b>: order slot {@code i} →
 * inventory slot {@code i}. A 27-slot chest receives the layout in its first 9 slots; gaps impose nothing on
 * their mapped slot; an occupied slot passes only when it holds the same item and the merged total fits with
 * zero leftover. When the layout can't land — a real entry past the target's slot count, a slot that rejects
 * its item — the unpack returns {@code false}, refusing the box at the packager so it waits and retries
 * (the same backpressure a full container produces), rather than scattering the pattern first-fit.
 *
 * <p>Arrangement packages are identified by an explicit {@linkplain #marker() marker entry} their orders end
 * with (appended by {@code VirtualGaugeBehaviour#buildOrderedSlots}); everything unmarked — including every
 * vanilla/Create order — takes {@link UnpackingHandler#DEFAULT}. Only the items physically present in the
 * box are placed (a short box yields a partial pattern), so counts are never conjured from the context.</p>
 *
 * <p>Registered as a catch-all registry <em>provider</em>: direct per-block registrations (Create's Basin /
 * Creative Crate / Mechanical Crafter, other mods' blocks) always outrank providers, and
 * {@code SimpleRegistry.register} throws on an already-claimed block — so the provider route both respects
 * other handlers and avoids duplicate-registration crashes entirely.</p>
 */
public enum ArrangementUnpackingHandler implements UnpackingHandler {
    INSTANCE;

    /** Registers the positional handler for every block without a direct {@code UnpackingHandler}
     *  registration. Called from the mod constructor: providers are consulted newest-first, so registering
     *  early lets later-registered providers from other mods outrank this catch-all. */
    public static void register() {
        UnpackingHandler.REGISTRY.registerProvider(block -> INSTANCE);
    }

    /** The sentinel entry flagging an order as a Custom Arrangement, appended LAST so payload slot indices
     *  stay untouched. The dedicated {@code arrangement_marker} item makes the flag namespaced (immune to
     *  other mods' meanings for empty entries or magic counts); count 0 keeps it out of every pull — Create
     *  only pulls {@code !stack.isEmpty() && count > 0} entries — so like a gap it exists only in the
     *  stamped order context. */
    public static BigItemStack marker() {
        return new BigItemStack(new ItemStack(CreateFactoryController.ARRANGEMENT_MARKER_ITEM.get()), 0);
    }

    /** O(1): whether this plain order ends with the arrangement marker. Identity only — a foreign mod
     *  mutating the entry's count must not flip a marked order back to first-fit. */
    private static boolean isMarked(List<BigItemStack> order) {
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
     * Maps the box's actual contents onto the payload's slot positions: entry {@code i} is filled with up to
     * {@code count} matching items drawn from the box, targeting inventory slot {@code i}. Returns the
     * per-slot stacks to insert, or {@code null} when the layout can't land: a real entry sits past the
     * target's slot count, its slot rejects the items ({@code insertItem} leftover — a different item, or a
     * same-item merge past the slot's limit), or the box holds items the payload doesn't mention.
     */
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

    /** A non-pulling order entry — empty stack or zero count; Create's pull filter skips exactly these, so
     *  they only exist to hold a slot position open. */
    private static boolean isGap(BigItemStack e) {
        return e.stack.isEmpty() || e.count <= 0;
    }
}
