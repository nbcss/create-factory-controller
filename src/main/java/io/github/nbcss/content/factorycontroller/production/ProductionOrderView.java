package io.github.nbcss.content.factorycontroller.production;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Client-facing snapshot of a {@link ProductionOrder} for the monitoring tab (item 8): the order id, its address,
 * how long ago it was created (for the mm:ss timer), and each request's promised item + amount + state. Synced
 * from the server; carries no gauge internals.
 */
public record ProductionOrderView(int orderId, String address, int ageTicks, List<RequestView> requests) {

    /** One request line: the promised item, how much, how much is currently in network stock (for a processing
     *  task's progress display), and {@link ProductionTask.State} as an ordinal. */
    public record RequestView(ItemStack display, int amount, int inStock, int state) {
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestView> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC, RequestView::display,
            ByteBufCodecs.VAR_INT, RequestView::amount,
            ByteBufCodecs.VAR_INT, RequestView::inStock,
            ByteBufCodecs.VAR_INT, RequestView::state,
            RequestView::new);

        public ProductionTask.State stateEnum() {
            ProductionTask.State[] values = ProductionTask.State.values();
            return values[Math.floorMod(state, values.length)];
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ProductionOrderView> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, ProductionOrderView::orderId,
        ByteBufCodecs.STRING_UTF8, ProductionOrderView::address,
        ByteBufCodecs.VAR_INT, ProductionOrderView::ageTicks,
        RequestView.STREAM_CODEC.apply(ByteBufCodecs.list()), ProductionOrderView::requests,
        ProductionOrderView::new);
}
